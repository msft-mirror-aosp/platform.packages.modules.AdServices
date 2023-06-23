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

package com.android.adservices.data.adselection;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.FrequencyCapFilters;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.android.adservices.service.adselection.HistogramEvent;

import com.google.common.base.Preconditions;

import java.time.Instant;
import java.util.Objects;

/**
 * DAO used to access ad counter histogram data used in frequency cap filtering during ad selection.
 *
 * <p>Annotated abstract methods will generate their own Room DB implementations.
 */
@Dao
public abstract class FrequencyCapDao {
    /**
     * Attempts to persist a new {@link DBHistogramIdentifier} to the identifier table.
     *
     * <p>If there is already an identifier persisted with the same foreign key (see {@link
     * DBHistogramIdentifier#getHistogramIdentifierForeignKey()}), the transaction is canceled and
     * rolled back.
     *
     * <p>This method is not intended to be called on its own. Please use {@link
     * #insertHistogramEvent(HistogramEvent, int, int, int, int)} instead.
     *
     * @return the row ID of the identifier in the table, or {@code -1} if the specified row ID is
     *     already occupied
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract long insertNewHistogramIdentifier(@NonNull DBHistogramIdentifier identifier);

    /**
     * Returns the foreign key ID for the identifier matching the given constraints, or {@code null}
     * if no match is found.
     *
     * <p>If multiple matches are found, only the first (as ordered by the numerical ID) is
     * returned.
     *
     * <p>This method is not intended to be called on its own. It should only be used in {@link
     * #insertHistogramEvent(HistogramEvent, int, int, int, int)}.
     *
     * @return the row ID of the identifier in the table, or {@code null} if not found
     */
    @Query(
            "SELECT foreign_key_id FROM fcap_histogram_ids "
                    + "WHERE buyer = :buyer "
                    // Note that the IS operator in SQLite specifically is equivalent to = for value
                    // matching except that it also matches NULL
                    + "AND custom_audience_owner IS :customAudienceOwner "
                    + "AND custom_audience_name IS :customAudienceName "
                    + "ORDER BY foreign_key_id ASC "
                    + "LIMIT 1")
    @Nullable
    protected abstract Long getHistogramIdentifierForeignKeyIfExists(
            @NonNull AdTechIdentifier buyer,
            @Nullable String customAudienceOwner,
            @Nullable String customAudienceName);

    /**
     * Attempts to persist a new {@link DBHistogramEventData} to the event data table.
     *
     * <p>If there is already an entry in the table with the same non-{@code null} row ID, the
     * transaction is canceled and rolled back.
     *
     * <p>This method is not intended to be called on its own. Please use {@link
     * #insertHistogramEvent(HistogramEvent, int, int, int, int)} instead.
     *
     * @return the row ID of the event data in the table, or -1 if the event data already exists
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract long insertNewHistogramEventData(@NonNull DBHistogramEventData eventData);

    /**
     * Attempts to insert a {@link HistogramEvent} into the histogram tables in a single
     * transaction.
     *
     * <p>If the current number of events in the histogram table is larger than the given {@code
     * absoluteMaxTotalHistogramEventCount}, then the oldest events in the table will be evicted so
     * that the count of events is the given {@code lowerMaxTotalHistogramEventCount}.
     *
     * @throws IllegalStateException if an error was encountered adding the event
     */
    @Transaction
    public void insertHistogramEvent(
            @NonNull HistogramEvent event,
            int absoluteMaxTotalHistogramEventCount,
            int lowerMaxTotalHistogramEventCount,
            int absoluteMaxPerBuyerHistogramEventCount,
            int lowerMaxPerBuyerHistogramEventCount)
            throws IllegalStateException {
        Objects.requireNonNull(event);
        Preconditions.checkArgument(absoluteMaxTotalHistogramEventCount > 0);
        Preconditions.checkArgument(lowerMaxTotalHistogramEventCount > 0);
        Preconditions.checkArgument(absoluteMaxPerBuyerHistogramEventCount > 0);
        Preconditions.checkArgument(lowerMaxPerBuyerHistogramEventCount > 0);
        Preconditions.checkArgument(
                absoluteMaxTotalHistogramEventCount > lowerMaxTotalHistogramEventCount);
        Preconditions.checkArgument(
                absoluteMaxPerBuyerHistogramEventCount > lowerMaxPerBuyerHistogramEventCount);

        // TODO(b/275581841): Collect and send telemetry on frequency cap eviction
        int numEventsDeleted = 0;

        // Check the table size first and evict older events if necessary
        int currentTotalHistogramEventCount = getTotalNumHistogramEvents();
        if (currentTotalHistogramEventCount >= absoluteMaxTotalHistogramEventCount) {
            int numEventsToDelete =
                    currentTotalHistogramEventCount - lowerMaxTotalHistogramEventCount;
            numEventsDeleted += deleteOldestHistogramEventData(numEventsToDelete);
        }

        // Check the per-buyer quota
        int currentPerBuyerHistogramEventCount = getNumHistogramEventsByBuyer(event.getBuyer());
        if (currentPerBuyerHistogramEventCount >= absoluteMaxPerBuyerHistogramEventCount) {
            int numEventsToDelete =
                    currentPerBuyerHistogramEventCount - lowerMaxPerBuyerHistogramEventCount;
            numEventsDeleted +=
                    deleteOldestHistogramEventDataByBuyer(event.getBuyer(), numEventsToDelete);
        }

        // Be efficient with I/O operations; background maintenance job will also clean up data
        if (numEventsDeleted > 0) {
            deleteUnpairedHistogramIdentifiers();
        }

        // Converting to DBHistogramIdentifier drops custom audience fields if the type is WIN
        DBHistogramIdentifier identifier = DBHistogramIdentifier.fromHistogramEvent(event);
        Long foreignKeyId =
                getHistogramIdentifierForeignKeyIfExists(
                        identifier.getBuyer(),
                        identifier.getCustomAudienceOwner(),
                        identifier.getCustomAudienceName());

        if (foreignKeyId == null) {
            try {
                foreignKeyId = insertNewHistogramIdentifier(identifier);
            } catch (Exception exception) {
                throw new IllegalStateException("Error inserting histogram identifier", exception);
            }
        }

        try {
            insertNewHistogramEventData(
                    DBHistogramEventData.fromHistogramEvent(foreignKeyId, event));
        } catch (Exception exception) {
            throw new IllegalStateException("Error inserting histogram event data", exception);
        }
    }

    /**
     * Returns the number of events in the appropriate buyer's histograms that have been registered
     * since the given timestamp.
     *
     * @return the number of found events that match the criteria
     */
    @Query(
            "SELECT COUNT(DISTINCT data.row_id) FROM fcap_histogram_data AS data "
                    + "INNER JOIN fcap_histogram_ids AS ids "
                    + "ON data.foreign_key_id = ids.foreign_key_id "
                    + "WHERE data.ad_counter_int_key = :adCounterIntKey "
                    + "AND ids.buyer = :buyer "
                    + "AND data.ad_event_type = :adEventType "
                    + "AND data.timestamp >= :startTime")
    public abstract int getNumEventsForBuyerAfterTime(
            int adCounterIntKey,
            @NonNull AdTechIdentifier buyer,
            @FrequencyCapFilters.AdEventType int adEventType,
            @NonNull Instant startTime);

    /**
     * Returns the number of events in the appropriate custom audience's histogram that have been
     * registered since the given timestamp.
     *
     * @return the number of found events that match the criteria
     */
    @Query(
            "SELECT COUNT(DISTINCT data.row_id) FROM fcap_histogram_data AS data "
                    + "INNER JOIN fcap_histogram_ids AS ids "
                    + "ON data.foreign_key_id = ids.foreign_key_id "
                    + "WHERE data.ad_counter_int_key = :adCounterIntKey "
                    + "AND ids.buyer = :buyer "
                    + "AND ids.custom_audience_owner = :customAudienceOwner "
                    + "AND ids.custom_audience_name = :customAudienceName "
                    + "AND data.ad_event_type = :adEventType "
                    + "AND data.timestamp >= :startTime")
    public abstract int getNumEventsForCustomAudienceAfterTime(
            int adCounterIntKey,
            @NonNull AdTechIdentifier buyer,
            @NonNull String customAudienceOwner,
            @NonNull String customAudienceName,
            @FrequencyCapFilters.AdEventType int adEventType,
            @NonNull Instant startTime);

    /**
     * Deletes all histogram event data older than the given {@code expiryTime}.
     *
     * <p>This method is not intended to be called on its own. Please use {@link
     * #deleteAllExpiredHistogramData(Instant)} instead.
     *
     * @return the number of deleted events
     */
    @Query("DELETE FROM fcap_histogram_data WHERE timestamp < :expiryTime")
    protected abstract int deleteHistogramEventDataBeforeTime(@NonNull Instant expiryTime);

    /**
     * Deletes the oldest {@code N} histogram events, where {@code N} is at most {@code
     * numEventsToDelete}, and returns the number of entries deleted.
     *
     * <p>This method is not meant to be called on its own. Please use {@link
     * #insertHistogramEvent(HistogramEvent, int, int, int, int)} to evict data when the table is
     * full.
     */
    @Query(
            "DELETE FROM fcap_histogram_data "
                    + "WHERE row_id IN "
                    + "(SELECT row_id FROM fcap_histogram_data "
                    + "ORDER BY timestamp ASC "
                    + "LIMIT :numEventsToDelete)")
    protected abstract int deleteOldestHistogramEventData(int numEventsToDelete);

    /**
     * Deletes the oldest {@code N} histogram events that belong to a given {@code buyer}, where
     * {@code N} is at most {@code numEventsToDelete}, and returns the number of entries deleted.
     *
     * <p>This method is not meant to be called on its own. Please use {@link
     * #insertHistogramEvent(HistogramEvent, int, int, int, int)} to evict data when the table is
     * full.
     */
    @Query(
            "DELETE FROM fcap_histogram_data "
                    + "WHERE row_id IN "
                    + "(SELECT data.row_id FROM fcap_histogram_data AS data "
                    + "INNER JOIN fcap_histogram_ids AS ids "
                    + "ON data.foreign_key_id = ids.foreign_key_id "
                    + "WHERE ids.buyer = :buyer "
                    + "ORDER BY data.timestamp ASC "
                    + "LIMIT :numEventsToDelete)")
    protected abstract int deleteOldestHistogramEventDataByBuyer(
            @NonNull AdTechIdentifier buyer, int numEventsToDelete);

    /**
     * Deletes histogram identifiers which have no associated event data.
     *
     * <p>This method is not intended to be called on its own. Please use {@link
     * #deleteAllExpiredHistogramData(Instant)} instead.
     *
     * @return the number of deleted identifiers
     */
    @Query(
            "DELETE FROM fcap_histogram_ids "
                    + "WHERE foreign_key_id NOT IN "
                    + "(SELECT ids.foreign_key_id FROM fcap_histogram_ids AS ids "
                    + "INNER JOIN fcap_histogram_data AS data "
                    + "ON ids.foreign_key_id = data.foreign_key_id)")
    protected abstract int deleteUnpairedHistogramIdentifiers();

    /**
     * Deletes all histogram data older than the given {@code expiryTime} in a single database
     * transaction.
     *
     * <p>Also cleans up any histogram identifiers which are no longer associated with any event
     * data.
     *
     * @return the number of deleted events
     */
    @Transaction
    public int deleteAllExpiredHistogramData(@NonNull Instant expiryTime) {
        Objects.requireNonNull(expiryTime);

        int numDeletedEvents = deleteHistogramEventDataBeforeTime(expiryTime);
        deleteUnpairedHistogramIdentifiers();
        return numDeletedEvents;
    }

    /**
     * Deletes all histogram event data.
     *
     * <p>This method is not meant to be called on its own. Please use {@link
     * #deleteAllHistogramData()} to delete all histogram data (including all identifiers).
     *
     * @return the number of deleted events
     */
    @Query("DELETE FROM fcap_histogram_data")
    protected abstract int deleteAllHistogramEventData();

    /**
     * Deletes all histogram identifiers.
     *
     * <p>This method is not meant to be called on its own. Please use {@link
     * #deleteAllHistogramData()} to delete all histogram data (including all identifiers).
     *
     * @return the number of deleted identifiers
     */
    @Query("DELETE FROM fcap_histogram_ids")
    protected abstract int deleteAllHistogramIdentifiers();

    /**
     * Deletes all histogram data and identifiers in a single database transaction.
     *
     * @return the number of deleted events
     */
    @Transaction
    public int deleteAllHistogramData() {
        int numDeletedEvents = deleteAllHistogramEventData();
        deleteAllHistogramIdentifiers();
        return numDeletedEvents;
    }

    /** Returns the current total number of histogram events in the data table. */
    @Query("SELECT COUNT(DISTINCT row_id) FROM fcap_histogram_data")
    public abstract int getTotalNumHistogramEvents();

    /** Returns the current total number of histogram identifiers in the identifier table. */
    @Query("SELECT COUNT(DISTINCT foreign_key_id) FROM fcap_histogram_ids")
    public abstract int getTotalNumHistogramIdentifiers();

    /**
     * Returns the current number of histogram events in the data table for the given {@code buyer}.
     */
    @Query(
            "SELECT COUNT(DISTINCT data.row_id) FROM fcap_histogram_data AS data "
                    + "INNER JOIN fcap_histogram_ids AS ids "
                    + "ON data.foreign_key_id = ids.foreign_key_id "
                    + "WHERE ids.buyer = :buyer ")
    public abstract int getNumHistogramEventsByBuyer(@NonNull AdTechIdentifier buyer);
}
