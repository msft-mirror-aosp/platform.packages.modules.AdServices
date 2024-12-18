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

package com.android.adservices.service.shell.customaudience;

import static com.android.adservices.service.shell.AdServicesShellCommandHandler.TAG;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_CUSTOM_AUDIENCE_LIST;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_SUCCESS;

import android.adservices.common.AdTechIdentifier;
import android.util.Log;

import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBCustomAudienceBackgroundFetchData;
import com.android.adservices.service.shell.AbstractShellCommand;
import com.android.adservices.service.shell.ShellCommandResult;
import com.android.adservices.service.stats.ShellCommandStats;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Command to list custom audiences created in Protected Audience. */
public final class CustomAudienceListCommand extends AbstractShellCommand {

    @VisibleForTesting public static final String CMD = "list";

    @VisibleForTesting
    public static final String HELP =
            CustomAudienceShellCommandFactory.COMMAND_PREFIX
                    + " "
                    + CMD
                    + " "
                    + CustomAudienceArgs.OWNER
                    + " <owner>"
                    + " "
                    + CustomAudienceArgs.BUYER
                    + " <buyer>"
                    + "\n    List custom audiences. See documentation for `view` for more info.";

    private final CustomAudienceDao mCustomAudienceDao;
    private final CustomAudienceArgParser mCustomAudienceArgParser;
    private final Clock mClock;
    private final long mFledgeCustomAudienceActiveTimeWindowInMs;

    CustomAudienceListCommand(
            CustomAudienceDao customAudienceDao,
            Clock clock,
            long fledgeCustomAudienceActiveTimeWindowInMs) {
        mCustomAudienceDao = customAudienceDao;
        mFledgeCustomAudienceActiveTimeWindowInMs = fledgeCustomAudienceActiveTimeWindowInMs;
        mCustomAudienceArgParser =
                new CustomAudienceArgParser(CustomAudienceArgs.OWNER, CustomAudienceArgs.BUYER);
        mClock = clock;
    }

    @Override
    public String getCommandName() {
        return CMD;
    }

    @Override
    public int getMetricsLoggerCommand() {
        return COMMAND_CUSTOM_AUDIENCE_LIST;
    }

    @Override
    public String getCommandHelp() {
        return HELP;
    }

    @Override
    public ShellCommandResult run(PrintWriter out, PrintWriter err, String[] args) {
        try {
            mCustomAudienceArgParser.parse(args);
        } catch (IllegalArgumentException e) {
            err.printf("Failed to parse arguments: %s\n", e.getMessage());
            Log.e(TAG, "Failed to parse arguments: " + e.getMessage());
            return invalidArgsError(HELP, err, COMMAND_CUSTOM_AUDIENCE_LIST, args);
        }

        String owner = mCustomAudienceArgParser.getValue(CustomAudienceArgs.OWNER);
        AdTechIdentifier buyer =
                AdTechIdentifier.fromString(
                        mCustomAudienceArgParser.getValue(CustomAudienceArgs.BUYER));
        try {
            out.print(
                    createOutputJson(
                            queryForDebuggableCustomAudiences(owner, buyer),
                            queryForDebuggableBackgroundFetchData(owner, buyer),
                            queryForActiveCustomAudiences(buyer)));
        } catch (JSONException e) {
            err.printf("Failed to generate output: %s\n", e.getMessage());
            Log.e(TAG, "Failed to generate JSON: " + e.getMessage());
            return toShellCommandResult(
                    ShellCommandStats.RESULT_GENERIC_ERROR, COMMAND_CUSTOM_AUDIENCE_LIST);
        }
        return toShellCommandResult(RESULT_SUCCESS, COMMAND_CUSTOM_AUDIENCE_LIST);
    }

    private List<DBCustomAudience> queryForActiveCustomAudiences(AdTechIdentifier buyer) {
        Log.d(TAG, String.format("Querying for active CAs from buyer %s", buyer));
        List<DBCustomAudience> customAudienceList =
                mCustomAudienceDao.getActiveCustomAudienceByBuyers(
                        List.of(buyer),
                        mClock.instant(),
                        mFledgeCustomAudienceActiveTimeWindowInMs);
        if (customAudienceList == null) {
            customAudienceList = List.of();
        }
        Log.d(TAG, String.format("%d active custom audiences found.", customAudienceList.size()));
        return customAudienceList;
    }

    private List<DBCustomAudience> queryForDebuggableCustomAudiences(
            String owner, AdTechIdentifier buyer) {
        Log.d(TAG, String.format("Querying for CA with owner %s and buyer %s", owner, buyer));
        List<DBCustomAudience> customAudienceList =
                mCustomAudienceDao.listDebuggableCustomAudiencesByOwnerAndBuyer(owner, buyer);
        if (customAudienceList == null || customAudienceList.isEmpty()) {
            customAudienceList = List.of(); // Desired output is to print an empty JSON.
        }
        Log.d(TAG, String.format("%d custom audiences found.", customAudienceList.size()));
        return customAudienceList;
    }

    private Map<String, DBCustomAudienceBackgroundFetchData> queryForDebuggableBackgroundFetchData(
            String owner, AdTechIdentifier buyer) {
        Log.d(
                TAG,
                String.format(
                        "Querying for CA background fetch data with owner %s and buyer %s",
                        owner, buyer));
        List<DBCustomAudienceBackgroundFetchData> backgroundFetchDataList =
                mCustomAudienceDao.listDebuggableCustomAudienceBackgroundFetchData(owner, buyer);
        Map<String, DBCustomAudienceBackgroundFetchData> backgroundFetchDataHashMap =
                new HashMap<>();
        if (backgroundFetchDataList == null || backgroundFetchDataList.isEmpty()) {
            backgroundFetchDataList = List.of();
        }
        for (DBCustomAudienceBackgroundFetchData backgroundFetchData : backgroundFetchDataList) {
            backgroundFetchDataHashMap.put(backgroundFetchData.getName(), backgroundFetchData);
        }
        Log.d(
                TAG,
                String.format(
                        "%d custom audience background fetch data found.",
                        backgroundFetchDataList.size()));
        return backgroundFetchDataHashMap;
    }

    private static JSONObject createOutputJson(
            List<DBCustomAudience> customAudienceList,
            Map<String, DBCustomAudienceBackgroundFetchData> backgroundFetchDataMap,
            List<DBCustomAudience> activeCustomAudiences)
            throws JSONException {
        JSONObject jsonObject = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        for (DBCustomAudience customAudience : customAudienceList) {
            if (!backgroundFetchDataMap.containsKey(customAudience.getName())) {
                Log.d(
                        TAG,
                        String.format(
                                "Background fetch data missing for CA with name %s owner %s and"
                                        + " buyer %s",
                                customAudience.getName(),
                                customAudience.getOwner(),
                                customAudience.getBuyer()));
                continue;
            }
            jsonArray.put(
                    CustomAudienceHelper.toJson(
                            customAudience,
                            backgroundFetchDataMap.get(customAudience.getName()),
                            CustomAudienceEligibilityInfo.create(
                                    customAudience, activeCustomAudiences)));
        }
        jsonObject.put("custom_audiences", jsonArray);
        return jsonObject;
    }
}
