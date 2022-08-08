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

package com.android.sdksandbox;

import android.annotation.SuppressLint;
import android.app.sdksandbox.SandboxedSdkContext;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceControlViewHost;
import android.view.View;
import android.view.WindowManager;

import java.io.PrintWriter;
import java.security.SecureRandom;
import java.util.Random;

/**
 * A holder for loaded code.
 */
@SuppressLint("NewApi") // TODO(b/227329631): remove this after T SDK is finalized
class SandboxedSdkHolder {

    private static final String TAG = "SdkSandbox";

    private boolean mInitialized = false;
    private SandboxedSdkProvider mSdk;
    private Context mContext;

    private DisplayManager mDisplayManager;
    private final Random mRandom = new SecureRandom();
    private final SparseArray<SurfaceControlViewHost.SurfacePackage> mSurfacePackages =
            new SparseArray<>();

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    void init(
            Context context,
            Bundle params,
            ILoadSdkInSandboxCallback callback,
            String sdkProviderClassName,
            ClassLoader loader,
            SandboxedSdkContext sandboxedSdkContext) {
        if (mInitialized) {
            throw new IllegalStateException("Already initialized!");
        }
        mInitialized = true;
        mContext = context;
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        try {
            Class<?> clz = Class.forName(sdkProviderClassName, true, loader);
            mSdk = (SandboxedSdkProvider) clz.getConstructor().newInstance();
            mSdk.attachContext(sandboxedSdkContext);
            mSdk.onLoadSdk(
                    params,
                    mContext.getMainExecutor(),
                    new SandboxedSdkProvider.OnLoadSdkCallback() {
                        @Override
                        public void onLoadSdkFinished(Bundle extraParams) {
                            sendLoadSdkSuccess(callback);
                        }

                        @Override
                        public void onLoadSdkError(String errorMessage) {
                            sendLoadSdkError(errorMessage, callback);
                        }
                    });
        } catch (ClassNotFoundException e) {
            sendLoadSdkError("Could not find class: " + sdkProviderClassName, callback);
        } catch (Exception e) {
            sendLoadSdkError("Could not instantiate SandboxedSdkProvider: " + e, callback);
        } catch (Throwable e) {
            sendLoadSdkError("Error thrown during init: " + e, callback);
        }
    }

    void unloadSdk() {
        mSdk.beforeUnloadSdk();
    }

    void dump(PrintWriter writer) {
        writer.print("mInitialized: " + mInitialized);
        final String sdkClass = mSdk == null ? "null" : mSdk.getClass().getName();
        writer.println(" mSdk class: " + sdkClass);
    }

    private void sendLoadSdkSuccess(ILoadSdkInSandboxCallback callback) {
        try {
            callback.onLoadSdkSuccess(new Bundle(), new SdkSandboxCallbackImpl());
        } catch (RemoteException e) {
            Log.e(TAG, "Could not send onLoadSdkSuccess: " + e);
        }
    }

    private void sendSurfacePackageError(
            String errorMessage, IRequestSurfacePackageFromSdkCallback callback) {
        try {
            callback.onSurfacePackageError(
                    IRequestSurfacePackageFromSdkCallback.SURFACE_PACKAGE_INTERNAL_ERROR,
                    errorMessage);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not send onSurfacePackageError: " + e);
        }
    }

    private void sendLoadSdkError(String errorMessage, ILoadSdkInSandboxCallback callback) {
        try {
            callback.onLoadSdkError(
                    ILoadSdkInSandboxCallback.LOAD_SDK_PROVIDER_INIT_ERROR, errorMessage);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not send onLoadSdkError: " + e);
        }
    }

    private void sendDataReceivedSuccess(Bundle params, IDataReceivedCallback callback) {
        try {
            callback.onDataReceivedSuccess(params);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not send onDataReceivedSuccess: " + e);
        }
    }

    private void sendDataReceivedError(String errorMessage, IDataReceivedCallback callback) {
        try {
            callback.onDataReceivedError(
                    IDataReceivedCallback.DATA_RECEIVED_INTERNAL_ERROR, errorMessage);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not send onDataReceivedError: " + e);
        }
    }

    private int allocateSurfacePackageId(SurfaceControlViewHost.SurfacePackage surfacePackage) {
        synchronized (mSurfacePackages) {
            for (int i = 0; i < 32; i++) {
                int id = mRandom.nextInt();
                if (!mSurfacePackages.contains(id)) {
                    mSurfacePackages.put(id, surfacePackage);
                    return id;
                }
            }
            throw new IllegalStateException("Could not allocate surfacePackageId");
        }
    }

    private class SdkSandboxCallbackImpl
            extends ISdkSandboxManagerToSdkSandboxCallback.Stub {

        @Override
        public void onSurfacePackageRequested(
                IBinder token,
                int displayId,
                int width,
                int height,
                Bundle params,
                IRequestSurfacePackageFromSdkCallback callback) {
            try {
                Context displayContext = mContext.createDisplayContext(
                        mDisplayManager.getDisplay(displayId));
                // TODO(b/209009304): Support other window contexts?
                Context windowContext = displayContext.createWindowContext(
                        WindowManager.LayoutParams.TYPE_APPLICATION_PANEL, null);
                // Creating a SurfaceControlViewHost needs to done on the handler thread.
                mHandler.post(
                        () -> {
                            try {
                                final View view =
                                        mSdk.getView(windowContext, params, width, height);
                                SurfaceControlViewHost host =
                                        new SurfaceControlViewHost(
                                                windowContext,
                                                mDisplayManager.getDisplay(displayId),
                                                token);
                                host.setView(view, width, height);
                                SurfaceControlViewHost.SurfacePackage surfacePackage =
                                        host.getSurfacePackage();
                                int surfacePackageId = allocateSurfacePackageId(surfacePackage);
                                callback.onSurfacePackageReady(
                                        surfacePackage, surfacePackageId, params);
                            } catch (RemoteException e) {
                                Log.e(TAG, "Could not send onSurfacePackageReady", e);
                            } catch (Throwable e) {
                                sendSurfacePackageError(
                                        "Error thrown while getting surface package: " + e,
                                        callback);
                            }
                        });
            } catch (Throwable e) {
                sendSurfacePackageError(
                        "Error thrown while getting surface package: " + e, callback);
            }
        }

        @Override
        public void onDataReceived(Bundle data, IDataReceivedCallback callback) {
            mSdk.onDataReceived(
                    data,
                    new SandboxedSdkProvider.DataReceivedCallback() {
                        @Override
                        public void onDataReceivedSuccess(Bundle params) {
                            sendDataReceivedSuccess(params, callback);
                        }

                        @Override
                        public void onDataReceivedError(String errorMessage) {
                            sendDataReceivedError(errorMessage, callback);
                        }
                    });
        }
    }
}
