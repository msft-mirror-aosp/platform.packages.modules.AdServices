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

package com.android.sdksandboxclient;

import android.app.Activity;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.SdkSandboxManager.LoadSdkCallback;
import android.app.sdksandbox.SdkSandboxManager.RequestSurfacePackageCallback;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String SDK_NAME = "com.android.sdksandboxcode";

    private boolean mSdkLoaded = false;
    private SdkSandboxManager mSdkSandboxManager;

    private Button mLoadButton;
    private Button mRenderButton;
    private SurfaceView mRenderedView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSdkSandboxManager = getApplicationContext().getSystemService(
                SdkSandboxManager.class);

        mRenderedView = findViewById(R.id.rendered_view);
        mRenderedView.setZOrderOnTop(true);
        mRenderedView.setVisibility(View.INVISIBLE);

        mLoadButton = findViewById(R.id.load_code_button);
        mRenderButton = findViewById(R.id.request_surface_button);
        registerLoadSdkProviderButton();
        registerLoadSurfacePackageButton();
    }

    private void registerLoadSdkProviderButton() {
        mLoadButton.setOnClickListener(
                v -> {
                    Bundle params = new Bundle();
                    final LoadSdkCallbackImpl callback = new LoadSdkCallbackImpl();
                    mSdkSandboxManager.loadSdk(SDK_NAME, params, Runnable::run, callback);
                });
    }

    private void registerLoadSurfacePackageButton() {
        mRenderButton.setOnClickListener(
                v -> {
                    if (mSdkLoaded) {
                        new Handler(Looper.getMainLooper())
                                .post(
                                        () ->
                                                mSdkSandboxManager.requestSurfacePackage(
                                                        SDK_NAME,
                                                        getDisplay().getDisplayId(),
                                                        mRenderedView.getWidth(),
                                                        mRenderedView.getHeight(),
                                                        new Bundle(),
                                                        Runnable::run,
                                                        new RequestSurfacePackageCallbackImpl()));
                    } else {
                        makeToast("Sdk is not loaded");
                    }
                });
    }

    private void makeToast(String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
    }

    private class LoadSdkCallbackImpl implements LoadSdkCallback {
        @Override
        public void onLoadSdkSuccess(Bundle bundle) {
            mSdkLoaded = true;
            makeToast("Loaded successfully!");
        }

        @Override
        public void onLoadSdkFailure(int errorCode, String errorMessage) {
            makeToast("Failed: " + errorMessage);
        }
    }

    private class RequestSurfacePackageCallbackImpl implements RequestSurfacePackageCallback {
        @Override
        public void onSurfacePackageReady(SurfaceControlViewHost.SurfacePackage surfacePackage,
                int i, Bundle bundle) {
            new Handler(Looper.getMainLooper()).post(() -> {
                mRenderedView.setChildSurfacePackage(surfacePackage);
                mRenderedView.setVisibility(View.VISIBLE);
            });
            makeToast("Rendered surface view");
        }

        @Override
        public void onSurfacePackageError(int errorCode, String errorMessage) {
            makeToast("Failed: " + errorMessage);
        }
    }
}
