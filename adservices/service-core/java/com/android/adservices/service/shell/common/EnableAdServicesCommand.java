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
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_ENABLE_ADSERVICES;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_SUCCESS;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesOutcomeReceiver;
import android.adservices.common.AdServicesStates;
import android.annotation.SuppressLint;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.shell.AbstractShellCommand;
import com.android.adservices.service.shell.ShellCommandArgParserHelper;
import com.android.adservices.service.shell.ShellCommandResult;
import com.android.adservices.service.stats.ShellCommandStats;
import com.android.adservices.shared.common.ApplicationContextSingleton;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.PrintWriter;

/**
 * This class implements enable_adservices shell command.
 *
 * <p>It triggers the adservices related apis.
 */
@RequiresApi(Build.VERSION_CODES.S)
public final class EnableAdServicesCommand extends AbstractShellCommand {
    /** Runs the shell command and returns the result. */
    public static final String CMD_ENABLE_ADSERVICES = "enable-adservices";

    public static final String HELP_ENABLE_ADSERVICES =
            CMD_ENABLE_ADSERVICES
                    + "usage:\n enable-adservices [--adid true|false][--adult true|false][--u18 "
                    + "true|false]";

    private static final String ADID_KEY = "adid";

    private static final String ADULT_ACCOUNT_KEY = "adult";

    private static final String U18_ACCOUNT_KEY = "u18";

    @SuppressLint("MissingPermission")
    @Override
    public ShellCommandResult run(PrintWriter out, PrintWriter err, String[] args) {
        // args length should be odd number, first arg is the command itself
        if (args.length % 2 == 0) {
            return invalidArgsError(
                    HELP_ENABLE_ADSERVICES, err, ShellCommandStats.COMMAND_ENABLE_ADSERVICES, args);
        }
        ImmutableMap<String, String> paramMap =
                ShellCommandArgParserHelper.parseCliArguments(args, 1);

        AdServicesCommonManager commonManager =
                AdServicesCommonManager.get(ApplicationContextSingleton.get());

        ListenableFuture<Boolean> responseFuture =
                CallbackToFutureAdapter.getFuture(
                        completer -> {
                            commonManager.enableAdServices(
                                    new AdServicesStates.Builder()
                                            .setAdIdEnabled(
                                                    Boolean.parseBoolean(
                                                            paramMap.getOrDefault(
                                                                    ADID_KEY, "true")))
                                            .setAdultAccount(
                                                    Boolean.parseBoolean(
                                                            paramMap.getOrDefault(
                                                                    ADULT_ACCOUNT_KEY, "true")))
                                            .setU18Account(
                                                    Boolean.parseBoolean(
                                                            paramMap.getOrDefault(
                                                                    U18_ACCOUNT_KEY, "false")))
                                            .setPrivacySandboxUiEnabled(true)
                                            .build(),
                                    AdServicesExecutors.getLightWeightExecutor(),
                                    new AdServicesOutcomeReceiver<>() {
                                        @Override
                                        public void onResult(Boolean result) {
                                            completer.set(result);
                                        }

                                        @Override
                                        public void onError(Exception exception) {
                                            completer.setException(exception);
                                        }
                                    });
                            return "enableAdservices";
                        });

        try {
            Boolean response = responseFuture.get();
            return toShellCommandResult(
                    response ? RESULT_SUCCESS : ShellCommandStats.RESULT_GENERIC_ERROR,
                    ShellCommandStats.COMMAND_ENABLE_ADSERVICES);
        } catch (Exception e) {
            err.printf("Failed to enable adServices: %s\n", e.getMessage());
            Log.e(TAG, "Failed to enable adServices: " + e.getMessage());
            return toShellCommandResult(
                    ShellCommandStats.RESULT_GENERIC_ERROR, COMMAND_ENABLE_ADSERVICES);
        }
    }

    @Override
    public String getCommandName() {
        return CMD_ENABLE_ADSERVICES;
    }

    @Override
    public int getMetricsLoggerCommand() {
        return COMMAND_ENABLE_ADSERVICES;
    }

    @Override
    public String getCommandHelp() {
        return HELP_ENABLE_ADSERVICES;
    }
}
