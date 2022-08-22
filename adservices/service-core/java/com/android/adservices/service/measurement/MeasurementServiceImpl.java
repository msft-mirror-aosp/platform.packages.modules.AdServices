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
import static android.adservices.common.AdServicesStatusUtils.StatusCode;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__MEASUREMENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__DELETE_REGISTRATIONS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_MEASUREMENT_API_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REGISTER_SOURCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REGISTER_TRIGGER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REGISTER_WEB_SOURCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REGISTER_WEB_TRIGGER;

import android.adservices.common.AdServicesPermissions;
import android.adservices.common.CallerMetadata;
import android.adservices.measurement.DeletionParam;
import android.adservices.measurement.IMeasurementApiStatusCallback;
import android.adservices.measurement.IMeasurementCallback;
import android.adservices.measurement.IMeasurementService;
import android.adservices.measurement.MeasurementErrorResponse;
import android.adservices.measurement.MeasurementManager;
import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.StatusParam;
import android.adservices.measurement.WebSourceRegistrationRequestInternal;
import android.adservices.measurement.WebTriggerRegistrationRequestInternal;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.content.Context;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.PermissionHelper;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.measurement.access.AppPackageAccessResolver;
import com.android.adservices.service.measurement.access.IAccessResolver;
import com.android.adservices.service.measurement.access.KillSwitchAccessResolver;
import com.android.adservices.service.measurement.access.ManifestBasedAdtechAccessResolver;
import com.android.adservices.service.measurement.access.PermissionAccessResolver;
import com.android.adservices.service.measurement.access.UserConsentAccessResolver;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.service.stats.Clock;
import com.android.internal.annotations.VisibleForTesting;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
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
    private final Clock mClock;
    private final MeasurementImpl mMeasurementImpl;
    private final Flags mFlags;
    private final AdServicesLogger mAdServicesLogger;
    private final ConsentManager mConsentManager;
    private final EnrollmentDao mEnrollmentDao;
    private final Context mContext;
    private final Throttler mThrottler;
    private static final String RATE_LIMIT_REACHED = "Rate limit reached to call this API.";
    private static final String CALLBACK_ERROR = "Unable to send result to the callback";

    public MeasurementServiceImpl(
            @NonNull Context context,
            @NonNull Clock clock,
            @NonNull ConsentManager consentManager,
            @NonNull EnrollmentDao enrollmentDao,
            @NonNull Flags flags) {
        this(
                MeasurementImpl.getInstance(context),
                context,
                clock,
                consentManager,
                enrollmentDao,
                Throttler.getInstance(FlagsFactory.getFlags().getSdkRequestPermitsPerSecond()),
                flags,
                AdServicesLoggerImpl.getInstance());
    }

    @VisibleForTesting
    MeasurementServiceImpl(
            @NonNull MeasurementImpl measurementImpl,
            @NonNull Context context,
            @NonNull Clock clock,
            @NonNull ConsentManager consentManager,
            @NonNull EnrollmentDao enrollmentDao,
            @NonNull Throttler throttler,
            @NonNull Flags flags,
            @NonNull AdServicesLogger adServicesLogger) {
        mContext = context;
        mClock = clock;
        mMeasurementImpl = measurementImpl;
        mConsentManager = consentManager;
        mEnrollmentDao = enrollmentDao;
        mThrottler = throttler;
        mFlags = flags;
        mAdServicesLogger = adServicesLogger;
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_ATTRIBUTION)
    public void register(
            @NonNull RegistrationRequest request,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callback);

        final long serviceStartTime = mClock.elapsedRealtime();

        final Throttler.ApiKey apiKey = getApiKey(request);
        final int apiNameId = getApiNameId(request);
        if (isThrottled(request.getPackageName(), apiKey, callback)) {
            logApiStats(
                    apiNameId,
                    request.getPackageName(),
                    getLatency(callerMetadata, serviceStartTime),
                    STATUS_RATE_LIMIT_REACHED);
            return;
        }

        final boolean attributionPermission = PermissionHelper.hasAttributionPermission(mContext);
        sBackgroundExecutor.execute(
                () -> {
                    performRegistration(
                            (service) -> service.register(request, now()),
                            List.of(
                                    new KillSwitchAccessResolver(() -> isRegisterDisabled(request)),
                                    new AppPackageAccessResolver(
                                            mFlags.getPpapiAppSignatureBypassList(),
                                            request.getPackageName()),
                                    new UserConsentAccessResolver(mConsentManager),
                                    new PermissionAccessResolver(attributionPermission),
                                    new ManifestBasedAdtechAccessResolver(
                                            mEnrollmentDao,
                                            mFlags,
                                            request.getPackageName(),
                                            request.getRegistrationUri())),
                            callback,
                            apiNameId,
                            request.getPackageName(),
                            callerMetadata,
                            serviceStartTime);
                });
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_ATTRIBUTION)
    public void registerWebSource(
            @NonNull WebSourceRegistrationRequestInternal request,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callback);

        final long serviceStartTime = mClock.elapsedRealtime();

        final Throttler.ApiKey apiKey = Throttler.ApiKey.MEASUREMENT_API_REGISTER_WEB_SOURCE;
        final int apiNameId = AD_SERVICES_API_CALLED__API_NAME__REGISTER_WEB_SOURCE;
        if (isThrottled(request.getPackageName(), apiKey, callback)) {
            logApiStats(
                    apiNameId,
                    request.getPackageName(),
                    getLatency(callerMetadata, serviceStartTime),
                    STATUS_RATE_LIMIT_REACHED);
            return;
        }

        final boolean attributionPermission = PermissionHelper.hasAttributionPermission(mContext);
        sBackgroundExecutor.execute(
                () -> {
                    performRegistration(
                            (service) -> service.registerWebSource(request, now()),
                            List.of(
                                    new KillSwitchAccessResolver(
                                            mFlags::getMeasurementApiRegisterWebSourceKillSwitch),
                                    new AppPackageAccessResolver(
                                            mFlags.getPpapiAppSignatureBypassList(),
                                            request.getPackageName()),
                                    new UserConsentAccessResolver(mConsentManager),
                                    new PermissionAccessResolver(attributionPermission),
                                    new ManifestBasedAdtechAccessResolver(
                                            mEnrollmentDao,
                                            mFlags,
                                            request.getPackageName(),
                                            request.getSourceRegistrationRequest()
                                                    .getSourceParams()
                                                    .get(0)
                                                    .getRegistrationUri()),
                                    new AppPackageAccessResolver(
                                            mFlags.getWebContextClientAppAllowList(),
                                            request.getPackageName())),
                            callback,
                            apiNameId,
                            request.getPackageName(),
                            callerMetadata,
                            serviceStartTime);
                });
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_ATTRIBUTION)
    public void registerWebTrigger(
            @NonNull WebTriggerRegistrationRequestInternal request,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callback);

        final long serviceStartTime = mClock.elapsedRealtime();

        final Throttler.ApiKey apiKey = Throttler.ApiKey.MEASUREMENT_API_REGISTER_WEB_TRIGGER;
        final int apiNameId = AD_SERVICES_API_CALLED__API_NAME__REGISTER_WEB_TRIGGER;
        if (isThrottled(request.getPackageName(), apiKey, callback)) {
            logApiStats(
                    apiNameId,
                    request.getPackageName(),
                    getLatency(callerMetadata, serviceStartTime),
                    STATUS_RATE_LIMIT_REACHED);
            return;
        }

        final boolean attributionPermission = PermissionHelper.hasAttributionPermission(mContext);
        sBackgroundExecutor.execute(
                () -> {
                    performRegistration(
                            (service) -> service.registerWebTrigger(request, now()),
                            List.of(
                                    new KillSwitchAccessResolver(
                                            mFlags::getMeasurementApiRegisterWebTriggerKillSwitch),
                                    new AppPackageAccessResolver(
                                            mFlags.getPpapiAppSignatureBypassList(),
                                            request.getPackageName()),
                                    new UserConsentAccessResolver(mConsentManager),
                                    new PermissionAccessResolver(attributionPermission),
                                    new ManifestBasedAdtechAccessResolver(
                                            mEnrollmentDao,
                                            mFlags,
                                            request.getPackageName(),
                                            request.getTriggerRegistrationRequest()
                                                    .getTriggerParams()
                                                    .get(0)
                                                    .getRegistrationUri())),
                            callback,
                            apiNameId,
                            request.getPackageName(),
                            callerMetadata,
                            serviceStartTime);
                });
    }

    @Override
    public void deleteRegistrations(
            @NonNull DeletionParam request,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callback);

        final long serviceStartTime = mClock.elapsedRealtime();

        final Throttler.ApiKey apiKey = Throttler.ApiKey.MEASUREMENT_API_DELETION_REGISTRATION;
        final int apiNameId = AD_SERVICES_API_CALLED__API_NAME__DELETE_REGISTRATIONS;
        if (isThrottled(request.getPackageName(), apiKey, callback)) {
            logApiStats(
                    apiNameId,
                    request.getPackageName(),
                    getLatency(callerMetadata, serviceStartTime),
                    STATUS_RATE_LIMIT_REACHED);
            return;
        }

        sBackgroundExecutor.execute(
                () -> {
                    final Supplier<Boolean> killSwitchSupplier =
                            mFlags::getMeasurementApiDeleteRegistrationsKillSwitch;
                    performDeletion(
                            (service) -> mMeasurementImpl.deleteRegistrations(request),
                            List.of(
                                    new KillSwitchAccessResolver(killSwitchSupplier),
                                    new AppPackageAccessResolver(
                                            mFlags.getPpapiAppSignatureBypassList(),
                                            request.getPackageName()),
                                    new AppPackageAccessResolver(
                                            mFlags.getWebContextClientAppAllowList(),
                                            request.getPackageName())),
                            callback,
                            apiNameId,
                            request.getPackageName(),
                            callerMetadata,
                            serviceStartTime);
                });
    }

    @Override
    public void getMeasurementApiStatus(
            @NonNull StatusParam statusParam,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IMeasurementApiStatusCallback callback) {
        Objects.requireNonNull(callback);

        final long serviceStartTime = mClock.elapsedRealtime();

        final int apiNameId = AD_SERVICES_API_CALLED__API_NAME__GET_MEASUREMENT_API_STATUS;

        sLightExecutor.execute(
                () -> {
                    @StatusCode int statusCode = STATUS_UNSET;
                    try {

                        List<IAccessResolver> accessResolvers =
                                List.of(
                                        new KillSwitchAccessResolver(
                                                mFlags::getMeasurementApiStatusKillSwitch),
                                        new AppPackageAccessResolver(
                                                mFlags.getPpapiAppSignatureBypassList(),
                                                statusParam.getAppPackageName()));

                        final Optional<IAccessResolver> optionalResolver =
                                getAccessDenied(accessResolvers);

                        if (optionalResolver.isPresent()) {
                            final IAccessResolver resolver = optionalResolver.get();
                            LogUtil.e(resolver.getErrorMessage());
                            callback.onResult(MeasurementManager.MEASUREMENT_API_STATE_DISABLED);
                            statusCode = STATUS_KILLSWITCH_ENABLED;
                            return;
                        }

                        callback.onResult(mMeasurementImpl.getMeasurementApiStatus());
                        statusCode = STATUS_SUCCESS;
                    } catch (RemoteException e) {
                        LogUtil.e(e, CALLBACK_ERROR);
                        statusCode = STATUS_INTERNAL_ERROR;
                    } finally {
                        logApiStats(
                                apiNameId,
                                statusParam.getAppPackageName(),
                                getLatency(callerMetadata, serviceStartTime),
                                statusCode);
                    }
                });
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

    private void performRegistration(
            Consumer<MeasurementImpl> execute,
            List<IAccessResolver> accessResolvers,
            IMeasurementCallback callback,
            int apiNameId,
            String appPackageName,
            CallerMetadata callerMetadata,
            long serviceStartTime) {

        int statusCode = STATUS_UNSET;
        try {

            final Optional<IAccessResolver> optionalResolver = getAccessDenied(accessResolvers);
            if (optionalResolver.isPresent()) {
                final IAccessResolver resolver = optionalResolver.get();
                LogUtil.e(resolver.getErrorMessage());
                statusCode = resolver.getErrorStatusCode();
                callback.onFailure(
                        new MeasurementErrorResponse.Builder()
                                .setStatusCode(resolver.getErrorStatusCode())
                                .setErrorMessage(resolver.getErrorMessage())
                                .build());
                return;
            }

            execute.accept(mMeasurementImpl);
            callback.onResult();
            statusCode = STATUS_SUCCESS;

        } catch (RemoteException e) {
            LogUtil.e(e, CALLBACK_ERROR);
            statusCode = STATUS_INTERNAL_ERROR;
        } finally {
            logApiStats(
                    apiNameId,
                    appPackageName,
                    getLatency(callerMetadata, serviceStartTime),
                    statusCode);
        }
    }

    private void performDeletion(
            Function<MeasurementImpl, Integer> execute,
            List<IAccessResolver> accessResolvers,
            IMeasurementCallback callback,
            int apiNameId,
            String appPackageName,
            CallerMetadata callerMetadata,
            long serviceStartTime) {

        int statusCode = STATUS_UNSET;
        try {

            final Optional<IAccessResolver> optionalResolver = getAccessDenied(accessResolvers);
            if (optionalResolver.isPresent()) {
                final IAccessResolver resolver = optionalResolver.get();
                LogUtil.e(resolver.getErrorMessage());
                statusCode = resolver.getErrorStatusCode();
                callback.onFailure(
                        new MeasurementErrorResponse.Builder()
                                .setStatusCode(resolver.getErrorStatusCode())
                                .setErrorMessage(resolver.getErrorMessage())
                                .build());
                return;
            }

            statusCode = execute.apply(mMeasurementImpl);
            if (statusCode == STATUS_SUCCESS) {
                callback.onResult();
            } else {
                callback.onFailure(
                        new MeasurementErrorResponse.Builder()
                                .setStatusCode(statusCode)
                                .setErrorMessage("Encountered failure during Measurement deletion.")
                                .build());
            }

        } catch (RemoteException e) {
            LogUtil.e(e, CALLBACK_ERROR);
            statusCode = STATUS_INTERNAL_ERROR;
        } finally {
            logApiStats(
                    apiNameId,
                    appPackageName,
                    getLatency(callerMetadata, serviceStartTime),
                    statusCode);
        }
    }

    private Optional<IAccessResolver> getAccessDenied(List<IAccessResolver> apiAccessResolvers) {
        return apiAccessResolvers.stream()
                .filter(accessResolver -> !accessResolver.isAllowed(mContext))
                .findFirst();
    }

    private Throttler.ApiKey getApiKey(RegistrationRequest request) {
        return RegistrationRequest.REGISTER_SOURCE == request.getRegistrationType()
                ? Throttler.ApiKey.MEASUREMENT_API_REGISTER_SOURCE
                : Throttler.ApiKey.MEASUREMENT_API_REGISTER_TRIGGER;
    }

    private int getApiNameId(RegistrationRequest request) {
        return RegistrationRequest.REGISTER_SOURCE == request.getRegistrationType()
                ? AD_SERVICES_API_CALLED__API_NAME__REGISTER_SOURCE
                : AD_SERVICES_API_CALLED__API_NAME__REGISTER_TRIGGER;
    }

    private int getLatency(CallerMetadata metadata, long serviceStartTime) {
        long binderCallStartTimeMillis = metadata.getBinderElapsedTimestamp();
        long serviceLatency = mClock.elapsedRealtime() - serviceStartTime;
        // Double it to simulate the return binder time is same to call binder time
        long binderLatency = (serviceStartTime - binderCallStartTimeMillis) * 2;

        return (int) (serviceLatency + binderLatency);
    }

    private long now() {
        return System.currentTimeMillis();
    }
}
