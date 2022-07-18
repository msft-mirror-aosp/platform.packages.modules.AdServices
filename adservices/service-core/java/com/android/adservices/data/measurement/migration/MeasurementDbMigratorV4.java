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

/** Migrates Measurement DB from user version 3 to 4. */
public class MeasurementDbMigratorV4 extends AbstractMeasurementDbMigrator {

    private static final int MIGRATION_TARGET_VERSION = 4;
    private static final String[] ALTER_TABLES_V4 = {
        String.format(
                "ALTER TABLE %1$s ADD %2$s TEXT",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.WEB_DESTINATION),
        String.format(
                "ALTER TABLE %1$s RENAME COLUMN %2$s TO %3$s",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.DEPRECATED_ATTRIBUTION_DESTINATION,
                MeasurementTables.SourceContract.APP_DESTINATION)
    };

    public MeasurementDbMigratorV4() {
        super(MIGRATION_TARGET_VERSION);
    }

    @Override
    protected void performMigration(SQLiteDatabase db) {
        for (String sql : ALTER_TABLES_V4) {
            db.execSQL(sql);
        }
    }
}
