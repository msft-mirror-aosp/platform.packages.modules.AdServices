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

package com.android.adservices.service.adselection;

import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__FLEDGE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES;

import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionFromOutcomesInput;
import android.adservices.adselection.AdSelectionInput;
import android.adservices.adselection.AdSelectionOverrideCallback;
import android.adservices.adselection.AdSelectionService;
import android.adservices.adselection.ReportImpressionCallback;
import android.adservices.adselection.ReportImpressionInput;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.CallerMetadata;
import android.annotation.NonNull;
import android.content.Context;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AdServicesHttpsClient;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.CallingAppUidSupplier;
import com.android.adservices.service.common.CallingAppUidSupplierBinderImpl;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.AdSelectionOverrider;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.service.stats.AdSelectionExecutionLogger;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.Clock;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Implementation of {@link AdSelectionService}.
 *
 * @hide
 */
public class AdSelectionServiceImpl extends AdSelectionService.Stub {

    @NonNull private final AdSelectionEntryDao mAdSelectionEntryDao;
    @NonNull private final CustomAudienceDao mCustomAudienceDao;
    @NonNull private final AdServicesHttpsClient mAdServicesHttpsClient;
    @NonNull private final ExecutorService mLightweightExecutor;
    @NonNull private final ExecutorService mBackgroundExecutor;
    @NonNull private final ScheduledThreadPoolExecutor mScheduledExecutor;
    @NonNull private final Context mContext;
    @NonNull private final ConsentManager mConsentManager;
    @NonNull private final DevContextFilter mDevContextFilter;
    @NonNull private final AppImportanceFilter mAppImportanceFilter;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final Flags mFlags;
    @NonNull private final CallingAppUidSupplier mCallingAppUidSupplier;
    @NonNull private final FledgeAuthorizationFilter mFledgeAuthorizationFilter;
    @NonNull private final FledgeAllowListsFilter mFledgeAllowListsFilter;

    private static final String API_NOT_AUTHORIZED_MSG =
            "This API is not enabled for the given app because either dev options are disabled or"
                    + " the app is not debuggable.";

    @VisibleForTesting
    public AdSelectionServiceImpl(
            @NonNull AdSelectionEntryDao adSelectionEntryDao,
            @NonNull CustomAudienceDao customAudienceDao,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull DevContextFilter devContextFilter,
            @NonNull AppImportanceFilter appImportanceFilter,
            @NonNull ExecutorService lightweightExecutorService,
            @NonNull ExecutorService backgroundExecutorService,
            @NonNull ScheduledThreadPoolExecutor scheduledExecutor,
            @NonNull Context context,
            ConsentManager consentManager,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull Flags flags,
            @NonNull CallingAppUidSupplier callingAppUidSupplier,
            @NonNull FledgeAuthorizationFilter fledgeAuthorizationFilter,
            @NonNull FledgeAllowListsFilter fledgeAllowListsFilter) {
        Objects.requireNonNull(context, "Context must be provided.");
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(adServicesHttpsClient);
        Objects.requireNonNull(devContextFilter);
        Objects.requireNonNull(appImportanceFilter);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(scheduledExecutor);
        Objects.requireNonNull(consentManager);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(callingAppUidSupplier);
        Objects.requireNonNull(fledgeAuthorizationFilter);

        mAdSelectionEntryDao = adSelectionEntryDao;
        mCustomAudienceDao = customAudienceDao;
        mAdServicesHttpsClient = adServicesHttpsClient;
        mDevContextFilter = devContextFilter;
        mAppImportanceFilter = appImportanceFilter;
        mLightweightExecutor = lightweightExecutorService;
        mBackgroundExecutor = backgroundExecutorService;
        mScheduledExecutor = scheduledExecutor;
        mContext = context;
        mConsentManager = consentManager;
        mAdServicesLogger = adServicesLogger;
        mFlags = flags;
        mCallingAppUidSupplier = callingAppUidSupplier;
        mFledgeAuthorizationFilter = fledgeAuthorizationFilter;
        mFledgeAllowListsFilter = fledgeAllowListsFilter;
    }

    /** Creates a new instance of {@link AdSelectionServiceImpl}. */
    public static AdSelectionServiceImpl create(@NonNull Context context) {
        return new AdSelectionServiceImpl(context);
    }

    /** Creates an instance of {@link AdSelectionServiceImpl} to be used. */
    private AdSelectionServiceImpl(@NonNull Context context) {
        this(
                AdSelectionDatabase.getInstance(context).adSelectionEntryDao(),
                CustomAudienceDatabase.getInstance(context).customAudienceDao(),
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        CacheProviderFactory.create(context, FlagsFactory.getFlags())),
                DevContextFilter.create(context),
                AppImportanceFilter.create(
                        context,
                        AD_SERVICES_API_CALLED__API_CLASS__FLEDGE,
                        () -> FlagsFactory.getFlags().getForegroundStatuslLevelForValidation()),
                AdServicesExecutors.getLightWeightExecutor(),
                AdServicesExecutors.getBackgroundExecutor(),
                AdServicesExecutors.getScheduler(),
                context,
                ConsentManager.getInstance(context),
                AdServicesLoggerImpl.getInstance(),
                FlagsFactory.getFlags(),
                CallingAppUidSupplierBinderImpl.create(),
                FledgeAuthorizationFilter.create(context, AdServicesLoggerImpl.getInstance()),
                new FledgeAllowListsFilter(
                        FlagsFactory.getFlags(), AdServicesLoggerImpl.getInstance()));
    }

    // TODO(b/233116758): Validate all the fields inside the adSelectionConfig.
    @Override
    public void selectAds(
            @NonNull AdSelectionInput inputParams,
            @NonNull CallerMetadata callerMetadata,
            @NonNull AdSelectionCallback callback) {
        final AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        callerMetadata, Clock.SYSTEM_CLOCK, mContext, mAdServicesLogger);
        int apiName = AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS;
        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredPermission(mContext, apiName);
        try {
            Objects.requireNonNull(inputParams);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            int overallLatencyMs = adSelectionExecutionLogger.getRunAdSelectionOverallLatencyInMs();
            LogUtil.v(
                    "The selectAds(AdSelectionConfig) arguments should not be null, failed with"
                            + " overall latency %d in ms.",
                    overallLatencyMs);
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, STATUS_INVALID_ARGUMENT, overallLatencyMs);
            // Rethrow because we want to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();
        int callerUid = getCallingUid(apiName);
        mLightweightExecutor.execute(
                () -> {
                    // TODO(b/249298855): Evolve off device ad selection logic.
                    if (mFlags.getAdSelectionOffDeviceEnabled()) {
                        runOffDeviceAdSelection(
                                devContext,
                                callerUid,
                                inputParams,
                                callback,
                                adSelectionExecutionLogger);
                    } else {
                        runOnDeviceAdSelection(
                                devContext,
                                callerUid,
                                inputParams,
                                callback,
                                adSelectionExecutionLogger);
                    }
                });
    }

    private void runOnDeviceAdSelection(
            DevContext devContext,
            int callerUid,
            @NonNull AdSelectionInput inputParams,
            @NonNull AdSelectionCallback callback,
            @NonNull AdSelectionExecutionLogger adSelectionExecutionLogger) {
        OnDeviceAdSelectionRunner runner =
                new OnDeviceAdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mAdServicesHttpsClient,
                        mLightweightExecutor,
                        mBackgroundExecutor,
                        mScheduledExecutor,
                        mConsentManager,
                        mAdServicesLogger,
                        devContext,
                        mAppImportanceFilter,
                        mFlags,
                        () -> Throttler.getInstance(mFlags),
                        callerUid,
                        mFledgeAuthorizationFilter,
                        mFledgeAllowListsFilter,
                        adSelectionExecutionLogger);
        runner.runAdSelection(inputParams, callback);
    }

    private void runOffDeviceAdSelection(
            DevContext devContext,
            int callerUid,
            @NonNull AdSelectionInput inputParams,
            @NonNull AdSelectionCallback callback,
            @NonNull AdSelectionExecutionLogger adSelectionExecutionLogger) {
        TrustedServerAdSelectionRunner runner =
                new TrustedServerAdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mAdServicesHttpsClient,
                        mLightweightExecutor,
                        mBackgroundExecutor,
                        mScheduledExecutor,
                        mConsentManager,
                        mAdServicesLogger,
                        devContext,
                        mAppImportanceFilter,
                        mFlags,
                        () -> Throttler.getInstance(mFlags),
                        callerUid,
                        mFledgeAuthorizationFilter,
                        mFledgeAllowListsFilter,
                        adSelectionExecutionLogger);
        runner.runAdSelection(inputParams, callback);
    }

    /**
     * Returns an ultimate winner ad of given list of previous winner ads.
     *
     * @param inputParams includes list of outcomes, signals and uri to download selection logic
     * @param callerMetadata caller's metadata for stat logging
     * @param callback delivers the results via OutcomeReceiver
     * @throws RemoteException thrown in case callback fails
     */
    @Override
    public void selectAdsFromOutcomes(
            @NonNull AdSelectionFromOutcomesInput inputParams,
            @NonNull CallerMetadata callerMetadata,
            @NonNull AdSelectionCallback callback)
            throws RemoteException {
        // TODO (b/258604183): This endpoint suppose to be an overload of selectAds but .aidl
        //  doesn't allow overloading. Investigation where to go from here

        // TODO(b/258836148): Use proper short name for this endpoint when there is one created
        int apiName = AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredPermission(mContext, apiName);

        // TODO(257134800): Add telemetry
        try {
            Objects.requireNonNull(inputParams);
            Objects.requireNonNull(callback);
        } catch (NullPointerException e) {
            LogUtil.v(
                    "The selectAds(AdSelectionFromOutcomesConfig) arguments should not be null,"
                            + " failed");
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, 0);
            // Rethrow because we want to fail fast
            throw e;
        }

        DevContext devContext = mDevContextFilter.createDevContext();
        int callerUid = getCallingUid(apiName);
        mLightweightExecutor.execute(
                () -> {
                    OutcomeSelectionRunner runner =
                            new OutcomeSelectionRunner(
                                    callerUid,
                                    mAdSelectionEntryDao,
                                    mBackgroundExecutor,
                                    mLightweightExecutor,
                                    mScheduledExecutor,
                                    mAdServicesHttpsClient,
                                    mAdServicesLogger,
                                    mFledgeAuthorizationFilter,
                                    mAppImportanceFilter,
                                    () -> Throttler.getInstance(mFlags),
                                    mFledgeAllowListsFilter,
                                    mConsentManager,
                                    devContext,
                                    mContext,
                                    mFlags);
                    runner.runOutcomeSelection(inputParams, callback);
                });
    }

    @Override
    public void reportImpression(
            @NonNull ReportImpressionInput requestParams,
            @NonNull ReportImpressionCallback callback) {
        int apiName = AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION;

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredPermission(mContext, apiName);

        try {
            Objects.requireNonNull(requestParams);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(apiName, STATUS_INVALID_ARGUMENT, 0);
            // Rethrow because we want to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        ImpressionReporter reporter =
                new ImpressionReporter(
                        mContext,
                        mLightweightExecutor,
                        mBackgroundExecutor,
                        mScheduledExecutor,
                        mAdSelectionEntryDao,
                        mAdServicesHttpsClient,
                        mConsentManager,
                        devContext,
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        mFlags,
                        () -> Throttler.getInstance(mFlags),
                        getCallingUid(apiName),
                        mFledgeAuthorizationFilter,
                        mFledgeAllowListsFilter);
        reporter.reportImpression(requestParams, callback);
    }

    @Override
    public void overrideAdSelectionConfigRemoteInfo(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull String decisionLogicJS,
            @NonNull AdSelectionSignals trustedScoringSignals,
            @NonNull AdSelectionOverrideCallback callback) {
        int apiName = AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO;

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredPermission(mContext, apiName);

        try {
            Objects.requireNonNull(adSelectionConfig);
            Objects.requireNonNull(decisionLogicJS);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(apiName, STATUS_INVALID_ARGUMENT, 0);
            // Rethrow because we want to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, AdServicesStatusUtils.STATUS_INTERNAL_ERROR, 0);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        AdSelectionOverrider overrider =
                new AdSelectionOverrider(
                        devContext,
                        mAdSelectionEntryDao,
                        mLightweightExecutor,
                        mBackgroundExecutor,
                        mContext.getPackageManager(),
                        mConsentManager,
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        mFlags,
                        getCallingUid(apiName));

        overrider.addOverride(adSelectionConfig, decisionLogicJS, trustedScoringSignals, callback);
    }

    private int getCallingUid(int apiNameLoggingId) {
        try {
            return mCallingAppUidSupplier.getCallingAppUid();
        } catch (IllegalStateException illegalStateException) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiNameLoggingId, AdServicesStatusUtils.STATUS_INTERNAL_ERROR, 0);
            throw illegalStateException;
        }
    }

    @Override
    public void removeAdSelectionConfigRemoteInfoOverride(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull AdSelectionOverrideCallback callback) {
        // Auto-generated variable name is too long for lint check
        int apiName =
                AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE;

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredPermission(mContext, apiName);

        try {
            Objects.requireNonNull(adSelectionConfig);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(apiName, STATUS_INVALID_ARGUMENT, 0);
            // Rethrow because we want to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, AdServicesStatusUtils.STATUS_INTERNAL_ERROR, 0);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        AdSelectionOverrider overrider =
                new AdSelectionOverrider(
                        devContext,
                        mAdSelectionEntryDao,
                        mLightweightExecutor,
                        mBackgroundExecutor,
                        mContext.getPackageManager(),
                        mConsentManager,
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        mFlags,
                        getCallingUid(apiName));

        overrider.removeOverride(adSelectionConfig, callback);
    }

    @Override
    public void resetAllAdSelectionConfigRemoteOverrides(
            @NonNull AdSelectionOverrideCallback callback) {
        // Auto-generated variable name is too long for lint check
        int apiName =
                AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES;

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredPermission(mContext, apiName);

        try {
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(apiName, STATUS_INVALID_ARGUMENT, 0);
            // Rethrow because we want to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, AdServicesStatusUtils.STATUS_INTERNAL_ERROR, 0);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        AdSelectionOverrider overrider =
                new AdSelectionOverrider(
                        devContext,
                        mAdSelectionEntryDao,
                        mLightweightExecutor,
                        mBackgroundExecutor,
                        mContext.getPackageManager(),
                        mConsentManager,
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        mFlags,
                        getCallingUid(apiName));

        overrider.removeAllOverridesForAdSelectionConfig(callback);
    }

    @Override
    public void overrideAdSelectionFromOutcomesConfigRemoteInfo(
            @NonNull AdSelectionFromOutcomesConfig config,
            @NonNull String selectionLogicJs,
            @NonNull AdSelectionSignals selectionSignals,
            @NonNull AdSelectionOverrideCallback callback) {
        int apiName = AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredPermission(mContext, apiName);

        try {
            Objects.requireNonNull(config);
            Objects.requireNonNull(selectionLogicJs);
            Objects.requireNonNull(selectionSignals);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, 0);
            // Rethrow because we want to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, AdServicesStatusUtils.STATUS_INTERNAL_ERROR, 0);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        AdSelectionOverrider overrider =
                new AdSelectionOverrider(
                        devContext,
                        mAdSelectionEntryDao,
                        mLightweightExecutor,
                        mBackgroundExecutor,
                        mContext.getPackageManager(),
                        mConsentManager,
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        mFlags,
                        getCallingUid(apiName));

        overrider.addOverride(config, selectionLogicJs, selectionSignals, callback);
    }

    @Override
    public void removeAdSelectionFromOutcomesConfigRemoteInfoOverride(
            @NonNull AdSelectionFromOutcomesConfig config,
            @NonNull AdSelectionOverrideCallback callback) {
        // Auto-generated variable name is too long for lint check
        int apiName = AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredPermission(mContext, apiName);

        try {
            Objects.requireNonNull(config);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(apiName, STATUS_INVALID_ARGUMENT, 0);
            // Rethrow because we want to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, AdServicesStatusUtils.STATUS_INTERNAL_ERROR, 0);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        AdSelectionOverrider overrider =
                new AdSelectionOverrider(
                        devContext,
                        mAdSelectionEntryDao,
                        mLightweightExecutor,
                        mBackgroundExecutor,
                        mContext.getPackageManager(),
                        mConsentManager,
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        mFlags,
                        getCallingUid(apiName));

        overrider.removeOverride(config, callback);
    }

    @Override
    public void resetAllAdSelectionFromOutcomesConfigRemoteOverrides(
            @NonNull AdSelectionOverrideCallback callback) {
        // Auto-generated variable name is too long for lint check
        int apiName = AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredPermission(mContext, apiName);

        try {
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(apiName, STATUS_INVALID_ARGUMENT, 0);
            // Rethrow because we want to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, AdServicesStatusUtils.STATUS_INTERNAL_ERROR, 0);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        AdSelectionOverrider overrider =
                new AdSelectionOverrider(
                        devContext,
                        mAdSelectionEntryDao,
                        mLightweightExecutor,
                        mBackgroundExecutor,
                        mContext.getPackageManager(),
                        mConsentManager,
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        mFlags,
                        getCallingUid(apiName));

        overrider.removeAllOverridesForAdSelectionFromOutcomes(callback);
    }

    /** Close down method to be invoked when the PPAPI process is shut down. */
    @SuppressWarnings("FutureReturnValueIgnored")
    public void destroy() {
        LogUtil.i("Shutting down AdSelectionService");
        JSScriptEngine.getInstance(mContext).shutdown();
    }
}
