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

import static com.android.adservices.service.stats.AdServicesLoggerUtil.FIELD_UNSET;

import android.adservices.common.AdTechIdentifier;

import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdsRelevanceStatusUtils;
import com.android.adservices.service.stats.BuyerInputGeneratorIntermediateStats;
import com.android.adservices.service.stats.GetAdSelectionDataApiCalledStats;
import com.android.adservices.service.stats.GetAdSelectionDataBuyerInputGeneratedStats;

import java.util.Map;

public class AuctionServerPayloadMetricsStrategyEnabled
        implements AuctionServerPayloadMetricsStrategy {
    private final AdServicesLogger mAdServicesLogger;
    private final SellerConfigurationMetricsStrategy mSellerConfigurationMetricsStrategy;

    /** Constructs a {@link AuctionServerPayloadMetricsStrategyEnabled} instance. */
    public AuctionServerPayloadMetricsStrategyEnabled(
            AdServicesLogger adServicesLogger,
            SellerConfigurationMetricsStrategy sellerConfigurationMetricsStrategy) {
        mAdServicesLogger = adServicesLogger;
        mSellerConfigurationMetricsStrategy = sellerConfigurationMetricsStrategy;
    }

    @Override
    public void setNumBuyers(GetAdSelectionDataApiCalledStats.Builder builder, int numBuyers) {
        builder.setNumBuyers(numBuyers);
    }

    @Override
    public void setSellerConfigurationMetrics(
            GetAdSelectionDataApiCalledStats.Builder builder,
            GetAdSelectionDataApiCalledStats.PayloadOptimizationResult payloadOptimizationResult,
            int inputGenerationLatencyMs,
            int compressedBuyerInputCreatorVersion,
            int numReEstimations) {
        mSellerConfigurationMetricsStrategy.setSellerConfigurationMetrics(
                builder,
                payloadOptimizationResult,
                inputGenerationLatencyMs,
                compressedBuyerInputCreatorVersion,
                numReEstimations);
    }

    @Override
    public void setSellerMaxPayloadSizeKb(
            GetAdSelectionDataApiCalledStats.Builder builder, int sellerMaxPayloadSizeKb) {
        mSellerConfigurationMetricsStrategy.setSellerMaxPayloadSizeKb(
                builder, sellerMaxPayloadSizeKb);
    }

    @Override
    public void setInputGenerationLatencyMsAndBuyerCreatorVersion(
            GetAdSelectionDataApiCalledStats.Builder builder,
            int inputGenerationLatencyMs,
            int compressedBuyerInputCreatorVersion) {
        mSellerConfigurationMetricsStrategy.setInputGenerationLatencyMsAndBuyerCreatorVersion(
                builder, inputGenerationLatencyMs, compressedBuyerInputCreatorVersion);
    }

    @Override
    public void setServerAuctionCoordinatorSource(
            GetAdSelectionDataApiCalledStats.Builder builder,
            @AdsRelevanceStatusUtils.ServerAuctionCoordinatorSource int coordinatorSource) {
        // do nothing
    }

    @Override
    public void logGetAdSelectionDataApiCalledStats(
            GetAdSelectionDataApiCalledStats.Builder builder, int payloadSize, int statusCode) {
        mAdServicesLogger.logGetAdSelectionDataApiCalledStats(
                builder.setPayloadSizeKb(payloadSize).setStatusCode(statusCode).build());
    }

    @Override
    public void logGetAdSelectionDataBuyerInputGeneratedStats(
            Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> statsMap) {
        for (BuyerInputGeneratorIntermediateStats buyerStats : statsMap.values()) {
            GetAdSelectionDataBuyerInputGeneratedStats stats =
                    GetAdSelectionDataBuyerInputGeneratedStats.builder()
                            .setNumCustomAudiences(buyerStats.getNumCustomAudiences())
                            .setNumCustomAudiencesOmitAds(buyerStats.getNumCustomAudiencesOmitAds())
                            .setCustomAudienceSizeMeanB(buyerStats.getCustomAudienceSizeMeanB())
                            .setCustomAudienceSizeVarianceB(
                                    buyerStats.getCustomAudienceSizeVarianceB())
                            .setTrustedBiddingSignalsKeysSizeMeanB(
                                    buyerStats.getTrustedBiddingSignalsKeysSizeMeanB())
                            .setTrustedBiddingSignalsKeysSizeVarianceB(
                                    buyerStats.getTrustedBiddingSignalskeysSizeVarianceB())
                            .setUserBiddingSignalsSizeMeanB(
                                    buyerStats.getUserBiddingSignalsSizeMeanB())
                            .setUserBiddingSignalsSizeVarianceB(
                                    buyerStats.getUserBiddingSignalsSizeVarianceB())
                            .build();
            mAdServicesLogger.logGetAdSelectionDataBuyerInputGeneratedStats(stats);
        }
    }

    @Override
    public void addToBuyerIntermediateStats(
            Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> perBuyerStats,
            DBCustomAudience dbCustomAudience,
            BiddingAuctionServers.BuyerInput.CustomAudience customAudience) {
        AdTechIdentifier buyerName = dbCustomAudience.getBuyer();

        if (!perBuyerStats.containsKey(buyerName)) {
            perBuyerStats.put(buyerName, new BuyerInputGeneratorIntermediateStats());
        }
        updateInputFromCustomAudience(
                perBuyerStats.get(buyerName), customAudience, dbCustomAudience);
    }

    @Override
    public void logGetAdSelectionDataBuyerInputGeneratedStatsWithExtendedPasMetrics(
            Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> statsMap,
            int encodedSignalsCount,
            int encodedSignalsTotalSizeInBytes,
            int encodedSignalsMaxSizeInBytes,
            int encodedSignalsMinSizeInBytes) {
        int encodedSignalsMeanSizeInBytes =
                encodedSignalsCount == 0 ? 0 : encodedSignalsTotalSizeInBytes / encodedSignalsCount;

        if (statsMap.isEmpty()) {
            GetAdSelectionDataBuyerInputGeneratedStats stats =
                    GetAdSelectionDataBuyerInputGeneratedStats.builder()
                            .setNumCustomAudiences(FIELD_UNSET)
                            .setNumCustomAudiencesOmitAds(FIELD_UNSET)
                            .setCustomAudienceSizeMeanB(FIELD_UNSET)
                            .setCustomAudienceSizeVarianceB(FIELD_UNSET)
                            .setTrustedBiddingSignalsKeysSizeMeanB(FIELD_UNSET)
                            .setTrustedBiddingSignalsKeysSizeVarianceB(FIELD_UNSET)
                            .setUserBiddingSignalsSizeMeanB(FIELD_UNSET)
                            .setUserBiddingSignalsSizeVarianceB(FIELD_UNSET)
                            .setNumEncodedSignals(encodedSignalsCount)
                            .setEncodedSignalsSizeMean(encodedSignalsMeanSizeInBytes)
                            .setEncodedSignalsSizeMax(encodedSignalsMaxSizeInBytes)
                            .setEncodedSignalsSizeMin(encodedSignalsMinSizeInBytes)
                            .build();
            mAdServicesLogger.logGetAdSelectionDataBuyerInputGeneratedStats(stats);
        } else {
            for (BuyerInputGeneratorIntermediateStats buyerStats : statsMap.values()) {
                GetAdSelectionDataBuyerInputGeneratedStats stats =
                        GetAdSelectionDataBuyerInputGeneratedStats.builder()
                                .setNumCustomAudiences(buyerStats.getNumCustomAudiences())
                                .setNumCustomAudiencesOmitAds(
                                        buyerStats.getNumCustomAudiencesOmitAds())
                                .setCustomAudienceSizeMeanB(buyerStats.getCustomAudienceSizeMeanB())
                                .setCustomAudienceSizeVarianceB(
                                        buyerStats.getCustomAudienceSizeVarianceB())
                                .setTrustedBiddingSignalsKeysSizeMeanB(
                                        buyerStats.getTrustedBiddingSignalsKeysSizeMeanB())
                                .setTrustedBiddingSignalsKeysSizeVarianceB(
                                        buyerStats.getTrustedBiddingSignalskeysSizeVarianceB())
                                .setUserBiddingSignalsSizeMeanB(
                                        buyerStats.getUserBiddingSignalsSizeMeanB())
                                .setUserBiddingSignalsSizeVarianceB(
                                        buyerStats.getUserBiddingSignalsSizeVarianceB())
                                .setNumEncodedSignals(encodedSignalsCount)
                                .setEncodedSignalsSizeMean(encodedSignalsMeanSizeInBytes)
                                .setEncodedSignalsSizeMax(encodedSignalsMaxSizeInBytes)
                                .setEncodedSignalsSizeMin(encodedSignalsMinSizeInBytes)
                                .build();
                mAdServicesLogger.logGetAdSelectionDataBuyerInputGeneratedStats(stats);
            }
        }
    }

    private void updateInputFromCustomAudience(
            BuyerInputGeneratorIntermediateStats stats,
            BiddingAuctionServers.BuyerInput.CustomAudience customAudience,
            DBCustomAudience dbCustomAudience) {
        stats.incrementNumCustomAudiences();
        if (isCaOmittingAds(customAudience, dbCustomAudience)) {
            stats.incrementNumCustomAudiencesOmitAds();
        }
        stats.addCustomAudienceSize(customAudience.getSerializedSize());
        if (dbCustomAudience.getTrustedBiddingData() != null) {
            int trustedBiddingSignalsKeysSize =
                    dbCustomAudience.getTrustedBiddingData().size()
                            - dbCustomAudience
                                    .getTrustedBiddingData()
                                    .getUri()
                                    .toString()
                                    .getBytes()
                                    .length;
            stats.addTrustedBiddingSignalsKeysSize(trustedBiddingSignalsKeysSize);
        } else {
            stats.addTrustedBiddingSignalsKeysSize(0);
        }
        if (dbCustomAudience.getUserBiddingSignals() != null) {
            stats.addUserBiddingSignalsSize(
                    dbCustomAudience.getUserBiddingSignals().getSizeInBytes());
        } else {
            stats.addUserBiddingSignalsSize(0);
        }
    }

    private boolean isCaOmittingAds(
            BiddingAuctionServers.BuyerInput.CustomAudience customAudience,
            DBCustomAudience dbCustomAudience) {
        return customAudience.getAdRenderIdsCount() == 0
                && ((dbCustomAudience.getAuctionServerRequestFlags()
                                & FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                        != 0);
    }
}
