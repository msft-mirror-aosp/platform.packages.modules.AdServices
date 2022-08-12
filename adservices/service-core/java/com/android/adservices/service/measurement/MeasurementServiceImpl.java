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
package com.android.adservices.service.measurement;

import static android.adservices.common.AdServicesStatusUtils.STATUS_KILLSWITCH_ENABLED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.measurement.DeletionParam;
import android.adservices.measurement.IMeasurementApiStatusCallback;
import android.adservices.measurement.IMeasurementCallback;
import android.adservices.measurement.IMeasurementService;
import android.adservices.measurement.MeasurementErrorResponse;
import android.adservices.measurement.MeasurementManager;
import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.WebSourceRegistrationRequestInternal;
import android.adservices.measurement.WebTriggerRegistrationRequestInternal;
import android.annotation.NonNull;
import android.content.Context;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.measurement.access.IAccessResolver;
import com.android.adservices.service.measurement.access.UserConsentAccessResolver;
import com.android.adservices.service.measurement.access.WebRegistrationByPackageAccessResolver;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Implementation of {@link IMeasurementService}.
 *
 * @hide
 */
public class MeasurementServiceImpl extends IMeasurementService.Stub {
    private static final Executor sBackgroundExecutor = AdServicesExecutors.getBackgroundExecutor();
    private static final Executor sLightExecutor = AdServicesExecutors.getLightWeightExecutor();
    private final MeasurementImpl mMeasurementImpl;
    private final Flags mFlags;
    private final ConsentManager mConsentManager;
    private final Context mContext;
    private final Throttler mThrottler;
    private static final String RATE_LIMIT_REACHED = "Rate limit reached to call this API.";
    private static final String KILL_SWITCH_ENABLED = "Measurement API is disabled.";

    public MeasurementServiceImpl(
            @NonNull Context context,
            @NonNull ConsentManager consentManager,
            @NonNull Flags flags) {
        this(
                MeasurementImpl.getInstance(context),
                context,
                consentManager,
                Throttler.getInstance(FlagsFactory.getFlags().getSdkRequestPermitsPerSecond()),
                flags);
    }

    @VisibleForTesting
    MeasurementServiceImpl(
            @NonNull MeasurementImpl measurementImpl,
            @NonNull Context context,
            @NonNull ConsentManager consentManager,
            @NonNull Throttler throttler,
            @NonNull Flags flags) {
        mContext = context;
        mMeasurementImpl = measurementImpl;
        mConsentManager = consentManager;
        mThrottler = throttler;
        mFlags = flags;
    }

    @Override
    public void register(
            @NonNull RegistrationRequest request, @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callback);

        final Throttler.ApiKey apiKey =
                RegistrationRequest.REGISTER_SOURCE == request.getRegistrationType()
                        ? Throttler.ApiKey.MEASUREMENT_API_REGISTER_SOURCE
                        : Throttler.ApiKey.MEASUREMENT_API_REGISTER_TRIGGER;
        if (isThrottled(request.getPackageName(), apiKey, callback)) {
            return;
        }

        sBackgroundExecutor.execute(
                () -> {
                    if (isRegisterDisabled(request)) {
                        setKillSwitchCallbackFailure(callback);
                        return;
                    }

                    performWorkIfAllowed(
                            (mMeasurementImpl) ->
                                    mMeasurementImpl.register(request, System.currentTimeMillis()),
                            Collections.singletonList(
                                    new UserConsentAccessResolver(mConsentManager)),
                            callback);
                });
    }

    @Override
    public void registerWebSource(
            @NonNull WebSourceRegistrationRequestInternal request,
            @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callback);

        final Throttler.ApiKey apiKey = Throttler.ApiKey.MEASUREMENT_API_REGISTER_WEB_SOURCE;
        if (isThrottled(request.getPackageName(), apiKey, callback)) {
            return;
        }

        sBackgroundExecutor.execute(
                () -> {
                    if (mFlags.getMeasurementApiRegisterWebSourceKillSwitch()) {
                        LogUtil.e("Measurement Register Web Source API is disabled");
                        setKillSwitchCallbackFailure(callback);
                        return;
                    }

                    performWorkIfAllowed(
                            (mMeasurementImpl) ->
                                    mMeasurementImpl.registerWebSource(
                                            request, System.currentTimeMillis()),
                            Arrays.asList(
                                    new UserConsentAccessResolver(mConsentManager),
                                    new WebRegistrationByPackageAccessResolver(
                                            mFlags.getWebContextRegistrationClientAppAllowList(),
                                            request.getPackageName())),
                            callback);
                });
    }

    @Override
    public void registerWebTrigger(
            @NonNull WebTriggerRegistrationRequestInternal request,
            @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callback);

        final Throttler.ApiKey apiKey = Throttler.ApiKey.MEASUREMENT_API_REGISTER_WEB_TRIGGER;
        if (isThrottled(request.getPackageName(), apiKey, callback)) {
            return;
        }

        sBackgroundExecutor.execute(
                () -> {
                    if (mFlags.getMeasurementApiRegisterWebTriggerKillSwitch()) {
                        LogUtil.e("Measurement Register Web Trigger API is disabled");
                        setKillSwitchCallbackFailure(callback);
                        return;
                    }

                    performWorkIfAllowed(
                            (measurementImpl) ->
                                    measurementImpl.registerWebTrigger(
                                            request, System.currentTimeMillis()),
                            Arrays.asList(
                                    new UserConsentAccessResolver(mConsentManager),
                                    new WebRegistrationByPackageAccessResolver(
                                            mFlags.getWebContextRegistrationClientAppAllowList(),
                                            request.getPackageName())),
                            callback);
                });
    }

    @Override
    public void deleteRegistrations(
            @NonNull DeletionParam request, @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callback);

        final Throttler.ApiKey apiKey = Throttler.ApiKey.MEASUREMENT_API_DELETION_REGISTRATION;
        if (isThrottled(request.getPackageName(), apiKey, callback)) {
            return;
        }

        sBackgroundExecutor.execute(
                () -> {
                    if (mFlags.getMeasurementApiDeleteRegistrationsKillSwitch()) {
                        LogUtil.e("Measurement Delete Registrations API is disabled");
                        setKillSwitchCallbackFailure(callback);
                        return;
                    }

                    try {
                        @AdServicesStatusUtils.StatusCode
                        int resultCode = mMeasurementImpl.deleteRegistrations(request);
                        if (resultCode == STATUS_SUCCESS) {
                            callback.onResult();
                        } else {
                            callback.onFailure(
                                    new MeasurementErrorResponse.Builder()
                                            .setStatusCode(resultCode)
                                            .setErrorMessage(
                                                    "Encountered failure during "
                                                            + "Measurement deletion.")
                                            .build());
                        }
                    } catch (RemoteException e) {
                        LogUtil.e(e, "Unable to send result to the callback");
                    }
                });
    }

    @Override
    public void getMeasurementApiStatus(@NonNull IMeasurementApiStatusCallback callback) {
        Objects.requireNonNull(callback);

        sLightExecutor.execute(
                () -> {
                    if (mFlags.getMeasurementApiStatusKillSwitch()) {
                        LogUtil.e("Measurement Status API is disabled");
                        try {
                            callback.onResult(MeasurementManager.MEASUREMENT_API_STATE_DISABLED);
                        } catch (RemoteException e) {
                            LogUtil.e(
                                    e,
                                    "Failed to call the callback on measurement kill switch"
                                            + " enabled.");
                        }
                        return;
                    }
                    try {
                        callback.onResult(mMeasurementImpl.getMeasurementApiStatus());
                    } catch (RemoteException e) {
                        LogUtil.e(e, "Unable to send result to the callback");
                    }
                });
    }

    private void performWorkIfAllowed(
            Consumer<MeasurementImpl> execute,
            List<IAccessResolver> permissionResolvers,
            IMeasurementCallback callback) {
        try {
            Optional<IAccessResolver> accessDenier =
                    permissionResolvers.stream()
                            .filter(accessResolver -> !accessResolver.isAllowed(mContext))
                            .findFirst();

            if (accessDenier.isPresent()) {
                callback.onFailure(
                        new MeasurementErrorResponse.Builder()
                                .setStatusCode(STATUS_USER_CONSENT_REVOKED)
                                .setErrorMessage(accessDenier.get().getErrorMessage())
                                .build());
                return;
            }

            execute.accept(mMeasurementImpl);
            callback.onResult();

        } catch (RemoteException e) {
            LogUtil.e(e, "Unable to send result to the callback");
        }
    }

    // Return true if we should throttle (don't allow the API call).
    private boolean isThrottled(
            String appPackageName, Throttler.ApiKey apiKey, IMeasurementCallback callback) {

        final boolean throttled = !mThrottler.tryAcquire(apiKey, appPackageName);
        if (throttled) {
            LogUtil.e("Rate Limit Reached for Measurement API");
            try {
                callback.onFailure(
                        new MeasurementErrorResponse.Builder()
                                .setStatusCode(STATUS_RATE_LIMIT_REACHED)
                                .setErrorMessage(RATE_LIMIT_REACHED)
                                .build());
            } catch (RemoteException e) {
                LogUtil.e(e, "Failed to call the callback while performing rate limits.");
            }
            return true;
        }
        return false;
    }

    private boolean isRegisterDisabled(RegistrationRequest request) {
        final boolean isRegistrationSource =
                request.getRegistrationType() == RegistrationRequest.REGISTER_SOURCE;

        if (isRegistrationSource && mFlags.getMeasurementApiRegisterSourceKillSwitch()) {
            LogUtil.e("Measurement Register Source API is disabled");
            return true;
        } else if (!isRegistrationSource && mFlags.getMeasurementApiRegisterTriggerKillSwitch()) {
            LogUtil.e("Measurement Register Trigger API is disabled");
            return true;
        }
        return false;
    }

    private void setKillSwitchCallbackFailure(IMeasurementCallback callback) {
        try {
            callback.onFailure(
                    new MeasurementErrorResponse.Builder()
                            .setStatusCode(STATUS_KILLSWITCH_ENABLED)
                            .setErrorMessage(KILL_SWITCH_ENABLED)
                            .build());
        } catch (RemoteException e) {
            LogUtil.e(e, "Failed to call the callback on measurement kill switch enabled.");
        }
    }
}
