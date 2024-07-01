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

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;

import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers;
import com.android.adservices.service.stats.BuyerInputGeneratorIntermediateStats;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CompressedBuyerInputCreatorHelper {
    protected static final String EMPTY_USER_BIDDING_SIGNALS = "{}";
    private final AuctionServerPayloadMetricsStrategy mAuctionServerPayloadMetricsStrategy;
    private final boolean mPasExtendedMetricsEnabled;
    private final boolean mEnableOmitAds;

    private int mEncodedSignalsCount = 0;
    private int mEncodedSignalsTotalSizeInBytes = 0;
    private int mEncodedSignalsMaxSizeInBytes = 0;
    private int mEncodedSignalsMinSizeInBytes = Integer.MAX_VALUE;
    private boolean mPasExtendedMetricsIncremented = false;

    public CompressedBuyerInputCreatorHelper(
            AuctionServerPayloadMetricsStrategy auctionServerPayloadMetricsStrategy,
            boolean pasExtendedMetricsEnabled,
            boolean enableOmitAds) {
        mAuctionServerPayloadMetricsStrategy = auctionServerPayloadMetricsStrategy;
        mPasExtendedMetricsEnabled = pasExtendedMetricsEnabled;
        mEnableOmitAds = enableOmitAds;
    }

    /**
     * Builds a bidding and auction server custom audience proto from a {@link DBCustomAudience}.
     */
    public BiddingAuctionServers.BuyerInput.CustomAudience buildCustomAudienceProtoFrom(
            DBCustomAudience customAudience) {
        BiddingAuctionServers.BuyerInput.CustomAudience.Builder customAudienceBuilder =
                BiddingAuctionServers.BuyerInput.CustomAudience.newBuilder();

        customAudienceBuilder
                .setName(customAudience.getName())
                .setOwner(customAudience.getOwner())
                .setUserBiddingSignals(getUserBiddingSignals(customAudience))
                .addAllBiddingSignalsKeys(getTrustedBiddingSignalKeys(customAudience));

        if (shouldIncludeAds(customAudience)) {
            customAudienceBuilder.addAllAdRenderIds(getAdRenderIds(customAudience));
        }
        return customAudienceBuilder.build();
    }

    /**
     * Builds a bidding and auction server protected signals proto from a {@link DBEncodedPayload}.
     */
    public BiddingAuctionServers.ProtectedAppSignals buildProtectedSignalsProtoFrom(
            DBEncodedPayload dbEncodedSignalsPayload) {
        BiddingAuctionServers.ProtectedAppSignals.Builder protectedSignalsBuilder =
                BiddingAuctionServers.ProtectedAppSignals.newBuilder();

        return protectedSignalsBuilder
                .setAppInstallSignals(
                        ByteString.copyFrom(dbEncodedSignalsPayload.getEncodedPayload()))
                .setEncodingVersion(dbEncodedSignalsPayload.getVersion())
                .build();
    }

    protected String getUserBiddingSignals(DBCustomAudience customAudience) {
        return customAudience.getUserBiddingSignals() == null
                ? EMPTY_USER_BIDDING_SIGNALS
                : customAudience.getUserBiddingSignals().toString();
    }

    private List<String> getAdRenderIds(DBCustomAudience dbCustomAudience) {
        return dbCustomAudience.getAds().stream()
                .filter(ad -> !Strings.isNullOrEmpty(ad.getAdRenderId()))
                .map(ad -> ad.getAdRenderId())
                .collect(Collectors.toList());
    }

    private boolean shouldIncludeAds(DBCustomAudience customAudience) {
        return !(mEnableOmitAds
                && ((customAudience.getAuctionServerRequestFlags()
                                & FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                        != 0));
    }

    protected List<String> getTrustedBiddingSignalKeys(@NonNull DBCustomAudience customAudience) {
        List<String> biddingSignalKeys = customAudience.getTrustedBiddingData().getKeys();
        // If the bidding signal keys is just the CA name, we don't need to pass it to the server.
        if (biddingSignalKeys.size() == 1
                && customAudience.getName().equals(biddingSignalKeys.get(0))) {
            return ImmutableList.of();
        }

        // Remove the CA name from the bidding signal keys list to save space.
        biddingSignalKeys.remove(customAudience.getName());
        return biddingSignalKeys;
    }

    private int calculateEncodedSignalsInBytes(byte[] encodedPayload) {
        return encodedPayload.length;
    }

    /** Invokes {@link AuctionServerPayloadMetricsStrategy} to add per buyer intermediate stats. */
    public void addToBuyerIntermediateStats(
            Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> perBuyerStats,
            DBCustomAudience dbCustomAudience,
            BiddingAuctionServers.BuyerInput.CustomAudience customAudience) {
        mAuctionServerPayloadMetricsStrategy.addToBuyerIntermediateStats(
                perBuyerStats, dbCustomAudience, customAudience);
    }

    /** Increments PAS extended metrics based on the length of the encoded payload. */
    public void incrementPasExtendedMetrics(byte[] encodedPayload) {
        if (mPasExtendedMetricsEnabled) {
            mPasExtendedMetricsIncremented = true;
            int encodedSignalsInBytes = calculateEncodedSignalsInBytes(encodedPayload);
            mEncodedSignalsCount += 1;
            mEncodedSignalsTotalSizeInBytes += encodedSignalsInBytes;
            mEncodedSignalsMaxSizeInBytes =
                    Math.max(mEncodedSignalsMaxSizeInBytes, encodedSignalsInBytes);
            mEncodedSignalsMinSizeInBytes =
                    Math.min(mEncodedSignalsMinSizeInBytes, encodedSignalsInBytes);
        }
    }

    /** Logs the buyer input generated stats. */
    public void logBuyerInputGeneratedStats(
            Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> perBuyerStats) {
        if (mPasExtendedMetricsEnabled) {
            int encodedSignalsMinSizeInBytes =
                    mPasExtendedMetricsIncremented ? mEncodedSignalsMinSizeInBytes : 0;
            mAuctionServerPayloadMetricsStrategy
                    .logGetAdSelectionDataBuyerInputGeneratedStatsWithExtendedPasMetrics(
                            perBuyerStats,
                            mEncodedSignalsCount,
                            mEncodedSignalsTotalSizeInBytes,
                            mEncodedSignalsMaxSizeInBytes,
                            encodedSignalsMinSizeInBytes);
        } else {
            mAuctionServerPayloadMetricsStrategy.logGetAdSelectionDataBuyerInputGeneratedStats(
                    perBuyerStats);
        }
    }
}
