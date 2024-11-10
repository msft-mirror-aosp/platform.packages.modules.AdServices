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
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.data.measurement.MeasurementTables.SourceAttributionScopeContract;
import com.android.adservices.data.measurement.MeasurementTables.SourceContract;
import com.android.adservices.data.measurement.MeasurementTables.SourceDestination;

/**
 * Migrates Measurement DB to version 34. This upgrade adds the {@link
 * MeasurementTables.SourceContract#ATTRIBUTION_SCOPE_LIMIT} and {@link
 * MeasurementTables.SourceContract#MAX_EVENT_STATES} columns to the {@value
 * MeasurementTables.SourceContract#TABLE} table, and creates {@value
 * MeasurementTables.SourceAttributionScopeContract#TABLE} table with {@link
 * MeasurementTables.SourceAttributionScopeContract#SOURCE_ID} and {@link
 * MeasurementTables.SourceAttributionScopeContract#ATTRIBUTION_SCOPE} columns.
 */
public class MeasurementDbMigratorV34 extends AbstractMeasurementDbMigrator {
    public static final String CREATE_TABLE_SOURCE_ATTRIBUTION_SCOPE_V34 =
            "CREATE TABLE "
                    + SourceAttributionScopeContract.TABLE
                    + " ("
                    + SourceAttributionScopeContract.SOURCE_ID
                    + " TEXT, "
                    + SourceAttributionScopeContract.ATTRIBUTION_SCOPE
                    + " TEXT, "
                    + "FOREIGN KEY ("
                    + SourceDestination.SOURCE_ID
                    + ") REFERENCES "
                    + SourceContract.TABLE
                    + "("
                    + SourceContract.ID
                    + ") ON DELETE CASCADE "
                    + ")";
    private static final String[] CREATE_INDEXES = {
        "CREATE INDEX "
                + "idx_"
                + MeasurementTables.SourceAttributionScopeContract.TABLE
                + "_a"
                + " ON "
                + MeasurementTables.SourceAttributionScopeContract.TABLE
                + "("
                + MeasurementTables.SourceAttributionScopeContract.ATTRIBUTION_SCOPE
                + ")",
        "CREATE INDEX "
                + "idx_"
                + MeasurementTables.SourceAttributionScopeContract.TABLE
                + "_s"
                + " ON "
                + MeasurementTables.SourceAttributionScopeContract.TABLE
                + "("
                + MeasurementTables.SourceAttributionScopeContract.SOURCE_ID
                + ")",
        "CREATE INDEX "
                + "idx_"
                + MeasurementTables.SourceContract.TABLE
                + "_asl "
                + "ON "
                + MeasurementTables.SourceContract.TABLE
                + "("
                + MeasurementTables.SourceContract.ATTRIBUTION_SCOPE_LIMIT
                + ")",
        "CREATE INDEX "
                + "idx_"
                + MeasurementTables.SourceContract.TABLE
                + "_mes "
                + "ON "
                + MeasurementTables.SourceContract.TABLE
                + "("
                + MeasurementTables.SourceContract.MAX_EVENT_STATES
                + ")",
    };

    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        MigrationHelpers.addIntColumnsIfAbsent(
                db,
                MeasurementTables.SourceContract.TABLE,
                new String[] {
                    MeasurementTables.SourceContract.ATTRIBUTION_SCOPE_LIMIT,
                    MeasurementTables.SourceContract.MAX_EVENT_STATES
                });
        MigrationHelpers.addTextColumnIfAbsent(
                db,
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.ATTRIBUTION_SCOPES);

        db.execSQL(CREATE_TABLE_SOURCE_ATTRIBUTION_SCOPE_V34);

        for (String statement : CREATE_INDEXES) {
            db.execSQL(statement);
        }
    }

    public MeasurementDbMigratorV34() {
        super(34);
    }
}
