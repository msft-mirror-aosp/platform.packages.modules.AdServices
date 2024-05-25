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
import static com.android.adservices.service.shell.adselection.GetAdSelectionDataArgs.BUYER;
import static com.android.adservices.service.shell.adselection.GetAdSelectionDataArgs.FIRST_ARG_FOR_PARSING;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_AD_SELECTION_GET_AD_SELECTION_DATA;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_SUCCESS;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_GENERIC_ERROR;
import static com.android.internal.util.Preconditions.checkArgument;

import android.adservices.common.AdTechIdentifier;
import android.util.Log;

import com.android.adservices.service.adselection.AuctionServerDataCompressor;
import com.android.adservices.service.adselection.BuyerInputGenerator;
import com.android.adservices.service.shell.AbstractShellCommand;
import com.android.adservices.service.shell.ShellCommandArgParserHelper;
import com.android.adservices.service.shell.ShellCommandResult;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FluentFuture;

import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class GetAdSelectionDataCommand extends AbstractShellCommand {
    @VisibleForTesting public static final String CMD = "get_ad_selection_data";

    public static final String HELP =
            AdSelectionShellCommandFactory.COMMAND_PREFIX
                    + " "
                    + CMD
                    + " "
                    + GetAdSelectionDataArgs.BUYER
                    + " <buyer>"
                    + "\n    Get ad selection data for a given buyer. This generates the "
                    + "GetBidsRequest protocol buffer message designed for usage together with "
                    + "`secure_invoke`.";

    private static final int DB_TIMEOUT_SEC = 3;

    private final BuyerInputGenerator mBuyerInputGenerator;

    public GetAdSelectionDataCommand(BuyerInputGenerator buyerInputGenerator) {
        mBuyerInputGenerator = buyerInputGenerator;
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
        FluentFuture<Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData>>
                buyerInputs = mBuyerInputGenerator.createCompressedBuyerInputs();
        // TODO(b/339851172): Decompress the data after b/339411896 is fixed.
        try {
            buyerInputs.get(DB_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            return toShellCommandResult(
                    RESULT_GENERIC_ERROR, COMMAND_AD_SELECTION_GET_AD_SELECTION_DATA);
        }

        return toShellCommandResult(RESULT_SUCCESS, COMMAND_AD_SELECTION_GET_AD_SELECTION_DATA);
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
