/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.adservices.customaudience.CustomAudience.FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS;

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_COORDINATOR_SOURCE_API;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;

import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.BuyerInputGeneratorIntermediateStats;
import com.android.adservices.service.stats.GetAdSelectionDataApiCalledStats;
import com.android.adservices.service.stats.GetAdSelectionDataBuyerInputGeneratedStats;

import com.google.common.base.Strings;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AuctionServerPayloadMetricsStrategyEnabledTest {
    @Mock private GetAdSelectionDataApiCalledStats.Builder mBuilderMock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;
    @Mock private SellerConfigurationMetricsStrategy mSellerConfigurationMetricsStrategyMock;
    private AuctionServerPayloadMetricsStrategy mAuctionServerPayloadMetricsStrategy;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mAuctionServerPayloadMetricsStrategy =
                new AuctionServerPayloadMetricsStrategyEnabled(
                        mAdServicesLoggerMock, mSellerConfigurationMetricsStrategyMock);
    }

    @Test
    public void testSetNumBuyersSetsNumBuyers() {
        mAuctionServerPayloadMetricsStrategy.setNumBuyers(mBuilderMock, 2);
        verify(mBuilderMock).setNumBuyers(2);
    }

    @Test
    public void testSetSellerConfigurationMetrics() {
        GetAdSelectionDataApiCalledStats.PayloadOptimizationResult payloadOptimizationResult =
                GetAdSelectionDataApiCalledStats.PayloadOptimizationResult
                        .PAYLOAD_WITHIN_REQUESTED_MAX;
        int inputGenerationLatencyMs = 3;
        int compressedBuyerInputCreatorVersion = 1;
        int numReEstimations = 2;
        mAuctionServerPayloadMetricsStrategy.setSellerConfigurationMetrics(
                mBuilderMock,
                payloadOptimizationResult,
                inputGenerationLatencyMs,
                compressedBuyerInputCreatorVersion,
                numReEstimations);
        verify(mSellerConfigurationMetricsStrategyMock)
                .setSellerConfigurationMetrics(
                        mBuilderMock,
                        payloadOptimizationResult,
                        inputGenerationLatencyMs,
                        compressedBuyerInputCreatorVersion,
                        numReEstimations);
    }

    @Test
    public void testSetSellerMaxPayloadSizeKBMetrics() {
        int sellerMaxPayloadSize = 5;
        mAuctionServerPayloadMetricsStrategy.setSellerMaxPayloadSizeKb(
                mBuilderMock, sellerMaxPayloadSize);
        verify(mSellerConfigurationMetricsStrategyMock)
                .setSellerMaxPayloadSizeKb(mBuilderMock, sellerMaxPayloadSize);
    }

    @Test
    public void testSetInputGenerationLatencyMsMetricsAndCompressedBuyerInputCreatorVersion() {
        int inputGenerationLatencyMs = 3;
        int intBuyerCreatorVersion = 1;
        mAuctionServerPayloadMetricsStrategy.setInputGenerationLatencyMsAndBuyerCreatorVersion(
                mBuilderMock, inputGenerationLatencyMs, intBuyerCreatorVersion);
        verify(mSellerConfigurationMetricsStrategyMock)
                .setInputGenerationLatencyMsAndBuyerCreatorVersion(
                        mBuilderMock, inputGenerationLatencyMs, intBuyerCreatorVersion);
    }

    @Test
    public void testSetServerAuctionCoordinatorSourceDoesNothing() {
        mAuctionServerPayloadMetricsStrategy.setServerAuctionCoordinatorSource(
                mBuilderMock, SERVER_AUCTION_COORDINATOR_SOURCE_API);
        verifyZeroInteractions(mBuilderMock);
    }

    @Test
    public void testLogGetAdSelectionDataApiCalledStatsDoesLog() {
        int payloadSize = 2000;
        when(mBuilderMock.setStatusCode(anyInt())).thenReturn(mBuilderMock);
        when(mBuilderMock.setPayloadSizeKb(anyInt())).thenReturn(mBuilderMock);
        mAuctionServerPayloadMetricsStrategy.logGetAdSelectionDataApiCalledStats(
                mBuilderMock, payloadSize, AdServicesStatusUtils.STATUS_SUCCESS);
        verify(mBuilderMock).setStatusCode(AdServicesStatusUtils.STATUS_SUCCESS);
        verify(mBuilderMock).setPayloadSizeKb(payloadSize);

        verify(mAdServicesLoggerMock).logGetAdSelectionDataApiCalledStats(any());
    }

    @Test
    public void testLogGetAdSelectionDataBuyerInputGeneratedStatsDoesLog() {
        Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> buyerStats = new HashMap<>();
        BuyerInputGeneratorIntermediateStats stats1 = new BuyerInputGeneratorIntermediateStats();
        stats1.incrementNumCustomAudiences();
        BuyerInputGeneratorIntermediateStats stats2 = new BuyerInputGeneratorIntermediateStats();
        stats2.incrementNumCustomAudiences();
        buyerStats.put(AdTechIdentifier.fromString("hello"), stats1);
        buyerStats.put(AdTechIdentifier.fromString("hello2"), stats2);
        mAuctionServerPayloadMetricsStrategy.logGetAdSelectionDataBuyerInputGeneratedStats(
                buyerStats);
        verify(mAdServicesLoggerMock, times(2))
                .logGetAdSelectionDataBuyerInputGeneratedStats(any());
    }

    @Test
    public void testLogGetAdSelectionDataBuyerInputGeneratedStatsWithPasMetricsDoesLog() {
        ArgumentCaptor<GetAdSelectionDataBuyerInputGeneratedStats> argumentCaptor =
                ArgumentCaptor.forClass(GetAdSelectionDataBuyerInputGeneratedStats.class);
        Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> buyerStats = new HashMap<>();
        BuyerInputGeneratorIntermediateStats stats1 = new BuyerInputGeneratorIntermediateStats();
        stats1.incrementNumCustomAudiences();
        BuyerInputGeneratorIntermediateStats stats2 = new BuyerInputGeneratorIntermediateStats();
        stats2.incrementNumCustomAudiences();
        buyerStats.put(AdTechIdentifier.fromString("hello"), stats1);
        buyerStats.put(AdTechIdentifier.fromString("hello2"), stats2);

        int encodedSignalsCount = 5;
        int encodedSignalsTotalSizeInBytes = 11;
        int encodedSignalsMaxSizeInBytes = 3;
        int encodedSignalsMinSizeInBytes = 2;

        mAuctionServerPayloadMetricsStrategy
                .logGetAdSelectionDataBuyerInputGeneratedStatsWithExtendedPasMetrics(
                        buyerStats,
                        encodedSignalsCount,
                        encodedSignalsTotalSizeInBytes,
                        encodedSignalsMaxSizeInBytes,
                        encodedSignalsMinSizeInBytes);
        verify(mAdServicesLoggerMock, times(2))
                .logGetAdSelectionDataBuyerInputGeneratedStats(argumentCaptor.capture());

        GetAdSelectionDataBuyerInputGeneratedStats stats = argumentCaptor.getAllValues().get(0);
        assertThat(stats.getNumEncodedSignals()).isEqualTo(encodedSignalsCount);
        assertThat(stats.getEncodedSignalsSizeMean())
                .isEqualTo(encodedSignalsTotalSizeInBytes / encodedSignalsCount);
        assertThat(stats.getEncodedSignalsSizeMax()).isEqualTo(encodedSignalsMaxSizeInBytes);
        assertThat(stats.getEncodedSignalsSizeMin()).isEqualTo(encodedSignalsMinSizeInBytes);
    }

    @Test
    public void
            testLogGetAdSelectionDataBuyerInputGeneratedStatsWithPasMetricsDoesLog_emptyStats() {
        ArgumentCaptor<GetAdSelectionDataBuyerInputGeneratedStats> argumentCaptor =
                ArgumentCaptor.forClass(GetAdSelectionDataBuyerInputGeneratedStats.class);
        Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> buyerStats = new HashMap<>();

        int encodedSignalsCount = 5;
        int encodedSignalsTotalSizeInBytes = 11;
        int encodedSignalsMaxSizeInBytes = 3;
        int encodedSignalsMinSizeInBytes = 2;

        mAuctionServerPayloadMetricsStrategy
                .logGetAdSelectionDataBuyerInputGeneratedStatsWithExtendedPasMetrics(
                        buyerStats,
                        encodedSignalsCount,
                        encodedSignalsTotalSizeInBytes,
                        encodedSignalsMaxSizeInBytes,
                        encodedSignalsMinSizeInBytes);
        verify(mAdServicesLoggerMock, times(1))
                .logGetAdSelectionDataBuyerInputGeneratedStats(argumentCaptor.capture());

        GetAdSelectionDataBuyerInputGeneratedStats stats = argumentCaptor.getValue();
        assertThat(stats.getNumEncodedSignals()).isEqualTo(encodedSignalsCount);
        assertThat(stats.getEncodedSignalsSizeMean())
                .isEqualTo(encodedSignalsTotalSizeInBytes / encodedSignalsCount);
        assertThat(stats.getEncodedSignalsSizeMax()).isEqualTo(encodedSignalsMaxSizeInBytes);
        assertThat(stats.getEncodedSignalsSizeMin()).isEqualTo(encodedSignalsMinSizeInBytes);
    }

    @Test
    public void testAddToBuyerIntermediateStatsDoesAdd() {
        AdTechIdentifier buyer = AdTechIdentifier.fromString("buyer");
        Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> perBuyerStats = new HashMap<>();
        DBCustomAudience dbCustomAudience =
                DBCustomAudienceFixture.getValidBuilderByBuyerWithOmitAdsEnabled(buyer).build();
        BiddingAuctionServers.BuyerInput.CustomAudience customAudience =
                buildCustomAudienceProtoFrom(dbCustomAudience);

        mAuctionServerPayloadMetricsStrategy.addToBuyerIntermediateStats(
                perBuyerStats, dbCustomAudience, customAudience);

        assertThat(perBuyerStats).containsKey(buyer);
        BuyerInputGeneratorIntermediateStats stats = perBuyerStats.get(buyer);
        assertThat(stats.getNumCustomAudiences()).isEqualTo(1);
        assertThat(stats.getNumCustomAudiencesOmitAds()).isEqualTo(1);
    }

    private BiddingAuctionServers.BuyerInput.CustomAudience buildCustomAudienceProtoFrom(
            DBCustomAudience customAudience) {
        BiddingAuctionServers.BuyerInput.CustomAudience.Builder customAudienceBuilder =
                BiddingAuctionServers.BuyerInput.CustomAudience.newBuilder();

        customAudienceBuilder.setName(customAudience.getName()).setOwner(customAudience.getOwner());

        if ((customAudience.getAuctionServerRequestFlags() & FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
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
