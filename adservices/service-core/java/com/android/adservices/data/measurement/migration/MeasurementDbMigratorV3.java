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
                MeasurementTables.AsyncRegistrationContract.INPUT_EVENT,
                MeasurementTables.AsyncRegistrationContract.SOURCE_TYPE)
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
