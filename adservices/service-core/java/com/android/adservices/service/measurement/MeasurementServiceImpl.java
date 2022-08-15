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

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_KILLSWITCH_ENABLED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNSET;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
import static android.adservices.common.AdServicesStatusUtils.StatusCode;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__MEASUREMENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__DELETE_REGISTRATIONS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_MEASUREMENT_API_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REGISTER_SOURCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REGISTER_TRIGGER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REGISTER_WEB_SOURCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REGISTER_WEB_TRIGGER;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.CallerMetadata;
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
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Implementation of {@link IMeasurementService}.
 *
 * @hide
 */
public class MeasurementServiceImpl extends IMeasurementService.Stub {
    private static final String EMPTY_PACKAGE_NAME = "";
    private static final Executor sBackgroundExecutor = AdServicesExecutors.getBackgroundExecutor();
    private static final Executor sLightExecutor = AdServicesExecutors.getLightWeightExecutor();
    private final MeasurementImpl mMeasurementImpl;
    private final Flags mFlags;
    private final AdServicesLogger mAdServicesLogger;
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
                flags,
                AdServicesLoggerImpl.getInstance());
    }

    @VisibleForTesting
    MeasurementServiceImpl(
            @NonNull MeasurementImpl measurementImpl,
            @NonNull Context context,
            @NonNull ConsentManager consentManager,
            @NonNull Throttler throttler,
            @NonNull Flags flags,
            @NonNull AdServicesLogger adServicesLogger) {
        mContext = context;
        mMeasurementImpl = measurementImpl;
        mConsentManager = consentManager;
        mThrottler = throttler;
        mFlags = flags;
        mAdServicesLogger = adServicesLogger;
    }

    @Override
    public void register(
            @NonNull RegistrationRequest request,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callback);

        long startTime = callerMetadata.getBinderElapsedTimestamp();
        Throttler.ApiKey apiKey =
                RegistrationRequest.REGISTER_SOURCE == request.getRegistrationType()
                        ? Throttler.ApiKey.MEASUREMENT_API_REGISTER_SOURCE
                        : Throttler.ApiKey.MEASUREMENT_API_REGISTER_TRIGGER;
        final int apiNameId =
                RegistrationRequest.REGISTER_SOURCE == request.getRegistrationType()
                        ? AD_SERVICES_API_CALLED__API_NAME__REGISTER_SOURCE
                        : AD_SERVICES_API_CALLED__API_NAME__REGISTER_TRIGGER;
        if (isThrottled(request.getPackageName(), apiKey, callback)) {
            long endTime = System.currentTimeMillis();
            logApiStats(
                    apiNameId,
                    request.getPackageName(),
                    (int) (endTime - startTime),
                    STATUS_RATE_LIMIT_REACHED);
            return;
        }

        sBackgroundExecutor.execute(
                () -> {
                    performWorkIfAllowed(
                            () -> isRegisterDisabled(request),
                            (mMeasurementImpl) ->
                                    mMeasurementImpl.register(request, System.currentTimeMillis()),
                            Collections.singletonList(
                                    new UserConsentAccessResolver(mConsentManager)),
                            callback,
                            apiNameId,
                            request.getPackageName(),
                            startTime);
                });
    }

    @Override
    public void registerWebSource(
            @NonNull WebSourceRegistrationRequestInternal request,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callback);

        long startTime = callerMetadata.getBinderElapsedTimestamp();
        final Throttler.ApiKey apiKey = Throttler.ApiKey.MEASUREMENT_API_REGISTER_WEB_SOURCE;
        if (isThrottled(request.getPackageName(), apiKey, callback)) {
            long endTime = System.currentTimeMillis();
            logApiStats(
                    AD_SERVICES_API_CALLED__API_NAME__REGISTER_WEB_SOURCE,
                    request.getPackageName(),
                    (int) (endTime - startTime),
                    STATUS_RATE_LIMIT_REACHED);
            return;
        }

        sBackgroundExecutor.execute(
                () -> {
                    performWorkIfAllowed(
                            mFlags::getMeasurementApiRegisterWebSourceKillSwitch,
                            (mMeasurementImpl) ->
                                    mMeasurementImpl.registerWebSource(
                                            request, System.currentTimeMillis()),
                            Arrays.asList(
                                    new UserConsentAccessResolver(mConsentManager),
                                    new WebRegistrationByPackageAccessResolver(
                                            mFlags.getWebContextRegistrationClientAppAllowList(),
                                            request.getPackageName())),
                            callback,
                            AD_SERVICES_API_CALLED__API_NAME__REGISTER_WEB_SOURCE,
                            request.getPackageName(),
                            startTime);
                });
    }

    @Override
    public void registerWebTrigger(
            @NonNull WebTriggerRegistrationRequestInternal request,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callback);

        long startTime = callerMetadata.getBinderElapsedTimestamp();
        final Throttler.ApiKey apiKey = Throttler.ApiKey.MEASUREMENT_API_REGISTER_WEB_TRIGGER;
        if (isThrottled(request.getPackageName(), apiKey, callback)) {
            long endTime = System.currentTimeMillis();
            logApiStats(
                    AD_SERVICES_API_CALLED__API_NAME__REGISTER_WEB_TRIGGER,
                    request.getPackageName(),
                    (int) (endTime - startTime),
                    STATUS_RATE_LIMIT_REACHED);
            return;
        }

        sBackgroundExecutor.execute(
                () -> {
                    performWorkIfAllowed(
                            mFlags::getMeasurementApiRegisterWebTriggerKillSwitch,
                            (measurementImpl) ->
                                    measurementImpl.registerWebTrigger(
                                            request, System.currentTimeMillis()),
                            Arrays.asList(
                                    new UserConsentAccessResolver(mConsentManager),
                                    new WebRegistrationByPackageAccessResolver(
                                            mFlags.getWebContextRegistrationClientAppAllowList(),
                                            request.getPackageName())),
                            callback,
                            AD_SERVICES_API_CALLED__API_NAME__REGISTER_WEB_TRIGGER,
                            request.getPackageName(),
                            startTime);
                });
    }

    @Override
    public void deleteRegistrations(
            @NonNull DeletionParam request,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callback);

        long startTime = callerMetadata.getBinderElapsedTimestamp();
        final Throttler.ApiKey apiKey = Throttler.ApiKey.MEASUREMENT_API_DELETION_REGISTRATION;
        if (isThrottled(request.getPackageName(), apiKey, callback)) {
            long endTime = System.currentTimeMillis();
            logApiStats(
                    AD_SERVICES_API_CALLED__API_NAME__DELETE_REGISTRATIONS,
                    request.getPackageName(),
                    (int) (endTime - startTime),
                    STATUS_RATE_LIMIT_REACHED);
            return;
        }

        sBackgroundExecutor.execute(
                () -> {
                    @AdServicesStatusUtils.StatusCode int resultCode = STATUS_UNSET;
                    try {
                        if (mFlags.getMeasurementApiDeleteRegistrationsKillSwitch()) {
                            LogUtil.e("Measurement Delete Registrations API is disabled");
                            callback.onFailure(
                                    new MeasurementErrorResponse.Builder()
                                            .setStatusCode(STATUS_KILLSWITCH_ENABLED)
                                            .setErrorMessage(KILL_SWITCH_ENABLED)
                                            .build());
                            resultCode = STATUS_KILLSWITCH_ENABLED;
                            return;
                        }

                        resultCode = mMeasurementImpl.deleteRegistrations(request);
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
                        resultCode = STATUS_INTERNAL_ERROR;
                    } finally {
                        long endTime = System.currentTimeMillis();
                        logApiStats(
                                AD_SERVICES_API_CALLED__API_NAME__DELETE_REGISTRATIONS,
                                request.getPackageName(),
                                (int) (endTime - startTime),
                                resultCode);
                    }
                });
    }

    @Override
    public void getMeasurementApiStatus(
            @NonNull CallerMetadata callerMetadata,
            @NonNull IMeasurementApiStatusCallback callback) {
        Objects.requireNonNull(callback);
        long startTime = callerMetadata.getBinderElapsedTimestamp();

        sLightExecutor.execute(
                () -> {
                    @StatusCode int statusCode = STATUS_UNSET;
                    try {
                        if (mFlags.getMeasurementApiStatusKillSwitch()) {
                            LogUtil.e("Measurement Status API is disabled");
                            callback.onResult(MeasurementManager.MEASUREMENT_API_STATE_DISABLED);
                            statusCode = STATUS_KILLSWITCH_ENABLED;
                            return;
                        }

                        callback.onResult(mMeasurementImpl.getMeasurementApiStatus());
                        statusCode = STATUS_SUCCESS;
                    } catch (RemoteException e) {
                        LogUtil.e(e, "Unable to send result to the callback.");
                        statusCode = STATUS_INTERNAL_ERROR;
                    } finally {
                        long endTime = System.currentTimeMillis();
                        logApiStats(
                                AD_SERVICES_API_CALLED__API_NAME__GET_MEASUREMENT_API_STATUS,
                                EMPTY_PACKAGE_NAME,
                                (int) (endTime - startTime),
                                statusCode);
                    }
                });
    }

    private void performWorkIfAllowed(
            Supplier<Boolean> isKillSwitchEnabledSupplier,
            Consumer<MeasurementImpl> execute,
            List<IAccessResolver> apiAccessResolvers,
            IMeasurementCallback callback,
            int apiNameId,
            String appPackageName,
            long startTime) {
        int statusCode = STATUS_UNSET;
        try {
            // TODO: Inject isKillSwitchEnabledSupplier as an IAccessResolver
            if (isKillSwitchEnabledSupplier.get()) {
                callback.onFailure(
                        new MeasurementErrorResponse.Builder()
                                .setStatusCode(STATUS_KILLSWITCH_ENABLED)
                                .setErrorMessage(KILL_SWITCH_ENABLED)
                                .build());
                statusCode = STATUS_KILLSWITCH_ENABLED;
                return;
            }

            Optional<IAccessResolver> accessDenierOpt =
                    apiAccessResolvers.stream()
                            .filter(accessResolver -> !accessResolver.isAllowed(mContext))
                            .findFirst();

            if (accessDenierOpt.isPresent()) {
                IAccessResolver accessDenier = accessDenierOpt.get();
                statusCode = accessDenier.getErrorStatusCode();
                callback.onFailure(
                        new MeasurementErrorResponse.Builder()
                                .setStatusCode(STATUS_USER_CONSENT_REVOKED)
                                .setErrorMessage(accessDenier.getErrorMessage())
                                .build());
                return;
            }

            execute.accept(mMeasurementImpl);
            callback.onResult();
            statusCode = STATUS_SUCCESS;

        } catch (RemoteException e) {
            LogUtil.e(e, "Unable to send result to the callback");
            statusCode = STATUS_INTERNAL_ERROR;
        } finally {
            long endTime = System.currentTimeMillis();
            logApiStats(apiNameId, appPackageName, (int) (endTime - startTime), statusCode);
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

    private void logApiStats(int apiNameId, String appPackageName, int latency, int resultCode) {
        mAdServicesLogger.logApiCallStats(
                new ApiCallStats.Builder()
                        .setCode(AD_SERVICES_API_CALLED)
                        .setApiClass(AD_SERVICES_API_CALLED__API_CLASS__MEASUREMENT)
                        .setApiName(apiNameId)
                        .setAppPackageName(appPackageName)
                        .setSdkPackageName(EMPTY_PACKAGE_NAME)
                        .setLatencyMillisecond(latency)
                        .setResultCode(resultCode)
                        .build());
    }
}
