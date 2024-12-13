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
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_CUSTOM_AUDIENCE_VIEW;
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

import org.json.JSONException;

import java.io.PrintWriter;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/** Command to view custom audiences created in Protected Audience. */
public final class CustomAudienceViewCommand extends AbstractShellCommand {

    @VisibleForTesting public static final String CMD = "view";
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
                    + " "
                    + CustomAudienceArgs.NAME
                    + " <name>"
                    + "\n    View a custom audience. For a CA to appear here, it must be 1) "
                    + "created in a a context where android:debuggable=\"true\" is in the owning "
                    + "app's manifest and 2) system-wide developer options are enabled";

    private final CustomAudienceDao mCustomAudienceDao;
    private final CustomAudienceArgParser mCustomAudienceArgParser;
    private final Clock mClock;
    private final long mFledgeCustomAudienceActiveTimeWindowInMs;

    CustomAudienceViewCommand(
            CustomAudienceDao customAudienceDao,
            Clock clock,
            long fledgeCustomAudienceActiveTimeWindowInMs) {
        mCustomAudienceDao = customAudienceDao;
        mFledgeCustomAudienceActiveTimeWindowInMs = fledgeCustomAudienceActiveTimeWindowInMs;
        mCustomAudienceArgParser =
                new CustomAudienceArgParser(
                        CustomAudienceArgs.OWNER,
                        CustomAudienceArgs.BUYER,
                        CustomAudienceArgs.NAME);
        mClock = clock;
    }

    @Override
    public String getCommandName() {
        return CMD;
    }

    @Override
    public int getMetricsLoggerCommand() {
        return COMMAND_CUSTOM_AUDIENCE_VIEW;
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
            return invalidArgsError(HELP, err, COMMAND_CUSTOM_AUDIENCE_VIEW, args);
        }

        String owner = mCustomAudienceArgParser.getValue(CustomAudienceArgs.OWNER);
        AdTechIdentifier buyer =
                AdTechIdentifier.fromString(
                        mCustomAudienceArgParser.getValue(CustomAudienceArgs.BUYER));
        String name = mCustomAudienceArgParser.getValue(CustomAudienceArgs.NAME);
        Optional<DBCustomAudience> customAudience =
                queryForDebuggableCustomAudience(owner, buyer, name);
        Optional<DBCustomAudienceBackgroundFetchData> customAudienceBackgroundFetchData =
                queryForDebuggableCustomAudienceBackgroundFetchData(owner, buyer, name);
        try {
            if (customAudience.isPresent() && customAudienceBackgroundFetchData.isPresent()) {
                out.print(
                        CustomAudienceHelper.toJson(
                                customAudience.get(),
                                customAudienceBackgroundFetchData.get(),
                                CustomAudienceEligibilityInfo.create(
                                        customAudience.get(),
                                        queryForActiveCustomAudiences(
                                                customAudience.get().getBuyer()))));
            } else {
                out.print("{}");
            }
            return toShellCommandResult(RESULT_SUCCESS, COMMAND_CUSTOM_AUDIENCE_VIEW);
        } catch (JSONException e) {
            err.printf("Failed to generate output: %s\n", e.getMessage());
            Log.e(TAG, "Failed to generate JSON: " + e.getMessage());
            return toShellCommandResult(
                    ShellCommandStats.RESULT_GENERIC_ERROR, COMMAND_CUSTOM_AUDIENCE_VIEW);
        }
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

    private Optional<DBCustomAudience> queryForDebuggableCustomAudience(
            String owner, AdTechIdentifier buyer, String name) {
        Log.d(
                TAG,
                String.format(
                        "Querying for CA with owner %s, buyer %s and name %s", owner, buyer, name));
        DBCustomAudience customAudience =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(owner, buyer, name);
        if (customAudience == null || !customAudience.isDebuggable()) {
            Log.d(
                    TAG,
                    String.format(
                            "No debuggable custom audience found with owner %s, buyer %s and name"
                                    + " %s.",
                            owner, buyer, name));
            return Optional.empty();
        } else {
            Log.d(
                    TAG,
                    String.format(
                            "Debuggable custom audience found with owner %s, buyer %s and name"
                                    + " %s.",
                            owner, buyer, name));
            return Optional.of(customAudience);
        }
    }

    private Optional<DBCustomAudienceBackgroundFetchData>
            queryForDebuggableCustomAudienceBackgroundFetchData(
                    String owner, AdTechIdentifier buyer, String name) {
        Log.d(
                TAG,
                String.format(
                        "Querying for CA background fetch data with owner %s, buyer %s and name %s",
                        owner, buyer, name));
        DBCustomAudienceBackgroundFetchData customAudienceBackgroundFetchData =
                mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        owner, buyer, name);
        if (customAudienceBackgroundFetchData == null) {
            Log.d(
                    TAG,
                    String.format(
                            "No debuggable custom audience background fetch data found with owner"
                                    + " %s, buyer %s and name %s.",
                            owner, buyer, name));
            return Optional.empty();
        } else {
            Log.d(
                    TAG,
                    String.format(
                            "Debuggable custom audience background fetch data found with owner "
                                    + "%s, buyer %s and name %s",
                            owner, buyer, name));
            return Optional.of(customAudienceBackgroundFetchData);
        }
    }
}
