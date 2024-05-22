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
import static com.android.internal.util.Preconditions.checkArgument;

import android.util.Log;

import com.android.adservices.service.shell.AbstractShellCommand;
import com.android.adservices.service.shell.ShellCommandArgParserHelper;
import com.android.adservices.service.shell.ShellCommandResult;
import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.Map;

public class GetAdSelectionDataCommand extends AbstractShellCommand {
    @VisibleForTesting public static final String CMD = "get-ad-selection-data";

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
