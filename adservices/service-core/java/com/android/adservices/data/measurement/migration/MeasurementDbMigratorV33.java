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

/**
 * Migrates Measurement DB to version 33. This upgrade adds the {@link
 * MeasurementTables.TriggerContract#TRIGGER_CONTEXT_ID} column to the {@value
 * MeasurementTables.TriggerContract#TABLE} table, and the {@link
 * MeasurementTables.AggregateReport#TRIGGER_CONTEXT_ID} column to the {@value
 * MeasurementTables.AggregateReport#TABLE} table.
 */
public class MeasurementDbMigratorV33 extends AbstractMeasurementDbMigrator {
    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        addTriggerContextIdColumn(db);
    }

    private void addTriggerContextIdColumn(@NonNull SQLiteDatabase db) {
        MigrationHelpers.addTextColumnIfAbsent(
                db,
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.TRIGGER_CONTEXT_ID);

        MigrationHelpers.addTextColumnIfAbsent(
                db,
                MeasurementTables.AggregateReport.TABLE,
                MeasurementTables.AggregateReport.TRIGGER_CONTEXT_ID);
    }

    public MeasurementDbMigratorV33() {
        super(33);
    }
}
