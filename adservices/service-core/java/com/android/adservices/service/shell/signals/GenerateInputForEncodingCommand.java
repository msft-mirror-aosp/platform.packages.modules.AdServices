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

package com.android.adservices.service.shell.signals;

import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_APP_SIGNALS_GENERATE_INPUT_FOR_ENCODING;

import com.android.adservices.service.shell.AbstractShellCommand;
import com.android.adservices.service.shell.ShellCommandResult;
import com.android.adservices.service.shell.customaudience.CustomAudienceShellCommandFactory;
import com.android.adservices.service.stats.ShellCommandStats;
import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;

public class GenerateInputForEncodingCommand extends AbstractShellCommand {
    @VisibleForTesting public static final String CMD = "generate-input-for-encoding";

    public static final String HELP =
            CustomAudienceShellCommandFactory.COMMAND_PREFIX
                    + " "
                    + CMD
                    + " "
                    + GenerateInputForEncodingArgs.BUYER
                    + " <buyer>"
                    + " "
                    + "\n    Generate input JavaScript for signals encoding. This command generates"
                    + " JavaScript code that can be appended to a user-supplied encodeSignals"
                    + " method for offline testing.";

    @Override
    public ShellCommandResult run(PrintWriter out, PrintWriter err, String[] args) {
        // TODO(b/338182810): Add implementation for command after refactoring script engine.
        return toShellCommandResult(
                ShellCommandStats.RESULT_SUCCESS, COMMAND_APP_SIGNALS_GENERATE_INPUT_FOR_ENCODING);
    }

    @Override
    public String getCommandName() {
        return CMD;
    }

    @Override
    public int getMetricsLoggerCommand() {
        return COMMAND_APP_SIGNALS_GENERATE_INPUT_FOR_ENCODING;
    }

    @Override
    public String getCommandHelp() {
        return HELP;
    }
}
