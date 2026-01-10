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

import static android.adservices.common.AdServicesStatusUtils.STATUS_ADSERVICES_DISABLED;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__UPDATE_AD_COUNTER_HISTOGRAM;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE;

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
import android.adservices.common.FledgeErrorResponse;
import android.annotation.NonNull;
import android.content.Context;
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
import com.android.adservices.data.adselection.AdSelectionServerDatabase;
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
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptorImpl;
import com.android.adservices.service.adselection.encryption.ProtectedServersEncryptionConfigManager;
import com.android.adservices.service.adselection.encryption.ServerAuctionCoordinatorUriStrategyFactory;
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
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.js.JSSandboxIsNotAvailableException;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.service.kanon.KAnonSignJoinFactory;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.AdsRelevanceStatusUtils;
import com.android.internal.annotations.VisibleForTesting;

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
    @NonNull private final ObliviousHttpEncryptor mObliviousHttpEncryptor;
    @NonNull KAnonSignJoinFactory mKAnonSignJoinFactory;
    private final boolean mShouldUseUnifiedTables;
    @NonNull private final RetryStrategyFactory mRetryStrategyFactory;

    private final boolean mConsoleMessageInLogsEnabled;

    @NonNull
    private final AuctionServerDebugConfigurationGenerator
            mAuctionServerDebugConfigurationGenerator;

    @NonNull
    private final ServerAuctionCoordinatorUriStrategyFactory
            mServerAuctionCoordinatorUriStrategyFactory;

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
            @NonNull ObliviousHttpEncryptor obliviousHttpEncryptor,
            @NonNull AdSelectionDebugReportDao adSelectionDebugReportDao,
            @NonNull AdIdFetcher adIdFetcher,
            @NonNull KAnonSignJoinFactory kAnonSignJoinFactory,
            boolean shouldUseUnifiedTables,
            @NonNull RetryStrategyFactory retryStrategyFactory,
            boolean consoleMessageInLogsEnabled,
            @NonNull
                    AuctionServerDebugConfigurationGenerator
                            auctionServerDebugConfigurationGenerator,
            @NonNull
                    ServerAuctionCoordinatorUriStrategyFactory
                            serverAuctionCoordinatorUriStrategyFactory) {
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
        Objects.requireNonNull(obliviousHttpEncryptor);
        Objects.requireNonNull(adSelectionDebugReportDao);
        Objects.requireNonNull(adIdFetcher);
        Objects.requireNonNull(kAnonSignJoinFactory);
        Objects.requireNonNull(retryStrategyFactory);
        Objects.requireNonNull(auctionServerDebugConfigurationGenerator);
        Objects.requireNonNull(serverAuctionCoordinatorUriStrategyFactory);

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
        mObliviousHttpEncryptor = obliviousHttpEncryptor;
        mAdSelectionDebugReportDao = adSelectionDebugReportDao;
        mAdIdFetcher = adIdFetcher;
        mShouldUseUnifiedTables = shouldUseUnifiedTables;
        mKAnonSignJoinFactory = kAnonSignJoinFactory;
        mRetryStrategyFactory = retryStrategyFactory;
        mAuctionServerDebugConfigurationGenerator = auctionServerDebugConfigurationGenerator;
        mConsoleMessageInLogsEnabled = consoleMessageInLogsEnabled;
        mServerAuctionCoordinatorUriStrategyFactory = serverAuctionCoordinatorUriStrategyFactory;
    }

    /** Creates a new instance of {@link AdSelectionServiceImpl}. */
    @SuppressWarnings("AvoidStaticContext") // Factory method
    public static AdSelectionServiceImpl create(@NonNull Context context) {
        sLogger.d("AdSelectionServiceImpl create");
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
                                                .getForegroundStatuslLevelForValidation(),
                                BinderFlagReader.readFlag(
                                        () ->
                                                FlagsFactory.getFlags()
                                                        .getEnableGetBindingUidImportance())),
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
                new ObliviousHttpEncryptorImpl(
                        new ProtectedServersEncryptionConfigManager(
                                AdSelectionServerDatabase.getInstance()
                                        .protectedServersEncryptionConfigDao(),
                                FlagsFactory.getFlags(),
                                new AdServicesHttpsClient(
                                        AdServicesExecutors.getBlockingExecutor(),
                                        CacheProviderFactory.create(
                                                context, FlagsFactory.getFlags())),
                                AdServicesExecutors.getLightWeightExecutor(),
                                AdServicesLoggerImpl.getInstance(),
                                new ServerAuctionCoordinatorUriStrategyFactory(
                                        BinderFlagReader.readFlag(
                                                () ->
                                                        FlagsFactory.getFlags()
                                                                .getFledgeAuctionServerCoordinatorUrlAllowlist()))),
                        AdSelectionServerDatabase.getInstance().encryptionContextDao(),
                        AdServicesExecutors.getLightWeightExecutor()),
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
                        AdServicesExecutors.getLightWeightExecutor()),
                new ServerAuctionCoordinatorUriStrategyFactory(
                        BinderFlagReader.readFlag(
                                () ->
                                        FlagsFactory.getFlags()
                                                .getFledgeAuctionServerCoordinatorUrlAllowlist())));
    }

    @Override
    public void getAdSelectionData(
            GetAdSelectionDataInput inputParams,
            CallerMetadata callerMetadata,
            GetAdSelectionDataCallback callback)
            throws RemoteException {
        // Logs API deprecated.
        logStatsdForDeprecation(
                inputParams.getCallerPackageName(),
                AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA);

        // Sent back deprecation message throw callback
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE);
        }
    }

    @Override
    public void persistAdSelectionResult(
            PersistAdSelectionResultInput inputParams,
            CallerMetadata callerMetadata,
            PersistAdSelectionResultCallback callback)
            throws RemoteException {
        // Logs API deprecated.
        logStatsdForDeprecation(
                inputParams.getCallerPackageName(),
                AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT);

        // Sent back deprecation message throw callback
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE);
        }
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
        // Logs API deprecated.
        logStatsdForDeprecation(
                inputParams.getCallerPackageName(),
                AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS);

        // Sent back deprecation message throw callback
        try {
            partialCallback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE);
        }
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
        // Logs API deprecated.
        logStatsdForDeprecation(
                inputParams.getCallerPackageName(),
                AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS_FROM_OUTCOMES);

        // Sent back deprecation message throw callback
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE);
        }
    }

    @Override
    public void reportImpression(
            @NonNull ReportImpressionInput requestParams,
            @NonNull ReportImpressionCallback callback) {
        // Logs API deprecated.
        logStatsdForDeprecation(
                requestParams.getCallerPackageName(),
                AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION);

        // Sent back deprecation message throw callback
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE);
        }
    }

    @Override
    public void reportInteraction(
            @NonNull ReportInteractionInput inputParams,
            @NonNull ReportInteractionCallback callback) {
        // Logs API deprecated.
        logStatsdForDeprecation(
                inputParams.getCallerPackageName(),
                AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION);

        // Sent back deprecation message throw callback
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE);
        }
    }

    @Override
    public void setAppInstallAdvertisers(
            @NonNull SetAppInstallAdvertisersInput request,
            @NonNull SetAppInstallAdvertisersCallback callback)
            throws RemoteException {
        // Logs API deprecated.
        logStatsdForDeprecation(
                request.getCallerPackageName(),
                AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SET_APP_INSTALL_ADVERTISERS);

        // Sent back deprecation message throw callback
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE);
        }
    }

    @Override
    public void updateAdCounterHistogram(
            @NonNull UpdateAdCounterHistogramInput inputParams,
            @NonNull UpdateAdCounterHistogramCallback callback) {
        // Logs API deprecated.
        logStatsdForDeprecation(
                inputParams.getCallerPackageName(),
                AD_SERVICES_API_CALLED__API_NAME__UPDATE_AD_COUNTER_HISTOGRAM);

        // Sent back deprecation message throw callback
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE);
        }
    }

    @Override
    public void overrideAdSelectionConfigRemoteInfo(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull String decisionLogicJS,
            @NonNull AdSelectionSignals trustedScoringSignals,
            @NonNull PerBuyerDecisionLogic perBuyerDecisionLogic,
            @NonNull AdSelectionOverrideCallback callback) {
        DevContext devContext = mDevContextFilter.createDevContext();

        // Logs API deprecated.
        logStatsdForDeprecation(
                devContext.getCallingAppPackageName(),
                AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO);

        // Sent back deprecation message throw callback
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE);
        }
    }

    @Override
    public void removeAdSelectionConfigRemoteInfoOverride(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull AdSelectionOverrideCallback callback) {
        DevContext devContext = mDevContextFilter.createDevContext();

        // Logs API deprecated.
        logStatsdForDeprecation(
                devContext.getCallingAppPackageName(),
                AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE);

        // Sent back deprecation message throw callback
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE);
        }
    }

    @Override
    public void resetAllAdSelectionConfigRemoteOverrides(
            @NonNull AdSelectionOverrideCallback callback) {
        DevContext devContext = mDevContextFilter.createDevContext();

        // Logs API deprecated.
        logStatsdForDeprecation(
                devContext.getCallingAppPackageName(),
                AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES);

        // Sent back deprecation message throw callback
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE);
        }
    }

    @Override
    public void overrideAdSelectionFromOutcomesConfigRemoteInfo(
            @NonNull AdSelectionFromOutcomesConfig config,
            @NonNull String selectionLogicJs,
            @NonNull AdSelectionSignals selectionSignals,
            @NonNull AdSelectionOverrideCallback callback) {
        DevContext devContext = mDevContextFilter.createDevContext();

        // Logs API deprecated.
        // Auto-generated variable name is too long for lint check
        logStatsdForDeprecation(
                devContext.getCallingAppPackageName(),
                AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN);

        // Sent back deprecation message throw callback
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE);
        }
    }

    @Override
    public void removeAdSelectionFromOutcomesConfigRemoteInfoOverride(
            @NonNull AdSelectionFromOutcomesConfig config,
            @NonNull AdSelectionOverrideCallback callback) {
        DevContext devContext = mDevContextFilter.createDevContext();

        // Logs API deprecated.
        // Auto-generated variable name is too long for lint check
        logStatsdForDeprecation(
                devContext.getCallingAppPackageName(),
                AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN);

        // Sent back deprecation message throw callback
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE);
        }
    }

    @Override
    public void resetAllAdSelectionFromOutcomesConfigRemoteOverrides(
            @NonNull AdSelectionOverrideCallback callback) {
        DevContext devContext = mDevContextFilter.createDevContext();

        // Logs API deprecated.
        // Auto-generated variable name is too long for lint check
        logStatsdForDeprecation(
                devContext.getCallingAppPackageName(),
                AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN);

        // Sent back deprecation message throw callback
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE);
        }
    }

    @Override
    public void setAdCounterHistogramOverride(
            @NonNull SetAdCounterHistogramOverrideInput inputParams,
            @NonNull AdSelectionOverrideCallback callback) {
        DevContext devContext = mDevContextFilter.createDevContext();

        // Logs API deprecated.
        // Auto-generated variable name is too long for lint check
        logStatsdForDeprecation(
                devContext.getCallingAppPackageName(),
                AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN);

        // Sent back deprecation message throw callback
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE);
        }
    }

    @Override
    public void removeAdCounterHistogramOverride(
            @NonNull RemoveAdCounterHistogramOverrideInput inputParams,
            @NonNull AdSelectionOverrideCallback callback) {
        DevContext devContext = mDevContextFilter.createDevContext();

        // Logs API deprecated.
        // Auto-generated variable name is too long for lint check
        logStatsdForDeprecation(
                devContext.getCallingAppPackageName(),
                AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN);

        // Sent back deprecation message throw callback
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE);
        }
    }

    @Override
    public void resetAllAdCounterHistogramOverrides(@NonNull AdSelectionOverrideCallback callback) {
        DevContext devContext = mDevContextFilter.createDevContext();

        // Logs API deprecated.
        // Auto-generated variable name is too long for lint check
        logStatsdForDeprecation(
                devContext.getCallingAppPackageName(),
                AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN);

        // Sent back deprecation message throw callback
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(STATUS_ADSERVICES_DISABLED)
                            .build());
        } catch (RemoteException e) {
            sLogger.e("Failed sending back deprecation message to client.");

            // logs CEL in case failed to send back response
            AdsRelevanceStatusUtils.logCelInsideBinderThread(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE);
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

    private void logStatsdForDeprecation(String packageName, int apiName) {
        mAdServicesLogger.logFledgeApiCallStats(
                apiName, packageName, STATUS_ADSERVICES_DISABLED, /* latencyMs */ 0);
        sLogger.e("Got in-coming calls but AdSelectionService APIs are deprecated.");
    }
}
