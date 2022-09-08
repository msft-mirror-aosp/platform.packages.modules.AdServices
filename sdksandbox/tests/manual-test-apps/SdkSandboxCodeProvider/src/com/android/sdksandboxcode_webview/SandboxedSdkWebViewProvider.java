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

package com.android.sdksandboxcode_webview;

import android.app.sdksandbox.SandboxedSdkContext;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class SandboxedSdkWebViewProvider extends SandboxedSdkProvider {

    private SandboxedSdkContext mContext;

    @Override
    public void initSdk(SandboxedSdkContext context, Bundle params,
            Executor executor, InitSdkCallback callback) {
        mContext = context;
        callback.onInitSdkFinished(null);
    }

    @Override
    public View getView(Context windowContext, Bundle params) {
        final CountDownLatch latch = new CountDownLatch(1);
        final TestWebView webview = new TestWebView();
        try {
            webview.generate(windowContext, latch);
            latch.await(2, TimeUnit.SECONDS);
            return webview.getWebView();
        } catch (Exception e) {
            return null;
        }
    }
    @Override
    public void onExtraDataReceived(Bundle extraData) {
    }


    private class TestWebView {

        WebView mWebView = null;

        private TestWebView() {
        }

        private void initializeSettings(WebSettings settings) {
            settings.setJavaScriptEnabled(true);

            settings.setGeolocationEnabled(true);
            settings.setSupportZoom(true);
            settings.setDatabaseEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);

            // Default layout behavior for chrome on android.
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
        }
        WebView getWebView() {
            return mWebView;
        }

        void generate(Context context, CountDownLatch latch) {
            WebView wv = new WebView(context);
            WebSettings settings = wv.getSettings();
            initializeSettings(settings);
            wv.loadUrl("https://www.google.com");
            mWebView = wv;
            latch.countDown();
        }
    }
}
