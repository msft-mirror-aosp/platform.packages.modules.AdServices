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

    /**
     * SDK not found.
     *
     * <p>This indicates that client application tried to load a non-existing SDK by calling {@link
     * SdkSandboxManager#loadSdk(String, Bundle, Executor, LoadSdkCallback)}.
     */
    public static final int LOAD_SDK_NOT_FOUND = 100;
    /**
     * SDK is already loaded.
     *
     * <p>This indicates that client application tried to reload the same SDk by calling {@link
     * SdkSandboxManager#loadSdk(String, Bundle, Executor, LoadSdkCallback)} after being
     * successfully loaded.
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
     * Internal error while performing {@link SdkSandboxManager#sendData(String, Bundle, Executor,
     * SendDataCallback)}.
     *
     * <p>This indicates a generic internal error happened while requesting to send data to an SDK.
     */
    public static final int SEND_DATA_INTERNAL_ERROR = 800;

    /** @hide */
    @IntDef(
            prefix = "SEND_DATA_",
            value = {SEND_DATA_INTERNAL_ERROR})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SendDataErrorCode {}

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
        return SDK_SANDBOX_STATE_ENABLED_PROCESS_ISOLATION;
    }

    /**
     * Load SDK in a SDK sandbox java process.
     *
     * <p>It loads SDK library with {@code sdkName} to a sandbox process asynchronously, caller
     * should be notified through {@link LoadSdkCallback} {@code callback}.
     *
     * <p>App should already declare {@code SDKs} it depends on in its {@code AndroidManifest} using
     * {@code <use-sdk-library>} tag. App can only load {@code SDKs} it depends on into the {@code
     * SdkSandbox}.
     *
     * <p>When client application loads the first SDK, a new {@code SdkSandbox} process will be
     * created, otherwise other SDKs will be loaded into the same sandbox which already created for
     * the client application.
     *
     * @param sdkName name of the SDK to be loaded
     * @param params the parameters App passes to SDK
     * @param callbackExecutor the {@link Executor} on which to invoke the callback
     * @param callback the {@link LoadSdkCallback} which will receive events from loading SDKs
     */
    public void loadSdk(
            @NonNull String sdkName,
            @NonNull Bundle params,
            @NonNull @CallbackExecutor Executor callbackExecutor,
            @NonNull LoadSdkCallback callback) {
        final LoadSdkCallbackProxy callbackProxy =
                new LoadSdkCallbackProxy(callbackExecutor, callback);
        try {
            mService.loadSdk(mContext.getPackageName(), sdkName, params, callbackProxy);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Send a request for a surface package to the sdk.
     *
     * <p>After client application receives a signal about a successful SDK loading by {@link
     * LoadSdkCallback#onLoadSdkSuccess(Bundle)}, it is then able to asynchronously request a {@link
     * SurfacePackage} to render view from SDK.
     *
     * <p>The requested {@link SurfacePackage} is returned to client application through {@link
     * RequestSurfacePackageCallback#onSurfacePackageReady(SurfacePackage, int, Bundle)}.
     *
     * @param sdkName name of the SDK loaded into sdk sandbox
     * @param displayId the id of the logical display to display the surface package
     * @param width the width of the surface package
     * @param height the height of the surface package
     * @param params the parameters which client application passes to SDK
     * @param callbackExecutor the {@link Executor} on which to invoke the callback
     * @param callback the {@link RequestSurfacePackageCallback} which will receive results of
     *     requesting surface packages from SDKs.
     */
    public void requestSurfacePackage(
            @NonNull String sdkName,
            int displayId,
            int width,
            int height,
            @NonNull Bundle params,
            @NonNull @CallbackExecutor Executor callbackExecutor,
            @NonNull RequestSurfacePackageCallback callback) {
        final RequestSurfacePackageCallbackProxy callbackProxy =
                new RequestSurfacePackageCallbackProxy(callbackExecutor, callback);
        try {
            mService.requestSurfacePackage(
                    mContext.getPackageName(),
                    sdkName,
                    new Binder(),
                    displayId,
                    width,
                    height,
                    params,
                    callbackProxy);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sends a bundle of {@code data} to SDK.
     *
     * <p>After the client application receives a signal about a successful SDK loading by {@link
     * LoadSdkCallback#onLoadSdkSuccess(Bundle)}, it is then able to asynchronously
     * request to send any data to the SDK in the sandbox. if the SDK is not loaded,
     * {@link SecurityException} is thrown.
     *
     * @param sdkName name of the SDK loaded into sdk sandbox, the same name used in {@link
     *     SdkSandboxManager#loadSdk(String, Bundle, Executor, LoadSdkCallback)}
     * @param data the data to be sent to the SDK represented in the form of a {@link Bundle}
     * @param callbackExecutor the {@link Executor} on which to invoke the callback
     * @param callback the {@link SendDataCallback} which will receive events from loading and
     *     interacting with SDKs. The SDK may also send back data through
     *     {@link SendDataCallback#onSendDataSuccess(Bundle)}
     */
    public void sendData(
            @NonNull String sdkName,
            @NonNull Bundle data,
            @NonNull @CallbackExecutor Executor callbackExecutor,
            @NonNull SendDataCallback callback) {
        SendDataCallbackProxy callbackProxy = new SendDataCallbackProxy(callbackExecutor, callback);
        try {
            mService.sendData(mContext.getPackageName(), sdkName, data, callbackProxy);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * A callback for tracking events regarding loading and interacting with SDKs.
     *
     * <p>Callback is registered to {@link SdkSandboxManager} at the moment of loading a new SDK by
     * passing an implementation of this callback to {@link SdkSandboxManager#loadSdk(String,
     * Bundle, Executor, LoadSdkCallback)}.
     */
    public interface LoadSdkCallback {
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
    }

    /**
     * A callback for tracking a request for a surface package from an SDK.
     *
     * <p>An implementation of this callback is sent to {@link
     * SdkSandboxManager#requestSurfacePackage(String, int, int, int, Bundle, Executor,
     * RequestSurfacePackageCallback)}.
     */
    public interface RequestSurfacePackageCallback {
        /**
         * This notifies client application that {@link SurfacePackage} is ready to remote render
         * view from the SDK.
         *
         * @param surfacePackage the requested surface package by {@link
         *     SdkSandboxManager#requestSurfacePackage(String, int, int, int, Bundle, Executor,
         *     RequestSurfacePackageCallback)}
         * @param surfacePackageId a unique id for the {@link SurfacePackage} {@code surfacePackage}
         * @param params list of params returned from Sdk to the App.
         */
        void onSurfacePackageReady(
                @NonNull SurfacePackage surfacePackage,
                int surfacePackageId,
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

    /**
     * A callback for tracking sending of data to an SDK.
     */
    public interface SendDataCallback {
        /**
         * This notifies the client application that sending data to the SDK has completed
         * successfully.
         */
        void onSendDataSuccess(@NonNull Bundle params);

        /**
         * This notifies the client application that sending data to an SDK has failed.
         *
         * @param errorCode int code for the error
         * @param errorMsg a String description of the error
         */
        void onSendDataError(@SendDataErrorCode int errorCode, @NonNull String errorMsg);
    }

    /** @hide */
    private static class LoadSdkCallbackProxy extends ILoadSdkCallback.Stub {
        private final Executor mExecutor;
        private final LoadSdkCallback mCallback;

        LoadSdkCallbackProxy(Executor executor, LoadSdkCallback callback) {
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
    }

    /** @hide */
    private static class RequestSurfacePackageCallbackProxy
            extends IRequestSurfacePackageCallback.Stub {
        private final Executor mExecutor;
        private final RequestSurfacePackageCallback mCallback;

        RequestSurfacePackageCallbackProxy(
                Executor executor, RequestSurfacePackageCallback callback) {
            mExecutor = executor;
            mCallback = callback;
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

    /** @hide */
    private static class SendDataCallbackProxy extends ISendDataCallback.Stub {
        private final Executor mExecutor;
        private final SendDataCallback mCallback;

        SendDataCallbackProxy(Executor executor, SendDataCallback callback) {
            mExecutor = executor;
            mCallback = callback;
        }

        @Override
        public void onSendDataSuccess(Bundle params) {
            mExecutor.execute(() -> mCallback.onSendDataSuccess(params));
        }

        @Override
        public void onSendDataError(int errorCode, String errorMsg) {
            mExecutor.execute(() -> mCallback.onSendDataError(errorCode, errorMsg));
        }
    }
}
