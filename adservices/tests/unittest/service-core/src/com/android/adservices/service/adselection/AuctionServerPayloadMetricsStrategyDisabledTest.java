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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_COORDINATOR_SOURCE_API;

import static org.mockito.Mockito.verifyZeroInteractions;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;

import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers;
import com.android.adservices.service.stats.BuyerInputGeneratorIntermediateStats;
import com.android.adservices.service.stats.GetAdSelectionDataApiCalledStats;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

public class AuctionServerPayloadMetricsStrategyDisabledTest {
    @Mock private GetAdSelectionDataApiCalledStats.Builder mBuilder;
    private final AuctionServerPayloadMetricsStrategy mAuctionServerPayloadMetricsStrategy =
            new AuctionServerPayloadMetricsStrategyDisabled();

    @Mock private Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> mPerBuyerStatsMock;
    @Mock private DBCustomAudience mDBCustomAudienceMock;
    @Mock private BiddingAuctionServers.BuyerInput.CustomAudience mCustomAudienceMock;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSetNumBuyerDoesNothing() {
        mAuctionServerPayloadMetricsStrategy.setNumBuyers(mBuilder, 2);
        verifyZeroInteractions(mBuilder);
    }

    @Test
    public void testSellerConfigurationMetricsDoesNothing() {
        mAuctionServerPayloadMetricsStrategy.setSellerConfigurationMetrics(
                mBuilder,
                GetAdSelectionDataApiCalledStats.PayloadOptimizationResult
                        .PAYLOAD_WITHIN_REQUESTED_MAX,
                /* inputGenerationLatencyMs= */ 3,
                /* compressedBuyerInputCreatorVersion= */ 1,
                /* numReEstimations= */ 2);
        verifyZeroInteractions(mBuilder);
    }

    @Test
    public void testSetSellerMaxSizeDoesNothing() {
        mAuctionServerPayloadMetricsStrategy.setSellerMaxPayloadSizeKb(
                mBuilder, /* sellerMaxSize= */ 5);
        verifyZeroInteractions(mBuilder);
    }

    @Test
    public void
            testSetInputGenerationLatencyMsMetricsAndCompressedBuyerInputCreatorVersionDoesNothing() {
        mAuctionServerPayloadMetricsStrategy.setInputGenerationLatencyMsAndBuyerCreatorVersion(
                mBuilder,
                /* inputGenerationLatencyMs= */ 5,
                CompressedBuyerInputCreatorNoOptimizations.VERSION);
        verifyZeroInteractions(mBuilder);
    }

    @Test
    public void testSetServerAuctionCoordinatorSourceDoesNothing() {
        mAuctionServerPayloadMetricsStrategy.setServerAuctionCoordinatorSource(
                mBuilder, SERVER_AUCTION_COORDINATOR_SOURCE_API);
        verifyZeroInteractions(mBuilder);
    }

    @Test
    public void testLogGetAdSelectionDataApiCalledStatsDoesNothing() {
        mAuctionServerPayloadMetricsStrategy.logGetAdSelectionDataApiCalledStats(
                mBuilder, 2000, AdServicesStatusUtils.STATUS_SUCCESS);
        verifyZeroInteractions(mBuilder);
    }

    @Test
    public void testLogGetAdSelectionDataBuyerInputGeneratedStatsDoesNothing() {
        mAuctionServerPayloadMetricsStrategy.logGetAdSelectionDataBuyerInputGeneratedStats(
                mPerBuyerStatsMock);
        verifyZeroInteractions(mPerBuyerStatsMock);
    }

    @Test
    public void testAddToBuyerIntermediateStatsDoesNothing() {
        mAuctionServerPayloadMetricsStrategy.addToBuyerIntermediateStats(
                mPerBuyerStatsMock, mDBCustomAudienceMock, mCustomAudienceMock);
        verifyZeroInteractions(mPerBuyerStatsMock, mDBCustomAudienceMock, mCustomAudienceMock);
    }

    @Test
    public void
            testLogGetAdSelectionDataBuyerInputGeneratedStatsWithExtendedPasMetricsDoesNothing() {
        mAuctionServerPayloadMetricsStrategy
                .logGetAdSelectionDataBuyerInputGeneratedStatsWithExtendedPasMetrics(
                        mPerBuyerStatsMock,
                        /* encodedSignalsCount */ 0,
                        /* encodedSignalsTotalSizeInBytes */ 0,
                        /* encodedSignalsMaxSizeInBytes */ 0,
                        /* encodedSignalsMinSizeInBytes */ 0);
        verifyZeroInteractions(mPerBuyerStatsMock);
    }
}
