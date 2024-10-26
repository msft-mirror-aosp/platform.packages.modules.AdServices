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

package com.android.adservices.service.shell.common;

import static com.android.adservices.service.shell.AdServicesShellCommandHandler.TAG;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_ECHO;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_SUCCESS;

import android.text.TextUtils;
import android.util.Log;

import com.android.adservices.service.shell.AbstractShellCommand;
import com.android.adservices.service.shell.ShellCommandResult;

import java.io.PrintWriter;

/**
 * This class implements echo shell command.
 *
 * <p>It just prints the given message.
 */
public final class EchoCommand extends AbstractShellCommand {
    // This command is also used by the CTS and it should never change.
    public static final String CMD_ECHO = "echo";
    public static final String HELP_ECHO =
            CMD_ECHO + " <message>\n    Prints the given message (useful to check cmd is working).";

    @Override
    public ShellCommandResult run(PrintWriter out, PrintWriter err, String[] args) {
        if (args.length < 2) {
            return invalidArgsError(HELP_ECHO, err, COMMAND_ECHO, args);
        }
        String message = concatenateMessages(args);
        if (TextUtils.isEmpty(message)) {
            return invalidArgsError(HELP_ECHO, err, COMMAND_ECHO, args);
        }

        Log.i(TAG, CMD_ECHO + " message='" + message + "'");
        out.println(message);
        return toShellCommandResult(RESULT_SUCCESS, COMMAND_ECHO);
    }

    @Override
    public String getCommandName() {
        return CMD_ECHO;
    }

    @Override
    public int getMetricsLoggerCommand() {
        return COMMAND_ECHO;
    }

    @Override
    public String getCommandHelp() {
        return HELP_ECHO;
    }

    private String concatenateMessages(String[] args) {
        StringBuilder sb = new StringBuilder();
        // Start with index 1, index 0 is the command arg
        for (int i = 1; i < args.length; i++) {
            if (i > 1) {
                sb.append(" ");
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }
}
