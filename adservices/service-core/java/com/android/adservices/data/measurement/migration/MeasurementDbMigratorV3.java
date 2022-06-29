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

import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.service.measurement.Trigger;

import java.util.Arrays;
import java.util.List;
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

    private static final String TRIGGER_V3_COLUMNS =
            String.join(",", List.of(
                    MeasurementTables.TriggerContract.ID,
                    MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION,
                    MeasurementTables.TriggerContract.AD_TECH_DOMAIN,
                    MeasurementTables.TriggerContract.TRIGGER_TIME,
                    MeasurementTables.TriggerContract.EVENT_TRIGGERS,
                    MeasurementTables.TriggerContract.STATUS,
                    MeasurementTables.TriggerContract.REGISTRANT,
                    MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA,
                    MeasurementTables.TriggerContract.AGGREGATE_VALUES));

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

    private static final String TRIGGER_MIGRATION_SELECT_STATEMENT =
            String.format("%1$s, %2$s, %3$s, %4$s, (%5$s), %6$s, %7$s, %8$s, %9$s",
                    MeasurementTables.TriggerContract.ID,
                    MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION,
                    MeasurementTables.TriggerContract.AD_TECH_DOMAIN,
                    MeasurementTables.TriggerContract.TRIGGER_TIME,
                    getJSONString(
                            Trigger.EventTriggerContract.DEDUPLICATION_KEY,
                            MeasurementTables.TriggerContract.DEPRECATED_DEDUP_KEY,
                            Trigger.EventTriggerContract.TRIGGER_DATA,
                            MeasurementTables.TriggerContract.DEPRECATED_EVENT_TRIGGER_DATA,
                            Trigger.EventTriggerContract.PRIORITY,
                            MeasurementTables.TriggerContract.DEPRECATED_PRIORITY),
                    MeasurementTables.TriggerContract.STATUS,
                    MeasurementTables.TriggerContract.REGISTRANT,
                    MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA,
                    MeasurementTables.TriggerContract.AGGREGATE_VALUES);

    private static final String MIGRATE_TRIGGER_DATA =
            String.format("INSERT INTO %s(%s) SELECT %s FROM %s;",
                    TRIGGER_BACKUP_TABLE_V3, TRIGGER_V3_COLUMNS, TRIGGER_MIGRATION_SELECT_STATEMENT,
                    MeasurementTables.TriggerContract.TABLE);

    private static final String MIGRATE_AGGREGATE_REPORT_DATA =
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

        // Transfer data to the new tables
        db.execSQL(MIGRATE_TRIGGER_DATA);
        db.execSQL(MIGRATE_AGGREGATE_REPORT_DATA);

        for (String sql : migrationScriptV3PostDataTransfer()) {
            db.execSQL(sql);
        }
    }

    private static String getJSONString(String... strs) {
        StringBuilder result = new StringBuilder("'[{'");
        for (int i = 0; i < strs.length; i += 2) {
            result.append(String.format(" || '\"%s\":' || ifnull(%s, 'null')",
                    strs[i], strs[i + 1]));
            if (i < strs.length - 2) {
                result.append(" || ','");
            }
        }
        result.append(" || '}]'");
        return result.toString();
    }
}
