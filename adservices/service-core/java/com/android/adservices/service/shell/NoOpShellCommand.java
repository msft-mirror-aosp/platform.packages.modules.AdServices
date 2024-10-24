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

package com.android.adservices.service.shell;

import static com.android.adservices.service.shell.AdServicesShellCommandHandler.TAG;
import static com.android.adservices.service.stats.ShellCommandStats.Command;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_NOT_ENABLED;

import android.util.Log;

import java.io.PrintWriter;

public final class NoOpShellCommand extends AbstractShellCommand {
    public static final String RESPONSE_MSG = "%s is disabled.";
    private final String mDebugFlagKey;
    private final String mCommandName;
    private final int mMetricsLoggerCommand;

    public NoOpShellCommand(
            final String commandName, @Command int metricsLoggerCommand, String debugFlagKey) {
        mCommandName = commandName;
        mMetricsLoggerCommand = metricsLoggerCommand;
        mDebugFlagKey = debugFlagKey;
    }

    public NoOpShellCommand(final String commandName, @Command int metricsLoggerCommand) {
        this(commandName, metricsLoggerCommand, /* debugFlagKey= */ "");
    }

    @Override
    public ShellCommandResult run(PrintWriter out, PrintWriter err, String[] args) {
        if (mDebugFlagKey.isEmpty()) {
            Log.d(TAG, String.format("Command is disabled. %s cannot be executed.", mCommandName));
        } else {
            Log.d(
                    TAG,
                    String.format(
                            "%s is disabled. %s cannot be executed.", mDebugFlagKey, mCommandName));
        }
        err.print(String.format(RESPONSE_MSG, mCommandName));
        return toShellCommandResult(RESULT_NOT_ENABLED, mMetricsLoggerCommand);
    }

    @Override
    public String getCommandName() {
        return mCommandName;
    }

    @Override
    public int getMetricsLoggerCommand() {
        return mMetricsLoggerCommand;
    }

    @Override
    public String getCommandHelp() {
        return "";
    }
}
