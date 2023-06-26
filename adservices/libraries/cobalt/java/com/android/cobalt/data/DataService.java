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
import android.util.Log;

import com.google.cobalt.AggregateValue;
import com.google.cobalt.SystemProfile;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/** Provides essential operations for interacting with Cobalt's database. */
public final class DataService {
    private static final String LOG_TAG = "cobalt.data";

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
                        GlobalValueEntity.Key.INITIAL_DISABLED_TIME,
                        GlobalValueEntity.timeToDbString(currentTime));
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
                        .collect(
                                toMap(
                                        Map.Entry::getKey,
                                        v -> GlobalValueEntity.timeFromDbString(v.getValue())));
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
                            GlobalValueEntity.timeToDbString(initialEnabledTime));
            mDaoBuildingBlocks.insertOrReplaceGlobalValue(entity);
        }

        return initialEnabledTime;
    }

    /**
     * Update the aggregated data for a COUNT report in response to an event that occurred.
     *
     * <p>For the given report, dayIndex, systemProfile, and eventVector at the time of the event,
     * check whether the DB already contains an entry. If so, add count to its aggregateValue and
     * update the entry. If not, create a new entry with count as the aggregateValue. This only
     * supports the REPORT_ALL system profile selection policy.
     *
     * @param reportKey the report being aggregated
     * @param dayIndex the day on which the event occurred
     * @param systemProfile the SystemProfile on the device at the time of the event
     * @param eventVector the event the value was logged for
     * @param eventVectorBufferMax the maximum number of event vectors to store per
     *     report/day/profile
     * @param count the count value of the event
     */
    public ListenableFuture<Void> aggregateCount(
            ReportKey reportKey,
            int dayIndex,
            SystemProfile systemProfile,
            EventVector eventVector,
            long eventVectorBufferMax,
            long count) {
        return Futures.submit(
                () ->
                        mCobaltDatabase.runInTransaction(
                                () ->
                                        aggregateCountSync(
                                                reportKey,
                                                dayIndex,
                                                systemProfile,
                                                eventVector,
                                                eventVectorBufferMax,
                                                count)),
                mExecutorService);
    }

    private void aggregateCountSync(
            ReportKey reportKey,
            int dayIndex,
            SystemProfile systemProfile,
            EventVector eventVector,
            long eventVectorBufferMax,
            long count) {
        long systemProfileHash = SystemProfileEntity.getSystemProfileHash(systemProfile);
        mDaoBuildingBlocks.insertSystemProfile(
                SystemProfileEntity.create(systemProfileHash, systemProfile));
        mDaoBuildingBlocks.insertLastSentDayIndex(ReportEntity.create(reportKey, dayIndex - 1));

        Optional<SystemProfileAndAggregateValue> existingSystemProfileAndAggregateValue =
                mDaoBuildingBlocks.queryOneSystemProfileAndAggregateValue(
                        reportKey, dayIndex, eventVector, systemProfileHash);

        if (existingSystemProfileAndAggregateValue.isEmpty()) {
            // No aggregate value was found for the provided report, day index, and event vector
            // combination, insert one.
            insertAggregateRow(
                    reportKey,
                    dayIndex,
                    systemProfileHash,
                    eventVector,
                    eventVectorBufferMax,
                    AggregateValue.newBuilder().setIntegerValue(count).build());
            return;
        }

        // An existing entry matches the provided report, day index, and event vector combination,
        // update an existing system profile or add a new one.
        long existingSystemProfileHash =
                existingSystemProfileAndAggregateValue.get().systemProfileHash();
        if (existingSystemProfileHash == systemProfileHash) {
            // The system profile in the DB should be used, update the value.
            AggregateValue existingAggregateValue =
                    existingSystemProfileAndAggregateValue.get().aggregateValue();
            mDaoBuildingBlocks.updateAggregateValue(
                    reportKey,
                    dayIndex,
                    eventVector,
                    systemProfileHash,
                    existingAggregateValue.toBuilder()
                            .setIntegerValue(existingAggregateValue.getIntegerValue() + count)
                            .build());
            return;
        }

        // All system profiles should be reported, add the system profile and value.
        insertAggregateRow(
                reportKey,
                dayIndex,
                systemProfileHash,
                eventVector,
                eventVectorBufferMax,
                AggregateValue.newBuilder().setIntegerValue(count).build());
    }

    /**
     * Associates `newValue` with the provided report, day index, event vector, and system profile
     * in the DB, if present. Does nothing otherwise.
     */
    private void insertAggregateRow(
            ReportKey reportKey,
            int dayIndex,
            long systemProfileHash,
            EventVector eventVector,
            long eventVectorBufferMax,
            AggregateValue newValue) {
        if (!canAddEventVectorToSystemProfile(
                reportKey, dayIndex, systemProfileHash, eventVectorBufferMax)) {
            return;
        }
        mDaoBuildingBlocks.insertAggregateValue(
                AggregateStoreEntity.create(
                        reportKey, dayIndex, eventVector, systemProfileHash, newValue));
    }

    private boolean canAddEventVectorToSystemProfile(
            ReportKey reportKey, int dayIndex, long systemProfileHash, long eventVectorBufferMax) {
        if (eventVectorBufferMax == 0) {
            return true;
        }

        long numEventVectors =
                mDaoBuildingBlocks.queryCountEventVectors(reportKey, dayIndex, systemProfileHash);
        if (numEventVectors >= eventVectorBufferMax) {
            if (Log.isLoggable(LOG_TAG, Log.WARN)) {
                Log.w(
                        LOG_TAG,
                        String.format(
                                Locale.US,
                                "Dropping eventVector for report %s, due to exceeding "
                                        + "event_vector_buffer_max %s",
                                reportKey,
                                eventVectorBufferMax));
            }
            return false;
        }
        return true;
    }
}
