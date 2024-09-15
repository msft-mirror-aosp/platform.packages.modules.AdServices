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
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_CUSTOM_AUDIENCE_REFRESH;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_SUCCESS;

import android.adservices.common.AdTechIdentifier;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudienceBackgroundFetchData;
import com.android.adservices.service.customaudience.BackgroundFetchRunner;
import com.android.adservices.service.customaudience.BackgroundFetchRunner.UpdateResultType;
import com.android.adservices.service.shell.AbstractShellCommand;
import com.android.adservices.service.shell.ShellCommandResult;
import com.android.adservices.service.stats.ShellCommandStats;

import java.io.PrintWriter;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Command to refresh a single custom audience. */
public final class CustomAudienceRefreshCommand extends AbstractShellCommand {

    public static final String CMD = "refresh";
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
                    + " <name>";
    @VisibleForTesting public static final int BACKGROUND_FETCH_TIMEOUT_FINAL_SECONDS = 3;

    @VisibleForTesting
    public static final String OUTPUT_ERROR_NO_CUSTOM_AUDIENCE = "Error: No custom audience found.";

    @VisibleForTesting
    public static final String OUTPUT_ERROR_WITH_MESSAGE =
            "Failed to update custom audience. Error message: %s";

    @VisibleForTesting
    public static final String OUTPUT_SUCCESS = "Successfully updated custom audience.";

    @VisibleForTesting public static final String OUTPUT_ERROR_NETWORK = "NETWORK_FAILURE";
    @VisibleForTesting public static final String OUTPUT_ERROR_UNKNOWN = "UNKNOWN_FAILURE";

    private final BackgroundFetchRunner mBackgroundFetchRunner;
    private final CustomAudienceArgParser mArgParser;
    private final CustomAudienceDao mCustomAudienceDao;
    private final int mFetchTimeoutInSeconds;
    private final Clock mClock;
    private final ScheduledThreadPoolExecutor mScheduledExecutor;

    CustomAudienceRefreshCommand(
            BackgroundFetchRunner backgroundFetchRunner,
            CustomAudienceDao customAudienceDao,
            ScheduledThreadPoolExecutor executor) {
        this(
                backgroundFetchRunner,
                customAudienceDao,
                BACKGROUND_FETCH_TIMEOUT_FINAL_SECONDS,
                Clock.systemUTC(),
                executor);
    }

    @VisibleForTesting
    CustomAudienceRefreshCommand(
            BackgroundFetchRunner backgroundFetchRunner,
            CustomAudienceDao customAudienceDao,
            int backgroundFetchTimeoutInSeconds,
            Clock clock,
            ScheduledThreadPoolExecutor executor) {
        mBackgroundFetchRunner = backgroundFetchRunner;
        mCustomAudienceDao = customAudienceDao;
        mFetchTimeoutInSeconds = backgroundFetchTimeoutInSeconds;
        mClock = clock;
        mScheduledExecutor = executor;
        mArgParser =
                new CustomAudienceArgParser(
                        CustomAudienceArgs.OWNER,
                        CustomAudienceArgs.BUYER,
                        CustomAudienceArgs.NAME);
    }

    @Override
    public ShellCommandResult run(PrintWriter out, PrintWriter err, String[] args) {
        try {
            mArgParser.parse(args);
        } catch (IllegalArgumentException e) {
            err.printf("Failed to parse arguments: %s\n", e.getMessage());
            Log.e(TAG, "Failed to parse arguments", e);
            return invalidArgsError(HELP, err, COMMAND_CUSTOM_AUDIENCE_REFRESH, args);
        }

        Optional<DBCustomAudienceBackgroundFetchData> backgroundFetchData =
                getDbCustomAudienceBackgroundFetchData(
                        mArgParser.getValue(CustomAudienceArgs.NAME),
                        mArgParser.getValue(CustomAudienceArgs.OWNER),
                        AdTechIdentifier.fromString(mArgParser.getValue(CustomAudienceArgs.BUYER)));
        if (backgroundFetchData.isPresent()) {
            UpdateResultType updateResult = refreshCustomAudience(backgroundFetchData.get());
            if (updateResult == UpdateResultType.SUCCESS) {
                out.printf(OUTPUT_SUCCESS);
                return toShellCommandResult(RESULT_SUCCESS, COMMAND_CUSTOM_AUDIENCE_REFRESH);
            } else {
                err.printf(
                        OUTPUT_ERROR_WITH_MESSAGE,
                        errorMessageFromCustomAudienceUpdateResult(updateResult));
                return toShellCommandResult(
                        ShellCommandStats.RESULT_GENERIC_ERROR, COMMAND_CUSTOM_AUDIENCE_REFRESH);
            }
        } else {
            err.printf(OUTPUT_ERROR_NO_CUSTOM_AUDIENCE);
            return toShellCommandResult(
                    ShellCommandStats.RESULT_GENERIC_ERROR, COMMAND_CUSTOM_AUDIENCE_REFRESH);
        }
    }

    @Override
    public String getCommandName() {
        return CMD;
    }

    @Override
    public int getMetricsLoggerCommand() {
        return COMMAND_CUSTOM_AUDIENCE_REFRESH;
    }

    @Override
    public String getCommandHelp() {
        return HELP;
    }

    private String errorMessageFromCustomAudienceUpdateResult(UpdateResultType updateResultType) {
        switch (updateResultType) {
            case NETWORK_FAILURE:
            case NETWORK_READ_TIMEOUT_FAILURE:
                return OUTPUT_ERROR_NETWORK;
            case SUCCESS:
                // Success has no appropriate message.
            case UNKNOWN:
            case RESPONSE_VALIDATION_FAILURE:
            case K_ANON_FAILURE:
            default:
                return OUTPUT_ERROR_UNKNOWN;
        }
    }

    private UpdateResultType refreshCustomAudience(
            DBCustomAudienceBackgroundFetchData backgroundFetchData) {
        try {
            return mBackgroundFetchRunner
                    .updateCustomAudience(mClock.instant(), backgroundFetchData)
                    .withTimeout(mFetchTimeoutInSeconds, TimeUnit.SECONDS, mScheduledExecutor)
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            Log.e(TAG, "Failed to update custom audience", e);
            return UpdateResultType.UNKNOWN;
        }
    }

    private Optional<DBCustomAudienceBackgroundFetchData> getDbCustomAudienceBackgroundFetchData(
            String name, String ownerAppPackage, AdTechIdentifier buyer) {
        DBCustomAudienceBackgroundFetchData data =
                mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        ownerAppPackage, buyer, name);
        if (Objects.isNull(data)) {
            Log.d(
                    TAG,
                    String.format(
                            "No debuggable custom audience background fetch data found for key:"
                                    + " (name: %s, owner: %s, buyer: %s)",
                            name, ownerAppPackage, buyer));
            return Optional.empty();
        }
        Log.d(
                TAG,
                String.format(
                        "Debuggable custom audience background fetch was data found for key:"
                                + " (name: %s, owner: %s, buyer: %s)",
                        name, ownerAppPackage, buyer));
        return Optional.of(data);
    }
}
