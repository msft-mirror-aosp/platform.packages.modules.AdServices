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

package com.android.adservices.service.shell;

import static com.android.adservices.service.shell.AdServicesShellCommandHandler.TAG;

import android.adservices.common.AdTechIdentifier;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.shared.common.ApplicationContextSingleton;

import org.json.JSONException;

import java.io.PrintWriter;
import java.util.Optional;

/** Command to view custom audiences created in Protected Audience. */
final class CustomAudienceViewCommand extends AbstractShellCommand {

    public static final String CMD = "view-custom-audience";
    public static final String HELP =
            CMD
                    + " --"
                    + CustomAudienceArgs.OWNER
                    + " <owner> --"
                    + CustomAudienceArgs.BUYER
                    + " <buyer> --"
                    + CustomAudienceArgs.NAME
                    + " <name>";

    private final CustomAudienceDao mCustomAudienceDao;
    private final ArgParser mArgParser;

    CustomAudienceViewCommand() {
        this(
                CustomAudienceDatabase.getInstance(ApplicationContextSingleton.get())
                        .customAudienceDao());
    }

    @VisibleForTesting
    CustomAudienceViewCommand(CustomAudienceDao customAudienceDao) {
        mCustomAudienceDao = customAudienceDao;
        mArgParser =
                new ArgParser(
                        CustomAudienceArgs.OWNER,
                        CustomAudienceArgs.BUYER,
                        CustomAudienceArgs.NAME);
    }

    @Override
    public int run(PrintWriter out, PrintWriter err, String[] args) {
        try {
            mArgParser.parse(args);
        } catch (IllegalArgumentException e) {
            err.printf("Failed to parse arguments: %s\n", e.getMessage());
            Log.e(TAG, "Failed to parse arguments: " + e.getMessage());
            return invalidArgsError(HELP, err, args);
        }

        String owner = mArgParser.getValue(CustomAudienceArgs.OWNER);
        AdTechIdentifier buyer =
                AdTechIdentifier.fromString(mArgParser.getValue(CustomAudienceArgs.BUYER));
        String name = mArgParser.getValue(CustomAudienceArgs.NAME);
        Optional<DBCustomAudience> customAudience = queryForCustomAudience(owner, buyer, name);
        try {
            if (customAudience.isPresent()) {
                out.print(CustomAudienceHelper.toJson(customAudience.get()));
            } else {
                out.print("{}");
            }
            return RESULT_OK;
        } catch (JSONException e) {
            err.printf("Failed to generate output: %s\n", e.getMessage());
            Log.e(TAG, "Failed to generate JSON: " + e.getMessage());
            return RESULT_GENERIC_ERROR;
        }
    }

    private Optional<DBCustomAudience> queryForCustomAudience(
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
}
