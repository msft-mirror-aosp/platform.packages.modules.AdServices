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

package android.app.sdksandbox;

import static android.app.sdksandbox.SdkSandboxManager.SDK_SANDBOX_SERVICE;

import android.annotation.CallbackExecutor;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemService;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.SurfaceControlViewHost.SurfacePackage;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.Executor;

/**
 * Provides APIs to load {@link android.content.pm.SharedLibraryInfo#TYPE_SDK_PACKAGE SDKs}
 * into SDK sandbox process, and then interact with them.
 *
 * <p>{@code SdkSandbox} is a java process running in a separate uid range. Each app has its own
 * SDK sandbox process.
 *
 * <p>First app needs to declare {@code SDKs} it depends on in it's {@code AndroidManifest.xml}
 * using {@code <uses-sdk-library>} tag. App can only load {@code SDKs} it depends on into the
 * {@code SdkSandbox}.
 *
 * <p>Note: All APIs defined in this class are not stable and subject to change.
 *
 * @see android.content.pm.SharedLibraryInfo#TYPE_SDK_PACKAGE
 * @see
 * <a href="https://developer.android.com/design-for-safety/ads/sdk-runtime">SDK runtime design proposal</a>
 */
@SystemService(SDK_SANDBOX_SERVICE)
public final class SdkSandboxManager {

    /**
     * Use with {@link Context#getSystemService(String)} to retrieve a {@link SdkSandboxManager} for
     * interacting with the SDKs belonging to this client application.
     */
    public static final String SDK_SANDBOX_SERVICE = "sdk_sandbox";

    /** SDK not found.
     *
     * <p>This indicates that client application tried to load a non-existing SDK by calling
     * {@link SdkSandboxManager#loadSdk(String, Bundle, Executor, RemoteSdkCallback)}.
     */
    public static final int LOAD_SDK_NOT_FOUND = 100;
    /** SDK is already loaded.
     *
     * <p>This indicates that client application tried to reload the same SDk by calling
     * {@link SdkSandboxManager#loadSdk(String, Bundle, Executor, RemoteSdkCallback)}
     * after being successfully loaded.
     */
    public static final int LOAD_SDK_ALREADY_LOADED = 101;
    /** Internal error while loading SDK.
     *
     * <p>This indicates a generic internal error happened while applying the call from
     * client application.
     */
    public static final int LOAD_SDK_INTERNAL_ERROR = 500;

    /** @hide */
    @IntDef(prefix = "LOAD_SDK_", value = {
            LOAD_SDK_NOT_FOUND,
            LOAD_SDK_ALREADY_LOADED,
            LOAD_SDK_INTERNAL_ERROR,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface LoadSdkErrorCode {}

    /** Internal error while requesting a {@link SurfacePackage}.
     *
     * <p>This indicates a generic internal error happened while requesting a
     * {@link SurfacePackage}.
     */
    public static final int REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR = 700;

    /** @hide */
    @IntDef(prefix = "REQUEST_SURFACE_PACKAGE_", value = {
            REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface RequestSurfacePackageErrorCode {}

    /**
     * SDK Sandbox is disabled.
     *
     * <p>{@link SdkSandboxManager} APIs are hidden. Attempts at calling them will result in
     * {@link UnsupportedOperationException}.
     */
    public static final int SDK_SANDBOX_STATE_DISABLED = 0;

    /**
     * SDK Sandbox is enabled.
     *
     * <p>App can use {@link SdkSandboxManager} APIs to load {@code SDKs} it depends on into the
     * corresponding {@code SdkSandbox} process.
     */
    public static final int SDK_SANDBOX_STATE_ENABLED_PROCESS_ISOLATION = 2;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "SDK_SANDBOX_STATUS_", value = {
            SDK_SANDBOX_STATE_DISABLED,
            SDK_SANDBOX_STATE_ENABLED_PROCESS_ISOLATION,
    })
    public @interface SdkSandboxState {}

    private final ISdkSandboxManager mService;

    private final Context mContext;

    /** @hide */
    public SdkSandboxManager(@NonNull Context context, @NonNull ISdkSandboxManager binder) {
        mContext = context;
        mService = binder;
    }

    /**
     * Returns current state of the {@code SdkSandbox}.
     */
    @SdkSandboxState
    public static int getSdkSandboxState() {
        return SDK_SANDBOX_STATE_DISABLED;
    }

    /**
     * Load SDK in a SDK sandbox java process.
     *
     * <p>It loads SDK library with {@code sdkPackageName} to a sandbox process
     * asynchronously, caller should be notified through
     * {@link RemoteSdkCallback} {@code callback}.
     *
     * <p>App should already declare {@code SDKs} it depends on in its {@code AndroidManifest}
     * using {@code <use-sdk-library>} tag. App can only load {@code SDKs} it depends on into
     * the {@code SdkSandbox}.
     *
     * <p>When client application loads the first SDK, a new {@code SdkSandbox} process
     * will be created, otherwise other SDKs will be loaded into the same sandbox which
     * already created for the client application.
     *
     * @param sdkPackageName name of the SDK to be loaded
     * @param params the parameters App passes to SDK
     * @param callbackExecutor the {@link Executor} on which to invoke the callback
     * @param callback the {@link RemoteSdkCallback} which will receive events from
     *                 loading and interacting with SDKs
     */
    public void loadSdk(
            @NonNull String sdkPackageName,
            @NonNull Bundle params, @NonNull @CallbackExecutor Executor callbackExecutor,
            @NonNull RemoteSdkCallback callback) {
        RemoteSdkCallbackProxy callbackProxy =
                new RemoteSdkCallbackProxy(callbackExecutor, callback);
        try {
            mService.loadSdk(mContext.getPackageName(), sdkPackageName, params, callbackProxy);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Send a request for a surface package to the sdk.
     *
     * <p>After client application receives a signal about a successful SDK loading
     * by {@link RemoteSdkCallback#onLoadSdkSuccess(Bundle)},
     * it is then able to asynchronously request a {@link SurfacePackage} to render view from SDK.
     * <p>The requested {@link SurfacePackage} is returned to client application through
     * {@link RemoteSdkCallback#onSurfacePackageReady(SurfacePackage, int, Bundle)}.
     *
     * @param sdkPackageName name of the SDK loaded into sdk sandbox
     * @param displayId the id of the logical display to display the surface package
     * @param width the width of the surface package
     * @param height the height of the surface package
     * @param params the parameters which client application passes to SDK
     */
    public void requestSurfacePackage(@NonNull String sdkPackageName,
            int displayId, int width, int height, @NonNull Bundle params) {
        try {
            mService.requestSurfacePackage(sdkPackageName, new Binder(), displayId,
                    width, height, params);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sends a bundle of {@code params} to SDK.
     *
     * @param sdkPackageName a String maps to the SDK library package name, the same name used in
     *              {@link SdkSandboxManager#loadSdk(String, Bundle,Executor, RemoteSdkCallback)}
     * @hide
     */
    public void sendData(@NonNull String sdkPackageName, @NonNull Bundle params) {
        try {
            mService.sendData(sdkPackageName, params);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * A callback for tracking events regarding loading and interacting with SDKs.
     *
     * <p>Callback is registered to {@link SdkSandboxManager} at the moment of loading a new SDK
     * by passing an implementation of this callback to
     * {@link SdkSandboxManager#loadSdk(String, Bundle, Executor, RemoteSdkCallback)}.
     */
    public interface RemoteSdkCallback {
        /**
         * This notifies client application that the requested Sdk is successfully loaded.
         *
         * @param params list of params returned from Sdk to the App.
         */
        void onLoadSdkSuccess(@NonNull Bundle params);
        /**
         * This notifies client application that the requested Sdk is failed to be loaded.
         *
         * @param errorCode int code for the error
         * @param errorMsg a String description of the error
         */
        void onLoadSdkFailure(@LoadSdkErrorCode int errorCode, @NonNull String errorMsg);
        /**
         * This notifies client application that {@link SurfacePackage}
         * is ready to remote render view from the SDK.
         *
         * @param surfacePackage the requested surface package by
         *            {@link SdkSandboxManager#requestSurfacePackage(String, int, int, int, Bundle)}
         * @param surfacePackageId a unique id for the {@link SurfacePackage} {@code surfacePackage}
         * @param params list of params returned from Sdk to the App.
         */
        void onSurfacePackageReady(@NonNull SurfacePackage surfacePackage, int surfacePackageId,
                @NonNull Bundle params);
        /**
         * This notifies client application that requesting {@link SurfacePackage} has failed.
         *
         * @param errorCode int code for the error
         * @param errorMsg a String description of the error
         */
        void onSurfacePackageError(@RequestSurfacePackageErrorCode int errorCode,
                @NonNull String errorMsg);
    }

    /** @hide */
    private static class RemoteSdkCallbackProxy extends IRemoteSdkCallback.Stub {
        private final Executor mExecutor;
        private final RemoteSdkCallback mCallback;

        RemoteSdkCallbackProxy(Executor executor, RemoteSdkCallback callback) {
            mExecutor = executor;
            mCallback = callback;
        }

        @Override
        public void onLoadSdkSuccess(Bundle params) {
            mExecutor.execute(() -> mCallback.onLoadSdkSuccess(params));
        }

        @Override
        public void onLoadSdkFailure(int errorCode, String errorMsg) {
            mExecutor.execute(() -> mCallback.onLoadSdkFailure(errorCode, errorMsg));
        }

        @Override
        public void onSurfacePackageReady(SurfacePackage surfacePackage,
                int surfacePackageId, Bundle params) {
            mExecutor.execute(() ->
                    mCallback.onSurfacePackageReady(surfacePackage, surfacePackageId, params));
        }

        @Override
        public void onSurfacePackageError(int errorCode, String errorMsg) {
            mExecutor.execute(() -> mCallback.onSurfacePackageError(errorCode, errorMsg));
        }
    }

}
