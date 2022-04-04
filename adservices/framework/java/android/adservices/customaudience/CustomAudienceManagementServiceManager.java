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

package android.adservices.customaudience;

import android.adservices.common.FledgeErrorResponse;
import android.adservices.exceptions.AdServicesException;
import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.content.Context;
import android.os.OutcomeReceiver;
import android.os.RemoteException;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.LogUtil;
import com.android.adservices.ServiceBinder;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Custom Audience management service manager.
 *
 * @hide
 */
public class CustomAudienceManagementServiceManager {
    public static final String CUSTOM_AUDIENCE_MANAGEMENT_SERVICE =
            "custom_audience_management_service";

    // TODO(b/221861041): Remove warning suppression; context needed later for
    //  authorization/authentication
    @NonNull
    @SuppressWarnings("unused")
    private final Context mContext;
    @NonNull
    private final ServiceBinder<ICustomAudienceManagementService> mServiceBinder;

    /**
     * Create a service binder CustomAudienceManagementManager
     */
    public CustomAudienceManagementServiceManager(@NonNull Context context) {
        Objects.requireNonNull(context);
        mContext = context;
        mServiceBinder =
                ServiceBinder.getServiceBinder(context,
                        AdServicesCommon.ACTION_CUSTOM_AUDIENCE_MANAGEMENT_SERVICE,
                        ICustomAudienceManagementService.Stub::asInterface);
    }

    @NonNull
    private ICustomAudienceManagementService getService() {
        ICustomAudienceManagementService service = mServiceBinder.getService();
        Objects.requireNonNull(service);
        return service;
    }

    /**
     * Adds the current user to a custom audience serving targeted ads during the ad selection
     * process.
     */
    @NonNull
    public void joinCustomAudience(@NonNull CustomAudience customAudience,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, AdServicesException> receiver) {
        Objects.requireNonNull(customAudience);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);

        try {
            final ICustomAudienceManagementService service = getService();

            service.joinCustomAudience(customAudience, new ICustomAudienceCallback.Stub() {
                @Override
                public void onSuccess() {
                    executor.execute(
                            () -> {
                                receiver.onResult(null);
                            });
                }

                @Override
                public void onFailure(FledgeErrorResponse failureParcel) {
                    executor.execute(
                            () -> {
                                receiver.onError(failureParcel.asException());
                            });
                }
            });
        } catch (RemoteException e) {
            LogUtil.e("Exception", e);
            receiver.onError(new AdServicesException("Internal Error!"));
        }
    }

    /**
     * Attempts to remove a user from a custom audience by deleting any existing
     * {@link CustomAudience} data.
     *
     * In case of a non-existent or mis-identified {@link CustomAudience}, no actions are taken.
     */
    @NonNull
    public void leaveCustomAudience(@NonNull String owner, @NonNull String buyer,
            @NonNull String name, @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, AdServicesException> receiver) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(buyer);
        Objects.requireNonNull(name);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);

        try {
            final ICustomAudienceManagementService service = getService();

            service.leaveCustomAudience(owner, buyer, name, new ICustomAudienceCallback.Stub() {
                @Override
                public void onSuccess() {
                    executor.execute(
                            () -> {
                                receiver.onResult(null);
                            });
                }

                @Override
                public void onFailure(FledgeErrorResponse failureParcel) {
                    executor.execute(
                            () -> {
                                // leaveCustomAudience() does not throw errors or exceptions in the
                                // course of expected operation
                                receiver.onResult(null);
                            });
                }
            });
        } catch (RemoteException e) {
            LogUtil.e("Exception", e);
            receiver.onError(new AdServicesException("Internal Error!"));
        }
    }
}
