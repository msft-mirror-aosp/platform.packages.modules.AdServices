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
import android.annotation.RequiresPermission;
import android.annotation.SystemService;
import android.annotation.TestApi;
import android.content.Context;
import android.content.pm.SharedLibraryInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.view.SurfaceControlViewHost.SurfacePackage;

import com.android.internal.annotations.GuardedBy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
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
     * SdkSandboxManager#loadSdk(String, Bundle, Executor, OutcomeReceiver)}.
     */
    public static final int LOAD_SDK_NOT_FOUND = 100;
    /**
     * SDK is already loaded.
     *
     * <p>This indicates that client application tried to reload the same SDk by calling {@link
     * SdkSandboxManager#loadSdk(String, Bundle, Executor, OutcomeReceiver)} after being
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
     * Internal error while performing {@link SdkSandboxManager#sendData}.
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

    @GuardedBy("mLifecycleCallbacks")
    private final ArrayList<SdkSandboxLifecycleCallbackProxy> mLifecycleCallbacks =
            new ArrayList<>();

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
     * Stop the SDK sandbox process corresponding to the app.
     *
     * @hide
     */
    @TestApi
    @RequiresPermission("com.android.app.sdksandbox.permission.STOP_SDK_SANDBOX")
    public void stopSdkSandbox() {
        try {
            mService.stopSdkSandbox(mContext.getPackageName());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Add a callback which gets registered for sdk sandbox lifecycle events, such as sdk sandbox
     * death. If the sandbox has not yet been created when this is called, the request will be
     * stored until a sandbox is created, at which point it is activated for that sandbox. Multiple
     * callbacks can be added to detect death.
     *
     * @param callbackExecutor the {@link Executor} on which to invoke the callback
     * @param callback the {@link SdkSandboxLifecycleCallback} which will receive sdk sandbox
     *     lifecycle events.
     */
    public void addSdkSandboxLifecycleCallback(
            @NonNull @CallbackExecutor Executor callbackExecutor,
            @NonNull SdkSandboxLifecycleCallback callback) {
        if (callbackExecutor == null) {
            throw new IllegalArgumentException("executor cannot be null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback cannot be null");
        }

        synchronized (mLifecycleCallbacks) {
            final SdkSandboxLifecycleCallbackProxy callbackProxy =
                    new SdkSandboxLifecycleCallbackProxy(callbackExecutor, callback);
            try {
                mService.addSdkSandboxLifecycleCallback(
                        mContext.getPackageName(), callbackProxy);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
            mLifecycleCallbacks.add(callbackProxy);
        }
    }

    /**
     * Remove an {@link SdkSandboxLifecycleCallback} that was previously added using {@link
     * SdkSandboxManager#addSdkSandboxLifecycleCallback(Executor, SdkSandboxLifecycleCallback)}
     *
     * @param callback the {@link SdkSandboxLifecycleCallback} which was previously added using
     *     {@link SdkSandboxManager#addSdkSandboxLifecycleCallback(Executor,
     *     SdkSandboxLifecycleCallback)}
     */
    public void removeSdkSandboxLifecycleCallback(
            @NonNull SdkSandboxLifecycleCallback callback) {
        synchronized (mLifecycleCallbacks) {
            for (int i = mLifecycleCallbacks.size() - 1; i >= 0; i--) {
                final SdkSandboxLifecycleCallbackProxy callbackProxy = mLifecycleCallbacks.get(i);
                if (callbackProxy.callback == callback) {
                    try {
                        mService.removeSdkSandboxLifecycleCallback(
                                mContext.getPackageName(), callbackProxy);
                    } catch (RemoteException e) {
                        throw e.rethrowFromSystemServer();
                    }
                    mLifecycleCallbacks.remove(i);
                }
            }
        }
    }

    /**
     * Load SDK in a SDK sandbox java process.
     *
     * <p>It loads SDK library with {@code sdkName} to a sandbox process asynchronously, caller
     * should be notified through {@code receiver}.
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
     * @param executor the {@link Executor} on which to invoke the receiver.
     * @param receiver This either returns a Bundle of params on a successful run, or {@link
     *     LoadSdkException}.
     */
    public void loadSdk(
            @NonNull String sdkName,
            @NonNull Bundle params,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<LoadSdkResponse, LoadSdkException> receiver) {
        final LoadSdkReceiverProxy callbackProxy = new LoadSdkReceiverProxy(executor, receiver);
        try {
            mService.loadSdk(
                    mContext.getPackageName(),
                    sdkName,
                    /*timeAppCalledSystemServer=*/ System.currentTimeMillis(),
                    params,
                    callbackProxy);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Fetches information about Sdks that are loaded in the sandbox.
     *
     * @return List of {@link SharedLibraryInfo} containing all currently loaded sdks
     */
    public @NonNull List<SharedLibraryInfo> getLoadedSdkLibrariesInfo() {
        try {
            return mService.getLoadedSdkLibrariesInfo(mContext.getPackageName());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Unloads an SDK that has been previously loaded by the caller.
     *
     * <p>It is not guaranteed that the memory allocated for this SDK will be freed immediately. All
     * subsequent calls to {@link #sendData(String, Bundle, Executor, OutcomeReceiver)} or {@link
     * #requestSurfacePackage(String, int, int, int, IBinder, Bundle, Executor, OutcomeReceiver)}
     * for the given {@code sdkName} will fail.
     *
     * @param sdkName name of the SDK to be unloaded.
     * @throws IllegalArgumentException if the SDK is not loaded.
     */
    public void unloadSdk(@NonNull String sdkName) {
        try {
            mService.unloadSdk(mContext.getPackageName(), sdkName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Send a request for a surface package to the sdk.
     *
     * <p>After client application receives a signal about a successful SDK loading, and has added a
     * {@link android.view.SurfaceView} to the view hierarchy, it may asynchronously request a
     * {@link SurfacePackage} to render a view from the SDK.
     *
     * <p>The requested {@link SurfacePackage} is returned to client application through {@code
     * receiver}
     *
     * @param sdkName name of the SDK loaded into sdk sandbox
     * @param displayId the id of the logical display to display the surface package
     * @param width the width of the surface package
     * @param height the height of the surface package
     * @param hostToken the token returned by {@link android.view.SurfaceView#getHostToken()} once
     *     the {@link android.view.SurfaceView} has been added to the view hierarchy. Only a
     *     non-null hostToken is accepted to enable ANR reporting.
     * @param params the parameters which client application passes to SDK
     * @param callbackExecutor the {@link Executor} on which to invoke the callback
     * @param receiver This either returns a {@link RequestSurfacePackageResponse} on success, or
     *     {@link RequestSurfacePackageException}.
     */
    public void requestSurfacePackage(
            @NonNull String sdkName,
            int displayId,
            int width,
            int height,
            @NonNull IBinder hostToken,
            @NonNull Bundle params,
            @NonNull @CallbackExecutor Executor callbackExecutor,
            @NonNull
                    OutcomeReceiver<RequestSurfacePackageResponse, RequestSurfacePackageException>
                            receiver) {
        final RequestSurfacePackageReceiverProxy callbackProxy =
                new RequestSurfacePackageReceiverProxy(callbackExecutor, receiver);
        try {
            mService.requestSurfacePackage(
                    mContext.getPackageName(),
                    sdkName,
                    hostToken,
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
     * <p>After the client application receives a signal about a successful SDK load, it is then
     * able to asynchronously request to send any data to the SDK in the sandbox. If the SDK is not
     * loaded, {@link IllegalArgumentException} is thrown.
     *
     * @param sdkName name of the SDK loaded into sdk sandbox, the same name used in {@link
     *     SdkSandboxManager#loadSdk(String, Bundle, Executor, OutcomeReceiver)}
     * @param data the data to be sent to the SDK represented in the form of a {@link Bundle}
     * @param callbackExecutor the {@link Executor} on which to invoke the callback
     * @param receiver the {@link OutcomeReceiver} which will receive events from loading and
     *     interacting with SDKs. The SDK may also send a Bundle of data back on a successful run.
     * @throws IllegalArgumentException if the SDK is not loaded.
     */
    public void sendData(
            @NonNull String sdkName,
            @NonNull Bundle data,
            @NonNull @CallbackExecutor Executor callbackExecutor,
            @NonNull OutcomeReceiver<SendDataResponse, SendDataException> receiver) {
        SendDataReceiverProxy callbackProxy = new SendDataReceiverProxy(callbackExecutor, receiver);
        try {
            mService.sendData(mContext.getPackageName(), sdkName, data, callbackProxy);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * A callback for tracking events SDK sandbox death.
     *
     * <p>The callback can be added using {@link
     * SdkSandboxManager#addSdkSandboxLifecycleCallback(Executor, SdkSandboxLifecycleCallback)}
     * and removed using {@link
     * SdkSandboxManager#removeSdkSandboxLifecycleCallback(SdkSandboxLifecycleCallback)}
     */
    public interface SdkSandboxLifecycleCallback {
        /**
         * Notifies the client application that the SDK sandbox has died. The sandbox could die for
         * various reasons, for example, due to memory pressure on the system, or a crash in the
         * sandbox.
         *
         * The system will automatically restart the sandbox process if it died due to a crash.
         * However, the state of the sandbox will be lost - so any SDKs that were loaded previously
         * would have to be loaded again, using {@link SdkSandboxManager#loadSdk(String, Bundle,
         * Executor, OutcomeReceiver)} to continue using them.
         */
        void onSdkSandboxDied();
    }

    /** @hide */
    private static class SdkSandboxLifecycleCallbackProxy
            extends ISdkSandboxLifecycleCallback.Stub {
        private final Executor mExecutor;
        public final SdkSandboxLifecycleCallback callback;

        SdkSandboxLifecycleCallbackProxy(
                Executor executor, SdkSandboxLifecycleCallback lifecycleCallback) {
            mExecutor = executor;
            callback = lifecycleCallback;
        }

        @Override
        public void onSdkSandboxDied() {
            mExecutor.execute(() -> callback.onSdkSandboxDied());
        }
    }

    /** @hide */
    private static class LoadSdkReceiverProxy extends ILoadSdkCallback.Stub {
        private final Executor mExecutor;
        private final OutcomeReceiver<LoadSdkResponse, LoadSdkException> mCallback;

        LoadSdkReceiverProxy(
                Executor executor, OutcomeReceiver<LoadSdkResponse, LoadSdkException> callback) {
            mExecutor = executor;
            mCallback = callback;
        }

        @Override
        public void onLoadSdkSuccess(Bundle params) {
            mExecutor.execute(() -> mCallback.onResult(new LoadSdkResponse(params)));
        }

        @Override
        public void onLoadSdkFailure(int errorCode, String errorMsg) {
            mExecutor.execute(() -> mCallback.onError(new LoadSdkException(errorCode, errorMsg)));
        }
    }

    /** @hide */
    private static class RequestSurfacePackageReceiverProxy
            extends IRequestSurfacePackageCallback.Stub {
        private final Executor mExecutor;
        private final OutcomeReceiver<RequestSurfacePackageResponse, RequestSurfacePackageException>
                mReceiver;

        RequestSurfacePackageReceiverProxy(
                Executor executor,
                OutcomeReceiver<RequestSurfacePackageResponse, RequestSurfacePackageException>
                        receiver) {
            mExecutor = executor;
            mReceiver = receiver;
        }

        @Override
        public void onSurfacePackageReady(SurfacePackage surfacePackage,
                int surfacePackageId, Bundle params) {
            mExecutor.execute(
                    () ->
                            mReceiver.onResult(
                                    new RequestSurfacePackageResponse(surfacePackage, params)));
        }

        @Override
        public void onSurfacePackageError(int errorCode, String errorMsg) {
            mExecutor.execute(
                    () ->
                            mReceiver.onError(
                                    new RequestSurfacePackageException(errorCode, errorMsg)));
        }
    }

    /** @hide */
    private static class SendDataReceiverProxy extends ISendDataCallback.Stub {
        private final Executor mExecutor;
        private final OutcomeReceiver<SendDataResponse, SendDataException> mReceiver;

        SendDataReceiverProxy(
                Executor executor, OutcomeReceiver<SendDataResponse, SendDataException> receiver) {
            mExecutor = executor;
            mReceiver = receiver;
        }

        @Override
        public void onSendDataSuccess(Bundle params) {
            mExecutor.execute(() -> mReceiver.onResult(new SendDataResponse(params)));
        }

        @Override
        public void onSendDataError(int errorCode, String errorMsg) {
            mExecutor.execute(() -> mReceiver.onError(new SendDataException(errorCode, errorMsg)));
        }
    }
}
