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

import android.adservices.common.AdTechIdentifier;
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
 * CustomAudienceManager provides APIs for app and ad-SDKs to join / leave custom audiences.
 */
public class CustomAudienceManager {
    public static final String CUSTOM_AUDIENCE_SERVICE = "custom_audience_service";

    @NonNull
    private final ServiceBinder<ICustomAudienceService> mServiceBinder;

    /**
     * Create a service binder CustomAudienceManager
     *
     * @hide
     */
    public CustomAudienceManager(@NonNull Context context) {
        mServiceBinder =
                ServiceBinder.getServiceBinder(
                        context,
                        AdServicesCommon.ACTION_CUSTOM_AUDIENCE_SERVICE,
                        ICustomAudienceService.Stub::asInterface);
    }

    @NonNull
    private ICustomAudienceService getService() {
        ICustomAudienceService service = mServiceBinder.getService();
        Objects.requireNonNull(service);
        return service;
    }

    /**
     * Adds the user to the given {@link CustomAudience}.
     *
     * <p>An attempt to register the user for a custom audience with the same combination of owner,
     * buyer, and name will cause the existing custom audience's information to be overwritten,
     * including the list of ads data.
     *
     * <p>Note that the ads list can be completely overwritten by the daily background fetch job.
     *
     * <p>This call fails with an {@link SecurityException} if
     *
     * <ol>
     *   <li>the owner is not calling app's package name and/or
     *   <li>the buyer is not authorized to use the API.
     * </ol>
     *
     * <p>This call fails with an {@link IllegalArgumentException} if
     *
     * <ol>
     *   <li>the storage limit has been exceeded by the calling application and/or
     *   <li>any URL parameters in the {@link CustomAudience} given are not authenticated with the
     *       {@link CustomAudience} buyer.
     * </ol>
     *
     * <p>This call fails with an {@link IllegalStateException} if an internal service error is
     * encountered.
     */
    public void joinCustomAudience(
            @NonNull JoinCustomAudienceRequest joinCustomAudienceRequest,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, AdServicesException> receiver) {
        Objects.requireNonNull(joinCustomAudienceRequest);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);

        final CustomAudience customAudience = joinCustomAudienceRequest.getCustomAudience();

        try {
            final ICustomAudienceService service = getService();

            service.joinCustomAudience(
                    customAudience,
                    new ICustomAudienceCallback.Stub() {
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
            LogUtil.e(e, "Exception");
            receiver.onError(new AdServicesException("Internal Error!"));
        }
    }

    /**
     * Attempts to remove a user from a custom audience by deleting any existing {@link
     * CustomAudience} data, identified by {@code owner}, {@code buyer}, and {@code name}.
     *
     * <p>This call fails with an {@link SecurityException} if
     *
     * <ol>
     *   <li>the owner is not calling app's package name; and/or
     *   <li>the buyer is not authorized to use the API.
     * </ol>
     *
     * <p>This call does not inform the caller whether the custom audience specified existed in
     * on-device storage. In another word, it will fail silently when try to leave a not joined
     * custom audience.
     */
    public void leaveCustomAudience(
            @NonNull LeaveCustomAudienceRequest leaveCustomAudienceRequest,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, AdServicesException> receiver) {
        Objects.requireNonNull(leaveCustomAudienceRequest);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);

        final String owner = leaveCustomAudienceRequest.getOwner();
        final AdTechIdentifier buyer = leaveCustomAudienceRequest.getBuyer();
        final String name = leaveCustomAudienceRequest.getName();

        try {
            final ICustomAudienceService service = getService();

            service.leaveCustomAudience(
                    owner,
                    buyer,
                    name,
                    new ICustomAudienceCallback.Stub() {
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
                                        // leaveCustomAudience() does not throw errors or exceptions
                                        // in the
                                        // course of expected operation
                                        receiver.onResult(null);
                                    });
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e(e, "Exception");
            receiver.onError(new AdServicesException("Internal Error!"));
        }
    }

    /**
     * Overrides the Custom Audience API to avoid fetching data from remote servers and use the data
     * provided in {@link AddCustomAudienceOverrideRequest} instead. The {@link
     * AddCustomAudienceOverrideRequest} is provided by the Ads SDK.
     *
     * <p>This method is intended to be used for end-to-end testing. This API is enabled only for
     * apps in debug mode with developer options enabled.
     *
     * <p>This call will fail silently if the {@code owner} in the {@code request} is not the
     * calling app's package name.
     *
     * @throws IllegalStateException if this API is not enabled for the caller
     *     <p>The receiver either returns a {@code void} for a successful run, or an {@link
     *     AdServicesException} indicates the error.
     */
    @NonNull
    public void overrideCustomAudienceRemoteInfo(
            @NonNull AddCustomAudienceOverrideRequest request,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, AdServicesException> receiver) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);

        try {
            final ICustomAudienceService service = getService();
            service.overrideCustomAudienceRemoteInfo(
                    request.getOwner(),
                    request.getBuyer(),
                    request.getName(),
                    request.getBiddingLogicJs(),
                    request.getTrustedBiddingSignals(),
                    new CustomAudienceOverrideCallback.Stub() {
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
            LogUtil.e(e, "Exception");
            receiver.onError(new AdServicesException("Internal Error!"));
        }
    }

    /**
     * Removes an override in th Custom Audience API with associated the data in {@link
     * RemoveCustomAudienceOverrideRequest}.
     *
     * <p>This method is intended to be used for end-to-end testing. This API is enabled only for
     * apps in debug mode with developer options enabled.
     *
     * @throws IllegalStateException if this API is not enabled for the caller
     *     <p>The {@link RemoveCustomAudienceOverrideRequest} is provided by the Ads SDK. The
     *     receiver either returns a {@code void} for a successful run, or an {@link
     *     AdServicesException} indicates the error.
     */
    @NonNull
    public void removeCustomAudienceRemoteInfoOverride(
            @NonNull RemoveCustomAudienceOverrideRequest request,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, AdServicesException> receiver) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);

        try {
            final ICustomAudienceService service = getService();
            service.removeCustomAudienceRemoteInfoOverride(
                    request.getOwner(),
                    request.getBuyer(),
                    request.getName(),
                    new CustomAudienceOverrideCallback.Stub() {
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
            LogUtil.e(e, "Exception");
            receiver.onError(new AdServicesException("Internal Error!"));
        }
    }

    /**
     * Removes all override data in the Custom Audience API.
     *
     * <p>This method is intended to be used for end-to-end testing. This API is enabled only for
     * apps in debug mode with developer options enabled.
     *
     * @throws IllegalStateException if this API is not enabled for the caller
     *     <p>The receiver either returns a {@code void} for a successful run, or an {@link
     *     AdServicesException} indicates the error.
     */
    @NonNull
    public void resetAllCustomAudienceOverrides(
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, AdServicesException> receiver) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);

        try {
            final ICustomAudienceService service = getService();
            service.resetAllCustomAudienceOverrides(
                    new CustomAudienceOverrideCallback.Stub() {
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
            LogUtil.e(e, "Exception");
            receiver.onError(new AdServicesException("Internal Error!"));
        }
    }
}
