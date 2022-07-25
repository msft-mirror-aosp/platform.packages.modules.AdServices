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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.exception.JSExecutionException;
import com.android.adservices.service.profiling.JSScriptEngineLogConstants;
import com.android.adservices.service.profiling.Profiler;
import com.android.adservices.service.profiling.StopWatch;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ClosingFuture;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import org.chromium.android_webview.js_sandbox.client.JsIsolate;
import org.chromium.android_webview.js_sandbox.client.JsSandbox;

import java.io.Closeable;
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

    @VisibleForTesting public static final String TAG = JSScriptEngine.class.getSimpleName();
    public static final String WASM_MODULE_BYTES_ID = "__wasmModuleBytes";
    public static final String WASM_MODULE_ARG_NAME = "wasmModule";

    @SuppressLint("StaticFieldLeak")
    private static JSScriptEngine sSingleton;

    @NonNull private final Context mContext;
    @NonNull private final JsSandboxProvider mJsSandboxProvider;
    @NonNull private final ListeningExecutorService mExecutorService;
    @NonNull private final Profiler mProfiler;

    /**
     * Extracting the logic to create the JsSandbox in a factory class for better testability. This
     * factory class creates a single instance of `JsSandbox` until the instance is invalidated by
     * calling {@link JsSandboxProvider#destroyCurrentInstance()}. The instance is returned wrapped
     * in a {@code Future}
     */
    @VisibleForTesting
    static class JsSandboxProvider {
        private final Object mSandboxLock = new Object();
        private StopWatch mSandboxInitStopWatch;
        private Profiler mProfiler;

        @GuardedBy("mSandboxLock")
        private FluentFuture<JsSandbox> mFutureSandbox;

        JsSandboxProvider(Profiler profiler) {
            mProfiler = profiler;
        }

        public FluentFuture<JsSandbox> getFutureInstance(Context context) {
            synchronized (mSandboxLock) {
                if (mFutureSandbox == null) {
                    LogUtil.i("Creating JsSandbox");
                    mSandboxInitStopWatch =
                            mProfiler.start(JSScriptEngineLogConstants.SANDBOX_INIT_TIME);

                    mFutureSandbox =
                            FluentFuture.from(
                                    JsSandbox.newConnectedInstanceAsync(
                                            // This instance will have the same lifetime
                                            // of the PPAPI process
                                            context.getApplicationContext()));

                    mFutureSandbox.addCallback(
                            new FutureCallback<JsSandbox>() {
                                @Override
                                public void onSuccess(JsSandbox result) {
                                    mSandboxInitStopWatch.stop();
                                    LogUtil.i("JSScriptEngine created.");
                                }

                                @Override
                                public void onFailure(Throwable t) {
                                    mSandboxInitStopWatch.stop();
                                    LogUtil.e(t, "JsSandbox initialization failed");
                                }
                            },
                            AdServicesExecutors.getLightWeightExecutor());
                }

                return mFutureSandbox;
            }
        }

        /**
         * Closes the connection with {@code JsSandbox}. Any running computation will fail. A new
         * call to {@link #getFutureInstance(Context)} will create the instance again.
         */
        public ListenableFuture<Void> destroyCurrentInstance() {
            synchronized (mSandboxLock) {
                if (mFutureSandbox != null) {
                    ListenableFuture<Void> result =
                            FluentFuture.from(mFutureSandbox)
                                    .<Void>transform(
                                            jsSandbox -> {
                                                LogUtil.i(
                                                        "Closing connection from JSScriptEngine to"
                                                                + " WebView Sandbox");
                                                jsSandbox.close();
                                                return null;
                                            },
                                            AdServicesExecutors.getLightWeightExecutor())
                                    .catching(
                                            Throwable.class,
                                            t -> {
                                                LogUtil.i(
                                                        "JsSandbox initialization failed, won't"
                                                                + " close");
                                                return null;
                                            },
                                            AdServicesExecutors.getLightWeightExecutor());
                    mFutureSandbox = null;
                    return result;
                } else {
                    return Futures.immediateVoidFuture();
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
                Profiler.createNoOpInstance(TAG),
                AdServicesExecutors.getLightWeightExecutor());
    }

    /** @return JSScriptEngine instance */
    public static JSScriptEngine getInstance(@NonNull Context context) {
        synchronized (JSScriptEngine.class) {
            if (sSingleton == null) {
                Profiler profiler = Profiler.createNoOpInstance(TAG);
                sSingleton =
                        new JSScriptEngine(
                                context,
                                new JsSandboxProvider(profiler),
                                profiler,
                                // There is no blocking call or IO code in the service logic
                                AdServicesExecutors.getLightWeightExecutor());
            }

            return sSingleton;
        }
    }

    /**
     * @return a singleton JSScriptEngine instance with the given profiler
     * @throws IllegalStateException if an existing instance exists
     */
    @VisibleForTesting
    public static JSScriptEngine getInstanceForTesting(
            @NonNull Context context, @NonNull Profiler profiler) {
        synchronized (JSScriptEngine.class) {
            // If there is no instance already created or the instance was shutdown
            if (sSingleton != null) {
                throw new IllegalStateException(
                        "Unable to initialize test JSScriptEngine multiple times using"
                                + "the real JsSandboxProvider.");
            }

            sSingleton =
                    new JSScriptEngine(
                            context,
                            new JsSandboxProvider(profiler),
                            profiler,
                            AdServicesExecutors.getLightWeightExecutor());
        }

        return sSingleton;
    }

    /**
     * This method will instantiate a new instance of JSScriptEngine every time. It is intended to
     * be used with a fake/mock {@link JsSandboxProvider}. Using a real one would cause exception
     * when trying to create the second instance of {@link JsSandbox}.
     *
     * @return a new JSScriptEngine instance
     */
    @VisibleForTesting
    public static JSScriptEngine createNewInstanceForTesting(
            @NonNull Context context,
            @NonNull JsSandboxProvider jsSandboxProvider,
            @NonNull Profiler profiler) {
        return new JSScriptEngine(
                context, jsSandboxProvider, profiler, AdServicesExecutors.getLightWeightExecutor());
    }

    /**
     * Closes the connection with WebView. Any running computation will be terminated. It is not
     * necessary to recreate instances of {@link JSScriptEngine} after this call; new calls to
     * {@code evaluate} for existing instance will cause the connection to WV to be restored if
     * necessary.
     *
     * @return A future to be used by tests needing to know when the sandbox close call happened.
     */
    public ListenableFuture<Void> shutdown() {
        return mJsSandboxProvider.destroyCurrentInstance();
    }

    @VisibleForTesting
    @SuppressWarnings("FutureReturnValueIgnored")
    JSScriptEngine(
            @NonNull Context context,
            @NonNull JsSandboxProvider jsSandboxProvider,
            @NonNull Profiler profiler,
            @NonNull ListeningExecutorService executorService) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(jsSandboxProvider);
        Objects.requireNonNull(profiler);
        Objects.requireNonNull(executorService);

        this.mContext = context;
        this.mJsSandboxProvider = jsSandboxProvider;
        this.mProfiler = profiler;
        this.mExecutorService = executorService;
        // Forcing initialization of WebView
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
    public ListenableFuture<String> evaluate(
            @NonNull String jsScript,
            @NonNull List<JSScriptArgument> args,
            @NonNull String entryFunctionName) {
        return evaluateInternal(jsScript, args, entryFunctionName, null);
    }

    /**
     * Loads the WASM module defined by {@code wasmBinary}, invokes the function {@code
     * entryFunctionName} defined by the JS code in {@code jsScript} and return the result. It will
     * reset the WebView status after evaluating the script. The function is expected to accept all
     * the arguments defined in {@code args} plus an extra final parameter of type {@code
     * WebAssembly.Module}.
     *
     * @param jsScript The JS script
     * @param args The arguments to pass when invoking {@code entryFunctionName}
     * @param entryFunctionName The name of a function defined in {@code jsScript} that should be
     *     invoked.
     * @return A {@link ListenableFuture} containing the JS string representation of the result of
     *     {@code entryFunctionName}'s invocation
     */
    @NonNull
    public ListenableFuture<String> evaluate(
            @NonNull String jsScript,
            @NonNull byte[] wasmBinary,
            @NonNull List<JSScriptArgument> args,
            @NonNull String entryFunctionName) {
        Objects.requireNonNull(wasmBinary);

        return evaluateInternal(jsScript, args, entryFunctionName, wasmBinary);
    }

    @NonNull
    private ListenableFuture<String> evaluateInternal(
            @NonNull String jsScript,
            @NonNull List<JSScriptArgument> args,
            @NonNull String entryFunctionName,
            @Nullable byte[] wasmBinary) {
        Objects.requireNonNull(jsScript);
        Objects.requireNonNull(args);
        Objects.requireNonNull(entryFunctionName);

        return ClosingFuture.from(mJsSandboxProvider.getFutureInstance(mContext))
                .transformAsync(
                        (closer, jsSandbox) ->
                                evaluateOnSandbox(
                                        closer,
                                        jsSandbox,
                                        jsScript,
                                        args,
                                        entryFunctionName,
                                        wasmBinary),
                        mExecutorService)
                .finishToFuture();
    }

    @NonNull
    private ClosingFuture<String> evaluateOnSandbox(
            @NonNull ClosingFuture.DeferredCloser closer,
            @NonNull JsSandbox jsSandbox,
            @NonNull String jsScript,
            @NonNull List<JSScriptArgument> args,
            @NonNull String entryFunctionName,
            @Nullable byte[] wasmBinary) {

        boolean hasWasmModule = wasmBinary != null;
        if (hasWasmModule) {
            Preconditions.checkState(
                    isWasmSupported(jsSandbox),
                    "Cannot evaluate a JS script depending on WASM on the JS"
                            + " Sandbox available on this device");
        }

        JsIsolate jsIsolate = createIsolate(jsSandbox);
        closer.eventuallyClose(new CloseableIsolateWrapper(jsIsolate), mExecutorService);

        if (hasWasmModule) {
            LogUtil.d(
                    "Evaluating JS script with associated WASM on thread %s",
                    Thread.currentThread().getName());

            if (!jsIsolate.provideNamedData(WASM_MODULE_BYTES_ID, wasmBinary)) {
                throw new JSExecutionException("Unable to pass WASM byte array to JS Isolate");
            }
        } else {
            LogUtil.d("Evaluating JS script on thread %s", Thread.currentThread().getName());
        }

        String entryPointCall = callEntryPoint(args, entryFunctionName, hasWasmModule);

        String fullScript = jsScript + "\n" + entryPointCall;
        LogUtil.d("Calling WebView for script %s", fullScript);

        StopWatch jsExecutionStopWatch =
                mProfiler.start(JSScriptEngineLogConstants.JAVA_EXECUTION_TIME);
        return ClosingFuture.from(jsIsolate.evaluateJavascriptAsync(fullScript))
                .transform(
                        (ignoredCloser, result) -> {
                            jsExecutionStopWatch.stop();
                            return result;
                        },
                        mExecutorService)
                .catching(
                        Exception.class,
                        (ignoredCloser, exception) -> {
                            jsExecutionStopWatch.stop();
                            throw new JSExecutionException(
                                    "Failure running JS in WebView: " + exception.getMessage(),
                                    exception);
                        },
                        mExecutorService);
    }

    private boolean isWasmSupported(JsSandbox jsSandbox) {
        boolean wasmCompilationSupported = jsSandbox.isFeatureSupported(JsSandbox.WASM_COMPILATION);
        // We will pass the WASM binary via `provideNamesData`
        // The JS will read the data using android.consumeNamedDataAsArrayBuffer
        boolean provideConsumeArrayBufferSupported =
                jsSandbox.isFeatureSupported(JsSandbox.PROVIDE_CONSUME_ARRAY_BUFFER);
        // The call android.consumeNamedDataAsArrayBuffer to read the WASM byte array
        // returns a promises so all our code will be in a promise chain
        boolean promiseReturnSupported = jsSandbox.isFeatureSupported(JsSandbox.PROMISE_RETURN);
        LogUtil.d(
                String.format(
                        "Is WASM supported? WASM_COMPILATION: %b  PROVIDE_CONSUME_ARRAY_BUFFER: %b,"
                                + " PROMISE_RETURN: %b",
                        wasmCompilationSupported,
                        provideConsumeArrayBufferSupported,
                        promiseReturnSupported));
        return wasmCompilationSupported
                && provideConsumeArrayBufferSupported
                && promiseReturnSupported;
    }

    /**
     * @return a future value inidicating if the JS Sandbox installed on the device supports WASM
     *     execution or an error if the connection to the JS Sandbox failed.
     */
    public ListenableFuture<Boolean> isWasmSupported() {
        return mJsSandboxProvider
                .getFutureInstance(mContext)
                .transform(jsSandbox -> isWasmSupported(jsSandbox), mExecutorService);
    }

    /**
     * Creates a new isolate. This method handles the case where the `JsSandbox` process has been
     * terminated by closing this connection. The ongoing call will fail, we won't try to recover it
     * to keep the code simple.
     */
    private JsIsolate createIsolate(JsSandbox jsSandbox) {
        StopWatch isolateStopWatch =
                mProfiler.start(JSScriptEngineLogConstants.ISOLATE_CREATE_TIME);
        try {
            return jsSandbox.createIsolate();
        } catch (RuntimeException jsSandboxIsDisconnected) {
            LogUtil.e(
                    "JSSandboxProcess is disconnected, cannot create an isolate to run JS code"
                        + " into. Resetting connection with AwJsSandbox to enable future calls.");
            mJsSandboxProvider.destroyCurrentInstance();
            throw new JSScriptEngineConnectionException(
                    "Unable to create isolate", jsSandboxIsDisconnected);
        } finally {
            isolateStopWatch.stop();
        }
    }

    /**
     * @return The JS code for the definition an anonymous function containing the declaration of
     *     the value of {@code args} and the invocation of the given {@code entryFunctionName}. If
     *     the {@code addWasmBinary} parameter is true, the target function is expected to accept an
     *     extra final parameter 'wasmModule' of type {@code WebAssembly.Module} and the method will
     *     return a promise.
     */
    @NonNull
    private String callEntryPoint(
            @NonNull List<JSScriptArgument> args,
            @NonNull String entryFunctionName,
            boolean addWasmBinary) {
        StringBuilder resultBuilder = new StringBuilder("(function() {\n");
        // Declare args as constant inside this function closure to avoid any direct access by
        // the functions in the script we are calling.
        for (JSScriptArgument arg : args) {
            // Avoiding to use addJavaScriptInterface because too expensive, just
            // declaring the string parameter as part of the script.
            resultBuilder.append(arg.variableDeclaration());
            resultBuilder.append("\n");
        }

        String argumentPassing =
                args.stream().map(JSScriptArgument::name).collect(Collectors.joining(","));
        if (addWasmBinary) {
            argumentPassing += "," + WASM_MODULE_ARG_NAME;
            resultBuilder.append(
                    String.format(
                            "return android.consumeNamedDataAsArrayBuffer(\"%s\")"
                                    + ".then((__value) => {\n"
                                    + " return WebAssembly.compile(__value).then((%s) => {\n",
                            WASM_MODULE_BYTES_ID, WASM_MODULE_ARG_NAME));
        }

        // Call entryFunctionName with the constants just declared as parameters
        resultBuilder.append(
                String.format(
                        "return JSON.stringify(%s(%s));\n", entryFunctionName, argumentPassing));

        if (addWasmBinary) {
            resultBuilder.append("})});\n");
        }
        resultBuilder.append("})();\n");

        return resultBuilder.toString();
    }

    /**
     * Wrapper class required to convert an {@link java.lang.AutoCloseable} {@link JsIsolate} into a
     * {@link Closeable} type.
     */
    private static class CloseableIsolateWrapper implements Closeable {
        @NonNull final JsIsolate mIsolate;

        CloseableIsolateWrapper(@NonNull JsIsolate isolate) {
            Objects.requireNonNull(isolate);
            mIsolate = isolate;
        }

        @Override
        public void close() {
            LogUtil.d("Closing WebView isolate");
            // Closing the isolate will also cause the thread in WebView to be terminated if
            // still running.
            // There is no need to verify if ISOLATE_TERMINATION is supported by WebView
            // because there is no new API but just new capability on the WebView side for
            // existing API.
            mIsolate.close();
        }
    }
}
