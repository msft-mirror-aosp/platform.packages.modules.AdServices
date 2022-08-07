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

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudienceBackgroundFetchData;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdServicesHttpsClient;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/** Runner executing actual background fetch tasks. */
public class BackgroundFetchRunner {
    private final CustomAudienceDao mCustomAudienceDao;
    private final Flags mFlags;
    private final AdServicesHttpsClient mHttpsClient;

    /** Represents the result of an update attempt prior to parsing the update response. */
    public enum UpdateResultType {
        SUCCESS,
        UNKNOWN,
        K_ANON_FAILURE,
        // TODO(b/237342352): Consolidate if we don't need to distinguish network timeouts
        NETWORK_CONNECT_TIMEOUT_FAILURE,
        NETWORK_READ_TIMEOUT_FAILURE,
        RESPONSE_VALIDATION_FAILURE
    }

    public BackgroundFetchRunner(
            @NonNull CustomAudienceDao customAudienceDao, @NonNull Flags flags) {
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(flags);
        mCustomAudienceDao = customAudienceDao;
        mFlags = flags;
        mHttpsClient =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        flags.getFledgeBackgroundFetchNetworkConnectTimeoutMs(),
                        flags.getFledgeBackgroundFetchNetworkReadTimeoutMs());
    }

    /**
     * Deletes custom audiences whose expiration timestamps have passed.
     *
     * <p>Also clears corresponding update information from the background fetch DB.
     */
    public void deleteExpiredCustomAudiences(@NonNull Instant jobStartTime) {
        Objects.requireNonNull(jobStartTime);

        LogUtil.d("Starting custom audience garbage collection");
        int numCustomAudiencesDeleted =
                mCustomAudienceDao.deleteAllExpiredCustomAudienceData(jobStartTime);
        LogUtil.d("Deleted %d expired custom audiences", numCustomAudiencesDeleted);
    }

    /** Updates a single given custom audience and persists the results. */
    public void updateCustomAudience(
            @NonNull Instant jobStartTime, @NonNull DBCustomAudienceBackgroundFetchData fetchData) {
        Objects.requireNonNull(jobStartTime);
        Objects.requireNonNull(fetchData);

        CustomAudienceUpdatableData updatableData =
                fetchAndValidateCustomAudienceUpdatableData(
                        jobStartTime, fetchData.getBuyer(), fetchData.getDailyUpdateUrl());
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
     * Fetches the custom audience update from the given daily update URI and validates the response
     * in a {@link CustomAudienceUpdatableData} object.
     */
    @NonNull
    public CustomAudienceUpdatableData fetchAndValidateCustomAudienceUpdatableData(
            @NonNull Instant jobStartTime,
            @NonNull AdTechIdentifier buyer,
            @NonNull Uri dailyFetchUri) {
        Objects.requireNonNull(jobStartTime);
        Objects.requireNonNull(buyer);
        Objects.requireNonNull(dailyFetchUri);

        UpdateResultType fetchResult = UpdateResultType.SUCCESS;
        String updateResponse = "{}";

        // TODO(b/234884352): Perform k-anon check on daily fetch URI

        try {
            ListenableFuture<String> futureResponse = mHttpsClient.fetchPayload(dailyFetchUri);
            updateResponse = futureResponse.get();
        } catch (ExecutionException exception) {
            if (exception.getCause() != null && exception.getCause() instanceof IOException) {
                // TODO(b/237342352): Investigate separating connect and read timeouts
                LogUtil.e(
                        exception,
                        "Timed out while fetching custom audience update from %s",
                        dailyFetchUri.toSafeString());
                fetchResult = UpdateResultType.NETWORK_CONNECT_TIMEOUT_FAILURE;
            } else {
                LogUtil.e(
                        exception,
                        "Encountered unexpected error while fetching custom audience update from"
                                + " %s",
                        dailyFetchUri.toSafeString());
                fetchResult = UpdateResultType.UNKNOWN;
            }
        } catch (CancellationException exception) {
            LogUtil.e(
                    exception,
                    "Custom audience update cancelled while fetching from %s",
                    dailyFetchUri.toSafeString());
            fetchResult = UpdateResultType.UNKNOWN;
        } catch (InterruptedException exception) {
            LogUtil.e(
                    exception,
                    "Custom audience update interrupted while fetching from %s",
                    dailyFetchUri.toSafeString());
            fetchResult = UpdateResultType.UNKNOWN;
            Thread.currentThread().interrupt();
        }

        int maxResponseSizeBytes = mFlags.getFledgeBackgroundFetchMaxResponseSizeB();
        if (fetchResult == UpdateResultType.SUCCESS
                && updateResponse.length() > maxResponseSizeBytes) {
            LogUtil.e(
                    "Custom audience update response is greater than configured max %d bytes",
                    maxResponseSizeBytes);
            fetchResult = UpdateResultType.RESPONSE_VALIDATION_FAILURE;
        }

        return CustomAudienceUpdatableData.createFromResponseString(
                jobStartTime, buyer, fetchResult, updateResponse, mFlags);
    }
}
