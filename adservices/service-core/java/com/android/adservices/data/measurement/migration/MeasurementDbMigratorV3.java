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

import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementTables;

/** Migrates Measurement DB from user version 2 to 3. */
public class MeasurementDbMigratorV3 extends AbstractMeasurementDbMigrator {
    private static final String EVENT_REPORT_CONTRACT_BACKUP =
            MeasurementTables.EventReportContract.TABLE + "_backup";
    private static final String AGGREGATE_REPORT_CONTRACT_BACKUP =
            MeasurementTables.AggregateReport.TABLE + "_backup";
    private static final String ATTRIBUTION_REPORT_CONTRACT_BACKUP =
            MeasurementTables.AttributionContract.TABLE + "_backup";

    private static final String[] ALTER_STATEMENTS_VER_3 = {
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.AsyncRegistrationContract.TABLE,
                MeasurementTables.AsyncRegistrationContract.DEBUG_KEY_ALLOWED),
        String.format(
                "ALTER TABLE %1$s ADD %2$s TEXT",
                MeasurementTables.AsyncRegistrationContract.TABLE,
                MeasurementTables.AsyncRegistrationContract.ENROLLMENT_ID),
        String.format(
                "ALTER TABLE %1$s " + "RENAME COLUMN %2$s TO %3$s",
                MeasurementTables.AsyncRegistrationContract.TABLE,
                MeasurementTablesDeprecated.AsyncRegistration.INPUT_EVENT,
                MeasurementTables.AsyncRegistrationContract.SOURCE_TYPE),
        String.format(
                "ALTER TABLE %1$s " + "RENAME COLUMN %2$s TO %3$s",
                MeasurementTables.AsyncRegistrationContract.TABLE,
                MeasurementTablesDeprecated.AsyncRegistration.REDIRECT,
                MeasurementTables.AsyncRegistrationContract.REDIRECT_TYPE),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.AsyncRegistrationContract.TABLE,
                MeasurementTables.AsyncRegistrationContract.REDIRECT_COUNT),
        String.format(
                "ALTER TABLE %1$s RENAME COLUMN %2$s TO %3$s",
                MeasurementTables.EventReportContract.TABLE,
                MeasurementTables.EventReportContract.SOURCE_ID,
                MeasurementTables.EventReportContract.SOURCE_EVENT_ID),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.EventReportContract.TABLE,
                MeasurementTables.EventReportContract.SOURCE_ID),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.EventReportContract.TABLE,
                MeasurementTables.EventReportContract.TRIGGER_ID),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.AggregateReport.TABLE,
                MeasurementTables.AggregateReport.SOURCE_ID),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.AggregateReport.TABLE,
                MeasurementTables.AggregateReport.TRIGGER_ID),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.AttributionContract.TABLE,
                MeasurementTables.AttributionContract.SOURCE_ID),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.AttributionContract.TABLE,
                MeasurementTables.AttributionContract.TRIGGER_ID),

        // SQLite does not support ALTER TABLE statement with foreign keys
        String.format(
                "ALTER TABLE %1$s RENAME TO %2$s",
                MeasurementTables.EventReportContract.TABLE, EVENT_REPORT_CONTRACT_BACKUP),
        MeasurementTables.CREATE_TABLE_EVENT_REPORT_V3,
        String.format(
                "INSERT INTO %1$s SELECT * FROM %2$s",
                MeasurementTables.EventReportContract.TABLE, EVENT_REPORT_CONTRACT_BACKUP),
        String.format("DROP TABLE %1$s", EVENT_REPORT_CONTRACT_BACKUP),
        String.format(
                "ALTER TABLE %1$s RENAME TO %2$s",
                MeasurementTables.AggregateReport.TABLE, AGGREGATE_REPORT_CONTRACT_BACKUP),
        MeasurementTables.CREATE_TABLE_AGGREGATE_REPORT_V3,
        String.format(
                "INSERT INTO %1$s SELECT * FROM %2$s",
                MeasurementTables.AggregateReport.TABLE, AGGREGATE_REPORT_CONTRACT_BACKUP),
        String.format("DROP TABLE %1$s", AGGREGATE_REPORT_CONTRACT_BACKUP),
        String.format(
                "ALTER TABLE %1$s RENAME TO %2$s",
                MeasurementTables.AttributionContract.TABLE, ATTRIBUTION_REPORT_CONTRACT_BACKUP),
        MeasurementTables.CREATE_TABLE_ATTRIBUTION_V3,
        String.format(
                "INSERT INTO %1$s SELECT * FROM %2$s",
                MeasurementTables.AttributionContract.TABLE, ATTRIBUTION_REPORT_CONTRACT_BACKUP),
        String.format("DROP TABLE %1$s", ATTRIBUTION_REPORT_CONTRACT_BACKUP),
    };

    public MeasurementDbMigratorV3() {
        super(3);
    }

    @Override
    protected void performMigration(SQLiteDatabase db) {
        for (String sql : ALTER_STATEMENTS_VER_3) {
            db.execSQL(sql);
        }
    }
}
