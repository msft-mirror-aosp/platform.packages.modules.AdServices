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

import static android.adservices.adselection.AdSelectionConfigFixture.BUYER_3;
import static android.adservices.adselection.SellerConfigurationFixture.PER_BUYER_CONFIGURATION_1;
import static android.adservices.adselection.SellerConfigurationFixture.SELLER_CONFIGURATION;

import static com.android.adservices.service.Flags.FLEDGE_AUCTION_SERVER_COMPRESSION_ALGORITHM_VERSION;
import static com.android.adservices.service.Flags.FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS;
import static com.android.adservices.service.Flags.PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES;
import static com.android.adservices.service.stats.AdServicesLoggerUtil.FIELD_UNSET;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudience;
import android.net.Uri;

import androidx.room.Room;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.data.signals.DBEncodedPayloadFixture;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.FakeFlagsFactory;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.BuyerInput;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.ProtectedAppSignals;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.GetAdSelectionDataApiCalledStats;
import com.android.adservices.service.stats.GetAdSelectionDataBuyerInputGeneratedStats;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@SpyStatic(FlagsFactory.class)
@MockStatic(PackageManagerCompatUtils.class)
public final class BuyerInputGeneratorTest extends AdServicesExtendedMockitoTestCase {
    private static final boolean ENABLE_AD_FILTER = true;
    private static final boolean ENABLE_PERIODIC_SIGNALS = true;
    private static final long API_RESPONSE_TIMEOUT_SECONDS = 10_000L;
    private static final AdTechIdentifier BUYER_1 = AdSelectionConfigFixture.BUYER_1;
    private static final AdTechIdentifier BUYER_2 = AdSelectionConfigFixture.BUYER_2;
    private ExecutorService mLightweightExecutorService;
    private ExecutorService mBackgroundExecutorService;
    private CustomAudienceDao mCustomAudienceDao;
    private EncodedPayloadDao mEncodedPayloadDao;
    @Mock private FrequencyCapAdFilterer mFrequencyCapAdFiltererMock;
    @Mock private AppInstallAdFilterer mAppInstallAdFiltererMock;
    private BuyerInputGenerator mBuyerInputGenerator;
    private AuctionServerDataCompressor mDataCompressor;
    @Mock private AdServicesLogger mAdServicesLoggerMock;
    private final AuctionServerPayloadMetricsStrategy mAuctionServerPayloadMetricsStrategyDisabled =
            new AuctionServerPayloadMetricsStrategyDisabled();
    @Mock private CompressedBuyerInputCreatorFactory mCompressedBuyerInputCreatorFactoryMock;

    private static final PayloadOptimizationContext PAYLOAD_OPTIMIZATION_CONTEXT_DISABLED =
            PayloadOptimizationContext.builder().build();

    @Mock GetAdSelectionDataApiCalledStats.Builder mStatsBuilderMock;

    @Before
    public void setUp() throws Exception {
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(mContext, CustomAudienceDatabase.class)
                        .addTypeConverter(new DBCustomAudience.Converters(true, true, true))
                        .build()
                        .customAudienceDao();
        mEncodedPayloadDao =
                Room.inMemoryDatabaseBuilder(mContext, ProtectedSignalsDatabase.class)
                        .build()
                        .getEncodedPayloadDao();
        mDataCompressor =
                AuctionServerDataCompressorFactory.getDataCompressor(
                        FLEDGE_AUCTION_SERVER_COMPRESSION_ALGORITHM_VERSION);
        setupCompressedBuyerInputCreatorHelper(
                /* auctionServerMetricsEnabled= */ false,
                /* pasExtendedMetricsEnabled= */ false,
                /* omitAdsEnabled= */ false);

        mBuyerInputGenerator =
                new BuyerInputGenerator(
                        mFrequencyCapAdFiltererMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS,
                        ENABLE_AD_FILTER,
                        ENABLE_PERIODIC_SIGNALS,
                        mAppInstallAdFiltererMock,
                        mCompressedBuyerInputCreatorFactoryMock);

        // Required by CustomAudienceDao.
        doReturn(FakeFlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);
    }

    @Test
    public void testBuyerInputGenerator_returnsBuyerInputs_onlyCAsWithRenderIdReturned_success()
            throws ExecutionException, InterruptedException, TimeoutException,
                    InvalidProtocolBufferException {
        // Set AdFiltering to return all custom audiences in the input argument.
        when(mFrequencyCapAdFiltererMock.filterCustomAudiences(any()))
                .thenAnswer(i -> i.getArguments()[0]);
        when(mAppInstallAdFiltererMock.filterCustomAudiences(any()))
                .thenAnswer(i -> i.getArguments()[0]);

        Map<String, AdTechIdentifier> nameAndBuyersMap =
                Map.of(
                        "Shoes CA of Buyer 1", BUYER_1,
                        "Shirts CA of Buyer 1", BUYER_1,
                        "Shoes CA Of Buyer 2", BUYER_2);
        Set<AdTechIdentifier> buyers = new HashSet<>(nameAndBuyersMap.values());
        Map<String, DBCustomAudience> namesAndCustomAudiences =
                createAndPersistDBCustomAudiencesWithAdRenderId(nameAndBuyersMap);
        // Insert a CA without ad render id. This should get filtered out.
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyer(BUYER_3).build(), Uri.EMPTY, false);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> buyerAndBuyerInputs =
                mBuyerInputGenerator
                        .createCompressedBuyerInputs(
                                PAYLOAD_OPTIMIZATION_CONTEXT_DISABLED, mStatsBuilderMock)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.MILLISECONDS);

        Assert.assertEquals(buyers, buyerAndBuyerInputs.keySet());
        // CustomAudience of BUYER_3 did not contain ad render id and so, should be filtered out.
        assertFalse(buyerAndBuyerInputs.containsKey(BUYER_3));

        for (AdTechIdentifier buyer : buyerAndBuyerInputs.keySet()) {
            BuyerInput buyerInput =
                    BuyerInput.parseFrom(
                            mDataCompressor.decompress(buyerAndBuyerInputs.get(buyer)).getData());
            for (BuyerInput.CustomAudience buyerInputsCA : buyerInput.getCustomAudiencesList()) {
                String buyerInputsCAName = buyerInputsCA.getName();
                assertTrue(namesAndCustomAudiences.containsKey(buyerInputsCAName));
                DBCustomAudience deviceCA = namesAndCustomAudiences.get(buyerInputsCAName);
                Assert.assertEquals(deviceCA.getName(), buyerInputsCAName);
                Assert.assertEquals(deviceCA.getBuyer(), buyer);
                assertEqual(buyerInputsCA, deviceCA, true);
            }
        }
        verify(mFrequencyCapAdFiltererMock).filterCustomAudiences(any());
        verify(mAppInstallAdFiltererMock).filterCustomAudiences(any());
    }

    @Test
    public void
            testBuyerInputGenerator_returnsBuyerInputs_onlyBuyersInPayloadOptimizationContext_SellerMaxImpl()
                    throws ExecutionException,
                            InterruptedException,
                            TimeoutException,
                            InvalidProtocolBufferException {
        // Set AdFiltering to return all custom audiences in the input argument.
        when(mFrequencyCapAdFiltererMock.filterCustomAudiences(any()))
                .thenAnswer(i -> i.getArguments()[0]);
        when(mAppInstallAdFiltererMock.filterCustomAudiences(any()))
                .thenAnswer(i -> i.getArguments()[0]);

        Map<String, AdTechIdentifier> nameAndBuyersMap =
                Map.of(
                        "Shoes CA of Buyer 1", BUYER_1,
                        "Shirts CA of Buyer 1", BUYER_1,
                        "Shoes CA Of Buyer 2", BUYER_2,
                        "Shirts CA of Buyer 3", BUYER_3);
        Map<String, DBCustomAudience> namesAndCustomAudiences =
                createAndPersistDBCustomAudiencesWithAdRenderId(nameAndBuyersMap);

        PayloadOptimizationContext payloadOptimizationContext =
                PayloadOptimizationContext.builder()
                        .setMaxBuyerInputSizeBytes(
                                SELLER_CONFIGURATION.getMaximumPayloadSizeBytes())
                        .setPerBuyerConfigurations(SELLER_CONFIGURATION.getPerBuyerConfigurations())
                        .setOptimizationsEnabled(true)
                        .build();

        CompressedBuyerInputCreatorHelper helper =
                new CompressedBuyerInputCreatorHelper(
                        mAuctionServerPayloadMetricsStrategyDisabled, false, false);
        when(mCompressedBuyerInputCreatorFactoryMock.createCompressedBuyerInputCreator(
                        payloadOptimizationContext, mStatsBuilderMock))
                .thenReturn(
                        new CompressedBuyerInputCreatorSellerPayloadMaxImpl(
                                helper,
                                mDataCompressor,
                                5,
                                payloadOptimizationContext.getMaxBuyerInputSizeBytes(),
                                PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES,
                                Clock.systemUTC(),
                                mStatsBuilderMock));

        when(mCompressedBuyerInputCreatorFactoryMock.getBuyerInputDataFetcher())
                .thenReturn(
                        new BuyerInputDataFetcherBuyerAllowListImpl(
                                mCustomAudienceDao, mEncodedPayloadDao));

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> buyerAndBuyerInputs =
                mBuyerInputGenerator
                        .createCompressedBuyerInputs(payloadOptimizationContext, mStatsBuilderMock)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.MILLISECONDS);

        Assert.assertEquals(Set.of(BUYER_1, BUYER_2), buyerAndBuyerInputs.keySet());
        // BUYER 3 is not in the buyer allowlist of seller configurations, so it should not be
        // included in the payload
        assertFalse(buyerAndBuyerInputs.containsKey(BUYER_3));

        for (AdTechIdentifier buyer : buyerAndBuyerInputs.keySet()) {
            BuyerInput buyerInput =
                    BuyerInput.parseFrom(
                            mDataCompressor.decompress(buyerAndBuyerInputs.get(buyer)).getData());
            for (BuyerInput.CustomAudience buyerInputsCA : buyerInput.getCustomAudiencesList()) {
                String buyerInputsCAName = buyerInputsCA.getName();
                assertTrue(namesAndCustomAudiences.containsKey(buyerInputsCAName));
                DBCustomAudience deviceCA = namesAndCustomAudiences.get(buyerInputsCAName);
                Assert.assertEquals(deviceCA.getName(), buyerInputsCAName);
                Assert.assertEquals(deviceCA.getBuyer(), buyer);
                assertEqual(buyerInputsCA, deviceCA, true);
            }
        }
        verify(mFrequencyCapAdFiltererMock).filterCustomAudiences(any());
        verify(mAppInstallAdFiltererMock).filterCustomAudiences(any());
    }

    @Test
    public void
            testBuyerInputGenerator_returnsBuyerInputs_onlyBuyersInPayloadOptimizationContext_PerBuyerLimitsGreedyImpl()
                    throws ExecutionException,
                            InterruptedException,
                            TimeoutException,
                            InvalidProtocolBufferException {
        // Set AdFiltering to return all custom audiences in the input argument.
        when(mFrequencyCapAdFiltererMock.filterCustomAudiences(any()))
                .thenAnswer(i -> i.getArguments()[0]);
        when(mAppInstallAdFiltererMock.filterCustomAudiences(any()))
                .thenAnswer(i -> i.getArguments()[0]);

        Map<String, AdTechIdentifier> nameAndBuyersMap =
                Map.of(
                        "Shoes CA of Buyer 1", BUYER_1,
                        "Shirts CA of Buyer 1", BUYER_1,
                        "Shoes CA Of Buyer 2", BUYER_2,
                        "Shirts CA of Buyer 3", BUYER_3);
        Map<String, DBCustomAudience> namesAndCustomAudiences =
                createAndPersistDBCustomAudiencesWithAdRenderId(nameAndBuyersMap);

        PayloadOptimizationContext payloadOptimizationContext =
                PayloadOptimizationContext.builder()
                        .setMaxBuyerInputSizeBytes(
                                SELLER_CONFIGURATION.getMaximumPayloadSizeBytes())
                        .setPerBuyerConfigurations(ImmutableSet.of(PER_BUYER_CONFIGURATION_1))
                        .setOptimizationsEnabled(true)
                        .build();

        CompressedBuyerInputCreatorHelper helper =
                new CompressedBuyerInputCreatorHelper(
                        mAuctionServerPayloadMetricsStrategyDisabled,
                        /* pasExtendedMetricsEnabled= */ false,
                        /* enableOmitAds= */ false);
        when(mCompressedBuyerInputCreatorFactoryMock.createCompressedBuyerInputCreator(
                        payloadOptimizationContext, mStatsBuilderMock))
                .thenReturn(
                        new CompressedBuyerInputCreatorPerBuyerLimitsGreedyImpl(
                                helper,
                                mDataCompressor,
                                payloadOptimizationContext,
                                PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES));

        when(mCompressedBuyerInputCreatorFactoryMock.getBuyerInputDataFetcher())
                .thenReturn(
                        new BuyerInputDataFetcherBuyerAllowListImpl(
                                mCustomAudienceDao, mEncodedPayloadDao));

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> buyerAndBuyerInputs =
                mBuyerInputGenerator
                        .createCompressedBuyerInputs(payloadOptimizationContext, mStatsBuilderMock)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.MILLISECONDS);

        assertThat(buyerAndBuyerInputs.keySet()).containsExactly(BUYER_1);
        // BUYER 2 and BUYER 3 is not in the buyer allowlist of seller configurations, so it should
        // not be included in the payload
        assertFalse(buyerAndBuyerInputs.containsKey(BUYER_2));
        assertFalse(buyerAndBuyerInputs.containsKey(BUYER_3));

        for (AdTechIdentifier buyer : buyerAndBuyerInputs.keySet()) {
            BuyerInput buyerInput =
                    BuyerInput.parseFrom(
                            mDataCompressor.decompress(buyerAndBuyerInputs.get(buyer)).getData());
            for (BuyerInput.CustomAudience buyerInputsCA : buyerInput.getCustomAudiencesList()) {
                String buyerInputsCAName = buyerInputsCA.getName();
                assertTrue(namesAndCustomAudiences.containsKey(buyerInputsCAName));
                DBCustomAudience deviceCA = namesAndCustomAudiences.get(buyerInputsCAName);
                Assert.assertEquals(deviceCA.getName(), buyerInputsCAName);
                Assert.assertEquals(deviceCA.getBuyer(), buyer);
                assertEqual(buyerInputsCA, deviceCA, true);
            }
        }
        verify(mFrequencyCapAdFiltererMock).filterCustomAudiences(any());
        verify(mAppInstallAdFiltererMock).filterCustomAudiences(any());
    }

    @Test
    public void
            testBuyerInputGenerator_returnsBuyerInputs_onlyCAsWithRenderIdReturned_successPayloadMetricsEnabled()
                    throws ExecutionException, InterruptedException, TimeoutException,
                            InvalidProtocolBufferException {
        ArgumentCaptor<GetAdSelectionDataBuyerInputGeneratedStats> argumentCaptor =
                ArgumentCaptor.forClass(GetAdSelectionDataBuyerInputGeneratedStats.class);
        // Set AdFiltering to return all custom audiences in the input argument.
        when(mFrequencyCapAdFiltererMock.filterCustomAudiences(any()))
                .thenAnswer(i -> i.getArguments()[0]);
        when(mAppInstallAdFiltererMock.filterCustomAudiences(any()))
                .thenAnswer(i -> i.getArguments()[0]);

        Map<String, AdTechIdentifier> nameAndBuyersMap =
                Map.of(
                        "Shoes CA of Buyer 1", BUYER_1,
                        "Shirts CA of Buyer 1", BUYER_1,
                        "Shoes CA Of Buyer 2", BUYER_2);
        Set<AdTechIdentifier> buyers = new HashSet<>(nameAndBuyersMap.values());
        Map<String, DBCustomAudience> namesAndCustomAudiences =
                createAndPersistDBCustomAudiencesWithAdRenderId(nameAndBuyersMap);
        // Insert a CA without ad render id. This should get filtered out.
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyer(BUYER_3).build(), Uri.EMPTY, false);

        setupCompressedBuyerInputCreatorHelper(
                /* auctionServerMetricsEnabled= */ true,
                /* pasExtendedMetricsEnabled= */ true,
                /* omitAdsEnabled= */ false);

        // Re init buyer input generator to enable payloadMetrics
        mBuyerInputGenerator =
                new BuyerInputGenerator(
                        mFrequencyCapAdFiltererMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS,
                        ENABLE_AD_FILTER,
                        ENABLE_PERIODIC_SIGNALS,
                        mAppInstallAdFiltererMock,
                        mCompressedBuyerInputCreatorFactoryMock);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> buyerAndBuyerInputs =
                mBuyerInputGenerator
                        .createCompressedBuyerInputs(
                                PAYLOAD_OPTIMIZATION_CONTEXT_DISABLED, mStatsBuilderMock)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.MILLISECONDS);

        Assert.assertEquals(buyers, buyerAndBuyerInputs.keySet());
        // CustomAudience of BUYER_3 did not contain ad render id and so, should be filtered out.
        assertFalse(buyerAndBuyerInputs.containsKey(BUYER_3));

        for (AdTechIdentifier buyer : buyerAndBuyerInputs.keySet()) {
            BuyerInput buyerInput =
                    BuyerInput.parseFrom(
                            mDataCompressor.decompress(buyerAndBuyerInputs.get(buyer)).getData());
            for (BuyerInput.CustomAudience buyerInputsCA : buyerInput.getCustomAudiencesList()) {
                String buyerInputsCAName = buyerInputsCA.getName();
                assertTrue(namesAndCustomAudiences.containsKey(buyerInputsCAName));
                DBCustomAudience deviceCA = namesAndCustomAudiences.get(buyerInputsCAName);
                Assert.assertEquals(deviceCA.getName(), buyerInputsCAName);
                Assert.assertEquals(deviceCA.getBuyer(), buyer);
                assertEqual(buyerInputsCA, deviceCA, true);
            }
        }
        verify(mFrequencyCapAdFiltererMock).filterCustomAudiences(any());
        verify(mAppInstallAdFiltererMock).filterCustomAudiences(any());

        verify(mAdServicesLoggerMock, times(2))
                .logGetAdSelectionDataBuyerInputGeneratedStats(argumentCaptor.capture());
        List<GetAdSelectionDataBuyerInputGeneratedStats> stats = argumentCaptor.getAllValues();

        GetAdSelectionDataBuyerInputGeneratedStats stats1 = stats.get(0);
        assertThat(stats1.getNumCustomAudiences()).isEqualTo(2);
        assertThat(stats1.getNumCustomAudiencesOmitAds()).isEqualTo(0);

        DBCustomAudience db1 = namesAndCustomAudiences.get("Shoes CA of Buyer 1");
        BuyerInput.CustomAudience db1CA = buildCustomAudienceProtoFrom(db1);

        DBCustomAudience db2 = namesAndCustomAudiences.get("Shirts CA of Buyer 1");
        BuyerInput.CustomAudience db2CA = buildCustomAudienceProtoFrom(db2);

        assertThat(stats1.getCustomAudienceSizeMeanB())
                .isWithin(10F)
                .of(
                        getMean(
                                ImmutableList.of(
                                        db1CA.getSerializedSize(), db2CA.getSerializedSize())));
        assertThat(stats1.getCustomAudienceSizeVarianceB())
                .isWithin(10F)
                .of(
                        getVariance(
                                ImmutableList.of(
                                        db1CA.getSerializedSize(), db2CA.getSerializedSize())));

        assertThat(stats1.getTrustedBiddingSignalsKeysSizeMeanB())
                .isWithin(10F)
                .of(
                        getMean(
                                ImmutableList.of(
                                        db1.getTrustedBiddingData()
                                                .getKeys()
                                                .toString()
                                                .getBytes()
                                                .length,
                                        db2.getTrustedBiddingData()
                                                .getKeys()
                                                .toString()
                                                .getBytes()
                                                .length)));

        assertThat(stats1.getTrustedBiddingSignalsKeysSizeVarianceB())
                .isWithin(10F)
                .of(
                        getVariance(
                                ImmutableList.of(
                                        db1.getTrustedBiddingData()
                                                .getKeys()
                                                .toString()
                                                .getBytes()
                                                .length,
                                        db2.getTrustedBiddingData()
                                                .getKeys()
                                                .toString()
                                                .getBytes()
                                                .length)));

        assertThat(stats1.getUserBiddingSignalsSizeMeanB())
                .isWithin(10F)
                .of(
                        getMean(
                                ImmutableList.of(
                                        db1.getUserBiddingSignals().getSizeInBytes(),
                                        db2.getUserBiddingSignals().getSizeInBytes())));
        assertThat(stats1.getUserBiddingSignalsSizeVarianceB())
                .isWithin(10F)
                .of(
                        getVariance(
                                ImmutableList.of(
                                        db1.getUserBiddingSignals().getSizeInBytes(),
                                        db2.getUserBiddingSignals().getSizeInBytes())));

        // Checking other stats for this object is not necessary since it's only one CA
        GetAdSelectionDataBuyerInputGeneratedStats stats2 = stats.get(1);
        assertThat(stats2.getNumCustomAudiences()).isEqualTo(1);
        assertThat(stats2.getNumCustomAudiencesOmitAds()).isEqualTo(0);

        // No encoded payload involves in this test, the extended PAS metrics should be set as 0.
        assertThat(stats1.getNumEncodedSignals()).isEqualTo(0);
        assertThat(stats1.getEncodedSignalsSizeMean()).isEqualTo(0);
        assertThat(stats1.getEncodedSignalsSizeMin()).isEqualTo(0);
        assertThat(stats1.getEncodedSignalsSizeMax()).isEqualTo(0);

        assertThat(stats2.getNumEncodedSignals()).isEqualTo(0);
        assertThat(stats2.getEncodedSignalsSizeMean()).isEqualTo(0);
        assertThat(stats2.getEncodedSignalsSizeMin()).isEqualTo(0);
        assertThat(stats2.getEncodedSignalsSizeMax()).isEqualTo(0);
    }

    @Test
    public void testBuyerInputGenerator_returnsBuyerInputsWithoutRenderIdForSpecifiedCA()
            throws ExecutionException, InterruptedException, TimeoutException,
                    InvalidProtocolBufferException {
        // Set AdFiltering to return all custom audiences in the input argument.
        when(mFrequencyCapAdFiltererMock.filterCustomAudiences(any()))
                .thenAnswer(i -> i.getArguments()[0]);
        when(mAppInstallAdFiltererMock.filterCustomAudiences(any()))
                .thenAnswer(i -> i.getArguments()[0]);

        Map<String, AdTechIdentifier> nameAndBuyersMap =
                Map.of(
                        "Shoes CA of Buyer 1", BUYER_1,
                        "Shirts CA of Buyer 1", BUYER_1,
                        "Shoes CA Of Buyer 2", BUYER_2);
        Set<AdTechIdentifier> buyers = new HashSet<>(nameAndBuyersMap.values());
        Map<String, DBCustomAudience> namesAndCustomAudiences =
                createAndPersistDBCustomAudiencesWithAdRenderId(nameAndBuyersMap);

        String buyer2ShirtsName = "Shirts CA of Buyer 2";
        // Insert a CA with omit ads enabled
        DBCustomAudience dbCustomAudienceOmitAdsEnabled =
                createAndPersistDBCustomAudienceWithOmitAdsEnabled(buyer2ShirtsName, BUYER_2);
        namesAndCustomAudiences.put(buyer2ShirtsName, dbCustomAudienceOmitAdsEnabled);

        setupCompressedBuyerInputCreatorHelper(
                /* auctionServerMetricsEnabled= */ true,
                /* pasExtendedMetricsEnabled= */ false,
                /* omitAdsEnabled= */ true);

        // Re init buyer input generator to enable omit ads feature
        mBuyerInputGenerator =
                new BuyerInputGenerator(
                        mFrequencyCapAdFiltererMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS,
                        ENABLE_AD_FILTER,
                        ENABLE_PERIODIC_SIGNALS,
                        mAppInstallAdFiltererMock,
                        mCompressedBuyerInputCreatorFactoryMock);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> buyerAndBuyerInputs =
                mBuyerInputGenerator
                        .createCompressedBuyerInputs(
                                PAYLOAD_OPTIMIZATION_CONTEXT_DISABLED, mStatsBuilderMock)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.MILLISECONDS);

        Assert.assertEquals(buyers, buyerAndBuyerInputs.keySet());

        for (AdTechIdentifier buyer : buyerAndBuyerInputs.keySet()) {
            BuyerInput buyerInput =
                    BuyerInput.parseFrom(
                            mDataCompressor.decompress(buyerAndBuyerInputs.get(buyer)).getData());
            for (BuyerInput.CustomAudience buyerInputsCA : buyerInput.getCustomAudiencesList()) {
                String buyerInputsCAName = buyerInputsCA.getName();
                assertTrue(namesAndCustomAudiences.containsKey(buyerInputsCAName));
                DBCustomAudience deviceCA = namesAndCustomAudiences.get(buyerInputsCAName);
                Assert.assertEquals(deviceCA.getName(), buyerInputsCAName);
                Assert.assertEquals(deviceCA.getBuyer(), buyer);
                assertEqual(buyerInputsCA, deviceCA, false);

                // Buyer 2 shirts ca should not have ad render ids list
                if (deviceCA.getBuyer().equals(BUYER_2)
                        && deviceCA.getName().equals(buyer2ShirtsName)) {
                    assertTrue(buyerInputsCA.getAdRenderIdsList().isEmpty());
                } else {
                    // All other CAs should still have ad render ids
                    assertFalse(buyerInputsCA.getAdRenderIdsList().isEmpty());
                    assertAdsEqual(buyerInputsCA, deviceCA);
                }
            }
        }
        verify(mFrequencyCapAdFiltererMock).filterCustomAudiences(any());
        verify(mAppInstallAdFiltererMock).filterCustomAudiences(any());
    }

    @Test
    public void
            testBuyerInputGenerator_returnsBuyerInputsWithoutRenderIdForSpecifiedCAPayloadMetricsEnabled()
                    throws ExecutionException, InterruptedException, TimeoutException,
                            InvalidProtocolBufferException {
        ArgumentCaptor<GetAdSelectionDataBuyerInputGeneratedStats> argumentCaptor =
                ArgumentCaptor.forClass(GetAdSelectionDataBuyerInputGeneratedStats.class);

        // Set AdFiltering to return all custom audiences in the input argument.
        when(mFrequencyCapAdFiltererMock.filterCustomAudiences(any()))
                .thenAnswer(i -> i.getArguments()[0]);
        when(mAppInstallAdFiltererMock.filterCustomAudiences(any()))
                .thenAnswer(i -> i.getArguments()[0]);

        Map<String, AdTechIdentifier> nameAndBuyersMap =
                Map.of(
                        "Shoes CA of Buyer 1", BUYER_1,
                        "Shirts CA of Buyer 1", BUYER_1,
                        "Shoes CA Of Buyer 2", BUYER_2);
        Set<AdTechIdentifier> buyers = new HashSet<>(nameAndBuyersMap.values());
        Map<String, DBCustomAudience> namesAndCustomAudiences =
                createAndPersistDBCustomAudiencesWithAdRenderId(nameAndBuyersMap);

        String buyer2ShirtsName = "Shirts CA of Buyer 2";
        // Insert a CA with omit ads enabled
        DBCustomAudience dbCustomAudienceOmitAdsEnabled =
                createAndPersistDBCustomAudienceWithOmitAdsEnabled(buyer2ShirtsName, BUYER_2);
        namesAndCustomAudiences.put(buyer2ShirtsName, dbCustomAudienceOmitAdsEnabled);

        setupCompressedBuyerInputCreatorHelper(
                /* auctionServerMetricsEnabled= */ true,
                /* pasExtendedMetricsEnabled= */ true,
                /* omitAdsEnabled= */ true);

        // Re init buyer input generator to enable payloadMetrics and omitAds
        mBuyerInputGenerator =
                new BuyerInputGenerator(
                        mFrequencyCapAdFiltererMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS,
                        ENABLE_AD_FILTER,
                        ENABLE_PERIODIC_SIGNALS,
                        mAppInstallAdFiltererMock,
                        mCompressedBuyerInputCreatorFactoryMock);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> buyerAndBuyerInputs =
                mBuyerInputGenerator
                        .createCompressedBuyerInputs(
                                PAYLOAD_OPTIMIZATION_CONTEXT_DISABLED, mStatsBuilderMock)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.MILLISECONDS);

        Assert.assertEquals(buyers, buyerAndBuyerInputs.keySet());

        for (AdTechIdentifier buyer : buyerAndBuyerInputs.keySet()) {
            BuyerInput buyerInput =
                    BuyerInput.parseFrom(
                            mDataCompressor.decompress(buyerAndBuyerInputs.get(buyer)).getData());
            for (BuyerInput.CustomAudience buyerInputsCA : buyerInput.getCustomAudiencesList()) {
                String buyerInputsCAName = buyerInputsCA.getName();
                assertTrue(namesAndCustomAudiences.containsKey(buyerInputsCAName));
                DBCustomAudience deviceCA = namesAndCustomAudiences.get(buyerInputsCAName);
                Assert.assertEquals(deviceCA.getName(), buyerInputsCAName);
                Assert.assertEquals(deviceCA.getBuyer(), buyer);
                assertEqual(buyerInputsCA, deviceCA, false);

                // Buyer 2 shirts ca should not have ad render ids list
                if (deviceCA.getBuyer().equals(BUYER_2)
                        && deviceCA.getName().equals(buyer2ShirtsName)) {
                    assertTrue(buyerInputsCA.getAdRenderIdsList().isEmpty());
                } else {
                    // All other CAs should still have ad render ids
                    assertFalse(buyerInputsCA.getAdRenderIdsList().isEmpty());
                    assertAdsEqual(buyerInputsCA, deviceCA);
                }
            }
        }
        verify(mFrequencyCapAdFiltererMock).filterCustomAudiences(any());
        verify(mAppInstallAdFiltererMock).filterCustomAudiences(any());

        verify(mAdServicesLoggerMock, times(2))
                .logGetAdSelectionDataBuyerInputGeneratedStats(argumentCaptor.capture());
        List<GetAdSelectionDataBuyerInputGeneratedStats> stats = argumentCaptor.getAllValues();

        GetAdSelectionDataBuyerInputGeneratedStats stats1 = stats.get(0);
        assertThat(stats1.getNumCustomAudiences()).isEqualTo(2);
        assertThat(stats1.getNumCustomAudiencesOmitAds()).isEqualTo(0);

        GetAdSelectionDataBuyerInputGeneratedStats stats2 = stats.get(1);
        assertThat(stats2.getNumCustomAudiences()).isEqualTo(2);
        assertThat(stats2.getNumCustomAudiencesOmitAds()).isEqualTo(1);
    }

    @Test
    public void testBuyerInputGenerator_returnsBuyerInputsWithRenderIdIfFlagFalse()
            throws ExecutionException, InterruptedException, TimeoutException,
                    InvalidProtocolBufferException {
        // Set AdFiltering to return all custom audiences in the input argument.
        when(mFrequencyCapAdFiltererMock.filterCustomAudiences(any()))
                .thenAnswer(i -> i.getArguments()[0]);
        when(mAppInstallAdFiltererMock.filterCustomAudiences(any()))
                .thenAnswer(i -> i.getArguments()[0]);

        Map<String, AdTechIdentifier> nameAndBuyersMap =
                Map.of(
                        "Shoes CA of Buyer 1", BUYER_1,
                        "Shirts CA of Buyer 1", BUYER_1,
                        "Shoes CA Of Buyer 2", BUYER_2);
        Set<AdTechIdentifier> buyers = new HashSet<>(nameAndBuyersMap.values());
        Map<String, DBCustomAudience> namesAndCustomAudiences =
                createAndPersistDBCustomAudiencesWithAdRenderId(nameAndBuyersMap);

        String buyer2ShirtsName = "Shirts CA of Buyer 2";
        // Insert a CA with omit ads enabled
        DBCustomAudience dbCustomAudienceOmitAdsEnabled =
                createAndPersistDBCustomAudienceWithOmitAdsEnabled(buyer2ShirtsName, BUYER_2);
        namesAndCustomAudiences.put(buyer2ShirtsName, dbCustomAudienceOmitAdsEnabled);

        // Re init buyer input generator to disable omit ads feature
        mBuyerInputGenerator =
                new BuyerInputGenerator(
                        mFrequencyCapAdFiltererMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS,
                        ENABLE_AD_FILTER,
                        ENABLE_PERIODIC_SIGNALS,
                        mAppInstallAdFiltererMock,
                        mCompressedBuyerInputCreatorFactoryMock);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> buyerAndBuyerInputs =
                mBuyerInputGenerator
                        .createCompressedBuyerInputs(
                                PAYLOAD_OPTIMIZATION_CONTEXT_DISABLED, mStatsBuilderMock)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.MILLISECONDS);

        Assert.assertEquals(buyers, buyerAndBuyerInputs.keySet());

        for (AdTechIdentifier buyer : buyerAndBuyerInputs.keySet()) {
            BuyerInput buyerInput =
                    BuyerInput.parseFrom(
                            mDataCompressor.decompress(buyerAndBuyerInputs.get(buyer)).getData());
            for (BuyerInput.CustomAudience buyerInputsCA : buyerInput.getCustomAudiencesList()) {
                String buyerInputsCAName = buyerInputsCA.getName();
                assertTrue(namesAndCustomAudiences.containsKey(buyerInputsCAName));
                DBCustomAudience deviceCA = namesAndCustomAudiences.get(buyerInputsCAName);
                Assert.assertEquals(deviceCA.getName(), buyerInputsCAName);
                Assert.assertEquals(deviceCA.getBuyer(), buyer);
                // Even though one of the CAs indicated that it wants to omit ads, the flag is false
                // so we expect the ads to stay
                assertEqual(buyerInputsCA, deviceCA, true);
            }
        }
        verify(mFrequencyCapAdFiltererMock).filterCustomAudiences(any());
        verify(mAppInstallAdFiltererMock).filterCustomAudiences(any());
    }

    @Test
    public void testBuyerInputGenerator_returnsBuyerInputs_onlySignals_success()
            throws ExecutionException, InterruptedException, TimeoutException,
                    InvalidProtocolBufferException {
        ArgumentCaptor<GetAdSelectionDataBuyerInputGeneratedStats> argumentCaptor =
                ArgumentCaptor.forClass(GetAdSelectionDataBuyerInputGeneratedStats.class);

        Map<AdTechIdentifier, DBEncodedPayload> encodedPayloads =
                generateAndPersistEncodedPayload(List.of(BUYER_1, BUYER_2));
        Set<AdTechIdentifier> buyers = new HashSet<>(encodedPayloads.keySet());

        setupCompressedBuyerInputCreatorHelper(
                /* auctionServerMetricsEnabled= */ true,
                /* pasExtendedMetricsEnabled= */ true,
                /* omitAdsEnabled= */ false);

        mBuyerInputGenerator =
                new BuyerInputGenerator(
                        mFrequencyCapAdFiltererMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS,
                        ENABLE_AD_FILTER,
                        ENABLE_PERIODIC_SIGNALS,
                        mAppInstallAdFiltererMock,
                        mCompressedBuyerInputCreatorFactoryMock);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> buyerAndBuyerInputs =
                mBuyerInputGenerator
                        .createCompressedBuyerInputs(
                                PAYLOAD_OPTIMIZATION_CONTEXT_DISABLED, mStatsBuilderMock)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.MILLISECONDS);

        Assert.assertEquals(buyers, buyerAndBuyerInputs.keySet());

        for (AdTechIdentifier buyer : buyerAndBuyerInputs.keySet()) {
            BuyerInput buyerInput =
                    BuyerInput.parseFrom(
                            mDataCompressor.decompress(buyerAndBuyerInputs.get(buyer)).getData());
            ProtectedAppSignals appSignals = buyerInput.getProtectedAppSignals();
            assertEquals(encodedPayloads.get(buyer).getVersion(), appSignals.getEncodingVersion());
            assertEquals(
                    ByteString.copyFrom(encodedPayloads.get(buyer).getEncodedPayload()),
                    appSignals.getAppInstallSignals());
        }
        verify(mFrequencyCapAdFiltererMock).filterCustomAudiences(any());
        verify(mAppInstallAdFiltererMock).filterCustomAudiences(any());

        verify(mAdServicesLoggerMock)
                .logGetAdSelectionDataBuyerInputGeneratedStats(argumentCaptor.capture());
        GetAdSelectionDataBuyerInputGeneratedStats stats = argumentCaptor.getValue();

        assertThat(stats.getNumEncodedSignals()).isEqualTo(2);
        assertThat(stats.getEncodedSignalsSizeMean()).isEqualTo(4);
        assertThat(stats.getEncodedSignalsSizeMin()).isEqualTo(4);
        assertThat(stats.getEncodedSignalsSizeMax()).isEqualTo(4);
        assertThat(stats.getNumCustomAudiencesOmitAds()).isEqualTo(FIELD_UNSET);
        assertThat(stats.getNumCustomAudiences()).isEqualTo(FIELD_UNSET);
        assertThat(stats.getTrustedBiddingSignalsKeysSizeVarianceB()).isEqualTo(FIELD_UNSET);
    }

    @Test
    public void testBuyerInputGenerator_returnsBuyerInputs_CAsAndSignalsCombined_success()
            throws ExecutionException, InterruptedException, TimeoutException,
                    InvalidProtocolBufferException {
        ArgumentCaptor<GetAdSelectionDataBuyerInputGeneratedStats> argumentCaptor =
                ArgumentCaptor.forClass(GetAdSelectionDataBuyerInputGeneratedStats.class);

        // Set AdFiltering to return all custom audiences in the input argument.
        when(mFrequencyCapAdFiltererMock.filterCustomAudiences(any()))
                .thenAnswer(i -> i.getArguments()[0]);
        when(mAppInstallAdFiltererMock.filterCustomAudiences(any()))
                .thenAnswer(i -> i.getArguments()[0]);
        // Custom Audiences
        Map<String, AdTechIdentifier> nameAndBuyersMap = Map.of("Shoes CA of Buyer 1", BUYER_1);
        Map<String, DBCustomAudience> namesAndCustomAudiences =
                createAndPersistDBCustomAudiencesWithAdRenderId(nameAndBuyersMap);
        // Insert a CA without ad render id. This should get filtered out.
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyer(BUYER_3).build(), Uri.EMPTY, false);

        // Signals
        Map<AdTechIdentifier, DBEncodedPayload> encodedPayloads =
                generateAndPersistEncodedPayload(List.of(BUYER_1, BUYER_2));
        Set<AdTechIdentifier> buyersWithSignals = new HashSet<>(encodedPayloads.keySet());

        setupCompressedBuyerInputCreatorHelper(
                /* auctionServerMetricsEnabled= */ true,
                /* pasExtendedMetricsEnabled= */ true,
                /* omitAdsEnabled= */ false);

        mBuyerInputGenerator =
                new BuyerInputGenerator(
                        mFrequencyCapAdFiltererMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS,
                        ENABLE_AD_FILTER,
                        ENABLE_PERIODIC_SIGNALS,
                        mAppInstallAdFiltererMock,
                        mCompressedBuyerInputCreatorFactoryMock);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> buyerAndBuyerInputs =
                mBuyerInputGenerator
                        .createCompressedBuyerInputs(
                                PAYLOAD_OPTIMIZATION_CONTEXT_DISABLED, mStatsBuilderMock)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.MILLISECONDS);

        Assert.assertEquals(buyersWithSignals, buyerAndBuyerInputs.keySet());

        for (AdTechIdentifier buyer : buyerAndBuyerInputs.keySet()) {
            BuyerInput buyerInput =
                    BuyerInput.parseFrom(
                            mDataCompressor.decompress(buyerAndBuyerInputs.get(buyer)).getData());
            ProtectedAppSignals appSignals = buyerInput.getProtectedAppSignals();
            assertEquals(encodedPayloads.get(buyer).getVersion(), appSignals.getEncodingVersion());
            assertEquals(
                    ByteString.copyFrom(encodedPayloads.get(buyer).getEncodedPayload()),
                    appSignals.getAppInstallSignals());
        }

        // CustomAudience of BUYER_3 did not contain ad render id and so, should be filtered out.
        assertFalse(buyerAndBuyerInputs.containsKey(BUYER_3));

        BuyerInput buyerInput =
                BuyerInput.parseFrom(
                        mDataCompressor.decompress(buyerAndBuyerInputs.get(BUYER_1)).getData());
        for (BuyerInput.CustomAudience buyerInputsCA : buyerInput.getCustomAudiencesList()) {
            String buyerInputsCAName = buyerInputsCA.getName();
            assertTrue(namesAndCustomAudiences.containsKey(buyerInputsCAName));
            DBCustomAudience deviceCA = namesAndCustomAudiences.get(buyerInputsCAName);
            Assert.assertEquals(deviceCA.getName(), buyerInputsCAName);
            Assert.assertEquals(deviceCA.getBuyer(), BUYER_1);
            assertEqual(buyerInputsCA, deviceCA, true);
        }

        verify(mFrequencyCapAdFiltererMock).filterCustomAudiences(any());
        verify(mAppInstallAdFiltererMock).filterCustomAudiences(any());

        verify(mAdServicesLoggerMock)
                .logGetAdSelectionDataBuyerInputGeneratedStats(argumentCaptor.capture());
        GetAdSelectionDataBuyerInputGeneratedStats stats = argumentCaptor.getValue();

        assertThat(stats.getNumEncodedSignals()).isEqualTo(2);
        assertThat(stats.getEncodedSignalsSizeMean()).isEqualTo(4);
        assertThat(stats.getEncodedSignalsSizeMin()).isEqualTo(4);
        assertThat(stats.getEncodedSignalsSizeMax()).isEqualTo(4);
        assertThat(stats.getNumCustomAudiencesOmitAds()).isEqualTo(0);
        assertThat(stats.getNumCustomAudiences()).isEqualTo(1);
        assertThat(stats.getTrustedBiddingSignalsKeysSizeVarianceB()).isEqualTo(0.0f);
        assertThat(stats.getTrustedBiddingSignalsKeysSizeMeanB()).isEqualTo(22.0f);
    }

    @Test
    public void testBuyerInputGenerator_returnsBuyerInputs_CAsAndSignalsCombined_SignalDisabled()
            throws ExecutionException, InterruptedException, TimeoutException,
                    InvalidProtocolBufferException {
        // Set AdFiltering to return all custom audiences in the input argument.
        when(mFrequencyCapAdFiltererMock.filterCustomAudiences(any()))
                .thenAnswer(i -> i.getArguments()[0]);
        when(mAppInstallAdFiltererMock.filterCustomAudiences(any()))
                .thenAnswer(i -> i.getArguments()[0]);
        // Custom Audiences
        Map<String, AdTechIdentifier> nameAndBuyersMap = Map.of("Shoes CA of Buyer 1", BUYER_1);
        Map<String, DBCustomAudience> namesAndCustomAudiences =
                createAndPersistDBCustomAudiencesWithAdRenderId(nameAndBuyersMap);
        // Insert a CA without ad render id. This should get filtered out.
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyer(BUYER_3).build(), Uri.EMPTY, false);

        BuyerInputGenerator buyerInputGeneratorSignalsDisabled =
                new BuyerInputGenerator(
                        mFrequencyCapAdFiltererMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS,
                        ENABLE_AD_FILTER,
                        false,
                        mAppInstallAdFiltererMock,
                        mCompressedBuyerInputCreatorFactoryMock);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> buyerAndBuyerInputs =
                buyerInputGeneratorSignalsDisabled
                        .createCompressedBuyerInputs(
                                PAYLOAD_OPTIMIZATION_CONTEXT_DISABLED, mStatsBuilderMock)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.MILLISECONDS);

        for (AdTechIdentifier buyer : buyerAndBuyerInputs.keySet()) {
            BuyerInput buyerInput =
                    BuyerInput.parseFrom(
                            mDataCompressor.decompress(buyerAndBuyerInputs.get(buyer)).getData());
            ProtectedAppSignals appSignals = buyerInput.getProtectedAppSignals();
            assertTrue(
                    "Encoded signals should have been empty",
                    appSignals.getAppInstallSignals().isEmpty());
        }

        // CustomAudience of BUYER_3 did not contain ad render id and so, should be filtered out.
        assertFalse(buyerAndBuyerInputs.containsKey(BUYER_3));

        BuyerInput buyerInput =
                BuyerInput.parseFrom(
                        mDataCompressor.decompress(buyerAndBuyerInputs.get(BUYER_1)).getData());
        for (BuyerInput.CustomAudience buyerInputsCA : buyerInput.getCustomAudiencesList()) {
            String buyerInputsCAName = buyerInputsCA.getName();
            assertTrue(namesAndCustomAudiences.containsKey(buyerInputsCAName));
            DBCustomAudience deviceCA = namesAndCustomAudiences.get(buyerInputsCAName);
            Assert.assertEquals(deviceCA.getName(), buyerInputsCAName);
            Assert.assertEquals(deviceCA.getBuyer(), BUYER_1);
            assertEqual(buyerInputsCA, deviceCA, true);
        }

        verify(mFrequencyCapAdFiltererMock).filterCustomAudiences(any());
        verify(mAppInstallAdFiltererMock).filterCustomAudiences(any());
    }

    @Test
    public void testBuyerInputGenerator_disableAdFilter_successWithAdFilteringNotCalled()
            throws ExecutionException, InterruptedException, TimeoutException,
                    InvalidProtocolBufferException {

        mBuyerInputGenerator =
                new BuyerInputGenerator(
                        mFrequencyCapAdFiltererMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS,
                        false,
                        ENABLE_PERIODIC_SIGNALS,
                        mAppInstallAdFiltererMock,
                        mCompressedBuyerInputCreatorFactoryMock);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(BUYER_1, "testCA")
                        .build(),
                Uri.EMPTY,
                false);
        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> buyerAndBuyerInputs =
                mBuyerInputGenerator
                        .createCompressedBuyerInputs(
                                PAYLOAD_OPTIMIZATION_CONTEXT_DISABLED, mStatsBuilderMock)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.MILLISECONDS);

        assertThat(buyerAndBuyerInputs).hasSize(1);
        assertTrue(buyerAndBuyerInputs.containsKey(BUYER_1));
        verify(mFrequencyCapAdFiltererMock, never()).filterCustomAudiences(any());
    }

    @Test
    public void testBuyerInputGenerator_enableAdFilter_successWithFrequencyCapAdFilteringCalled()
            throws ExecutionException, InterruptedException, TimeoutException,
                    InvalidProtocolBufferException {
        // Populate Custom Audiences in the DB
        DBCustomAudience customAudienceBuyer1 =
                DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(BUYER_1, "testCA")
                        .build();
        DBCustomAudience customAudienceBuyer2 =
                DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(BUYER_2, "testCA2")
                        .build();
        mCustomAudienceDao.insertOrOverwriteCustomAudience(customAudienceBuyer1, Uri.EMPTY, false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(customAudienceBuyer2, Uri.EMPTY, false);

        // Set Frequency cap AdFiltering to return only one custom audience.
        when(mFrequencyCapAdFiltererMock.filterCustomAudiences(any()))
                .thenAnswer(
                        i -> {
                            List<DBCustomAudience> cas = (List) i.getArguments()[0];
                            assertThat(cas).hasSize(2);
                            assertThat(cas)
                                    .containsExactly(customAudienceBuyer1, customAudienceBuyer2);
                            return ImmutableList.of(customAudienceBuyer2);
                        });
        when(mAppInstallAdFiltererMock.filterCustomAudiences(any()))
                .thenAnswer(i -> i.getArguments()[0]);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> buyerAndBuyerInputs =
                mBuyerInputGenerator
                        .createCompressedBuyerInputs(
                                PAYLOAD_OPTIMIZATION_CONTEXT_DISABLED, mStatsBuilderMock)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.MILLISECONDS);

        assertThat(buyerAndBuyerInputs).hasSize(1);
        assertTrue(buyerAndBuyerInputs.containsKey(BUYER_2));
        verify(mFrequencyCapAdFiltererMock).filterCustomAudiences(any());
        verify(mAppInstallAdFiltererMock).filterCustomAudiences(any());
    }

    @Test
    public void testBuyerInputGenerator_enableAdFilter_successWithAppInstallCapAdFilteringCalled()
            throws ExecutionException, InterruptedException, TimeoutException,
                    InvalidProtocolBufferException {
        // Populate Custom Audiences in the DB
        DBCustomAudience customAudienceBuyer1 =
                DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(BUYER_1, "testCA")
                        .build();
        DBCustomAudience customAudienceBuyer2 =
                DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(BUYER_2, "testCA2")
                        .build();
        mCustomAudienceDao.insertOrOverwriteCustomAudience(customAudienceBuyer1, Uri.EMPTY, false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(customAudienceBuyer2, Uri.EMPTY, false);

        // Set App install AdFiltering to return only one custom audience.
        when(mAppInstallAdFiltererMock.filterCustomAudiences(any()))
                .thenAnswer(
                        i -> {
                            List<DBCustomAudience> cas = (List) i.getArguments()[0];
                            assertThat(cas).hasSize(2);
                            assertThat(cas)
                                    .containsExactly(customAudienceBuyer1, customAudienceBuyer2);
                            return ImmutableList.of(customAudienceBuyer2);
                        });
        when(mFrequencyCapAdFiltererMock.filterCustomAudiences(any()))
                .thenAnswer(i -> i.getArguments()[0]);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> buyerAndBuyerInputs =
                mBuyerInputGenerator
                        .createCompressedBuyerInputs(
                                PAYLOAD_OPTIMIZATION_CONTEXT_DISABLED, mStatsBuilderMock)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.MILLISECONDS);

        assertThat(buyerAndBuyerInputs).hasSize(1);
        assertTrue(buyerAndBuyerInputs.containsKey(BUYER_2));
        verify(mFrequencyCapAdFiltererMock).filterCustomAudiences(any());
        verify(mAppInstallAdFiltererMock).filterCustomAudiences(any());
    }

    /**
     * Asserts if a {@link BuyerInput.CustomAudience} and {@link DBCustomAudience} objects are
     * equal.
     */
    private void assertEqual(
            BuyerInput.CustomAudience buyerInputCA,
            DBCustomAudience dbCustomAudience,
            boolean compareAds) {
        Assert.assertEquals(buyerInputCA.getName(), dbCustomAudience.getName());
        Assert.assertEquals(buyerInputCA.getOwner(), dbCustomAudience.getOwner());
        Assert.assertNotNull(dbCustomAudience.getTrustedBiddingData());
        Assert.assertEquals(
                buyerInputCA.getBiddingSignalsKeysList(),
                dbCustomAudience.getTrustedBiddingData().getKeys());
        Assert.assertNotNull(dbCustomAudience.getUserBiddingSignals());
        Assert.assertEquals(
                buyerInputCA.getUserBiddingSignals(),
                dbCustomAudience.getUserBiddingSignals().toString());
        Assert.assertNotNull(dbCustomAudience.getAds());
        if (compareAds) {
            Assert.assertEquals(
                    buyerInputCA.getAdRenderIdsList(),
                    dbCustomAudience.getAds().stream()
                            .filter(
                                    ad ->
                                            ad.getAdRenderId() != null
                                                    && !ad.getAdRenderId().isEmpty())
                            .map(ad -> ad.getAdRenderId())
                            .collect(Collectors.toList()));
        }
    }

    private void setupCompressedBuyerInputCreatorHelper(
            boolean auctionServerMetricsEnabled,
            boolean pasExtendedMetricsEnabled,
            boolean omitAdsEnabled) {
        AuctionServerPayloadMetricsStrategy serverPayloadMetricsStrategy =
                auctionServerMetricsEnabled
                        ? new AuctionServerPayloadMetricsStrategyEnabled(
                                mAdServicesLoggerMock,
                                new SellerConfigurationMetricsStrategyDisabled())
                        : mAuctionServerPayloadMetricsStrategyDisabled;
        CompressedBuyerInputCreatorHelper helper =
                new CompressedBuyerInputCreatorHelper(
                        serverPayloadMetricsStrategy, pasExtendedMetricsEnabled, omitAdsEnabled);
        when(mCompressedBuyerInputCreatorFactoryMock.createCompressedBuyerInputCreator(
                        any(), any()))
                .thenReturn(
                        new CompressedBuyerInputCreatorNoOptimizations(
                                helper,
                                mDataCompressor,
                                CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                GetAdSelectionDataApiCalledStats.builder()));
        when(mCompressedBuyerInputCreatorFactoryMock.getBuyerInputDataFetcher())
                .thenReturn(
                        new BuyerInputDataFetcherAllBuyersImpl(
                                mCustomAudienceDao, mEncodedPayloadDao));
    }

    private void assertAdsEqual(
            BuyerInput.CustomAudience buyerInputCA, DBCustomAudience dbCustomAudience) {
        Assert.assertEquals(
                buyerInputCA.getAdRenderIdsList(),
                dbCustomAudience.getAds().stream()
                        .filter(ad -> ad.getAdRenderId() != null && !ad.getAdRenderId().isEmpty())
                        .map(ad -> ad.getAdRenderId())
                        .collect(Collectors.toList()));
    }

    private Map<String, DBCustomAudience> createAndPersistDBCustomAudiencesWithAdRenderId(
            Map<String, AdTechIdentifier> nameAndBuyers) {
        Map<String, DBCustomAudience> customAudiences = new HashMap<>();
        for (Map.Entry<String, AdTechIdentifier> entry : nameAndBuyers.entrySet()) {
            AdTechIdentifier buyer = entry.getValue();
            String name = entry.getKey();
            DBCustomAudience thisCustomAudience =
                    DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(buyer, name)
                            .build();
            customAudiences.put(name, thisCustomAudience);
            mCustomAudienceDao.insertOrOverwriteCustomAudience(
                    thisCustomAudience, Uri.EMPTY, false);
        }
        return customAudiences;
    }

    private DBCustomAudience createAndPersistDBCustomAudienceWithOmitAdsEnabled(
            String name, AdTechIdentifier buyer) {
        DBCustomAudience thisCustomAudience =
                DBCustomAudienceFixture.getValidBuilderByBuyerWithOmitAdsEnabled(buyer, name)
                        .build();
        mCustomAudienceDao.insertOrOverwriteCustomAudience(thisCustomAudience, Uri.EMPTY, false);
        return thisCustomAudience;
    }

    private Map<AdTechIdentifier, DBEncodedPayload> generateAndPersistEncodedPayload(
            List<AdTechIdentifier> buyers) {
        Map<AdTechIdentifier, DBEncodedPayload> map = new HashMap<>();
        for (AdTechIdentifier buyer : buyers) {
            DBEncodedPayload payload =
                    DBEncodedPayloadFixture.anEncodedPayloadBuilder(buyer).build();
            map.put(buyer, payload);
            mEncodedPayloadDao.persistEncodedPayload(payload);
        }
        return map;
    }

    private float getMean(List<Integer> list) {
        if (list.size() == 0) {
            return 0;
        }
        float temp = 0;
        for (Integer n : list) {
            temp += n;
        }
        return temp / list.size();
    }

    private float getVariance(List<Integer> list) {
        if (list.size() == 0) {
            return 0;
        }
        float mean = getMean(list);
        float temp = 0;
        for (Integer n : list) {
            temp += Math.pow(mean - n, 2);
        }
        return temp / list.size();
    }

    private BuyerInput.CustomAudience buildCustomAudienceProtoFrom(
            DBCustomAudience customAudience) {
        BuyerInput.CustomAudience.Builder customAudienceBuilder =
                BuyerInput.CustomAudience.newBuilder();

        customAudienceBuilder
                .setName(customAudience.getName())
                .setOwner(customAudience.getOwner())
                .setUserBiddingSignals(customAudience.getUserBiddingSignals().toString())
                .addAllBiddingSignalsKeys(customAudience.getTrustedBiddingData().getKeys());

        if ((customAudience.getAuctionServerRequestFlags()
                        & CustomAudience.FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                == 0) {
            customAudienceBuilder.addAllAdRenderIds(getAdRenderIds(customAudience));
        }
        return customAudienceBuilder.build();
    }

    private List<String> getAdRenderIds(DBCustomAudience dbCustomAudience) {
        return dbCustomAudience.getAds().stream()
                .filter(ad -> !Strings.isNullOrEmpty(ad.getAdRenderId()))
                .map(ad -> ad.getAdRenderId())
                .collect(Collectors.toList());
    }
}
