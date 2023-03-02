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
     * #insertHistogramEvent(HistogramEvent)} instead.
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
     * #insertHistogramEvent(HistogramEvent)}.
     *
     * @return the row ID of the identifier in the table, or {@code null} if not found
     */
    @Query(
            "SELECT foreign_key_id FROM fcap_histogram_ids "
                    + "WHERE ad_counter_key = :adCounterKey "
                    + "AND buyer = :buyer "
                    + "AND custom_audience_owner = :customAudienceOwner "
                    + "AND custom_audience_name = :customAudienceName "
                    + "ORDER BY foreign_key_id ASC "
                    + "LIMIT 1")
    @Nullable
    protected abstract Long getHistogramIdentifierForeignKeyIfExists(
            @NonNull String adCounterKey,
            @NonNull AdTechIdentifier buyer,
            @NonNull String customAudienceOwner,
            @NonNull String customAudienceName);

    /**
     * Attempts to persist a new {@link DBHistogramEventData} to the event data table.
     *
     * <p>If there is already an entry in the table with the same non-{@code null} row ID, the
     * transaction is canceled and rolled back.
     *
     * <p>This method is not intended to be called on its own. Please use {@link
     * #insertHistogramEvent(HistogramEvent)} instead.
     *
     * @return the row ID of the event data in the table, or -1 if the event data already exists
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract long insertNewHistogramEventData(@NonNull DBHistogramEventData eventData);

    /**
     * Attempts to insert a {@link HistogramEvent} into the histogram tables in a single
     * transaction.
     *
     * @throws IllegalStateException if an error was encountered adding the event
     */
    @Transaction
    public void insertHistogramEvent(@NonNull HistogramEvent event) throws IllegalStateException {
        Objects.requireNonNull(event);

        Long foreignKeyId =
                getHistogramIdentifierForeignKeyIfExists(
                        event.getAdCounterKey(),
                        event.getBuyer(),
                        event.getCustomAudienceOwner(),
                        event.getCustomAudienceName());
        if (foreignKeyId == null) {
            try {
                foreignKeyId =
                        insertNewHistogramIdentifier(
                                DBHistogramIdentifier.fromHistogramEvent(event));
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
                    + "WHERE ids.ad_counter_key = :adCounterKey "
                    + "AND ids.buyer = :buyer "
                    + "AND data.ad_event_type = :adEventType "
                    + "AND data.timestamp >= :startTime")
    public abstract int getNumEventsForBuyerAfterTime(
            @NonNull String adCounterKey,
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
                    + "WHERE ids.ad_counter_key = :adCounterKey "
                    + "AND ids.buyer = :buyer "
                    + "AND ids.custom_audience_owner = :customAudienceOwner "
                    + "AND ids.custom_audience_name = :customAudienceName "
                    + "AND data.ad_event_type = :adEventType "
                    + "AND data.timestamp >= :startTime")
    public abstract int getNumEventsForCustomAudienceAfterTime(
            @NonNull String adCounterKey,
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
    protected abstract int deleteHistogramEventsBeforeTime(@NonNull Instant expiryTime);

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

        int numDeletedEvents = deleteHistogramEventsBeforeTime(expiryTime);
        deleteUnpairedHistogramIdentifiers();
        return numDeletedEvents;
    }
}
