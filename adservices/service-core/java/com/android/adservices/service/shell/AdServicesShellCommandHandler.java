/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.adservices.service.shell.AbstractShellCommand.RESULT_GENERIC_ERROR;
import static com.android.adservices.service.shell.AbstractShellCommand.RESULT_OK;
import static com.android.adservices.service.shell.common.EchoCommand.HELP_ECHO;
import static com.android.adservices.service.shell.common.IsAllowedAdSelectionAccessCommand.HELP_IS_ALLOWED_AD_SELECTION_ACCESS;
import static com.android.adservices.service.shell.common.IsAllowedAttributionAccessCommand.HELP_IS_ALLOWED_ATTRIBUTION_ACCESS;
import static com.android.adservices.service.shell.common.IsAllowedCustomAudiencesAccessCommand.HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS;
import static com.android.adservices.service.shell.common.IsAllowedProtectedSignalsAccessCommand.HELP_IS_ALLOWED_PROTECTED_SIGNALS_ACCESS;
import static com.android.adservices.service.shell.common.IsAllowedTopicsAccessCommand.HELP_IS_ALLOWED_TOPICS_ACCESS;

import android.annotation.Nullable;
import android.util.Log;

import com.android.adservices.service.stats.ShellCommandStats;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import com.google.common.collect.ImmutableMap;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Objects;

// TODO(b/308009734): STOPSHIP - document that it's up to each command implementation to check about
// caller's permission, whether device is userdebug, etc...

// TODO(b/308009734): arg parsing logic mostly copied from on BasicShellCommandHandler, it might be
// worth to refactor it once the handler is called by both system_server and service.

/**
 * Service-side version of {@code cmd adservices_manager}.
 *
 * <p>By convention, methods implementing commands should be prefixed with {@code run}.
 */
public final class AdServicesShellCommandHandler {
    @VisibleForTesting static final String ERROR_EMPTY_COMMAND = "Must provide a non-empty command";

    @VisibleForTesting static final String CMD_SHORT_HELP = "-h";
    @VisibleForTesting static final String CMD_HELP = "help";

    // TODO(b/280460130): use adservice helpers for tag name / logging methods
    public static final String TAG = "AdServicesShellCmd";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final ShellCommandFactory COMMON_SHELL_COMMAND_FACTORY =
            CommonShellCommandFactory.getInstance();
    private final PrintWriter mOut;
    private final PrintWriter mErr;
    private final ImmutableMap<String, ShellCommandFactory> mShellCommandFactories;
    private String[] mArgs;
    private int mArgPos;
    private String mCurArgData;

    /** If PrintWriter {@code err} is not provided, we use {@code out} for the {@code err}. */
    public AdServicesShellCommandHandler(
            PrintWriter out, ShellCommandFactorySupplier shellCommandFactorySupplier) {
        this(out, /* err= */ out, shellCommandFactorySupplier);
    }

    public AdServicesShellCommandHandler(
            PrintWriter out,
            PrintWriter err,
            ShellCommandFactorySupplier shellCommandFactorySupplier) {
        mOut = Objects.requireNonNull(out, "out cannot be null");
        mErr = Objects.requireNonNull(err, "err cannot be null");
        Objects.requireNonNull(
                shellCommandFactorySupplier, "shellCommandFactorySupplier cannot be null");
        mShellCommandFactories = shellCommandFactorySupplier.getShellCommandFactories();
    }

    /** Runs the given command ({@code args[0]}) and optional arguments */
    public int run(String... args) {
        Objects.requireNonNull(args, "args cannot be null");
        Preconditions.checkArgument(
                args.length >= 1, "must have at least one argument (the command itself)");
        if (DEBUG) {
            Log.d(TAG, "run(): " + Arrays.toString(args));
        }
        mArgs = args;
        String cmd = getNextArg();

        int res = RESULT_GENERIC_ERROR;
        try {
            res = onCommand(cmd);
            if (DEBUG) {
                Log.d(TAG, "Executed command " + cmd);
            }
        } catch (Throwable e) {
            // TODO(b/308009734): need to test this
            mErr.printf("Exception occurred while executing %s\n", Arrays.toString(mArgs));
            e.printStackTrace(mOut);
        } finally {
            if (DEBUG) {
                Log.d(TAG, "Flushing output");
            }
            mOut.flush();
            mErr.flush();
        }
        if (DEBUG) {
            Log.d(TAG, "Sending command result: " + res);
        }
        return res;
    }

    /******************
     * Helper methods *
     ******************/
    @Nullable
    private String getNextArg() {
        if (mArgs == null) {
            throw new IllegalStateException("INTERNAL ERROR: getNextArg() called before run()");
        }
        if (mCurArgData != null) {
            String arg = mCurArgData;
            mCurArgData = null;
            return arg;
        } else if (mArgPos < mArgs.length) {
            return mArgs[mArgPos++];
        } else {
            return null;
        }
    }

    /****************************************************************************
     * Commands - for each new one, add onHelp(), onCommand(), and runCommand() *
     ****************************************************************************/

    private static void onHelp(PrintWriter pw) {
        pw.printf("%s\n\n", HELP_ECHO);
        pw.printf("%s\n\n", HELP_IS_ALLOWED_ATTRIBUTION_ACCESS);
        pw.printf("%s\n\n", HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS);
        pw.printf("%s\n\n", HELP_IS_ALLOWED_PROTECTED_SIGNALS_ACCESS);
        pw.printf("%s\n\n", HELP_IS_ALLOWED_AD_SELECTION_ACCESS);
        pw.printf("%s\n\n", HELP_IS_ALLOWED_TOPICS_ACCESS);
        pw.printf("%s\n\n", CustomAudienceListCommand.HELP);
        pw.printf("%s\n\n", CustomAudienceViewCommand.HELP);
        pw.printf("%s\n\n", CustomAudienceRefreshCommand.HELP);
    }

    private int onCommand(String cmd) {
        switch (cmd) {
            case CMD_SHORT_HELP:
            case CMD_HELP:
                onHelp(mOut);
                return RESULT_OK;
            case "":
                mErr.println(ERROR_EMPTY_COMMAND);
                return RESULT_GENERIC_ERROR;
            default:
                // TODO (b/308009734): Move other shell commands implement ICommand interface.
                ShellCommand shellCommand;
                if (mShellCommandFactories.containsKey(cmd)) {
                    ShellCommandFactory shellCommandFactory = mShellCommandFactories.get(cmd);
                    String subCommand = getNextArg();
                    shellCommand = shellCommandFactory.getShellCommand(subCommand);
                } else {
                    shellCommand = COMMON_SHELL_COMMAND_FACTORY.getShellCommand(cmd);
                }

                if (shellCommand == null) {
                    mErr.printf("Unknown command: %s\n", cmd);
                    mErr.println("Use -h for help.");
                    return RESULT_GENERIC_ERROR;
                }
                ShellCommandResult shellCommandResult = shellCommand.run(mOut, mErr, mArgs);
                return convertToExternalResultCode(shellCommandResult.getResultCode());
        }
    }

    private int convertToExternalResultCode(@ShellCommandStats.CommandResult int commandResult) {
        switch (commandResult) {
            case ShellCommandStats.RESULT_SUCCESS -> {
                return RESULT_OK;
            }
            default -> {
                return RESULT_GENERIC_ERROR;
            }
        }
    }
}
