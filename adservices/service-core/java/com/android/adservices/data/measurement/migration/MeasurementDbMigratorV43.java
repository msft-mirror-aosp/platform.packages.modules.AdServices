/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.data.measurement.MeasurementTables.AggregatableDebugReportBudgetTrackerContract;
import com.android.adservices.data.measurement.MeasurementTables.AggregateReport;
import com.android.adservices.data.measurement.MeasurementTables.SourceContract;
import com.android.adservices.data.measurement.MeasurementTables.TriggerContract;

/**
 * Migrates Measurement DB to version 43. This upgrade adds the {@link
 * MeasurementTables.SourceContract#AGGREGATE_DEBUG_REPORTING} and {@link
 * MeasurementTables.SourceContract#AGGREGATE_DEBUG_REPORT_CONTRIBUTIONS} columns to the {@value
 * MeasurementTables.SourceContract#TABLE} table, {@link
 * MeasurementTables.TriggerContract#AGGREGATE_DEBUG_REPORTING} column to the {@value
 * MeasurementTables.TriggerContract#TABLE} table, {@link MeasurementTables.AggregateReport#API}
 * column to the {@value MeasurementTables.AggregateReport#TABLE} table, and creates {@value
 * MeasurementTables.AggregatableDebugReportBudgetTrackerContract#TABLE} table with {@link
 * MeasurementTables.AggregatableDebugReportBudgetTrackerContract#REPORT_GENERATION_TIME}, {@link
 * MeasurementTables.AggregatableDebugReportBudgetTrackerContract#TOP_LEVEL_REGISTRANT}, {@link
 * MeasurementTables.AggregatableDebugReportBudgetTrackerContract#REGISTRANT_APP}, {@link
 * MeasurementTables.AggregatableDebugReportBudgetTrackerContract#REGISTRATION_ORIGIN}, {@link
 * MeasurementTables.AggregatableDebugReportBudgetTrackerContract#SOURCE_ID}, {@link
 * MeasurementTables.AggregatableDebugReportBudgetTrackerContract#TRIGGER_ID} and {@link
 * MeasurementTables.AggregatableDebugReportBudgetTrackerContract#CONTRIBUTIONS} columns.
 */
public class MeasurementDbMigratorV43 extends AbstractMeasurementDbMigrator {
    public static final String CREATE_TABLE_AGGREGATABLE_DEBUG_REPORT_BUDGET_TRACKER_V43 =
            "CREATE TABLE IF NOT EXISTS "
                    + AggregatableDebugReportBudgetTrackerContract.TABLE
                    + " ("
                    + AggregatableDebugReportBudgetTrackerContract.REPORT_GENERATION_TIME
                    + " INTEGER, "
                    + AggregatableDebugReportBudgetTrackerContract.TOP_LEVEL_REGISTRANT
                    + " TEXT, "
                    + AggregatableDebugReportBudgetTrackerContract.REGISTRANT_APP
                    + " TEXT, "
                    + AggregatableDebugReportBudgetTrackerContract.REGISTRATION_ORIGIN
                    + " TEXT, "
                    + AggregatableDebugReportBudgetTrackerContract.SOURCE_ID
                    + " TEXT, "
                    + AggregatableDebugReportBudgetTrackerContract.TRIGGER_ID
                    + " TEXT, "
                    + AggregatableDebugReportBudgetTrackerContract.CONTRIBUTIONS
                    + " INTEGER, "
                    + "FOREIGN KEY ("
                    + AggregatableDebugReportBudgetTrackerContract.SOURCE_ID
                    + ") REFERENCES "
                    + SourceContract.TABLE
                    + "("
                    + SourceContract.ID
                    + ") ON DELETE CASCADE "
                    + "FOREIGN KEY ("
                    + AggregatableDebugReportBudgetTrackerContract.TRIGGER_ID
                    + ") REFERENCES "
                    + TriggerContract.TABLE
                    + "("
                    + TriggerContract.ID
                    + ") ON DELETE CASCADE"
                    + ")";

    private static final String AGGREGATE_REPORT_ATTRIBUTION_API = "attribution-reporting";

    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        // migrate existing tables (msmt_source, msmt_trigger, msmt_aggregate_report)
        addAggregateDebugReportRelatedColumns(db);

        // backfill msmt_aggregate_report.api column
        updateApiForAllAggregateReportRecords(db);

        // create new aggregatable debug report budget tracker table
        db.execSQL(CREATE_TABLE_AGGREGATABLE_DEBUG_REPORT_BUDGET_TRACKER_V43);
    }

    public MeasurementDbMigratorV43() {
        super(43);
    }

    private void addAggregateDebugReportRelatedColumns(@NonNull SQLiteDatabase db) {
        // update source table
        MigrationHelpers.addTextColumnIfAbsent(
                db,
                MeasurementTables.SourceContract.TABLE,
                SourceContract.AGGREGATE_DEBUG_REPORTING);
        MigrationHelpers.addIntColumnsIfAbsent(
                db,
                MeasurementTables.SourceContract.TABLE,
                new String[] {SourceContract.AGGREGATE_DEBUG_REPORT_CONTRIBUTIONS});

        // update trigger table
        MigrationHelpers.addTextColumnIfAbsent(
                db,
                MeasurementTables.TriggerContract.TABLE,
                TriggerContract.AGGREGATE_DEBUG_REPORTING);

        // update aggregate report table
        MigrationHelpers.addTextColumnIfAbsent(
                db, MeasurementTables.AggregateReport.TABLE, AggregateReport.API);
    }

    private void updateApiForAllAggregateReportRecords(@NonNull SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AggregateReport.API, AGGREGATE_REPORT_ATTRIBUTION_API);
        long rows = db.update(MeasurementTables.AggregateReport.TABLE, values, null, new String[0]);
        MigrationHelpers.logUpdateQuery(
                MeasurementTables.AggregateReport.API,
                rows,
                MeasurementTables.AggregateReport.TABLE);
    }
}
