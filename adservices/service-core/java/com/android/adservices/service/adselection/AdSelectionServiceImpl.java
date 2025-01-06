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

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_KILLSWITCH_ENABLED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__UPDATE_AD_COUNTER_HISTOGRAM;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__AD_SELECTION_SERVICE_AUCTION_SERVER_API_NOT_AVAILABLE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__AD_SELECTION_SERVICE_GET_CALLING_UID_ILLEGAL_STATE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__AD_SELECTION_SERVICE_NULL_ARGUMENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PERSIST_AD_SELECTION_RESULT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__REPORT_IMPRESSION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__REPORT_INTERACTION;

import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionFromOutcomesInput;
import android.adservices.adselection.AdSelectionInput;
import android.adservices.adselection.AdSelectionOverrideCallback;
import android.adservices.adselection.AdSelectionService;
import android.adservices.adselection.GetAdSelectionDataCallback;
import android.adservices.adselection.GetAdSelectionDataInput;
import android.adservices.adselection.PerBuyerDecisionLogic;
import android.adservices.adselection.PersistAdSelectionResultCallback;
import android.adservices.adselection.PersistAdSelectionResultInput;
import android.adservices.adselection.RemoveAdCounterHistogramOverrideInput;
import android.adservices.adselection.ReportImpressionCallback;
import android.adservices.adselection.ReportImpressionInput;
import android.adservices.adselection.ReportInteractionCallback;
import android.adservices.adselection.ReportInteractionInput;
import android.adservices.adselection.SetAdCounterHistogramOverrideInput;
import android.adservices.adselection.SetAppInstallAdvertisersCallback;
import android.adservices.adselection.SetAppInstallAdvertisersInput;
import android.adservices.adselection.UpdateAdCounterHistogramCallback;
import android.adservices.adselection.UpdateAdCounterHistogramInput;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesPermissions;
import android.adservices.common.CallerMetadata;
import android.annotation.NonNull;
import android.content.Context;
import android.os.Binder;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionDebugReportDao;
import com.android.adservices.data.adselection.AdSelectionDebugReportingDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adid.AdIdWorker;
import com.android.adservices.service.adselection.debug.AuctionServerDebugConfigurationGenerator;
import com.android.adservices.service.adselection.debug.ConsentedDebugConfigurationGeneratorFactory;
import com.android.adservices.service.adselection.debug.DebugReporting;
import com.android.adservices.service.adselection.debug.DebugReportingDisabled;
import com.android.adservices.service.common.AdRenderIdValidator;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.BinderFlagReader;
import com.android.adservices.service.common.CallingAppUidSupplier;
import com.android.adservices.service.common.CallingAppUidSupplierBinderImpl;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeApiThrottleFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.FledgeConsentFilter;
import com.android.adservices.service.common.RetryStrategyFactory;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.AdSelectionOverrider;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.js.JSSandboxIsNotAvailableException;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.service.kanon.KAnonSignJoinFactory;
import com.android.adservices.service.measurement.MeasurementImpl;
import com.android.adservices.service.profiling.Tracing;
import com.android.adservices.service.stats.AdSelectionExecutionLogger;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.AdsRelevanceExecutionLogger;
import com.android.adservices.service.stats.AdsRelevanceExecutionLoggerFactory;
import com.android.adservices.service.stats.AdsRelevanceStatusUtils;
import com.android.adservices.service.stats.ReportImpressionExecutionLogger;
import com.android.adservices.service.stats.ReportImpressionExecutionLoggerFactory;
import com.android.adservices.service.stats.SelectAdsFromOutcomesExecutionLogger;
import com.android.adservices.service.stats.SelectAdsFromOutcomesExecutionLoggerFactory;
import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Implementation of {@link AdSelectionService}.
 *
 * @hide
 */
@RequiresApi(Build.VERSION_CODES.S)
public class AdSelectionServiceImpl extends AdSelectionService.Stub {
    @VisibleForTesting
    static final String AUCTION_SERVER_API_IS_NOT_AVAILABLE =
            "Auction Server API is not available!";

    @VisibleForTesting
    public static final Set<String> PERMISSIONS_SET =
            new HashSet<>(
                    Arrays.asList(
                            AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE,
                            AdServicesPermissions.ACCESS_ADSERVICES_PROTECTED_SIGNALS,
                            AdServicesPermissions.ACCESS_ADSERVICES_AD_SELECTION));

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final AdSelectionEntryDao mAdSelectionEntryDao;
    @NonNull private final AppInstallDao mAppInstallDao;
    @NonNull private final CustomAudienceDao mCustomAudienceDao;
    @NonNull private final EncodedPayloadDao mEncodedPayloadDao;
    @NonNull private final FrequencyCapDao mFrequencyCapDao;
    @NonNull private final EncryptionKeyDao mEncryptionKeyDao;
    @NonNull private final EnrollmentDao mEnrollmentDao;
    @NonNull private final AdServicesHttpsClient mAdServicesHttpsClient;
    @NonNull private final ExecutorService mLightweightExecutor;
    @NonNull private final ExecutorService mBackgroundExecutor;
    @NonNull private final ScheduledThreadPoolExecutor mScheduledExecutor;
    @NonNull private final Context mContext;
    @NonNull private final DevContextFilter mDevContextFilter;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final Flags mFlags;
    @NonNull private final DebugFlags mDebugFlags;
    @NonNull private final CallingAppUidSupplier mCallingAppUidSupplier;
    @NonNull private final FledgeAuthorizationFilter mFledgeAuthorizationFilter;
    @NonNull private final AdSelectionServiceFilter mAdSelectionServiceFilter;
    @NonNull private final AdFilteringFeatureFactory mAdFilteringFeatureFactory;
    @NonNull private final ConsentManager mConsentManager;
    @NonNull private final AdRenderIdValidator mAdRenderIdValidator;
    @NonNull private final AdSelectionDebugReportDao mAdSelectionDebugReportDao;
    @NonNull private final AdIdFetcher mAdIdFetcher;
    @NonNull private final MultiCloudSupportStrategy mMultiCloudSupportStrategy;
    @NonNull KAnonSignJoinFactory mKAnonSignJoinFactory;
    private final boolean mShouldUseUnifiedTables;
    private static final String API_NOT_AUTHORIZED_MSG =
            "This API is not enabled for the given app because either dev options are disabled or"
                    + " the app is not debuggable.";
    @NonNull private final RetryStrategyFactory mRetryStrategyFactory;

    private final boolean mConsoleMessageInLogsEnabled;

    @NonNull
    private final AuctionServerDebugConfigurationGenerator
            mAuctionServerDebugConfigurationGenerator;

    @VisibleForTesting
    public AdSelectionServiceImpl(
            @NonNull AdSelectionEntryDao adSelectionEntryDao,
            @NonNull AppInstallDao appInstallDao,
            @NonNull CustomAudienceDao customAudienceDao,
            @NonNull EncodedPayloadDao encodedPayloadDao,
            @NonNull FrequencyCapDao frequencyCapDao,
            @NonNull EncryptionKeyDao encryptionKeyDao,
            @NonNull EnrollmentDao enrollmentDao,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull DevContextFilter devContextFilter,
            @NonNull ExecutorService lightweightExecutorService,
            @NonNull ExecutorService backgroundExecutorService,
            @NonNull ScheduledThreadPoolExecutor scheduledExecutor,
            @NonNull Context context,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull Flags flags,
            @NonNull DebugFlags debugFlags,
            @NonNull CallingAppUidSupplier callingAppUidSupplier,
            @NonNull FledgeAuthorizationFilter fledgeAuthorizationFilter,
            @NonNull AdSelectionServiceFilter adSelectionServiceFilter,
            @NonNull AdFilteringFeatureFactory adFilteringFeatureFactory,
            @NonNull ConsentManager consentManager,
            @NonNull MultiCloudSupportStrategy multiCloudSupportStrategy,
            @NonNull AdSelectionDebugReportDao adSelectionDebugReportDao,
            @NonNull AdIdFetcher adIdFetcher,
            @NonNull KAnonSignJoinFactory kAnonSignJoinFactory,
            boolean shouldUseUnifiedTables,
            @NonNull RetryStrategyFactory retryStrategyFactory,
            boolean consoleMessageInLogsEnabled,
            @NonNull
                    AuctionServerDebugConfigurationGenerator
                            auctionServerDebugConfigurationGenerator) {
        Objects.requireNonNull(context, "Context must be provided.");
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(appInstallDao);
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(encodedPayloadDao);
        Objects.requireNonNull(frequencyCapDao);
        Objects.requireNonNull(encryptionKeyDao);
        Objects.requireNonNull(enrollmentDao);
        Objects.requireNonNull(adServicesHttpsClient);
        Objects.requireNonNull(devContextFilter);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(scheduledExecutor);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(debugFlags);
        Objects.requireNonNull(adFilteringFeatureFactory);
        Objects.requireNonNull(consentManager);
        Objects.requireNonNull(multiCloudSupportStrategy);
        Objects.requireNonNull(adSelectionDebugReportDao);
        Objects.requireNonNull(adIdFetcher);
        Objects.requireNonNull(kAnonSignJoinFactory);
        Objects.requireNonNull(retryStrategyFactory);
        Objects.requireNonNull(auctionServerDebugConfigurationGenerator);

        mAdSelectionEntryDao = adSelectionEntryDao;
        mAppInstallDao = appInstallDao;
        mCustomAudienceDao = customAudienceDao;
        mEncodedPayloadDao = encodedPayloadDao;
        mFrequencyCapDao = frequencyCapDao;
        mEncryptionKeyDao = encryptionKeyDao;
        mEnrollmentDao = enrollmentDao;
        mAdServicesHttpsClient = adServicesHttpsClient;
        mDevContextFilter = devContextFilter;
        mLightweightExecutor = lightweightExecutorService;
        mBackgroundExecutor = backgroundExecutorService;
        mScheduledExecutor = scheduledExecutor;
        mContext = context;
        mAdServicesLogger = adServicesLogger;
        mFlags = flags;
        mDebugFlags = debugFlags;
        mCallingAppUidSupplier = callingAppUidSupplier;
        mFledgeAuthorizationFilter = fledgeAuthorizationFilter;
        mAdSelectionServiceFilter = adSelectionServiceFilter;
        mAdFilteringFeatureFactory = adFilteringFeatureFactory;
        mConsentManager = consentManager;
        // No support for renderId on device
        mAdRenderIdValidator = AdRenderIdValidator.AD_RENDER_ID_VALIDATOR_NO_OP;
        mMultiCloudSupportStrategy = multiCloudSupportStrategy;
        mAdSelectionDebugReportDao = adSelectionDebugReportDao;
        mAdIdFetcher = adIdFetcher;
        mShouldUseUnifiedTables = shouldUseUnifiedTables;
        mKAnonSignJoinFactory = kAnonSignJoinFactory;
        mRetryStrategyFactory = retryStrategyFactory;
        mAuctionServerDebugConfigurationGenerator = auctionServerDebugConfigurationGenerator;
        mConsoleMessageInLogsEnabled = consoleMessageInLogsEnabled;
    }

    /** Creates a new instance of {@link AdSelectionServiceImpl}. */
    @SuppressWarnings("AvoidStaticContext") // Factory method
    public static AdSelectionServiceImpl create(@NonNull Context context) {
        return new AdSelectionServiceImpl(context);
    }

    /** Creates an instance of {@link AdSelectionServiceImpl} to be used. */
    private AdSelectionServiceImpl(@NonNull Context context) {
        this(
                AdSelectionDatabase.getInstance().adSelectionEntryDao(),
                SharedStorageDatabase.getInstance().appInstallDao(),
                CustomAudienceDatabase.getInstance().customAudienceDao(),
                ProtectedSignalsDatabase.getInstance().getEncodedPayloadDao(),
                SharedStorageDatabase.getInstance().frequencyCapDao(),
                EncryptionKeyDao.getInstance(),
                EnrollmentDao.getInstance(),
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        CacheProviderFactory.create(context, FlagsFactory.getFlags())),
                DevContextFilter.create(
                        context,
                        BinderFlagReader.readFlag(
                                () ->
                                        DebugFlags.getInstance()
                                                .getDeveloperSessionFeatureEnabled())),
                AdServicesExecutors.getLightWeightExecutor(),
                AdServicesExecutors.getBackgroundExecutor(),
                AdServicesExecutors.getScheduler(),
                context,
                AdServicesLoggerImpl.getInstance(),
                FlagsFactory.getFlags(),
                DebugFlags.getInstance(),
                CallingAppUidSupplierBinderImpl.create(),
                FledgeAuthorizationFilter.create(context, AdServicesLoggerImpl.getInstance()),
                new AdSelectionServiceFilter(
                        context,
                        new FledgeConsentFilter(
                                ConsentManager.getInstance(), AdServicesLoggerImpl.getInstance()),
                        FlagsFactory.getFlags(),
                        AppImportanceFilter.create(
                                context,
                                () ->
                                        FlagsFactory.getFlags()
                                                .getForegroundStatuslLevelForValidation()),
                        FledgeAuthorizationFilter.create(
                                context, AdServicesLoggerImpl.getInstance()),
                        new FledgeAllowListsFilter(
                                FlagsFactory.getFlags(), AdServicesLoggerImpl.getInstance()),
                        new FledgeApiThrottleFilter(
                                Throttler.getInstance(), AdServicesLoggerImpl.getInstance())),
                new AdFilteringFeatureFactory(
                        SharedStorageDatabase.getInstance().appInstallDao(),
                        SharedStorageDatabase.getInstance().frequencyCapDao(),
                        FlagsFactory.getFlags()),
                ConsentManager.getInstance(),
                MultiCloudSupportStrategyFactory.getStrategy(
                        FlagsFactory.getFlags().getFledgeAuctionServerMultiCloudEnabled(),
                        FlagsFactory.getFlags().getFledgeAuctionServerCoordinatorUrlAllowlist()),
                AdSelectionDebugReportingDatabase.getInstance().getAdSelectionDebugReportDao(),
                new AdIdFetcher(
                        context,
                        AdIdWorker.getInstance(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        AdServicesExecutors.getScheduler()),
                new KAnonSignJoinFactory(context),
                BinderFlagReader.readFlag(
                        () ->
                                FlagsFactory.getFlags()
                                        .getFledgeOnDeviceAuctionShouldUseUnifiedTables()),
                RetryStrategyFactory.createInstance(
                        BinderFlagReader.readFlag(
                                () -> FlagsFactory.getFlags().getAdServicesRetryStrategyEnabled()),
                        AdServicesExecutors.getLightWeightExecutor()),
                BinderFlagReader.readFlag(
                        () ->
                                DebugFlags.getInstance()
                                        .getAdServicesJsIsolateConsoleMessagesInLogsEnabled()),
                new AuctionServerDebugConfigurationGenerator(
                        BinderFlagReader.readFlag(
                                () -> FlagsFactory.getFlags().getAdIdKillSwitch()),
                        BinderFlagReader.readFlag(
                                () ->
                                        FlagsFactory.getFlags()
                                                .getFledgeAuctionServerAdIdFetcherTimeoutMs()),
                        BinderFlagReader.readFlag(
                                () ->
                                        FlagsFactory.getFlags()
                                                .getFledgeAuctionServerEnableDebugReporting()),
                        BinderFlagReader.readFlag(
                                () ->
                                        FlagsFactory.getFlags()
                                                .getFledgeAuctionServerEnablePasUnlimitedEgress()),
                        BinderFlagReader.readFlag(
                                () -> FlagsFactory.getFlags().getEnableProdDebugInAuctionServer()),
                        new AdIdFetcher(
                                context,
                                AdIdWorker.getInstance(),
                                AdServicesExecutors.getLightWeightExecutor(),
                                AdServicesExecutors.getScheduler()),
                        new ConsentedDebugConfigurationGeneratorFactory(
                                        BinderFlagReader.readFlag(
                                                () ->
                                                        DebugFlags.getInstance()
                                                                .getFledgeAuctionServerConsentedDebuggingEnabled()),
                                        AdSelectionDatabase.getInstance()
                                                .consentedDebugConfigurationDao())
                                .create(),
                        AdServicesExecutors.getLightWeightExecutor()));
    }

    @Override
    public void getAdSelectionData(
            GetAdSelectionDataInput inputParams,
            CallerMetadata callerMetadata,
            GetAdSelectionDataCallback callback)
            throws RemoteException {
        int e2eTraceCookie = Tracing.beginAsyncSection(Tracing.GET_AD_SELECTION_DATA);
        int onBinderThreadTraceCookie =
                Tracing.beginAsyncSection(Tracing.GET_AD_SELECTION_ON_DATA_BINDER_THREAD);

        int apiName = AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA;

        AdsRelevanceExecutionLoggerFactory adsRelevanceExecutionLoggerFactory =
                new AdsRelevanceExecutionLoggerFactory(
                        inputParams.getCallerPackageName(),
                        callerMetadata,
                        Clock.getInstance(),
                        mAdServicesLogger,
                        mFlags,
                        apiName);
        final AdsRelevanceExecutionLogger adsRelevanceExecutionLogger =
                adsRelevanceExecutionLoggerFactory.getAdsRelevanceExecutionLogger();

        if (BinderFlagReader.readFlag(mFlags::getFledgeAuctionServerKillSwitch)) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    inputParams.getCallerPackageName(),
                    STATUS_KILLSWITCH_ENABLED,
                    /* latencyMs= */ 0);
            // TODO(b/376542959): replace this temporary solution for CEL inside Binder thread.
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__AD_SELECTION_SERVICE_AUCTION_SERVER_API_NOT_AVAILABLE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA);
            throw new IllegalStateException(AUCTION_SERVER_API_IS_NOT_AVAILABLE);
        }

        try {
            Objects.requireNonNull(inputParams);
            Objects.requireNonNull(inputParams.getSeller());
            Objects.requireNonNull(callback);
        } catch (NullPointerException e) {
            sLogger.v("The getAdSelectionData() arguments should not be null!");
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    inputParams.getCallerPackageName(),
                    STATUS_INVALID_ARGUMENT,
                    /* latencyMs= */ 0);
            // TODO(b/376542959): replace this temporary solution for CEL inside Binder thread.
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__AD_SELECTION_SERVICE_NULL_ARGUMENT,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA);
            // Rethrow because we want to fail fast
            throw e;
        }

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredAnyPermission(
                mContext, inputParams.getCallerPackageName(), apiName, PERMISSIONS_SET);

        int callingUid = getCallingUid(apiName);
        final DevContext devContext = mDevContextFilter.createDevContext();
        Tracing.endAsyncSection(
                Tracing.GET_AD_SELECTION_ON_DATA_BINDER_THREAD, onBinderThreadTraceCookie);

        mLightweightExecutor.execute(
                () -> {
                    runGetAdSelectionData(
                            inputParams,
                            callback,
                            callingUid,
                            devContext,
                            adsRelevanceExecutionLogger,
                            e2eTraceCookie);
                });
    }

    @Override
    public void persistAdSelectionResult(
            PersistAdSelectionResultInput inputParams,
            CallerMetadata callerMetadata,
            PersistAdSelectionResultCallback callback)
            throws RemoteException {
        int traceCookie = Tracing.beginAsyncSection(Tracing.PERSIST_AD_SELECTION_RESULT);
        int apiName =
                AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT;

        AdsRelevanceExecutionLoggerFactory adsRelevanceExecutionLoggerFactory =
                new AdsRelevanceExecutionLoggerFactory(
                        inputParams.getCallerPackageName(),
                        callerMetadata,
                        Clock.getInstance(),
                        mAdServicesLogger,
                        mFlags,
                        apiName);
        final AdsRelevanceExecutionLogger adsRelevanceExecutionLogger =
                adsRelevanceExecutionLoggerFactory.getAdsRelevanceExecutionLogger();

        if (BinderFlagReader.readFlag(mFlags::getFledgeAuctionServerKillSwitch)) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    inputParams.getCallerPackageName(),
                    STATUS_KILLSWITCH_ENABLED,
                    /* latencyMs= */ 0);
            // TODO(b/376542959): replace this temporary solution for CEL inside Binder thread.
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__AD_SELECTION_SERVICE_AUCTION_SERVER_API_NOT_AVAILABLE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PERSIST_AD_SELECTION_RESULT);
            throw new IllegalStateException(AUCTION_SERVER_API_IS_NOT_AVAILABLE);
        }

        try {
            Objects.requireNonNull(inputParams);
            Objects.requireNonNull(inputParams.getSeller());
            Objects.requireNonNull(callback);
        } catch (NullPointerException e) {
            sLogger.v("The processAdSelectionResult() arguments should not be null!");
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    inputParams.getCallerPackageName(),
                    STATUS_INVALID_ARGUMENT,
                    /* latencyMs= */ 0);
            // TODO(b/376542959): replace this temporary solution for CEL inside Binder thread.
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__AD_SELECTION_SERVICE_NULL_ARGUMENT,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PERSIST_AD_SELECTION_RESULT);
            // Rethrow because we want to fail fast
            throw e;
        }

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredAnyPermission(
                mContext, inputParams.getCallerPackageName(), apiName, PERMISSIONS_SET);

        int callingUid = getCallingUid(apiName);
        final DevContext devContext = mDevContextFilter.createDevContext();
        final long overallTimeout =
                BinderFlagReader.readFlag(mFlags::getFledgeAuctionServerOverallTimeoutMs);
        final boolean forceSearchOnAbsentOwner =
                BinderFlagReader.readFlag(
                        mFlags::getFledgeAuctionServerForceSearchWhenOwnerIsAbsentEnabled);
        final boolean auctionServerEnabledForUpdateHistogram =
                BinderFlagReader.readFlag(mFlags::getFledgeAuctionServerEnabledForUpdateHistogram);

        PersistAdSelectionResultRunner.ReportingRegistrationLimits limits =
                PersistAdSelectionResultRunner.ReportingRegistrationLimits.builder()
                        .setMaxRegisteredAdBeaconsTotalCount(
                                BinderFlagReader.readFlag(
                                        mFlags
                                                ::getFledgeReportImpressionMaxRegisteredAdBeaconsTotalCount))
                        .setMaxInteractionKeySize(
                                BinderFlagReader.readFlag(
                                        mFlags
                                                ::getFledgeReportImpressionRegisteredAdBeaconsMaxInteractionKeySizeB))
                        .setMaxInteractionReportingUriSize(
                                BinderFlagReader.readFlag(
                                        mFlags
                                                ::getFledgeReportImpressionMaxInteractionReportingUriSizeB))
                        .setMaxRegisteredAdBeaconsPerAdTechCount(
                                BinderFlagReader.readFlag(
                                        mFlags
                                                ::getFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount))
                        .build();
        AuctionResultValidator auctionResultValidator =
                new AuctionResultValidator(
                        mFledgeAuthorizationFilter,
                        BinderFlagReader.readFlag(mFlags::getDisableFledgeEnrollmentCheck),
                        BinderFlagReader.readFlag(
                                mFlags::getEnableWinningSellerIdInAdSelectionOutcome));
        mLightweightExecutor.execute(
                () -> {
                    PersistAdSelectionResultRunner runner =
                            new PersistAdSelectionResultRunner(
                                    mMultiCloudSupportStrategy.getObliviousHttpEncryptor(
                                            mContext, mFlags),
                                    mAdSelectionEntryDao,
                                    mCustomAudienceDao,
                                    mAdSelectionServiceFilter,
                                    mBackgroundExecutor,
                                    mLightweightExecutor,
                                    mScheduledExecutor,
                                    callingUid,
                                    devContext,
                                    overallTimeout,
                                    forceSearchOnAbsentOwner,
                                    limits,
                                    mAdFilteringFeatureFactory.getAdCounterHistogramUpdater(
                                            mAdSelectionEntryDao,
                                            auctionServerEnabledForUpdateHistogram),
                                    auctionResultValidator,
                                    mFlags,
                                    mDebugFlags,
                                    mAdServicesLogger,
                                    adsRelevanceExecutionLogger,
                                    mKAnonSignJoinFactory);
                    runner.run(inputParams, callback);
                    Tracing.endAsyncSection(Tracing.PERSIST_AD_SELECTION_RESULT, traceCookie);
                });
    }

    // TODO(b/233116758): Validate all the fields inside the adSelectionConfig.
    @Override
    public void selectAds(
            @NonNull AdSelectionInput inputParams,
            @NonNull CallerMetadata callerMetadata,
            @NonNull AdSelectionCallback callback) {
        selectAds(inputParams, callerMetadata, callback, null);
    }

    /**
     * This method takes an extra callback which is triggered once all background tasks for ad
     * selection are complete. Only required for testing.
     */
    @VisibleForTesting
    public void selectAds(
            @NonNull AdSelectionInput inputParams,
            @NonNull CallerMetadata callerMetadata,
            @NonNull AdSelectionCallback partialCallback,
            @Nullable AdSelectionCallback fullCallback) {
        final AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        callerMetadata, Clock.getInstance(), mContext, mAdServicesLogger, mFlags);
        int apiName = AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS;

        try {
            Objects.requireNonNull(inputParams);
            Objects.requireNonNull(partialCallback);
        } catch (NullPointerException exception) {
            int overallLatencyMs = adSelectionExecutionLogger.getRunAdSelectionOverallLatencyInMs();
            sLogger.v(
                    "The selectAds(AdSelectionConfig) arguments should not be null, failed with"
                            + " overall latency %d in ms.",
                    overallLatencyMs);
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    inputParams.getCallerPackageName(),
                    STATUS_INVALID_ARGUMENT,
                    overallLatencyMs);
            // Rethrow because we want to fail fast
            throw exception;
        }

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredAnyPermission(
                mContext, inputParams.getCallerPackageName(), apiName, PERMISSIONS_SET);

        int callingUid = getCallingUid(apiName);

        DevContext devContext = mDevContextFilter.createDevContext();
        final boolean auctionServerEnabledForUpdateHistogram =
                BinderFlagReader.readFlag(mFlags::getFledgeAuctionServerEnabledForUpdateHistogram);
        mLightweightExecutor.execute(
                () ->
                        runAdSelection(
                                inputParams,
                                partialCallback,
                                fullCallback,
                                adSelectionExecutionLogger,
                                callingUid,
                                devContext,
                                auctionServerEnabledForUpdateHistogram));
    }

    private void runGetAdSelectionData(
            GetAdSelectionDataInput inputParams,
            GetAdSelectionDataCallback callback,
            int callingUid,
            DevContext devContext,
            AdsRelevanceExecutionLogger adsRelevanceExecutionLogger,
            int e2eTraceCookie) {
        int offBinderThreadTraceCookie =
                Tracing.beginAsyncSection(Tracing.GET_AD_SELECTION_DATA_OFF_BINDER_THREAD);
        GetAdSelectionDataRunner runner =
                new GetAdSelectionDataRunner(
                        mContext,
                        e2eTraceCookie,
                        mMultiCloudSupportStrategy,
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory.getFrequencyCapAdFilterer(),
                        mBackgroundExecutor,
                        mLightweightExecutor,
                        AdServicesExecutors.getBlockingExecutor(),
                        mScheduledExecutor,
                        mFlags,
                        mDebugFlags,
                        callingUid,
                        devContext,
                        adsRelevanceExecutionLogger,
                        mAdServicesLogger,
                        getAuctionServerPayloadMetricsStrategy(mFlags),
                        mAdFilteringFeatureFactory.getAppInstallAdFilterer(),
                        mAuctionServerDebugConfigurationGenerator);
        Tracing.endAsyncSection(
                Tracing.GET_AD_SELECTION_DATA_OFF_BINDER_THREAD, offBinderThreadTraceCookie);
        runner.run(inputParams, callback);
    }

    private AuctionServerPayloadMetricsStrategy getAuctionServerPayloadMetricsStrategy(
            Flags flags) {
        if (flags.getFledgeAuctionServerGetAdSelectionDataPayloadMetricsEnabled()) {
            SellerConfigurationMetricsStrategy sellerConfigurationMetricsStrategy;
            if (flags.getFledgeGetAdSelectionDataSellerConfigurationEnabled()) {
                sellerConfigurationMetricsStrategy =
                        new SellerConfigurationMetricsStrategyEnabled();
            } else {
                sellerConfigurationMetricsStrategy =
                        new SellerConfigurationMetricsStrategyDisabled();
            }
            if (flags.getFledgeAuctionServerKeyFetchMetricsEnabled()) {
                return new AuctionServerPayloadMetricsStrategyWithKeyFetchEnabled(
                        mAdServicesLogger, sellerConfigurationMetricsStrategy);
            }
            return new AuctionServerPayloadMetricsStrategyEnabled(
                    mAdServicesLogger, sellerConfigurationMetricsStrategy);
        }
        return new AuctionServerPayloadMetricsStrategyDisabled();
    }

    private void runAdSelection(
            AdSelectionInput inputParams,
            AdSelectionCallback partialCallback,
            @Nullable AdSelectionCallback fullCallback,
            AdSelectionExecutionLogger adSelectionExecutionLogger,
            int callingUid,
            DevContext devContext,
            boolean auctionServerEnabledForUpdateHistogram) {

        ListenableFuture<DebugReporting> debugReportingFuture =
                DebugReporting.createInstance(
                        mContext,
                        mFlags,
                        mAdServicesHttpsClient,
                        devContext,
                        mAdSelectionDebugReportDao,
                        mLightweightExecutor,
                        mAdIdFetcher,
                        inputParams.getCallerPackageName(),
                        callingUid);

        FluentFuture.from(debugReportingFuture)
                .addCallback(
                        new FutureCallback<>() {
                            @Override
                            public void onSuccess(DebugReporting debugReporting) {
                                sLogger.v(
                                        "Debug reporting enabled: %b", debugReporting.isEnabled());
                                runAdSelectionWithDebugReporting(
                                        inputParams,
                                        partialCallback,
                                        fullCallback,
                                        adSelectionExecutionLogger,
                                        callingUid,
                                        devContext,
                                        auctionServerEnabledForUpdateHistogram,
                                        debugReporting);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                sLogger.e(
                                        t,
                                        "Failed to create Debug Reporting instance, debug reporting"
                                                + " is disabled");
                                runAdSelectionWithDebugReporting(
                                        inputParams,
                                        partialCallback,
                                        fullCallback,
                                        adSelectionExecutionLogger,
                                        callingUid,
                                        devContext,
                                        auctionServerEnabledForUpdateHistogram,
                                        new DebugReportingDisabled());
                            }
                        },
                        mLightweightExecutor);
    }

    private void runAdSelectionWithDebugReporting(
            AdSelectionInput inputParams,
            AdSelectionCallback partialCallback,
            @Nullable AdSelectionCallback fullCallback,
            AdSelectionExecutionLogger adSelectionExecutionLogger,
            int callingUid,
            DevContext devContext,
            boolean auctionServerEnabledForUpdateHistogram,
            @NonNull DebugReporting debugReporting) {

        OnDeviceAdSelectionRunner runner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mAdServicesHttpsClient,
                        mLightweightExecutor,
                        mBackgroundExecutor,
                        mScheduledExecutor,
                        mAdServicesLogger,
                        devContext,
                        mFlags,
                        mDebugFlags,
                        adSelectionExecutionLogger,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory.getFrequencyCapAdFilterer(),
                        mAdFilteringFeatureFactory.getAdCounterKeyCopier(),
                        mAdFilteringFeatureFactory.getAdCounterHistogramUpdater(
                                mAdSelectionEntryDao, auctionServerEnabledForUpdateHistogram),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        debugReporting,
                        callingUid,
                        mShouldUseUnifiedTables,
                        mRetryStrategyFactory.createRetryStrategy(
                                mFlags.getAdServicesJsScriptEngineMaxRetryAttempts()),
                        mKAnonSignJoinFactory,
                        mAdFilteringFeatureFactory.getAppInstallAdFilterer(),
                        mConsoleMessageInLogsEnabled);
        runner.runAdSelection(inputParams, partialCallback, devContext, fullCallback);
    }

    /**
     * Returns an ultimate winner ad of given list of previous winner ads.
     *
     * @param inputParams includes list of outcomes, signals and uri to download selection logic
     * @param callerMetadata caller's metadata for stat logging
     * @param callback delivers the results via OutcomeReceiver
     */
    @Override
    public void selectAdsFromOutcomes(
            @NonNull AdSelectionFromOutcomesInput inputParams,
            @NonNull CallerMetadata callerMetadata,
            @NonNull AdSelectionCallback callback)
            throws RemoteException {
        int apiName = AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS_FROM_OUTCOMES;

        SelectAdsFromOutcomesExecutionLogger selectAdsFromOutcomesExecutionLogger =
                new SelectAdsFromOutcomesExecutionLoggerFactory(
                                Clock.getInstance(), mAdServicesLogger, mFlags)
                        .getSelectAdsFromOutcomesExecutionLogger();

        try {
            Objects.requireNonNull(inputParams);
            Objects.requireNonNull(callback);
        } catch (NullPointerException e) {
            sLogger.v(
                    "The selectAds(AdSelectionFromOutcomesConfig) arguments should not be null,"
                            + " failed");
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    inputParams.getCallerPackageName(),
                    STATUS_INVALID_ARGUMENT,
                    /* latencyMs= */ 0);
            // Rethrow because we want to fail fast
            throw e;
        }

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredAnyPermission(
                mContext, inputParams.getCallerPackageName(), apiName, PERMISSIONS_SET);

        int callingUid = getCallingUid(apiName);

        DevContext devContext = mDevContextFilter.createDevContext();
        mLightweightExecutor.execute(
                () -> {
                    OutcomeSelectionRunner runner =
                            new OutcomeSelectionRunner(
                                    mAdSelectionEntryDao,
                                    mBackgroundExecutor,
                                    mLightweightExecutor,
                                    mScheduledExecutor,
                                    mAdServicesHttpsClient,
                                    mAdServicesLogger,
                                    devContext,
                                    mContext,
                                    mFlags,
                                    mDebugFlags,
                                    mAdSelectionServiceFilter,
                                    mAdFilteringFeatureFactory.getAdCounterKeyCopier(),
                                    callingUid,
                                    mShouldUseUnifiedTables,
                                    mRetryStrategyFactory.createRetryStrategy(
                                            mFlags.getAdServicesJsScriptEngineMaxRetryAttempts()),
                                    mConsoleMessageInLogsEnabled);
                    runner.runOutcomeSelection(
                            inputParams, callback, selectAdsFromOutcomesExecutionLogger);
                });
    }

    @Override
    public void reportImpression(
            @NonNull ReportImpressionInput requestParams,
            @NonNull ReportImpressionCallback callback) {
        int apiName = AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION;

        try {
            Objects.requireNonNull(requestParams);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, STATUS_INVALID_ARGUMENT, /* latencyMs= */ 0);
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    exception,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__AD_SELECTION_SERVICE_NULL_ARGUMENT,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__REPORT_IMPRESSION);
            // Rethrow because we want to fail fast
            throw exception;
        }

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredAnyPermission(
                mContext, requestParams.getCallerPackageName(), apiName, PERMISSIONS_SET);

        DevContext devContext = mDevContextFilter.createDevContext();

        int callingUid = getCallingUid(apiName);

        ReportImpressionExecutionLogger reportImpressionExecutionLogger =
                new ReportImpressionExecutionLoggerFactory(mAdServicesLogger, mFlags)
                        .getReportImpressionExecutionLogger();

        // ImpressionReporter enables Auction Server flow reporting and sets the stage for Phase 2
        // in go/rb-rm-unified-flow-reporting whereas ImpressionReporterLegacy is the logic before
        // Phase 1. FLEDGE_AUCTION_SERVER_REPORTING_ENABLED flag controls which logic is called.
        if (BinderFlagReader.readFlag(mFlags::getFledgeAuctionServerEnabledForReportImpression)) {
            ImpressionReporter reporter =
                    new ImpressionReporter(
                            mLightweightExecutor,
                            mBackgroundExecutor,
                            mScheduledExecutor,
                            mAdSelectionEntryDao,
                            mCustomAudienceDao,
                            mAdServicesHttpsClient,
                            devContext,
                            mAdServicesLogger,
                            mFlags,
                            mDebugFlags,
                            mAdSelectionServiceFilter,
                            mFledgeAuthorizationFilter,
                            mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                            callingUid,
                            mRetryStrategyFactory.createRetryStrategy(
                                    BinderFlagReader.readFlag(
                                            mFlags::getAdServicesJsScriptEngineMaxRetryAttempts)),
                            mShouldUseUnifiedTables,
                            reportImpressionExecutionLogger);
            reporter.reportImpression(requestParams, callback);
        } else {
            ImpressionReporterLegacy reporter =
                    new ImpressionReporterLegacy(
                            mLightweightExecutor,
                            mBackgroundExecutor,
                            mScheduledExecutor,
                            mAdSelectionEntryDao,
                            mCustomAudienceDao,
                            mAdServicesHttpsClient,
                            devContext,
                            mAdServicesLogger,
                            mFlags,
                            mDebugFlags,
                            mAdSelectionServiceFilter,
                            mFledgeAuthorizationFilter,
                            mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                            callingUid,
                            mShouldUseUnifiedTables,
                            mRetryStrategyFactory.createRetryStrategy(
                                    BinderFlagReader.readFlag(
                                            mFlags::getAdServicesJsScriptEngineMaxRetryAttempts)),
                            reportImpressionExecutionLogger);
            reporter.reportImpression(requestParams, callback);
        }
    }

    @Override
    public void reportInteraction(
            @NonNull ReportInteractionInput inputParams,
            @NonNull ReportInteractionCallback callback) {
        int apiName = AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION;

        try {
            Objects.requireNonNull(inputParams);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, STATUS_INVALID_ARGUMENT, /* latencyMs= */ 0);
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    exception,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__AD_SELECTION_SERVICE_NULL_ARGUMENT,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__REPORT_INTERACTION);
            // Rethrow because we want to fail fast
            throw exception;
        }

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredAnyPermission(
                mContext, inputParams.getCallerPackageName(), apiName, PERMISSIONS_SET);

        int callerUid = getCallingUid(apiName);
        DevContext devContext = mDevContextFilter.createDevContext();

        // Get an instance of measurement service
        // Binder identity is cleared and eventually restored to allow reading values of device
        // config flags.
        MeasurementImpl measurementService;
        final long token = Binder.clearCallingIdentity();
        try {
            measurementService = MeasurementImpl.getInstance();
        } finally {
            Binder.restoreCallingIdentity(token);
        }

        // Get an instance of the event reporter
        EventReporter eventReporter =
                new EventReporterFactory(
                                mAdSelectionEntryDao,
                                mAdServicesHttpsClient,
                                mLightweightExecutor,
                                mBackgroundExecutor,
                                mAdServicesLogger,
                                mFlags,
                                mDebugFlags,
                                mAdSelectionServiceFilter,
                                callerUid,
                                mFledgeAuthorizationFilter,
                                devContext,
                                measurementService,
                                mConsentManager,
                                mContext,
                                mShouldUseUnifiedTables)
                        .getEventReporter();

        eventReporter.reportInteraction(inputParams, callback);
    }

    @Override
    public void setAppInstallAdvertisers(
            @NonNull SetAppInstallAdvertisersInput request,
            @NonNull SetAppInstallAdvertisersCallback callback)
            throws RemoteException {
        int apiName =
                AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SET_APP_INSTALL_ADVERTISERS;

        try {
            Objects.requireNonNull(request);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, STATUS_INVALID_ARGUMENT, /* latencyMs= */ 0);
            // Rethrow because we want to fail fast
            throw exception;
        }

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredAnyPermission(
                mContext, request.getCallerPackageName(), apiName, PERMISSIONS_SET);

        AppInstallAdvertisersSetter setter =
                new AppInstallAdvertisersSetter(
                        mAppInstallDao,
                        mBackgroundExecutor,
                        mAdServicesLogger,
                        mFlags,
                        mDebugFlags,
                        mAdSelectionServiceFilter,
                        mConsentManager,
                        getCallingUid(apiName),
                        mDevContextFilter.createDevContext());
        setter.setAppInstallAdvertisers(request, callback);
    }

    @Override
    public void updateAdCounterHistogram(
            @NonNull UpdateAdCounterHistogramInput inputParams,
            @NonNull UpdateAdCounterHistogramCallback callback) {
        int apiName = AD_SERVICES_API_CALLED__API_NAME__UPDATE_AD_COUNTER_HISTOGRAM;

        try {
            Objects.requireNonNull(inputParams);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, STATUS_INVALID_ARGUMENT, /* latencyMs= */ 0);
            // Rethrow because we want to fail fast
            throw exception;
        }

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredAnyPermission(
                mContext, inputParams.getCallerPackageName(), apiName, PERMISSIONS_SET);

        final int callingUid = getCallingUid(apiName);
        final int adCounterHistogramAbsoluteMaxTotalEventCount =
                BinderFlagReader.readFlag(
                        mFlags::getFledgeAdCounterHistogramAbsoluteMaxTotalEventCount);
        final int adCounterHistogramLowerMaxTotalEventCount =
                BinderFlagReader.readFlag(
                        mFlags::getFledgeAdCounterHistogramLowerMaxTotalEventCount);
        final int adCounterHistogramAbsoluteMaxPerBuyerEventCount =
                BinderFlagReader.readFlag(
                        mFlags::getFledgeAdCounterHistogramAbsoluteMaxPerBuyerEventCount);
        final int adCounterHistogramLowerMaxPerBuyerEventCount =
                BinderFlagReader.readFlag(
                        mFlags::getFledgeAdCounterHistogramLowerMaxPerBuyerEventCount);
        final boolean auctionServerEnabledForUpdateHistogram =
                BinderFlagReader.readFlag(mFlags::getFledgeAuctionServerEnabledForUpdateHistogram);

        final UpdateAdCounterHistogramWorker worker =
                new UpdateAdCounterHistogramWorker(
                        new AdCounterHistogramUpdaterImpl(
                                mAdSelectionEntryDao,
                                mFrequencyCapDao,
                                adCounterHistogramAbsoluteMaxTotalEventCount,
                                adCounterHistogramLowerMaxTotalEventCount,
                                adCounterHistogramAbsoluteMaxPerBuyerEventCount,
                                adCounterHistogramLowerMaxPerBuyerEventCount,
                                auctionServerEnabledForUpdateHistogram,
                                mShouldUseUnifiedTables),
                        mBackgroundExecutor,
                        // TODO(b/235841960): Use the same injected clock as AdSelectionRunner
                        //  after aligning on Clock usage
                        java.time.Clock.systemUTC(),
                        mAdServicesLogger,
                        mFlags,
                        mDebugFlags,
                        mAdSelectionServiceFilter,
                        mConsentManager,
                        callingUid,
                        mDevContextFilter.createDevContext());

        worker.updateAdCounterHistogram(inputParams, callback);
    }

    @Override
    public void overrideAdSelectionConfigRemoteInfo(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull String decisionLogicJS,
            @NonNull AdSelectionSignals trustedScoringSignals,
            @NonNull PerBuyerDecisionLogic perBuyerDecisionLogic,
            @NonNull AdSelectionOverrideCallback callback) {
        int apiName = AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO;

        try {
            Objects.requireNonNull(adSelectionConfig);
            Objects.requireNonNull(decisionLogicJS);
            Objects.requireNonNull(perBuyerDecisionLogic);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, STATUS_INVALID_ARGUMENT, /* latencyMs= */ 0);
            // Rethrow because we want to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDeviceDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    devContext.getCallingAppPackageName(),
                    STATUS_INTERNAL_ERROR,
                    /* latencyMs= */ 0);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        // Caller permissions must be checked with a non-null callingAppPackageName
        mFledgeAuthorizationFilter.assertAppDeclaredAnyPermission(
                mContext, devContext.getCallingAppPackageName(), apiName, PERMISSIONS_SET);

        int callingUid = getCallingUid(apiName);

        AdSelectionOverrider overrider =
                new AdSelectionOverrider(
                        devContext,
                        mAdSelectionEntryDao,
                        mLightweightExecutor,
                        mBackgroundExecutor,
                        mContext.getPackageManager(),
                        ConsentManager.getInstance(),
                        mAdServicesLogger,
                        AppImportanceFilter.create(
                                mContext,
                                () ->
                                        FlagsFactory.getFlags()
                                                .getForegroundStatuslLevelForValidation()),
                        mFlags,
                        callingUid);

        overrider.addOverride(
                adSelectionConfig,
                decisionLogicJS,
                trustedScoringSignals,
                perBuyerDecisionLogic,
                callback);
    }

    private int getCallingUid(int apiNameLoggingId) throws IllegalStateException {
        return getCallingUid(apiNameLoggingId, null);
    }

    private int getCallingUid(int apiNameLoggingId, String callerAppPackageName) {
        try {
            return mCallingAppUidSupplier.getCallingAppUid();
        } catch (IllegalStateException illegalStateException) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiNameLoggingId,
                    callerAppPackageName,
                    STATUS_INTERNAL_ERROR,
                    /* latencyMs= */ 0);
            logGetCallingUidCEL(apiNameLoggingId);
            throw illegalStateException;
        }
    }

    private void logGetCallingUidCEL(int apiNameLoggingId) {
        int celApiNameId = AdsRelevanceStatusUtils.getCelPpApiNameId(apiNameLoggingId);
        if (celApiNameId != AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED) {
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__AD_SELECTION_SERVICE_GET_CALLING_UID_ILLEGAL_STATE,
                    celApiNameId);
        }
    }

    @Override
    public void removeAdSelectionConfigRemoteInfoOverride(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull AdSelectionOverrideCallback callback) {
        // Auto-generated variable name is too long for lint check
        int apiName =
                AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE;

        try {
            Objects.requireNonNull(adSelectionConfig);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, STATUS_INVALID_ARGUMENT, /* latencyMs= */ 0);
            // Rethrow because we want to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDeviceDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    devContext.getCallingAppPackageName(),
                    STATUS_INTERNAL_ERROR,
                    /* latencyMs= */ 0);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        // Caller permissions must be checked with a non-null callingAppPackageName
        mFledgeAuthorizationFilter.assertAppDeclaredAnyPermission(
                mContext, devContext.getCallingAppPackageName(), apiName, PERMISSIONS_SET);

        int callingUid = getCallingUid(apiName);

        AdSelectionOverrider overrider =
                new AdSelectionOverrider(
                        devContext,
                        mAdSelectionEntryDao,
                        mLightweightExecutor,
                        mBackgroundExecutor,
                        mContext.getPackageManager(),
                        ConsentManager.getInstance(),
                        mAdServicesLogger,
                        AppImportanceFilter.create(
                                mContext,
                                () ->
                                        FlagsFactory.getFlags()
                                                .getForegroundStatuslLevelForValidation()),
                        mFlags,
                        callingUid);

        overrider.removeOverride(adSelectionConfig, callback);
    }

    @Override
    public void resetAllAdSelectionConfigRemoteOverrides(
            @NonNull AdSelectionOverrideCallback callback) {
        // Auto-generated variable name is too long for lint check
        int apiName =
                AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES;

        try {
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, STATUS_INVALID_ARGUMENT, /* latencyMs= */ 0);
            // Rethrow because we want to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDeviceDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    devContext.getCallingAppPackageName(),
                    STATUS_INTERNAL_ERROR,
                    /* latencyMs= */ 0);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        // Caller permissions must be checked with a non-null callingAppPackageName
        mFledgeAuthorizationFilter.assertAppDeclaredAnyPermission(
                mContext, devContext.getCallingAppPackageName(), apiName, PERMISSIONS_SET);

        int callingUid = getCallingUid(apiName);

        AdSelectionOverrider overrider =
                new AdSelectionOverrider(
                        devContext,
                        mAdSelectionEntryDao,
                        mLightweightExecutor,
                        mBackgroundExecutor,
                        mContext.getPackageManager(),
                        ConsentManager.getInstance(),
                        mAdServicesLogger,
                        AppImportanceFilter.create(
                                mContext,
                                () ->
                                        FlagsFactory.getFlags()
                                                .getForegroundStatuslLevelForValidation()),
                        mFlags,
                        callingUid);

        overrider.removeAllOverridesForAdSelectionConfig(callback);
    }

    @Override
    public void overrideAdSelectionFromOutcomesConfigRemoteInfo(
            @NonNull AdSelectionFromOutcomesConfig config,
            @NonNull String selectionLogicJs,
            @NonNull AdSelectionSignals selectionSignals,
            @NonNull AdSelectionOverrideCallback callback) {
        int apiName = AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;

        try {
            Objects.requireNonNull(config);
            Objects.requireNonNull(selectionLogicJs);
            Objects.requireNonNull(selectionSignals);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, STATUS_INVALID_ARGUMENT, /* latencyMs= */ 0);
            // Rethrow because we want to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDeviceDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    devContext.getCallingAppPackageName(),
                    STATUS_INTERNAL_ERROR,
                    /* latencyMs= */ 0);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        // Caller permissions must be checked with a non-null callingAppPackageName
        mFledgeAuthorizationFilter.assertAppDeclaredAnyPermission(
                mContext, devContext.getCallingAppPackageName(), apiName, PERMISSIONS_SET);

        int callingUid = getCallingUid(apiName);

        AdSelectionOverrider overrider =
                new AdSelectionOverrider(
                        devContext,
                        mAdSelectionEntryDao,
                        mLightweightExecutor,
                        mBackgroundExecutor,
                        mContext.getPackageManager(),
                        ConsentManager.getInstance(),
                        mAdServicesLogger,
                        AppImportanceFilter.create(
                                mContext,
                                () ->
                                        FlagsFactory.getFlags()
                                                .getForegroundStatuslLevelForValidation()),
                        mFlags,
                        callingUid);

        overrider.addOverride(config, selectionLogicJs, selectionSignals, callback);
    }

    @Override
    public void removeAdSelectionFromOutcomesConfigRemoteInfoOverride(
            @NonNull AdSelectionFromOutcomesConfig config,
            @NonNull AdSelectionOverrideCallback callback) {
        // Auto-generated variable name is too long for lint check
        int apiName = AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;

        try {
            Objects.requireNonNull(config);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, STATUS_INVALID_ARGUMENT, /* latencyMs= */ 0);
            // Rethrow because we want to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDeviceDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    devContext.getCallingAppPackageName(),
                    STATUS_INTERNAL_ERROR,
                    /* latencyMs= */ 0);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        // Caller permissions must be checked with a non-null callingAppPackageName
        mFledgeAuthorizationFilter.assertAppDeclaredAnyPermission(
                mContext, devContext.getCallingAppPackageName(), apiName, PERMISSIONS_SET);

        int callingUid = getCallingUid(apiName);

        AdSelectionOverrider overrider =
                new AdSelectionOverrider(
                        devContext,
                        mAdSelectionEntryDao,
                        mLightweightExecutor,
                        mBackgroundExecutor,
                        mContext.getPackageManager(),
                        ConsentManager.getInstance(),
                        mAdServicesLogger,
                        AppImportanceFilter.create(
                                mContext,
                                () ->
                                        FlagsFactory.getFlags()
                                                .getForegroundStatuslLevelForValidation()),
                        mFlags,
                        callingUid);

        overrider.removeOverride(config, callback);
    }

    @Override
    public void resetAllAdSelectionFromOutcomesConfigRemoteOverrides(
            @NonNull AdSelectionOverrideCallback callback) {
        // Auto-generated variable name is too long for lint check
        int apiName = AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;

        try {
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, STATUS_INVALID_ARGUMENT, /* latencyMs= */ 0);
            // Rethrow because we want to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDeviceDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    devContext.getCallingAppPackageName(),
                    STATUS_INTERNAL_ERROR,
                    /* latencyMs= */ 0);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        // Caller permissions must be checked with a non-null callingAppPackageName
        mFledgeAuthorizationFilter.assertAppDeclaredAnyPermission(
                mContext, devContext.getCallingAppPackageName(), apiName, PERMISSIONS_SET);

        int callingUid = getCallingUid(apiName);

        AdSelectionOverrider overrider =
                new AdSelectionOverrider(
                        devContext,
                        mAdSelectionEntryDao,
                        mLightweightExecutor,
                        mBackgroundExecutor,
                        mContext.getPackageManager(),
                        ConsentManager.getInstance(),
                        mAdServicesLogger,
                        AppImportanceFilter.create(
                                mContext,
                                () ->
                                        FlagsFactory.getFlags()
                                                .getForegroundStatuslLevelForValidation()),
                        mFlags,
                        callingUid);

        overrider.removeAllOverridesForAdSelectionFromOutcomes(callback);
    }

    @Override
    public void setAdCounterHistogramOverride(
            @NonNull SetAdCounterHistogramOverrideInput inputParams,
            @NonNull AdSelectionOverrideCallback callback) {
        int apiName = AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;

        try {
            Objects.requireNonNull(inputParams);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, STATUS_INVALID_ARGUMENT, /* latencyMs= */ 0);
            // Rethrow because we want to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDeviceDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    devContext.getCallingAppPackageName(),
                    STATUS_INTERNAL_ERROR,
                    /* latencyMs= */ 0);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        // Caller permissions must be checked with a non-null callingAppPackageName
        mFledgeAuthorizationFilter.assertAppDeclaredAnyPermission(
                mContext, devContext.getCallingAppPackageName(), apiName, PERMISSIONS_SET);

        // TODO(b/265204820): Implement service
        int status = STATUS_SUCCESS;
        try {
            callback.onSuccess();
        } catch (RemoteException exception) {
            status = STATUS_INTERNAL_ERROR;
        } finally {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, devContext.getCallingAppPackageName(), status, /* latencyMs= */ 0);
        }
    }

    @Override
    public void removeAdCounterHistogramOverride(
            @NonNull RemoveAdCounterHistogramOverrideInput inputParams,
            @NonNull AdSelectionOverrideCallback callback) {
        int apiName = AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;

        try {
            Objects.requireNonNull(inputParams);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, STATUS_INVALID_ARGUMENT, /* latencyMs= */ 0);
            // Rethrow because we want to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDeviceDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    devContext.getCallingAppPackageName(),
                    STATUS_INTERNAL_ERROR,
                    /* latencyMs= */ 0);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        // Caller permissions must be checked with a non-null callingAppPackageName
        mFledgeAuthorizationFilter.assertAppDeclaredAnyPermission(
                mContext, devContext.getCallingAppPackageName(), apiName, PERMISSIONS_SET);

        // TODO(b/265204820): Implement service
        int status = STATUS_SUCCESS;
        try {
            callback.onSuccess();
        } catch (RemoteException exception) {
            status = STATUS_INTERNAL_ERROR;
        } finally {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, devContext.getCallingAppPackageName(), status, /* latencyMs= */ 0);
        }
    }

    @Override
    public void resetAllAdCounterHistogramOverrides(@NonNull AdSelectionOverrideCallback callback) {
        int apiName = AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;

        try {
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, STATUS_INVALID_ARGUMENT, /* latencyMs= */ 0);
            // Rethrow because we want to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDeviceDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName,
                    devContext.getCallingAppPackageName(),
                    STATUS_INTERNAL_ERROR,
                    /* latencyMs= */ 0);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        // Caller permissions must be checked with a non-null callingAppPackageName
        mFledgeAuthorizationFilter.assertAppDeclaredAnyPermission(
                mContext, devContext.getCallingAppPackageName(), apiName, PERMISSIONS_SET);

        // TODO(b/265204820): Implement service
        int status = STATUS_SUCCESS;
        try {
            callback.onSuccess();
        } catch (RemoteException exception) {
            status = STATUS_INTERNAL_ERROR;
        } finally {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, devContext.getCallingAppPackageName(), status, /* latencyMs= */ 0);
        }
    }

    /** Close down method to be invoked when the PPAPI process is shut down. */
    @SuppressWarnings("FutureReturnValueIgnored")
    public void destroy() {
        sLogger.i("Shutting down AdSelectionService");
        try {
            JSScriptEngine jsScriptEngine = JSScriptEngine.getInstance();
            jsScriptEngine.shutdown();
        } catch (JSSandboxIsNotAvailableException exception) {
            sLogger.i("Java script sandbox is not available, not shutting down JSScriptEngine.");
        }
    }
}
