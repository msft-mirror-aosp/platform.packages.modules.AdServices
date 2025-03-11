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

import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.data.measurement.MeasurementTables.SourceNamedBudgetContract;
import com.android.adservices.data.measurement.MeasurementTables.TriggerContract;

import java.util.Objects;

/**
 * Migrates Measurement DB to version 45. This upgrade adds the {@link
 * MeasurementTables.TriggerContract#NAMED_BUDGETS} column to the {@value
 * MeasurementTables.TriggerContract#TABLE} table, and creates {@value
 * MeasurementTables.SourceNamedBudgetContract#TABLE} table with {@link
 * MeasurementTables.SourceNamedBudgetContract#SOURCE_ID}, {@link
 * MeasurementTables.SourceNamedBudgetContract#NAME}, {@link
 * MeasurementTables.SourceNamedBudgetContract#BUDGET}, and {@link
 * MeasurementTables.SourceNamedBudgetContract#AGGREGATE_CONTRIBUTIONS} columns.
 */
public class MeasurementDbMigratorV45 extends AbstractMeasurementDbMigrator {
    public static final String CREATE_TABLE_SOURCE_NAMED_BUDGET_V45 =
            "CREATE TABLE "
                    + SourceNamedBudgetContract.TABLE
                    + " ("
                    + SourceNamedBudgetContract.SOURCE_ID
                    + " TEXT, "
                    + SourceNamedBudgetContract.NAME
                    + " TEXT, "
                    + SourceNamedBudgetContract.BUDGET
                    + " INTEGER, "
                    + SourceNamedBudgetContract.AGGREGATE_CONTRIBUTIONS
                    + " INTEGER, "
                    + "FOREIGN KEY ("
                    + SourceNamedBudgetContract.SOURCE_ID
                    + ") REFERENCES "
                    + MeasurementTables.SourceContract.TABLE
                    + "("
                    + MeasurementTables.SourceContract.ID
                    + ") ON DELETE CASCADE "
                    + ")";
    private static final String[] CREATE_INDEXES = {
        "CREATE INDEX "
                + "idx_"
                + SourceNamedBudgetContract.TABLE
                + "_s_n"
                + " ON "
                + SourceNamedBudgetContract.TABLE
                + "("
                + SourceNamedBudgetContract.SOURCE_ID
                + ", "
                + SourceNamedBudgetContract.NAME
                + ")",
        "CREATE INDEX "
                + "idx_"
                + SourceNamedBudgetContract.TABLE
                + "_s"
                + " ON "
                + SourceNamedBudgetContract.TABLE
                + "("
                + SourceNamedBudgetContract.SOURCE_ID
                + ")"
    };

    @Override
    protected void performMigration(SQLiteDatabase db) {
        Objects.requireNonNull(db, "db cannot be null");
        MigrationHelpers.addTextColumnIfAbsent(
                db, TriggerContract.TABLE, TriggerContract.NAMED_BUDGETS);

        db.execSQL(CREATE_TABLE_SOURCE_NAMED_BUDGET_V45);

        for (String statement : CREATE_INDEXES) {
            db.execSQL(statement);
        }
    }

    public MeasurementDbMigratorV45() {
        super(/* migrationTargetVersion= */ 45);
    }
}
