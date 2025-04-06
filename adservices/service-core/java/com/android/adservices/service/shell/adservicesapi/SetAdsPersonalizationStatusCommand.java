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

import static android.adservices.common.AdServicesCommonManager.ADS_PERSONALZATION_DISABLED;
import static android.adservices.common.AdServicesCommonManager.ADS_PERSONALZATION_ENABLED;

import static com.android.adservices.service.shell.AdServicesShellCommandHandler.TAG;
import static com.android.adservices.service.shell.adservicesapi.AdServicesApiShellCommandFactory.COMMAND_PREFIX;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_SET_ADS_PERSONALIZATION_STATUS;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_SUCCESS;

import android.adservices.common.AdServicesCommonManager;
import android.os.Build;
import android.os.OutcomeReceiver;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.shell.AbstractShellCommand;
import com.android.adservices.service.shell.ShellCommandResult;
import com.android.adservices.service.stats.ShellCommandStats;
import com.android.adservices.shared.common.ApplicationContextSingleton;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.PrintWriter;

@RequiresApi(Build.VERSION_CODES.S)
public final class SetAdsPersonalizationStatusCommand extends AbstractShellCommand {
    public static final String CMD_SET_ADS_PERSONALIZATION_STATUS =
            "set-ads-personalization-status";

    public static final String HELP =
            "usage:\n "
                    + COMMAND_PREFIX
                    + " "
                    + CMD_SET_ADS_PERSONALIZATION_STATUS
                    + " [enabled|disabled]";

    @Override
    public ShellCommandResult run(PrintWriter out, PrintWriter err, String[] args) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            err.printf("Command is not supported on Android version: " + Build.VERSION.SDK_INT);
            return toShellCommandResult(
                    ShellCommandStats.RESULT_GENERIC_ERROR, COMMAND_SET_ADS_PERSONALIZATION_STATUS);
        }
        if (args.length != 3 || (!args[2].equals("enabled") && !args[2].equals("disabled"))) {
            return invalidArgsError(HELP, err, COMMAND_SET_ADS_PERSONALIZATION_STATUS, args);
        }

        AdServicesCommonManager commonManager =
                AdServicesCommonManager.get(ApplicationContextSingleton.get());

        @AdServicesCommonManager.AdsPersonalizationStatus
        int status =
                args[2].equals("enabled")
                        ? ADS_PERSONALZATION_ENABLED
                        : ADS_PERSONALZATION_DISABLED;

        ListenableFuture<Boolean> responseFuture =
                CallbackToFutureAdapter.getFuture(
                        completer -> {
                            commonManager.setAdsPersonalizationStatus(
                                    status,
                                    AdServicesExecutors.getLightWeightExecutor(),
                                    new OutcomeReceiver<>() {
                                        @Override
                                        public void onResult(Boolean result) {
                                            completer.set(true);
                                        }

                                        @Override
                                        public void onError(Exception exception) {
                                            completer.setException(exception);
                                        }
                                    });
                            return "Ads Personalization Status has been set";
                        });

        try {
            Boolean response = responseFuture.get();
            if (response) {
                String msg = "Set Ads Personalization Status to " + args[2];
                Log.i(TAG, msg);
                out.print(msg);
            }
            return toShellCommandResult(
                    response ? RESULT_SUCCESS : ShellCommandStats.RESULT_GENERIC_ERROR,
                    COMMAND_SET_ADS_PERSONALIZATION_STATUS);
        } catch (Exception e) {
            // err.printf will print error on the shell terminal
            // Log.e will write in the local logcat.
            // The log is helpful when we are looking at the local logcat for debugging.
            err.printf("Failed to set ads personalization status: %s\n", e.getMessage());
            Log.e(TAG, "Failed to set ads personalization status: " + e.getMessage());
            return toShellCommandResult(
                    ShellCommandStats.RESULT_GENERIC_ERROR, COMMAND_SET_ADS_PERSONALIZATION_STATUS);
        }
    }

    @Override
    public String getCommandName() {
        return CMD_SET_ADS_PERSONALIZATION_STATUS;
    }

    @Override
    public int getMetricsLoggerCommand() {
        return COMMAND_SET_ADS_PERSONALIZATION_STATUS;
    }

    @Override
    public String getCommandHelp() {
        return HELP;
    }
}
