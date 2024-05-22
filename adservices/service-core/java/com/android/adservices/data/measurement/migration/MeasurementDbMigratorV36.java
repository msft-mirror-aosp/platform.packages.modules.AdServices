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
 * Migrates Measurement DB to version 36. This upgrade adds the {@link
 * MeasurementTables.SourceContract#REINSTALL_REATTRIBUTION_WINDOW} column to the {@value
 * MeasurementTables.SourceContract#TABLE} table
 */
public class MeasurementDbMigratorV36 extends AbstractMeasurementDbMigrator {
    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        MigrationHelpers.addIntColumnsIfAbsent(
                db,
                MeasurementTables.SourceContract.TABLE,
                new String[] {MeasurementTables.SourceContract.REINSTALL_REATTRIBUTION_WINDOW});
    }

    public MeasurementDbMigratorV36() {
        super(36);
    }
}
