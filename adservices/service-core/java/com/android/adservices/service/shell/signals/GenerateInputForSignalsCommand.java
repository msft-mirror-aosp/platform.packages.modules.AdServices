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

import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_SIGNALS_GENERATE_INPUT_FOR_SIGNALS;

import com.android.adservices.service.shell.AbstractShellCommand;
import com.android.adservices.service.shell.ShellCommandResult;
import com.android.adservices.service.shell.customaudience.CustomAudienceShellCommandFactory;
import com.android.adservices.service.stats.ShellCommandStats;
import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;

public class GenerateInputForSignalsCommand extends AbstractShellCommand {
    @VisibleForTesting public static final String CMD = "generate-input-for-signals";

    public static final String HELP =
            CustomAudienceShellCommandFactory.COMMAND_PREFIX
                    + " "
                    + CMD
                    + " "
                    + GenerateInputForSignalsArgs.BUYER
                    + " <buyer>"
                    + " "
                    + "\n    Generate input JavaScript for signals. This command generates wrapper "
                    + "JavaScript code that can be appended to a user-supplied encodeSignals method"
                    + "for offline testing.";

    @Override
    public ShellCommandResult run(PrintWriter out, PrintWriter err, String[] args) {
        // TODO(b/338182810): Add implementation for command after refactoring script engine.
        return toShellCommandResult(
                ShellCommandStats.RESULT_SUCCESS, COMMAND_SIGNALS_GENERATE_INPUT_FOR_SIGNALS);
    }

    @Override
    public String getCommandName() {
        return CMD;
    }

    @Override
    public int getMetricsLoggerCommand() {
        return COMMAND_SIGNALS_GENERATE_INPUT_FOR_SIGNALS;
    }

    @Override
    public String getCommandHelp() {
        return HELP;
    }
}
