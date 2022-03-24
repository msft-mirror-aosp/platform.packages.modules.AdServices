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
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.android.internal.annotations.GuardedBy;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
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
    private static final String TAG = "JSScriptEngine";
    public static final String ENTRY_POINT_FUNC_NAME = "__rb_entry_point";
    // This needs to be the same for all the instance of the JSScriptEngine otherwise
    // the second webview instance initialization happening on a different thread would cause
    // the app to crash.
    @GuardedBy("JSScriptEngine.class")
    private static HandlerThread sWebViewThread;

    @GuardedBy("JSScriptEngine.class")
    private static Handler sWebViewHandler;

    private final Semaphore mJsExecutionLock = new Semaphore(1);
    // Keeping this instance to enable to destroy the last running instance of the WebView, if any.
    private final AtomicReference<WebView> mWebView = new AtomicReference<>();
    private final WebViewClient mWebViewClient;

    @SuppressLint("SetJavaScriptEnabled")
    public JSScriptEngine(@NonNull Context context) {
        Log.i(TAG, "Creating JSScriptEngine");
        mWebViewClient =
                new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        Log.i(
                                TAG,
                                "WebView status cleaned up. Releasing lock on JS" + " execution");
                        mJsExecutionLock.release();
                    }
                };
        runOnWebViewThread(
                () -> {
                    Log.i(
                            TAG,
                            "Creating new WebView instance in thread "
                                    + Thread.currentThread().getName());
                    WebView wv = new WebView(context);
                    wv.setWebViewClient(mWebViewClient);
                    wv.getSettings().setJavaScriptEnabled(true);
                    mWebView.set(wv);
                });
    }

    @NonNull
    private static synchronized Handler getWebViewHandler() {
        if (sWebViewHandler == null) {
            sWebViewThread = new HandlerThread("WebViewExecutionThread");
            sWebViewThread.start();
            sWebViewHandler = new Handler(sWebViewThread.getLooper());
        }
        return sWebViewHandler;
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
        SettableFuture<String> result = SettableFuture.create();
        try {
            Log.i(TAG, "Trying to acquire lock on JS execution");
            // Don't acquire the lock in the UI thread since that thread is used to run
            // the evalateJavascript callback and it would deadlock if any other request is
            // blocking it.
            mJsExecutionLock.acquire();
            runOnWebViewThread(
                    () -> evaluateOnWebViewThread(jsScript, args, entryFunctionName, result));
        } catch (InterruptedException e) {
            result.setException(new IllegalStateException("Unable to acquire lock"));
        }
        return result;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void evaluateOnWebViewThread(
            @NonNull String jsScript,
            @NonNull List<JSScriptArgument> args,
            @NonNull String entryFunctionName,
            @NonNull SettableFuture<String> result) {
        Objects.requireNonNull(jsScript);
        Objects.requireNonNull(args);
        Objects.requireNonNull(entryFunctionName);
        Objects.requireNonNull(result);

        // Calling resetWebViewStatus before evaluation didn't work, not sure why.
        // Moved it after evaluation
        Log.i(TAG, "Evaluating JS script on thread " + Thread.currentThread().getName());
        String entryPointCall = callEntryPoint(args, entryFunctionName);

        String fullScript = jsScript + "\n" + entryPointCall;
        Log.i(TAG, String.format("Calling WebView for script %s", fullScript));
        mWebView.get()
                .evaluateJavascript(
                        fullScript,
                        jsResult -> {
                            Log.i(TAG, "Done Evaluating JS script result is " + jsResult);
                            Log.i(TAG, "Resetting WebView status");
                            // The release of the lock is done in the WebViewClient
                            // The call to mWebView.load(about:blank) is not blocking so the
                            // unlock of the semaphore needs to be done only when WebView
                            // confirms to our client that the load operation completed.
                            mWebView.get().loadUrl("about:blank");
                            result.set(jsResult);
                        });
    }

    /**
     * @return The JS code for the definition an anonymous function containing the declaration of
     *     the value of {@code args} and the invocation of the given {@code entryFunctionName}.
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
                        "return %s(%s);\n",
                        entryFunctionName,
                        args.stream()
                                .map(JSScriptArgument::name)
                                .collect(Collectors.joining(","))));
        resultBuilder.append("})();\n");

        return resultBuilder.toString();
    }

    /**
     * Interaction with the WebView object need to happen on the same thread used to initialize the
     * WebView otherwise WebView will cause the process to crash. This thread needs to be a looper
     * thread with its own handler.
     */
    private void runOnWebViewThread(@NonNull Runnable task) {
        getWebViewHandler().post(task);
    }
}
