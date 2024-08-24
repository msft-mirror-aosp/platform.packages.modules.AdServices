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

import android.adservices.adselection.PerBuyerConfiguration;
import android.adservices.common.AdTechIdentifier;
import android.annotation.Nullable;
import android.util.Pair;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.service.profiling.Tracing;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers;
import com.android.adservices.service.stats.BuyerInputGeneratorIntermediateStats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CompressedBuyerInputCreatorPerBuyerLimitsGreedyImpl
        implements CompressedBuyerInputCreator {
    public static final int VERSION = 2;
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    // Consider making this a flag, aim for 0.95 in future
    private static final double PAYLOAD_UTILIZATION_GOAL = 0.90;
    private static final int MINIMUM_SIZE_OF_CA_BYTES = 64;

    // When casting to an int from a double, add this constant to get size ceiling
    private static final int BYTE_CEILING = 1;
    private final CompressedBuyerInputCreatorHelper mCompressedBuyerInputCreatorHelper;
    private final AuctionServerDataCompressor mDataCompressor;
    private final int mSellerMaxSizeBytes;
    private final Map<AdTechIdentifier, Integer> mPerBuyerLimits;
    private final int mPerBuyerPasMaxSizeBytes;

    public CompressedBuyerInputCreatorPerBuyerLimitsGreedyImpl(
            CompressedBuyerInputCreatorHelper compressedBuyerInputCreatorHelper,
            AuctionServerDataCompressor dataCompressor,
            PayloadOptimizationContext payloadOptimizationContext,
            int perBuyerPasMaxSizeBytes) {
        mCompressedBuyerInputCreatorHelper = compressedBuyerInputCreatorHelper;
        mDataCompressor = dataCompressor;
        mSellerMaxSizeBytes = payloadOptimizationContext.getMaxBuyerInputSizeBytes();
        mPerBuyerPasMaxSizeBytes = perBuyerPasMaxSizeBytes;
        mPerBuyerLimits =
                buildPerBuyerLimits(payloadOptimizationContext.getPerBuyerConfigurations());
    }

    @Override
    public Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData>
            generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                    List<DBCustomAudience> dbCustomAudiences,
                    Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.GET_COMPRESSED_BUYERS_INPUTS);

        // create buyer inputs and custom audiences that will be added to the payload for stats
        Map<AdTechIdentifier, BiddingAuctionServers.BuyerInput.Builder> buyerInputs =
                new HashMap<>();
        // Hold the DB and Proto object to avoid rebuilding the proto
        final Map<
                        AdTechIdentifier,
                        List<
                                Pair<
                                        DBCustomAudience,
                                        BiddingAuctionServers.BuyerInput.CustomAudience>>>
                customAudiencesInPayload = new HashMap<>();

        // create map of buyers and their custom audiences
        // sort per buyer CAs by priority in descending order (highest -> lowest)
        Map<AdTechIdentifier, List<DBCustomAudience>> perBuyerDBCAs =
                buildAndSortPerBuyerDBCAs(dbCustomAudiences);

        // prepare custom audiences in payload map for stat tracking
        for (AdTechIdentifier adTechIdentifier : perBuyerDBCAs.keySet()) {
            customAudiencesInPayload.put(adTechIdentifier, new ArrayList<>());
        }

        // create map of estimated compression ratio per buyer
        final Map<AdTechIdentifier, Double> estimatedRateOfCompressionPerBuyer =
                buildEstimatedRateOfCompressionPerBuyer(perBuyerDBCAs, encodedPayloadMap);

        // add PAS to buyer input, returns the estimated amount of bytes used for PAS
        int totalPASBytesUsed =
                addPASToBuyerInput(
                        buyerInputs, encodedPayloadMap, estimatedRateOfCompressionPerBuyer);

        // add CAs to buyer input
        addCAsToBuyerInput(
                buyerInputs,
                customAudiencesInPayload,
                perBuyerDBCAs,
                estimatedRateOfCompressionPerBuyer,
                totalPASBytesUsed);

        // compress buyer inputs
        sLogger.v(String.format("Created BuyerInput proto for %s buyers", buyerInputs.size()));
        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedInputs =
                compressBuyerInputs(buyerInputs);

        // If needed, truncate CAs
        int totalSizeOfBuyerInputs = getCurrentSize(compressedInputs);
        if (totalSizeOfBuyerInputs > mSellerMaxSizeBytes) {
            // payload is too big, we need to truncate some CAs
            int truncateBytes = (totalSizeOfBuyerInputs - mSellerMaxSizeBytes);
            sLogger.v("Truncating CAs, overflow size: %d", truncateBytes);

            // truncate and recompress buyer inputs
            truncateCAs(
                    buyerInputs,
                    customAudiencesInPayload,
                    truncateBytes,
                    estimatedRateOfCompressionPerBuyer);
            compressedInputs = compressBuyerInputs(buyerInputs);
        }

        // build and log per buyer stats
        final Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> perBuyerStats =
                generatePerBuyerStats(customAudiencesInPayload);
        mCompressedBuyerInputCreatorHelper.logBuyerInputGeneratedStats(perBuyerStats);

        Tracing.endAsyncSection(Tracing.GET_COMPRESSED_BUYERS_INPUTS, traceCookie);
        return compressedInputs;
    }

    private int addPASToBuyerInput(
            Map<AdTechIdentifier, BiddingAuctionServers.BuyerInput.Builder> buyerInputs,
            Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap,
            Map<AdTechIdentifier, Double> rateOfCompression) {
        int totalPASBytesUsed = 0; // total bytes used for PAS, return value
        if (mSellerMaxSizeBytes <= mPerBuyerPasMaxSizeBytes) {
            sLogger.v("Not enough space for any PAS");
            return 0;
        }
        for (Map.Entry<AdTechIdentifier, DBEncodedPayload> entry : encodedPayloadMap.entrySet()) {
            final AdTechIdentifier buyerName = entry.getKey();
            final DBEncodedPayload buyerEncodedPayload = entry.getValue();

            if (!buyerInputs.containsKey(buyerName)) {
                buyerInputs.put(buyerName, BiddingAuctionServers.BuyerInput.newBuilder());
            }

            // Break and go onto next buyer if no size remains to add PAS
            if (mPerBuyerLimits.get(buyerName) < mPerBuyerPasMaxSizeBytes) {
                sLogger.v("Not enough space for per buyer PAS");
            } else {
                sLogger.v("Adding PAS to the buyer input.");
                BiddingAuctionServers.BuyerInput.Builder builderWithSignals =
                        buyerInputs.get(buyerName);

                builderWithSignals.setProtectedAppSignals(
                        mCompressedBuyerInputCreatorHelper.buildProtectedSignalsProtoFrom(
                                buyerEncodedPayload));

                mCompressedBuyerInputCreatorHelper.incrementPasExtendedMetrics(
                        buyerEncodedPayload.getEncodedPayload());

                buyerInputs.put(buyerName, builderWithSignals);

                // update per buyer limits, negating PAS estimated size
                int currBuyerLimit = mPerBuyerLimits.get(buyerName);
                // +1 is for rounding up, since casting double to int rounds down (truncates)
                int estimatedSizeOfCompressedPAS =
                        (int)
                                        (buyerEncodedPayload.getEncodedPayload().length
                                                * rateOfCompression.get(buyerName))
                                + BYTE_CEILING;
                mPerBuyerLimits.put(buyerName, currBuyerLimit - estimatedSizeOfCompressedPAS);

                // add the estimated size of current pas for buyer to total size of all PAS
                totalPASBytesUsed += estimatedSizeOfCompressedPAS;
            }
        }
        return totalPASBytesUsed;
    }

    // TODO (b/359587202) explore multithreading
    private void addCAsToBuyerInput(
            Map<AdTechIdentifier, BiddingAuctionServers.BuyerInput.Builder> buyerInputs,
            Map<
                            AdTechIdentifier,
                            List<
                                    Pair<
                                            DBCustomAudience,
                                            BiddingAuctionServers.BuyerInput.CustomAudience>>>
                    customAudiencesInPayload,
            Map<AdTechIdentifier, List<DBCustomAudience>> perBuyerDBCAs,
            Map<AdTechIdentifier, Double> rateOfCompressionPerBuyer,
            int totalPASBytesUsed) {
        // remaining CAs per buyer in will be descending order, so index 0 will have highest
        // priority per buyer
        Map<AdTechIdentifier, List<DBCustomAudience>> remainingDBCAsPerBuyer = new HashMap<>();

        if (mSellerMaxSizeBytes <= 0) {
            sLogger.v("Max Size is invalid, seller max size: %d", mSellerMaxSizeBytes);
            return;
        }

        // Keep track of the amount of estimated bytes used
        int totalEstimatedBytesUsed = totalPASBytesUsed;
        sLogger.v("Initial estimated size after PAS" + totalEstimatedBytesUsed);

        // Add CAs per buyer, respecting per buyer limits
        for (Map.Entry<AdTechIdentifier, List<DBCustomAudience>> entry : perBuyerDBCAs.entrySet()) {
            AdTechIdentifier buyerName = entry.getKey();
            List<DBCustomAudience> dbCustomAudienceList = entry.getValue();
            int perBuyerLimit = (int) (mPerBuyerLimits.get(buyerName) * PAYLOAD_UTILIZATION_GOAL);

            // Prepare for overflow CAs to be added for utilization of remaining payload size (if
            // needed)
            remainingDBCAsPerBuyer.put(buyerName, new ArrayList<>());

            if (!buyerInputs.containsKey(buyerName)) {
                buyerInputs.put(buyerName, BiddingAuctionServers.BuyerInput.newBuilder());
            }

            // iterate from beginning to end, since list is sorted in descending order by priority
            for (int i = 0; i < dbCustomAudienceList.size(); i++) {
                // get the CA with the ith highest priority
                DBCustomAudience dbCustomAudience = dbCustomAudienceList.get(i);
                BiddingAuctionServers.BuyerInput.CustomAudience customAudienceProto =
                        mCompressedBuyerInputCreatorHelper.buildCustomAudienceProtoFrom(
                                dbCustomAudience);
                // get the estimated compressed size of the current custom audience
                // +1 is for rounding up, since casting from double -> int rounds down
                int estimatedSizeOfCa =
                        (int)
                                        (getUncompressedSize(customAudienceProto)
                                                * rateOfCompressionPerBuyer.get(buyerName))
                                + BYTE_CEILING;

                // check if per buyer limit is greater than size of CAs
                if (perBuyerLimit >= estimatedSizeOfCa
                        && totalEstimatedBytesUsed + estimatedSizeOfCa <= mSellerMaxSizeBytes) {
                    totalEstimatedBytesUsed += estimatedSizeOfCa; // for all buyers
                    perBuyerLimit -= estimatedSizeOfCa; // for single buyer
                    buyerInputs.get(buyerName).addCustomAudiences(customAudienceProto);

                    // add for stat tracking later
                    customAudiencesInPayload
                            .get(buyerName)
                            .add(Pair.create(dbCustomAudience, customAudienceProto));

                } else if (perBuyerLimit > MINIMUM_SIZE_OF_CA_BYTES) {
                    // If the per buyer limit is greater than minimum size of ca, add current CA for
                    // payload optimization later
                    remainingDBCAsPerBuyer.get(buyerName).add(dbCustomAudience);
                } else {
                    // Since there is probably not enough space to add any remaining potential CAs,
                    // add the remaining CAs for utilizing remaining payload space, end loop
                    List<DBCustomAudience> sublist =
                            dbCustomAudienceList.subList(i, dbCustomAudienceList.size());
                    remainingDBCAsPerBuyer.get(buyerName).addAll(sublist);
                    break;
                }
            }
        }

        // we only need to use rest of remaining payload space if we are less than payload
        // utilization goal
        if (totalEstimatedBytesUsed <= mSellerMaxSizeBytes * PAYLOAD_UTILIZATION_GOAL) {
            sLogger.v("Building remaining DBCA list");
            List<DBCustomAudience> remainingDBCAs = buildRemainingDBCAsList(remainingDBCAsPerBuyer);

            sLogger.v(
                    "Utilizing rest of payload with greedy function, estimated remaining size: %d",
                    (mSellerMaxSizeBytes - totalEstimatedBytesUsed));

            utilizeRemainingPayloadSpace(
                    remainingDBCAs,
                    buyerInputs,
                    customAudiencesInPayload,
                    rateOfCompressionPerBuyer,
                    totalEstimatedBytesUsed,
                    mSellerMaxSizeBytes);
        }
    }

    private Map<AdTechIdentifier, Double> buildEstimatedRateOfCompressionPerBuyer(
            Map<AdTechIdentifier, List<DBCustomAudience>> perBuyerCAs,
            Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap) {
        Map<AdTechIdentifier, Double> rateOfCompressionPerBuyer = new HashMap<>();

        for (Map.Entry<AdTechIdentifier, List<DBCustomAudience>> entry : perBuyerCAs.entrySet()) {
            AdTechIdentifier buyerName = entry.getKey();
            List<DBCustomAudience> customAudienceList = entry.getValue();
            if (encodedPayloadMap.containsKey(buyerName)) {
                DBEncodedPayload encodedPayload = encodedPayloadMap.get(buyerName);
                rateOfCompressionPerBuyer.put(
                        buyerName, getCompressionRatio(customAudienceList, encodedPayload));
            } else {
                rateOfCompressionPerBuyer.put(
                        buyerName, getCompressionRatio(customAudienceList, null));
            }
        }

        return rateOfCompressionPerBuyer;
    }

    /** Compresses data, divide over decompressed size to get a compression ratio */
    private double getCompressionRatio(
            List<DBCustomAudience> customAudienceList,
            @Nullable DBEncodedPayload dbEncodedPayload) {
        BiddingAuctionServers.BuyerInput.Builder buyerInput =
                BiddingAuctionServers.BuyerInput.newBuilder();

        if (dbEncodedPayload != null) {
            AdTechIdentifier buyerName = dbEncodedPayload.getBuyer();
            sLogger.v(
                    "Adding signals from buyer: %s to estimate compression ratio",
                    buyerName.toString());
            buyerInput.setProtectedAppSignals(
                    mCompressedBuyerInputCreatorHelper.buildProtectedSignalsProtoFrom(
                            dbEncodedPayload));
        }
        for (DBCustomAudience dbCustomAudience : customAudienceList) {
            BiddingAuctionServers.BuyerInput.CustomAudience customAudienceProto =
                    mCompressedBuyerInputCreatorHelper.buildCustomAudienceProtoFrom(
                            dbCustomAudience);
            buyerInput.addCustomAudiences(customAudienceProto);
        }

        AuctionServerDataCompressor.UncompressedData uncompressedData =
                AuctionServerDataCompressor.UncompressedData.create(
                        buyerInput.build().toByteArray());

        int compressedSize = mDataCompressor.compress(uncompressedData).getData().length;

        int uncompressedSize = uncompressedData.getData().length;

        sLogger.v("Compression ratio: %.15f", ((double) compressedSize / uncompressedSize));

        return ((double) compressedSize / uncompressedSize);
    }

    /**
     * Transforms a set of {@code perBuyerConfigurations} into a map of {@code AdTechIdentifier,
     * Integer} <br>
     * Allocate the necessary space among buyers, following two cases <br>
     * Case 1: Seller max size => sum(Per buyer limits), leave as is <br>
     * Case 2: Seller max size < sum(per buyer limits), split per buyer limits proportionally
     */
    private Map<AdTechIdentifier, Integer> buildPerBuyerLimits(
            Set<PerBuyerConfiguration> perBuyerConfigurations) {
        Map<AdTechIdentifier, Integer> perBuyerLimits = new HashMap<>();
        // get sum of all the per buyer limits
        int sumOfBuyerTargetSize = 0;
        for (PerBuyerConfiguration perBuyerConfiguration : perBuyerConfigurations) {
            sumOfBuyerTargetSize += perBuyerConfiguration.getTargetInputSizeBytes();
        }

        if (sumOfBuyerTargetSize <= mSellerMaxSizeBytes) {
            // copy per buyer configuration sizes into per buyer limits
            for (PerBuyerConfiguration perBuyerConfiguration : perBuyerConfigurations) {
                AdTechIdentifier buyerName = perBuyerConfiguration.getBuyer();
                int perBuyerLimit = perBuyerConfiguration.getTargetInputSizeBytes();
                perBuyerLimits.put(buyerName, perBuyerLimit);
            }
        } else {
            // allocate space across buyers proportionally
            for (PerBuyerConfiguration perBuyerConfiguration : perBuyerConfigurations) {
                AdTechIdentifier buyerName = perBuyerConfiguration.getBuyer();
                int updatedBuyerTarget =
                        (int)
                                (((double) perBuyerConfiguration.getTargetInputSizeBytes()
                                                / sumOfBuyerTargetSize)
                                        * mSellerMaxSizeBytes);
                perBuyerLimits.put(buyerName, updatedBuyerTarget);
            }
        }
        return perBuyerLimits;
    }

    /** Split up custom audiences with their buyers */
    private Map<AdTechIdentifier, List<DBCustomAudience>> buildAndSortPerBuyerDBCAs(
            List<DBCustomAudience> customAudiences) {
        // customAudiences is in descending order by priority
        Map<AdTechIdentifier, List<DBCustomAudience>> perBuyerDBCAs = new HashMap<>();

        for (AdTechIdentifier buyerName : mPerBuyerLimits.keySet()) {
            if (!perBuyerDBCAs.containsKey(buyerName)) {
                perBuyerDBCAs.put(buyerName, new ArrayList<>());
            }
        }

        for (DBCustomAudience dbCustomAudience : customAudiences) {
            AdTechIdentifier caBuyerName = dbCustomAudience.getBuyer();
            if (perBuyerDBCAs.containsKey(caBuyerName)) {
                // CAs are added in descending order per buyer by priority
                perBuyerDBCAs.get(caBuyerName).add(dbCustomAudience);
            }
        }

        for (List<DBCustomAudience> buyerDBCAs : perBuyerDBCAs.values()) {
            buyerDBCAs.sort(Comparator.comparingDouble(DBCustomAudience::getPriority).reversed());
        }
        return perBuyerDBCAs;
    }

    /**
     * Build list given remaining buyers, with structure [buyer1P1, buyer2P1, ..., buyerNP1,
     * buyer1P2 ... buyerNPN]
     */
    private List<DBCustomAudience> buildRemainingDBCAsList(
            Map<AdTechIdentifier, List<DBCustomAudience>> remainingDBCAsPerBuyer) {

        List<DBCustomAudience> remainingDBCAList = new ArrayList<>();
        int currentIndex = 0;
        boolean customAudiencesStillRemain = true;

        // loop through all CAs for all buyers, add a CA from each buyer 1 by 1
        while (customAudiencesStillRemain) {
            customAudiencesStillRemain =
                    false; // set back to true if there are CAs that still need to be added
            for (Map.Entry<AdTechIdentifier, List<DBCustomAudience>> entry :
                    remainingDBCAsPerBuyer.entrySet()) {
                List<DBCustomAudience> dbCustomAudienceList = entry.getValue();
                if (currentIndex < dbCustomAudienceList.size()) {
                    remainingDBCAList.add(dbCustomAudienceList.get(currentIndex));
                    customAudiencesStillRemain = true;
                }
            }
            currentIndex += 1;
        }
        return remainingDBCAList;
    }

    /**
     * <b>{@code sellerMaxSizeBytes}</b> minus <b>{@code currentSizeOfPayload}</b> gives us a
     * remaining size. <br>
     * <br>
     * With this remaining size, we will add more CAs to the payload in a greedy fashion until we
     * hit a target goal: (sellerMaxSizeBytes * PAYLOAD_UTILIZATION_GOAL)
     */
    private void utilizeRemainingPayloadSpace(
            List<DBCustomAudience> remainingDBCAs,
            Map<AdTechIdentifier, BiddingAuctionServers.BuyerInput.Builder> buyerInputs,
            Map<
                            AdTechIdentifier,
                            List<
                                    Pair<
                                            DBCustomAudience,
                                            BiddingAuctionServers.BuyerInput.CustomAudience>>>
                    customAudiencesInPayload,
            Map<AdTechIdentifier, Double> rateOfCompressionPerBuyer,
            int currentSizeOfPayload,
            int sellerMaxSizeBytes) {

        int totalSize = currentSizeOfPayload; // avoid reassigning parameter
        int goalSize = (int) (sellerMaxSizeBytes * PAYLOAD_UTILIZATION_GOAL);

        for (DBCustomAudience dbCustomAudience : remainingDBCAs) {
            AdTechIdentifier buyerName = dbCustomAudience.getBuyer();

            BiddingAuctionServers.BuyerInput.CustomAudience customAudienceProto =
                    mCompressedBuyerInputCreatorHelper.buildCustomAudienceProtoFrom(
                            dbCustomAudience);

            int estimatedSizeOfCA =
                    (int)
                                    ((double) getUncompressedSize(customAudienceProto)
                                            * rateOfCompressionPerBuyer.get(buyerName))
                            + BYTE_CEILING;

            // Check if we are under the goal size
            if (estimatedSizeOfCA + totalSize < goalSize) {
                totalSize += estimatedSizeOfCA;
                buyerInputs.get(buyerName).addCustomAudiences(customAudienceProto);
                customAudiencesInPayload
                        .get(buyerName)
                        .add(Pair.create(dbCustomAudience, customAudienceProto));
            } else if (totalSize >= goalSize - MINIMUM_SIZE_OF_CA_BYTES) {
                // to reduce the amount of unnecessary checks, incorporate the min size of a CA here
                break; // we hit goal size, so we break the loop
            } else {
                continue; // current CA does not get added to the payload, move onto next CA
            }
        }
    }

    /** A truncate function runs if the total payload size is greater than the seller max size */
    private void truncateCAs(
            Map<AdTechIdentifier, BiddingAuctionServers.BuyerInput.Builder> buyerInputs,
            Map<
                            AdTechIdentifier,
                            List<
                                    Pair<
                                            DBCustomAudience,
                                            BiddingAuctionServers.BuyerInput.CustomAudience>>>
                    customAudiencesInPayload,
            int overflowSize,
            Map<AdTechIdentifier, Double> rateOfCompressionPerBuyer) {
        /* To ensure that enough bytes are removed and the payload utilization goal is targeted,
        the overflow size is overestimated by adding the product of the sellerMaxBytes and the
        complement of the payload utilization goal (1 - payload utilization goal)

        So if the overflow bytes were 50, and the seller max size is 1000, and the utilization
        goal is 0.9 then the amount of bytes that will be removed is: 50 + (1000 * (0.1)) = 150

        Again, +1 to allocate for truncation of casting double to int */
        int currentOverflow =
                overflowSize
                        + (int) (mSellerMaxSizeBytes * (1 - PAYLOAD_UTILIZATION_GOAL))
                        + BYTE_CEILING;

        while (currentOverflow > 0) {
            for (Map.Entry<AdTechIdentifier, BiddingAuctionServers.BuyerInput.Builder> entry :
                    buyerInputs.entrySet()) {
                if (currentOverflow <= 0) {
                    // no more overflow space, we break and end loop
                    break;
                } else {
                    AdTechIdentifier buyerName = entry.getKey();
                    // index = last element in list, since that element has the lowest priority
                    int index = buyerInputs.get(buyerName).getCustomAudiencesList().size() - 1;
                    // lets ensure that every buyer has a custom audience
                    // TODO (b/359593515) handle case where every buyer has one custom audience
                    if (index > 0) {
                        BiddingAuctionServers.BuyerInput.CustomAudience customAudienceProto =
                                buyerInputs.get(buyerName).getCustomAudiences(index);
                        buyerInputs.get(buyerName).removeCustomAudiences(index);

                        // since CA isn't included in the payload, remove last added CA from stat
                        // tracking
                        customAudiencesInPayload
                                .get(buyerName)
                                .remove(customAudiencesInPayload.get(buyerName).size() - 1);

                        // we subtract estimated size of the CA that was removed from the overflow
                        currentOverflow -=
                                (int)
                                                (getUncompressedSize(customAudienceProto)
                                                        * rateOfCompressionPerBuyer.get(buyerName))
                                        + BYTE_CEILING;
                    }
                }
            }
        }
    }

    /** Function that returns stats for all CAs in the payload */
    private Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> generatePerBuyerStats(
            Map<
                            AdTechIdentifier,
                            List<
                                    Pair<
                                            DBCustomAudience,
                                            BiddingAuctionServers.BuyerInput.CustomAudience>>>
                    customAudiencesInPayload) {
        Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> perBuyerStats = new HashMap<>();

        for (Map.Entry<
                        AdTechIdentifier,
                        List<
                                Pair<
                                        DBCustomAudience,
                                        BiddingAuctionServers.BuyerInput.CustomAudience>>>
                entry : customAudiencesInPayload.entrySet()) {
            List<Pair<DBCustomAudience, BiddingAuctionServers.BuyerInput.CustomAudience>>
                    customAudiences = entry.getValue();
            for (Pair<DBCustomAudience, BiddingAuctionServers.BuyerInput.CustomAudience>
                    customAudiencePair : customAudiences) {
                BiddingAuctionServers.BuyerInput.CustomAudience customAudienceProto =
                        customAudiencePair.second;
                DBCustomAudience dbCustomAudience = customAudiencePair.first;
                mCompressedBuyerInputCreatorHelper.addToBuyerIntermediateStats(
                        perBuyerStats, dbCustomAudience, customAudienceProto);
            }
        }
        return perBuyerStats;
    }

    private int getUncompressedSize(
            BiddingAuctionServers.BuyerInput.CustomAudience customAudience) {
        return customAudience.toByteArray().length;
    }

    private Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressBuyerInputs(
            Map<AdTechIdentifier, BiddingAuctionServers.BuyerInput.Builder> buyerInputs) {
        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedDataMap =
                new HashMap<>();

        for (Map.Entry<AdTechIdentifier, BiddingAuctionServers.BuyerInput.Builder> entry :
                buyerInputs.entrySet()) {
            AdTechIdentifier buyerName = entry.getKey();
            BiddingAuctionServers.BuyerInput.Builder buyerInput = entry.getValue();
            compressedDataMap.put(
                    buyerName,
                    mDataCompressor.compress(
                            AuctionServerDataCompressor.UncompressedData.create(
                                    buyerInput.build().toByteArray())));
        }
        return compressedDataMap;
    }

    private int getCurrentSize(
            Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedDataMap) {
        int total = 0;
        for (AuctionServerDataCompressor.CompressedData compressedData :
                compressedDataMap.values()) {
            total += compressedData.getData().length;
        }
        return total;
    }
}
