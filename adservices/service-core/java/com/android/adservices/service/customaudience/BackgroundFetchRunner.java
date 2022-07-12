/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.customaudience;

import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudienceBackgroundFetchData;
import com.android.adservices.service.Flags;

import java.time.Instant;
import java.util.Objects;

/** Runner executing actual background fetch tasks. */
public class BackgroundFetchRunner {
    private final CustomAudienceDao mCustomAudienceDao;
    private final Flags mFlags;

    /** Represents the result of an update attempt prior to parsing the update response. */
    public enum UpdateResultType {
        SUCCESS,
        UNKNOWN,
        K_ANON_FAILURE,
        INITIAL_CONNECTION_TIMEOUT_FAILURE,
        NETWORK_CONNECTION_TIMEOUT_FAILURE,
        RESPONSE_VALIDATION_FAILURE
    }

    public BackgroundFetchRunner(
            @NonNull CustomAudienceDao customAudienceDao, @NonNull Flags flags) {
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(flags);
        mCustomAudienceDao = customAudienceDao;
        mFlags = flags;
    }

    /**
     * Deletes custom audiences whose expiration timestamps have passed.
     *
     * <p>Also clears corresponding update information from the background fetch DB.
     */
    public void deleteExpiredCustomAudiences(@NonNull Instant jobStartTime) {
        Objects.requireNonNull(jobStartTime);
        LogUtil.d("Starting custom audience garbage collection");
        // TODO(b/221862020): Garbage collection of expired custom audiences
        //  Two options for synchronized deletion across two tables:
        //  A) Transactions (with @Transaction) in DAO to delete entries in both the main table and
        //     the BgF table based on the expiration time in the main CA table.
        //  B) Create a unique foreign key to use @ForeignKey relationship to automatically cascade
        //     deletion in the main table to the BgF table.
    }

    /** Updates a single given custom audience and persists the results. */
    public void updateCustomAudience(
            @NonNull Instant jobStartTime, @NonNull DBCustomAudienceBackgroundFetchData fetchData) {
        Objects.requireNonNull(jobStartTime);
        Objects.requireNonNull(fetchData);

        CustomAudienceUpdatableData updatableData =
                fetchAndValidateCustomAudienceUpdatableData(
                        jobStartTime, fetchData.getDailyUpdateUrl());
        fetchData = fetchData.copyWithUpdatableData(updatableData);

        if (updatableData.getContainsSuccessfulUpdate()) {
            mCustomAudienceDao.updateCustomAudienceAndBackgroundFetchData(fetchData, updatableData);
        } else {
            // In a failed update, we don't need to update the main CA table, so only update the
            // background fetch table
            mCustomAudienceDao.persistCustomAudienceBackgroundFetchData(fetchData);
        }
    }

    /**
     * Fetches the custom audience update from the given daily update URL and validates the response
     * in a {@link CustomAudienceUpdatableData} object.
     */
    @NonNull
    public CustomAudienceUpdatableData fetchAndValidateCustomAudienceUpdatableData(
            @NonNull Instant jobStartTime, @NonNull Uri dailyFetchUrl) {
        Objects.requireNonNull(jobStartTime);
        Objects.requireNonNull(dailyFetchUrl);

        UpdateResultType fetchResult = UpdateResultType.SUCCESS;
        // TODO(b/235858839): Implement dev override for background fetch response
        // TODO(b/234884352): Perform k-anon check on daily fetch URL
        // TODO(b/235842292): Implement network fetch from server; for now, return empty response
        // TODO(b/233739309): Response data validation (less than 10 KB)
        String updateResponse = "{}";
        return CustomAudienceUpdatableData.createFromResponseString(
                jobStartTime, fetchResult, updateResponse);
    }
}
