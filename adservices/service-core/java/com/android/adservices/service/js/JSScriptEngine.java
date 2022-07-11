/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.adservices.service.js;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.content.Context;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.adservices.LogUtil;
import com.android.adservices.service.exception.JSExecutionException;
import com.android.adservices.service.profiling.JSScriptEngineLogConstants;
import com.android.adservices.service.profiling.Profiler;
import com.android.adservices.service.profiling.StopWatch;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;

import org.chromium.android_webview.js_sandbox.client.AwJsIsolate;
import org.chromium.android_webview.js_sandbox.client.AwJsSandbox;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.concurrent.GuardedBy;

/**
 * A convenience class to execute JS scripts using a WebView. Because arguments to the {@link
 * #evaluate(String, List)} methods are set at WebView level, calls to that methods are serialized
 * to avoid one scripts being able to interfere one another.
 *
 * <p>The class is re-entrant, for best performance when using it on multiple thread is better to
 * have every thread using its own instance.
 */
public class JSScriptEngine {
    public static final String ENTRY_POINT_FUNC_NAME = "__rb_entry_point";
    public static final String TAG = JSScriptEngine.class.getSimpleName();

    private final JsSandboxProvider mJsSandboxProvider;

    @SuppressLint("StaticFieldLeak")
    private static JSScriptEngine sSingleton;

    private final Context mContext;
    private final Profiler mProfiler;

    /**
     * Closes the connection with WebView. Any running computation will be terminated. It is not
     * necessary to recreate instances of {@link JSScriptEngine} after this call; new calls to
     * {@code evaluate} for existing instance will cause the connection to WV to be restored if
     * necessary.
     */
    public void shutdown() {
        mJsSandboxProvider.destroyCurrentInstance();
    }

    /**
     * Extracting the logic to create the AwJsSandbox in a factory class for better testability.
     * This factory class creates a single instance of `AwJsSandbox` until the instance is
     * invalidated by calling {@link JsSandboxProvider#destroyCurrentInstance()}. The instance is
     * returned wrapped in a {@code Future}
     */
    @VisibleForTesting
    static class JsSandboxProvider {
        private final Object mSandboxLock = new Object();
        private StopWatch mSandboxInitStopWatch;
        private Profiler mProfiler;

        @GuardedBy("mSandboxLock")
        private FluentFuture<AwJsSandbox> mFutureSandbox;

        JsSandboxProvider(Profiler profiler) {
            mProfiler = profiler;
        }

        public FluentFuture<AwJsSandbox> getFutureInstance(Context context) {
            synchronized (mSandboxLock) {
                if (mFutureSandbox == null) {
                    mSandboxInitStopWatch =
                            mProfiler.start(JSScriptEngineLogConstants.SANDBOX_INIT_TIME);
                    ListenableFuture<AwJsSandbox> future =
                            CallbackToFutureAdapter.getFuture(
                                    completer -> {
                                        LogUtil.i("Creating AwJsSandbox");
                                        AwJsSandbox.newConnectedInstance(
                                                // This instance has the same lifetime of the PPAPI
                                                // process.
                                                context.getApplicationContext(),
                                                awJsSandbox -> {
                                                    completer.set(awJsSandbox);
                                                    mSandboxInitStopWatch.stop();
                                                });
                                        LogUtil.i("JSScriptEngine created.");

                                        // This value is used only for debug purposes: it will be
                                        // used in toString() of returned future or error cases.
                                        return "JSSscriptEngine constructor";
                                    });
                    mFutureSandbox = FluentFuture.from(future);
                }

                return mFutureSandbox;
            }
        }

        /**
         * Closes the connection with {@code AwJsSandbox}. Any running computation will fail. A new
         * call to {@link #getFutureInstance(Context)} will create the instance again.
         */
        public void destroyCurrentInstance() {
            synchronized (mSandboxLock) {
                if (mFutureSandbox != null) {
                    LogUtil.i("Closing connection from JSScriptEngine to WebView Sandbox");
                    mFutureSandbox.addCallback(
                            new FutureCallback<AwJsSandbox>() {
                                @Override
                                public void onSuccess(AwJsSandbox result) {
                                    result.close();
                                }

                                @Override
                                public void onFailure(Throwable t) {
                                    LogUtil.i("AwJsSandbox initialization failed, won't close");
                                }
                            },
                            directExecutor());
                    mFutureSandbox = null;
                }
            }
        }
    }

    @VisibleForTesting
    @SuppressLint("SetJavaScriptEnabled")
    public JSScriptEngine(@NonNull Context context) {
        this(
                context,
                new JsSandboxProvider(Profiler.createNoOpInstance(TAG)),
                Profiler.createNoOpInstance(TAG));
    }

    /** @return JSScriptEngine instance */
    public static JSScriptEngine getInstance(@NonNull Context context) {
        synchronized (JSScriptEngine.class) {
            if (sSingleton == null) {
                Profiler profiler = Profiler.createNoOpInstance(TAG);
                sSingleton = new JSScriptEngine(context, new JsSandboxProvider(profiler), profiler);
            }

            return sSingleton;
        }
    }

    @VisibleForTesting
    public static JSScriptEngine getInstanceForTesting(Context context, Profiler profiler) {
        return new JSScriptEngine(context, new JsSandboxProvider(profiler), profiler);
    }

    @VisibleForTesting
    @SuppressWarnings("FutureReturnValueIgnored")
    JSScriptEngine(
            @NonNull Context context,
            @NonNull JsSandboxProvider jsSandboxProvider,
            Profiler profiler) {
        this.mContext = context;
        this.mJsSandboxProvider = jsSandboxProvider;
        this.mProfiler = profiler;

        // Forcing initialization of WebView.
        jsSandboxProvider.getFutureInstance(mContext);
    }

    /**
     * Same as {@link #evaluate(String, List, String)} where the entry point function name is {@link
     * #ENTRY_POINT_FUNC_NAME}.
     */
    @NonNull
    public ListenableFuture<String> evaluate(
            @NonNull String jsScript, @NonNull List<JSScriptArgument> args) {
        return evaluate(jsScript, args, ENTRY_POINT_FUNC_NAME);
    }

    /**
     * Invokes the function {@code entryFunctionName} defined by the JS code in {@code jsScript} and
     * return the result. It will reset the WebView status after evaluating the script.
     *
     * @param jsScript The JS script
     * @param args The arguments to pass when invoking {@code entryFunctionName}
     * @param entryFunctionName The name of a function defined in {@code jsScript} that should be
     *     invoked.
     * @return A {@link ListenableFuture} containing the JS string representation of the result of
     *     {@code entryFunctionName}'s invocation
     */
    @NonNull
    @SuppressLint("SetJavaScriptEnabled")
    public ListenableFuture<String> evaluate(
            @NonNull String jsScript,
            @NonNull List<JSScriptArgument> args,
            @NonNull String entryFunctionName) {
        Objects.requireNonNull(jsScript);
        Objects.requireNonNull(args);
        Objects.requireNonNull(entryFunctionName);

        LogUtil.d(TAG, "Evaluating JS script on thread %s", Thread.currentThread().getName());
        String entryPointCall = callEntryPoint(args, entryFunctionName);

        String fullScript = jsScript + "\n" + entryPointCall;
        LogUtil.d("Calling WebView for script %s", fullScript);

        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mJsSandboxProvider
                            .getFutureInstance(mContext)
                            .addCallback(
                                    new FutureCallback<AwJsSandbox>() {
                                        @Override
                                        public void onSuccess(AwJsSandbox jsSandbox) {
                                            try {
                                                StopWatch isolateStopWatch =
                                                        mProfiler.start(
                                                                JSScriptEngineLogConstants
                                                                        .ISOLATE_CREATE_TIME);
                                                AwJsIsolate jsIsolate = createIsolate(jsSandbox);
                                                isolateStopWatch.stop();

                                                StopWatch jsExecutionStopWatch =
                                                        mProfiler.start(
                                                                JSScriptEngineLogConstants
                                                                        .JAVA_EXECUTION_TIME);
                                                evaluateJavascript(
                                                        jsIsolate,
                                                        completer,
                                                        fullScript,
                                                        jsExecutionStopWatch);
                                            } catch (RuntimeException isolateCreationFailure) {
                                                LogUtil.w(
                                                        "Error trying to create an isolate, it"
                                                            + " looks like the WebView process is"
                                                            + " not available.",
                                                        isolateCreationFailure);
                                                completer.setException(isolateCreationFailure);
                                            }
                                        }

                                        @Override
                                        public void onFailure(Throwable t) {
                                            LogUtil.w("Failure creating AwJsSandbox", t);
                                            completer.setException(
                                                    new JSExecutionException(
                                                            "Failed creating AwJsSandbox", t));
                                        }
                                    },
                                    // Using directExecutor() since this is a short, light task.
                                    directExecutor());

                    // This value is used only for debug purposes: it will be used in toString()
                    // of returned future or error cases.
                    return "AwJsSandbox.addCallback operation";
                });
    }

    private void evaluateJavascript(
            AwJsIsolate jsIsolate,
            CallbackToFutureAdapter.Completer<String> completer,
            String fullScript,
            StopWatch jsExecutionStopWatch) {
        ExecutionCallback callback =
                new ExecutionCallback(jsIsolate, completer, jsExecutionStopWatch);
        try {
            jsIsolate.evaluateJavascript(fullScript, callback);
        } catch (RuntimeException e) {
            callback.reportError("Exception while evaluating JS in WebView: " + e);
        }
    }

    /**
     * Creates a new isolate. This method handles the case where the `AwJsSandbox` process has been
     * terminated by closing this connection. The ongoing call will fail, we won't try to recover it
     * to keep the code simple.
     */
    private AwJsIsolate createIsolate(AwJsSandbox jsSandbox) {
        try {
            AwJsIsolate jsIsolate = jsSandbox.createIsolate();
            return jsIsolate;
        } catch (RuntimeException jsSandboxIsDisconnected) {
            LogUtil.e(
                    "JSSandboxProcess is disconnected, cannot create an isolate to run JS code"
                        + " into. Resetting connection with AwJsSandbox to enable future calls.");
            mJsSandboxProvider.destroyCurrentInstance();
            throw new JSScriptEngineConnectionException(
                    "Unable to create isolate", jsSandboxIsDisconnected);
        }
    }

    /**
     * @return The JS code for the definition an anonymous function containing the declaration of
     *     the value of {@code args} and the invocation of the given {@code entryFunctionName}. Ex.
     *     (function() { const name = "Stefano"; return helloPerson(name); })();
     */
    @NonNull
    private String callEntryPoint(
            @NonNull List<JSScriptArgument> args, @NonNull String entryFunctionName) {
        StringBuilder resultBuilder = new StringBuilder("(function() {\n");
        // Declare args as constant inside this function closure to avoid any direct access by
        // the functions in the script we are calling.
        for (JSScriptArgument arg : args) {
            // Avoiding to use addJavaScriptInterface because too expensive, just
            // declaring the string parameter as part of the script.
            resultBuilder.append(arg.variableDeclaration());
            resultBuilder.append("\n");
        }

        // Call entryFunctionName with the constants just declared as parameters
        resultBuilder.append(
                String.format(
                        "return JSON.stringify(%s(%s));\n",
                        entryFunctionName,
                        args.stream()
                                .map(JSScriptArgument::name)
                                .collect(Collectors.joining(","))));
        resultBuilder.append("})();\n");

        return resultBuilder.toString();
    }

    private static class ExecutionCallback implements AwJsIsolate.ExecutionCallback {
        private AwJsIsolate mJsIsolate;
        private CallbackToFutureAdapter.Completer<String> mFuture;
        private StopWatch mJsExecutionStopWatch;

        ExecutionCallback(
                AwJsIsolate jsIsolate,
                CallbackToFutureAdapter.Completer<String> future,
                StopWatch jsExecutionStopWatch) {
            this.mJsIsolate = jsIsolate;
            this.mFuture = future;
            this.mJsExecutionStopWatch = jsExecutionStopWatch;
        }

        @Override
        public void reportResult(String result) {
            mFuture.set(result);
            mJsIsolate.close();
            mJsExecutionStopWatch.stop();
        }

        @Override
        public void reportError(String error) {
            mFuture.setException(new JSExecutionException(error));
            mJsIsolate.close();
            mJsExecutionStopWatch.stop();
        }
    }
}
