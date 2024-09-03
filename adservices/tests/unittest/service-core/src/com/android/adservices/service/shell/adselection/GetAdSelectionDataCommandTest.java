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

import static com.android.adservices.service.shell.adselection.AdSelectionShellCommandConstants.OUTPUT_PROTO_FIELD_NAME;
import static com.android.adservices.service.shell.adselection.GetAdSelectionDataCommand.APP_PACKAGE_NAME_HINT;
import static com.android.adservices.service.shell.adselection.GetAdSelectionDataCommand.BUYER_KV_EXPERIMENT_ID_HINT;
import static com.android.adservices.service.shell.adselection.GetAdSelectionDataCommand.CMD;
import static com.android.adservices.service.shell.adselection.GetAdSelectionDataCommand.ERROR_UNKNOWN_BUYER_FORMAT;
import static com.android.adservices.service.shell.adselection.GetAdSelectionDataCommand.HELP;
import static com.android.adservices.service.shell.adselection.GetAdSelectionDataCommand.SELLER_HINT;
import static com.android.adservices.service.shell.adselection.GetAdSelectionDataCommand.TOP_LEVEL_SELLER_HINT;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_AD_SELECTION_GET_AD_SELECTION_DATA;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.util.Base64;

import com.android.adservices.service.adselection.AuctionServerDataCompressor;
import com.android.adservices.service.adselection.AuctionServerDataCompressorGzip;
import com.android.adservices.service.adselection.BuyerInputGenerator;
import com.android.adservices.service.adselection.debug.ConsentedDebugConfigurationGenerator;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.BuyerInput;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.BuyerInput.CustomAudience;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.ConsentedDebugConfiguration;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.GetBidsRequest.GetBidsRawRequest;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.ProtectedAppSignals;
import com.android.adservices.service.shell.ShellCommandTestCase;
import com.android.adservices.service.stats.ShellCommandStats;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

public class GetAdSelectionDataCommandTest extends ShellCommandTestCase<GetAdSelectionDataCommand> {

    @ShellCommandStats.Command
    private static final int EXPECTED_COMMAND = COMMAND_AD_SELECTION_GET_AD_SELECTION_DATA;

    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("example.com");

    @Mock private BuyerInputGenerator mBuyerInputGenerator;
    @Mock private ConsentedDebugConfigurationGenerator mConsentedDebugConfigurationGenerator;
    private AuctionServerDataCompressor mAuctionServerDataCompressor;

    @Before
    public void setUp() {
        mAuctionServerDataCompressor = new AuctionServerDataCompressorGzip();
    }

    @Test
    public void testRun_missingBothArguments_returnsHelp() {
        runAndExpectInvalidArgument(
                new GetAdSelectionDataCommand(
                        mBuyerInputGenerator,
                        mAuctionServerDataCompressor,
                        mConsentedDebugConfigurationGenerator),
                HELP,
                EXPECTED_COMMAND,
                AdSelectionShellCommandFactory.COMMAND_PREFIX,
                CMD);
    }

    @Test
    public void testRun_missingBuyerArgument_returnsHelp() {
        runAndExpectInvalidArgument(
                new GetAdSelectionDataCommand(
                        mBuyerInputGenerator,
                        mAuctionServerDataCompressor,
                        mConsentedDebugConfigurationGenerator),
                HELP,
                EXPECTED_COMMAND,
                AdSelectionShellCommandFactory.COMMAND_PREFIX,
                CMD);
    }

    @Test
    public void testRun_withUnknownBuyer_throwsException() {
        when(mBuyerInputGenerator.createCompressedBuyerInputs(any(), any()))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(Map.of())));

        Result result =
                run(
                        new GetAdSelectionDataCommand(
                                mBuyerInputGenerator,
                                mAuctionServerDataCompressor,
                                mConsentedDebugConfigurationGenerator),
                        AdSelectionShellCommandFactory.COMMAND_PREFIX,
                        CMD,
                        AdSelectionShellCommandConstants.BUYER,
                        BUYER.toString());

        expectFailure(
                result,
                String.format(ERROR_UNKNOWN_BUYER_FORMAT, BUYER),
                EXPECTED_COMMAND,
                ShellCommandStats.RESULT_GENERIC_ERROR);
    }

    @Test
    public void testRun_withAllArguments_returnsSuccess()
            throws InvalidProtocolBufferException, JSONException {
        when(mConsentedDebugConfigurationGenerator.getConsentedDebugConfiguration())
                .thenReturn(Optional.empty());
        ProtectedAppSignals protectedAppSignals =
                ProtectedAppSignals.newBuilder()
                        .setEncodingVersion(1)
                        .setAppInstallSignals(ByteString.copyFrom("hello", StandardCharsets.UTF_8))
                        .build();
        CustomAudience customAudience =
                CustomAudience.newBuilder()
                        .setName("shoes")
                        .setOwner("com.example")
                        .addBiddingSignalsKeys("abc")
                        .addAdRenderIds("123")
                        .setUserBiddingSignals("{}")
                        .build();
        BuyerInput buyerInput =
                BuyerInput.newBuilder()
                        .setProtectedAppSignals(protectedAppSignals)
                        .addCustomAudiences(customAudience)
                        .build();
        when(mBuyerInputGenerator.createCompressedBuyerInputs(any(), any()))
                .thenReturn(
                        FluentFuture.from(
                                Futures.immediateFuture(
                                        getCompressedBuyerInput(BUYER, buyerInput))));

        Result result =
                run(
                        new GetAdSelectionDataCommand(
                                mBuyerInputGenerator,
                                mAuctionServerDataCompressor,
                                mConsentedDebugConfigurationGenerator),
                        AdSelectionShellCommandFactory.COMMAND_PREFIX,
                        CMD,
                        AdSelectionShellCommandConstants.BUYER,
                        BUYER.toString());

        expectSuccess(result, EXPECTED_COMMAND);
        // Verify that the written proto conforms to the BuyerInput specification.
        GetBidsRawRequest request =
                GetBidsRawRequest.parseFrom(
                        Base64.decode(
                                new JSONObject(result.mOut).getString(OUTPUT_PROTO_FIELD_NAME),
                                Base64.DEFAULT));
        assertThat(request.getProtectedAppSignalsBuyerInput().getProtectedAppSignals())
                .isEqualTo(buyerInput.getProtectedAppSignals());
        assertThat(request.getBuyerInput().getCustomAudiencesCount()).isEqualTo(1);
        assertThat(request.getBuyerInput().getCustomAudiences(0)).isEqualTo(customAudience);
        assertThat(request.getConsentedDebugConfig().getIsConsented()).isFalse();
        assertThat(request.getEnableDebugReporting()).isTrue();
        assertThat(request.getEnableUnlimitedEgress()).isTrue();
        assertThat(request.getSeller()).isEqualTo(SELLER_HINT);
        assertThat(request.getTopLevelSeller()).isEqualTo(TOP_LEVEL_SELLER_HINT);
        assertThat(request.getPublisherName()).isEqualTo(APP_PACKAGE_NAME_HINT);
        assertThat(request.getBuyerKvExperimentGroupId()).isEqualTo(BUYER_KV_EXPERIMENT_ID_HINT);
    }

    @Test
    public void testRun_withConsentedDebugConfiguration_returnsSuccess()
            throws InvalidProtocolBufferException, JSONException {
        String consentedToken = "123456";
        ConsentedDebugConfiguration consentedDebugConfiguration =
                ConsentedDebugConfiguration.newBuilder()
                        .setIsConsented(true)
                        .setToken(consentedToken)
                        .setIsDebugInfoInResponse(false)
                        .build();
        when(mConsentedDebugConfigurationGenerator.getConsentedDebugConfiguration())
                .thenReturn(Optional.of(consentedDebugConfiguration));
        ProtectedAppSignals protectedAppSignals =
                ProtectedAppSignals.newBuilder()
                        .setEncodingVersion(1)
                        .setAppInstallSignals(ByteString.copyFrom("hello", StandardCharsets.UTF_8))
                        .build();
        CustomAudience customAudience =
                CustomAudience.newBuilder()
                        .setName("shoes")
                        .setOwner("com.example")
                        .addBiddingSignalsKeys("abc")
                        .addAdRenderIds("123")
                        .setUserBiddingSignals("{}")
                        .build();
        BuyerInput buyerInput =
                BuyerInput.newBuilder()
                        .setProtectedAppSignals(protectedAppSignals)
                        .addCustomAudiences(customAudience)
                        .build();
        when(mBuyerInputGenerator.createCompressedBuyerInputs(any(), any()))
                .thenReturn(
                        FluentFuture.from(
                                Futures.immediateFuture(
                                        getCompressedBuyerInput(BUYER, buyerInput))));

        Result result =
                run(
                        new GetAdSelectionDataCommand(
                                mBuyerInputGenerator,
                                mAuctionServerDataCompressor,
                                mConsentedDebugConfigurationGenerator),
                        AdSelectionShellCommandFactory.COMMAND_PREFIX,
                        CMD,
                        AdSelectionShellCommandConstants.BUYER,
                        BUYER.toString());

        expectSuccess(result, EXPECTED_COMMAND);
        // Verify that the written proto conforms to the BuyerInput specification.
        GetBidsRawRequest request =
                GetBidsRawRequest.parseFrom(
                        Base64.decode(
                                new JSONObject(result.mOut).getString(OUTPUT_PROTO_FIELD_NAME),
                                Base64.DEFAULT));
        assertThat(request.getProtectedAppSignalsBuyerInput().getProtectedAppSignals())
                .isEqualTo(buyerInput.getProtectedAppSignals());
        assertThat(request.getBuyerInput().getCustomAudiencesCount()).isEqualTo(1);
        assertThat(request.getBuyerInput().getCustomAudiences(0)).isEqualTo(customAudience);
        assertThat(request.getConsentedDebugConfig().getIsConsented()).isTrue();
        assertThat(request.getConsentedDebugConfig().getToken()).isEqualTo(consentedToken);
        assertThat(request.getEnableDebugReporting()).isTrue();
        assertThat(request.getEnableUnlimitedEgress()).isTrue();
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
