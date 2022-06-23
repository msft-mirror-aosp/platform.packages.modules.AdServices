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

package com.android.adservices.data.measurement.migration;

import static com.android.adservices.data.measurement.MeasurementTables.INDEX_PREFIX;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.data.measurement.SqliteObjectMapper;
import com.android.adservices.service.measurement.Trigger;

import com.google.common.collect.ImmutableMap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** Migrates Measurement DB from user version 2 to 3. */
public class MeasurementDbMigratorV3 extends AbstractMeasurementDbMigrator {

    private static final String API_VERSION = "0.1";

    private static final String TRIGGER_BACKUP_TABLE_V3 =
            MeasurementTables.TriggerContract.TABLE + "_backup_v3";

    private static final String AGGREGATE_REPORT_BACKUP_TABLE_V3 =
            MeasurementTables.AggregateReport.TABLE + "_backup_v3";

    private static final String CREATE_TABLE_TRIGGER_BACKUP_V3 =
            "CREATE TABLE "
                    + TRIGGER_BACKUP_TABLE_V3
                    + " ("
                    + MeasurementTables.TriggerContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION
                    + " TEXT, "
                    + MeasurementTables.TriggerContract.AD_TECH_DOMAIN
                    + " TEXT, "
                    + MeasurementTables.TriggerContract.TRIGGER_TIME
                    + " INTEGER, "
                    + MeasurementTables.TriggerContract.EVENT_TRIGGERS
                    + " TEXT, "
                    + MeasurementTables.TriggerContract.STATUS
                    + " INTEGER, "
                    + MeasurementTables.TriggerContract.REGISTRANT
                    + " TEXT, "
                    + MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA
                    + " TEXT, "
                    + MeasurementTables.TriggerContract.AGGREGATE_VALUES
                    + " TEXT, "
                    + MeasurementTables.TriggerContract.FILTERS
                    + " TEXT "
                    + ")";

    private static final String CREATE_TABLE_AGGREGATE_REPORT_BACKUP_V3 =
            "CREATE TABLE "
                    + AGGREGATE_REPORT_BACKUP_TABLE_V3
                    + " ("
                    + MeasurementTables.AggregateReport.ID + " TEXT PRIMARY KEY NOT NULL, "
                    + MeasurementTables.AggregateReport.PUBLISHER + " TEXT, "
                    + MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION + " TEXT, "
                    + MeasurementTables.AggregateReport.SOURCE_REGISTRATION_TIME + " INTEGER, "
                    + MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME + " INTEGER, "
                    + MeasurementTables.AggregateReport.REPORTING_ORIGIN + " TEXT, "
                    + MeasurementTables.AggregateReport.DEBUG_CLEARTEXT_PAYLOAD + " TEXT, "
                    + MeasurementTables.SourceContract.STATUS + " INTEGER, "
                    + MeasurementTables.AggregateReport.API_VERSION + " TEXT "
                    + ")";

    private static final String AGGREGATE_REPORT_V3_COLUMNS =
            String.join(",", List.of(
                    MeasurementTables.AggregateReport.ID,
                    MeasurementTables.AggregateReport.PUBLISHER,
                    MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION,
                    MeasurementTables.AggregateReport.SOURCE_REGISTRATION_TIME,
                    MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME,
                    MeasurementTables.AggregateReport.REPORTING_ORIGIN,
                    MeasurementTables.AggregateReport.DEBUG_CLEARTEXT_PAYLOAD,
                    MeasurementTables.SourceContract.STATUS));

    private static final String COPY_AGGREGATE_REPORT_DATA =
            String.format("INSERT INTO %1$s(%2$s) SELECT %2$s FROM %3$s;",
                    AGGREGATE_REPORT_BACKUP_TABLE_V3, AGGREGATE_REPORT_V3_COLUMNS,
                    MeasurementTables.AggregateReport.TABLE);

    private static final String[] DROP_TABLES_V3 = {
        "DROP TABLE IF EXISTS " + MeasurementTables.TriggerContract.TABLE,
        "DROP TABLE IF EXISTS " + MeasurementTables.AggregateReport.TABLE
    };

    private static final String[] ALTER_TABLES_V3 = {
        String.format(
                "ALTER TABLE %1$s RENAME TO %2$s",
                TRIGGER_BACKUP_TABLE_V3, MeasurementTables.TriggerContract.TABLE),
        String.format(
                "ALTER TABLE %1$s RENAME TO %2$s",
                AGGREGATE_REPORT_BACKUP_TABLE_V3, MeasurementTables.AggregateReport.TABLE),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.AGGREGATE_CONTRIBUTIONS)
    };

    private static final String[] CREATE_INDEXES_V3 = {
        // Reinstate the index created in v2, since that table is dropped
        "CREATE INDEX "
                + INDEX_PREFIX
                + MeasurementTables.TriggerContract.TABLE
                + "_ad_rt_tt "
                + "ON "
                + MeasurementTables.TriggerContract.TABLE
                + "( "
                + MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION
                + ", "
                + MeasurementTables.TriggerContract.AD_TECH_DOMAIN
                + ", "
                + MeasurementTables.TriggerContract.TRIGGER_TIME
                + " ASC)",
        "CREATE INDEX "
                + INDEX_PREFIX
                + MeasurementTables.TriggerContract.TABLE
                + "_tt "
                + "ON "
                + MeasurementTables.TriggerContract.TABLE
                + "("
                + MeasurementTables.TriggerContract.TRIGGER_TIME
                + ")"
    };

    private static final String[] MIGRATE_API_VERSION = {
        String.format("UPDATE %1$s SET %2$s = '%3$s'", MeasurementTables.AggregateReport.TABLE,
                MeasurementTables.AggregateReport.API_VERSION, API_VERSION)
    };

    public MeasurementDbMigratorV3() {
        super(3);
    }

    /**
     * Consolidated migration scripts that will execute on the following tables:
     *
     * <p>Trigger
     *
     * <ul>
     *   <li>Add : filters
     * </ul>
     *
     * Aggregate Encryption Key
     *
     * <ul>
     *   <li>Create table
     *   <li>Create index on expiry
     * </ul>
     *
     * @return consolidated scripts to migrate to version 3
     */
    private static String[] migrationScriptV3PreDataTransfer() {
        return Stream.of(
                        new String[] {CREATE_TABLE_TRIGGER_BACKUP_V3},
                        new String[] {CREATE_TABLE_AGGREGATE_REPORT_BACKUP_V3},
                        new String[] {MeasurementTables.CREATE_TABLE_AGGREGATE_ENCRYPTION_KEY})
                .flatMap(Arrays::stream)
                .toArray(String[]::new);
    }

    private static String[] migrationScriptV3PostDataTransfer() {
        return Stream.of(DROP_TABLES_V3, ALTER_TABLES_V3, CREATE_INDEXES_V3, MIGRATE_API_VERSION)
                .flatMap(Arrays::stream)
                .toArray(String[]::new);
    }

    @Override
    protected void performMigration(SQLiteDatabase db) {
        for (String sql : migrationScriptV3PreDataTransfer()) {
            db.execSQL(sql);
        }

        // transfer data to new table
        db.execSQL(COPY_AGGREGATE_REPORT_DATA);
        migrateData(db);

        for (String sql : migrationScriptV3PostDataTransfer()) {
            db.execSQL(sql);
        }
    }

    private void migrateData(SQLiteDatabase sqLiteDatabase) {
        List<Pair<Trigger, TriggerV2Extension>> v2Triggers = new ArrayList<>();
        try (Cursor cursor =
                sqLiteDatabase.query(
                        MeasurementTables.TriggerContract.TABLE,
                        /*columns=*/ null,
                        /*selection=*/ null,
                        /*selectionArgs=*/ null,
                        /*groupBy=*/ null,
                        /*having=*/ null,
                        /*orderBy=*/ null,
                        /*limit=*/ null)) {
            while (cursor.moveToNext()) {
                Pair<Trigger, TriggerV2Extension> triggerAndExtension =
                        new Pair<>(
                                SqliteObjectMapper.constructTriggerFromCursor(cursor),
                                buildTriggerExtensionFromCursor(cursor));
                v2Triggers.add(triggerAndExtension);
            }
        }

        v2Triggers.forEach(
                (v2TriggerAndExtension) -> {
                    Trigger v2Trigger = v2TriggerAndExtension.first;
                    TriggerV2Extension v2TriggerExtension = v2TriggerAndExtension.second;
                    ContentValues values = new ContentValues();
                    values.put(MeasurementTables.TriggerContract.ID, v2Trigger.getId());
                    values.put(
                            MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION,
                            v2Trigger.getAttributionDestination().toString());
                    values.put(
                            MeasurementTables.TriggerContract.TRIGGER_TIME,
                            v2Trigger.getTriggerTime());
                    values.put(
                            MeasurementTables.TriggerContract.EVENT_TRIGGERS,
                            v2TriggerExtension.serializeEventTriggers());
                    values.put(MeasurementTables.TriggerContract.STATUS, v2Trigger.getStatus());
                    values.put(
                            MeasurementTables.TriggerContract.AD_TECH_DOMAIN,
                            v2Trigger.getAdTechDomain().toString());
                    values.put(
                            MeasurementTables.TriggerContract.REGISTRANT,
                            v2Trigger.getRegistrant().toString());
                    values.put(
                            MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA,
                            v2Trigger.getAggregateTriggerData());
                    values.put(
                            MeasurementTables.TriggerContract.AGGREGATE_VALUES,
                            v2Trigger.getAggregateValues());
                    values.put(MeasurementTables.TriggerContract.FILTERS, v2Trigger.getFilters());

                    sqLiteDatabase.insert(
                            TRIGGER_BACKUP_TABLE_V3, /*nullColumnHack=*/ null, values);
                });
    }

    private TriggerV2Extension buildTriggerExtensionFromCursor(Cursor cursor) {
        return new TriggerV2Extension(
                getLongColumnValueFromCursor(
                                cursor, MeasurementTables.TriggerContract.DEPRECATED_DEDUP_KEY)
                        .orElse(null),
                getLongColumnValueFromCursor(
                                cursor,
                                MeasurementTables.TriggerContract.DEPRECATED_EVENT_TRIGGER_DATA)
                        .orElse(0L),
                getLongColumnValueFromCursor(
                                cursor, MeasurementTables.TriggerContract.DEPRECATED_PRIORITY)
                        .orElse(0L));
    }

    private Optional<Long> getLongColumnValueFromCursor(Cursor cursor, String columnName) {
        int columnIndex = cursor.getColumnIndex(columnName);
        if (columnIndex != -1 && !cursor.isNull(columnIndex)) {
            return Optional.of(cursor.getLong(columnIndex));
        }

        return Optional.empty();
    }

    static class TriggerV2Extension {
        private final Long mDedupKey;
        private final long mEventTriggerData;
        private final long mPriority;

        TriggerV2Extension(Long dedupKey, long eventTriggerData, long priority) {
            mDedupKey = dedupKey;
            mEventTriggerData = eventTriggerData;
            mPriority = priority;
        }

        Long getDedupKey() {
            return mDedupKey;
        }

        Long getEventTriggerData() {
            return mEventTriggerData;
        }

        Long getPriority() {
            return mPriority;
        }

        /**
         * To be used for V2 -> V3 DB migration purpose. Prior to V3, trigger data, de-dup key and
         * priority existed as separate columns in the DB. They are combined to form a single blob
         * starting from DB version 3.
         *
         * @return serialized event triggers string
         */
        String serializeEventTriggers() {
            Map<String, Long> eventTriggersDataV2 =
                    ImmutableMap.of(
                            Trigger.EventTriggerContract.DEDUPLICATION_KEY, mDedupKey,
                            Trigger.EventTriggerContract.TRIGGER_DATA, mEventTriggerData,
                            Trigger.EventTriggerContract.PRIORITY, mPriority);

            JSONObject eventTrigger = new JSONObject(eventTriggersDataV2);
            JSONArray eventTriggers = new JSONArray();
            eventTriggers.put(eventTrigger);

            return eventTriggers.toString();
        }
    }
}
