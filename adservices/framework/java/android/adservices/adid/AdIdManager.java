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
package android.adservices.adid;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.CallerMetadata;
import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.app.sdksandbox.SandboxedSdkContext;
import android.content.Context;
import android.os.LimitExceededException;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.os.SystemClock;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.LogUtil;
import com.android.adservices.ServiceBinder;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * AdId Manager provides APIs for app and ad-SDKs to access advertising ID. The advertising ID is a
 * unique, per-device, user-resettable ID for advertising. It gives users better controls and
 * provides developers with a simple, standard system to continue to monetize their apps via
 * personalized ads (formerly known as interest-based ads).
 */
public class AdIdManager {
    /**
     * Service used for registering AdIdManager in the system service registry.
     *
     * @hide
     */
    public static final String ADID_SERVICE = "adid_service";

    // When an app calls the AdId API directly, it sets the SDK name to empty string.
    static final String EMPTY_SDK = "";

    private final Context mContext;
    private final ServiceBinder<IAdIdService> mServiceBinder;

    /**
     * Create AdIdManager
     *
     * @hide
     */
    public AdIdManager(Context context) {
        mContext = context;
        mServiceBinder =
                ServiceBinder.getServiceBinder(
                        context,
                        AdServicesCommon.ACTION_ADID_SERVICE,
                        IAdIdService.Stub::asInterface);
    }

    @NonNull
    private IAdIdService getService() {
        IAdIdService service = mServiceBinder.getService();
        if (service == null) {
            throw new IllegalStateException("Unable to find the service");
        }
        return service;
    }

    @NonNull
    private Context getContext() {
        return mContext;
    }

    /**
     * Return the AdId.
     *
     * @param executor The executor to run callback.
     * @param callback The callback that's called after adid are available or an error occurs.
     * @throws SecurityException if caller is not authorized to call this API.
     * @throws IllegalStateException if this API is not available.
     * @throws LimitExceededException if rate limit was reached.
     */
    @NonNull
    public void getAdId(
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<AdId, Exception> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        CallerMetadata callerMetadata =
                new CallerMetadata.Builder()
                        .setBinderElapsedTimestamp(SystemClock.elapsedRealtime())
                        .build();
        final IAdIdService service = getService();
        String appPackageName = "";
        String sdkPackageName = "";
        // First check if context is SandboxedSdkContext or not
        Context getAdIdRequestContext = getContext();
        if (getAdIdRequestContext instanceof SandboxedSdkContext) {
            SandboxedSdkContext requestContext = ((SandboxedSdkContext) getAdIdRequestContext);
            sdkPackageName = requestContext.getSdkPackageName();
            appPackageName = requestContext.getClientPackageName();
        } else { // This is the case without the Sandbox.
            appPackageName = getAdIdRequestContext.getPackageName();
        }

        try {
            service.getAdId(
                    new GetAdIdParam.Builder()
                            .setAppPackageName(appPackageName)
                            .setSdkPackageName(sdkPackageName)
                            .build(),
                    callerMetadata,
                    new IGetAdIdCallback.Stub() {
                        @Override
                        public void onResult(GetAdIdResult resultParcel) {
                            executor.execute(
                                    () -> {
                                        if (resultParcel.isSuccess()) {
                                            callback.onResult(
                                                    new AdId(
                                                            resultParcel.getAdId(),
                                                            resultParcel.isLatEnabled()));
                                        } else {
                                            callback.onError(
                                                    AdServicesStatusUtils.asException(
                                                            resultParcel));
                                        }
                                    });
                        }

                        @Override
                        public void onError(int resultCode) {
                            executor.execute(
                                    () ->
                                            callback.onError(
                                                    AdServicesStatusUtils.asException(resultCode)));
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e(e, "RemoteException");
            callback.onError(e);
        }
    }

    /**
     * If the service is in an APK (as opposed to the system service), unbind it from the service to
     * allow the APK process to die.
     *
     * @hide
     */
    // TODO: change to @VisibleForTesting
    public void unbindFromService() {
        mServiceBinder.unbindFromService();
    }
}
