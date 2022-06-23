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
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudienceBackgroundFetchData;
import com.android.adservices.service.AdServicesExecutors;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Worker instance for updating custom audiences in the background. */
public class BackgroundFetchWorker {
    private static final Object SINGLETON_LOCK = new Object();

    private static volatile BackgroundFetchWorker sBackgroundFetchWorker;

    private final CustomAudienceDao mCustomAudienceDao;
    private final Flags mFlags;
    private final BackgroundFetchRunner mBackgroundFetchRunner;

    @VisibleForTesting
    protected BackgroundFetchWorker(
            @NonNull CustomAudienceDao customAudienceDao,
            @NonNull Flags flags,
            @NonNull BackgroundFetchRunner backgroundFetchRunner) {
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(backgroundFetchRunner);
        mCustomAudienceDao = customAudienceDao;
        mFlags = flags;
        mBackgroundFetchRunner = backgroundFetchRunner;
    }

    /**
     * Gets an instance of a {@link BackgroundFetchWorker}.
     *
     * <p>If an instance hasn't been initialized, a new singleton will be created and returned.
     */
    @NonNull
    public static BackgroundFetchWorker getInstance(@NonNull Context context) {
        Objects.requireNonNull(context);

        if (sBackgroundFetchWorker == null) {
            synchronized (SINGLETON_LOCK) {
                if (sBackgroundFetchWorker == null) {
                    CustomAudienceDao customAudienceDao =
                            CustomAudienceDatabase.getInstance(context).customAudienceDao();
                    Flags flags = FlagsFactory.getFlags();
                    sBackgroundFetchWorker =
                            new BackgroundFetchWorker(
                                    customAudienceDao,
                                    flags,
                                    new BackgroundFetchRunner(customAudienceDao, flags));
                }
            }
        }

        return sBackgroundFetchWorker;
    }

    /**
     * Runs the background fetch job for FLEDGE, including garbage collection and updating custom
     * audiences.
     *
     * @param jobStartTime the {@link Instant} that the job was started, marking the beginning of
     *     the runtime timeout countdown
     * @throws InterruptedException if the thread was interrupted while waiting for workers to
     *     complete their custom audience updates
     * @throws ExecutionException if an internal exception was thrown while waiting for custom
     *     audience updates to complete
     * @throws TimeoutException if the job exceeds the configured maximum runtime
     */
    public void runBackgroundFetch(@NonNull Instant jobStartTime)
            throws InterruptedException, ExecutionException, TimeoutException {
        Objects.requireNonNull(jobStartTime);

        // Clean up expired custom audiences first so the actual fetch won't do unnecessary work
        mBackgroundFetchRunner.deleteExpiredCustomAudiences(jobStartTime);

        long remainingJobTimeMs =
                mFlags.getFledgeBackgroundFetchJobMaxRuntimeMs()
                        - (Clock.systemUTC().instant().toEpochMilli()
                                - jobStartTime.toEpochMilli());
        if (remainingJobTimeMs <= 0) {
            LogUtil.e("Timed out before updating FLEDGE custom audiences");
            throw new TimeoutException();
        }

        // Fetch stale/eligible/delinquent custom audiences
        final List<DBCustomAudienceBackgroundFetchData> fetchDataList =
                mCustomAudienceDao.getActiveEligibleCustomAudienceBackgroundFetchData(
                        jobStartTime, mFlags.getFledgeBackgroundFetchMaxNumUpdated());

        if (fetchDataList.isEmpty()) {
            LogUtil.d("No custom audiences found to update");
            return;
        } else {
            LogUtil.d("Updating %d custom audiences", fetchDataList.size());
        }

        // Divide the gathered CAs among worker threads
        int numWorkers =
                Math.min(
                        Math.max(1, Runtime.getRuntime().availableProcessors() - 2),
                        mFlags.getFledgeBackgroundFetchThreadPoolSize());
        int numCustomAudiencesPerWorker =
                (fetchDataList.size() / numWorkers)
                        + (((fetchDataList.size() % numWorkers) == 0) ? 0 : 1);

        List<ListenableFuture<Void>> subListFutureUpdates = new ArrayList<>();

        for (final List<DBCustomAudienceBackgroundFetchData> fetchDataSubList :
                Lists.partition(fetchDataList, numCustomAudiencesPerWorker)) {
            subListFutureUpdates.add(
                    AdServicesExecutors.getBackgroundExecutor()
                            .submit(
                                    () -> {
                                        for (DBCustomAudienceBackgroundFetchData fetchData :
                                                fetchDataSubList) {
                                            mBackgroundFetchRunner.updateCustomAudience(
                                                    jobStartTime, fetchData);
                                        }
                                        return null;
                                    }));
        }

        // Wait for all workers to complete within the allotted time
        remainingJobTimeMs =
                mFlags.getFledgeBackgroundFetchJobMaxRuntimeMs()
                        - (Clock.systemUTC().instant().toEpochMilli()
                                - jobStartTime.toEpochMilli());
        Futures.allAsList(subListFutureUpdates).get(remainingJobTimeMs, TimeUnit.MILLISECONDS);
    }
}
