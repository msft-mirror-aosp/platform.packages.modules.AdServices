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
package android.adservices.appsetid;

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
 * AppSetIdManager provides APIs for app and ad-SDKs to access appSetId for non-monetizing purpose.
 */
public class AppSetIdManager {
    /**
     * Service used for registering AppSetIdManager in the system service registry.
     *
     * @hide
     */
    public static final String APPSETID_SERVICE = "appsetid_service";

    /* When an app calls the AppSetId API directly, it sets the SDK name to empty string. */
    static final String EMPTY_SDK = "";

    private final Context mContext;
    private final ServiceBinder<IAppSetIdService> mServiceBinder;

    /**
     * Create AppSetIdManager
     *
     * @hide
     */
    public AppSetIdManager(Context context) {
        mContext = context;
        mServiceBinder =
                ServiceBinder.getServiceBinder(
                        context,
                        AdServicesCommon.ACTION_APPSETID_SERVICE,
                        IAppSetIdService.Stub::asInterface);
    }

    @NonNull
    private IAppSetIdService getService() {
        IAppSetIdService service = mServiceBinder.getService();
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
     * Retrieve the AppSetId.
     *
     * @param executor The executor to run callback.
     * @param callback The callback that's called after appsetid are available or an error occurs.
     * @throws SecurityException if caller is not authorized to call this API.
     * @throws IllegalStateException if this API is not available.
     * @throws LimitExceededException if rate limit was reached.
     */
    @NonNull
    public void getAppSetId(
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<AppSetId, Exception> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        CallerMetadata callerMetadata =
                new CallerMetadata.Builder()
                        .setBinderElapsedTimestamp(SystemClock.elapsedRealtime())
                        .build();
        final IAppSetIdService service = getService();
        String appPackageName = "";
        String sdkPackageName = "";
        // First check if context is SandboxedSdkContext or not
        Context getAppSetIdRequestContext = getContext();
        if (getAppSetIdRequestContext instanceof SandboxedSdkContext) {
            SandboxedSdkContext requestContext = ((SandboxedSdkContext) getAppSetIdRequestContext);
            sdkPackageName = requestContext.getSdkPackageName();
            appPackageName = requestContext.getClientPackageName();
        } else { // This is the case without the Sandbox.
            appPackageName = getAppSetIdRequestContext.getPackageName();
        }
        try {
            service.getAppSetId(
                    new GetAppSetIdParam.Builder()
                            .setAppPackageName(appPackageName)
                            .setSdkPackageName(sdkPackageName)
                            .build(),
                    callerMetadata,
                    new IGetAppSetIdCallback.Stub() {
                        @Override
                        public void onResult(GetAppSetIdResult resultParcel) {
                            executor.execute(
                                    () -> {
                                        if (resultParcel.isSuccess()) {
                                            callback.onResult(
                                                    new AppSetId(
                                                            resultParcel.getAppSetId(),
                                                            resultParcel.getAppSetIdScope()));
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
            LogUtil.e("RemoteException", e);
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
