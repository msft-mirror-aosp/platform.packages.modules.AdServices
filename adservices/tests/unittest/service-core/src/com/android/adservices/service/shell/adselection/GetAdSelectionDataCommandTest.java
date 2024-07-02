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

package com.android.adservices.service.shell.adselection;

import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_AD_SELECTION_GET_AD_SELECTION_DATA;

import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.util.Base64;

import com.android.adservices.service.adselection.AuctionServerDataCompressor;
import com.android.adservices.service.adselection.AuctionServerDataCompressorGzip;
import com.android.adservices.service.adselection.BuyerInputGenerator;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.BuyerInput;
import com.android.adservices.service.shell.ShellCommandTestCase;
import com.android.adservices.service.stats.ShellCommandStats;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.protobuf.InvalidProtocolBufferException;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Map;

public class GetAdSelectionDataCommandTest extends ShellCommandTestCase<GetAdSelectionDataCommand> {

    @ShellCommandStats.Command
    private static final int EXPECTED_COMMAND = COMMAND_AD_SELECTION_GET_AD_SELECTION_DATA;

    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("example.com");

    @Mock private BuyerInputGenerator mBuyerInputGenerator;
    private AuctionServerDataCompressor mAuctionServerDataCompressor;

    @Before
    public void setUp() {
        mAuctionServerDataCompressor = new AuctionServerDataCompressorGzip();
    }

    @Test
    public void testRun_missingBothArguments_returnsHelp() {
        runAndExpectInvalidArgument(
                new GetAdSelectionDataCommand(mBuyerInputGenerator, mAuctionServerDataCompressor),
                GetAdSelectionDataCommand.HELP,
                EXPECTED_COMMAND,
                AdSelectionShellCommandFactory.COMMAND_PREFIX,
                GetAdSelectionDataCommand.CMD);
    }

    @Test
    public void testRun_missingBuyerArgument_returnsHelp() {
        runAndExpectInvalidArgument(
                new GetAdSelectionDataCommand(mBuyerInputGenerator, mAuctionServerDataCompressor),
                GetAdSelectionDataCommand.HELP,
                EXPECTED_COMMAND,
                AdSelectionShellCommandFactory.COMMAND_PREFIX,
                GetAdSelectionDataCommand.CMD);
    }

    @Test
    public void testRun_withUnknownBuyer_throwsException() {
        when(mBuyerInputGenerator.createCompressedBuyerInputs(null))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(Map.of())));

        Result result =
                run(
                        new GetAdSelectionDataCommand(
                                mBuyerInputGenerator, mAuctionServerDataCompressor),
                        AdSelectionShellCommandFactory.COMMAND_PREFIX,
                        GetAdSelectionDataCommand.CMD,
                        GetAdSelectionDataArgs.BUYER,
                        BUYER.toString());

        expectFailure(
                result,
                String.format(GetAdSelectionDataCommand.ERROR_UNKNOWN_BUYER_FORMAT, BUYER),
                EXPECTED_COMMAND,
                ShellCommandStats.RESULT_GENERIC_ERROR);
    }

    @Test
    public void testRun_withAllArguments_returnsSuccess()
            throws InvalidProtocolBufferException, JSONException {
        BuyerInput buyerInput = BuyerInput.newBuilder().build();
        when(mBuyerInputGenerator.createCompressedBuyerInputs(null))
                .thenReturn(
                        FluentFuture.from(
                                Futures.immediateFuture(
                                        getCompressedBuyerInput(BUYER, buyerInput))));

        Result result =
                run(
                        new GetAdSelectionDataCommand(
                                mBuyerInputGenerator, mAuctionServerDataCompressor),
                        AdSelectionShellCommandFactory.COMMAND_PREFIX,
                        GetAdSelectionDataCommand.CMD,
                        GetAdSelectionDataArgs.BUYER,
                        BUYER.toString());

        expectSuccess(result, EXPECTED_COMMAND);
        // Verify that the written proto conforms to the BuyerInput specification.
        BuyerInput.parseFrom(
                Base64.decode(
                        new JSONObject(result.mOut)
                                .getString(GetAdSelectionDataCommand.OUTPUT_PROTO_FIELD_NAME),
                        Base64.DEFAULT));
    }

    private Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData>
            getCompressedBuyerInput(AdTechIdentifier adTechIdentifier, BuyerInput buyerInput) {
        return Map.of(
                adTechIdentifier,
                mAuctionServerDataCompressor.compress(
                        AuctionServerDataCompressor.UncompressedData.create(
                                buyerInput.toByteArray())));
    }
}
