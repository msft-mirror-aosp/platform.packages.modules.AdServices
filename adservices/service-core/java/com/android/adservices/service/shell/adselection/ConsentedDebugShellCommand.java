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
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_AD_SELECTION_CONSENTED_DEBUG_DISABLE;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_AD_SELECTION_CONSENTED_DEBUG_ENABLE;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_AD_SELECTION_CONSENTED_DEBUG_HELP;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_AD_SELECTION_CONSENTED_DEBUG_VIEW;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_UNKNOWN;
import static com.android.adservices.service.stats.ShellCommandStats.Command;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_SUCCESS;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.adservices.data.adselection.ConsentedDebugConfigurationDao;
import com.android.adservices.data.adselection.DBConsentedDebugConfiguration;
import com.android.adservices.service.shell.AbstractShellCommand;
import com.android.adservices.service.shell.ShellCommandArgParserHelper;
import com.android.adservices.service.shell.ShellCommandResult;
import com.android.adservices.service.stats.ShellCommandStats;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ConsentedDebugShellCommand extends AbstractShellCommand {
    @VisibleForTesting public static final String CMD = "consented-debug";
    // SUB COMMANDS
    @VisibleForTesting public static final String ENABLE_SUB_CMD = "enable";
    @VisibleForTesting public static final String DISABLE_SUB_CMD = "disable";
    @VisibleForTesting public static final String HELP_SUB_CMD = "help";
    @VisibleForTesting public static final String VIEW_SUB_CMD = "view";

    // JSON Property names
    @VisibleForTesting public static final String JSON_IS_CONSENTED = "is_consented";
    @VisibleForTesting public static final String JSON_DEBUG_TOKEN = "secret_debug_token";
    @VisibleForTesting public static final String JSON_EXPIRY = "expiry_timestamp";
    @VisibleForTesting public static final String JSON_CREATION_TIME = "creation_timestamp";

    // Output success messages
    @VisibleForTesting
    public static final String ENABLE_SUCCESS = "Successfully enabled consented debugging.";

    @VisibleForTesting
    public static final String DISABLE_SUCCESS = "Successfully disabled consented debugging.";

    @VisibleForTesting
    public static final String VIEW_SUCCESS_NO_CONFIGURATION =
            "No configuration for consented debug found";

    // Output error messages
    @VisibleForTesting
    public static final String ENABLE_ERROR = "Unable to enable consented debug configuration.";

    @VisibleForTesting
    public static final String DISABLE_ERROR = "Unable to disable consented debugging.";

    @VisibleForTesting
    public static final String VIEW_ERROR = "Unable to fetch consented debug configuration";

    private static final String ENABLE_SUB_COMMAND_HELP =
            ENABLE_SUB_CMD
                    + " "
                    + ConsentedDebugEnableArgs.SECRET_DEBUG_TOKEN_ARG_NAME
                    + " <min 6 character length token>"
                    + " "
                    + ConsentedDebugEnableArgs.EXPIRY_IN_HOURS_ARG_NAME
                    + " <optional param to set expiry in hours>";

    @VisibleForTesting
    public static final String HELP =
            AdSelectionShellCommandFactory.COMMAND_PREFIX
                    + " "
                    + CMD
                    + "\n"
                    + "  options:"
                    + "\n    "
                    + ENABLE_SUB_COMMAND_HELP
                    + "\n    "
                    + DISABLE_SUB_CMD
                    + "\n    "
                    + VIEW_SUB_CMD
                    + "\n    "
                    + HELP_SUB_CMD;

    private static final int SUB_COMMAND_INDEX = 2;
    private static final int SUB_COMMAND_ARG_INDEX = 3;
    private static final int CONSENTED_DEBUG_DEFAULT_COMMAND = COMMAND_UNKNOWN;
    private static final Map<String, Integer> SUB_COMMAND_TO_METRICS_LOGGER_COMMAND_MAP =
            ImmutableMap.of(
                    ENABLE_SUB_CMD,
                    COMMAND_AD_SELECTION_CONSENTED_DEBUG_ENABLE,
                    DISABLE_SUB_CMD,
                    COMMAND_AD_SELECTION_CONSENTED_DEBUG_DISABLE,
                    VIEW_SUB_CMD,
                    COMMAND_AD_SELECTION_CONSENTED_DEBUG_VIEW,
                    HELP_SUB_CMD,
                    COMMAND_AD_SELECTION_CONSENTED_DEBUG_HELP);
    private final ConsentedDebugConfigurationDao mConsentedDebugConfigurationDao;

    ConsentedDebugShellCommand(
            @NonNull ConsentedDebugConfigurationDao consentedDebugConfigurationDao) {
        mConsentedDebugConfigurationDao = Objects.requireNonNull(consentedDebugConfigurationDao);
    }

    @Override
    public int getMetricsLoggerCommand() {
        return CONSENTED_DEBUG_DEFAULT_COMMAND;
    }

    @Override
    public String getCommandHelp() {
        return HELP;
    }

    @Override
    public ShellCommandResult run(PrintWriter out, PrintWriter err, String[] args) {
        String subCommand;
        ImmutableMap<String, String> cliArgs;
        try {
            subCommand = validateAndReturnSubCommand(args);
            cliArgs = ShellCommandArgParserHelper.parseCliArguments(args, SUB_COMMAND_ARG_INDEX);
        } catch (IllegalArgumentException exception) {
            Log.e(TAG, "IllegalArgumentException while running consented-debug command", exception);
            return invalidArgsError(HELP, err, CONSENTED_DEBUG_DEFAULT_COMMAND, args);
        }
        int metricsLoggerCommand =
                SUB_COMMAND_TO_METRICS_LOGGER_COMMAND_MAP.getOrDefault(
                        subCommand, CONSENTED_DEBUG_DEFAULT_COMMAND);
        Log.d(
                TAG,
                "metricsLoggerCommand for subCommand "
                        + subCommand
                        + " is: "
                        + metricsLoggerCommand);
        try {
            switch (subCommand) {
                case ENABLE_SUB_CMD -> {
                    return runEnableSubCommand(out, err, metricsLoggerCommand, cliArgs);
                }
                case DISABLE_SUB_CMD -> {
                    return runDisableSubCommand(out, err, metricsLoggerCommand, cliArgs);
                }
                case VIEW_SUB_CMD -> {
                    return runViewSubCommand(out, err, metricsLoggerCommand, cliArgs);
                }
                case HELP_SUB_CMD -> {
                    out.print(HELP);
                    return toShellCommandResult(RESULT_SUCCESS, metricsLoggerCommand);
                }
                default -> {
                    return invalidArgsError(HELP, err, CONSENTED_DEBUG_DEFAULT_COMMAND, args);
                }
            }
        } catch (IllegalArgumentException exception) {
            Log.e(
                    TAG,
                    "IllegalArgumentException while running consented-debug sub command. Using"
                            + " metrics logger as: "
                            + metricsLoggerCommand,
                    exception);
            return invalidArgsError(HELP, err, metricsLoggerCommand, args);
        }
    }



    @Override
    public String getCommandName() {
        return CMD;
    }
    private String validateAndReturnSubCommand(String[] args) {
        // 0th index ad-selection,
        // 1st index is consented-debug.
        if (args.length <= SUB_COMMAND_INDEX) {
            Log.e(TAG, "Consented Debug Sub Command not found");
            throw new IllegalArgumentException("Consented Debug Sub Command not found");
        }
        String subCommand = args[SUB_COMMAND_INDEX];
        if (!SUB_COMMAND_TO_METRICS_LOGGER_COMMAND_MAP.containsKey(subCommand)) {
            Log.e(TAG, "Consented Debug Sub Command in not valid. Sub command: " + subCommand);
            throw new IllegalArgumentException(
                    "Invalid Consented Debug Sub Command: " + subCommand);
        }
        return args[SUB_COMMAND_INDEX];
    }

    private ShellCommandResult runEnableSubCommand(
            PrintWriter out,
            PrintWriter err,
            @Command int metricsLoggerCommand,
            ImmutableMap<String, String> inputArgs) {
        ConsentedDebugEnableArgs consentedDebugEnableArgs =
                ConsentedDebugEnableArgs.parseCliArgs(inputArgs);
        Log.d(TAG, "Parsed ConsentedDebugEnableArgs: " + consentedDebugEnableArgs);
        try {
            DBConsentedDebugConfiguration dbConsentedDebugConfiguration =
                    DBConsentedDebugConfiguration.builder()
                            .setDebugToken(consentedDebugEnableArgs.getSecretDebugToken())
                            .setIsConsentProvided(true)
                            .setExpiryTimestamp(consentedDebugEnableArgs.getExpiryTimestamp())
                            .build();
            mConsentedDebugConfigurationDao.deleteExistingConsentedDebugConfigurationsAndPersist(
                    dbConsentedDebugConfiguration);
            Log.d(TAG, "Persisted consented debug configuration in DB");
            out.print(ENABLE_SUCCESS);
            return toShellCommandResult(RESULT_SUCCESS, metricsLoggerCommand);
        } catch (Exception exception) {
            Log.e(TAG, "Exception while persisting consented debug configuration in DB", exception);
            err.print(exception.getMessage() + " " + ENABLE_ERROR);
            return toShellCommandResult(
                    ShellCommandStats.RESULT_GENERIC_ERROR, metricsLoggerCommand);
        }
    }

    private ShellCommandResult runDisableSubCommand(
            PrintWriter out,
            PrintWriter err,
            @Command int metricsLoggerCommand,
            ImmutableMap<String, String> inputArgs) {
        Preconditions.checkArgument(
                inputArgs.isEmpty(), "no argument expected for %s", DISABLE_SUB_CMD);
        try {
            mConsentedDebugConfigurationDao.deleteAllConsentedDebugConfigurations();
            Log.d(TAG, "Successfully deleted all consented debug configurations");
            out.print(DISABLE_SUCCESS);
            return toShellCommandResult(RESULT_SUCCESS, metricsLoggerCommand);
        } catch (Exception exception) {
            Log.e(TAG, "Exception while deleting consented debug configuration DB", exception);
            err.print(exception.getMessage() + " " + DISABLE_ERROR);
            return toShellCommandResult(
                    ShellCommandStats.RESULT_GENERIC_ERROR, metricsLoggerCommand);
        }
    }

    private ShellCommandResult runViewSubCommand(
            PrintWriter out,
            PrintWriter err,
            @Command int metricsLoggerCommand,
            ImmutableMap<String, String> inputArgs) {
        Preconditions.checkArgument(
                inputArgs.isEmpty(), "no argument expected for %s", VIEW_SUB_CMD);
        try {
            List<DBConsentedDebugConfiguration> consentedDebugConfigurations =
                    mConsentedDebugConfigurationDao.getAllActiveConsentedDebugConfigurations(
                            Instant.now(), 1);
            if (consentedDebugConfigurations == null || consentedDebugConfigurations.isEmpty()) {
                Log.d(TAG, VIEW_SUCCESS_NO_CONFIGURATION);
                out.print(VIEW_SUCCESS_NO_CONFIGURATION);
                return toShellCommandResult(RESULT_SUCCESS, metricsLoggerCommand);
            }
            String json = convertToJson(consentedDebugConfigurations.get(0));
            Log.d(
                    TAG,
                    String.format(
                            "converted consented debug configuration to JSON. Json: %s", json));
            out.print(json);
            return toShellCommandResult(RESULT_SUCCESS, metricsLoggerCommand);
        } catch (Exception exception) {
            Log.e(TAG, "Exception during view consented debug configuration", exception);
            err.print(exception.getMessage() + " " + VIEW_ERROR);
            return toShellCommandResult(
                    ShellCommandStats.RESULT_GENERIC_ERROR, metricsLoggerCommand);
        }
    }

    private String convertToJson(DBConsentedDebugConfiguration consentedDebugConfiguration)
            throws JSONException {
        return new JSONObject()
                .put(JSON_IS_CONSENTED, consentedDebugConfiguration.getIsConsentProvided())
                .put(JSON_DEBUG_TOKEN, consentedDebugConfiguration.getDebugToken())
                .put(JSON_EXPIRY, consentedDebugConfiguration.getExpiryTimestamp())
                .put(JSON_CREATION_TIME, consentedDebugConfiguration.getCreationTimestamp())
                .toString();
    }
}
