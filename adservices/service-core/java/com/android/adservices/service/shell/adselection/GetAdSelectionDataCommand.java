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

import static com.android.adservices.service.shell.AdServicesShellCommandHandler.TAG;
import static com.android.adservices.service.shell.adselection.AdSelectionShellCommandConstants.BUYER;
import static com.android.adservices.service.shell.adselection.AdSelectionShellCommandConstants.FIRST_ARG_FOR_PARSING;
import static com.android.adservices.service.shell.adselection.AdSelectionShellCommandConstants.OUTPUT_PROTO_FIELD_NAME;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_AD_SELECTION_GET_AD_SELECTION_DATA;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_SUCCESS;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_TIMEOUT_ERROR;
import static com.android.internal.util.Preconditions.checkArgument;

import android.adservices.common.AdTechIdentifier;
import android.util.Base64;
import android.util.Log;

import com.android.adservices.service.adselection.AuctionServerDataCompressor;
import com.android.adservices.service.adselection.BuyerInputGenerator;
import com.android.adservices.service.adselection.PayloadOptimizationContext;
import com.android.adservices.service.adselection.debug.ConsentedDebugConfigurationGenerator;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.BuyerInput;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.ClientType;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.ConsentedDebugConfiguration;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.GetBidsRequest;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.ProtectedAppSignalsBuyerInput;
import com.android.adservices.service.shell.AbstractShellCommand;
import com.android.adservices.service.shell.ShellCommandArgParserHelper;
import com.android.adservices.service.shell.ShellCommandResult;
import com.android.adservices.service.stats.GetAdSelectionDataApiCalledStats;
import com.android.adservices.service.stats.ShellCommandStats;
import com.android.internal.annotations.VisibleForTesting;

import com.google.protobuf.InvalidProtocolBufferException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class GetAdSelectionDataCommand extends AbstractShellCommand {
    @VisibleForTesting public static final String CMD = "get-ad-selection-data";

    @VisibleForTesting
    public static final String ERROR_UNKNOWN_BUYER_FORMAT = "could not find data for buyer: %s";

    @VisibleForTesting
    public static final String ERROR_TIMEOUT_DB = "timed-out while querying database";

    @VisibleForTesting
    public static final String ERROR_PROTOBUF_FORMAT = "could not format BuyerInput protobuf";

    @VisibleForTesting
    public static final String ERROR_JSON_FORMAT = "could not format json output: %s";

    @VisibleForTesting
    public static final String APP_PACKAGE_NAME_HINT =
            "Placeholder-Should-Match-With-App-Package-Name";

    @VisibleForTesting
    public static final String TOP_LEVEL_SELLER_HINT =
            "Placeholder-Should-Match-With-Top-Level-Seller-Origin-Domain-In-SFE-Config";

    @VisibleForTesting
    public static final String SELLER_HINT =
            "Placeholder-Should-Match-With-Seller-Origin-Domain-In-SFE-Config";

    @VisibleForTesting public static final int BUYER_KV_EXPERIMENT_ID_HINT = 0;

    // Both debug reporting and unlimited egress are enabled by default for shell command and
    // testing purposes.
    private static final boolean IS_DEBUG_REPORTING_ENABLED = true;
    private static final boolean IS_UNLIMITED_EGRESS_ENABLED = true;

    public static final String HELP =
            AdSelectionShellCommandFactory.COMMAND_PREFIX
                    + " "
                    + CMD
                    + " "
                    + AdSelectionShellCommandConstants.BUYER
                    + " <buyer>"
                    + "\n    Get ad selection data for a given buyer. This generates the "
                    + "base64 encoded GetBidsRequest protocol buffer message designed for usage "
                    + "together with `secure_invoke`.";

    private static final int DB_TIMEOUT_SEC = 3;

    private final BuyerInputGenerator mBuyerInputGenerator;
    private final AuctionServerDataCompressor mAuctionServerDataCompressor;
    private final ConsentedDebugConfigurationGenerator mConsentedDebugConfigurationGenerator;

    public GetAdSelectionDataCommand(
            BuyerInputGenerator buyerInputGenerator,
            AuctionServerDataCompressor auctionServerDataCompressor,
            ConsentedDebugConfigurationGenerator consentedDebugConfigurationGenerator) {
        mBuyerInputGenerator = buyerInputGenerator;
        mAuctionServerDataCompressor = auctionServerDataCompressor;
        mConsentedDebugConfigurationGenerator = consentedDebugConfigurationGenerator;
    }

    @Override
    public ShellCommandResult run(PrintWriter out, PrintWriter err, String[] args) {
        Map<String, String> cliArgs;
        try {
            cliArgs = ShellCommandArgParserHelper.parseCliArguments(args, FIRST_ARG_FOR_PARSING);
            checkArgument(cliArgs.containsKey(BUYER));
        } catch (IllegalArgumentException exception) {
            Log.e(TAG, "Error running get_ad_selection_data command", exception);
            return invalidArgsError(HELP, err, COMMAND_AD_SELECTION_GET_AD_SELECTION_DATA, args);
        }

        AdTechIdentifier buyer = AdTechIdentifier.fromString(cliArgs.get(BUYER));
        BuyerInput buyerInput;
        ConsentedDebugConfiguration consentedDebugConfiguration = getConsentedDebugConfiguration();
        try {
            buyerInput = getBuyerInputForBuyer(buyer);

        } catch (InvalidProtocolBufferException e) {
            err.printf(ERROR_PROTOBUF_FORMAT);
            Log.v(TAG, ERROR_PROTOBUF_FORMAT);
            return toShellCommandResult(
                    ShellCommandStats.RESULT_GENERIC_ERROR,
                    COMMAND_AD_SELECTION_GET_AD_SELECTION_DATA);
        } catch (IllegalStateException e) {
            err.printf(String.format(ERROR_UNKNOWN_BUYER_FORMAT, buyer));
            Log.v(TAG, String.format(ERROR_UNKNOWN_BUYER_FORMAT, buyer.toString()));
            return toShellCommandResult(
                    ShellCommandStats.RESULT_GENERIC_ERROR,
                    COMMAND_AD_SELECTION_GET_AD_SELECTION_DATA);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            err.printf(ERROR_TIMEOUT_DB);
            Log.v(TAG, ERROR_TIMEOUT_DB);
            return toShellCommandResult(
                    RESULT_TIMEOUT_ERROR, COMMAND_AD_SELECTION_GET_AD_SELECTION_DATA);
        }

        GetBidsRequest.GetBidsRawRequest request =
                getBidsRawRequestForBuyer(buyerInput, consentedDebugConfiguration);
        Log.v(TAG, "Loaded GetBidsRawRequest: " + request);
        try {
            out.printf(
                    new JSONObject()
                            .put(
                                    OUTPUT_PROTO_FIELD_NAME,
                                    Base64.encodeToString(request.toByteArray(), Base64.DEFAULT))
                            .toString());
        } catch (JSONException e) {
            Log.v(TAG, ERROR_JSON_FORMAT, e);
            err.printf(String.format(ERROR_JSON_FORMAT, e.getMessage()));
            return toShellCommandResult(
                    ShellCommandStats.RESULT_GENERIC_ERROR,
                    COMMAND_AD_SELECTION_GET_AD_SELECTION_DATA);
        }

        return toShellCommandResult(RESULT_SUCCESS, COMMAND_AD_SELECTION_GET_AD_SELECTION_DATA);
    }

    private GetBidsRequest.GetBidsRawRequest getBidsRawRequestForBuyer(
            BuyerInput buyerInput, ConsentedDebugConfiguration consentedDebugConfiguration) {
        return GetBidsRequest.GetBidsRawRequest.newBuilder()
                .setIsChaff(false)
                .setBuyerInput(buyerInput.toBuilder().clearProtectedAppSignals().build())
                .setAuctionSignals(new JSONObject().toString())
                .setProtectedAppSignalsBuyerInput(
                        ProtectedAppSignalsBuyerInput.newBuilder()
                                .setProtectedAppSignals(buyerInput.getProtectedAppSignals())
                                .build())
                .setBuyerSignals(new JSONObject().toString())
                .setEnableDebugReporting(IS_DEBUG_REPORTING_ENABLED)
                .setEnableUnlimitedEgress(IS_UNLIMITED_EGRESS_ENABLED)
                .setConsentedDebugConfig(consentedDebugConfiguration)
                .setClientType(ClientType.ANDROID)
                .setPublisherName(APP_PACKAGE_NAME_HINT)
                .setTopLevelSeller(TOP_LEVEL_SELLER_HINT)
                .setSeller(SELLER_HINT)
                .setBuyerKvExperimentGroupId(BUYER_KV_EXPERIMENT_ID_HINT)
                .build();
    }

    private BuyerInput getBuyerInputForBuyer(AdTechIdentifier buyer)
            throws ExecutionException,
                    InterruptedException,
                    TimeoutException,
                    InvalidProtocolBufferException {
        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> buyerInputs =
                mBuyerInputGenerator
                        .createCompressedBuyerInputs(
                                PayloadOptimizationContext.builder().build(),
                                GetAdSelectionDataApiCalledStats.builder())
                        .get(DB_TIMEOUT_SEC, TimeUnit.SECONDS);

        if (!buyerInputs.containsKey(buyer)) {
            Log.v(TAG, "get-ad-selection-data cmd: Could not find buyer in BuyerInput list.");
            throw new IllegalStateException(
                    String.format(ERROR_UNKNOWN_BUYER_FORMAT, buyer.toString()));
        } else {
            Log.v(TAG, "get-ad-selection-data cmd: Found BuyerInput for given buyer.");
        }
        AuctionServerDataCompressor.CompressedData compressedData = buyerInputs.get(buyer);

        return BuyerInput.parseFrom(
                mAuctionServerDataCompressor.decompress(compressedData).getData());
    }

    private ConsentedDebugConfiguration getConsentedDebugConfiguration() {
        Optional<ConsentedDebugConfiguration> consentedDebugConfiguration =
                mConsentedDebugConfigurationGenerator.getConsentedDebugConfiguration();
        return consentedDebugConfiguration.orElseGet(
                () -> ConsentedDebugConfiguration.newBuilder().setIsConsented(false).build());
    }

    @Override
    public String getCommandName() {
        return CMD;
    }

    @Override
    public int getMetricsLoggerCommand() {
        return COMMAND_AD_SELECTION_GET_AD_SELECTION_DATA;
    }

    @Override
    public String getCommandHelp() {
        return HELP;
    }
}
