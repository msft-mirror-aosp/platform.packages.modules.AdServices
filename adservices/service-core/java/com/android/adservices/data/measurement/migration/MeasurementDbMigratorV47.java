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

import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import com.android.adservices.data.measurement.MeasurementTables;

import java.util.Objects;

public class MeasurementDbMigratorV47 extends AbstractMeasurementDbMigrator {

    public static final String CREATE_TABLE_COUNT_UNIQUE_METADATA_V47 =
            "CREATE TABLE "
                    + MeasurementTables.CountUniqueMetadataContract.TABLE
                    + " ("
                    + MeasurementTables.CountUniqueMetadataContract.REPORTING_ORIGIN
                    + " TEXT, "
                    + MeasurementTables.CountUniqueMetadataContract.KEY
                    + " TEXT, "
                    + MeasurementTables.CountUniqueMetadataContract.VALUE
                    + " INTEGER, "
                    + MeasurementTables.CountUniqueMetadataContract.EXPIRATION_TIME
                    + " INTEGER, "
                    + "PRIMARY KEY ("
                    + MeasurementTables.CountUniqueMetadataContract.KEY
                    + ", "
                    + MeasurementTables.CountUniqueMetadataContract.REPORTING_ORIGIN
                    + " )"
                    + " )";

    public MeasurementDbMigratorV47() {
        super(47);
    }

    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        Objects.requireNonNull(db, "db cannot be null");
        db.execSQL(CREATE_TABLE_COUNT_UNIQUE_METADATA_V47);
    }
}
