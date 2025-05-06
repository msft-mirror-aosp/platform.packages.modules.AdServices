/*
 * Copyright (C) 2025 The Android Open Source Project
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

/** Migrates Measurement DB from user version 49 to 50. */
public class MeasurementDbMigratorV50 extends AbstractMeasurementDbMigrator {
    private static final Long NULL_LONG = null;

    private static final String UPDATE_KEY_VALUE_DATA_STATEMENT =
            String.format(
                    "UPDATE %1$s SET %2$s = NULL WHERE %2$s = '%3$s'",
                    MeasurementTables.KeyValueDataContract.TABLE,
                    MeasurementTables.KeyValueDataContract.VALUE,
                    String.valueOf(NULL_LONG));

    public MeasurementDbMigratorV50() {
        super(50);
    }

    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        migrateKeyValueData(db);
    }

    private static void migrateKeyValueData(SQLiteDatabase db) {
        db.execSQL(UPDATE_KEY_VALUE_DATA_STATEMENT);
    }
}
