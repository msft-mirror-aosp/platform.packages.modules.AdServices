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

import static android.adservices.adselection.AdSelectionConfigFixture.BUYER_1;

import static com.android.adservices.service.adselection.CompressedBuyerInputCreatorHelper.EMPTY_USER_BIDDING_SIGNALS;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.adservices.common.AdTechIdentifier;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.customaudience.DBTrustedBiddingDataFixture;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.data.signals.DBEncodedPayloadFixture;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers;
import com.android.adservices.service.stats.BuyerInputGeneratorIntermediateStats;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;

public class CompressedBuyerInputCreatorHelperTest extends AdServicesExtendedMockitoTestCase {
    private CompressedBuyerInputCreatorHelper mCompressedBuyerInputCreatorHelper;

    @Mock AuctionServerPayloadMetricsStrategy mAuctionServerPayloadMetricsStrategy;

    private final boolean mOmitAdsEnabled = false;
    private final boolean mPasExtendedMetricsEnabled = false;

    @Before
    public void setup() {
        mCompressedBuyerInputCreatorHelper =
                new CompressedBuyerInputCreatorHelper(
                        mAuctionServerPayloadMetricsStrategy,
                        mPasExtendedMetricsEnabled,
                        mOmitAdsEnabled);
    }

    @Test
    public void testBuildCustomAudienceProto() {
        DBCustomAudience dbCustomAudience =
                DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(BUYER_1, "buyer1")
                        .build();

        BiddingAuctionServers.BuyerInput.CustomAudience customAudience =
                mCompressedBuyerInputCreatorHelper.buildCustomAudienceProtoFrom(dbCustomAudience);

        expect.that(customAudience.getName()).isEqualTo(dbCustomAudience.getName());
        expect.that(customAudience.getOwner()).isEqualTo(dbCustomAudience.getOwner());
        expect.that(customAudience.getBiddingSignalsKeysCount())
                .isEqualTo(dbCustomAudience.getTrustedBiddingData().getKeys().size());
        expect.that(customAudience.getAdRenderIdsCount())
                .isEqualTo(2 /* from the DBCustomAudienceFixture */);
    }

    @Test
    public void testBuildCustomAudienceProtoOmitAdsEnabled_CADoesNotOmitsAds() {
        mCompressedBuyerInputCreatorHelper =
                new CompressedBuyerInputCreatorHelper(
                        mAuctionServerPayloadMetricsStrategy,
                        mPasExtendedMetricsEnabled, /* omitAdsEnabled */
                        true);

        DBCustomAudience dbCustomAudience =
                DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(BUYER_1, "buyer1")
                        .build();

        BiddingAuctionServers.BuyerInput.CustomAudience customAudience =
                mCompressedBuyerInputCreatorHelper.buildCustomAudienceProtoFrom(dbCustomAudience);

        expect.that(customAudience.getName()).isEqualTo(dbCustomAudience.getName());
        expect.that(customAudience.getOwner()).isEqualTo(dbCustomAudience.getOwner());
        expect.that(customAudience.getBiddingSignalsKeysCount())
                .isEqualTo(dbCustomAudience.getTrustedBiddingData().getKeys().size());
        expect.that(customAudience.getAdRenderIdsCount())
                .isEqualTo(2 /* from the DBCustomAudienceFixture */);
    }

    @Test
    public void testBuildCustomAudienceProtoOmitAdsEnabled_CAOmitsAds() {
        mCompressedBuyerInputCreatorHelper =
                new CompressedBuyerInputCreatorHelper(
                        mAuctionServerPayloadMetricsStrategy,
                        mPasExtendedMetricsEnabled, /* omitAdsEnabled */
                        true);

        DBCustomAudience dbCustomAudience =
                DBCustomAudienceFixture.getValidBuilderByBuyerWithOmitAdsEnabled(BUYER_1, "buyer1")
                        .build();

        BiddingAuctionServers.BuyerInput.CustomAudience customAudience =
                mCompressedBuyerInputCreatorHelper.buildCustomAudienceProtoFrom(dbCustomAudience);

        expect.that(customAudience.getName()).isEqualTo(dbCustomAudience.getName());
        expect.that(customAudience.getOwner()).isEqualTo(dbCustomAudience.getOwner());
        expect.that(customAudience.getBiddingSignalsKeysCount())
                .isEqualTo(dbCustomAudience.getTrustedBiddingData().getKeys().size());
        expect.that(customAudience.getAdRenderIdsCount())
                .isEqualTo(0 /* from the DBCustomAudienceFixture */);
    }

    @Test
    public void testBuildProtectedSignalsProtoFrom() {
        DBEncodedPayload dbEncodedSignalsPayload = DBEncodedPayloadFixture.anEncodedPayload();

        BiddingAuctionServers.ProtectedAppSignals protectedAppSignals =
                mCompressedBuyerInputCreatorHelper.buildProtectedSignalsProtoFrom(
                        dbEncodedSignalsPayload);

        expect.that(protectedAppSignals.getEncodingVersion())
                .isEqualTo(dbEncodedSignalsPayload.getVersion());
        expect.that(protectedAppSignals.getAppInstallSignals())
                .isEqualTo(ByteString.copyFrom(dbEncodedSignalsPayload.getEncodedPayload()));
    }

    @Test
    public void testAddToBuyerIntermediateStats() {
        DBCustomAudience dbCustomAudience =
                DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(BUYER_1, "buyer1")
                        .build();

        BiddingAuctionServers.BuyerInput.CustomAudience customAudience =
                mCompressedBuyerInputCreatorHelper.buildCustomAudienceProtoFrom(dbCustomAudience);

        mCompressedBuyerInputCreatorHelper.addToBuyerIntermediateStats(
                ImmutableMap.of(),
                DBCustomAudienceFixture.VALID_DB_CUSTOM_AUDIENCE_NO_FILTERS,
                customAudience);

        verify(mAuctionServerPayloadMetricsStrategy)
                .addToBuyerIntermediateStats(any(), any(), any());
    }

    @Test
    public void logBuyerInputGeneratedStatsPasExtendedMetricsEnabled() {
        mCompressedBuyerInputCreatorHelper =
                new CompressedBuyerInputCreatorHelper(
                        mAuctionServerPayloadMetricsStrategy,
                        /* pasExtendedMetricsEnabled */ true,
                        mOmitAdsEnabled);

        Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> perBuyerStats =
                ImmutableMap.of();

        mCompressedBuyerInputCreatorHelper.logBuyerInputGeneratedStats(perBuyerStats);
        verify(mAuctionServerPayloadMetricsStrategy)
                .logGetAdSelectionDataBuyerInputGeneratedStatsWithExtendedPasMetrics(
                        perBuyerStats, 0, 0, 0, Integer.MAX_VALUE);

        mCompressedBuyerInputCreatorHelper.incrementPasExtendedMetrics(new byte[5]);
        mCompressedBuyerInputCreatorHelper.logBuyerInputGeneratedStats(perBuyerStats);
        verify(mAuctionServerPayloadMetricsStrategy)
                .logGetAdSelectionDataBuyerInputGeneratedStatsWithExtendedPasMetrics(
                        perBuyerStats, 1, 5, 5, 5);

        mCompressedBuyerInputCreatorHelper.incrementPasExtendedMetrics(new byte[10]);
        mCompressedBuyerInputCreatorHelper.logBuyerInputGeneratedStats(perBuyerStats);
        verify(mAuctionServerPayloadMetricsStrategy)
                .logGetAdSelectionDataBuyerInputGeneratedStatsWithExtendedPasMetrics(
                        perBuyerStats, 2, 15, 10, 5);

        verify(mAuctionServerPayloadMetricsStrategy, never())
                .logGetAdSelectionDataBuyerInputGeneratedStats(any());
    }

    @Test
    public void logBuyerInputGeneratedStatsPasExtendedMetricsDisabled() {
        mCompressedBuyerInputCreatorHelper =
                new CompressedBuyerInputCreatorHelper(
                        mAuctionServerPayloadMetricsStrategy,
                        /* pasExtendedMetricsEnabled */ false,
                        mOmitAdsEnabled);

        Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> perBuyerStats =
                ImmutableMap.of();

        mCompressedBuyerInputCreatorHelper.logBuyerInputGeneratedStats(perBuyerStats);
        verify(mAuctionServerPayloadMetricsStrategy)
                .logGetAdSelectionDataBuyerInputGeneratedStats(perBuyerStats);

        verify(mAuctionServerPayloadMetricsStrategy, never())
                .logGetAdSelectionDataBuyerInputGeneratedStatsWithExtendedPasMetrics(
                        any(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    public void testGetUserBiddingSignals_NullUserBiddingSignals() {
        DBCustomAudience dbCustomAudience =
                DBCustomAudienceFixture.getValidBuilderByBuyer(BUYER_1)
                        .setUserBiddingSignals(null)
                        .build();

        String userBiddingSignals =
                mCompressedBuyerInputCreatorHelper.getUserBiddingSignals(dbCustomAudience);

        expect.that(userBiddingSignals).isEqualTo(EMPTY_USER_BIDDING_SIGNALS);
    }

    @Test
    public void testGetTrustedBiddingSignalsKeys_BiddingSignalsCANameOnly_ReturnsEmptyList() {
        String caName = "name";
        DBTrustedBiddingData trustedBiddingData =
                DBTrustedBiddingDataFixture.getValidBuilderByBuyer(BUYER_1)
                        .setKeys(ImmutableList.of(caName))
                        .build();
        DBCustomAudience dbCustomAudience =
                DBCustomAudienceFixture.getValidBuilderByBuyer(BUYER_1)
                        .setName(caName)
                        .setTrustedBiddingData(trustedBiddingData)
                        .build();

        List<String> trustedBiddingSignalKeys =
                mCompressedBuyerInputCreatorHelper.getTrustedBiddingSignalKeys(dbCustomAudience);

        expect.that(trustedBiddingSignalKeys).isEmpty();
    }
}
