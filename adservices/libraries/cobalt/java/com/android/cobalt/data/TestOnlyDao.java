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

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Dao;
import androidx.room.Embedded;
import androidx.room.Insert;
import androidx.room.MapInfo;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.android.internal.annotations.VisibleForTesting;

import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;
import com.google.cobalt.AggregateValue;
import com.google.cobalt.SystemProfile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Data Access Object offering database operations only required in tests. */
@Dao
@VisibleForTesting
public abstract class TestOnlyDao {

    /** Helper class for retrieving rows of the AggregateStore table. */
    @AutoValue
    @CopyAnnotations
    public abstract static class AggregateStoreTableRow {
        /** Get a builder for the row. */
        public static Builder builder() {
            return new AutoValue_TestOnlyDao_AggregateStoreTableRow.Builder();
        }

        /** Builder class for creating an AggregateStoreTableRow. */
        @AutoValue.Builder
        public abstract static class Builder {
            /** Set the report key for the row. */
            public abstract Builder setReportKey(ReportKey reportKey);

            /** Set the event vector for the row. */
            public abstract Builder setEventVector(EventVector value);

            /** Set the system profile for the row. */
            public abstract Builder setSystemProfile(SystemProfile value);

            /** Set the day index for the row. */
            public abstract Builder setDayIndex(int value);

            /** Set the aggregate value for the row. */
            public abstract Builder setAggregateValue(AggregateValue value);

            /** Build the row. */
            public abstract AggregateStoreTableRow build();
        }

        /** Create a new row. */
        @NonNull
        public static AggregateStoreTableRow create(
                ReportKey reportKey,
                int dayIndex,
                EventVector eventVector,
                SystemProfile systemProfile,
                AggregateValue aggregateValue) {
            return builder()
                    .setReportKey(reportKey)
                    .setDayIndex(dayIndex)
                    .setEventVector(eventVector)
                    .setSystemProfile(systemProfile)
                    .setAggregateValue(aggregateValue)
                    .build();
        }

        /** Get the report key of the row. */
        @CopyAnnotations
        @Embedded
        @NonNull
        public abstract ReportKey reportKey();

        /** Get the event vector of the row. */
        @CopyAnnotations
        @ColumnInfo(name = "event_vector")
        @NonNull
        public abstract EventVector eventVector();

        /** Get the system profile of the row. */
        @CopyAnnotations
        @ColumnInfo(name = "system_profile")
        @NonNull
        public abstract SystemProfile systemProfile();

        /** Get the day index of the row. */
        @CopyAnnotations
        @ColumnInfo(name = "day_index")
        public abstract int dayIndex();

        /** Get the aggregate value of the row. */
        @CopyAnnotations
        @ColumnInfo(name = "aggregate_value")
        @NonNull
        public abstract AggregateValue aggregateValue();
    }

    /** Get all the aggregate data from the database. */
    @Query(
            "SELECT customer_id, project_id, metric_id, report_id, day_index, event_vector, "
                    + "system_profile, aggregate_value FROM AggregateStore INNER JOIN "
                    + "SystemProfiles ON AggregateStore.system_profile_hash = SystemProfiles"
                    + ".system_profile_hash "
                    + "ORDER BY customer_id, project_id, metric_id, report_id, day_index, "
                    + "event_vector, AggregateStore.system_profile_hash, aggregate_value")
    public abstract List<AggregateStoreTableRow> getAllAggregates();

    /** Insert and aggregate value row. */
    public void insertAggregateValue(AggregateStoreTableRow aggregateStoreTableRow) {
        long systemProfileHash =
                SystemProfileEntity.getSystemProfileHash(aggregateStoreTableRow.systemProfile());
        insertLastSentDayIndex(ReportEntity.create(aggregateStoreTableRow.reportKey()));
        insertSystemProfile(
                SystemProfileEntity.create(
                        systemProfileHash, aggregateStoreTableRow.systemProfile()));
        insertAggregateValue(
                AggregateStoreEntity.create(
                        aggregateStoreTableRow.reportKey(),
                        aggregateStoreTableRow.dayIndex(),
                        aggregateStoreTableRow.eventVector(),
                        systemProfileHash,
                        aggregateStoreTableRow.aggregateValue()));
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract Void insertLastSentDayIndex(ReportEntity reportEntity);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract Void insertSystemProfile(SystemProfileEntity systemProfileEntity);

    @Insert(onConflict = OnConflictStrategy.ROLLBACK)
    abstract Void insertAggregateValue(AggregateStoreEntity aggregateStoreEntity);

    /** Get the time Cobalt was enabled. */
    public Optional<Instant> getInitialEnabledTime() {
        return Optional.ofNullable(
                        queryEnablementTimes().get(GlobalValueEntity.Key.INITIAL_ENABLED_TIME))
                .map(GlobalValueEntity::timeFromDbString);
    }

    /** Get the time Cobalt was disabled. */
    public Optional<Instant> getStartDisabledTime() {
        return Optional.ofNullable(
                        queryEnablementTimes().get(GlobalValueEntity.Key.INITIAL_DISABLED_TIME))
                .map(GlobalValueEntity::timeFromDbString);
    }

    @MapInfo(keyColumn = "key", valueColumn = "value")
    @Query(
            "SELECT * FROM GlobalValues WHERE key IN ('INITIAL_ENABLED_TIME',"
                    + " 'INITIAL_DISABLED_TIME')")
    abstract Map<GlobalValueEntity.Key, String> queryEnablementTimes();

    /** Delete all reports */
    @VisibleForTesting
    @Query("DELETE FROM Reports")
    public abstract void deleteAllReports();
}
