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

import static android.adservices.common.AdServicesStatusUtils.STATUS_ADSERVICES_DISABLED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNSET;
import static android.adservices.common.AdServicesStatusUtils.StatusCode;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__MEASUREMENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__DELETE_REGISTRATIONS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_MEASUREMENT_API_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REGISTER_SOURCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REGISTER_SOURCES;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REGISTER_TRIGGER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REGISTER_WEB_SOURCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REGISTER_WEB_TRIGGER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;

import android.adservices.common.AdServicesPermissions;
import android.adservices.common.CallerMetadata;
import android.adservices.measurement.DeletionParam;
import android.adservices.measurement.IMeasurementApiStatusCallback;
import android.adservices.measurement.IMeasurementCallback;
import android.adservices.measurement.IMeasurementService;
import android.adservices.measurement.MeasurementErrorResponse;
import android.adservices.measurement.MeasurementManager;
import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.SourceRegistrationRequestInternal;
import android.adservices.measurement.StatusParam;
import android.adservices.measurement.WebSourceRegistrationRequestInternal;
import android.adservices.measurement.WebTriggerRegistrationRequestInternal;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.content.Context;
import android.os.Binder;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.download.MddJob;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.common.AllowLists;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.BinderFlagReader;
import com.android.adservices.service.common.PermissionHelper;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.encryptionkey.EncryptionKeyJobService;
import com.android.adservices.service.measurement.access.AccessInfo;
import com.android.adservices.service.measurement.access.AccessResolverInfo;
import com.android.adservices.service.measurement.access.AppPackageAccessResolver;
import com.android.adservices.service.measurement.access.ConsentNotifiedAccessResolver;
import com.android.adservices.service.measurement.access.DevContextAccessResolver;
import com.android.adservices.service.measurement.access.ForegroundEnforcementAccessResolver;
import com.android.adservices.service.measurement.access.IAccessResolver;
import com.android.adservices.service.measurement.access.KillSwitchAccessResolver;
import com.android.adservices.service.measurement.access.PermissionAccessResolver;
import com.android.adservices.service.measurement.access.UserConsentAccessResolver;
import com.android.adservices.service.measurement.attribution.AttributionFallbackJobService;
import com.android.adservices.service.measurement.attribution.AttributionJobService;
import com.android.adservices.service.measurement.registration.AsyncRegistrationFallbackJob;
import com.android.adservices.service.measurement.registration.AsyncRegistrationQueueJobService;
import com.android.adservices.service.measurement.reporting.AggregateFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.AggregateReportingJobService;
import com.android.adservices.service.measurement.reporting.DebugReportingFallbackJobService;
import com.android.adservices.service.measurement.reporting.EventFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.EventReportingJobService;
import com.android.adservices.service.measurement.reporting.VerboseDebugReportingFallbackJobService;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;

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
@RequiresApi(Build.VERSION_CODES.S)
public class MeasurementServiceImpl extends IMeasurementService.Stub {
    private static final String RATE_LIMIT_REACHED = "Rate limit reached to call this API.";
    private static final String CALLBACK_ERROR = "Unable to send result to the callback";
    private static final Executor sLightExecutor = AdServicesExecutors.getLightWeightExecutor();
    private final ListeningExecutorService mBackgroundExecutor;
    private final Clock mClock;
    private final MeasurementImpl mMeasurementImpl;
    private final CachedFlags mFlags;
    private final DebugFlags mDebugFlags;
    private final AdServicesLogger mAdServicesLogger;
    private final ConsentManager mConsentManager;
    private final AppImportanceFilter mAppImportanceFilter;
    private final Context mContext;
    private final Throttler mThrottler;
    private final DevContextFilter mDevContextFilter;

    public MeasurementServiceImpl(
            @NonNull Context context,
            @NonNull Clock clock,
            @NonNull ConsentManager consentManager,
            @NonNull CachedFlags flags,
            @NonNull DebugFlags debugFlags,
            @NonNull AppImportanceFilter appImportanceFilter) {
        this(
                MeasurementImpl.getInstance(),
                context,
                clock,
                consentManager,
                Throttler.getInstance(),
                flags,
                debugFlags,
                AdServicesLoggerImpl.getInstance(),
                appImportanceFilter,
                DevContextFilter.create(
                        context,
                        /* developerModeFeatureEnabled= */ false),
                AdServicesExecutors.getBackgroundExecutor());
    }

    @VisibleForTesting
    MeasurementServiceImpl(
            @NonNull MeasurementImpl measurementImpl,
            @NonNull Context context,
            @NonNull Clock clock,
            @NonNull ConsentManager consentManager,
            @NonNull Throttler throttler,
            @NonNull CachedFlags flags,
            @NonNull DebugFlags debugFlags,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull AppImportanceFilter appImportanceFilter,
            @NonNull DevContextFilter devContextFilter,
            @NonNull ListeningExecutorService backgroundExecutor) {
        mContext = context;
        mClock = clock;
        mMeasurementImpl = measurementImpl;
        mConsentManager = consentManager;
        mThrottler = throttler;
        mFlags = flags;
        mDebugFlags = debugFlags;
        mAdServicesLogger = adServicesLogger;
        mAppImportanceFilter = appImportanceFilter;
        mDevContextFilter = devContextFilter;
        mBackgroundExecutor = backgroundExecutor;
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_ATTRIBUTION)
    public void register(
            @NonNull RegistrationRequest request,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callerMetadata);
        Objects.requireNonNull(callback);

        if (invokeCallbackOnFailureOnRvc(callback)) {
            return;
        }

        final long serviceStartTime = mClock.elapsedRealtime();

        final Throttler.ApiKey apiKey = getApiKey(request);
        final int apiNameId = getApiNameId(request);
        if (isThrottled(request.getAppPackageName(), apiKey, callback)) {
            logApiStats(
                    apiNameId,
                    request.getAppPackageName(),
                    request.getSdkPackageName(),
                    getLatency(callerMetadata, serviceStartTime),
                    STATUS_RATE_LIMIT_REACHED);
            return;
        }
        final int callerUid = Binder.getCallingUidOrThrow();
        final boolean attributionPermission =
                PermissionHelper.hasAttributionPermission(mContext, request.getAppPackageName());
        mBackgroundExecutor.execute(
                () -> {
                    performRegistration(
                            (service) ->
                                    service.register(
                                            request, request.isAdIdPermissionGranted(), now()),
                            List.of(
                                    new KillSwitchAccessResolver(() -> isRegisterDisabled(request)),
                                    new ForegroundEnforcementAccessResolver(
                                            apiNameId,
                                            callerUid,
                                            mAppImportanceFilter,
                                            getRegisterSourceOrTriggerEnforcementForegroundStatus(
                                                    request, mFlags)),
                                    new AppPackageAccessResolver(
                                            mFlags.getMsmtApiAppAllowList(),
                                            mFlags.getMsmtApiAppBlockList(),
                                            request.getAppPackageName()),
                                    new ConsentNotifiedAccessResolver(
                                            mConsentManager, mFlags, mDebugFlags),
                                    new UserConsentAccessResolver(mConsentManager),
                                    new PermissionAccessResolver(attributionPermission),
                                    new DevContextAccessResolver(
                                            mDevContextFilter.createDevContextFromCallingUid(
                                                    callerUid),
                                            request)),
                            callback,
                            apiNameId,
                            request.getAppPackageName(),
                            request.getSdkPackageName(),
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
        Objects.requireNonNull(callerMetadata);
        Objects.requireNonNull(callback);

        if (invokeCallbackOnFailureOnRvc(callback)) {
            return;
        }

        final long serviceStartTime = mClock.elapsedRealtime();

        final Throttler.ApiKey apiKey = Throttler.ApiKey.MEASUREMENT_API_REGISTER_WEB_SOURCE;
        final int apiNameId = AD_SERVICES_API_CALLED__API_NAME__REGISTER_WEB_SOURCE;
        if (isThrottled(request.getAppPackageName(), apiKey, callback)) {
            logApiStats(
                    apiNameId,
                    request.getAppPackageName(),
                    request.getSdkPackageName(),
                    getLatency(callerMetadata, serviceStartTime),
                    STATUS_RATE_LIMIT_REACHED);
            return;
        }

        final int callerUid = Binder.getCallingUidOrThrow();
        final boolean attributionPermission =
                PermissionHelper.hasAttributionPermission(mContext, request.getAppPackageName());
        mBackgroundExecutor.execute(
                () -> {
                    final Supplier<Boolean> enforceForeground =
                            mFlags::getEnforceForegroundStatusForMeasurementRegisterWebSource;
                    performRegistration(
                            (service) ->
                                    service.registerWebSource(
                                            request, request.isAdIdPermissionGranted(), now()),
                            List.of(
                                    new KillSwitchAccessResolver(
                                            mFlags::getMeasurementApiRegisterWebSourceKillSwitch),
                                    new ForegroundEnforcementAccessResolver(
                                            apiNameId,
                                            callerUid,
                                            mAppImportanceFilter,
                                            enforceForeground),
                                    new AppPackageAccessResolver(
                                            mFlags.getMsmtApiAppAllowList(),
                                            mFlags.getMsmtApiAppBlockList(),
                                            request.getAppPackageName()),
                                    new ConsentNotifiedAccessResolver(
                                            mConsentManager, mFlags, mDebugFlags),
                                    new UserConsentAccessResolver(mConsentManager),
                                    new PermissionAccessResolver(attributionPermission),
                                    new AppPackageAccessResolver(
                                            mFlags.getWebContextClientAppAllowList(),
                                            /*blocklist*/ null,
                                            request.getAppPackageName()),
                                    new DevContextAccessResolver(
                                            mDevContextFilter.createDevContextFromCallingUid(
                                                    callerUid),
                                            request.getSourceRegistrationRequest())),
                            callback,
                            apiNameId,
                            request.getAppPackageName(),
                            request.getSdkPackageName(),
                            callerMetadata,
                            serviceStartTime);
                });
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_ATTRIBUTION)
    public void registerSource(
            @NonNull SourceRegistrationRequestInternal request,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IMeasurementCallback callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(callerMetadata);
        Objects.requireNonNull(callback);

        if (invokeCallbackOnFailureOnRvc(callback)) {
            return;
        }

        final long serviceStartTime = mClock.elapsedRealtime();
        final Throttler.ApiKey apiKey = Throttler.ApiKey.MEASUREMENT_API_REGISTER_SOURCES;
        final int apiNameId = AD_SERVICES_API_CALLED__API_NAME__REGISTER_SOURCES;
        if (isThrottled(request.getAppPackageName(), apiKey, callback)) {
            logApiStats(
                    apiNameId,
                    request.getAppPackageName(),
                    request.getSdkPackageName(),
                    getLatency(callerMetadata, serviceStartTime),
                    STATUS_RATE_LIMIT_REACHED);
            return;
        }
        final int callerUid = Binder.getCallingUidOrThrow();
        mBackgroundExecutor.execute(
                () -> {
                    Supplier<Boolean> foregroundEnforcementSupplier =
                            mFlags::getEnforceForegroundStatusForMeasurementRegisterSources;
                    performRegistration(
                            (service) -> service.registerSources(request, now()),
                            List.of(
                                    new KillSwitchAccessResolver(
                                            mFlags::getMeasurementApiRegisterSourcesKillSwitch),
                                    new ForegroundEnforcementAccessResolver(
                                            apiNameId,
                                            callerUid,
                                            mAppImportanceFilter,
                                            foregroundEnforcementSupplier),
                                    new AppPackageAccessResolver(
                                            mFlags.getMsmtApiAppAllowList(),
                                            mFlags.getMsmtApiAppBlockList(),
                                            request.getAppPackageName()),
                                    new ConsentNotifiedAccessResolver(
                                            mConsentManager, mFlags, mDebugFlags),
                                    new UserConsentAccessResolver(mConsentManager),
                                    new PermissionAccessResolver(
                                            PermissionHelper.hasAttributionPermission(
                                                    mContext, request.getAppPackageName())),
                                    new DevContextAccessResolver(
                                            mDevContextFilter.createDevContextFromCallingUid(
                                                    callerUid),
                                            request.getSourceRegistrationRequest())),
                            callback,
                            apiNameId,
                            request.getAppPackageName(),
                            request.getSdkPackageName(),
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
        Objects.requireNonNull(callerMetadata);
        Objects.requireNonNull(callback);

        if (invokeCallbackOnFailureOnRvc(callback)) {
            return;
        }

        final long serviceStartTime = mClock.elapsedRealtime();

        final Throttler.ApiKey apiKey = Throttler.ApiKey.MEASUREMENT_API_REGISTER_WEB_TRIGGER;
        final int apiNameId = AD_SERVICES_API_CALLED__API_NAME__REGISTER_WEB_TRIGGER;
        if (isThrottled(request.getAppPackageName(), apiKey, callback)) {
            logApiStats(
                    apiNameId,
                    request.getAppPackageName(),
                    request.getSdkPackageName(),
                    getLatency(callerMetadata, serviceStartTime),
                    STATUS_RATE_LIMIT_REACHED);
            return;
        }

        final int callerUid = Binder.getCallingUidOrThrow();
        final boolean attributionPermission =
                PermissionHelper.hasAttributionPermission(mContext, request.getAppPackageName());
        mBackgroundExecutor.execute(
                () -> {
                    final Supplier<Boolean> enforceForeground =
                            mFlags::getEnforceForegroundStatusForMeasurementRegisterWebTrigger;
                    performRegistration(
                            (service) ->
                                    service.registerWebTrigger(
                                            request, request.isAdIdPermissionGranted(), now()),
                            List.of(
                                    new KillSwitchAccessResolver(
                                            mFlags::getMeasurementApiRegisterWebTriggerKillSwitch),
                                    new ForegroundEnforcementAccessResolver(
                                            apiNameId,
                                            callerUid,
                                            mAppImportanceFilter,
                                            enforceForeground),
                                    new AppPackageAccessResolver(
                                            mFlags.getMsmtApiAppAllowList(),
                                            mFlags.getMsmtApiAppBlockList(),
                                            request.getAppPackageName()),
                                    new ConsentNotifiedAccessResolver(
                                            mConsentManager, mFlags, mDebugFlags),
                                    new UserConsentAccessResolver(mConsentManager),
                                    new PermissionAccessResolver(attributionPermission),
                                    new DevContextAccessResolver(
                                            mDevContextFilter.createDevContextFromCallingUid(
                                                    callerUid),
                                            request.getTriggerRegistrationRequest())),
                            callback,
                            apiNameId,
                            request.getAppPackageName(),
                            request.getSdkPackageName(),
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
        Objects.requireNonNull(callerMetadata);
        Objects.requireNonNull(callback);

        if (invokeCallbackOnFailureOnRvc(callback)) {
            return;
        }

        final long serviceStartTime = mClock.elapsedRealtime();

        final Throttler.ApiKey apiKey = Throttler.ApiKey.MEASUREMENT_API_DELETION_REGISTRATION;
        final int apiNameId = AD_SERVICES_API_CALLED__API_NAME__DELETE_REGISTRATIONS;
        if (isThrottled(request.getAppPackageName(), apiKey, callback)) {
            logApiStats(
                    apiNameId,
                    request.getAppPackageName(),
                    request.getSdkPackageName(),
                    getLatency(callerMetadata, serviceStartTime),
                    STATUS_RATE_LIMIT_REACHED);
            return;
        }

        final int callerUid = Binder.getCallingUidOrThrow();
        mBackgroundExecutor.execute(
                () -> {
                    final Supplier<Boolean> enforceForeground =
                            mFlags::getEnforceForegroundStatusForMeasurementDeleteRegistrations;
                    final Supplier<Boolean> killSwitchSupplier =
                            mFlags::getMeasurementApiDeleteRegistrationsKillSwitch;
                    performDeletion(
                            (service) -> mMeasurementImpl.deleteRegistrations(request),
                            List.of(
                                    new KillSwitchAccessResolver(killSwitchSupplier),
                                    new ForegroundEnforcementAccessResolver(
                                            apiNameId,
                                            callerUid,
                                            mAppImportanceFilter,
                                            enforceForeground),
                                    new AppPackageAccessResolver(
                                            mFlags.getMsmtApiAppAllowList(),
                                            mFlags.getMsmtApiAppBlockList(),
                                            request.getAppPackageName()),
                                    new AppPackageAccessResolver(
                                            mFlags.getWebContextClientAppAllowList(),
                                            /*blocklist*/ null,
                                            request.getAppPackageName())),
                            callback,
                            apiNameId,
                            request.getAppPackageName(),
                            request.getSdkPackageName(),
                            callerMetadata,
                            serviceStartTime);
                });
    }

    @Override
    public void getMeasurementApiStatus(
            @NonNull StatusParam statusParam,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IMeasurementApiStatusCallback callback) {
        Objects.requireNonNull(statusParam);
        Objects.requireNonNull(callerMetadata);
        Objects.requireNonNull(callback);

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            try {
                // API status callback doesn't have an onError/onFailure
                callback.onResult(MeasurementManager.MEASUREMENT_API_STATE_DISABLED);
            } catch (RemoteException e) {
                String errorMsg = "AdServices is not enabled on Android R.";
                LogUtil.e(e, "Fail to call the callback. %s", errorMsg);
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            }
            return;
        }

        final long serviceStartTime = mClock.elapsedRealtime();

        final int apiNameId = AD_SERVICES_API_CALLED__API_NAME__GET_MEASUREMENT_API_STATUS;

        final int callerUid = Binder.getCallingUidOrThrow();
        sLightExecutor.execute(
                () -> {
                    @StatusCode int statusCode = STATUS_UNSET;
                    try {
                        final Supplier<Boolean> enforceForeground =
                                mFlags::getEnforceForegroundStatusForMeasurementStatus;
                        List<IAccessResolver> accessResolvers;
                        if (mFlags.getMsmtEnableApiStatusAllowListCheck()) {
                            if (!AllowLists.isPackageAllowListed(
                                    mFlags.getWebContextClientAppAllowList(),
                                    statusParam.getAppPackageName())) {
                                callback.onResult(MeasurementManager.MEASUREMENT_API_STATE_ENABLED);
                                statusCode = STATUS_SUCCESS;
                                return;
                            }
                            accessResolvers =
                                    List.of(
                                            new KillSwitchAccessResolver(
                                                    mFlags::getMeasurementApiStatusKillSwitch),
                                            new UserConsentAccessResolver(mConsentManager),
                                            new ForegroundEnforcementAccessResolver(
                                                    apiNameId,
                                                    callerUid,
                                                    mAppImportanceFilter,
                                                    enforceForeground));
                        } else {
                            accessResolvers =
                                    List.of(
                                            new KillSwitchAccessResolver(
                                                    mFlags::getMeasurementApiStatusKillSwitch),
                                            new ConsentNotifiedAccessResolver(
                                                    mConsentManager, mFlags, mDebugFlags),
                                            new UserConsentAccessResolver(mConsentManager),
                                            new ForegroundEnforcementAccessResolver(
                                                    apiNameId,
                                                    callerUid,
                                                    mAppImportanceFilter,
                                                    enforceForeground),
                                            new AppPackageAccessResolver(
                                                    mFlags.getMsmtApiAppAllowList(),
                                                    mFlags.getMsmtApiAppBlockList(),
                                                    statusParam.getAppPackageName()));
                        }

                        AccessResolverInfo accessResolverInfo = getAccessDenied(accessResolvers);
                        Optional<IAccessResolver> optionalResolver =
                                accessResolverInfo.getAccessResolver();

                        if (optionalResolver.isPresent()) {
                            IAccessResolver resolver = optionalResolver.get();
                            LoggerFactory.getMeasurementLogger().e(resolver.getErrorMessage());
                            callback.onResult(MeasurementManager.MEASUREMENT_API_STATE_DISABLED);
                            statusCode = accessResolverInfo.getAccessInfo().getResponseCode();
                            return;
                        }

                        callback.onResult(MeasurementManager.MEASUREMENT_API_STATE_ENABLED);
                        statusCode = STATUS_SUCCESS;
                    } catch (RemoteException e) {
                        LoggerFactory.getMeasurementLogger().e(e, CALLBACK_ERROR);
                        statusCode = STATUS_INTERNAL_ERROR;
                    } finally {
                        logApiStats(
                                apiNameId,
                                statusParam.getAppPackageName(),
                                statusParam.getSdkPackageName(),
                                getLatency(callerMetadata, serviceStartTime),
                                statusCode);
                    }
                });
    }

    @Override
    public void schedulePeriodicJobs(IMeasurementCallback callback) {

        if (invokeCallbackOnFailureOnRvc(callback)) {
            return;
        }

        // Job scheduling is an expensive operation because of calls to JobScheduler.getPendingJob.
        // Perform scheduling on a background thread so that the main thread isn't held up.
        FluentFuture.from(
                        mBackgroundExecutor.submit(
                                () -> {
                                    AggregateReportingJobService.scheduleIfNeeded(mContext, false);
                                    AggregateFallbackReportingJobService.scheduleIfNeeded(
                                            mContext, false);
                                    AttributionJobService.scheduleIfNeeded(mContext, false);
                                    AttributionFallbackJobService.scheduleIfNeeded(mContext, false);
                                    EventReportingJobService.scheduleIfNeeded(mContext, false);
                                    EventFallbackReportingJobService.scheduleIfNeeded(
                                            mContext, false);
                                    DeleteExpiredJobService.scheduleIfNeeded(mContext, false);
                                    DeleteUninstalledJobService.scheduleIfNeeded(mContext, false);
                                    MddJob.scheduleAllMddJobs();
                                    AsyncRegistrationQueueJobService.scheduleIfNeeded(
                                            mContext, false);
                                    AsyncRegistrationFallbackJob.schedule();
                                    DebugReportingFallbackJobService.scheduleIfNeeded(
                                            mContext, false);
                                    VerboseDebugReportingFallbackJobService.scheduleIfNeeded(
                                            mContext, false);
                                    EncryptionKeyJobService.scheduleIfNeeded(mContext, false);
                                }))
                .addCallback(
                        new FutureCallback<Object>() {
                            @Override
                            public void onSuccess(Object result) {
                                try {
                                    if (callback != null) {
                                        callback.onResult();
                                    }
                                } catch (RemoteException e) {
                                    LoggerFactory.getMeasurementLogger()
                                            .e(
                                                    "Unable to call onSuccess callback after"
                                                            + " scheduling periodic jobs");
                                }
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                try {
                                    if (callback != null) {
                                        callback.onFailure(null);
                                    }
                                } catch (RemoteException e) {
                                    LoggerFactory.getMeasurementLogger()
                                            .e(
                                                    "Unable to call onFailure callback after"
                                                            + " scheduling periodic jobs");
                                }
                            }
                        },
                        mBackgroundExecutor);
    }

    // Return true if we should throttle (don't allow the API call).
    private boolean isThrottled(
            String appPackageName, Throttler.ApiKey apiKey, IMeasurementCallback callback) {
        final boolean throttled = !mThrottler.tryAcquire(apiKey, appPackageName);
        if (throttled) {
            LoggerFactory.getMeasurementLogger().e("Rate Limit Reached for Measurement API");
            try {
                callback.onFailure(
                        new MeasurementErrorResponse.Builder()
                                .setStatusCode(STATUS_RATE_LIMIT_REACHED)
                                .setErrorMessage(RATE_LIMIT_REACHED)
                                .build());
            } catch (RemoteException e) {
                LoggerFactory.getMeasurementLogger()
                        .e(e, "Failed to call the callback while performing rate limits.");
            }
            return true;
        }
        return false;
    }

    private boolean isRegisterDisabled(RegistrationRequest request) {
        final boolean isRegistrationSource =
                request.getRegistrationType() == RegistrationRequest.REGISTER_SOURCE;

        if (isRegistrationSource && mFlags.getMeasurementApiRegisterSourceKillSwitch()) {
            LoggerFactory.getMeasurementLogger().e("Measurement Register Source API is disabled");
            return true;
        } else if (!isRegistrationSource && mFlags.getMeasurementApiRegisterTriggerKillSwitch()) {
            LoggerFactory.getMeasurementLogger().e("Measurement Register Trigger API is disabled");
            return true;
        }
        return false;
    }

    private void logApiStats(
            int apiNameId,
            String appPackageName,
            String sdkPackageName,
            int latency,
            int resultCode) {
        mAdServicesLogger.logApiCallStats(
                new ApiCallStats.Builder()
                        .setCode(AD_SERVICES_API_CALLED)
                        .setApiClass(AD_SERVICES_API_CALLED__API_CLASS__MEASUREMENT)
                        .setApiName(apiNameId)
                        .setAppPackageName(appPackageName)
                        .setSdkPackageName(sdkPackageName)
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
            String sdkPackageName,
            CallerMetadata callerMetadata,
            long serviceStartTime) {

        int statusCode = STATUS_UNSET;
        try {

            AccessResolverInfo accessResolverInfo = getAccessDenied(accessResolvers);
            Optional<IAccessResolver> optionalResolver = accessResolverInfo.getAccessResolver();
            if (optionalResolver.isPresent()) {
                IAccessResolver resolver = optionalResolver.get();
                LoggerFactory.getMeasurementLogger().e(resolver.getErrorMessage());
                statusCode = accessResolverInfo.getAccessInfo().getResponseCode();
                callback.onFailure(
                        new MeasurementErrorResponse.Builder()
                                .setStatusCode(statusCode)
                                .setErrorMessage(resolver.getErrorMessage())
                                .build());
                return;
            }

            execute.accept(mMeasurementImpl);
            callback.onResult();
            statusCode = STATUS_SUCCESS;

        } catch (RemoteException e) {
            LoggerFactory.getMeasurementLogger().e(e, CALLBACK_ERROR);
            statusCode = STATUS_INTERNAL_ERROR;
        } finally {
            logApiStats(
                    apiNameId,
                    appPackageName,
                    sdkPackageName,
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
            String sdkPackageName,
            CallerMetadata callerMetadata,
            long serviceStartTime) {

        int statusCode = STATUS_UNSET;
        try {

            AccessResolverInfo accessResolverInfo = getAccessDenied(accessResolvers);
            Optional<IAccessResolver> optionalResolver = accessResolverInfo.getAccessResolver();
            if (optionalResolver.isPresent()) {
                IAccessResolver resolver = optionalResolver.get();
                LoggerFactory.getMeasurementLogger().e(resolver.getErrorMessage());
                statusCode = accessResolverInfo.getAccessInfo().getResponseCode();
                callback.onFailure(
                        new MeasurementErrorResponse.Builder()
                                .setStatusCode(statusCode)
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
            LoggerFactory.getMeasurementLogger().e(e, CALLBACK_ERROR);
            statusCode = STATUS_INTERNAL_ERROR;
        } finally {
            logApiStats(
                    apiNameId,
                    appPackageName,
                    sdkPackageName,
                    getLatency(callerMetadata, serviceStartTime),
                    statusCode);
        }
    }

    private AccessResolverInfo getAccessDenied(List<IAccessResolver> apiAccessResolvers) {
        AccessResolverInfo accessResolverInfo =
                new AccessResolverInfo(Optional.empty(), new AccessInfo(true));
        Optional<IAccessResolver> deniedAccessResolver =
                apiAccessResolvers.stream()
                        .filter(
                                accessResolver -> {
                                    accessResolverInfo.setAccessInfo(
                                            accessResolver.getAccessInfo(mContext));
                                    return !accessResolverInfo.getAccessInfo().isAllowedAccess();
                                })
                        .findFirst();
        accessResolverInfo.setAccessResolver(deniedAccessResolver);
        return accessResolverInfo;
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

    private Supplier<Boolean> getRegisterSourceOrTriggerEnforcementForegroundStatus(
            RegistrationRequest request, CachedFlags flags) {
        return request.getRegistrationType() == RegistrationRequest.REGISTER_SOURCE
                ? flags::getEnforceForegroundStatusForMeasurementRegisterSource
                : flags::getEnforceForegroundStatusForMeasurementRegisterTrigger;
    }

    private boolean invokeCallbackOnFailureOnRvc(IMeasurementCallback callback) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            String errorMsg = "AdServices is not supported on Android R";
            MeasurementErrorResponse response =
                    new MeasurementErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .setErrorMessage(errorMsg)
                            .build();

            try {
                callback.onFailure(response);
            } catch (RemoteException e) {
                LogUtil.e(e, String.format("Fail to call the callback. %s", errorMsg));
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            }
            return true;
        }
        return false;
    }
}
