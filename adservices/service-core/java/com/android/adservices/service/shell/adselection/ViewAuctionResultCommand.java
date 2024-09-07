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

import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER;
import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;

import static com.android.adservices.service.shell.adselection.AdSelectionShellCommandConstants.AD_SELECTION_ID;
import static com.android.adservices.service.shell.adselection.AdSelectionShellCommandConstants.FIRST_ARG_FOR_PARSING;
import static com.android.adservices.service.shell.adselection.AdSelectionShellCommandConstants.OUTPUT_PROTO_FIELD_NAME;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_AD_SELECTION_VIEW_AUCTION_RESULT;

import android.util.Base64;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.AuctionResult;
import com.android.adservices.service.shell.AbstractShellCommand;
import com.android.adservices.service.shell.ShellCommandArgParserHelper;
import com.android.adservices.service.shell.ShellCommandResult;
import com.android.adservices.service.stats.ShellCommandStats;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableMap;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;

public class ViewAuctionResultCommand extends AbstractShellCommand {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting public static final String CMD = "view-auction-result";

    @VisibleForTesting
    public static final String HELP =
            AdSelectionShellCommandFactory.COMMAND_PREFIX
                    + " "
                    + CMD
                    + " "
                    + AD_SELECTION_ID
                    + " <ad-selection-id>"
                    + "\n    View the result of a successful auction for a given ad selection id.";

    private final AdSelectionEntryDao mAdSelectionEntryDao;

    public ViewAuctionResultCommand(AdSelectionEntryDao adSelectionEntryDao) {
        mAdSelectionEntryDao = adSelectionEntryDao;
    }

    @Override
    public ShellCommandResult run(PrintWriter out, PrintWriter err, String[] args) {
        ImmutableMap<String, String> cliArgs;
        try {
            cliArgs = ShellCommandArgParserHelper.parseCliArguments(args, FIRST_ARG_FOR_PARSING);
        } catch (IllegalArgumentException exception) {
            sLogger.e(exception, "could not parse arguments");
            return invalidArgsError(HELP, err, COMMAND_AD_SELECTION_VIEW_AUCTION_RESULT, args);
        }

        long adSelectionId;
        try {
            adSelectionId = Long.parseLong(cliArgs.get(AD_SELECTION_ID));
        } catch (NumberFormatException exception) {
            sLogger.e(exception, "could not parse ad selection ID to number");
            err.write("--ad-selection-id should be a number");
            return invalidArgsError(HELP, err, COMMAND_AD_SELECTION_VIEW_AUCTION_RESULT, args);
        }

        if (!mAdSelectionEntryDao.doesAdSelectionIdExist(adSelectionId)) {
            sLogger.v("no auction result found for ad selection id: %s", adSelectionId);
            err.write("no auction result found for ad selection id: " + adSelectionId);
            return invalidArgsError(HELP, err, COMMAND_AD_SELECTION_VIEW_AUCTION_RESULT, args);
        }

        try {
            AuctionResult result =
                    AdSelectionEntryHelper.getAuctionResultFromAdSelectionEntry(
                            mAdSelectionEntryDao.getAdSelectionEntityById(adSelectionId),
                            mAdSelectionEntryDao.getReportingUris(adSelectionId),
                            mAdSelectionEntryDao.listRegisteredAdInteractions(
                                    adSelectionId, FLAG_REPORTING_DESTINATION_BUYER),
                            mAdSelectionEntryDao.listRegisteredAdInteractions(
                                    adSelectionId, FLAG_REPORTING_DESTINATION_SELLER));
            out.printf(
                    new JSONObject()
                            .put(
                                    OUTPUT_PROTO_FIELD_NAME,
                                    Base64.encodeToString(result.toByteArray(), Base64.DEFAULT))
                            .toString());
        } catch (JSONException e) {
            sLogger.v("could not format ad selection (id: %s) entry to json", adSelectionId);
            err.write("could not format ad selection entry to json with id: " + adSelectionId);
            return toShellCommandResult(
                    ShellCommandStats.RESULT_GENERIC_ERROR,
                    COMMAND_AD_SELECTION_VIEW_AUCTION_RESULT);
        }
        return toShellCommandResult(
                ShellCommandStats.RESULT_SUCCESS, COMMAND_AD_SELECTION_VIEW_AUCTION_RESULT);
    }

    @Override
    public String getCommandName() {
        return CMD;
    }

    @Override
    public int getMetricsLoggerCommand() {
        return COMMAND_AD_SELECTION_VIEW_AUCTION_RESULT;
    }

    @Override
    public String getCommandHelp() {
        return HELP;
    }
}
