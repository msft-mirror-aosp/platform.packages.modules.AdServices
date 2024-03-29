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

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.adservices.data.adselection.ConsentedDebugConfigurationDao;
import com.android.adservices.data.adselection.DBConsentedDebugConfiguration;
import com.android.adservices.service.shell.AbstractShellCommand;
import com.android.adservices.service.shell.ShellCommandArgParserHelper;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class ConsentedDebugShellCommand extends AbstractShellCommand {
    @VisibleForTesting public static final String CMD = "consented_debug";
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
                    + " <optional param to set expiry in days>";

    @VisibleForTesting
    public static final String HELP =
            AdSelectionShellCommandFactory.COMMAND_PREFIX
                    + " "
                    + CMD
                    + "\n"
                    + "\toptions: "
                    + "\n\t\t"
                    + ENABLE_SUB_COMMAND_HELP
                    + "\n\t\t"
                    + DISABLE_SUB_CMD
                    + "\n\t\t"
                    + VIEW_SUB_CMD
                    + "\n\t\t"
                    + HELP_SUB_CMD
                    + "\n";

    private static final int SUB_COMMAND_INDEX = 2;
    private static final int SUB_COMMAND_ARG_INDEX = 3;
    private final ConsentedDebugConfigurationDao mConsentedDebugConfigurationDao;

    ConsentedDebugShellCommand(
            @NonNull ConsentedDebugConfigurationDao consentedDebugConfigurationDao) {
        mConsentedDebugConfigurationDao = Objects.requireNonNull(consentedDebugConfigurationDao);
    }

    @Override
    public int run(PrintWriter out, PrintWriter err, String[] args) {
        try {
            String subCommand = validateAndReturnSubCommand(args);
            ImmutableMap<String, String> cliArgs =
                    ShellCommandArgParserHelper.parseCliArguments(args, SUB_COMMAND_ARG_INDEX);
            Log.d(
                    TAG,
                    String.format(
                            "running sub command %s in consented debug shell command", subCommand));
            String output;
            switch (subCommand) {
                case ENABLE_SUB_CMD -> output = runEnableSubCommand(cliArgs);
                case DISABLE_SUB_CMD -> output = runDisableSubCommand(cliArgs);
                case VIEW_SUB_CMD -> output = runViewSubCommand(cliArgs);
                case HELP_SUB_CMD -> output = HELP;
                default -> throw new IllegalArgumentException("Unknown sub command");
            }
            out.print(output);
            return RESULT_OK;
        } catch (IllegalArgumentException exception) {
            Log.e(TAG, "IllegalArgumentException while running consented_debug command", exception);
            return invalidArgsError(HELP, err, args);
        } catch (RuntimeException exception) {
            Log.e(TAG, "RuntimeException while running consented_debug command", exception);
            err.print(exception.getMessage());
            return RESULT_GENERIC_ERROR;
        }
    }

    @Override
    public String getCommandName() {
        return CMD;
    }

    private String validateAndReturnSubCommand(String[] args) {
        // 0th index ad_selection,
        // 1st index is consented_debugging.
        if (args.length <= SUB_COMMAND_INDEX) {
            Log.e(TAG, "Consented Debug Sub Command not found");
            throw new IllegalArgumentException("Consented Debug Sub Command not found");
        }
        return args[SUB_COMMAND_INDEX];
    }

    private String runEnableSubCommand(ImmutableMap<String, String> inputArgs) {
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
            return ENABLE_SUCCESS;
        } catch (Exception exception) {
            Log.e(TAG, "Exception while persisting consented debug configuration in DB", exception);
            throw new RuntimeException(ENABLE_ERROR, exception);
        }
    }

    private String runDisableSubCommand(ImmutableMap<String, String> inputArgs) {
        Preconditions.checkArgument(
                inputArgs.isEmpty(), "no argument expected for %s", DISABLE_SUB_CMD);
        try {
            mConsentedDebugConfigurationDao.deleteAllConsentedDebugConfigurations();
            Log.d(TAG, "Successfully deleted all consented debug configurations");
            return DISABLE_SUCCESS;
        } catch (Exception exception) {
            Log.e(TAG, "Exception while deleting consented debug configuration DB", exception);
            throw new RuntimeException(DISABLE_ERROR, exception);
        }
    }

    private String runViewSubCommand(ImmutableMap<String, String> inputArgs) {
        Preconditions.checkArgument(
                inputArgs.isEmpty(), "no argument expected for %s", VIEW_SUB_CMD);
        try {
            List<DBConsentedDebugConfiguration> consentedDebugConfigurations =
                    mConsentedDebugConfigurationDao.getAllActiveConsentedDebugConfigurations(
                            Instant.now(), 1);
            if (consentedDebugConfigurations == null || consentedDebugConfigurations.isEmpty()) {
                Log.d(TAG, VIEW_SUCCESS_NO_CONFIGURATION);
                return VIEW_SUCCESS_NO_CONFIGURATION;
            }
            String json = convertToJson(consentedDebugConfigurations.get(0));
            Log.d(
                    TAG,
                    String.format(
                            "converted consented debug configuration to JSON. Json: %s", json));
            return json;
        } catch (Exception exception) {
            Log.e(TAG, "Exception during view consented debug configuration", exception);
            throw new RuntimeException(VIEW_ERROR, exception);
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
