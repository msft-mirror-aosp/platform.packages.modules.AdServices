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

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.MapInfo;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.google.cobalt.AggregateValue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data Access Object offering low-level operations to the database that can be used for more
 * complex work.
 */
@Dao
abstract class DaoBuildingBlocks {
    /**
     * Insert an entry into the system profiles table.
     *
     * @param systemProfileEntity the system profile to insert
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract Void insertSystemProfile(SystemProfileEntity systemProfileEntity);

    /**
     * Insert an aggregate value in the aggregate store.
     *
     * @param aggregateStoreEntity the aggregate to insert
     */
    @Insert(onConflict = OnConflictStrategy.ROLLBACK)
    abstract Void insertAggregateValue(AggregateStoreEntity aggregateStoreEntity);

    /**
     * Insert the value for ths key of `globalValueEntity`.
     *
     * @param globalValueEntity the key, value pair to insert or update
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract Void insertGlobalValue(GlobalValueEntity globalValueEntity);

    /**
     * Insert or update the value for ths key of `globalValueEntity`.
     *
     * @param globalValueEntity the key, value pair to insert or update
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract Void insertOrReplaceGlobalValue(GlobalValueEntity globalValueEntity);

    /**
     * Insert the day a report was last sent.
     *
     * @param reportEntity the report and day index to insert
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract Void insertLastSentDayIndex(ReportEntity reportEntity);

    /**
     * Insert observations into the observation store.
     *
     * @param observationStoreEntities the observations to insert
     */
    @Insert(onConflict = OnConflictStrategy.ROLLBACK)
    abstract Void insertObservations(List<ObservationStoreEntity> observationStoreEntities);

    /**
     * Update the day a report was last sent.
     *
     * @param reportEntity the report and day index to update
     */
    @Update(entity = ReportEntity.class)
    abstract Void updateLastSentDayIndex(ReportEntity reportEntity);

    /**
     * Update an aggregate value in the aggregate store.
     *
     * @param customerId the customer id to update the value under
     * @param projectId the project id to update the value under
     * @param metricId the metric id to update the value under
     * @param reportId the report id to update the value under
     * @param dayIndex the day the event occrurred
     * @param eventVector the event vector the value is being aggregated for
     * @param systemProfileHash the system profile hash of the event
     * @param newAggregateValue the new aggregate value
     */
    @Query(
            "UPDATE AggregateStore "
                    + "SET aggregate_value = :newAggregateValue "
                    + "WHERE customer_id = :customerId "
                    + "AND project_id = :projectId "
                    + "AND metric_id = :metricId "
                    + "AND report_id = :reportId "
                    + "AND day_index= :dayIndex "
                    + "AND event_vector = :eventVector "
                    + "AND system_profile_hash = :systemProfileHash")
    abstract Void updateAggregateValue(
            long customerId,
            long projectId,
            long metricId,
            long reportId,
            int dayIndex,
            EventVector eventVector,
            long systemProfileHash,
            AggregateValue newAggregateValue);

    /**
     * Update the system profile hash of an aggregate value.
     *
     * @param customerId the customer id to update the value under
     * @param projectId the project id to update the value under
     * @param metricId the metric id to update the value under
     * @param reportId the report id to update the value under
     * @param dayIndex the day the event occrurred
     * @param eventVector the event vector the value is being aggregated for
     * @param currentSystemProfileHash the current system profile hash of the event
     * @param newSystemProfileHash the new system profile hash of the event
     */
    @Query(
            "UPDATE AggregateStore "
                    + "SET system_profile_hash = :newSystemProfileHash "
                    + "WHERE customer_id = :customerId "
                    + "AND project_id = :projectId "
                    + "AND metric_id = :metricId "
                    + "AND report_id = :reportId "
                    + "AND day_index= :dayIndex "
                    + "AND event_vector = :eventVector "
                    + "AND system_profile_hash = :currentSystemProfileHash")
    abstract Void updateSystemProfileHash(
            long customerId,
            long projectId,
            long metricId,
            long reportId,
            int dayIndex,
            EventVector eventVector,
            long currentSystemProfileHash,
            long newSystemProfileHash);

    /**
     * Update the system profile hash and aggregate value of an event.
     *
     * @param customerId the customer id to update the value under
     * @param projectId the project id to update the value under
     * @param metricId the metric id to update the value under
     * @param reportId the report id to update the value under
     * @param dayIndex the day the event occrurred
     * @param eventVector the event vector the value is being aggregated for
     * @param currentSystemProfileHash the current system profile hash of the event
     * @param newSystemProfileHash the new system profile hash of the event
     * @param newAggregateValue the new aggregate value
     */
    @Query(
            "UPDATE AggregateStore "
                    + "SET system_profile_hash = :newSystemProfileHash, "
                    + "aggregate_value = :newAggregateValue "
                    + "WHERE customer_id = :customerId "
                    + "AND project_id = :projectId "
                    + "AND metric_id = :metricId "
                    + "AND report_id = :reportId "
                    + "AND day_index= :dayIndex "
                    + "AND event_vector = :eventVector "
                    + "AND system_profile_hash = :currentSystemProfileHash")
    abstract Void updateSystemProfileHashAndAggregateValue(
            long customerId,
            long projectId,
            long metricId,
            long reportId,
            int dayIndex,
            EventVector eventVector,
            long currentSystemProfileHash,
            long newSystemProfileHash,
            AggregateValue newAggregateValue);

    /**
     * Query the saved global values related to enablement.
     *
     * @return a map of the enablement values which have been saved
     */
    @MapInfo(keyColumn = "key", valueColumn = "value")
    @Query(
            "SELECT * FROM GlobalValues WHERE key IN ('INITIAL_ENABLED_TIME',"
                    + " 'INITIAL_DISABLED_TIME')")
    abstract Map<GlobalValueEntity.Key, String> queryEnablementTimes();

    /**
     * Selects one (system profile hash, aggregate value) pair from the DB for the given report,
     * day, and event vector to update. The returned system profile hash will be
     * `systemProfileHashHint` if it exists for the report, day, and event vector. Otherwise, a row
     * with a random system profile associated with report, day, and event vector will be returned.
     *
     * @param customerId the customer selected values are for
     * @param projectId the project selected values are for
     * @param metricId the metric selected values are for
     * @param reportId the report selected values are for
     * @param dayIndex the day selected values are aggregated on
     * @param eventVector the event codes (if any) selected values match
     * @param systemProfileHashHint the system profile of the record to return, if it exists
     * @return the system profile hash and aggregate value, if one is found
     */
    @Query(
            "SELECT system_profile_hash, aggregate_value FROM AggregateStore "
                    + "WHERE customer_id = :customerId "
                    + "AND project_id = :projectId "
                    + "AND metric_id = :metricId "
                    + "AND report_id = :reportId "
                    + "AND day_index = :dayIndex "
                    + "AND event_vector = :eventVector "
                    + "ORDER BY system_profile_hash = :systemProfileHashHint DESC, "
                    + "system_profile_hash ASC "
                    + "LIMIT 1")
    abstract Optional<SystemProfileAndAggregateValue> queryOneSystemProfileAndAggregateValue(
            long customerId,
            long projectId,
            long metricId,
            long reportId,
            int dayIndex,
            EventVector eventVector,
            long systemProfileHashHint);

    /**
     * Count the number of distinct event vectors saved for a report on a given day and under a
     * specific system profile.
     *
     * @param customerId the customer id to search ue under
     * @param projectId the project id to search under
     * @param metricId the metric id to search under
     * @param reportId the report id to search under
     * @param dayIndex the day to search under
     * @param systemProfileHash the system profile hash of the event
     * @return the number of distnct event codes
     */
    @Query(
            "SELECT COUNT(DISTINCT event_vector) "
                    + "FROM AggregateStore "
                    + "WHERE customer_id = :customerId "
                    + "AND project_id = :projectId "
                    + "AND metric_id = :metricId "
                    + "AND report_id = :reportId "
                    + "AND day_index= :dayIndex "
                    + "AND system_profile_hash = :systemProfileHash")
    abstract int queryCountEventVectors(
            long customerId,
            long projectId,
            long metricId,
            long reportId,
            int dayIndex,
            long systemProfileHash);

    /**
     * Get the aggregated values for a given report on a given day.
     *
     * @param customerId the customer id to search under
     * @param projectId the project id to search under
     * @param metricId the metric id to search under
     * @param reportId the report id to search under
     * @param dayIndex the day to search under
     * @return a list of events to be used for observation generation
     */
    @Query(
            "SELECT "
                    + "aggregate.system_profile_hash, "
                    + "profile.system_profile, "
                    + "aggregate.event_vector, "
                    + "aggregate.aggregate_value "
                    + "FROM "
                    + "AggregateStore AS aggregate "
                    + "INNER JOIN SystemProfiles AS profile "
                    + "ON aggregate.system_profile_hash = profile.system_profile_hash "
                    + "WHERE customer_id = :customerId "
                    + "AND project_id = :projectId "
                    + "AND metric_id = :metricId "
                    + "AND report_id = :reportId "
                    + "AND day_index= :dayIndex "
                    + "ORDER BY aggregate.system_profile_hash, "
                    + "aggregate.event_vector")
    abstract List<CountEvent> queryCountEventsForDay(
            long customerId, long projectId, long metricId, long reportId, int dayIndex);

    /**
     * Return the observations in the observation store, ordered by creation time.
     *
     * @return an list of observations, ordered by creation time
     */
    @Query("SELECT * FROM ObservationStore ORDER BY observation_store_id")
    abstract List<ObservationStoreEntity> queryOldestObservations();

    /**
     * Return the day a report was last sent, if in the reports table.
     *
     * @param customerId the customer id for the report
     * @param projectId the project id for the report
     * @param metricId the metric id for the report
     * @param reportId the report id for the report
     * @return the last sent day index, if found
     */
    @Query(
            "SELECT last_sent_day_index "
                    + "FROM Reports "
                    + "WHERE customer_id = :customerId "
                    + "AND project_id = :projectId "
                    + "AND metric_id = :metricId "
                    + "AND report_id = :reportId")
    abstract Optional<Integer> queryLastSentDayIndex(
            long customerId, long projectId, long metricId, long reportId);

    /** Delete the saved initial disabled time. */
    @Query("DELETE FROM GlobalValues WHERE key = 'INITIAL_DISABLED_TIME'")
    abstract Void deleteDisabledTime();

    /**
     * Delete aggregate values from before the specified day.
     *
     * @param oldestDayIndex oldest day index to keep events from
     */
    @Query("DELETE FROM AggregateStore WHERE day_index < :oldestDayIndex")
    abstract Void deleteOldAggregates(int oldestDayIndex);

    /** Delete system profiles which don't appear in the aggregate store. */
    @Query(
            "DELETE FROM SystemProfiles "
                    + "WHERE system_profile_hash NOT IN ("
                    + "SELECT DISTINCT system_profile_hash FROM AggregateStore"
                    + ")")
    abstract Void deleteUnusedSystemProfileHashes();

    /**
     * Delete the specified observations from the observation store.
     *
     * @param observationStoreIds ids of the observations to delete
     */
    @Query("DELETE FROM ObservationStore WHERE observation_store_id IN (:observationStoreIds)")
    abstract Void deleteByObservationId(List<Integer> observationStoreIds);
}
