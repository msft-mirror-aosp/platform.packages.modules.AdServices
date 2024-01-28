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

import android.adservices.common.AdTechIdentifier;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.shared.common.ApplicationContextSingleton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.util.List;

/** Command to list custom audiences created in Protected Audience. */
// TODO(b/318496217): Merge with background fetch data in follow-up CL.
final class CustomAudienceListCommand extends AbstractShellCommand {

    public static final String CMD = "list-custom-audiences";
    public static final String HELP =
            CMD
                    + " --"
                    + CustomAudienceArgs.OWNER
                    + "=<owner> --"
                    + CustomAudienceArgs.BUYER
                    + "=<buyer>";

    private final CustomAudienceDao mCustomAudienceDao;
    private final ArgParser mArgParser;

    CustomAudienceListCommand() {
        this(
                CustomAudienceDatabase.getInstance(ApplicationContextSingleton.get())
                        .customAudienceDao());
    }

    @VisibleForTesting
    CustomAudienceListCommand(CustomAudienceDao customAudienceDao) {
        mCustomAudienceDao = customAudienceDao;
        mArgParser = new ArgParser(CustomAudienceArgs.OWNER, CustomAudienceArgs.BUYER);
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
        try {
            out.print(createOutputJson(queryForCustomAudiences(owner, buyer)));
        } catch (JSONException e) {
            err.printf("Failed to generate output: %s\n", e.getMessage());
            Log.e(TAG, "Failed to generate JSON: " + e.getMessage());
            return RESULT_GENERIC_ERROR;
        }
        return RESULT_OK;
    }

    private List<DBCustomAudience> queryForCustomAudiences(String owner, AdTechIdentifier buyer) {
        Log.d(TAG, String.format("Querying for CA with owner %s and buyer %s", owner, buyer));
        List<DBCustomAudience> customAudienceList =
                mCustomAudienceDao.listDebuggableCustomAudiencesByOwnerAndBuyer(owner, buyer);
        if (customAudienceList == null || customAudienceList.isEmpty()) {
            customAudienceList = List.of(); // Desired output is to print an empty JSON.
        }
        Log.d(TAG, String.format("%d custom audiences found.", customAudienceList.size()));
        return customAudienceList;
    }

    private static JSONObject createOutputJson(List<DBCustomAudience> customAudienceList)
            throws JSONException {
        JSONObject jsonObject = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        for (DBCustomAudience customAudience : customAudienceList) {
            jsonArray.put(CustomAudienceHelper.toJson(customAudience));
        }
        jsonObject.put("custom_audiences", jsonArray);
        return jsonObject;
    }
}