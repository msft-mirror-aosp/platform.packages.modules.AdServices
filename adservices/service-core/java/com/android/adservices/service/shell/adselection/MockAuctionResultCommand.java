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
import static com.android.adservices.service.shell.adselection.GetAdSelectionDataArgs.FIRST_ARG_FOR_PARSING;
import static com.android.adservices.service.shell.adselection.MockAuctionResultArgs.AUCTION_RESULT;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_AD_SELECTION_MOCK_AUCTION_RESULT;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_SUCCESS;
import static com.android.internal.util.Preconditions.checkArgument;

import android.util.Log;

import com.android.adservices.service.shell.AbstractShellCommand;
import com.android.adservices.service.shell.ShellCommandArgParserHelper;
import com.android.adservices.service.shell.ShellCommandResult;
import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.Map;

public class MockAuctionResultCommand extends AbstractShellCommand {
    @VisibleForTesting public static final String CMD = "mock_auction_result";

    public static final String HELP =
            AdSelectionShellCommandFactory.COMMAND_PREFIX
                    + " "
                    + CMD
                    + " "
                    + "\n\t"
                    + AUCTION_RESULT
                    + " <auction-result>"
                    + "\n    Creates a mocked auction result and returns the ad_selection_id "
                    + " without running an actual auction on the bidding and auction server. "
                    + " The returned ad_selection_id can be used to test post auction "
                    + " workflows.";

    public MockAuctionResultCommand() {}

    @Override
    public ShellCommandResult run(PrintWriter out, PrintWriter err, String[] args) {
        Map<String, String> cliArgs;
        try {
            cliArgs = ShellCommandArgParserHelper.parseCliArguments(args, FIRST_ARG_FOR_PARSING);
            checkArgument(cliArgs.containsKey(AUCTION_RESULT));
        } catch (IllegalArgumentException exception) {
            Log.e(TAG, "Error running mock_auction_result command", exception);
            return invalidArgsError(HELP, err, COMMAND_AD_SELECTION_MOCK_AUCTION_RESULT, args);
        }
        return toShellCommandResult(RESULT_SUCCESS, COMMAND_AD_SELECTION_MOCK_AUCTION_RESULT);
    }

    @Override
    public String getCommandName() {
        return CMD;
    }

    @Override
    public int getMetricsLoggerCommand() {
        return COMMAND_AD_SELECTION_MOCK_AUCTION_RESULT;
    }

    @Override
    public String getCommandHelp() {
        return HELP;
    }
}
