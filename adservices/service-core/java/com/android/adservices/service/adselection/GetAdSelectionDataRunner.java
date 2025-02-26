/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.adservices.common.AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_IO_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_JS_SANDBOX_UNAVAILABLE;
import static android.adservices.common.AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_TIMEOUT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNSET;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_CODE_UNSPECIFIED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_ADSERVICES_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_CREATE_ASSET_FILE_DESCRIPTOR_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_EXCEEDED_ALLOWED_TIME_LIMIT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_FILTER_AND_REVOKED_CONSENT_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_EMPTY_SUCCESS_CALLBACK_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_CALLBACK_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_FILTER_EXCEPTION_BACKGROUND_CALLER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_FILTER_EXCEPTION_CALLER_NOT_ALLOWED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_FILTER_EXCEPTION_RATE_LIMIT_REACHED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_FILTER_EXCEPTION_UNAUTHORIZED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_FILTER_EXCEPTION_USER_CONSENT_REVOKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_INTERNAL_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_INVALID_ARGUMENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_JS_SANDBOX_UNAVAILABLE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_TIMEOUT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_SUCCESS_CALLBACK_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_RUNNER_OUTCOME_SELECTION_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_UNSUPPORTED_PAYLOAD_SIZE_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_COORDINATOR_SOURCE_API;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT;

import android.adservices.adselection.GetAdSelectionDataCallback;
import android.adservices.adselection.GetAdSelectionDataInput;
import android.adservices.adselection.GetAdSelectionDataResponse;
import android.adservices.adselection.SellerConfiguration;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.AssetFileDescriptorUtil;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.exceptions.AdServicesException;
import android.adservices.exceptions.UnsupportedPayloadSizeException;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.datahandlers.AdSelectionInitialization;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.adselection.debug.AuctionServerDebugConfiguration;
import com.android.adservices.service.adselection.debug.AuctionServerDebugConfigurationGenerator;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.CoordinatorOriginUriValidator;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.profiling.Tracing;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.ConsentedDebugConfiguration;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.ProtectedAuctionInput;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerUtil;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.AdsRelevanceExecutionLogger;
import com.android.adservices.service.stats.AdsRelevanceStatusUtils;
import com.android.adservices.service.stats.GetAdSelectionDataApiCalledStats;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/** Runner class for GetAdSelectionData service */
@RequiresApi(Build.VERSION_CODES.S)
public class GetAdSelectionDataRunner {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting
    static final String GET_AD_SELECTION_DATA_TIMED_OUT =
            "GetAdSelectionData exceeded allowed time limit";

    @VisibleForTesting static final int REVOKED_CONSENT_RANDOM_DATA_SIZE = 1024;

    private final int mE2ETraceCookie;
    @NonNull private final ObliviousHttpEncryptor mObliviousHttpEncryptor;
    @NonNull private final AdSelectionEntryDao mAdSelectionEntryDao;
    @NonNull private final CustomAudienceDao mCustomAudienceDao;
    @NonNull private final EncodedPayloadDao mEncodedPayloadDao;
    @NonNull private final AdSelectionServiceFilter mAdSelectionServiceFilter;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;
    @NonNull private final ListeningExecutorService mLightweightExecutorService;
    @NonNull private final ListeningExecutorService mBlockingExecutorService;
    @NonNull private final ScheduledThreadPoolExecutor mScheduledExecutor;
    @NonNull private final Flags mFlags;
    @NonNull private final DebugFlags mDebugFlags;
    private final int mCallerUid;

    @NonNull protected final AdSelectionIdGenerator mAdSelectionIdGenerator;
    @NonNull private final BuyerInputGenerator mBuyerInputGenerator;
    @NonNull private final AuctionServerDataCompressor mDataCompressor;
    @NonNull private final DevContext mDevContext;
    @NonNull private final Clock mClock;
    private final int mPayloadFormatterVersion;
    private final ImmutableList<Integer> mPayloadBucketSizes;

    @NonNull private final CoordinatorOriginUriValidator mCoordinatorOriginUriValidator;
    @NonNull private final AdsRelevanceExecutionLogger mAdsRelevanceExecutionLogger;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final AuctionServerPayloadMetricsStrategy mAuctionServerPayloadMetricsStrategy;
    private final BuyerInputGeneratorArgumentsPreparer mBuyerInputGeneratorArgumentsPreparer;
    private final AuctionServerDebugConfigurationGenerator
            mAuctionServerDebugConfigurationGenerator;

    public GetAdSelectionDataRunner(
            @NonNull final Context context,
            int e2eTraceCookie,
            @NonNull final MultiCloudSupportStrategy multiCloudSupportStrategy,
            @NonNull final AdSelectionEntryDao adSelectionEntryDao,
            @NonNull final CustomAudienceDao customAudienceDao,
            @NonNull final EncodedPayloadDao encodedPayloadDao,
            @NonNull final AdSelectionServiceFilter adSelectionServiceFilter,
            @NonNull final FrequencyCapAdFilterer frequencyCapAdFilterer,
            @NonNull final ExecutorService backgroundExecutorService,
            @NonNull final ExecutorService lightweightExecutorService,
            @NonNull final ExecutorService blockingExecutorService,
            @NonNull final ScheduledThreadPoolExecutor scheduledExecutor,
            @NonNull final Flags flags,
            @NonNull final DebugFlags debugFlags,
            final int callerUid,
            @NonNull final DevContext devContext,
            @NonNull final AdsRelevanceExecutionLogger adsRelevanceExecutionLogger,
            @NonNull final AdServicesLogger adServicesLogger,
            @NonNull AuctionServerPayloadMetricsStrategy auctionServerPayloadMetricsStrategy,
            @NonNull final AppInstallAdFilterer appInstallAdFilterer,
            @NonNull
                    final AuctionServerDebugConfigurationGenerator
                            auctionServerDebugConfigurationGenerator) {
        Objects.requireNonNull(multiCloudSupportStrategy);
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(encodedPayloadDao);
        Objects.requireNonNull(frequencyCapAdFilterer);
        Objects.requireNonNull(adSelectionServiceFilter);
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(scheduledExecutor);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(debugFlags);
        Objects.requireNonNull(devContext);
        Objects.requireNonNull(adsRelevanceExecutionLogger);
        Objects.requireNonNull(auctionServerPayloadMetricsStrategy);
        Objects.requireNonNull(appInstallAdFilterer);
        Objects.requireNonNull(auctionServerDebugConfigurationGenerator);

        mE2ETraceCookie = e2eTraceCookie;
        mObliviousHttpEncryptor =
                multiCloudSupportStrategy.getObliviousHttpEncryptor(context, flags);
        mCoordinatorOriginUriValidator =
                multiCloudSupportStrategy.getCoordinatorOriginUriValidator();
        mAdSelectionEntryDao = adSelectionEntryDao;
        mCustomAudienceDao = customAudienceDao;
        mEncodedPayloadDao = encodedPayloadDao;
        mAdSelectionServiceFilter = adSelectionServiceFilter;
        mBackgroundExecutorService = MoreExecutors.listeningDecorator(backgroundExecutorService);
        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutorService);
        mBlockingExecutorService = MoreExecutors.listeningDecorator(blockingExecutorService);
        mScheduledExecutor = scheduledExecutor;
        mFlags = flags;
        mDebugFlags = debugFlags;
        mCallerUid = callerUid;
        mDevContext = devContext;
        mClock = Clock.systemUTC();

        mAdSelectionIdGenerator = new AdSelectionIdGenerator();
        mAuctionServerPayloadMetricsStrategy = auctionServerPayloadMetricsStrategy;
        mDataCompressor =
                AuctionServerDataCompressorFactory.getDataCompressor(
                        mFlags.getFledgeAuctionServerCompressionAlgorithmVersion());
        CompressedBuyerInputCreatorHelper compressedBuyerInputCreatorHelper =
                new CompressedBuyerInputCreatorHelper(
                        mAuctionServerPayloadMetricsStrategy,
                        mFlags.getPasExtendedMetricsEnabled(),
                        mFlags.getFledgeAuctionServerOmitAdsEnabled());
        CompressedBuyerInputCreatorFactory compressedBuyerInputCreatorFactory =
                new CompressedBuyerInputCreatorFactory(
                        compressedBuyerInputCreatorHelper,
                        mDataCompressor,
                        mFlags.getFledgeGetAdSelectionDataSellerConfigurationEnabled(),
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFlags.getFledgeGetAdSelectionDataBuyerInputCreatorVersion(),
                        mFlags.getFledgeGetAdSelectionDataMaxNumEntirePayloadCompressions(),
                        mFlags.getProtectedSignalsEncodedPayloadMaxSizeBytes(),
                        mClock);

        mBuyerInputGeneratorArgumentsPreparer =
                compressedBuyerInputCreatorFactory.getBuyerInputGeneratorArgumentsPreparer();

        mBuyerInputGenerator =
                new BuyerInputGenerator(
                        frequencyCapAdFilterer,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mFlags.getFledgeCustomAudienceActiveTimeWindowInMs(),
                        mFlags.getFledgeAuctionServerEnableAdFilterInGetAdSelectionData(),
                        mFlags.getProtectedSignalsPeriodicEncodingEnabled(),
                        appInstallAdFilterer,
                        compressedBuyerInputCreatorFactory);
        mPayloadFormatterVersion = mFlags.getFledgeAuctionServerPayloadFormatVersion();
        mPayloadBucketSizes = mFlags.getFledgeAuctionServerPayloadBucketSizes();
        mAdsRelevanceExecutionLogger = adsRelevanceExecutionLogger;
        mAdServicesLogger = adServicesLogger;
        mAuctionServerDebugConfigurationGenerator = auctionServerDebugConfigurationGenerator;
    }

    @VisibleForTesting
    GetAdSelectionDataRunner(
            @NonNull final Context context,
            final int e2ETraceCookie,
            @NonNull final MultiCloudSupportStrategy multiCloudSupportStrategy,
            @NonNull final AdSelectionEntryDao adSelectionEntryDao,
            @NonNull final CustomAudienceDao customAudienceDao,
            @NonNull final EncodedPayloadDao encodedPayloadDao,
            @NonNull final AdSelectionServiceFilter adSelectionServiceFilter,
            @NonNull final FrequencyCapAdFilterer frequencyCapAdFilterer,
            @NonNull final ExecutorService backgroundExecutorService,
            @NonNull final ExecutorService lightweightExecutorService,
            @NonNull final ExecutorService blockingExecutorService,
            @NonNull final ScheduledThreadPoolExecutor scheduledExecutor,
            @NonNull final Flags flags,
            @NonNull final DebugFlags debugFlags,
            final int callerUid,
            @NonNull final DevContext devContext,
            @NonNull Clock clock,
            @NonNull AdsRelevanceExecutionLogger adsRelevanceExecutionLogger,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull AuctionServerPayloadMetricsStrategy auctionServerPayloadMetricsStrategy,
            @NonNull final AppInstallAdFilterer appInstallAdFilterer,
            @NonNull
                    final AuctionServerDebugConfigurationGenerator
                            auctionServerDebugConfigurationGenerator) {
        Objects.requireNonNull(multiCloudSupportStrategy);
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(encodedPayloadDao);
        Objects.requireNonNull(frequencyCapAdFilterer);
        Objects.requireNonNull(adSelectionServiceFilter);
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(scheduledExecutor);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(debugFlags);
        Objects.requireNonNull(devContext);
        Objects.requireNonNull(clock);
        Objects.requireNonNull(adsRelevanceExecutionLogger);
        Objects.requireNonNull(auctionServerPayloadMetricsStrategy);
        Objects.requireNonNull(appInstallAdFilterer);
        Objects.requireNonNull(auctionServerDebugConfigurationGenerator);

        mE2ETraceCookie = e2ETraceCookie;
        mObliviousHttpEncryptor =
                multiCloudSupportStrategy.getObliviousHttpEncryptor(context, flags);
        mCoordinatorOriginUriValidator =
                multiCloudSupportStrategy.getCoordinatorOriginUriValidator();
        mAdSelectionEntryDao = adSelectionEntryDao;
        mCustomAudienceDao = customAudienceDao;
        mEncodedPayloadDao = encodedPayloadDao;
        mAdSelectionServiceFilter = adSelectionServiceFilter;
        mBackgroundExecutorService = MoreExecutors.listeningDecorator(backgroundExecutorService);
        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutorService);
        mBlockingExecutorService = MoreExecutors.listeningDecorator(blockingExecutorService);
        mScheduledExecutor = scheduledExecutor;
        mFlags = flags;
        mDebugFlags = debugFlags;
        mCallerUid = callerUid;
        mDevContext = devContext;
        mClock = clock;

        mAdSelectionIdGenerator = new AdSelectionIdGenerator();
        mAuctionServerPayloadMetricsStrategy = auctionServerPayloadMetricsStrategy;
        mDataCompressor =
                AuctionServerDataCompressorFactory.getDataCompressor(
                        mFlags.getFledgeAuctionServerCompressionAlgorithmVersion());
        CompressedBuyerInputCreatorHelper compressedBuyerInputCreatorHelper =
                new CompressedBuyerInputCreatorHelper(
                        mAuctionServerPayloadMetricsStrategy,
                        mFlags.getPasExtendedMetricsEnabled(),
                        mFlags.getFledgeAuctionServerOmitAdsEnabled());
        CompressedBuyerInputCreatorFactory compressedBuyerInputCreatorFactory =
                new CompressedBuyerInputCreatorFactory(
                        compressedBuyerInputCreatorHelper,
                        mDataCompressor,
                        mFlags.getFledgeGetAdSelectionDataSellerConfigurationEnabled(),
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFlags.getFledgeGetAdSelectionDataBuyerInputCreatorVersion(),
                        mFlags.getFledgeGetAdSelectionDataMaxNumEntirePayloadCompressions(),
                        mFlags.getProtectedSignalsEncodedPayloadMaxSizeBytes(),
                        mClock);

        mBuyerInputGeneratorArgumentsPreparer =
                compressedBuyerInputCreatorFactory.getBuyerInputGeneratorArgumentsPreparer();

        mBuyerInputGenerator =
                new BuyerInputGenerator(
                        frequencyCapAdFilterer,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mFlags.getFledgeCustomAudienceActiveTimeWindowInMs(),
                        mFlags.getFledgeAuctionServerEnableAdFilterInGetAdSelectionData(),
                        mFlags.getProtectedSignalsPeriodicEncodingEnabled(),
                        appInstallAdFilterer,
                        compressedBuyerInputCreatorFactory);
        mPayloadFormatterVersion = mFlags.getFledgeAuctionServerPayloadFormatVersion();
        mPayloadBucketSizes = mFlags.getFledgeAuctionServerPayloadBucketSizes();
        mAdsRelevanceExecutionLogger = adsRelevanceExecutionLogger;
        mAdServicesLogger = adServicesLogger;
        mAuctionServerDebugConfigurationGenerator = auctionServerDebugConfigurationGenerator;
    }

    /** Orchestrates GetAdSelectionData process. */
    public void run(
            @NonNull GetAdSelectionDataInput inputParams,
            @NonNull GetAdSelectionDataCallback callback) {
        Objects.requireNonNull(inputParams);
        Objects.requireNonNull(callback);

        GetAdSelectionDataApiCalledStats.Builder apiCalledStatsBuilder =
                GetAdSelectionDataApiCalledStats.builder();

        int apiName = AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA;
        long adSelectionId = mAdSelectionIdGenerator.generateId();

        try {
            ListenableFuture<Void> filteredRequest =
                    Futures.submit(
                            () -> {
                                try {
                                    mAuctionServerPayloadMetricsStrategy
                                            .setServerAuctionCoordinatorSource(
                                                    apiCalledStatsBuilder,
                                                    getServerAuctionCoordinatorSourceFromUri(
                                                            inputParams.getCoordinatorOriginUri()));

                                    if (Objects.nonNull(inputParams.getSellerConfiguration())) {
                                        mAuctionServerPayloadMetricsStrategy
                                                .setSellerMaxPayloadSizeKb(
                                                        apiCalledStatsBuilder,
                                                        inputParams
                                                                        .getSellerConfiguration()
                                                                        .getMaximumPayloadSizeBytes()
                                                                / 1024);
                                    }
                                    sLogger.v("Starting filtering for GetAdSelectionData API.");
                                    mAdSelectionServiceFilter.filterRequest(
                                            inputParams.getSeller(),
                                            inputParams.getCallerPackageName(),
                                            /*enforceForeground:*/ false,
                                            /*enforceConsent:*/ true,
                                            !mDebugFlags.getConsentNotificationDebugMode(),
                                            mCallerUid,
                                            apiName,
                                            Throttler.ApiKey.FLEDGE_API_GET_AD_SELECTION_DATA,
                                            mDevContext);

                                    // Validate the coordinator origin URI
                                    mCoordinatorOriginUriValidator.validate(
                                            inputParams.getCoordinatorOriginUri());
                                } finally {
                                    sLogger.v("Completed filtering.");
                                }
                            },
                            mLightweightExecutorService);

            ListenableFuture<byte[]> getAdSelectionDataResult =
                    FluentFuture.from(filteredRequest)
                            .transformAsync(
                                    ignoredVoid ->
                                            orchestrateGetAdSelectionDataRunner(
                                                    inputParams.getSeller(),
                                                    adSelectionId,
                                                    inputParams.getCallerPackageName(),
                                                    inputParams.getCoordinatorOriginUri(),
                                                    apiCalledStatsBuilder,
                                                    inputParams.getSellerConfiguration()),
                                    mLightweightExecutorService);

            Futures.addCallback(
                    getAdSelectionDataResult,
                    new FutureCallback<>() {
                        @Override
                        public void onSuccess(byte[] result) {
                            Objects.requireNonNull(result);

                            notifySuccessToCaller(
                                    result, adSelectionId, callback, apiCalledStatsBuilder);
                            Tracing.endAsyncSection(Tracing.GET_AD_SELECTION_DATA, mE2ETraceCookie);
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            if (t instanceof FilterException
                                    && t.getCause()
                                    instanceof ConsentManager.RevokedConsentException) {
                                // Skip logging if a FilterException occurs.
                                // AdSelectionServiceFilter ensures the failing assertion is logged
                                // internally.

                                ErrorLogUtil.e(
                                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_FILTER_AND_REVOKED_CONSENT_EXCEPTION,
                                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA);
                                // Fail Silently by notifying success to caller
                                notifyEmptySuccessToCaller(callback);
                            } else {
                                if (t.getCause() instanceof AdServicesException) {
                                    ErrorLogUtil.e(
                                            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_ADSERVICES_EXCEPTION,
                                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA);
                                    notifyFailureToCaller(t.getCause(), callback);
                                } else {
                                    if (t instanceof UnsupportedPayloadSizeException) {
                                        ErrorLogUtil.e(
                                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_UNSUPPORTED_PAYLOAD_SIZE_EXCEPTION,
                                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA);
                                        mAuctionServerPayloadMetricsStrategy
                                                .logGetAdSelectionDataApiCalledStats(
                                                        apiCalledStatsBuilder,
                                                        ((UnsupportedPayloadSizeException) t)
                                                                .getPayloadSizeKb(),
                                                        STATUS_INTERNAL_ERROR);
                                    }
                                    notifyFailureToCaller(t, callback);
                                }
                            }
                            Tracing.endAsyncSection(Tracing.GET_AD_SELECTION_DATA, mE2ETraceCookie);
                        }
                    },
                    mLightweightExecutorService);
        } catch (Throwable t) {
            sLogger.v("runOutcomeSelection fails fast with exception %s.", t.toString());
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_RUNNER_OUTCOME_SELECTION_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA);
            notifyFailureToCaller(t, callback);
        }
    }

    private ListenableFuture<byte[]> orchestrateGetAdSelectionDataRunner(
            @NonNull AdTechIdentifier seller,
            long adSelectionId,
            @NonNull String packageName,
            @Nullable Uri coordinatorUrl,
            GetAdSelectionDataApiCalledStats.Builder apiCalledStatsBuilder,
            SellerConfiguration sellerConfiguration) {
        Objects.requireNonNull(seller);
        Objects.requireNonNull(packageName);

        int traceCookie = Tracing.beginAsyncSection(Tracing.ORCHESTRATE_GET_AD_SELECTION_DATA);

        long keyFetchTimeout = mFlags.getFledgeAuctionServerAuctionKeyFetchTimeoutMs();
        final AuctionServerPayloadInfo.Builder auctionServerPayloadInfoBuilder =
                AuctionServerPayloadInfo.builder()
                        .setAdSelectionDataId(adSelectionId)
                        .setPackageName(packageName);
        return setDebugConfiguration(auctionServerPayloadInfoBuilder, packageName, mCallerUid)
                .transformAsync(
                        auctionServerPayloadInfoBuilder2 ->
                                setCompressedBuyerInputs(
                                        auctionServerPayloadInfoBuilder2,
                                        mBuyerInputGeneratorArgumentsPreparer
                                                .preparePayloadOptimizationContext(
                                                        sellerConfiguration,
                                                        getCurrentPayloadSize(
                                                                auctionServerPayloadInfoBuilder2
                                                                        .setCompressedBuyerInput(
                                                                                ImmutableMap.of())
                                                                        .build())),
                                        apiCalledStatsBuilder),
                        mLightweightExecutorService)
                .transform(
                        auctionServerPayloadInfoBuilder3 ->
                                createPayload(
                                        auctionServerPayloadInfoBuilder3.build(),
                                        apiCalledStatsBuilder,
                                        sellerConfiguration),
                        mLightweightExecutorService)
                .transformAsync(
                        formatted -> {
                            sLogger.v("Encrypting composed proto bytes");
                            return mObliviousHttpEncryptor.encryptBytes(
                                    formatted.getData(),
                                    adSelectionId,
                                    keyFetchTimeout,
                                    coordinatorUrl,
                                    mDevContext);
                        },
                        mLightweightExecutorService)
                .transformAsync(
                        encrypted -> {
                            ListenableFuture<byte[]> encryptedBytes =
                                    persistAdSelectionIdRequest(
                                            adSelectionId, seller, packageName, encrypted);
                            Tracing.endAsyncSection(
                                    Tracing.ORCHESTRATE_GET_AD_SELECTION_DATA, traceCookie);
                            return encryptedBytes;
                        },
                        mLightweightExecutorService)
                .withTimeout(
                        mFlags.getFledgeAuctionServerOverallTimeoutMs(),
                        TimeUnit.MILLISECONDS,
                        mScheduledExecutor)
                .catching(
                        TimeoutException.class,
                        this::handleTimeoutError,
                        mLightweightExecutorService);
    }

    private FluentFuture<AuctionServerPayloadInfo.Builder> setCompressedBuyerInputs(
            AuctionServerPayloadInfo.Builder auctionServerPayloadInfoBuilder,
            PayloadOptimizationContext payloadOptimizationContext,
            GetAdSelectionDataApiCalledStats.Builder apiCalledStatsBuilder) {
        return mBuyerInputGenerator
                .createCompressedBuyerInputs(payloadOptimizationContext, apiCalledStatsBuilder)
                .transform(
                        compressedBuyerInput ->
                                auctionServerPayloadInfoBuilder.setCompressedBuyerInput(
                                        ImmutableMap.copyOf(compressedBuyerInput)),
                        mLightweightExecutorService);
    }

    private FluentFuture<AuctionServerPayloadInfo.Builder> setDebugConfiguration(
            AuctionServerPayloadInfo.Builder auctionServerPayloadInfoBuilder,
            String packageName,
            int callerUid) {
        return FluentFuture.from(
                        mAuctionServerDebugConfigurationGenerator
                                .getAuctionServerDebugConfiguration(packageName, callerUid))
                .transform(
                        auctionServerPayloadInfoBuilder::setAuctionServerDebugConfiguration,
                        mLightweightExecutorService);
    }

    @Nullable
    private byte[] handleTimeoutError(TimeoutException e) {
        sLogger.e(e, GET_AD_SELECTION_DATA_TIMED_OUT);
        ErrorLogUtil.e(
                e,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_EXCEEDED_ALLOWED_TIME_LIMIT,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA);
        throw new UncheckedTimeoutException(GET_AD_SELECTION_DATA_TIMED_OUT);
    }

    private AuctionServerPayloadFormattedData createPayload(
            AuctionServerPayloadInfo auctionServerPayloadInfo,
            GetAdSelectionDataApiCalledStats.Builder apiCalledStatsBuilder,
            @Nullable SellerConfiguration sellerConfiguration) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.CREATE_GET_AD_SELECTION_DATA_PAYLOAD);

        // Sets number of buyers
        mAuctionServerPayloadMetricsStrategy.setNumBuyers(
                apiCalledStatsBuilder, auctionServerPayloadInfo.getCompressedBuyerInput().size());

        ProtectedAuctionInput protectedAudienceInput =
                composeProtectedAuctionInputBytes(
                        auctionServerPayloadInfo.getCompressedBuyerInput(),
                        auctionServerPayloadInfo.getPackageName(),
                        auctionServerPayloadInfo.getAdSelectionDataId(),
                        auctionServerPayloadInfo.getAuctionServerDebugConfiguration());
        sLogger.v("ProtectedAuctionInput composed");
        AuctionServerPayloadFormattedData formattedData =
                applyPayloadFormatter(protectedAudienceInput, sellerConfiguration);

        Tracing.endAsyncSection(Tracing.CREATE_GET_AD_SELECTION_DATA_PAYLOAD, traceCookie);
        return formattedData;
    }

    private int getCurrentPayloadSize(AuctionServerPayloadInfo auctionServerPayloadInfo) {
        sLogger.v("Calling get current payload size");
        return composeProtectedAuctionInputBytes(
                        auctionServerPayloadInfo.getCompressedBuyerInput(),
                        auctionServerPayloadInfo.getPackageName(),
                        auctionServerPayloadInfo.getAdSelectionDataId(),
                        auctionServerPayloadInfo.getAuctionServerDebugConfiguration())
                .toByteArray()
                .length;
    }

    private ListenableFuture<byte[]> persistAdSelectionIdRequest(
            long adSelectionId,
            AdTechIdentifier seller,
            String packageName,
            byte[] encryptedBytes) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.PERSIST_AD_SELECTION_ID_REQUEST);
        return mBackgroundExecutorService.submit(
                () -> {
                    AdSelectionInitialization adSelectionInitialization =
                            AdSelectionInitialization.builder()
                                    .setSeller(seller)
                                    .setCallerPackageName(packageName)
                                    .setCreationInstant(mClock.instant())
                                    .build();
                    mAdSelectionEntryDao.persistAdSelectionInitialization(
                            adSelectionId, adSelectionInitialization);
                    Tracing.endAsyncSection(Tracing.PERSIST_AD_SELECTION_ID_REQUEST, traceCookie);
                    return encryptedBytes;
                });
    }

    @VisibleForTesting
    protected static ProtectedAuctionInput composeProtectedAuctionInputBytes(
            Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedBuyerInputs,
            String packageName,
            long adSelectionId,
            AuctionServerDebugConfiguration auctionServerDebugConfiguration) {
        sLogger.v("Composing ProtectedAuctionInput with buyer inputs and publisher");
        ProtectedAuctionInput.Builder protectedAuctionInputBuilder =
                ProtectedAuctionInput.newBuilder()
                        .putAllBuyerInput(
                                compressedBuyerInputs.entrySet().parallelStream()
                                        .collect(
                                                Collectors.toMap(
                                                        e -> e.getKey().toString(),
                                                        e ->
                                                                ByteString.copyFrom(
                                                                        e.getValue().getData()))))
                        .setPublisherName(packageName)
                        .setEnableDebugReporting(
                                auctionServerDebugConfiguration.isDebugReportingEnabled())
                        // TODO(b/288287435): Set generation ID as a UUID generated per request
                        // which is not
                        //  accessible in plaintext.
                        .setGenerationId(String.valueOf(adSelectionId))
                        .setEnableUnlimitedEgress(
                                auctionServerDebugConfiguration.isUnlimitedEgressEnabled())
                        .setProdDebug(auctionServerDebugConfiguration.isProdDebugEnabled());
        ConsentedDebugConfiguration consentedDebugConfiguration =
                auctionServerDebugConfiguration.getConsentedDebugConfiguration();
        if (consentedDebugConfiguration != null) {
            protectedAuctionInputBuilder.setConsentedDebugConfig(consentedDebugConfiguration);
        }
        return protectedAuctionInputBuilder.build();
    }

    private AuctionServerPayloadFormattedData applyPayloadFormatter(
            ProtectedAuctionInput protectedAudienceInput,
            @Nullable SellerConfiguration sellerConfiguration) {
        int version = mFlags.getFledgeAuctionServerCompressionAlgorithmVersion();
        sLogger.v("Applying formatter V" + version + " on protected audience input bytes");
        AuctionServerPayloadUnformattedData unformattedData =
                AuctionServerPayloadUnformattedData.create(protectedAudienceInput.toByteArray());

        AuctionServerPayloadFormatter payloadFormatter =
                AuctionServerPayloadFormatterFactory.createPayloadFormatter(
                        mPayloadFormatterVersion, mPayloadBucketSizes, sellerConfiguration);
        return payloadFormatter.apply(unformattedData, version);
    }

    private void notifySuccessToCaller(
            byte[] result,
            long adSelectionId,
            GetAdSelectionDataCallback callback,
            GetAdSelectionDataApiCalledStats.Builder apiCalledStatsBuilder) {
        Objects.requireNonNull(result);

        int resultCode = STATUS_SUCCESS;

        try {
            if (mPayloadFormatterVersion == AuctionServerPayloadFormatterExcessiveMaxSize.VERSION
                    || mPayloadFormatterVersion == AuctionServerPayloadFormatterExactSize.VERSION) {
                sLogger.d("Creating response with AssetFileDescriptor");
                AssetFileDescriptor assetFileDescriptor;
                try {
                    // Need to use the blocking executor here because the reader is depending on the
                    // data being written
                    assetFileDescriptor =
                            AssetFileDescriptorUtil.setupAssetFileDescriptorResponse(
                                    result, mBlockingExecutorService);
                } catch (IOException e) {
                    sLogger.e(e, "Encountered error creating response with AssetFileDescriptor");
                    ErrorLogUtil.e(
                            e,
                            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_CREATE_ASSET_FILE_DESCRIPTOR_ERROR,
                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA);
                    notifyFailureToCaller(e, callback);
                    resultCode = STATUS_IO_ERROR;
                    return;
                }
                callback.onSuccess(
                        new GetAdSelectionDataResponse.Builder()
                                .setAdSelectionId(adSelectionId)
                                .setAssetFileDescriptor(assetFileDescriptor)
                                .build());
            } else {
                callback.onSuccess(
                        new GetAdSelectionDataResponse.Builder()
                                .setAdSelectionId(adSelectionId)
                                .setAdSelectionData(result)
                                .build());
            }
        } catch (RemoteException e) {
            sLogger.e(e, "Encountered exception during notifying GetAdSelectionDataCallback");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_SUCCESS_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA);
            resultCode = STATUS_INTERNAL_ERROR;
        } finally {
            sLogger.v("Get Ad Selection Data completed and attempted notifying success");
            // The STATUS_IO_ERROR will be logged by notifyFailureToCaller.
            if (resultCode != STATUS_IO_ERROR) {
                mAdsRelevanceExecutionLogger.endAdsRelevanceApi(resultCode);
            }
            mAuctionServerPayloadMetricsStrategy.logGetAdSelectionDataApiCalledStats(
                    apiCalledStatsBuilder, result.length / 1024, resultCode);
        }
    }

    private void notifyEmptySuccessToCaller(@NonNull GetAdSelectionDataCallback callback) {
        int resultCode = STATUS_SUCCESS;
        try {
            // TODO(b/259522822): Determine what is an appropriate empty response for revoked
            //  consent for selectAdsFromOutcomes
            byte[] bytes = new byte[REVOKED_CONSENT_RANDOM_DATA_SIZE];
            new SecureRandom().nextBytes(bytes);
            callback.onSuccess(
                    new GetAdSelectionDataResponse.Builder()
                            .setAdSelectionId(mAdSelectionIdGenerator.generateId())
                            .setAdSelectionData(bytes)
                            .build());
        } catch (RemoteException e) {
            sLogger.e(e, "Encountered exception during notifying GetAdSelectionDataCallback");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_EMPTY_SUCCESS_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA);
            resultCode = STATUS_INTERNAL_ERROR;
        } finally {
            sLogger.v(
                    "Get Ad Selection Data completed, attempted notifying success for a"
                            + " silent failure");
        }
    }

    private void notifyFailureToCaller(Throwable t, GetAdSelectionDataCallback callback) {
        int resultCode = STATUS_UNSET;
        try {
            sLogger.e("Notify caller of error: " + t);
            resultCode = AdServicesLoggerUtil.getResultCodeFromException(t);

            FledgeErrorResponse selectionFailureResponse =
                    new FledgeErrorResponse.Builder()
                            .setErrorMessage("Error while collecting and compressing CAs")
                            .setStatusCode(resultCode)
                            .build();
            sLogger.e(t, "Ad Selection failure: ");
            callback.onFailure(selectionFailureResponse);
        } catch (RemoteException e) {
            sLogger.e(e, "Encountered exception during notifying GetAdSelectionDataCallback");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_CALLBACK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA);
            resultCode = STATUS_INTERNAL_ERROR;
        } finally {
            sLogger.v("Get Ad Selection Data failed");
            logGetAdSelectionDataRunnerExceptionCel(resultCode);
            mAdsRelevanceExecutionLogger.endAdsRelevanceApi(resultCode);
        }
    }

    @AdsRelevanceStatusUtils.ServerAuctionCoordinatorSource
    private int getServerAuctionCoordinatorSourceFromUri(@Nullable Uri coordinatorUri) {
        if (mFlags.getFledgeAuctionServerMultiCloudEnabled() && coordinatorUri != null) {
            return SERVER_AUCTION_COORDINATOR_SOURCE_API;
        }
        return SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT;
    }

    private void logGetAdSelectionDataRunnerExceptionCel(int resultCode) {
        int celEnum = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_CODE_UNSPECIFIED;
        switch (resultCode) {
            case STATUS_TIMEOUT:
                celEnum = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_TIMEOUT;
                break;
            case STATUS_JS_SANDBOX_UNAVAILABLE:
                celEnum = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_JS_SANDBOX_UNAVAILABLE;
                break;
            case STATUS_INVALID_ARGUMENT:
                celEnum = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_INVALID_ARGUMENT;
                break;
            case STATUS_INTERNAL_ERROR:
                celEnum = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_INTERNAL_ERROR;
                break;
            case STATUS_USER_CONSENT_REVOKED:
                celEnum = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_FILTER_EXCEPTION_USER_CONSENT_REVOKED;
                break;
            case STATUS_BACKGROUND_CALLER:
                celEnum = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_FILTER_EXCEPTION_BACKGROUND_CALLER;
                break;
            case STATUS_CALLER_NOT_ALLOWED:
                celEnum = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_FILTER_EXCEPTION_CALLER_NOT_ALLOWED;
                break;
            case STATUS_UNAUTHORIZED:
                celEnum = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_FILTER_EXCEPTION_UNAUTHORIZED;
                break;
            case STATUS_RATE_LIMIT_REACHED:
                celEnum = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_FILTER_EXCEPTION_RATE_LIMIT_REACHED;
                break;
            default:
                // Skip the error logging if celEnum is
                // AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_CODE_UNSPECIFIED.
                return;
        }
        ErrorLogUtil.e(celEnum, AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA);
    }
}
