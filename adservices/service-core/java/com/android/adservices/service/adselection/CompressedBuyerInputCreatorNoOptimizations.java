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

import android.adservices.common.AdTechIdentifier;

import androidx.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.service.profiling.Tracing;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers;
import com.android.adservices.service.stats.BuyerInputGeneratorIntermediateStats;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompressedBuyerInputCreatorNoOptimizations implements CompressedBuyerInputCreator {

    public static final int VERSION = 0;

    private final CompressedBuyerInputCreatorHelper mCompressedBuyerInputCreatorHelper;
    private final AuctionServerDataCompressor mDataCompressor;
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    public CompressedBuyerInputCreatorNoOptimizations(
            CompressedBuyerInputCreatorHelper compressedBuyerInputCreatorHelper,
            AuctionServerDataCompressor dataCompressor) {
        mCompressedBuyerInputCreatorHelper = compressedBuyerInputCreatorHelper;
        mDataCompressor = dataCompressor;
    }

    @Override
    public Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData>
            generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                    @NonNull List<DBCustomAudience> dbCustomAudiences,
                    @NonNull Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.GET_COMPRESSED_BUYERS_INPUTS);

        final Map<AdTechIdentifier, BiddingAuctionServers.BuyerInput.Builder> buyerInputs =
                new HashMap<>();
        final Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> perBuyerStats =
                new HashMap<>();

        // Creating a distinct loop over signals as buyers with CAs and Signals could be mutually
        // exclusive
        for (Map.Entry<AdTechIdentifier, DBEncodedPayload> entry : encodedPayloadMap.entrySet()) {
            final AdTechIdentifier buyerName = entry.getKey();
            if (!buyerInputs.containsKey(buyerName)) {
                buyerInputs.put(buyerName, BiddingAuctionServers.BuyerInput.newBuilder());
            }

            BiddingAuctionServers.BuyerInput.Builder builderWithSignals =
                    buyerInputs.get(buyerName);
            builderWithSignals.setProtectedAppSignals(
                    mCompressedBuyerInputCreatorHelper.buildProtectedSignalsProtoFrom(
                            entry.getValue()));

            mCompressedBuyerInputCreatorHelper.incrementPasExtendedMetrics(
                    entry.getValue().getEncodedPayload());

            buyerInputs.put(buyerName, builderWithSignals);
        }

        for (DBCustomAudience dBcustomAudience : dbCustomAudiences) {
            final AdTechIdentifier buyerName = dBcustomAudience.getBuyer();
            if (!buyerInputs.containsKey(buyerName)) {
                buyerInputs.put(buyerName, BiddingAuctionServers.BuyerInput.newBuilder());
            }
            BiddingAuctionServers.BuyerInput.CustomAudience customAudience =
                    mCompressedBuyerInputCreatorHelper.buildCustomAudienceProtoFrom(
                            dBcustomAudience);

            buyerInputs.get(buyerName).addCustomAudiences(customAudience);

            mCompressedBuyerInputCreatorHelper.addToBuyerIntermediateStats(
                    perBuyerStats, dBcustomAudience, customAudience);
        }

        mCompressedBuyerInputCreatorHelper.logBuyerInputGeneratedStats(perBuyerStats);

        sLogger.v(String.format("Created BuyerInput proto for %s buyers", buyerInputs.size()));
        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedInputs =
                new HashMap<>();
        for (Map.Entry<AdTechIdentifier, BiddingAuctionServers.BuyerInput.Builder> entry :
                buyerInputs.entrySet()) {
            compressedInputs.put(
                    entry.getKey(),
                    mDataCompressor.compress(
                            AuctionServerDataCompressor.UncompressedData.create(
                                    entry.getValue().build().toByteArray())));
        }
        Tracing.endAsyncSection(Tracing.GET_COMPRESSED_BUYERS_INPUTS, traceCookie);
        return compressedInputs;
    }
}
