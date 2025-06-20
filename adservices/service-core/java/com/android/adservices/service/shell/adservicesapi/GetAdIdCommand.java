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

import static com.android.adservices.service.shell.AdServicesShellCommandHandler.TAG;
import static com.android.adservices.service.shell.adservicesapi.AdServicesApiShellCommandFactory.COMMAND_PREFIX;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_GET_AD_ID;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_SUCCESS;

import android.adservices.adid.AdId;
import android.adservices.adid.AdIdManager;
import android.annotation.SuppressLint;
import android.os.Binder;
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

/** This class implements a shell command to retrieve the device Ad ID. */
@RequiresApi(Build.VERSION_CODES.S)
public final class GetAdIdCommand extends AbstractShellCommand {

    public static final String CMD_GET_ADID = "get-adid";
    public static final String HELP_GET_ADID = "usage:\n " + COMMAND_PREFIX + " " + CMD_GET_ADID;

    @SuppressLint("MissingPermission")
    @Override
    public ShellCommandResult run(PrintWriter out, PrintWriter err, String[] args) {
        if (args.length != 2) {
            return invalidArgsError(HELP_GET_ADID, err, COMMAND_GET_AD_ID, args);
        }

        long identity = Binder.clearCallingIdentity();
        try {
            AdIdManager manager = AdIdManager.get(ApplicationContextSingleton.get());
            ListenableFuture<AdId> future =
                    CallbackToFutureAdapter.getFuture(
                            completer -> {
                                manager.getAdId(
                                        AdServicesExecutors.getLightWeightExecutor(),
                                        new OutcomeReceiver<AdId, Exception>() {
                                            @Override
                                            public void onResult(AdId adId) {
                                                completer.set(adId);
                                            }

                                            @Override
                                            public void onError(Exception e) {
                                                completer.setException(e);
                                            }
                                        });
                                return "getAdId";
                            });

            AdId adId = future.get();
            out.printf(adId.getAdId());
            return toShellCommandResult(RESULT_SUCCESS, COMMAND_GET_AD_ID);
        } catch (Exception e) {
            err.printf("Failed to get Ad ID: %s\n", e.getMessage());
            Log.e(TAG, "Failed to get Ad ID", e);
            return toShellCommandResult(ShellCommandStats.RESULT_GENERIC_ERROR, COMMAND_GET_AD_ID);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public String getCommandName() {
        return CMD_GET_ADID;
    }

    @Override
    public int getMetricsLoggerCommand() {
        return COMMAND_GET_AD_ID;
    }

    @Override
    public String getCommandHelp() {
        return HELP_GET_ADID;
    }
}
