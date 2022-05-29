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

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;

import org.chromium.android_webview.js_sandbox.client.AwJsIsolate;
import org.chromium.android_webview.js_sandbox.client.AwJsSandbox;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

    private static final String TAG = JSScriptEngine.class.getSimpleName();

    private final Context mContext;

    private static final Object sSandboxLock = new Object();
    private static FluentFuture<AwJsSandbox> sFutureSandbox;

    protected FluentFuture<AwJsSandbox> getFutureSandbox(Context context) {
        synchronized (sSandboxLock) {
            if (sFutureSandbox == null) {
                sFutureSandbox =
                        FluentFuture.from(
                                CallbackToFutureAdapter.getFuture(
                                        completer -> {
                                            LogUtil.i("Creating AwJsSandbox");
                                            AwJsSandbox.newConnectedInstance(
                                                    // This instance will have the same lifetime
                                                    // of the PPAPI process
                                                    context.getApplicationContext(),
                                                    awJsSandbox -> {
                                                        completer.set(awJsSandbox);
                                                    });
                                            LogUtil.i("JSScriptEngine created.");

                                            // This value is used only for debug purposes: it will
                                            // be used
                                            // in
                                            // toString() of returned future or error cases.
                                            return "JSSscriptEngine constructor";
                                        }));
            }
            return sFutureSandbox;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @SuppressWarnings("FutureReturnValueIgnored")
    public JSScriptEngine(@NonNull Context context) {
        this.mContext = context;
        // Forcing initialization of WebView
        getFutureSandbox(mContext);
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
                    getFutureSandbox(mContext)
                            .addCallback(
                                    new FutureCallback<AwJsSandbox>() {
                                        @Override
                                        public void onSuccess(AwJsSandbox jsSandbox) {
                                            AwJsIsolate jsIsolate = jsSandbox.createIsolate();
                                            ExecutionCallback callback =
                                                    new ExecutionCallback(jsIsolate, completer);
                                            try {
                                                jsIsolate.evaluateJavascript(fullScript, callback);
                                            } catch (RuntimeException e) {

                                                callback.reportError(
                                                        "Exception while evaluating JS in WebView: "
                                                                + e);
                                            }
                                        }

                                        @Override
                                        public void onFailure(Throwable t) {
                                            String error = "Failed executing JS script";
                                            completer.setException(
                                                    new JSExecutionException(error, t));
                                        }
                                    },
                                    directExecutor()); // This just starts the JS execution in
                    // another thread; it's a lightweight task.

                    // This value is used only for debug purposes: it will be used in toString()
                    // of returned future or error cases.
                    return "AwJsSandbox.addCallback operation";
                });
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

        ExecutionCallback(AwJsIsolate jsIsolate, CallbackToFutureAdapter.Completer<String> future) {
            this.mJsIsolate = jsIsolate;
            this.mFuture = future;
        }

        @Override
        public void reportResult(String result) {
            // TODO: trim off these quotes, just keeping it so UTs don't fail.
            mFuture.set(result);
            mJsIsolate.close();
        }

        @Override
        public void reportError(String error) {
            mFuture.setException(new JSExecutionException(error));
            mJsIsolate.close();
        }
    }
}
