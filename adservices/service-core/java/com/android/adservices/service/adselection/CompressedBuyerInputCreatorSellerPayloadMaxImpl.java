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

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.service.profiling.Tracing;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers;
import com.android.adservices.service.stats.BuyerInputGeneratorIntermediateStats;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompressedBuyerInputCreatorSellerPayloadMaxImpl
        implements CompressedBuyerInputCreator {
    public static final int VERSION = 1;

    private final CompressedBuyerInputCreatorHelper mCompressedBuyerInputCreatorHelper;
    private final AuctionServerDataCompressor mDataCompressor;
    private final int mSellerMaxSizeBytes;
    private final int mMaxNumRecalculations;
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private final int mStoppingPointBytes;
    private final int mPerBuyerPasMaxSizeBytes;

    // TODO(b/347314190) investigate making this a flag
    private static final double UNDER_SIZE_TARGET_RATIO = .95;

    public CompressedBuyerInputCreatorSellerPayloadMaxImpl(
            CompressedBuyerInputCreatorHelper compressedBuyerInputCreatorHelper,
            AuctionServerDataCompressor dataCompressor,
            int maxNumRecalculations,
            int sellerMaxSizeBytes,
            int perBuyerPasMaxSizeBytes) {
        mCompressedBuyerInputCreatorHelper = compressedBuyerInputCreatorHelper;
        mDataCompressor = dataCompressor;
        mSellerMaxSizeBytes = sellerMaxSizeBytes;
        mMaxNumRecalculations = maxNumRecalculations;
        mStoppingPointBytes = (int) (mSellerMaxSizeBytes * UNDER_SIZE_TARGET_RATIO);
        mPerBuyerPasMaxSizeBytes = perBuyerPasMaxSizeBytes;
    }

    @Override
    public Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData>
            generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                    List<DBCustomAudience> dbCustomAudiences,
                    Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.GET_COMPRESSED_BUYERS_INPUTS);

        Map<AdTechIdentifier, BiddingAuctionServers.BuyerInput.Builder> buyerInputs =
                new HashMap<>();
        final Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> perBuyerStats =
                new HashMap<>();

        // Start with PAS
        addPASToBuyerInput(buyerInputs, encodedPayloadMap);

        // Calculate current total size post PAS and add CAs to buyer input
        int currentEstimatedTotalSize = getCurrentSize(buyerInputs);
        sLogger.v("Initial estimated size after PAS" + currentEstimatedTotalSize);
        addCAsToBuyerInput(
                buyerInputs, dbCustomAudiences, perBuyerStats, currentEstimatedTotalSize);

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
        sLogger.v("Total size:" + getTotalSize(compressedInputs));
        Tracing.endAsyncSection(Tracing.GET_COMPRESSED_BUYERS_INPUTS, traceCookie);
        return compressedInputs;
    }

    private int getCompressionSize(BiddingAuctionServers.BuyerInput.Builder buyerInput) {
        return mDataCompressor
                .compress(
                        AuctionServerDataCompressor.UncompressedData.create(
                                buyerInput.build().toByteArray()))
                .getData()
                .length;
    }

    private int getTotalSize(
            Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedInputs) {
        int totalSize = 0;
        for (AuctionServerDataCompressor.CompressedData data : compressedInputs.values()) {
            totalSize += data.getData().length;
        }
        return totalSize;
    }

    private int getCurrentSize(
            Map<AdTechIdentifier, BiddingAuctionServers.BuyerInput.Builder> buyerInputs) {
        int total = 0;
        for (BiddingAuctionServers.BuyerInput.Builder input : buyerInputs.values()) {
            total += getCompressionSize(input);
        }
        return total;
    }

    private int getCompressionSize(BiddingAuctionServers.BuyerInput.CustomAudience customAudience) {
        return mDataCompressor
                .compress(
                        AuctionServerDataCompressor.UncompressedData.create(
                                customAudience.toByteArray()))
                .getData()
                .length;
    }

    private void addPASToBuyerInput(
            Map<AdTechIdentifier, BiddingAuctionServers.BuyerInput.Builder> buyerInputs,
            Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap) {
        if (mSellerMaxSizeBytes <= 0) {
            sLogger.v("Max size is 0, returning an empty payload");
            return;
        }
        int numBuyers = encodedPayloadMap.keySet().size();
        if (numBuyers * mPerBuyerPasMaxSizeBytes > mSellerMaxSizeBytes) {
            sLogger.v("Not enough space for PAS");
            return;
        }
        sLogger.v("Adding PAS to the buyer input.");
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
    }

    private void addCAsToBuyerInput(
            Map<AdTechIdentifier, BiddingAuctionServers.BuyerInput.Builder> buyerInputs,
            List<DBCustomAudience> dbCustomAudiences,
            Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> perBuyerStats,
            int currentEstimatedTotalSize) {
        if (mSellerMaxSizeBytes <= 0) {
            sLogger.v("Max size is 0, returning an empty payload");
            return;
        }
        int currentRecalculations = 0;
        for (DBCustomAudience dBcustomAudience : dbCustomAudiences) {
            if (currentEstimatedTotalSize >= mStoppingPointBytes
                    && currentRecalculations >= mMaxNumRecalculations) {
                sLogger.v(
                        "Reached stopping point of %d bytes for buyer input generation with"
                                + " estimated size of %d bytes and %d maximum number of"
                                + " recalculations.",
                        mStoppingPointBytes, currentEstimatedTotalSize, mMaxNumRecalculations);
                break;
            }
            final AdTechIdentifier buyerName = dBcustomAudience.getBuyer();
            if (!buyerInputs.containsKey(buyerName)) {
                buyerInputs.put(buyerName, BiddingAuctionServers.BuyerInput.newBuilder());
            }
            BiddingAuctionServers.BuyerInput.CustomAudience customAudience =
                    mCompressedBuyerInputCreatorHelper.buildCustomAudienceProtoFrom(
                            dBcustomAudience);

            int nextCustomAudienceCompressedSize = getCompressionSize(customAudience);

            if (currentEstimatedTotalSize + nextCustomAudienceCompressedSize
                    > mSellerMaxSizeBytes) {
                sLogger.v("Max size exceeded, proceeding with re estimation or skipping.");
                if (currentRecalculations < mMaxNumRecalculations) {
                    currentRecalculations++;
                    currentEstimatedTotalSize = getCurrentSize(buyerInputs);
                    if (currentEstimatedTotalSize + nextCustomAudienceCompressedSize
                            > mSellerMaxSizeBytes) {
                        sLogger.v("Max size exceeded after re estimation, skipping.");
                        continue;
                    }
                }
            }
            sLogger.v("Adding to the buyer input");
            buyerInputs.get(buyerName).addCustomAudiences(customAudience);

            mCompressedBuyerInputCreatorHelper.addToBuyerIntermediateStats(
                    perBuyerStats, dBcustomAudience, customAudience);
            currentEstimatedTotalSize += nextCustomAudienceCompressedSize;
        }
    }
}
