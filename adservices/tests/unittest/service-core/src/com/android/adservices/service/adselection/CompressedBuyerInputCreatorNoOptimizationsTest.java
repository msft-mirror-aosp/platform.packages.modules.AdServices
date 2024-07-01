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

import static com.android.adservices.service.Flags.FLEDGE_AUCTION_SERVER_COMPRESSION_ALGORITHM_VERSION;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import android.adservices.common.AdTechIdentifier;
import android.util.Pair;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.data.signals.DBEncodedPayloadFixture;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CompressedBuyerInputCreatorNoOptimizationsTest
        extends AdServicesExtendedMockitoTestCase {
    private CompressedBuyerInputCreator mCompressedBuyerInputCreator;
    private AuctionServerDataCompressor mAuctionServerDataCompressor;
    @Mock private AuctionServerPayloadMetricsStrategy mAuctionServerPayloadMetricsStrategyMock;
    private static final boolean OMIT_ADS_DISABLED = false;
    private static final boolean PAS_METRICS_DISABLED = false;

    @Before
    public void setup() throws Exception {
        mAuctionServerDataCompressor =
                AuctionServerDataCompressorFactory.getDataCompressor(
                        FLEDGE_AUCTION_SERVER_COMPRESSION_ALGORITHM_VERSION);

        CompressedBuyerInputCreatorHelper compressedBuyerInputCreatorHelper =
                new CompressedBuyerInputCreatorHelper(
                        mAuctionServerPayloadMetricsStrategyMock,
                        PAS_METRICS_DISABLED,
                        OMIT_ADS_DISABLED);

        mCompressedBuyerInputCreator =
                new CompressedBuyerInputCreatorNoOptimizations(
                        compressedBuyerInputCreatorHelper, mAuctionServerDataCompressor);
    }

    @Test
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
                generateEncodedPayload(buyersList);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedDataMap =
                mCompressedBuyerInputCreator.generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                        dbCustomAudienceList, encodedPayloadMap);

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
            expect.that(encodedPayloadMap.get(buyer).getVersion())
                    .isEqualTo(appSignals.getEncodingVersion());
            expect.that(ByteString.copyFrom(encodedPayloadMap.get(buyer).getEncodedPayload()))
                    .isEqualTo(appSignals.getAppInstallSignals());
        }
        verify(mAuctionServerPayloadMetricsStrategyMock, times(dbCustomAudienceList.size()))
                .addToBuyerIntermediateStats(any(), any(), any());
        verify(mAuctionServerPayloadMetricsStrategyMock)
                .logGetAdSelectionDataBuyerInputGeneratedStats(any());
    }

    @Test
    public void
            generateCompressedBuyerInputFromDBCAsAndEncodedSignalsReturnsCompressedInputs_OnlyPAS()
                    throws Exception {
        List<AdTechIdentifier> buyersList = ImmutableList.of(BUYER_1, BUYER_2);
        Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap =
                generateEncodedPayload(buyersList);

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
            expect.that(encodedPayloadMap.get(buyer).getVersion())
                    .isEqualTo(appSignals.getEncodingVersion());
            expect.that(ByteString.copyFrom(encodedPayloadMap.get(buyer).getEncodedPayload()))
                    .isEqualTo(appSignals.getAppInstallSignals());
        }
        verify(mAuctionServerPayloadMetricsStrategyMock, never())
                .addToBuyerIntermediateStats(any(), any(), any());
        verify(mAuctionServerPayloadMetricsStrategyMock)
                .logGetAdSelectionDataBuyerInputGeneratedStats(any());
    }

    @Test
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
            expect.that(appSignals.getSerializedSize()).isEqualTo(0);
        }
        verify(mAuctionServerPayloadMetricsStrategyMock, times(dbCustomAudienceList.size()))
                .addToBuyerIntermediateStats(any(), any(), any());
        verify(mAuctionServerPayloadMetricsStrategyMock)
                .logGetAdSelectionDataBuyerInputGeneratedStats(any());
    }

    private Map<AdTechIdentifier, DBEncodedPayload> generateEncodedPayload(
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
