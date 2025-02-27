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

package com.android.adservices.service.shell.adservicesapi;

import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_DEV_SESSION;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_SUCCESS;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.devapi.DevSessionController;
import com.android.adservices.service.devapi.DevSessionControllerResult;
import com.android.adservices.service.shell.AbstractShellCommand;
import com.android.adservices.service.shell.ShellCommandResult;
import com.android.adservices.service.stats.ShellCommandStats;

import com.google.common.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class DevSessionCommand extends AbstractShellCommand {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();

    private static final String ERROR_RESET_WARNING =
            "WARNING: Enabling a development session will cause the AdServices database to be "
                    + "reset to a factory new state.";

    public static final String CMD = "dev-session";
    public static final String SUB_CMD_START = "start";
    public static final String SUB_CMD_END = "end";
    public static final String ARG_ERASE_DB = "--erase-db";

    @VisibleForTesting
    public static final String HELP =
            AdServicesApiShellCommandFactory.COMMAND_PREFIX
                    + CMD
                    + " <option>Enables a development session. During a development session,"
                    + " throttling, timeouts and UX consent are ignored in Protected Audience and"
                    + " Protected App Signals. "
                    + ERROR_RESET_WARNING
                    + "\nValid options: "
                    + SUB_CMD_START
                    + " and "
                    + SUB_CMD_END;

    @VisibleForTesting
    public static final String ERROR_UNKNOWN_SUB_CMD =
            "Subcommand must be '" + SUB_CMD_START + "' or '" + SUB_CMD_END + "'";

    @VisibleForTesting
    public static final String ERROR_NEED_ACKNOWLEDGEMENT =
            ERROR_RESET_WARNING + "Re-run with the " + ARG_ERASE_DB + " flag to acknowledge.";

    @VisibleForTesting
    public static final String ERROR_ALREADY_IN_DEV_MODE =
            "Already in developer mode. Call '"
                    + SUB_CMD_END
                    + "' and '"
                    + SUB_CMD_START
                    + "' to reset state.";

    @VisibleForTesting
    public static final String ERROR_FAILED_TO_RESET =
            "Failed to reset device state and set developer mode.";

    @VisibleForTesting
    public static final String OUTPUT_SUCCESS_FORMAT = "Successfully changed developer mode to: %s";

    static final int TIMEOUT_SEC = 5;
    private final DevSessionController mDevSessionController;

    public DevSessionCommand(DevSessionController devSessionController) {
        mDevSessionController = devSessionController;
    }

    @Override
    public ShellCommandResult run(PrintWriter out, PrintWriter err, String[] args) {
        if (args.length < 3) {
            return invalidArgsError(getCommandHelp(), err, getMetricsLoggerCommand(), args);
        }

        if (!SUB_CMD_START.equals(args[2]) && !SUB_CMD_END.equals(args[2])) {
            sLogger.v("Could not enter or exit dev mode:" + ERROR_UNKNOWN_SUB_CMD);
            err.write(ERROR_UNKNOWN_SUB_CMD);
            return invalidArgsError(getCommandHelp(), err, getMetricsLoggerCommand(), args);
        }

        boolean shouldSetDevSessionEnabled = SUB_CMD_START.equals(args[2]);
        sLogger.v("shouldSetDevSessionEnabled: %b", shouldSetDevSessionEnabled);

        if (args.length < 4 || !ARG_ERASE_DB.equals(args[3])) {
            sLogger.v("Could not enter or exit dev mode:" + ERROR_NEED_ACKNOWLEDGEMENT);
            err.write(ERROR_NEED_ACKNOWLEDGEMENT);
            return invalidArgsError(getCommandHelp(), err, getMetricsLoggerCommand(), args);
        }

        DevSessionControllerResult result;
        try {
            Future<DevSessionControllerResult> future =
                    shouldSetDevSessionEnabled
                            ? mDevSessionController.startDevSession()
                            : mDevSessionController.endDevSession();
            result = future.get(TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            sLogger.e(e, ERROR_FAILED_TO_RESET);
            err.write(ERROR_FAILED_TO_RESET);
            return toShellCommandResult(
                    ShellCommandStats.RESULT_GENERIC_ERROR, COMMAND_DEV_SESSION);
        }

        if (result.equals(DevSessionControllerResult.NO_OP)) {
            sLogger.e(ERROR_ALREADY_IN_DEV_MODE);
            err.write(ERROR_ALREADY_IN_DEV_MODE);
            return toShellCommandResult(
                    ShellCommandStats.RESULT_GENERIC_ERROR, COMMAND_DEV_SESSION);
        } else if (result.equals(DevSessionControllerResult.SUCCESS)) {
            String successMessage =
                    String.format(OUTPUT_SUCCESS_FORMAT, shouldSetDevSessionEnabled);
            sLogger.v(successMessage);
            out.write(successMessage);
            return toShellCommandResult(RESULT_SUCCESS, COMMAND_DEV_SESSION);
        } else {
            sLogger.e(ERROR_FAILED_TO_RESET);
            err.write(ERROR_FAILED_TO_RESET);
            return toShellCommandResult(
                    ShellCommandStats.RESULT_GENERIC_ERROR, COMMAND_DEV_SESSION);
        }
    }

    @Override
    public String getCommandName() {
        return CMD;
    }

    @Override
    public int getMetricsLoggerCommand() {
        return COMMAND_DEV_SESSION;
    }

    @Override
    public String getCommandHelp() {
        return HELP;
    }
}
