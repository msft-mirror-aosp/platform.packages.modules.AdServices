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

import static com.android.adservices.service.stats.ShellCommandStats.Command;
import static com.android.adservices.service.stats.ShellCommandStats.CommandResult;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_INVALID_ARGS;

import android.annotation.Nullable;
import android.text.TextUtils;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Locale;

/** Abstract class to implement common methods for every Shell Command */
public abstract class AbstractShellCommand implements ShellCommand {

    public static final int RESULT_GENERIC_ERROR = -1;
    public static final int RESULT_OK = 0;

    static final String ERROR_TEMPLATE_INVALID_ARGS = "Invalid cmd (%s).\n\nSyntax: %s\n";

    /** Method to return error in case of invalid arguments passed to a shell command. */
    public static ShellCommandResult invalidArgsError(
            String syntax, PrintWriter err, int metricIdentifier, String[] args) {
        err.printf(ERROR_TEMPLATE_INVALID_ARGS, Arrays.toString(args), syntax);
        return toShellCommandResult(RESULT_INVALID_ARGS, metricIdentifier);
    }

    /**
     * Converts String {@code arg} to a {@link Boolean}. Returns {@code null} if invalid or empty
     * String.
     *
     * <p>Note: We are not directly using {@code Boolean.parse} as it returns false when it's
     * invalid.
     */
    @Nullable
    public static Boolean toBoolean(String arg) {
        if (TextUtils.isEmpty(arg)) {
            return null;
        }
        // Boolean.parse returns false when it's invalid
        switch (arg.trim().toLowerCase(Locale.ROOT)) {
            case "true":
                return Boolean.TRUE;
            case "false":
                return Boolean.FALSE;
            default:
                return null;
        }
    }

    protected static ShellCommandResult toShellCommandResult(
            @CommandResult int commandResult, @Command int command) {
        return ShellCommandResult.create(commandResult, command);
    }
}
