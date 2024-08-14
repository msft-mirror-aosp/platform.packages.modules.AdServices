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
import static android.adservices.common.CommonFixture.getAlphaNumericString;

import static com.android.adservices.service.Flags.FLEDGE_AUCTION_SERVER_COMPRESSION_ALGORITHM_VERSION;
import static com.android.adservices.service.Flags.PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.atLeast;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.util.Pair;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.data.signals.DBEncodedPayloadFixture;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers;
import com.android.adservices.service.stats.GetAdSelectionDataApiCalledStats;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CompressedBuyerInputCreatorSellerMaxImplTest
        extends AdServicesExtendedMockitoTestCase {
    private CompressedBuyerInputCreator mCompressedBuyerInputCreator;
    private AuctionServerDataCompressor mAuctionServerDataCompressor;
    private CompressedBuyerInputCreatorHelper mCompressedBuyerInputCreatorHelper;
    @Mock private AuctionServerPayloadMetricsStrategy mAuctionServerPayloadMetricsStrategyMock;
    private static final int NUM_RECALCULATIONS = 10;
    private static final int MAX_SELLER_BYTES = 20 * 1024; // 20KB
    private static final boolean OMIT_ADS_DISABLED = false;
    private static final boolean PAS_METRICS_DISABLED = false;
    @Mock private Clock mClockMock;
    @Mock private GetAdSelectionDataApiCalledStats.Builder mStatsBuilderMock;
    private static final int LATENCY_MS = 100;
    private static final int NO_RECALCULATIONS = 0;

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

        // Make latency difference 100
        when(mClockMock.millis()).thenReturn(0L).thenReturn(100L);

        mCompressedBuyerInputCreator =
                new CompressedBuyerInputCreatorSellerPayloadMaxImpl(
                        mCompressedBuyerInputCreatorHelper,
                        mAuctionServerDataCompressor,
                        NUM_RECALCULATIONS,
                        MAX_SELLER_BYTES,
                        PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES,
                        mClockMock,
                        mStatsBuilderMock);
    }

    @Test
    @SuppressWarnings("ReturnValueIgnored")
    public void generateCompressedBuyerInputFromDBCAsAndEncodedSignalsReturnsCompressedInputs()
            throws Exception {
        Map<String, AdTechIdentifier> nameAndBuyersMap =
                Map.of(
                        "Shoes CA of Buyer 1", BUYER_1,
                        "Shirts CA of Buyer 1", BUYER_1,
                        "Shoes CA Of Buyer 2", BUYER_2);

        List<AdTechIdentifier> buyersList = ImmutableList.of(BUYER_1, BUYER_2);
        Pair<List<DBCustomAudience>, Map<String, DBCustomAudience>> resultPair =
                createDBCustomAudiences(nameAndBuyersMap);

        List<DBCustomAudience> dbCustomAudienceList = resultPair.first;
        Map<String, DBCustomAudience> namesToCustomAudience = resultPair.second;

        Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap =
                generateExpectedEncodedPayloadForBuyers(buyersList);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedDataMap =
                mCompressedBuyerInputCreator.generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                        dbCustomAudienceList, encodedPayloadMap);

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
            expect.that(appSignals.getEncodingVersion())
                    .isEqualTo(encodedPayloadMap.get(buyer).getVersion());
            expect.that(appSignals.getAppInstallSignals())
                    .isEqualTo(
                            ByteString.copyFrom(encodedPayloadMap.get(buyer).getEncodedPayload()));
        }
        expect.that(totalNumCAsInBuyerInput).isEqualTo(dbCustomAudienceList.size());
        verify(mAuctionServerPayloadMetricsStrategyMock, times(dbCustomAudienceList.size()))
                .addToBuyerIntermediateStats(any(), any(), any());
        verify(mAuctionServerPayloadMetricsStrategyMock)
                .logGetAdSelectionDataBuyerInputGeneratedStats(any());
        verify(mAuctionServerPayloadMetricsStrategyMock)
                .setSellerConfigurationMetrics(
                        mStatsBuilderMock,
                        GetAdSelectionDataApiCalledStats.PayloadOptimizationResult
                                .PAYLOAD_WITHIN_REQUESTED_MAX,
                        LATENCY_MS,
                        CompressedBuyerInputCreatorSellerPayloadMaxImpl.VERSION,
                        NO_RECALCULATIONS);
        verify(mClockMock, times(2)).millis();
    }

    @Test
    @SuppressWarnings("ReturnValueIgnored")
    public void
            generateCompressedBuyerInputFromDBCAsAndEncodedSignalsReturnsCompressedInputs_OnlyPAS()
                    throws Exception {
        List<AdTechIdentifier> buyersList = ImmutableList.of(BUYER_1, BUYER_2);
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
        verify(mAuctionServerPayloadMetricsStrategyMock, never())
                .addToBuyerIntermediateStats(any(), any(), any());
        verify(mAuctionServerPayloadMetricsStrategyMock)
                .logGetAdSelectionDataBuyerInputGeneratedStats(any());
        verify(mAuctionServerPayloadMetricsStrategyMock)
                .setSellerConfigurationMetrics(
                        mStatsBuilderMock,
                        GetAdSelectionDataApiCalledStats.PayloadOptimizationResult
                                .PAYLOAD_WITHIN_REQUESTED_MAX,
                        LATENCY_MS,
                        CompressedBuyerInputCreatorSellerPayloadMaxImpl.VERSION,
                        NO_RECALCULATIONS);
        verify(mClockMock, times(2)).millis();
    }

    @Test
    @SuppressWarnings("ReturnValueIgnored")
    public void
            generateCompressedBuyerInputFromDBCAsAndEncodedSignalsReturnsCompressedInputs_OnlyPA()
                    throws Exception {
        Map<String, AdTechIdentifier> nameAndBuyersMap =
                Map.of(
                        "Shoes CA of Buyer 1", BUYER_1,
                        "Shirts CA of Buyer 1", BUYER_1,
                        "Shoes CA Of Buyer 2", BUYER_2);

        List<AdTechIdentifier> buyersList = ImmutableList.of(BUYER_1, BUYER_2);
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
        verify(mAuctionServerPayloadMetricsStrategyMock, times(dbCustomAudienceList.size()))
                .addToBuyerIntermediateStats(any(), any(), any());
        verify(mAuctionServerPayloadMetricsStrategyMock)
                .logGetAdSelectionDataBuyerInputGeneratedStats(any());
        verify(mAuctionServerPayloadMetricsStrategyMock)
                .setSellerConfigurationMetrics(
                        mStatsBuilderMock,
                        GetAdSelectionDataApiCalledStats.PayloadOptimizationResult
                                .PAYLOAD_WITHIN_REQUESTED_MAX,
                        LATENCY_MS,
                        CompressedBuyerInputCreatorSellerPayloadMaxImpl.VERSION,
                        NO_RECALCULATIONS);
        verify(mClockMock, times(2)).millis();
    }

    @Test
    @SuppressWarnings("ReturnValueIgnored")
    public void
            generateCompressedBuyerInputFromDBCAsAndEncodedSignalsReturnsCompressedInputs_NotEnoughSpaceForPAS()
                    throws Exception {

        // Create new creator with PAS length as the max size, where the min for 2 buyers is 2*PAS
        // length
        CompressedBuyerInputCreator compressedBuyerInputCreator =
                new CompressedBuyerInputCreatorSellerPayloadMaxImpl(
                        mCompressedBuyerInputCreatorHelper,
                        mAuctionServerDataCompressor,
                        NUM_RECALCULATIONS,
                        PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES,
                        PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES,
                        mClockMock,
                        mStatsBuilderMock);

        Map<String, AdTechIdentifier> nameAndBuyersMap =
                Map.of(
                        "Shoes CA of Buyer 1", BUYER_1,
                        "Shirts CA of Buyer 1", BUYER_1,
                        "Shoes CA Of Buyer 2", BUYER_2);

        List<AdTechIdentifier> buyersList = ImmutableList.of(BUYER_1, BUYER_2);
        Pair<List<DBCustomAudience>, Map<String, DBCustomAudience>> resultPair =
                createDBCustomAudiences(nameAndBuyersMap);

        Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap =
                generateExpectedEncodedPayloadForBuyers(buyersList);

        List<DBCustomAudience> dbCustomAudienceList = resultPair.first;
        Map<String, DBCustomAudience> namesToCustomAudience = resultPair.second;

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedDataMap =
                compressedBuyerInputCreator.generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                        dbCustomAudienceList, encodedPayloadMap);

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
        verify(mAuctionServerPayloadMetricsStrategyMock, times(dbCustomAudienceList.size()))
                .addToBuyerIntermediateStats(any(), any(), any());
        verify(mAuctionServerPayloadMetricsStrategyMock)
                .logGetAdSelectionDataBuyerInputGeneratedStats(any());
        verify(mAuctionServerPayloadMetricsStrategyMock)
                .setSellerConfigurationMetrics(
                        mStatsBuilderMock,
                        GetAdSelectionDataApiCalledStats.PayloadOptimizationResult
                                .PAYLOAD_WITHIN_REQUESTED_MAX,
                        LATENCY_MS,
                        CompressedBuyerInputCreatorSellerPayloadMaxImpl.VERSION,
                        NO_RECALCULATIONS);
        verify(mClockMock, times(2)).millis();
    }

    @Test
    @SuppressWarnings("ReturnValueIgnored")
    public void generateCompressedBuyerInputFromDBCAsAndEncodedSignalsRespectsMaxSize()
            throws Exception {

        int smallerMaxSize = 3 * 1024;

        CompressedBuyerInputCreator compressedBuyerInputCreator =
                new CompressedBuyerInputCreatorSellerPayloadMaxImpl(
                        mCompressedBuyerInputCreatorHelper,
                        mAuctionServerDataCompressor,
                        NUM_RECALCULATIONS,
                        smallerMaxSize,
                        PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES,
                        mClockMock,
                        mStatsBuilderMock);

        List<AdTechIdentifier> buyersList = ImmutableList.of(BUYER_1, BUYER_2);

        // Init with 100 CAs, which by compressing everything is larger than 3Kb
        List<DBCustomAudience> dbCustomAudienceList = createBulkDBCustomAudiences(buyersList, 100);

        Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap =
                generateExpectedEncodedPayloadForBuyers(buyersList);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedDataMap =
                compressedBuyerInputCreator.generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                        dbCustomAudienceList, encodedPayloadMap);

        for (AdTechIdentifier buyer : buyersList) {
            BiddingAuctionServers.BuyerInput buyerInput =
                    BiddingAuctionServers.BuyerInput.parseFrom(
                            mAuctionServerDataCompressor
                                    .decompress(compressedDataMap.get(buyer))
                                    .getData());

            BiddingAuctionServers.ProtectedAppSignals appSignals =
                    buyerInput.getProtectedAppSignals();
            expect.that(appSignals.getEncodingVersion())
                    .isEqualTo(encodedPayloadMap.get(buyer).getVersion());
            expect.that(appSignals.getAppInstallSignals())
                    .isEqualTo(
                            ByteString.copyFrom(encodedPayloadMap.get(buyer).getEncodedPayload()));
        }

        expect.that(getTotalSize(compressedDataMap)).isLessThan(smallerMaxSize);

        // We don't know how many CAs were fitted, so we don't know how many times this should be
        // called, just making sure it is
        verify(mAuctionServerPayloadMetricsStrategyMock, atLeast(1))
                .addToBuyerIntermediateStats(any(), any(), any());
        verify(mAuctionServerPayloadMetricsStrategyMock)
                .logGetAdSelectionDataBuyerInputGeneratedStats(any());
        verify(mAuctionServerPayloadMetricsStrategyMock)
                .setSellerConfigurationMetrics(
                        mStatsBuilderMock,
                        GetAdSelectionDataApiCalledStats.PayloadOptimizationResult
                                .PAYLOAD_TRUNCATED_FOR_REQUESTED_MAX,
                        LATENCY_MS,
                        CompressedBuyerInputCreatorSellerPayloadMaxImpl.VERSION,
                        NUM_RECALCULATIONS);
        verify(mClockMock, times(2)).millis();
    }

    @Test
    @SuppressWarnings("ReturnValueIgnored")
    public void
            generateCompressedBuyerInputFromDBCAsAndEncodedSignalsReturnsEmptyPayloadWithZeroMaxSize()
                    throws Exception {

        int zeroMaxSize = 0;
        CompressedBuyerInputCreator compressedBuyerInputCreator =
                new CompressedBuyerInputCreatorSellerPayloadMaxImpl(
                        mCompressedBuyerInputCreatorHelper,
                        mAuctionServerDataCompressor,
                        NUM_RECALCULATIONS,
                        zeroMaxSize,
                        PROTECTED_SIGNALS_ENCODED_PAYLOAD_MAX_SIZE_BYTES,
                        mClockMock,
                        mStatsBuilderMock);

        Map<String, AdTechIdentifier> nameAndBuyersMap =
                Map.of(
                        "Shoes CA of Buyer 1", BUYER_1,
                        "Shirts CA of Buyer 1", BUYER_1,
                        "Shoes CA Of Buyer 2", BUYER_2);

        List<AdTechIdentifier> buyersList = ImmutableList.of(BUYER_1, BUYER_2);
        Pair<List<DBCustomAudience>, Map<String, DBCustomAudience>> resultPair =
                createDBCustomAudiences(nameAndBuyersMap);

        Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap =
                generateExpectedEncodedPayloadForBuyers(buyersList);

        List<DBCustomAudience> dbCustomAudienceList = resultPair.first;

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedDataMap =
                compressedBuyerInputCreator.generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                        dbCustomAudienceList, encodedPayloadMap);

        expect.that(compressedDataMap).isEmpty();

        // Verify this is not called because we don't add any buyer inputs
        verify(mAuctionServerPayloadMetricsStrategyMock, never())
                .addToBuyerIntermediateStats(any(), any(), any());

        verify(mAuctionServerPayloadMetricsStrategyMock)
                .logGetAdSelectionDataBuyerInputGeneratedStats(any());
        verify(mAuctionServerPayloadMetricsStrategyMock)
                .setSellerConfigurationMetrics(
                        mStatsBuilderMock,
                        GetAdSelectionDataApiCalledStats.PayloadOptimizationResult
                                .PAYLOAD_WITHIN_REQUESTED_MAX,
                        LATENCY_MS,
                        CompressedBuyerInputCreatorSellerPayloadMaxImpl.VERSION,
                        NO_RECALCULATIONS);
        verify(mClockMock, times(2)).millis();
    }

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
            DBCustomAudience thisCustomAudience =
                    DBCustomAudienceFixture.getValidBuilderByBuyer(buyer, name).build();
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
                                .build();
                customAudiences.add(thisCustomAudience);
            }
        }
        return customAudiences;
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
