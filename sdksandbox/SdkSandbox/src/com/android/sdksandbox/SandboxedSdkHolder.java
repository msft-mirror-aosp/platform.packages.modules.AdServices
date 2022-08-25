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

import android.app.sdksandbox.ISdkToServiceCallback;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxedSdk;
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
class SandboxedSdkHolder {

    private static final String TAG = "SdkSandbox";
    private static final int FAILED_LATENCY = -1;

    private boolean mInitialized = false;
    private SandboxedSdkProvider mSdk;
    private Context mContext;
    private SandboxedSdk mSandboxedSdk;

    private DisplayManager mDisplayManager;
    private final Random mRandom = new SecureRandom();
    private final SparseArray<SurfaceControlViewHost.SurfacePackage> mSurfacePackages =
            new SparseArray<>();

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private SdkSandboxServiceImpl.Injector mInjector;

    void init(
            Bundle params,
            ILoadSdkInSandboxCallback callback,
            String sdkProviderClassName,
            ClassLoader loader,
            SandboxedSdkContext sandboxedSdkContext,
            SdkSandboxServiceImpl.Injector injector,
            SandboxLatencyInfo sandboxLatencyInfo,
            ISdkToServiceCallback sdkToServiceCallback) {
        if (mInitialized) {
            throw new IllegalStateException("Already initialized!");
        }
        mInitialized = true;
        mContext = sandboxedSdkContext.getBaseContext();
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        mInjector = injector;
        /**
         * The code inside the try block is *owned* by the sandbox, and latency is measured
         * separately to the code that is owned by the SDK
         */
        try {
            Class<?> clz = Class.forName(sdkProviderClassName, true, loader);
            mSdk = (SandboxedSdkProvider) clz.getConstructor().newInstance();
            mSdk.attachContext(sandboxedSdkContext);
            mSdk.attachSdkSandboxController(
                    new SdkSandboxControllerImpl(
                            sandboxedSdkContext.getClientPackageName(), sdkToServiceCallback));
        } catch (ClassNotFoundException e) {
            sandboxLatencyInfo.setSandboxStatus(
                    SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SANDBOX);
            sendLoadSdkError(
                    new LoadSdkException(
                            ILoadSdkInSandboxCallback.LOAD_SDK_INTERNAL_ERROR,
                            "Could not find class: " + sdkProviderClassName),
                    sandboxLatencyInfo,
                    callback);
        } catch (Exception e) {
            sandboxLatencyInfo.setSandboxStatus(
                    SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SANDBOX);
            sendLoadSdkError(
                    new LoadSdkException(
                            ILoadSdkInSandboxCallback.LOAD_SDK_INTERNAL_ERROR,
                            "Could not instantiate SandboxedSdkProvider: " + e),
                    sandboxLatencyInfo,
                    callback);
        } catch (Throwable e) {
            sandboxLatencyInfo.setSandboxStatus(
                    SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SANDBOX);
            sendLoadSdkError(
                    new LoadSdkException(
                            ILoadSdkInSandboxCallback.LOAD_SDK_INTERNAL_ERROR,
                            "Error thrown during init: " + e),
                    sandboxLatencyInfo,
                    callback);
        }

        mHandler.post(
                () -> {
                    /** The code inside the try block runs in SDK and is measured latency of SDK */
                    try {
                        sandboxLatencyInfo.setTimeSandboxCalledSdk(mInjector.getCurrentTime());
                        mSandboxedSdk = mSdk.onLoadSdk(params);
                        sandboxLatencyInfo.setTimeSdkCallCompleted(mInjector.getCurrentTime());
                        sendLoadSdkSuccess(mSandboxedSdk, sandboxLatencyInfo, callback);
                    } catch (LoadSdkException exception) {
                        sandboxLatencyInfo.setTimeSdkCallCompleted(mInjector.getCurrentTime());
                        sandboxLatencyInfo.setSandboxStatus(
                                SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SDK);
                        sendLoadSdkError(exception, sandboxLatencyInfo, callback);
                    } catch (RuntimeException exception) {
                        sandboxLatencyInfo.setTimeSdkCallCompleted(mInjector.getCurrentTime());
                        sandboxLatencyInfo.setSandboxStatus(
                                SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SDK);
                        sendLoadSdkError(
                                new LoadSdkException(exception, new Bundle()),
                                sandboxLatencyInfo,
                                callback);
                    }
                });
    }

    void unloadSdk() {
        mSdk.beforeUnloadSdk();
    }

    void dump(PrintWriter writer) {
        writer.print("mInitialized: " + mInitialized);
        final String sdkClass = mSdk == null ? "null" : mSdk.getClass().getName();
        writer.println(" mSdk class: " + sdkClass);
    }

    private void sendLoadSdkSuccess(
            SandboxedSdk sandboxedSdk,
            SandboxLatencyInfo sandboxLatencyInfo,
            ILoadSdkInSandboxCallback callback) {
        sandboxLatencyInfo.setTimeSandboxCalledSystemServer(mInjector.getCurrentTime());
        try {
            callback.onLoadSdkSuccess(
                    sandboxedSdk, new SdkSandboxCallbackImpl(), sandboxLatencyInfo);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not send onLoadSdkSuccess: " + e);
        }
    }

    private void sendSurfacePackageError(
            String errorMessage,
            long timeSandboxReceivedCallFromSystemServer,
            // if true failure happened at SDK, else failure happened at sandbox
            boolean failedAtSdk,
            Bundle sandboxLatencies,
            IRequestSurfacePackageFromSdkCallback callback) {
        try {
            final long timeSandboxCalledSystemServer = mInjector.getCurrentTime();
            sandboxLatencies.putInt(
                    IRequestSurfacePackageFromSdkCallback.LATENCY_SANDBOX,
                    (int)
                            (timeSandboxCalledSystemServer
                                    - timeSandboxReceivedCallFromSystemServer));
            callback.onSurfacePackageError(
                    IRequestSurfacePackageFromSdkCallback.SURFACE_PACKAGE_INTERNAL_ERROR,
                    errorMessage,
                    timeSandboxCalledSystemServer,
                    failedAtSdk,
                    sandboxLatencies);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not send onSurfacePackageError: " + e);
        }
    }

    private void sendLoadSdkError(
            LoadSdkException exception,
            SandboxLatencyInfo sandboxLatencyInfo,
            ILoadSdkInSandboxCallback callback) {
        sandboxLatencyInfo.setTimeSandboxCalledSystemServer(mInjector.getCurrentTime());
        try {
            callback.onLoadSdkError(exception, sandboxLatencyInfo);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not send onLoadSdkError: " + e);
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
                long timeSystemServerCalledSandbox,
                Bundle params,
                IRequestSurfacePackageFromSdkCallback callback) {
            final long timeSandboxReceivedCallFromSystemServer = mInjector.getCurrentTime();
            final Bundle sandboxLatencies = new Bundle();

            sandboxLatencies.putInt(
                    IRequestSurfacePackageFromSdkCallback.LATENCY_SYSTEM_SERVER_TO_SANDBOX,
                    (int)
                            (timeSandboxReceivedCallFromSystemServer
                                    - timeSystemServerCalledSandbox));

            try {
                Context displayContext = mContext.createDisplayContext(
                        mDisplayManager.getDisplay(displayId));
                // TODO(b/209009304): Support other window contexts?
                Context windowContext = displayContext.createWindowContext(
                        WindowManager.LayoutParams.TYPE_APPLICATION_PANEL, null);
                // Creating a SurfaceControlViewHost needs to done on the handler thread.
                mHandler.post(
                        () -> {
                            final View view;
                            final long timeSandboxCalledSdk = mInjector.getCurrentTime();
                            try {
                                view = mSdk.getView(windowContext, params, width, height);
                            } catch (Throwable e) {
                                sandboxLatencies.putInt(
                                        IRequestSurfacePackageFromSdkCallback.LATENCY_SDK,
                                        (int) (mInjector.getCurrentTime() - timeSandboxCalledSdk));
                                sendSurfacePackageError(
                                        "Error thrown while getting surface package from SDK: " + e,
                                        timeSandboxReceivedCallFromSystemServer,
                                        /*failedAtSdk=*/ true,
                                        sandboxLatencies,
                                        callback);
                                return;
                            }
                            final int latencySdk =
                                    (int) (mInjector.getCurrentTime() - timeSandboxCalledSdk);
                            sandboxLatencies.putInt(
                                    IRequestSurfacePackageFromSdkCallback.LATENCY_SDK, latencySdk);
                            try {
                                SurfaceControlViewHost host =
                                        new SurfaceControlViewHost(
                                                windowContext,
                                                mDisplayManager.getDisplay(displayId),
                                                token);
                                host.setView(view, width, height);
                                SurfaceControlViewHost.SurfacePackage surfacePackage =
                                        host.getSurfacePackage();
                                int surfacePackageId = allocateSurfacePackageId(surfacePackage);

                                final long timeSandboxCalledSystemServer =
                                        mInjector.getCurrentTime();

                                sandboxLatencies.putInt(
                                        IRequestSurfacePackageFromSdkCallback.LATENCY_SANDBOX,
                                        (int)
                                                (timeSandboxCalledSystemServer
                                                        - timeSandboxReceivedCallFromSystemServer)
                                                - latencySdk);

                                callback.onSurfacePackageReady(
                                        surfacePackage,
                                        surfacePackageId,
                                        timeSandboxCalledSystemServer,
                                        params,
                                        sandboxLatencies);
                            } catch (RemoteException e) {
                                Log.e(TAG, "Could not send onSurfacePackageReady", e);
                            } catch (Throwable e) {
                                sendSurfacePackageError(
                                        "Error thrown while getting surface package: " + e,
                                        timeSandboxReceivedCallFromSystemServer,
                                        /*failedAtSdk=*/ false,
                                        sandboxLatencies,
                                        callback);
                            }
                        });
            } catch (Throwable e) {
                sendSurfacePackageError(
                        "Error thrown while getting surface package: " + e,
                        timeSandboxReceivedCallFromSystemServer,
                        /*failedAtSdk=*/ false,
                        sandboxLatencies,
                        callback);
            }
        }

    }
}
