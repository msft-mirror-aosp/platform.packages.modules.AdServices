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

package com.android.adservices.service.shell.attributionreporting;

import static com.android.adservices.service.shell.AdServicesShellCommandHandler.TAG;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_ATTRIBUTION_REPORTING_LIST_SOURCE_REGISTRATIONS;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_DEV_MODE_UNCONFIRMED;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_SUCCESS;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.service.devapi.DevSessionDataStore;
import com.android.adservices.service.devapi.DevSessionState;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.shell.AbstractShellCommand;
import com.android.adservices.service.shell.ShellCommandResult;
import com.android.adservices.service.stats.ShellCommandStats;

import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public final class AttributionReportingListSourceRegistrationsCommand extends AbstractShellCommand {
    public static final int TIMEOUT_SEC = 5;
    public static final String CMD = "list-source-registrations";
    public static final String HELP =
            AttributionReportingShellCommandFactory.COMMAND_PREFIX
                    + " "
                    + CMD
                    + " "
                    + "\n List Source Registration";

    private final DatastoreManager mDatastoreManager;
    private final DevSessionDataStore mDevSessionDataStore;
    private static final int DB_TIMEOUT_SEC = 3;

    @VisibleForTesting
    public static final String ERROR_DEVELOPER_MODE =
            "developer mode is required to get measurement data";

    AttributionReportingListSourceRegistrationsCommand(
            DatastoreManager datastoreManager, DevSessionDataStore devSessionDataStore) {
        mDatastoreManager = datastoreManager;
        mDevSessionDataStore = devSessionDataStore;
    }

    @Override
    public ShellCommandResult run(PrintWriter out, PrintWriter err, String[] args) {
        if (!isRunningInDevSession()) {
            Log.e(TAG, ERROR_DEVELOPER_MODE);
            return toShellCommandResult(
                    RESULT_DEV_MODE_UNCONFIRMED,
                    COMMAND_ATTRIBUTION_REPORTING_LIST_SOURCE_REGISTRATIONS);
        }

        try {
            ListenableFuture<Optional<List<Source>>> futureResult =
                    queryForListSourceRegistrationsCommand();
            Optional<List<Source>> result = futureResult.get(TIMEOUT_SEC, SECONDS);
            String output;
            if (result.isPresent()) {
                output = createOutputJson(result).toString();
            } else {
                output = "Error in retrieving sources from database.";
            }
            out.print(output);
            out.flush();
            return toShellCommandResult(
                    RESULT_SUCCESS, COMMAND_ATTRIBUTION_REPORTING_LIST_SOURCE_REGISTRATIONS);
        } catch (Exception e) {
            Log.e(TAG, String.format("Failed to generate JSON: %s", e.getMessage()));

            return toShellCommandResult(
                    ShellCommandStats.RESULT_GENERIC_ERROR,
                    COMMAND_ATTRIBUTION_REPORTING_LIST_SOURCE_REGISTRATIONS);
        }
    }

    @Override
    public String getCommandName() {
        return CMD;
    }

    @Override
    public int getMetricsLoggerCommand() {
        return COMMAND_ATTRIBUTION_REPORTING_LIST_SOURCE_REGISTRATIONS;
    }

    @Override
    public String getCommandHelp() {
        return HELP;
    }

    private ListenableFuture<Optional<List<Source>>> queryForListSourceRegistrationsCommand() {
        Log.d(TAG, String.format("Querying for list source registrations"));

        return AdServicesExecutors.getBackgroundExecutor()
                .submit(
                        () ->
                                mDatastoreManager.runInTransactionWithResult(
                                        (dao) -> dao.fetchAllSourceRegistrations()));
    }

    private static JSONObject createOutputJson(Optional<List<Source>> sources)
            throws JSONException {
        JSONObject jsonObject = new JSONObject();
        JSONArray jsonArray = new JSONArray();

        for (Source source : sources.get()) {
            jsonArray.put(AttributionReportingHelper.sourceToJson(source));
        }

        jsonObject.put("attribution_reporting", jsonArray);
        return jsonObject;
    }

    private boolean isRunningInDevSession() {
        try {
            return mDevSessionDataStore
                    .get()
                    .get(DB_TIMEOUT_SEC, SECONDS)
                    .getState()
                    .equals(DevSessionState.IN_DEV);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Log.e(TAG, String.format("Could not correctly retrieve dev session status: %s", e));
            return false;
        }
    }
}
