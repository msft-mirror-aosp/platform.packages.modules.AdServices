/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static android.adservices.common.AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN;
import static android.adservices.common.AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT;
import static android.adservices.common.AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN;
import static android.adservices.common.Module.ADID;
import static android.adservices.common.Module.MEASUREMENT;
import static android.adservices.common.Module.ON_DEVICE_PERSONALIZATION;
import static android.adservices.common.Module.PROTECTED_APP_SIGNALS;
import static android.adservices.common.Module.PROTECTED_AUDIENCE;
import static android.adservices.common.Module.TOPICS;

import static com.android.adservices.service.shell.AdServicesShellCommandHandler.TAG;
import static com.android.adservices.service.shell.adservicesapi.AdServicesApiShellCommandFactory.COMMAND_PREFIX;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_SET_USER_CHOICES;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_SUCCESS;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesOutcomeReceiver;
import android.adservices.common.UpdateAdServicesUserChoicesParams;
import android.annotation.SuppressLint;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.shell.AbstractShellCommand;
import com.android.adservices.service.shell.ShellCommandArgParserHelper;
import com.android.adservices.service.shell.ShellCommandResult;
import com.android.adservices.service.stats.ShellCommandStats;
import com.android.adservices.shared.common.ApplicationContextSingleton;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.PrintWriter;
import java.util.Map;

/**
 * This class implements set_user_choice shell command.
 *
 * <p>It triggers the adservices related apis.
 */
@RequiresApi(Build.VERSION_CODES.S)
public class SetUserChoicesCommand extends AbstractShellCommand {
    /** Runs the shell command and returns the result. */
    public static final String CMD_SET_USER_CHOICES = "set-user-choices";

    private static final Map<String, Integer> MODULE_CODE_INTDEF_MAPPING =
            Map.of(
                    "msmt", MEASUREMENT,
                    "pa", PROTECTED_AUDIENCE,
                    "pas", PROTECTED_APP_SIGNALS,
                    "topics", TOPICS,
                    "odp", ON_DEVICE_PERSONALIZATION,
                    "adid", ADID);

    private static final Map<String, Integer> USER_CHOICES_INTDEF_MAPPING =
            Map.of(
                    "unknown", USER_CHOICE_UNKNOWN,
                    "opted-in", USER_CHOICE_OPTED_IN,
                    "opted-out", USER_CHOICE_OPTED_OUT);

    public static final String HELP =
            "usage:\n "
                    + COMMAND_PREFIX
                    + " "
                    + CMD_SET_USER_CHOICES
                    + " [--msmt unknown|opted-in|opted-out][--pa unknown|opted-in|opted-out] "
                    + " [--pas unknown|opted-in|opted-out][--topics unknown|opted-in|opted-out] "
                    + " [--odp unknown|opted-in|opted-out][--adid unknown|opted-in|opted-out] ";

    private static final int ARG_PARSE_START_INDEX = 2;

    /** Runs the shell command and returns the result. */
    @SuppressLint("MissingPermission")
    @Override
    public ShellCommandResult run(PrintWriter out, PrintWriter err, String[] args) {
        // args length should be even number, first and second arg is factory and command name
        if (args.length < 2 || args.length % 2 != 0) {
            return invalidArgsError(HELP, err, COMMAND_SET_USER_CHOICES, args);
        }
        ConsentManager consentManager = ConsentManager.getInstance();
        ImmutableMap<String, String> paramMap =
                ShellCommandArgParserHelper.parseCliArguments(
                        args, ARG_PARSE_START_INDEX, /* removeKeyPrefix= */ true);

        UpdateAdServicesUserChoicesParams.Builder updateAdServicesUserChoicesParamsBuilder =
                new UpdateAdServicesUserChoicesParams.Builder();

        for (Map.Entry<String, String> entry : paramMap.entrySet()) {
            if (MODULE_CODE_INTDEF_MAPPING.containsKey(entry.getKey())
                    && USER_CHOICES_INTDEF_MAPPING.containsKey(entry.getValue())) {
                updateAdServicesUserChoicesParamsBuilder.setUserChoice(
                        MODULE_CODE_INTDEF_MAPPING.get(entry.getKey()),
                        USER_CHOICES_INTDEF_MAPPING.get(entry.getValue()));
            }
        }

        AdServicesCommonManager commonManager =
                AdServicesCommonManager.get(ApplicationContextSingleton.get());

        ListenableFuture<Boolean> responseFuture =
                CallbackToFutureAdapter.getFuture(
                        completer -> {
                            commonManager.requestAdServicesModuleUserChoices(
                                    updateAdServicesUserChoicesParamsBuilder.build(),
                                    AdServicesExecutors.getLightWeightExecutor(),
                                    new AdServicesOutcomeReceiver<>() {
                                        @Override
                                        public void onResult(Void result) {
                                            completer.set(true);
                                        }

                                        @Override
                                        public void onError(Exception exception) {
                                            completer.setException(exception);
                                        }
                                    });
                            return "set user choices data";
                        });

        try {
            Boolean response = responseFuture.get();
            if (response) {
                String msg =
                        "User choices data has been set. Current enrollment data is: \n"
                                + consentManager.getModuleEnrollmentState();
                Log.i(TAG, msg);
                out.print(msg);
            }
            return toShellCommandResult(
                    response ? RESULT_SUCCESS : ShellCommandStats.RESULT_GENERIC_ERROR,
                    COMMAND_SET_USER_CHOICES);
        } catch (Exception e) {
            err.printf("Failed to set user choices for adServices: %s\n", e.getMessage());
            Log.e(TAG, "Failed to set user choices for adServices: " + e.getMessage());
            return toShellCommandResult(
                    ShellCommandStats.RESULT_GENERIC_ERROR, COMMAND_SET_USER_CHOICES);
        }
    }

    @Override
    public String getCommandName() {
        return CMD_SET_USER_CHOICES;
    }

    @Override
    public int getMetricsLoggerCommand() {
        return COMMAND_SET_USER_CHOICES;
    }

    @Override
    public String getCommandHelp() {
        return HELP;
    }
}
