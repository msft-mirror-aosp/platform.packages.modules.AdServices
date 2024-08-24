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
import static android.adservices.adselection.AdSelectionConfigFixture.BUYER_2;
import static android.adservices.adselection.AdSelectionConfigFixture.BUYER_3;
import static android.adservices.common.CommonFixture.createNAmountOfBuyers;
import static android.adservices.common.CommonFixture.getAlphaNumericString;

import static com.android.adservices.service.Flags.FLEDGE_AUCTION_SERVER_COMPRESSION_ALGORITHM_VERSION;
import static com.android.adservices.service.Flags.PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES;

import android.adservices.adselection.PerBuyerConfiguration;
import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdTechIdentifier;
import android.util.Pair;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.data.signals.DBEncodedPayloadFixture;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class CompressedBuyerInputCreatorPerBuyerLimitsGreedyImplTest
        extends AdServicesExtendedMockitoTestCase {
    private CompressedBuyerInputCreator mCompressedBuyerInputCreator;
    private AuctionServerDataCompressor mAuctionServerDataCompressor;
    private CompressedBuyerInputCreatorHelper mCompressedBuyerInputCreatorHelper;
    private PayloadOptimizationContext mPayloadOptimizationContext;
    @Mock private AuctionServerPayloadMetricsStrategy mAuctionServerPayloadMetricsStrategyMock;
    private static final boolean OMIT_ADS_DISABLED = false;
    private static final boolean PAS_METRICS_DISABLED = false;
    private static final int KILOBYTES_TO_BYTES = 1024;
    private static final int[] PER_BUYER_LIMIT = {
        8 * KILOBYTES_TO_BYTES, 12 * KILOBYTES_TO_BYTES, 6 * KILOBYTES_TO_BYTES
    };
    // Buyer 1 = 8KB, Buyer 2 = 12KB, Buyer 3 = 6KB
    private static final int SELLER_MAX_SIZE = 20 * KILOBYTES_TO_BYTES;

    /* A random seed value that is used ensure reproducibility when dealing with random numbers
     * Allows for extensive testing with different use cases
     * Seed Numbers that I (@enochchigbu) used for testing:
     * 123456, 234567, 345678, 999999, 111111, 0, 100, 200, 300
     * 128892, 93813, 47923, 381, 8021, 298381, 479, 4792, 1829
     */
    private static final long RANDOM_SEED = 123456;

    // Change the constant values for size variability with ads per CAs, and CAs per buyer
    private static final int MAXIMUM_ADS_PER_CA = 100;
    private static final int MAXIMUM_CA_PER_BUYER = 500;

    private static final PerBuyerConfiguration PER_BUYER_CONFIG_BUYER_1 =
            new PerBuyerConfiguration.Builder()
                    .setBuyer(BUYER_1)
                    .setTargetInputSizeBytes(PER_BUYER_LIMIT[0])
                    .build();

    private static final PerBuyerConfiguration PER_BUYER_CONFIG_BUYER_2 =
            new PerBuyerConfiguration.Builder()
                    .setBuyer(BUYER_2)
                    .setTargetInputSizeBytes(PER_BUYER_LIMIT[1])
                    .build();

    private static final PerBuyerConfiguration PER_BUYER_CONFIG_BUYER_3 =
            new PerBuyerConfiguration.Builder()
                    .setBuyer(BUYER_3)
                    .setTargetInputSizeBytes(PER_BUYER_LIMIT[2])
                    .build();

    private static final Set<PerBuyerConfiguration> PER_BUYER_CONFIGURATIONS =
            ImmutableSet.of(
                    PER_BUYER_CONFIG_BUYER_1, PER_BUYER_CONFIG_BUYER_2, PER_BUYER_CONFIG_BUYER_3);

    private static final Map<String, AdTechIdentifier> THREE_BUYERS_EQUAL_CA_MAP =
            Map.of(
                    "Shoes CA of Buyer 1", BUYER_1,
                    "Shirts CA of Buyer 1", BUYER_1,
                    "Shoes CA Of Buyer 2", BUYER_2,
                    "Shirts CA of Buyer 2", BUYER_2,
                    "Shirts CA Of Buyer 3", BUYER_3,
                    "Shirts CA of Buyer 3", BUYER_3);

    private static final Map<String, AdTechIdentifier> THREE_BUYERS_BUYER_ONE_MOST_CA_MAP =
            Map.of(
                    "Shoes CA of Buyer 1", BUYER_1,
                    "Shirts CA of Buyer 1", BUYER_1,
                    "Pants CA of Buyer 1", BUYER_1,
                    "Socks CA of Buyer 1", BUYER_1,
                    "Shoes CA Of Buyer 2", BUYER_2,
                    "Shirts CA Of Buyer 3", BUYER_3);
    private static final List<AdTechIdentifier> THREE_BUYERS_LIST =
            ImmutableList.of(BUYER_1, BUYER_2, BUYER_3);

    @Before
    public void setup() throws Exception {
        mAuctionServerDataCompressor =
                AuctionServerDataCompressorFactory.getDataCompressor(
                        FLEDGE_AUCTION_SERVER_COMPRESSION_ALGORITHM_VERSION);

        mCompressedBuyerInputCreatorHelper =
                new CompressedBuyerInputCreatorHelper(
                        mAuctionServerPayloadMetricsStrategyMock,
                        PAS_METRICS_DISABLED,
                        OMIT_ADS_DISABLED);

        mPayloadOptimizationContext =
                PayloadOptimizationContext.builder()
                        .setPerBuyerConfigurations(PER_BUYER_CONFIGURATIONS)
                        .setMaxBuyerInputSizeBytes(SELLER_MAX_SIZE)
                        .setOptimizationsEnabled(true)
                        .build();
        mCompressedBuyerInputCreator =
                new CompressedBuyerInputCreatorPerBuyerLimitsGreedyImpl(
                        mCompressedBuyerInputCreatorHelper,
                        mAuctionServerDataCompressor,
                        mPayloadOptimizationContext,
                        PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES);
    }

    @Test
    @SuppressWarnings("ReturnValueIgnored")
    public void compressedBuyerInputsProportionalCAsReturnsCorrectly() throws Exception {
        List<AdTechIdentifier> buyersList = THREE_BUYERS_LIST;
        Map<String, AdTechIdentifier> nameAndBuyersMap = THREE_BUYERS_EQUAL_CA_MAP;

        Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap =
                generateExpectedEncodedPayloadForBuyers(buyersList);
        Pair<List<DBCustomAudience>, Map<String, DBCustomAudience>> resultPair =
                createDBCustomAudiences(nameAndBuyersMap);

        List<DBCustomAudience> dbCustomAudienceList = resultPair.first;
        Map<String, DBCustomAudience> namesToCustomAudience = resultPair.second;

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedDataMap =
                mCompressedBuyerInputCreator.generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                        dbCustomAudienceList, encodedPayloadMap);

        expect.that(compressedDataMap).isNotNull();

        int totalSize = getTotalSize(compressedDataMap);
        expect.that(totalSize).isAtMost(SELLER_MAX_SIZE);

        for (AdTechIdentifier buyer : buyersList) {
            BiddingAuctionServers.BuyerInput buyerInput =
                    BiddingAuctionServers.BuyerInput.parseFrom(
                            mAuctionServerDataCompressor
                                    .decompress(compressedDataMap.get(buyer))
                                    .getData());

            for (BiddingAuctionServers.BuyerInput.CustomAudience buyerInputsCA :
                    buyerInput.getCustomAudiencesList()) {
                String buyerInputsCAName = buyerInputsCA.getName();
                expect.that(nameAndBuyersMap).containsKey(buyerInputsCAName);
                DBCustomAudience deviceCA = namesToCustomAudience.get(buyerInputsCAName);
                expect.that(deviceCA.getName()).isEqualTo(buyerInputsCAName);
                expect.that(deviceCA.getBuyer()).isEqualTo(buyer);
                assertCAsEqual(buyerInputsCA, deviceCA, true);
            }

            BiddingAuctionServers.ProtectedAppSignals appSignals =
                    buyerInput.getProtectedAppSignals();
            expect.that(appSignals.getEncodingVersion())
                    .isEqualTo(encodedPayloadMap.get(buyer).getVersion());
            expect.that(appSignals.getAppInstallSignals())
                    .isEqualTo(
                            ByteString.copyFrom(encodedPayloadMap.get(buyer).getEncodedPayload()));
        }
    }

    @Test
    @SuppressWarnings("ReturnValueIgnored")
    public void compressedBuyerInputsNonProportionalCAsReturnsCorrectly() throws Exception {
        List<AdTechIdentifier> buyersList = THREE_BUYERS_LIST;
        Map<String, AdTechIdentifier> nameAndBuyersMap = THREE_BUYERS_BUYER_ONE_MOST_CA_MAP;

        Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap =
                generateExpectedEncodedPayloadForBuyers(buyersList);
        Pair<List<DBCustomAudience>, Map<String, DBCustomAudience>> resultPair =
                createDBCustomAudiences(nameAndBuyersMap);

        List<DBCustomAudience> dbCustomAudienceList = resultPair.first;
        Map<String, DBCustomAudience> namesToCustomAudience = resultPair.second;

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedDataMap =
                mCompressedBuyerInputCreator.generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                        dbCustomAudienceList, encodedPayloadMap);

        expect.that(compressedDataMap).isNotNull();

        int totalSize = getTotalSize(compressedDataMap);
        expect.that(totalSize).isAtMost(SELLER_MAX_SIZE);

        for (AdTechIdentifier buyer : buyersList) {
            BiddingAuctionServers.BuyerInput buyerInput =
                    BiddingAuctionServers.BuyerInput.parseFrom(
                            mAuctionServerDataCompressor
                                    .decompress(compressedDataMap.get(buyer))
                                    .getData());

            for (BiddingAuctionServers.BuyerInput.CustomAudience buyerInputsCA :
                    buyerInput.getCustomAudiencesList()) {
                String buyerInputsCAName = buyerInputsCA.getName();
                expect.that(nameAndBuyersMap).containsKey(buyerInputsCAName);
                DBCustomAudience deviceCA = namesToCustomAudience.get(buyerInputsCAName);
                expect.that(deviceCA.getName()).isEqualTo(buyerInputsCAName);
                expect.that(deviceCA.getBuyer()).isEqualTo(buyer);
                assertCAsEqual(buyerInputsCA, deviceCA, true);
            }

            BiddingAuctionServers.ProtectedAppSignals appSignals =
                    buyerInput.getProtectedAppSignals();
            expect.that(appSignals.getEncodingVersion())
                    .isEqualTo(encodedPayloadMap.get(buyer).getVersion());
            expect.that(appSignals.getAppInstallSignals())
                    .isEqualTo(
                            ByteString.copyFrom(encodedPayloadMap.get(buyer).getEncodedPayload()));
        }
    }

    @Test
    @SuppressWarnings("ReturnValueIgnored")
    public void compressedBuyerInputsRespectPerBuyerLimits() throws Exception {
        List<AdTechIdentifier> buyerList = THREE_BUYERS_LIST;
        List<DBCustomAudience> dbCustomAudiences = createBulkDBCustomAudiences(buyerList, 5000);

        Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap =
                generateExpectedEncodedPayloadForBuyers(buyerList);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedDataMap =
                mCompressedBuyerInputCreator.generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                        dbCustomAudiences, encodedPayloadMap);

        for (int i = 0; i < buyerList.size(); i++) {
            int perBuyerSize = compressedDataMap.get(buyerList.get(i)).getData().length;
            expect.that(perBuyerSize).isAtMost(PER_BUYER_LIMIT[i]);
        }

        for (AdTechIdentifier buyer : buyerList) {
            BiddingAuctionServers.BuyerInput buyerInput =
                    BiddingAuctionServers.BuyerInput.parseFrom(
                            mAuctionServerDataCompressor
                                    .decompress(compressedDataMap.get(buyer))
                                    .getData());

            expect.that(buyerInput.getCustomAudiencesList()).isNotEmpty();

            BiddingAuctionServers.ProtectedAppSignals appSignals =
                    buyerInput.getProtectedAppSignals();
            expect.that(appSignals.getEncodingVersion())
                    .isEqualTo(encodedPayloadMap.get(buyer).getVersion());
            expect.that(appSignals.getAppInstallSignals())
                    .isEqualTo(
                            ByteString.copyFrom(encodedPayloadMap.get(buyer).getEncodedPayload()));
            expect.that(getTotalSize(compressedDataMap)).isLessThan(SELLER_MAX_SIZE);
        }
    }

    @Test
    @SuppressWarnings("ReturnValueIgnored")
    public void compressedBuyerInputsReturnsEmptyPayloadIfSellerMaxSizeIsZero() throws Exception {
        int sellerMaxSize = 0;
        List<AdTechIdentifier> buyerList = THREE_BUYERS_LIST;
        Map<String, AdTechIdentifier> customAudienceMap = THREE_BUYERS_EQUAL_CA_MAP;

        Set<PerBuyerConfiguration> perBuyerConfigurations = new HashSet<>();

        for (AdTechIdentifier buyerName : buyerList) {
            perBuyerConfigurations.add(
                    new PerBuyerConfiguration.Builder()
                            .setBuyer(buyerName)
                            .setTargetInputSizeBytes(sellerMaxSize)
                            .build());
        }

        PayloadOptimizationContext payloadOptimizationContext =
                PayloadOptimizationContext.builder()
                        .setPerBuyerConfigurations(perBuyerConfigurations)
                        .setMaxBuyerInputSizeBytes(sellerMaxSize)
                        .setOptimizationsEnabled(true)
                        .build();

        CompressedBuyerInputCreator compressedBuyerInputCreator =
                new CompressedBuyerInputCreatorPerBuyerLimitsGreedyImpl(
                        mCompressedBuyerInputCreatorHelper,
                        mAuctionServerDataCompressor,
                        payloadOptimizationContext,
                        PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES);

        Pair<List<DBCustomAudience>, Map<String, DBCustomAudience>> resultPair =
                createDBCustomAudiences(customAudienceMap);

        List<DBCustomAudience> dbCustomAudienceList = resultPair.first;

        Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap =
                generateExpectedEncodedPayloadForBuyers(buyerList);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedDataMap =
                compressedBuyerInputCreator.generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                        dbCustomAudienceList, encodedPayloadMap);

        int totalSize = getTotalSize(compressedDataMap);
        expect.that(sellerMaxSize).isEqualTo(totalSize);
    }

    @Test
    @SuppressWarnings("ReturnValueIgnored")
    public void compressedBuyerInputsHasNoPASIfPerBuyerLimitLessThanPasMaxSize() throws Exception {
        int perBuyerPasSize = 5 * KILOBYTES_TO_BYTES;
        int sellerMaxSize = 3 * KILOBYTES_TO_BYTES;

        List<AdTechIdentifier> buyerList = THREE_BUYERS_LIST;
        Map<String, AdTechIdentifier> customAudienceMap = THREE_BUYERS_EQUAL_CA_MAP;

        Set<PerBuyerConfiguration> perBuyerConfigurations = new HashSet<>();

        for (AdTechIdentifier buyerName : buyerList) {
            perBuyerConfigurations.add(
                    new PerBuyerConfiguration.Builder()
                            .setBuyer(buyerName)
                            .setTargetInputSizeBytes(KILOBYTES_TO_BYTES) // 1 KB
                            .build());
        }

        PayloadOptimizationContext payloadOptimizationContext =
                PayloadOptimizationContext.builder()
                        .setPerBuyerConfigurations(perBuyerConfigurations)
                        .setMaxBuyerInputSizeBytes(sellerMaxSize)
                        .setOptimizationsEnabled(true)
                        .build();

        CompressedBuyerInputCreator compressedBuyerInputCreator =
                new CompressedBuyerInputCreatorPerBuyerLimitsGreedyImpl(
                        mCompressedBuyerInputCreatorHelper,
                        mAuctionServerDataCompressor,
                        payloadOptimizationContext,
                        perBuyerPasSize);

        Pair<List<DBCustomAudience>, Map<String, DBCustomAudience>> resultPair =
                createDBCustomAudiences(customAudienceMap);

        List<DBCustomAudience> dbCustomAudienceList = resultPair.first;

        Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap =
                generateExpectedEncodedPayloadForBuyers(buyerList);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedDataMap =
                compressedBuyerInputCreator.generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                        dbCustomAudienceList, encodedPayloadMap);

        for (AdTechIdentifier buyer : buyerList) {
            BiddingAuctionServers.BuyerInput buyerInput =
                    BiddingAuctionServers.BuyerInput.parseFrom(
                            mAuctionServerDataCompressor
                                    .decompress(compressedDataMap.get(buyer))
                                    .getData());

            BiddingAuctionServers.ProtectedAppSignals appSignals =
                    buyerInput.getProtectedAppSignals();

            expect.that(appSignals.getSerializedSize()).isEqualTo(0);
        }
    }

    @Test
    @SuppressWarnings("ReturnValueIgnored")
    public void compressedBuyerInputsBuyerHasPASIfPerBuyerLimitMoreThanPasMaxSize()
            throws Exception {
        int perBuyerPasSize = 2 * KILOBYTES_TO_BYTES; // 2 KB Pas Size
        int sellerMaxSize = 10 * KILOBYTES_TO_BYTES;

        List<AdTechIdentifier> buyerList = THREE_BUYERS_LIST;
        Map<String, AdTechIdentifier> customAudienceMap = THREE_BUYERS_EQUAL_CA_MAP;

        Set<PerBuyerConfiguration> perBuyerConfigurations = new HashSet<>();

        perBuyerConfigurations.add(
                new PerBuyerConfiguration.Builder()
                        .setBuyer(buyerList.get(0))
                        .setTargetInputSizeBytes(KILOBYTES_TO_BYTES) // 1 KB
                        .build());

        perBuyerConfigurations.add(
                new PerBuyerConfiguration.Builder()
                        .setBuyer(buyerList.get(1))
                        .setTargetInputSizeBytes(3 * KILOBYTES_TO_BYTES) // 3 KB
                        .build());

        perBuyerConfigurations.add(
                new PerBuyerConfiguration.Builder()
                        .setBuyer(buyerList.get(2))
                        .setTargetInputSizeBytes(6 * KILOBYTES_TO_BYTES) // 5 KB
                        .build());

        PayloadOptimizationContext payloadOptimizationContext =
                PayloadOptimizationContext.builder()
                        .setPerBuyerConfigurations(perBuyerConfigurations)
                        .setMaxBuyerInputSizeBytes(sellerMaxSize)
                        .setOptimizationsEnabled(true)
                        .build();

        CompressedBuyerInputCreator compressedBuyerInputCreator =
                new CompressedBuyerInputCreatorPerBuyerLimitsGreedyImpl(
                        mCompressedBuyerInputCreatorHelper,
                        mAuctionServerDataCompressor,
                        payloadOptimizationContext,
                        perBuyerPasSize);

        Pair<List<DBCustomAudience>, Map<String, DBCustomAudience>> resultPair =
                createDBCustomAudiences(customAudienceMap);

        List<DBCustomAudience> dbCustomAudienceList = resultPair.first;

        Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap =
                generateExpectedEncodedPayloadForBuyers(buyerList);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedDataMap =
                compressedBuyerInputCreator.generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                        dbCustomAudienceList, encodedPayloadMap);

        for (int i = 0; i < buyerList.size(); i++) {
            BiddingAuctionServers.BuyerInput buyerInput =
                    BiddingAuctionServers.BuyerInput.parseFrom(
                            mAuctionServerDataCompressor
                                    .decompress(compressedDataMap.get(buyerList.get(i)))
                                    .getData());

            BiddingAuctionServers.ProtectedAppSignals appSignals =
                    buyerInput.getProtectedAppSignals();

            // buyerList[0] should have no PAS
            // buyerList[1] and buyerList[2] should have PAS
            if (i == 0) {
                expect.that(appSignals.getSerializedSize()).isEqualTo(0);
            } else if (i == 1 || i == 2) {
                expect.that(appSignals.getSerializedSize()).isGreaterThan(0);
            }
        }
    }

    @Test
    @SuppressWarnings("ReturnValueIgnored")
    public void compressedBuyerInputsLargeInputRespectsSmallSellerMaxSize() throws Exception {
        int smallerMaxSize = 1 * KILOBYTES_TO_BYTES;

        PerBuyerConfiguration perBuyerConfigBuyer1 =
                new PerBuyerConfiguration.Builder()
                        .setBuyer(BUYER_1)
                        .setTargetInputSizeBytes(KILOBYTES_TO_BYTES * 4)
                        .build();

        PerBuyerConfiguration perBuyerConfigBuyer2 =
                new PerBuyerConfiguration.Builder()
                        .setBuyer(BUYER_2)
                        .setTargetInputSizeBytes(KILOBYTES_TO_BYTES * 10)
                        .build();

        PerBuyerConfiguration perBuyerConfigBuyer3 =
                new PerBuyerConfiguration.Builder()
                        .setBuyer(BUYER_3)
                        .setTargetInputSizeBytes(KILOBYTES_TO_BYTES * 1)
                        .build();

        Set<PerBuyerConfiguration> perBuyerConfigurationList = new HashSet<>();
        perBuyerConfigurationList.add(perBuyerConfigBuyer1);
        perBuyerConfigurationList.add(perBuyerConfigBuyer2);
        perBuyerConfigurationList.add(perBuyerConfigBuyer3);

        PayloadOptimizationContext payloadOptimizationContext =
                PayloadOptimizationContext.builder()
                        .setPerBuyerConfigurations(perBuyerConfigurationList)
                        .setMaxBuyerInputSizeBytes(smallerMaxSize)
                        .setOptimizationsEnabled(true)
                        .build();

        CompressedBuyerInputCreator compressedBuyerInputCreator =
                new CompressedBuyerInputCreatorPerBuyerLimitsGreedyImpl(
                        mCompressedBuyerInputCreatorHelper,
                        mAuctionServerDataCompressor,
                        payloadOptimizationContext,
                        PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES);

        List<DBCustomAudience> dbCustomAudienceList =
                createBulkDBCustomAudiences(THREE_BUYERS_LIST, 7500);

        Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap =
                generateExpectedEncodedPayloadForBuyers(THREE_BUYERS_LIST);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedDataMap =
                compressedBuyerInputCreator.generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                        dbCustomAudienceList, encodedPayloadMap);

        expect.that(getTotalSize(compressedDataMap)).isAtMost(smallerMaxSize);
        expect.that(getTotalSize(compressedDataMap)).isGreaterThan((int) (smallerMaxSize * 0.8));
    }

    @Test
    @SuppressWarnings("ReturnValueIgnored")
    public void compressedBuyerInputsOnlyReturnsPAS() throws Exception {
        List<AdTechIdentifier> buyersList = THREE_BUYERS_LIST;
        Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap =
                generateExpectedEncodedPayloadForBuyers(buyersList);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedDataMap =
                mCompressedBuyerInputCreator.generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                        ImmutableList.of(), encodedPayloadMap);

        for (AdTechIdentifier buyer : buyersList) {
            BiddingAuctionServers.BuyerInput buyerInput =
                    BiddingAuctionServers.BuyerInput.parseFrom(
                            mAuctionServerDataCompressor
                                    .decompress(compressedDataMap.get(buyer))
                                    .getData());

            expect.that(buyerInput.getCustomAudiencesList()).isEmpty();

            BiddingAuctionServers.ProtectedAppSignals appSignals =
                    buyerInput.getProtectedAppSignals();
            expect.that(appSignals.getEncodingVersion())
                    .isEqualTo(encodedPayloadMap.get(buyer).getVersion());
            expect.that(appSignals.getAppInstallSignals())
                    .isEqualTo(
                            ByteString.copyFrom(encodedPayloadMap.get(buyer).getEncodedPayload()));
        }
    }

    @Test
    @SuppressWarnings("ReturnValueIgnored")
    public void compressedBuyerInputsOnlyReturnsPA() throws Exception {
        List<AdTechIdentifier> buyersList = THREE_BUYERS_LIST;
        Map<String, AdTechIdentifier> nameAndBuyersMap = THREE_BUYERS_EQUAL_CA_MAP;

        Pair<List<DBCustomAudience>, Map<String, DBCustomAudience>> resultPair =
                createDBCustomAudiences(nameAndBuyersMap);

        List<DBCustomAudience> dbCustomAudienceList = resultPair.first;
        Map<String, DBCustomAudience> namesToCustomAudience = resultPair.second;

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedDataMap =
                mCompressedBuyerInputCreator.generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                        dbCustomAudienceList, ImmutableMap.of());

        int totalNumCAsInBuyerInput = 0;

        for (AdTechIdentifier buyer : buyersList) {
            BiddingAuctionServers.BuyerInput buyerInput =
                    BiddingAuctionServers.BuyerInput.parseFrom(
                            mAuctionServerDataCompressor
                                    .decompress(compressedDataMap.get(buyer))
                                    .getData());

            for (BiddingAuctionServers.BuyerInput.CustomAudience buyerInputsCA :
                    buyerInput.getCustomAudiencesList()) {
                String buyerInputsCAName = buyerInputsCA.getName();
                expect.that(nameAndBuyersMap).containsKey(buyerInputsCAName);
                DBCustomAudience deviceCA = namesToCustomAudience.get(buyerInputsCAName);
                expect.that(deviceCA.getName()).isEqualTo(buyerInputsCAName);
                expect.that(deviceCA.getBuyer()).isEqualTo(buyer);
                assertCAsEqual(buyerInputsCA, deviceCA, true);
                totalNumCAsInBuyerInput++;
            }

            BiddingAuctionServers.ProtectedAppSignals appSignals =
                    buyerInput.getProtectedAppSignals();
            expect.that(appSignals.getSerializedSize()).isEqualTo(0);
        }
        expect.that(totalNumCAsInBuyerInput).isEqualTo(dbCustomAudienceList.size());
    }

    // the test cases below use seed values to generate randomized reproducible tests
    @Test
    @SuppressWarnings("ReturnValueIgnored")
    public void compressedBuyerInputsSmallCaseIsValid() throws Exception {
        // Small case involves 5 buyers
        int sellerMaxSize = 5 * KILOBYTES_TO_BYTES;
        List<AdTechIdentifier> buyers = createNAmountOfBuyers(5);
        List<DBCustomAudience> dbCustomAudiences =
                createBulkDBCustomAudiencesWithVarianceInSize(buyers, MAXIMUM_CA_PER_BUYER);
        Set<PerBuyerConfiguration> perBuyerConfigurations =
                createNPerBuyerConfigurationsWithRandomTargetSize(buyers, sellerMaxSize);
        Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap =
                generateExpectedEncodedPayloadForBuyers(buyers);

        PayloadOptimizationContext payloadOptimizationContext =
                PayloadOptimizationContext.builder()
                        .setPerBuyerConfigurations(perBuyerConfigurations)
                        .setMaxBuyerInputSizeBytes(sellerMaxSize)
                        .setOptimizationsEnabled(true)
                        .build();

        CompressedBuyerInputCreatorPerBuyerLimitsGreedyImpl compressedBuyerInputCreator =
                new CompressedBuyerInputCreatorPerBuyerLimitsGreedyImpl(
                        mCompressedBuyerInputCreatorHelper,
                        mAuctionServerDataCompressor,
                        payloadOptimizationContext,
                        PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedDataMap =
                compressedBuyerInputCreator.generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                        dbCustomAudiences, encodedPayloadMap);

        int totalSizeOfCompressedMap = getTotalSize(compressedDataMap);
        expect.that(totalSizeOfCompressedMap).isAtMost(sellerMaxSize);
    }

    @Test
    @SuppressWarnings("ReturnValueIgnored")
    public void compressedBuyerInputsAverageCaseIsValid() throws Exception {
        // Average case involves 10 buyer
        int sellerMaxSize = 20 * KILOBYTES_TO_BYTES;
        List<AdTechIdentifier> buyers = createNAmountOfBuyers(10);
        List<DBCustomAudience> dbCustomAudiences =
                createBulkDBCustomAudiencesWithVarianceInSize(buyers, MAXIMUM_CA_PER_BUYER);
        Set<PerBuyerConfiguration> perBuyerConfigurations =
                createNPerBuyerConfigurationsWithRandomTargetSize(buyers, sellerMaxSize);
        Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap =
                generateExpectedEncodedPayloadForBuyers(buyers);

        PayloadOptimizationContext payloadOptimizationContext =
                PayloadOptimizationContext.builder()
                        .setPerBuyerConfigurations(perBuyerConfigurations)
                        .setMaxBuyerInputSizeBytes(sellerMaxSize)
                        .setOptimizationsEnabled(true)
                        .build();

        CompressedBuyerInputCreatorPerBuyerLimitsGreedyImpl compressedBuyerInputCreator =
                new CompressedBuyerInputCreatorPerBuyerLimitsGreedyImpl(
                        mCompressedBuyerInputCreatorHelper,
                        mAuctionServerDataCompressor,
                        payloadOptimizationContext,
                        PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedDataMap =
                compressedBuyerInputCreator.generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                        dbCustomAudiences, encodedPayloadMap);

        int totalSizeOfCompressedMap = getTotalSize(compressedDataMap);

        expect.that(totalSizeOfCompressedMap).isAtMost(sellerMaxSize);
    }

    @Test
    @SuppressWarnings("ReturnValueIgnored")
    public void compressedBuyerInputsLargeCaseIsValid() throws Exception {
        // Large case involves 20 buyers
        int sellerMaxSize = 30 * KILOBYTES_TO_BYTES;
        List<AdTechIdentifier> buyers = createNAmountOfBuyers(20);
        List<DBCustomAudience> dbCustomAudiences =
                createBulkDBCustomAudiencesWithVarianceInSize(buyers, MAXIMUM_CA_PER_BUYER);
        Set<PerBuyerConfiguration> perBuyerConfigurations =
                createNPerBuyerConfigurationsWithRandomTargetSize(buyers, sellerMaxSize);
        Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap =
                generateExpectedEncodedPayloadForBuyers(buyers);

        PayloadOptimizationContext payloadOptimizationContext =
                PayloadOptimizationContext.builder()
                        .setPerBuyerConfigurations(perBuyerConfigurations)
                        .setMaxBuyerInputSizeBytes(sellerMaxSize)
                        .setOptimizationsEnabled(true)
                        .build();

        CompressedBuyerInputCreatorPerBuyerLimitsGreedyImpl compressedBuyerInputCreator =
                new CompressedBuyerInputCreatorPerBuyerLimitsGreedyImpl(
                        mCompressedBuyerInputCreatorHelper,
                        mAuctionServerDataCompressor,
                        payloadOptimizationContext,
                        PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedDataMap =
                compressedBuyerInputCreator.generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                        dbCustomAudiences, encodedPayloadMap);

        int totalSizeOfCompressedMap = getTotalSize(compressedDataMap);
        expect.that(totalSizeOfCompressedMap).isAtMost(sellerMaxSize);
    }

    @Test
    @SuppressWarnings("ReturnValueIgnored")
    public void compressedBuyerInputsMaxCaseIsValid() throws Exception {
        int sellerMaxSize = 64 * KILOBYTES_TO_BYTES;
        List<AdTechIdentifier> buyers = createNAmountOfBuyers(30);
        List<DBCustomAudience> dbCustomAudiences =
                createBulkDBCustomAudiencesWithVarianceInSize(buyers, MAXIMUM_CA_PER_BUYER);
        Set<PerBuyerConfiguration> perBuyerConfigurations =
                createNPerBuyerConfigurationsWithRandomTargetSize(buyers, sellerMaxSize);
        Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap =
                generateExpectedEncodedPayloadForBuyers(buyers);

        PayloadOptimizationContext payloadOptimizationContext =
                PayloadOptimizationContext.builder()
                        .setPerBuyerConfigurations(perBuyerConfigurations)
                        .setMaxBuyerInputSizeBytes(sellerMaxSize)
                        .setOptimizationsEnabled(true)
                        .build();

        CompressedBuyerInputCreatorPerBuyerLimitsGreedyImpl compressedBuyerInputCreator =
                new CompressedBuyerInputCreatorPerBuyerLimitsGreedyImpl(
                        mCompressedBuyerInputCreatorHelper,
                        mAuctionServerDataCompressor,
                        payloadOptimizationContext,
                        PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedDataMap =
                compressedBuyerInputCreator.generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                        dbCustomAudiences, encodedPayloadMap);

        int totalSizeOfCompressedMap = getTotalSize(compressedDataMap);
        expect.that(totalSizeOfCompressedMap).isAtMost(sellerMaxSize);
    }

    @Test
    @SuppressWarnings("ReturnValueIgnored")
    public void compressedBuyerInputsReturnsCorrectlyWithOmitAdsEnabled() throws Exception {
        // set omit ads to true
        CompressedBuyerInputCreatorHelper compressedBuyerInputCreatorHelper =
                new CompressedBuyerInputCreatorHelper(
                        mAuctionServerPayloadMetricsStrategyMock,
                        PAS_METRICS_DISABLED,
                        /* enableOmitAds= */ true);

        int sellerMaxSize = 5 * KILOBYTES_TO_BYTES;
        List<AdTechIdentifier> buyers = createNAmountOfBuyers(20); // since omit ads data is smaller
        // create custom audiences with server flag enabled
        List<DBCustomAudience> dbCustomAudiences =
                createBulkDBCustomAudiencesAuctionServerFlagEnabled(buyers, MAXIMUM_CA_PER_BUYER);
        Set<PerBuyerConfiguration> perBuyerConfigurations =
                createNPerBuyerConfigurationsWithRandomTargetSize(buyers, sellerMaxSize);
        Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap =
                generateExpectedEncodedPayloadForBuyers(buyers);

        PayloadOptimizationContext payloadOptimizationContext =
                PayloadOptimizationContext.builder()
                        .setPerBuyerConfigurations(perBuyerConfigurations)
                        .setMaxBuyerInputSizeBytes(sellerMaxSize)
                        .setOptimizationsEnabled(true)
                        .build();

        CompressedBuyerInputCreatorPerBuyerLimitsGreedyImpl compressedBuyerInputCreator =
                new CompressedBuyerInputCreatorPerBuyerLimitsGreedyImpl(
                        compressedBuyerInputCreatorHelper,
                        mAuctionServerDataCompressor,
                        payloadOptimizationContext,
                        PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedDataMap =
                compressedBuyerInputCreator.generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                        dbCustomAudiences, encodedPayloadMap);

        for (AdTechIdentifier buyer : buyers) {
            BiddingAuctionServers.BuyerInput buyerInput =
                    BiddingAuctionServers.BuyerInput.parseFrom(
                            mAuctionServerDataCompressor
                                    .decompress(compressedDataMap.get(buyer))
                                    .getData());
            for (BiddingAuctionServers.BuyerInput.CustomAudience buyerInputsCA :
                    buyerInput.getCustomAudiencesList()) {
                expect.that(buyerInputsCA.getAdRenderIdsList()).isEmpty();
            }
        }
        int totalSizeOfCompressedMap = getTotalSize(compressedDataMap);
        expect.that(totalSizeOfCompressedMap).isAtMost(sellerMaxSize);
    }

    // helper functions
    private int getTotalSize(
            Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedInputs) {
        int totalSize = 0;
        for (AuctionServerDataCompressor.CompressedData data : compressedInputs.values()) {
            totalSize += data.getData().length;
        }
        return totalSize;
    }

    private Map<AdTechIdentifier, DBEncodedPayload> generateExpectedEncodedPayloadForBuyers(
            List<AdTechIdentifier> buyers) {
        Map<AdTechIdentifier, DBEncodedPayload> map = new HashMap<>();
        for (AdTechIdentifier buyer : buyers) {
            DBEncodedPayload payload =
                    DBEncodedPayloadFixture.anEncodedPayloadBuilder(buyer).build();
            map.put(buyer, payload);
        }
        return map;
    }

    private Pair<List<DBCustomAudience>, Map<String, DBCustomAudience>> createDBCustomAudiences(
            Map<String, AdTechIdentifier> nameAndBuyers) {
        List<DBCustomAudience> customAudiences = new ArrayList<>();
        Map<String, DBCustomAudience> namesToCustomAudiences = new HashMap<>();
        for (Map.Entry<String, AdTechIdentifier> entry : nameAndBuyers.entrySet()) {
            AdTechIdentifier buyer = entry.getValue();
            String name = entry.getKey();
            // Give each DBCustomAudience a random priority between -100 and 100
            DBCustomAudience thisCustomAudience =
                    DBCustomAudienceFixture.getValidBuilderByBuyer(buyer, name)
                            .setPriority(Math.random() * 200 - 100)
                            .build();
            namesToCustomAudiences.put(name, thisCustomAudience);
            customAudiences.add(thisCustomAudience);
        }
        return Pair.create(customAudiences, namesToCustomAudiences);
    }

    private List<DBCustomAudience> createBulkDBCustomAudiences(
            List<AdTechIdentifier> buyers, int numCAsForBuyer) {
        // Generates a 20 code point string, using only the letters a-z
        List<DBCustomAudience> customAudiences = new ArrayList<>();
        for (AdTechIdentifier buyer : buyers) {
            for (int i = 0; i < numCAsForBuyer; i++) {
                DBCustomAudience thisCustomAudience =
                        DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                                        buyer, getAlphaNumericString(15))
                                .setPriority(Math.random() * 200 - 100)
                                .build();
                customAudiences.add(thisCustomAudience);
            }
        }
        return customAudiences;
    }

    private List<DBCustomAudience> createBulkDBCustomAudiencesAuctionServerFlagEnabled(
            List<AdTechIdentifier> buyers, int numCAsForBuyer) {
        // Generates a 20 code point string, using only the letters a-z
        List<DBCustomAudience> customAudiences = new ArrayList<>();
        for (AdTechIdentifier buyer : buyers) {
            for (int i = 0; i < numCAsForBuyer; i++) {
                DBCustomAudience thisCustomAudience =
                        DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                                        buyer, getAlphaNumericString(15))
                                .setPriority(Math.random() * 200 - 100)
                                .setAuctionServerRequestFlags(1)
                                .build();
                customAudiences.add(thisCustomAudience);
            }
        }
        return customAudiences;
    }

    /**
     * Similar to the function {@code createBulkDBCustomAudiences}, with a variance in CAs Per Buyer
     * Uses a seed for reproducibility & stable testing
     */
    private List<DBCustomAudience> createBulkDBCustomAudiencesWithVarianceInSize(
            List<AdTechIdentifier> buyers, int maxNumCAsForBuyer) {
        List<DBCustomAudience> customAudiences = new ArrayList<>();
        Random random = new Random(RANDOM_SEED);
        for (AdTechIdentifier buyer : buyers) {
            int numOfCAs = (int) (random.nextDouble() * maxNumCAsForBuyer);
            for (int i = 0; i < numOfCAs; i++) {
                DBCustomAudience thisCustomAudience =
                        DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                                        buyer, getAlphaNumericString(15))
                                .setPriority(random.nextDouble() * 200 - 100) // -100 to 100
                                .setAds(
                                        getNValidAdsWithAdRenderId(
                                                buyer,
                                                (int) (random.nextDouble() * MAXIMUM_ADS_PER_CA)))
                                .build();
                customAudiences.add(thisCustomAudience);
            }
        }
        return customAudiences;
    }

    private List<DBAdData> getNValidAdsWithAdRenderId(AdTechIdentifier buyer, int amountOfAds) {
        List<AdData> adDataList = new ArrayList<>();
        for (int i = 0; i < amountOfAds; i++) {
            adDataList.add(AdDataFixture.getValidFilterAdDataWithAdRenderIdByBuyer(buyer, i));
        }
        // convert to DBAdData, then make list and return
        return adDataList.stream()
                .map(DBAdDataFixture::convertAdDataToDBAdData)
                .collect(Collectors.toList());
    }

    private Set<PerBuyerConfiguration> createNPerBuyerConfigurationsWithRandomTargetSize(
            List<AdTechIdentifier> buyers, int sellerMaxSize) {
        Set<PerBuyerConfiguration> perBuyerConfigurations = new HashSet<>();
        // +1 to differ between last random seed object created
        Random random = new Random(RANDOM_SEED + 1);
        for (AdTechIdentifier buyer : buyers) {
            perBuyerConfigurations.add(
                    new PerBuyerConfiguration.Builder()
                            .setBuyer(buyer)
                            .setTargetInputSizeBytes((int) (random.nextDouble() * sellerMaxSize))
                            .build());
        }
        return perBuyerConfigurations;
    }

    /**
     * Asserts if a {@link BuyerInput.CustomAudience} and {@link DBCustomAudience} objects are
     * equal.
     */
    private void assertCAsEqual(
            BiddingAuctionServers.BuyerInput.CustomAudience buyerInputCA,
            DBCustomAudience dbCustomAudience,
            boolean compareAds) {
        expect.that(buyerInputCA.getName()).isEqualTo(dbCustomAudience.getName());
        expect.that(buyerInputCA.getOwner()).isEqualTo(dbCustomAudience.getOwner());
        expect.that(dbCustomAudience.getTrustedBiddingData()).isNotNull();
        expect.that(buyerInputCA.getBiddingSignalsKeysList())
                .isEqualTo(dbCustomAudience.getTrustedBiddingData().getKeys());
        expect.that(dbCustomAudience.getUserBiddingSignals()).isNotNull();
        expect.that(buyerInputCA.getUserBiddingSignals())
                .isEqualTo(dbCustomAudience.getUserBiddingSignals().toString());
        expect.that(dbCustomAudience.getAds()).isNotNull();
        if (compareAds) {
            expect.that(buyerInputCA.getAdRenderIdsList())
                    .isEqualTo(
                            dbCustomAudience.getAds().stream()
                                    .filter(
                                            ad ->
                                                    ad.getAdRenderId() != null
                                                            && !ad.getAdRenderId().isEmpty())
                                    .map(ad -> ad.getAdRenderId())
                                    .collect(Collectors.toList()));
        }
    }
}
