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

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;

import com.android.internal.annotations.GuardedBy;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

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
    public JSScriptEngine(Context context) {
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

    private static synchronized Handler getWebViewHandler() {
        if (sWebViewHandler == null) {
            sWebViewThread = new HandlerThread("WebViewExecutionThread");
            sWebViewThread.start();
            sWebViewHandler = new Handler(sWebViewThread.getLooper());
        }
        return sWebViewHandler;
    }

    /**
     * Executes the JS code in {@code jsScript} and return the result. It will reset the WebView
     * status after evaluating the script.
     *
     * @param jsScript The JS code.
     * @param args A map from the argument name and the JSON serialized value
     * @return The script result as a {@link ListenableFuture}
     */
    public ListenableFuture<String> evaluate(
            String jsScript, @Nullable List<JSScriptArgument> args) {
        SettableFuture<String> result = SettableFuture.create();
        try {
            Log.i(TAG, "Trying to acquire lock on JS execution");
            // Don't acquire the lock in the UI thread since that thread is used to run
            // the evalateJavascript callback and it would deadlock if any other request is
            // blocking it.
            mJsExecutionLock.acquire();
            runOnWebViewThread(() -> evaluateOnWebViewThread(jsScript, args, result));
        } catch (InterruptedException e) {
            result.setException(new IllegalStateException("Unable to acquire lock"));
        }
        return result;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void evaluateOnWebViewThread(
            String jsScript, @Nullable List<JSScriptArgument> args, SettableFuture<String> result) {
        // Calling resetWebViewStatus before evaluation didn't work, not sure why.
        // Moved it after evaluation
        Log.i(TAG, "Evaluating JS script on thread " + Thread.currentThread().getName());
        StringBuilder jsParamsDeclaration = new StringBuilder();
        if (args != null) {
            for (JSScriptArgument arg : args) {
                // Avoiding to use addJavaScriptInterface because too expensive, just
                // declaring the string parameter as part of the script.
                jsParamsDeclaration.append(arg.variableDeclaration());
                jsParamsDeclaration.append("\n");
            }
        }
        String fullScript = jsParamsDeclaration.toString() + jsScript;
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
     * Interaction with the WebView object need to happen on the same thread used to initialize the
     * WebView otherwise WebView will cause the process to crash. This thread needs to be a looper
     * thread with its own handler.
     */
    private void runOnWebViewThread(Runnable task) {
        getWebViewHandler().post(task);
    }
}
