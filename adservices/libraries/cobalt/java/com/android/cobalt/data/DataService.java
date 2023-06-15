/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.cobalt.data;

import static java.util.stream.Collectors.toMap;

import android.annotation.NonNull;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/** Provides essential operations for interacting with Cobalt's database. */
public final class DataService {
    // The Logger resets and doesn't backfill if it is disabled for more than 2 days. 2 days means
    // that the logger was disabled for at least a single full day, and so data should not be sent
    // for at least one day.
    private static final Duration sDisabledResetTime = Duration.ofDays(2);

    private final ExecutorService mExecutorService;
    private final CobaltDatabase mCobaltDatabase;
    private final DaoBuildingBlocks mDaoBuildingBlocks;

    public DataService(@NonNull ExecutorService executor, @NonNull CobaltDatabase cobaltDatabase) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(cobaltDatabase);

        this.mExecutorService = executor;
        this.mCobaltDatabase = cobaltDatabase;
        this.mDaoBuildingBlocks = mCobaltDatabase.daoBuildingBlocks();
    }

    /**
     * Record that the logger is currently disabled.
     *
     * @param currentTime the current time
     * @return ListenableFuture to track the completion of this change
     */
    public ListenableFuture<Void> loggerDisabled(Instant currentTime) {
        GlobalValueEntity entity =
                GlobalValueEntity.create(
                        GlobalValueEntity.Key.INITIAL_DISABLED_TIME, currentTime.toString());
        return Futures.submit(
                () ->
                        mCobaltDatabase.runInTransaction(
                                () -> mDaoBuildingBlocks.insertGlobalValue(entity)),
                mExecutorService);
    }

    /**
     * Record that the logger is currently enabled, and determine how far back to backfill.
     *
     * @param currentTime the current time
     * @return the time when the logger was initially enabled since the last disabling, if it was
     *     for long enough
     */
    public ListenableFuture<Instant> loggerEnabled(Instant currentTime) {
        return Futures.submit(
                () -> mCobaltDatabase.runInTransaction(() -> loggerEnabledSync(currentTime)),
                mExecutorService);
    }

    private Instant loggerEnabledSync(Instant currentTime) {
        Map<GlobalValueEntity.Key, Instant> enablementTimes =
                mDaoBuildingBlocks.queryEnablementTimes().entrySet().stream()
                        .collect(toMap(Map.Entry::getKey, v -> Instant.parse(v.getValue())));
        Instant initialEnabledTime =
                enablementTimes.get(GlobalValueEntity.Key.INITIAL_ENABLED_TIME);
        Instant startDisabledTime =
                enablementTimes.get(GlobalValueEntity.Key.INITIAL_DISABLED_TIME);

        if (startDisabledTime != null) {
            // The logger was disabled, this is the first run since it was enabled.
            if (Duration.between(startDisabledTime, currentTime).compareTo(sDisabledResetTime)
                    > 0) {
                // Disabled for too long, start over from now.
                initialEnabledTime = null;
            }
            mDaoBuildingBlocks.deleteDisabledTime();
        }

        if (initialEnabledTime == null) {
            // Set/update the initial enabled time to the current time.
            initialEnabledTime = currentTime;
            GlobalValueEntity entity =
                    GlobalValueEntity.create(
                            GlobalValueEntity.Key.INITIAL_ENABLED_TIME,
                            initialEnabledTime.toString());
            mDaoBuildingBlocks.insertOrReplaceGlobalValue(entity);
        }

        return initialEnabledTime;
    }
}
