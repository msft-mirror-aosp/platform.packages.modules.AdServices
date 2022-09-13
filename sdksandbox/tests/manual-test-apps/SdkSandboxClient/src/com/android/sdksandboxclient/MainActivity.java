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

import static android.app.sdksandbox.SdkSandboxManager.EXTRA_DISPLAY_ID;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_HEIGHT_IN_PIXELS;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_HOST_TOKEN;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_SURFACE_PACKAGE;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_WIDTH_IN_PIXELS;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.RequestSurfacePackageException;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.interfaces.ISdkApi;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.text.InputType;
import android.util.Log;
import android.view.SurfaceControlViewHost.SurfacePackage;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;

public class MainActivity extends Activity {
    private static final String SDK_NAME = "com.android.sdksandboxcode";
    private static final String TAG = "SdkSandboxClientMainActivity";

    private boolean mSdkLoaded = false;
    private SdkSandboxManager mSdkSandboxManager;

    private Button mLoadButton;
    private Button mRenderButton;
    private Button mCreateFileButton;

    private SurfaceView mRenderedView;

    private SandboxedSdk mSandboxedSdk;

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
        mCreateFileButton = findViewById(R.id.create_file_button);

        registerLoadSdkProviderButton();
        registerLoadSurfacePackageButton();
        registerCreateFileButton();
    }

    private void registerLoadSdkProviderButton() {
        mLoadButton.setOnClickListener(
                v -> {
                    if (!mSdkLoaded) {
                        // Register for sandbox death event.
                        mSdkSandboxManager.addSdkSandboxProcessDeathCallback(
                                Runnable::run, () -> makeToast("Sdk Sandbox process died"));

                        Bundle params = new Bundle();
                        OutcomeReceiver<SandboxedSdk, LoadSdkException> receiver =
                                new OutcomeReceiver<SandboxedSdk, LoadSdkException>() {
                                    @Override
                                    public void onResult(SandboxedSdk sandboxedSdk) {
                                        mSdkLoaded = true;

                                        mSandboxedSdk = sandboxedSdk;

                                        makeToast("Loaded successfully!");
                                        mLoadButton.setText("Unload SDK");
                                    }

                                    @Override
                                    public void onError(LoadSdkException error) {
                                        makeToast("Failed: " + error);
                                    }
                                };
                        mSdkSandboxManager.loadSdk(SDK_NAME, params, Runnable::run, receiver);
                    } else {
                        mSdkSandboxManager.unloadSdk(SDK_NAME);
                        mLoadButton.setText("Load SDK");
                        mSdkLoaded = false;
                    }
                });
    }

    private void registerLoadSurfacePackageButton() {
        OutcomeReceiver<Bundle, RequestSurfacePackageException> receiver =
                new OutcomeReceiver<Bundle, RequestSurfacePackageException>() {
                    @Override
                    public void onResult(@NonNull Bundle result) {
                        new Handler(Looper.getMainLooper())
                                .post(
                                        () -> {
                                            SurfacePackage surfacePackage =
                                                    result.getParcelable(
                                                            EXTRA_SURFACE_PACKAGE,
                                                            SurfacePackage.class);
                                            mRenderedView.setChildSurfacePackage(surfacePackage);
                                            mRenderedView.setVisibility(View.VISIBLE);
                                        });
                        makeToast("Rendered surface view");
                    }

                    @Override
                    public void onError(@NonNull RequestSurfacePackageException error) {
                        makeToast("Failed: " + error.getMessage());
                        Log.e(TAG, error.getMessage(), error);
                    }
                };
        mRenderButton.setOnClickListener(
                v -> {
                    if (mSdkLoaded) {
                        new Handler(Looper.getMainLooper())
                                .post(
                                        () -> {
                                            Bundle params = new Bundle();
                                            params.putInt(
                                                    EXTRA_WIDTH_IN_PIXELS,
                                                    mRenderedView.getWidth());
                                            params.putInt(
                                                    EXTRA_HEIGHT_IN_PIXELS,
                                                    mRenderedView.getHeight());
                                            params.putInt(
                                                    EXTRA_DISPLAY_ID, getDisplay().getDisplayId());
                                            params.putBinder(
                                                    EXTRA_HOST_TOKEN, mRenderedView.getHostToken());
                                            mSdkSandboxManager.requestSurfacePackage(
                                                    SDK_NAME, params, Runnable::run, receiver);
                                        });
                    } else {
                        makeToast("Sdk is not loaded");
                    }
                });
    }

    private void registerCreateFileButton() {
        mCreateFileButton.setOnClickListener(
                v -> {
                    if (!mSdkLoaded) {
                        makeToast("Sdk is not loaded");
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Set size in MB");
                    final EditText input = new EditText(this);
                    input.setInputType(InputType.TYPE_CLASS_NUMBER);
                    builder.setView(input);
                    builder.setPositiveButton(
                            "Create",
                            (dialog, which) -> {
                                int sizeInMb = -1;
                                try {
                                    sizeInMb = Integer.parseInt(input.getText().toString());
                                } catch (Exception ignore) {
                                }
                                if (sizeInMb <= 0) {
                                    makeToast("Please provide positive integer value");
                                    return;
                                }
                                IBinder binder = mSandboxedSdk.getInterface();
                                ISdkApi sdkApi = ISdkApi.Stub.asInterface(binder);
                                try {
                                    String response = sdkApi.createFile(sizeInMb);
                                    makeToast(response);
                                } catch (RemoteException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                    builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
                    builder.show();
                });
    }

    private void makeToast(String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
    }
}
