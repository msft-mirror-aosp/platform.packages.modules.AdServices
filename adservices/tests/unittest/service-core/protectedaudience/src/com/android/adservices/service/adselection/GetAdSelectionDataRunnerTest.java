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

import static android.adservices.adselection.SellerConfigurationFixture.SELLER_CONFIGURATION;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_TIMEOUT;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;
import static com.android.adservices.service.stats.AdServicesLoggerUtil.FIELD_UNSET;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_CREATE_ASSET_FILE_DESCRIPTOR_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_EXCEEDED_ALLOWED_TIME_LIMIT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_FILTER_AND_REVOKED_CONSENT_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_INTERNAL_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_INVALID_ARGUMENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_TIMEOUT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA;
import static com.android.adservices.service.stats.AdsRelevanceExecutionLoggerImplFixture.BINDER_ELAPSED_TIMESTAMP;
import static com.android.adservices.service.stats.AdsRelevanceExecutionLoggerImplFixture.GET_AD_SELECTION_DATA_END_TIMESTAMP;
import static com.android.adservices.service.stats.AdsRelevanceExecutionLoggerImplFixture.GET_AD_SELECTION_DATA_OVERALL_LATENCY_MS;
import static com.android.adservices.service.stats.AdsRelevanceExecutionLoggerImplFixture.GET_AD_SELECTION_DATA_START_TIMESTAMP;
import static com.android.adservices.service.stats.AdsRelevanceExecutionLoggerImplFixture.sCallerMetadata;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_COORDINATOR_SOURCE_API;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_COORDINATOR_SOURCE_UNSET;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.GetAdSelectionDataCallback;
import android.adservices.adselection.GetAdSelectionDataInput;
import android.adservices.adselection.GetAdSelectionDataResponse;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.AssetFileDescriptorUtil;
import android.adservices.common.FledgeErrorResponse;
import android.net.Uri;
import android.os.Process;
import android.os.RemoteException;

import androidx.room.Room;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.datahandlers.AdSelectionInitialization;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.debug.AuctionServerDebugConfiguration;
import com.android.adservices.service.adselection.debug.AuctionServerDebugConfigurationGenerator;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.ConsentedDebugConfiguration;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.ProtectedAuctionInput;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.AdsRelevanceExecutionLogger;
import com.android.adservices.service.stats.AdsRelevanceExecutionLoggerFactory;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.service.stats.GetAdSelectionDataApiCalledStats;
import com.android.adservices.service.stats.GetAdSelectionDataBuyerInputGeneratedStats;
import com.android.adservices.shared.testing.concurrency.ResultSyncCallback;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Truth;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.protobuf.ByteString;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.Returns;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@SpyStatic(FlagsFactory.class)
@SpyStatic(DebugFlags.class)
@MockStatic(PackageManagerCompatUtils.class)
@SpyStatic(AssetFileDescriptorUtil.class)
@SetErrorLogUtilDefaultParams(
        throwable = Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA)
public final class GetAdSelectionDataRunnerTest extends AdServicesExtendedMockitoTestCase {
    private static final int CALLER_UID = Process.myUid();
    private static final int E2E_TRACE_COOKIE = 0;
    private static final String CALLER_PACKAGE_NAME = TEST_PACKAGE_NAME;
    private static final ExecutorService BLOCKING_EXECUTOR =
            AdServicesExecutors.getBlockingExecutor();
    private static final AdTechIdentifier SELLER = AdSelectionConfigFixture.SELLER_1;
    private static final AdTechIdentifier BUYER_1 = AdSelectionConfigFixture.BUYER_1;
    private static final AdTechIdentifier BUYER_2 = AdSelectionConfigFixture.BUYER_2;
    private static final byte[] CIPHER_TEXT_BYTES =
            "encrypted-cipher-for-auction-result".getBytes(StandardCharsets.UTF_8);

    private static final Instant AD_SELECTION_INITIALIZATION_INSTANT =
            Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final boolean FLEDGE_AUCTION_SERVER_API_USAGE_METRICS_ENABLED_IN_TEST = true;
    private static final boolean FLEDGE_AUCTION_SERVER_KEY_FETCH_METRICS_ENABLED_IN_TEST = true;
    private static final String ALLOW_LIST_COORDINATORS = "https://example.com";
    private static final int NUM_BUYERS = 2;
    private static final boolean IS_CONSENTED_DEBUG_ENABLED = true;
    private static final String DEBUG_TOKEN = UUID.randomUUID().toString();
    private static final BiddingAuctionServers.ConsentedDebugConfiguration
            CONSENTED_DEBUG_CONFIGURATION =
                    BiddingAuctionServers.ConsentedDebugConfiguration.newBuilder()
                            .setIsConsented(IS_CONSENTED_DEBUG_ENABLED)
                            .setToken(DEBUG_TOKEN)
                            .setIsDebugInfoInResponse(false)
                            .build();
    private Flags mLegacyFakeFlags;
    private ExecutorService mLightweightExecutorService;
    private ExecutorService mBackgroundExecutorService;
    private ScheduledThreadPoolExecutor mScheduledExecutor;
    private CustomAudienceDao mCustomAudienceDao;
    private EncodedPayloadDao mEncodedPayloadDao;
    private MultiCloudSupportStrategy mMultiCloudSupportStrategyFlagOff;
    private MultiCloudSupportStrategy mMultiCloudSupportStrategyFlagOn;
    private AdSelectionEntryDao mAdSelectionEntryDaoSpy;
    @Mock private ObliviousHttpEncryptor mObliviousHttpEncryptorMock;
    @Mock private AdSelectionServiceFilter mAdSelectionServiceFilterMock;

    @Spy
    private FrequencyCapAdFilterer mFrequencyCapAdFiltererSpy =
            new FrequencyCapAdFiltererNoOpImpl();

    @Spy private AppInstallAdFilterer mAppInstallAdFiltererSpy = new AppInstallAdFiltererNoOpImpl();
    @Mock private Clock mClockMock;

    private GetAdSelectionDataRunner mGetAdSelectionDataRunner;

    @Mock
    private com.android.adservices.shared.util.Clock mFledgeAuctionServerExecutionLoggerClockMock;

    private AdsRelevanceExecutionLoggerFactory mAdsRelevanceExecutionLoggerFactory;
    private AdsRelevanceExecutionLogger mAdsRelevanceExecutionLogger;

    private ResultSyncCallback<ApiCallStats> mLogApiCallStatsCallback;

    private AdServicesLogger mAdServicesLoggerSpy;

    @Mock
    private AuctionServerDebugConfigurationGenerator mAuctionServerDebugConfigurationGenerator;

    @Before
    public void setup() throws Exception {
        mLegacyFakeFlags = new GetAdSelectionDataRunnerTestFlags();
        mocker.mockGetDebugFlags(mFakeDebugFlags);
        mockGetConsentNotificationDebugMode(false);
        mMultiCloudSupportStrategyFlagOff =
                MultiCloudTestStrategyFactory.getDisabledTestStrategy(mObliviousHttpEncryptorMock);
        mMultiCloudSupportStrategyFlagOn =
                MultiCloudTestStrategyFactory.getEnabledTestStrategy(
                        mObliviousHttpEncryptorMock, ALLOW_LIST_COORDINATORS);
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mScheduledExecutor = AdServicesExecutors.getScheduler();

        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(mContext, CustomAudienceDatabase.class)
                        .addTypeConverter(new DBCustomAudience.Converters(true, true, true))
                        .build()
                        .customAudienceDao();
        mEncodedPayloadDao =
                Room.inMemoryDatabaseBuilder(mContext, ProtectedSignalsDatabase.class)
                        .build()
                        .getEncodedPayloadDao();
        mAdSelectionEntryDaoSpy =
                spy(
                        Room.inMemoryDatabaseBuilder(mContext, AdSelectionDatabase.class)
                                .build()
                                .adSelectionEntryDao());

        doNothing()
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        SELLER,
                        CALLER_PACKAGE_NAME,
                        true,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                        DevContext.createForDevOptionsDisabled());
        when(mClockMock.instant()).thenReturn(AD_SELECTION_INITIALIZATION_INSTANT);
        mAdServicesLoggerSpy = spy(AdServicesLoggerImpl.getInstance());
        mLogApiCallStatsCallback = mocker.mockLogApiCallStats(mAdServicesLoggerSpy);
        mAdsRelevanceExecutionLoggerFactory =
                new AdsRelevanceExecutionLoggerFactory(
                        TEST_PACKAGE_NAME,
                        sCallerMetadata,
                        mFledgeAuctionServerExecutionLoggerClockMock,
                        mAdServicesLoggerSpy,
                        mLegacyFakeFlags,
                        AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA);
        mAdsRelevanceExecutionLogger =
                mAdsRelevanceExecutionLoggerFactory.getAdsRelevanceExecutionLogger();
        when(mAuctionServerDebugConfigurationGenerator.getAuctionServerDebugConfiguration(
                        CALLER_PACKAGE_NAME, CALLER_UID))
                .thenReturn(
                        Futures.immediateFuture(
                                AuctionServerDebugConfiguration.builder()
                                        .setProdDebugEnabled(true)
                                        .setDebugReportingEnabled(true)
                                        .setUnlimitedEgressEnabled(true)
                                        .setConsentedDebugConfiguration(
                                                CONSENTED_DEBUG_CONFIGURATION)
                                        .build()));
        mGetAdSelectionDataRunner = initRunner(mLegacyFakeFlags, mAdsRelevanceExecutionLogger);
    }

    @Test
    public void testRunner_getAdSelectionData_returnsSuccess() throws Exception {
        mocker.mockGetFlags(mLegacyFakeFlags);
        doReturn(FluentFuture.from(immediateFuture(CIPHER_TEXT_BYTES)))
                .when(mObliviousHttpEncryptorMock)
                .encryptBytes(any(), anyLong(), anyLong(), any(), any());
        mockGetAdSelectionDataRunnerWithFledgeAuctionServerExecutionLogger(
                MultiCloudTestStrategyFactory.getDisabledTestStrategy(mObliviousHttpEncryptorMock));

        createAndPersistDBCustomAudiencesWithAdRenderId();
        GetAdSelectionDataInput inputParams =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(mGetAdSelectionDataRunner, inputParams);

        Assert.assertTrue(
                "Call failed with response " + callback.mFledgeErrorResponse, callback.mIsSuccess);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse.getAdSelectionData());
        Assert.assertArrayEquals(
                CIPHER_TEXT_BYTES, callback.mGetAdSelectionDataResponse.getAdSelectionData());
        Assert.assertTrue(callback.mGetAdSelectionDataResponse.getAdSelectionData().length > 0);
        verify(mObliviousHttpEncryptorMock, times(1))
                .encryptBytes(any(), anyLong(), anyLong(), any(), any());
        verify(mAdSelectionEntryDaoSpy, times(1))
                .persistAdSelectionInitialization(
                        callback.mGetAdSelectionDataResponse.getAdSelectionId(),
                        AdSelectionInitialization.builder()
                                .setSeller(SELLER)
                                .setCallerPackageName(CALLER_PACKAGE_NAME)
                                .setCreationInstant(AD_SELECTION_INITIALIZATION_INSTANT)
                                .build());
        verify(mFrequencyCapAdFiltererSpy).filterCustomAudiences(any());

        verifyGetAdSelectionDataApiUsageLog(STATUS_SUCCESS);
        verify(mAuctionServerDebugConfigurationGenerator)
                .getAuctionServerDebugConfiguration(CALLER_PACKAGE_NAME, CALLER_UID);
    }

    @Test
    public void testRunner_getAdSelectionData_returnsSuccessWithUXNotificationEnforcementDisabled()
            throws Exception {
        mockGetConsentNotificationDebugMode(true);

        mocker.mockGetFlags(mLegacyFakeFlags);
        doReturn(FluentFuture.from(immediateFuture(CIPHER_TEXT_BYTES)))
                .when(mObliviousHttpEncryptorMock)
                .encryptBytes(any(), anyLong(), anyLong(), any(), any());
        mockGetAdSelectionDataRunnerWithFledgeAuctionServerExecutionLogger(
                MultiCloudTestStrategyFactory.getDisabledTestStrategy(mObliviousHttpEncryptorMock));

        createAndPersistDBCustomAudiencesWithAdRenderId();
        GetAdSelectionDataInput inputParams =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataRunner getAdSelectionDataRunner =
                initRunner(mLegacyFakeFlags, mAdsRelevanceExecutionLogger);

        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(getAdSelectionDataRunner, inputParams);

        Assert.assertTrue(
                "Call failed with response " + callback.mFledgeErrorResponse, callback.mIsSuccess);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse.getAdSelectionData());
        Assert.assertArrayEquals(
                CIPHER_TEXT_BYTES, callback.mGetAdSelectionDataResponse.getAdSelectionData());
        Assert.assertTrue(callback.mGetAdSelectionDataResponse.getAdSelectionData().length > 0);
        verify(mObliviousHttpEncryptorMock, times(1))
                .encryptBytes(any(), anyLong(), anyLong(), any(), any());
        verify(mAdSelectionEntryDaoSpy, times(1))
                .persistAdSelectionInitialization(
                        callback.mGetAdSelectionDataResponse.getAdSelectionId(),
                        AdSelectionInitialization.builder()
                                .setSeller(SELLER)
                                .setCallerPackageName(CALLER_PACKAGE_NAME)
                                .setCreationInstant(AD_SELECTION_INITIALIZATION_INSTANT)
                                .build());
        verify(mFrequencyCapAdFiltererSpy).filterCustomAudiences(any());

        verifyGetAdSelectionDataApiUsageLog(STATUS_SUCCESS);
        verify(mAdSelectionServiceFilterMock)
                .filterRequest(
                        SELLER,
                        CALLER_PACKAGE_NAME,
                        false,
                        true,
                        false,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA,
                        Throttler.ApiKey.FLEDGE_API_GET_AD_SELECTION_DATA,
                        DevContext.createForDevOptionsDisabled());
        verify(mAuctionServerDebugConfigurationGenerator)
                .getAuctionServerDebugConfiguration(CALLER_PACKAGE_NAME, CALLER_UID);
    }

    @Test
    public void testRunner_getAdSelectionData_WithSellerConfigurationSuccessNoOptimizations()
            throws Exception {
        ArgumentCaptor<GetAdSelectionDataApiCalledStats> apiCalledArgumentCaptor =
                ArgumentCaptor.forClass(GetAdSelectionDataApiCalledStats.class);
        ArgumentCaptor<GetAdSelectionDataBuyerInputGeneratedStats>
                buyerInputGeneratedStatsArgumentCaptor =
                        ArgumentCaptor.forClass(GetAdSelectionDataBuyerInputGeneratedStats.class);

        // Mock clock to make latency of input generation 100
        when(mClockMock.millis()).thenReturn(0L).thenReturn(100L);

        Flags flags =
                new GetAdSelectionDataRunnerTestFlagsWithSellerConfigurationEnabled(
                        CompressedBuyerInputCreatorNoOptimizations.VERSION);

        doReturn(flags).when(FlagsFactory::getFlags);
        doReturn(FluentFuture.from(immediateFuture(CIPHER_TEXT_BYTES)))
                .when(mObliviousHttpEncryptorMock)
                .encryptBytes(any(), anyLong(), anyLong(), any(), any());

        when(mFledgeAuctionServerExecutionLoggerClockMock.elapsedRealtime())
                .thenReturn(
                        BINDER_ELAPSED_TIMESTAMP,
                        GET_AD_SELECTION_DATA_START_TIMESTAMP,
                        GET_AD_SELECTION_DATA_END_TIMESTAMP);
        mAdsRelevanceExecutionLogger =
                mAdsRelevanceExecutionLoggerFactory.getAdsRelevanceExecutionLogger();
        mGetAdSelectionDataRunner =
                initRunner(
                        flags,
                        mAdsRelevanceExecutionLogger,
                        MultiCloudTestStrategyFactory.getDisabledTestStrategy(
                                mObliviousHttpEncryptorMock),
                        new AuctionServerPayloadMetricsStrategyEnabled(
                                mAdServicesLoggerSpy,
                                new SellerConfigurationMetricsStrategyEnabled()));

        createAndPersistDBCustomAudiencesWithAdRenderId();
        GetAdSelectionDataInput inputParams =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setSellerConfiguration(SELLER_CONFIGURATION)
                        .build();

        // Create a logging latch with count of 3, 2 for buyer input logs and 1 for api logs
        CountDownLatch loggingLatch = new CountDownLatch(3);
        Answer<Void> countDownAnswer =
                unused -> {
                    loggingLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerSpy)
                .logGetAdSelectionDataApiCalledStats(any());
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerSpy)
                .logGetAdSelectionDataBuyerInputGeneratedStats(any());

        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(mGetAdSelectionDataRunner, inputParams);

        Assert.assertTrue(
                "Call failed with response " + callback.mFledgeErrorResponse, callback.mIsSuccess);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse.getAdSelectionData());
        Assert.assertArrayEquals(
                CIPHER_TEXT_BYTES, callback.mGetAdSelectionDataResponse.getAdSelectionData());
        Assert.assertTrue(callback.mGetAdSelectionDataResponse.getAdSelectionData().length > 0);
        verify(mObliviousHttpEncryptorMock, times(1))
                .encryptBytes(any(), anyLong(), anyLong(), any(), any());
        verify(mAdSelectionEntryDaoSpy, times(1))
                .persistAdSelectionInitialization(
                        callback.mGetAdSelectionDataResponse.getAdSelectionId(),
                        AdSelectionInitialization.builder()
                                .setSeller(SELLER)
                                .setCallerPackageName(CALLER_PACKAGE_NAME)
                                .setCreationInstant(AD_SELECTION_INITIALIZATION_INSTANT)
                                .build());
        verify(mFrequencyCapAdFiltererSpy).filterCustomAudiences(any());

        loggingLatch.await();
        // Verify GetAdSelectionDataBuyerInputGeneratedStats metrics
        verify(mAdServicesLoggerSpy, times(2))
                .logGetAdSelectionDataBuyerInputGeneratedStats(
                        buyerInputGeneratedStatsArgumentCaptor.capture());
        List<GetAdSelectionDataBuyerInputGeneratedStats> stats =
                buyerInputGeneratedStatsArgumentCaptor.getAllValues();

        GetAdSelectionDataBuyerInputGeneratedStats stats1 = stats.get(0);
        assertThat(stats1.getNumCustomAudiences()).isEqualTo(2);
        assertThat(stats1.getNumCustomAudiencesOmitAds()).isEqualTo(0);

        GetAdSelectionDataBuyerInputGeneratedStats stats2 = stats.get(1);
        assertThat(stats2.getNumCustomAudiences()).isEqualTo(1);
        assertThat(stats2.getNumCustomAudiencesOmitAds()).isEqualTo(0);

        verifyGetAdSelectionDataApiUsageLog(STATUS_SUCCESS);

        // Verify GetAdSelectionDataApiCalledStats metrics
        verify(mAdServicesLoggerSpy)
                .logGetAdSelectionDataApiCalledStats(apiCalledArgumentCaptor.capture());
        assertThat(apiCalledArgumentCaptor.getValue().getStatusCode()).isEqualTo(STATUS_SUCCESS);
        assertThat(apiCalledArgumentCaptor.getValue().getPayloadSizeKb())
                .isEqualTo(CIPHER_TEXT_BYTES.length / 1024);
        assertThat(apiCalledArgumentCaptor.getValue().getNumBuyers()).isEqualTo(NUM_BUYERS);
        assertThat(apiCalledArgumentCaptor.getValue().getServerAuctionCoordinatorSource())
                .isEqualTo(SERVER_AUCTION_COORDINATOR_SOURCE_UNSET);
        assertThat(apiCalledArgumentCaptor.getValue().getSellerMaxSizeKb())
                .isEqualTo(SELLER_CONFIGURATION.getMaximumPayloadSizeBytes() / 1024);
        assertThat(apiCalledArgumentCaptor.getValue().getPayloadOptimizationResult())
                .isEqualTo(
                        GetAdSelectionDataApiCalledStats.PayloadOptimizationResult
                                .PAYLOAD_OPTIMIZATION_RESULT_UNKNOWN);
        assertThat(apiCalledArgumentCaptor.getValue().getInputGenerationLatencyMs()).isEqualTo(100);
        assertThat(apiCalledArgumentCaptor.getValue().getCompressedBuyerInputCreatorVersion())
                .isEqualTo(CompressedBuyerInputCreatorNoOptimizations.VERSION);
        assertThat(apiCalledArgumentCaptor.getValue().getNumReEstimations()).isEqualTo(FIELD_UNSET);
    }

    @Test
    public void testRunner_getAdSelectionData_WithSellerConfigurationSuccessSellerMaxImpl()
            throws Exception {
        ArgumentCaptor<GetAdSelectionDataApiCalledStats> apiCalledArgumentCaptor =
                ArgumentCaptor.forClass(GetAdSelectionDataApiCalledStats.class);
        ArgumentCaptor<GetAdSelectionDataBuyerInputGeneratedStats>
                buyerInputGeneratedStatsArgumentCaptor =
                        ArgumentCaptor.forClass(GetAdSelectionDataBuyerInputGeneratedStats.class);

        // Mock clock to make latency of input generation 100
        when(mClockMock.millis()).thenReturn(0L).thenReturn(100L);

        Flags flags =
                new GetAdSelectionDataRunnerTestFlagsWithSellerConfigurationEnabled(
                        CompressedBuyerInputCreatorSellerPayloadMaxImpl.VERSION);

        doReturn(flags).when(FlagsFactory::getFlags);
        doReturn(FluentFuture.from(immediateFuture(CIPHER_TEXT_BYTES)))
                .when(mObliviousHttpEncryptorMock)
                .encryptBytes(any(), anyLong(), anyLong(), any(), any());

        when(mFledgeAuctionServerExecutionLoggerClockMock.elapsedRealtime())
                .thenReturn(
                        BINDER_ELAPSED_TIMESTAMP,
                        GET_AD_SELECTION_DATA_START_TIMESTAMP,
                        GET_AD_SELECTION_DATA_END_TIMESTAMP);
        mAdsRelevanceExecutionLogger =
                mAdsRelevanceExecutionLoggerFactory.getAdsRelevanceExecutionLogger();
        mGetAdSelectionDataRunner =
                initRunner(
                        flags,
                        mAdsRelevanceExecutionLogger,
                        MultiCloudTestStrategyFactory.getDisabledTestStrategy(
                                mObliviousHttpEncryptorMock),
                        new AuctionServerPayloadMetricsStrategyEnabled(
                                mAdServicesLoggerSpy,
                                new SellerConfigurationMetricsStrategyEnabled()));

        createAndPersistDBCustomAudiencesWithAdRenderId();
        GetAdSelectionDataInput inputParams =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setSellerConfiguration(SELLER_CONFIGURATION)
                        .build();

        // Create a logging latch with count of 3, 2 for buyer input logs and 1 for api logs
        CountDownLatch loggingLatch = new CountDownLatch(3);
        Answer<Void> countDownAnswer =
                unused -> {
                    loggingLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerSpy)
                .logGetAdSelectionDataApiCalledStats(any());
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerSpy)
                .logGetAdSelectionDataBuyerInputGeneratedStats(any());

        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(mGetAdSelectionDataRunner, inputParams);

        Assert.assertTrue(
                "Call failed with response " + callback.mFledgeErrorResponse, callback.mIsSuccess);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse.getAdSelectionData());
        Assert.assertArrayEquals(
                CIPHER_TEXT_BYTES, callback.mGetAdSelectionDataResponse.getAdSelectionData());
        Assert.assertTrue(callback.mGetAdSelectionDataResponse.getAdSelectionData().length > 0);
        verify(mObliviousHttpEncryptorMock, times(1))
                .encryptBytes(any(), anyLong(), anyLong(), any(), any());
        verify(mAdSelectionEntryDaoSpy, times(1))
                .persistAdSelectionInitialization(
                        callback.mGetAdSelectionDataResponse.getAdSelectionId(),
                        AdSelectionInitialization.builder()
                                .setSeller(SELLER)
                                .setCallerPackageName(CALLER_PACKAGE_NAME)
                                .setCreationInstant(AD_SELECTION_INITIALIZATION_INSTANT)
                                .build());
        verify(mFrequencyCapAdFiltererSpy).filterCustomAudiences(any());

        loggingLatch.await();
        // Verify GetAdSelectionDataBuyerInputGeneratedStats metrics
        verify(mAdServicesLoggerSpy, times(2))
                .logGetAdSelectionDataBuyerInputGeneratedStats(
                        buyerInputGeneratedStatsArgumentCaptor.capture());
        List<GetAdSelectionDataBuyerInputGeneratedStats> stats =
                buyerInputGeneratedStatsArgumentCaptor.getAllValues();

        GetAdSelectionDataBuyerInputGeneratedStats stats1 = stats.get(0);
        assertThat(stats1.getNumCustomAudiences()).isEqualTo(2);
        assertThat(stats1.getNumCustomAudiencesOmitAds()).isEqualTo(0);

        GetAdSelectionDataBuyerInputGeneratedStats stats2 = stats.get(1);
        assertThat(stats2.getNumCustomAudiences()).isEqualTo(1);
        assertThat(stats2.getNumCustomAudiencesOmitAds()).isEqualTo(0);

        verifyGetAdSelectionDataApiUsageLog(STATUS_SUCCESS);

        // Verify GetAdSelectionDataApiCalledStats metrics
        verify(mAdServicesLoggerSpy)
                .logGetAdSelectionDataApiCalledStats(apiCalledArgumentCaptor.capture());
        assertThat(apiCalledArgumentCaptor.getValue().getStatusCode()).isEqualTo(STATUS_SUCCESS);
        assertThat(apiCalledArgumentCaptor.getValue().getPayloadSizeKb())
                .isEqualTo(CIPHER_TEXT_BYTES.length / 1024);
        assertThat(apiCalledArgumentCaptor.getValue().getNumBuyers()).isEqualTo(NUM_BUYERS);
        assertThat(apiCalledArgumentCaptor.getValue().getServerAuctionCoordinatorSource())
                .isEqualTo(SERVER_AUCTION_COORDINATOR_SOURCE_UNSET);
        assertThat(apiCalledArgumentCaptor.getValue().getSellerMaxSizeKb())
                .isEqualTo(SELLER_CONFIGURATION.getMaximumPayloadSizeBytes() / 1024);
        assertThat(apiCalledArgumentCaptor.getValue().getPayloadOptimizationResult())
                .isEqualTo(
                        GetAdSelectionDataApiCalledStats.PayloadOptimizationResult
                                .PAYLOAD_WITHIN_REQUESTED_MAX);
        assertThat(apiCalledArgumentCaptor.getValue().getInputGenerationLatencyMs()).isEqualTo(100);
        assertThat(apiCalledArgumentCaptor.getValue().getCompressedBuyerInputCreatorVersion())
                .isEqualTo(CompressedBuyerInputCreatorSellerPayloadMaxImpl.VERSION);
        assertThat(apiCalledArgumentCaptor.getValue().getNumReEstimations()).isEqualTo(0);
    }

    @Test
    public void
            testRunner_getAdSelectionData_returnsSuccessGetAdSelectionDataPayloadMetricsEnabled()
                    throws Exception {
        ArgumentCaptor<GetAdSelectionDataApiCalledStats> apiCalledArgumentCaptor =
                ArgumentCaptor.forClass(GetAdSelectionDataApiCalledStats.class);
        ArgumentCaptor<GetAdSelectionDataBuyerInputGeneratedStats>
                buyerInputGeneratedStatsArgumentCaptor =
                        ArgumentCaptor.forClass(GetAdSelectionDataBuyerInputGeneratedStats.class);

        mocker.mockGetFlags(mLegacyFakeFlags);
        doReturn(FluentFuture.from(immediateFuture(CIPHER_TEXT_BYTES)))
                .when(mObliviousHttpEncryptorMock)
                .encryptBytes(any(), anyLong(), anyLong(), any(), any());

        when(mFledgeAuctionServerExecutionLoggerClockMock.elapsedRealtime())
                .thenReturn(
                        BINDER_ELAPSED_TIMESTAMP,
                        GET_AD_SELECTION_DATA_START_TIMESTAMP,
                        GET_AD_SELECTION_DATA_END_TIMESTAMP);
        mAdsRelevanceExecutionLogger =
                mAdsRelevanceExecutionLoggerFactory.getAdsRelevanceExecutionLogger();
        mGetAdSelectionDataRunner =
                initRunner(
                        mLegacyFakeFlags,
                        mAdsRelevanceExecutionLogger,
                        MultiCloudTestStrategyFactory.getDisabledTestStrategy(
                                mObliviousHttpEncryptorMock),
                        new AuctionServerPayloadMetricsStrategyEnabled(
                                mAdServicesLoggerSpy,
                                new SellerConfigurationMetricsStrategyDisabled()));

        createAndPersistDBCustomAudiencesWithAdRenderId();
        GetAdSelectionDataInput inputParams =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        // Create a logging latch with count of 3, 2 for buyer input logs and 1 for api logs
        CountDownLatch loggingLatch = new CountDownLatch(3);
        Answer<Void> countDownAnswer =
                unused -> {
                    loggingLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerSpy)
                .logGetAdSelectionDataApiCalledStats(any());
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerSpy)
                .logGetAdSelectionDataBuyerInputGeneratedStats(any());

        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(mGetAdSelectionDataRunner, inputParams);

        Assert.assertTrue(
                "Call failed with response " + callback.mFledgeErrorResponse, callback.mIsSuccess);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse.getAdSelectionData());
        Assert.assertArrayEquals(
                CIPHER_TEXT_BYTES, callback.mGetAdSelectionDataResponse.getAdSelectionData());
        Assert.assertTrue(callback.mGetAdSelectionDataResponse.getAdSelectionData().length > 0);
        verify(mObliviousHttpEncryptorMock, times(1))
                .encryptBytes(any(), anyLong(), anyLong(), any(), any());
        verify(mAdSelectionEntryDaoSpy, times(1))
                .persistAdSelectionInitialization(
                        callback.mGetAdSelectionDataResponse.getAdSelectionId(),
                        AdSelectionInitialization.builder()
                                .setSeller(SELLER)
                                .setCallerPackageName(CALLER_PACKAGE_NAME)
                                .setCreationInstant(AD_SELECTION_INITIALIZATION_INSTANT)
                                .build());
        verify(mFrequencyCapAdFiltererSpy).filterCustomAudiences(any());

        loggingLatch.await();
        // Verify GetAdSelectionDataBuyerInputGeneratedStats metrics
        verify(mAdServicesLoggerSpy, times(2))
                .logGetAdSelectionDataBuyerInputGeneratedStats(
                        buyerInputGeneratedStatsArgumentCaptor.capture());
        List<GetAdSelectionDataBuyerInputGeneratedStats> stats =
                buyerInputGeneratedStatsArgumentCaptor.getAllValues();

        GetAdSelectionDataBuyerInputGeneratedStats stats1 = stats.get(0);
        assertThat(stats1.getNumCustomAudiences()).isEqualTo(2);
        assertThat(stats1.getNumCustomAudiencesOmitAds()).isEqualTo(0);

        GetAdSelectionDataBuyerInputGeneratedStats stats2 = stats.get(1);
        assertThat(stats2.getNumCustomAudiences()).isEqualTo(1);
        assertThat(stats2.getNumCustomAudiencesOmitAds()).isEqualTo(0);

        verifyGetAdSelectionDataApiUsageLog(STATUS_SUCCESS);

        // Verify GetAdSelectionDataApiCalledStats metrics
        verify(mAdServicesLoggerSpy)
                .logGetAdSelectionDataApiCalledStats(apiCalledArgumentCaptor.capture());
        assertThat(apiCalledArgumentCaptor.getValue().getStatusCode()).isEqualTo(STATUS_SUCCESS);
        assertThat(apiCalledArgumentCaptor.getValue().getPayloadSizeKb())
                .isEqualTo(CIPHER_TEXT_BYTES.length / 1000);
        assertThat(apiCalledArgumentCaptor.getValue().getNumBuyers()).isEqualTo(NUM_BUYERS);
        assertThat(apiCalledArgumentCaptor.getValue().getServerAuctionCoordinatorSource())
                .isEqualTo(SERVER_AUCTION_COORDINATOR_SOURCE_UNSET);
    }

    @Test
    public void testRunner_getAdSelectionData_success_MetricsEnabled_withDefaultSourceCoordinator()
            throws Exception {
        ArgumentCaptor<GetAdSelectionDataApiCalledStats> apiCalledArgumentCaptor =
                ArgumentCaptor.forClass(GetAdSelectionDataApiCalledStats.class);
        ArgumentCaptor<GetAdSelectionDataBuyerInputGeneratedStats>
                buyerInputGeneratedStatsArgumentCaptor =
                        ArgumentCaptor.forClass(GetAdSelectionDataBuyerInputGeneratedStats.class);

        mocker.mockGetFlags(mLegacyFakeFlags);
        doReturn(FluentFuture.from(immediateFuture(CIPHER_TEXT_BYTES)))
                .when(mObliviousHttpEncryptorMock)
                .encryptBytes(any(), anyLong(), anyLong(), any(), any());

        when(mFledgeAuctionServerExecutionLoggerClockMock.elapsedRealtime())
                .thenReturn(
                        BINDER_ELAPSED_TIMESTAMP,
                        GET_AD_SELECTION_DATA_START_TIMESTAMP,
                        GET_AD_SELECTION_DATA_END_TIMESTAMP);
        mAdsRelevanceExecutionLogger =
                mAdsRelevanceExecutionLoggerFactory.getAdsRelevanceExecutionLogger();
        mGetAdSelectionDataRunner =
                initRunner(
                        mLegacyFakeFlags,
                        mAdsRelevanceExecutionLogger,
                        MultiCloudTestStrategyFactory.getDisabledTestStrategy(
                                mObliviousHttpEncryptorMock),
                        new AuctionServerPayloadMetricsStrategyWithKeyFetchEnabled(
                                mAdServicesLoggerSpy,
                                new SellerConfigurationMetricsStrategyDisabled()));

        createAndPersistDBCustomAudiencesWithAdRenderId();
        GetAdSelectionDataInput inputParams =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        // Create a logging latch with count of 3, 2 for buyer input logs and 1 for api logs
        CountDownLatch loggingLatch = new CountDownLatch(3);
        Answer<Void> countDownAnswer =
                unused -> {
                    loggingLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerSpy)
                .logGetAdSelectionDataApiCalledStats(any());
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerSpy)
                .logGetAdSelectionDataBuyerInputGeneratedStats(any());

        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(mGetAdSelectionDataRunner, inputParams);

        Assert.assertTrue(
                "Call failed with response " + callback.mFledgeErrorResponse, callback.mIsSuccess);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse.getAdSelectionData());
        Assert.assertArrayEquals(
                CIPHER_TEXT_BYTES, callback.mGetAdSelectionDataResponse.getAdSelectionData());
        Assert.assertTrue(callback.mGetAdSelectionDataResponse.getAdSelectionData().length > 0);
        verify(mObliviousHttpEncryptorMock, times(1))
                .encryptBytes(any(), anyLong(), anyLong(), any(), any());
        verify(mAdSelectionEntryDaoSpy, times(1))
                .persistAdSelectionInitialization(
                        callback.mGetAdSelectionDataResponse.getAdSelectionId(),
                        AdSelectionInitialization.builder()
                                .setSeller(SELLER)
                                .setCallerPackageName(CALLER_PACKAGE_NAME)
                                .setCreationInstant(AD_SELECTION_INITIALIZATION_INSTANT)
                                .build());
        verify(mFrequencyCapAdFiltererSpy).filterCustomAudiences(any());
        verify(mAppInstallAdFiltererSpy).filterCustomAudiences(any());

        loggingLatch.await();
        // Verify GetAdSelectionDataBuyerInputGeneratedStats metrics
        verify(mAdServicesLoggerSpy, times(2))
                .logGetAdSelectionDataBuyerInputGeneratedStats(
                        buyerInputGeneratedStatsArgumentCaptor.capture());
        List<GetAdSelectionDataBuyerInputGeneratedStats> stats =
                buyerInputGeneratedStatsArgumentCaptor.getAllValues();

        GetAdSelectionDataBuyerInputGeneratedStats stats1 = stats.get(0);
        assertThat(stats1.getNumCustomAudiences()).isEqualTo(2);
        assertThat(stats1.getNumCustomAudiencesOmitAds()).isEqualTo(0);

        GetAdSelectionDataBuyerInputGeneratedStats stats2 = stats.get(1);
        assertThat(stats2.getNumCustomAudiences()).isEqualTo(1);
        assertThat(stats2.getNumCustomAudiencesOmitAds()).isEqualTo(0);

        verifyGetAdSelectionDataApiUsageLog(STATUS_SUCCESS);

        // Verify GetAdSelectionDataApiCalledStats metrics
        verify(mAdServicesLoggerSpy)
                .logGetAdSelectionDataApiCalledStats(apiCalledArgumentCaptor.capture());
        assertThat(apiCalledArgumentCaptor.getValue().getStatusCode()).isEqualTo(STATUS_SUCCESS);
        assertThat(apiCalledArgumentCaptor.getValue().getPayloadSizeKb())
                .isEqualTo(CIPHER_TEXT_BYTES.length / 1000);
        assertThat(apiCalledArgumentCaptor.getValue().getNumBuyers()).isEqualTo(NUM_BUYERS);
        assertThat(apiCalledArgumentCaptor.getValue().getServerAuctionCoordinatorSource())
                .isEqualTo(SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT);
    }

    @Test
    public void
            testRunner_getAdSelectionData_returnsSuccessMetricsEnabled_withApiSourceCoordinator()
                    throws Exception {
        mLegacyFakeFlags = new GetAdSelectionDataRunnerTestFlagsWithMultiCloudEnabled(true);
        ArgumentCaptor<GetAdSelectionDataApiCalledStats> apiCalledArgumentCaptor =
                ArgumentCaptor.forClass(GetAdSelectionDataApiCalledStats.class);
        ArgumentCaptor<GetAdSelectionDataBuyerInputGeneratedStats>
                buyerInputGeneratedStatsArgumentCaptor =
                        ArgumentCaptor.forClass(GetAdSelectionDataBuyerInputGeneratedStats.class);

        mocker.mockGetFlags(mLegacyFakeFlags);
        doReturn(FluentFuture.from(immediateFuture(CIPHER_TEXT_BYTES)))
                .when(mObliviousHttpEncryptorMock)
                .encryptBytes(any(), anyLong(), anyLong(), any(), any());

        when(mFledgeAuctionServerExecutionLoggerClockMock.elapsedRealtime())
                .thenReturn(
                        BINDER_ELAPSED_TIMESTAMP,
                        GET_AD_SELECTION_DATA_START_TIMESTAMP,
                        GET_AD_SELECTION_DATA_END_TIMESTAMP);
        mAdsRelevanceExecutionLogger =
                mAdsRelevanceExecutionLoggerFactory.getAdsRelevanceExecutionLogger();
        mGetAdSelectionDataRunner =
                initRunner(
                        mLegacyFakeFlags,
                        mAdsRelevanceExecutionLogger,
                        MultiCloudTestStrategyFactory.getEnabledTestStrategy(
                                mObliviousHttpEncryptorMock, ALLOW_LIST_COORDINATORS),
                        new AuctionServerPayloadMetricsStrategyWithKeyFetchEnabled(
                                mAdServicesLoggerSpy,
                                new SellerConfigurationMetricsStrategyDisabled()));

        createAndPersistDBCustomAudiencesWithAdRenderId();
        GetAdSelectionDataInput inputParams =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setCoordinatorOriginUri(Uri.parse(ALLOW_LIST_COORDINATORS))
                        .build();

        // Create a logging latch with count of 3, 2 for buyer input logs and 1 for api logs
        CountDownLatch loggingLatch = new CountDownLatch(3);
        Answer<Void> countDownAnswer =
                unused -> {
                    loggingLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerSpy)
                .logGetAdSelectionDataApiCalledStats(any());
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerSpy)
                .logGetAdSelectionDataBuyerInputGeneratedStats(any());

        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(mGetAdSelectionDataRunner, inputParams);

        Assert.assertTrue(
                "Call failed with response " + callback.mFledgeErrorResponse, callback.mIsSuccess);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse.getAdSelectionData());
        Assert.assertArrayEquals(
                CIPHER_TEXT_BYTES, callback.mGetAdSelectionDataResponse.getAdSelectionData());
        Assert.assertTrue(callback.mGetAdSelectionDataResponse.getAdSelectionData().length > 0);
        verify(mObliviousHttpEncryptorMock, times(1))
                .encryptBytes(any(), anyLong(), anyLong(), any(), any());
        verify(mAdSelectionEntryDaoSpy, times(1))
                .persistAdSelectionInitialization(
                        callback.mGetAdSelectionDataResponse.getAdSelectionId(),
                        AdSelectionInitialization.builder()
                                .setSeller(SELLER)
                                .setCallerPackageName(CALLER_PACKAGE_NAME)
                                .setCreationInstant(AD_SELECTION_INITIALIZATION_INSTANT)
                                .build());
        verify(mFrequencyCapAdFiltererSpy).filterCustomAudiences(any());
        verify(mAppInstallAdFiltererSpy).filterCustomAudiences(any());

        loggingLatch.await();
        // Verify GetAdSelectionDataBuyerInputGeneratedStats metrics
        verify(mAdServicesLoggerSpy, times(2))
                .logGetAdSelectionDataBuyerInputGeneratedStats(
                        buyerInputGeneratedStatsArgumentCaptor.capture());
        List<GetAdSelectionDataBuyerInputGeneratedStats> stats =
                buyerInputGeneratedStatsArgumentCaptor.getAllValues();

        GetAdSelectionDataBuyerInputGeneratedStats stats1 = stats.get(0);
        assertThat(stats1.getNumCustomAudiences()).isEqualTo(2);
        assertThat(stats1.getNumCustomAudiencesOmitAds()).isEqualTo(0);

        GetAdSelectionDataBuyerInputGeneratedStats stats2 = stats.get(1);
        assertThat(stats2.getNumCustomAudiences()).isEqualTo(1);
        assertThat(stats2.getNumCustomAudiencesOmitAds()).isEqualTo(0);

        verifyGetAdSelectionDataApiUsageLog(STATUS_SUCCESS);

        // Verify GetAdSelectionDataApiCalledStats metrics
        verify(mAdServicesLoggerSpy)
                .logGetAdSelectionDataApiCalledStats(apiCalledArgumentCaptor.capture());
        assertThat(apiCalledArgumentCaptor.getValue().getStatusCode()).isEqualTo(STATUS_SUCCESS);
        assertThat(apiCalledArgumentCaptor.getValue().getPayloadSizeKb())
                .isEqualTo(CIPHER_TEXT_BYTES.length / 1000);
        assertThat(apiCalledArgumentCaptor.getValue().getNumBuyers()).isEqualTo(NUM_BUYERS);
        assertThat(apiCalledArgumentCaptor.getValue().getServerAuctionCoordinatorSource())
                .isEqualTo(SERVER_AUCTION_COORDINATOR_SOURCE_API);
        verify(mAuctionServerDebugConfigurationGenerator)
                .getAuctionServerDebugConfiguration(CALLER_PACKAGE_NAME, CALLER_UID);
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_INVALID_ARGUMENT)
    public void testRunner_getAdSelectionData_multiCloudFlagOn_invalidCoordinator_throwsError()
            throws Exception {

        mockGetAdSelectionDataRunnerWithFledgeAuctionServerExecutionLogger(
                mMultiCloudSupportStrategyFlagOn);

        mocker.mockGetFlags(mLegacyFakeFlags);

        doReturn(FluentFuture.from(immediateFuture(CIPHER_TEXT_BYTES)))
                .when(mObliviousHttpEncryptorMock)
                .encryptBytes(any(), anyLong(), anyLong(), any(), any());

        createAndPersistDBCustomAudiencesWithAdRenderId();
        GetAdSelectionDataInput inputParams =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setCoordinatorOriginUri(Uri.parse("random-url"))
                        .build();

        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(mGetAdSelectionDataRunner, inputParams);

        Assert.assertFalse("Call should not have succeeded", callback.mIsSuccess);
        Assert.assertEquals(STATUS_INVALID_ARGUMENT, callback.mFledgeErrorResponse.getStatusCode());
    }

    @Test
    public void testRunner_getAdSelectionData_multiCloudFlagOn_validCoordinator_IsSuccess()
            throws Exception {
        mocker.mockGetFlags(mLegacyFakeFlags);

        doReturn(FluentFuture.from(immediateFuture(CIPHER_TEXT_BYTES)))
                .when(mObliviousHttpEncryptorMock)
                .encryptBytes(any(), anyLong(), anyLong(), any(), any());

        mockGetAdSelectionDataRunnerWithFledgeAuctionServerExecutionLogger(
                MultiCloudTestStrategyFactory.getEnabledTestStrategy(
                        mObliviousHttpEncryptorMock, ALLOW_LIST_COORDINATORS));

        createAndPersistDBCustomAudiencesWithAdRenderId();
        GetAdSelectionDataInput inputParams =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setCoordinatorOriginUri(Uri.parse(ALLOW_LIST_COORDINATORS))
                        .build();

        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(mGetAdSelectionDataRunner, inputParams);

        Assert.assertTrue("Call should have succeeded", callback.mIsSuccess);

        verifyGetAdSelectionDataApiUsageLog(STATUS_SUCCESS);
    }

    @Test
    public void testRunner_getAdSelectionData_multiCloudFlagOn_nullCoordinator_IsSuccess()
            throws Exception {

        mocker.mockGetFlags(mLegacyFakeFlags);

        doReturn(FluentFuture.from(immediateFuture(CIPHER_TEXT_BYTES)))
                .when(mObliviousHttpEncryptorMock)
                .encryptBytes(any(), anyLong(), anyLong(), any(), any());

        mockGetAdSelectionDataRunnerWithFledgeAuctionServerExecutionLogger(
                MultiCloudTestStrategyFactory.getEnabledTestStrategy(
                        mObliviousHttpEncryptorMock, ALLOW_LIST_COORDINATORS));

        createAndPersistDBCustomAudiencesWithAdRenderId();
        GetAdSelectionDataInput inputParams =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setCoordinatorOriginUri(null)
                        .build();

        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(mGetAdSelectionDataRunner, inputParams);

        Assert.assertTrue("Call should have succeeded", callback.mIsSuccess);

        verifyGetAdSelectionDataApiUsageLog(STATUS_SUCCESS);
    }

    @Test
    public void testRunner_getAdSelectionData_multiCloudFlagOff_invalidCoordinator_IsSuccess()
            throws Exception {

        mocker.mockGetFlags(mLegacyFakeFlags);

        doReturn(FluentFuture.from(immediateFuture(CIPHER_TEXT_BYTES)))
                .when(mObliviousHttpEncryptorMock)
                .encryptBytes(any(), anyLong(), anyLong(), any(), any());

        mockGetAdSelectionDataRunnerWithFledgeAuctionServerExecutionLogger(
                MultiCloudTestStrategyFactory.getDisabledTestStrategy(mObliviousHttpEncryptorMock));

        createAndPersistDBCustomAudiencesWithAdRenderId();
        GetAdSelectionDataInput inputParams =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setCoordinatorOriginUri(Uri.parse("a/b/c"))
                        .build();

        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(mGetAdSelectionDataRunner, inputParams);

        Assert.assertTrue("Call should have succeeded", callback.mIsSuccess);

        verifyGetAdSelectionDataApiUsageLog(STATUS_SUCCESS);
    }

    @Test
    public void testRunner_getAdSelectionData_returnsSuccessWithExcessiveSizeFormatterVersion()
            throws Exception {
        doReturn(FluentFuture.from(immediateFuture(CIPHER_TEXT_BYTES)))
                .when(mObliviousHttpEncryptorMock)
                .encryptBytes(any(), anyLong(), anyLong(), any(), any());

        mLegacyFakeFlags = new GetAdSelectionDataRunnerTestFlagsWithExcessiveSizeFormatter();
        mockGetAdSelectionDataRunnerWithFledgeAuctionServerExecutionLogger(
                MultiCloudTestStrategyFactory.getDisabledTestStrategy(mObliviousHttpEncryptorMock));

        mocker.mockGetFlags(mLegacyFakeFlags);

        createAndPersistDBCustomAudiencesWithAdRenderId();
        GetAdSelectionDataInput inputParams =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(mGetAdSelectionDataRunner, inputParams);

        Assert.assertTrue(
                "Call failed with response " + callback.mFledgeErrorResponse, callback.mIsSuccess);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse);
        // Make sure traditional byte array is null
        Assert.assertNull(callback.mGetAdSelectionDataResponse.getAdSelectionData());
        // Make sure AssetFile descriptor is not null
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse.getAssetFileDescriptor());

        // Read result into buffer
        byte[] result =
                AssetFileDescriptorUtil.readAssetFileDescriptorIntoBuffer(
                        callback.mGetAdSelectionDataResponse.getAssetFileDescriptor());

        // Assert result is expected
        Assert.assertArrayEquals(CIPHER_TEXT_BYTES, result);
        verify(mObliviousHttpEncryptorMock, times(1))
                .encryptBytes(any(), anyLong(), anyLong(), any(), any());
        verify(mAdSelectionEntryDaoSpy, times(1))
                .persistAdSelectionInitialization(
                        callback.mGetAdSelectionDataResponse.getAdSelectionId(),
                        AdSelectionInitialization.builder()
                                .setSeller(SELLER)
                                .setCallerPackageName(CALLER_PACKAGE_NAME)
                                .setCreationInstant(AD_SELECTION_INITIALIZATION_INSTANT)
                                .build());
        verify(mFrequencyCapAdFiltererSpy).filterCustomAudiences(any());

        verifyGetAdSelectionDataApiUsageLog(STATUS_SUCCESS);


    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_CREATE_ASSET_FILE_DESCRIPTOR_ERROR)
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_INTERNAL_ERROR)
    public void testRunner_getAdSelectionData_returnsInternalErrorWhenEncounteringIOException()
            throws Exception {
        mLegacyFakeFlags = new GetAdSelectionDataRunnerTestFlagsWithExcessiveSizeFormatter();
        mockGetAdSelectionDataRunnerWithFledgeAuctionServerExecutionLogger(
                mMultiCloudSupportStrategyFlagOff);
        doThrow(new IOException())
                .when(() -> AssetFileDescriptorUtil.setupAssetFileDescriptorResponse(any(), any()));

        mocker.mockGetFlags(mLegacyFakeFlags);

        doReturn(FluentFuture.from(immediateFuture(CIPHER_TEXT_BYTES)))
                .when(mObliviousHttpEncryptorMock)
                .encryptBytes(any(), anyLong(), anyLong(), any(), any());

        createAndPersistDBCustomAudiencesWithAdRenderId();
        GetAdSelectionDataInput inputParams =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(mGetAdSelectionDataRunner, inputParams);

        Assert.assertFalse("Call should not have succeeded", callback.mIsSuccess);
        Assert.assertEquals(STATUS_INTERNAL_ERROR, callback.mFledgeErrorResponse.getStatusCode());

        verifyGetAdSelectionDataApiUsageLog(STATUS_INTERNAL_ERROR);


    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_FILTER_AND_REVOKED_CONSENT_EXCEPTION)
    public void testRunner_revokedUserConsent_returnsRandomResult() throws Exception {
        mocker.mockGetFlags(mLegacyFakeFlags);
        doThrow(new FilterException(new ConsentManager.RevokedConsentException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        eq(SELLER),
                        eq(CALLER_PACKAGE_NAME),
                        eq(false),
                        eq(true),
                        eq(true),
                        eq(CALLER_UID),
                        eq(AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA),
                        eq(Throttler.ApiKey.FLEDGE_API_GET_AD_SELECTION_DATA),
                        eq(DevContext.createForDevOptionsDisabled()));

        GetAdSelectionDataInput inputParams =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(mGetAdSelectionDataRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse.getAdSelectionData());
        Assert.assertEquals(
                GetAdSelectionDataRunner.REVOKED_CONSENT_RANDOM_DATA_SIZE,
                callback.mGetAdSelectionDataResponse.getAdSelectionData().length);
        verifyZeroInteractions(mObliviousHttpEncryptorMock);
        verifyZeroInteractions(mAdSelectionEntryDaoSpy);
        verifyZeroInteractions(mFrequencyCapAdFiltererSpy);
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_FILTER_AND_REVOKED_CONSENT_EXCEPTION)
    public void testRunner_revokedUserConsent_returnsRandomResultWithExcessiveSizeFormatterVersion()
            throws Exception {
        mLegacyFakeFlags = new GetAdSelectionDataRunnerTestFlagsWithExcessiveSizeFormatter();
        mGetAdSelectionDataRunner = initRunner(mLegacyFakeFlags, mAdsRelevanceExecutionLogger);

        mocker.mockGetFlags(mLegacyFakeFlags);
        doThrow(new FilterException(new ConsentManager.RevokedConsentException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        eq(SELLER),
                        eq(CALLER_PACKAGE_NAME),
                        eq(false),
                        eq(true),
                        eq(true),
                        eq(CALLER_UID),
                        eq(AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA),
                        eq(Throttler.ApiKey.FLEDGE_API_GET_AD_SELECTION_DATA),
                        eq(DevContext.createForDevOptionsDisabled()));

        GetAdSelectionDataInput inputParams =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(mGetAdSelectionDataRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse.getAdSelectionData());
        Assert.assertEquals(
                GetAdSelectionDataRunner.REVOKED_CONSENT_RANDOM_DATA_SIZE,
                callback.mGetAdSelectionDataResponse.getAdSelectionData().length);
        verifyZeroInteractions(mObliviousHttpEncryptorMock);
        verifyZeroInteractions(mAdSelectionEntryDaoSpy);
        verifyZeroInteractions(mFrequencyCapAdFiltererSpy);
    }

    @Test
    public void test_composeProtectedAuctionInput_generatesProto() {
        byte[] buyer1data = new byte[] {2, 3};
        byte[] buyer2data = new byte[] {1};
        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> buyerInputs =
                ImmutableMap.of(
                        BUYER_1,
                        AuctionServerDataCompressor.CompressedData.create(buyer1data),
                        BUYER_2,
                        AuctionServerDataCompressor.CompressedData.create(buyer2data));

        long adSelectionId = 234L;
        AuctionServerDebugConfiguration auctionServerDebugConfiguration =
                AuctionServerDebugConfiguration.builder()
                        .setUnlimitedEgressEnabled(false)
                        .setDebugReportingEnabled(false)
                        .setProdDebugEnabled(false)
                        .build();
        ProtectedAuctionInput result =
                GetAdSelectionDataRunner.composeProtectedAuctionInputBytes(
                        buyerInputs,
                        CALLER_PACKAGE_NAME,
                        adSelectionId,
                        auctionServerDebugConfiguration);

        Map<String, ByteString> expectedBuyerInput =
                ImmutableMap.of(
                        BUYER_1.toString(),
                        ByteString.copyFrom(buyer1data),
                        BUYER_2.toString(),
                        ByteString.copyFrom(buyer2data));
        Assert.assertEquals(result.getBuyerInput(), expectedBuyerInput);
        Assert.assertEquals(result.getPublisherName(), CALLER_PACKAGE_NAME);
        Assert.assertFalse(result.getEnableDebugReporting());
        Assert.assertEquals(result.getGenerationId(), String.valueOf(adSelectionId));
    }

    @Test
    public void test_composeProtectedAuctionInput_AuctionDebugConfiguration() {
        boolean isDebugReportingEnabled = true;
        boolean isProdDebugEnabled = true;
        boolean isUnlimitedEgressEnabled = true;
        ConsentedDebugConfiguration expectedConsentedDebugConfiguration =
                ConsentedDebugConfiguration.newBuilder()
                        .setIsConsented(false)
                        .setToken("")
                        .setIsDebugInfoInResponse(false)
                        .build();
        long adSelectionId = 234L;
        mocker.mockGetFlags(mLegacyFakeFlags);
        mockGetAdSelectionDataRunnerWithFledgeAuctionServerExecutionLogger(
                mMultiCloudSupportStrategyFlagOff);
        AuctionServerDebugConfiguration auctionServerDebugConfiguration =
                AuctionServerDebugConfiguration.builder()
                        .setDebugReportingEnabled(isDebugReportingEnabled)
                        .setProdDebugEnabled(isProdDebugEnabled)
                        .setUnlimitedEgressEnabled(isUnlimitedEgressEnabled)
                        .build();
        ProtectedAuctionInput result =
                GetAdSelectionDataRunner.composeProtectedAuctionInputBytes(
                        createTestBuyerInputs(),
                        CALLER_PACKAGE_NAME,
                        adSelectionId,
                        auctionServerDebugConfiguration);

        Truth.assertThat(result.getEnableDebugReporting()).isEqualTo(isDebugReportingEnabled);
        Truth.assertThat(result.getProdDebug()).isEqualTo(isProdDebugEnabled);
        Truth.assertThat(result.getEnableUnlimitedEgress()).isEqualTo(isUnlimitedEgressEnabled);
        ConsentedDebugConfiguration actual = result.getConsentedDebugConfig();
        Truth.assertThat(actual).isEqualTo(expectedConsentedDebugConfiguration);
    }

    @Test
    public void test_composeProtectedAuctionInput_generatesConsentedDebugConfig() {
        byte[] buyer1data = new byte[] {2, 3};
        byte[] buyer2data = new byte[] {1};
        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> buyerInputs =
                ImmutableMap.of(
                        BUYER_1,
                        AuctionServerDataCompressor.CompressedData.create(buyer1data),
                        BUYER_2,
                        AuctionServerDataCompressor.CompressedData.create(buyer2data));
        boolean isConsentedDebugEnabled = true;
        String debugToken = UUID.randomUUID().toString();
        ConsentedDebugConfiguration expectedConsentedDebugConfiguration =
                ConsentedDebugConfiguration.newBuilder()
                        .setIsConsented(isConsentedDebugEnabled)
                        .setToken(debugToken)
                        .setIsDebugInfoInResponse(false)
                        .build();
        AuctionServerDebugConfiguration auctionServerDebugConfiguration =
                AuctionServerDebugConfiguration.builder()
                        .setConsentedDebugConfiguration(expectedConsentedDebugConfiguration)
                        .setUnlimitedEgressEnabled(false)
                        .setDebugReportingEnabled(false)
                        .setProdDebugEnabled(false)
                        .build();
        long adSelectionId = 234L;
        ProtectedAuctionInput result =
                GetAdSelectionDataRunner.composeProtectedAuctionInputBytes(
                        buyerInputs,
                        CALLER_PACKAGE_NAME,
                        adSelectionId,
                        auctionServerDebugConfiguration);

        ConsentedDebugConfiguration actual = result.getConsentedDebugConfig();
        Truth.assertThat(actual).isEqualTo(expectedConsentedDebugConfiguration);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_EXCEEDED_ALLOWED_TIME_LIMIT)
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__GET_AD_SELECTION_DATA_RUNNER_NOTIFY_FAILURE_TIMEOUT)
    public void testRunner_getAdSelectionData_timeoutFailure() throws Exception {
        mocker.mockGetFlags(mLegacyFakeFlags);

        Flags shortTimeoutFlags =
                new GetAdSelectionDataRunnerTestFlags() {
                    @Override
                    public long getFledgeAuctionServerOverallTimeoutMs() {
                        return 200L;
                    }
                };

        when(mObliviousHttpEncryptorMock.encryptBytes(any(), anyLong(), anyLong(), any(), any()))
                .thenAnswer(
                        new AnswersWithDelay(
                                2 * shortTimeoutFlags.getFledgeAuctionServerOverallTimeoutMs(),
                                new Returns(
                                        FluentFuture.from(immediateFuture(CIPHER_TEXT_BYTES)))));

        mockGetAdSelectionDataRunnerWithFledgeAuctionServerExecutionLogger(
                mMultiCloudSupportStrategyFlagOff);
        GetAdSelectionDataRunner getAdSelectionDataRunner =
                new GetAdSelectionDataRunner(
                        mContext,
                        E2E_TRACE_COOKIE,
                        MultiCloudTestStrategyFactory.getDisabledTestStrategy(
                                mObliviousHttpEncryptorMock),
                        mAdSelectionEntryDaoSpy,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mAdSelectionServiceFilterMock,
                        mFrequencyCapAdFiltererSpy,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        BLOCKING_EXECUTOR,
                        mScheduledExecutor,
                        shortTimeoutFlags,
                        mFakeDebugFlags,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mAdsRelevanceExecutionLogger,
                        mAdServicesLoggerSpy,
                        new AuctionServerPayloadMetricsStrategyDisabled(),
                        mAppInstallAdFiltererSpy,
                        mAuctionServerDebugConfigurationGenerator);

        createAndPersistDBCustomAudiencesWithAdRenderId();
        GetAdSelectionDataInput inputParams =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(getAdSelectionDataRunner, inputParams);

        Assert.assertFalse(callback.mIsSuccess);
        assertNotNull(callback.mFledgeErrorResponse);
        assertEquals(STATUS_TIMEOUT, callback.mFledgeErrorResponse.getStatusCode());

        verifyGetAdSelectionDataApiUsageLog(STATUS_TIMEOUT);
    }

    private Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData>
            createTestBuyerInputs() {
        byte[] buyer1data = new byte[] {2, 3};
        byte[] buyer2data = new byte[] {1};
        return ImmutableMap.of(
                BUYER_1,
                AuctionServerDataCompressor.CompressedData.create(buyer1data),
                BUYER_2,
                AuctionServerDataCompressor.CompressedData.create(buyer2data));
    }

    private GetAdSelectionDataRunner initRunner(
            Flags flags, AdsRelevanceExecutionLogger adsRelevanceExecutionLogger) {
        return initRunner(
                flags,
                adsRelevanceExecutionLogger,
                mMultiCloudSupportStrategyFlagOff,
                new AuctionServerPayloadMetricsStrategyDisabled());
    }

    private GetAdSelectionDataRunner initRunner(
            Flags flags,
            AdsRelevanceExecutionLogger adsRelevanceExecutionLogger,
            MultiCloudSupportStrategy multiCloudSupportStrategy,
            AuctionServerPayloadMetricsStrategy auctionServerPayloadMetricsStrategy) {
        return new GetAdSelectionDataRunner(
                mContext,
                E2E_TRACE_COOKIE,
                multiCloudSupportStrategy,
                mAdSelectionEntryDaoSpy,
                mCustomAudienceDao,
                mEncodedPayloadDao,
                mAdSelectionServiceFilterMock,
                mFrequencyCapAdFiltererSpy,
                mBackgroundExecutorService,
                mLightweightExecutorService,
                BLOCKING_EXECUTOR,
                mScheduledExecutor,
                flags,
                mFakeDebugFlags,
                CALLER_UID,
                DevContext.createForDevOptionsDisabled(),
                mClockMock,
                adsRelevanceExecutionLogger,
                mAdServicesLoggerSpy,
                auctionServerPayloadMetricsStrategy,
                mAppInstallAdFiltererSpy,
                mAuctionServerDebugConfigurationGenerator);
    }

    private void createAndPersistDBCustomAudiencesWithAdRenderId() {
        Map<String, AdTechIdentifier> nameAndBuyers =
                Map.of(
                        "Shoes CA of Buyer 1", BUYER_1,
                        "Shirts CA of Buyer 1", BUYER_1,
                        "Shoes CA Of Buyer 2", BUYER_2);

        for (Map.Entry<String, AdTechIdentifier> entry : nameAndBuyers.entrySet()) {
            AdTechIdentifier buyer = entry.getValue();
            String name = entry.getKey();
            DBCustomAudience thisCustomAudience =
                    DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(buyer, name)
                            .build();
            mCustomAudienceDao.insertOrOverwriteCustomAudience(
                    thisCustomAudience, Uri.EMPTY, false);
        }
    }

    private GetAdSelectionDataTestCallback invokeGetAdSelectionData(
            GetAdSelectionDataRunner runner, GetAdSelectionDataInput inputParams)
            throws InterruptedException {

        CountDownLatch countdownLatch = new CountDownLatch(1);
        GetAdSelectionDataTestCallback callback =
                new GetAdSelectionDataTestCallback(countdownLatch);

        runner.run(inputParams, callback);
        callback.mCountDownLatch.await();
        return callback;
    }

    private void mockGetAdSelectionDataRunnerWithFledgeAuctionServerExecutionLogger(
            MultiCloudSupportStrategy multiCloudSupportStrategy) {
        when(mFledgeAuctionServerExecutionLoggerClockMock.elapsedRealtime())
                .thenReturn(
                        BINDER_ELAPSED_TIMESTAMP,
                        GET_AD_SELECTION_DATA_START_TIMESTAMP,
                        GET_AD_SELECTION_DATA_END_TIMESTAMP);
        mAdsRelevanceExecutionLogger =
                mAdsRelevanceExecutionLoggerFactory.getAdsRelevanceExecutionLogger();
        mGetAdSelectionDataRunner =
                initRunner(
                        mLegacyFakeFlags,
                        mAdsRelevanceExecutionLogger,
                        multiCloudSupportStrategy,
                        new AuctionServerPayloadMetricsStrategyDisabled());
    }

    private void verifyGetAdSelectionDataApiUsageLog(int resultCode) throws InterruptedException {
        ApiCallStats apiCallStats = mLogApiCallStatsCallback.assertResultReceived();
        assertThat(apiCallStats.getApiName()).isEqualTo(
                AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA);
        assertThat(apiCallStats.getAppPackageName()).isEqualTo(TEST_PACKAGE_NAME);
        assertThat(apiCallStats.getResultCode()).isEqualTo(resultCode);
        assertThat(apiCallStats.getLatencyMillisecond()).isEqualTo(
                GET_AD_SELECTION_DATA_OVERALL_LATENCY_MS);
    }

    public static class GetAdSelectionDataRunnerTestFlags implements Flags {
        @Override
        public long getFledgeAuctionServerOverallTimeoutMs() {
            return FLEDGE_AUCTION_SERVER_OVERALL_TIMEOUT_MS;
        }

        @Override
        public long getFledgeCustomAudienceActiveTimeWindowInMs() {
            return FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS;
        }

        @Override
        public boolean getDisableFledgeEnrollmentCheck() {
            return true;
        }

        @Override
        public boolean getFledgeAuctionServerApiUsageMetricsEnabled() {
            return FLEDGE_AUCTION_SERVER_API_USAGE_METRICS_ENABLED_IN_TEST;
        }

        @Override
        public boolean getFledgeAuctionServerKeyFetchMetricsEnabled() {
            return FLEDGE_AUCTION_SERVER_KEY_FETCH_METRICS_ENABLED_IN_TEST;
        }
    }

    static class GetAdSelectionDataRunnerTestFlagsWithExcessiveSizeFormatter
            extends GetAdSelectionDataRunnerTestFlags {
        @Override
        public int getFledgeAuctionServerPayloadFormatVersion() {
            return AuctionServerPayloadFormatterExcessiveMaxSize.VERSION;
        }
    }

    static class GetAdSelectionDataRunnerTestFlagsWithMultiCloudEnabled
            extends GetAdSelectionDataRunnerTestFlags {

        private final boolean mMultiCloudEnabled;

        GetAdSelectionDataRunnerTestFlagsWithMultiCloudEnabled(boolean multiCloudEnabled) {
            this.mMultiCloudEnabled = multiCloudEnabled;
        }

        @Override
        public boolean getFledgeAuctionServerMultiCloudEnabled() {
            return mMultiCloudEnabled;
        }

        @Override
        public String getFledgeAuctionServerCoordinatorUrlAllowlist() {
            return "https://example.com";
        }
    }

    static class GetAdSelectionDataRunnerTestFlagsWithSellerConfigurationEnabled
            extends GetAdSelectionDataRunnerTestFlags {

        private final int mCompressedBuyerInputCreatorVersion;

        GetAdSelectionDataRunnerTestFlagsWithSellerConfigurationEnabled(
                int compressedBuyerInputCreatorVersion) {
            this.mCompressedBuyerInputCreatorVersion = compressedBuyerInputCreatorVersion;
        }

        @Override
        public boolean getFledgeGetAdSelectionDataSellerConfigurationEnabled() {
            return true;
        }

        @Override
        public int getFledgeGetAdSelectionDataBuyerInputCreatorVersion() {
            return mCompressedBuyerInputCreatorVersion;
        }
    }

    static class GetAdSelectionDataTestCallback extends GetAdSelectionDataCallback.Stub {
        final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        GetAdSelectionDataResponse mGetAdSelectionDataResponse;
        FledgeErrorResponse mFledgeErrorResponse;

        GetAdSelectionDataTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
            mGetAdSelectionDataResponse = null;
            mFledgeErrorResponse = null;
        }

        @Override
        public void onSuccess(GetAdSelectionDataResponse getAdSelectionDataResponse)
                throws RemoteException {
            mIsSuccess = true;
            mGetAdSelectionDataResponse = getAdSelectionDataResponse;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mIsSuccess = false;
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }
}
