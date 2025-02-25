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

public class MeasurementDbMigratorV46 extends AbstractMeasurementDbMigrator {

    public static final String CREATE_TABLE_COUNT_UNIQUE_REPORTING_V46 =
            "CREATE TABLE "
                    + MeasurementTables.CountUniqueReportingContract.TABLE
                    + " ("
                    + MeasurementTables.CountUniqueReportingContract.REPORT_ID
                    + " TEXT, "
                    + MeasurementTables.CountUniqueReportingContract.PAYLOAD
                    + " TEXT, "
                    + MeasurementTables.CountUniqueReportingContract.REPORTING_ORIGIN
                    + " TEXT, "
                    + MeasurementTables.CountUniqueReportingContract.STATUS
                    + " INTEGER, "
                    + MeasurementTables.CountUniqueReportingContract.SCHEDULED_REPORT_TIME
                    + " INTEGER, "
                    + MeasurementTables.CountUniqueReportingContract.API_VERSION
                    + " TEXT, "
                    + MeasurementTables.CountUniqueReportingContract.DEBUG_KEY
                    + " TEXT, "
                    + MeasurementTables.CountUniqueReportingContract.CONTEXT_ID
                    + " TEXT, "
                    + "PRIMARY KEY("
                    + MeasurementTables.CountUniqueReportingContract.REPORT_ID
                    + "))";

    private static final String CREATE_INDEX =
            "CREATE INDEX "
                    + "idx_"
                    + MeasurementTables.CountUniqueReportingContract.TABLE
                    + "_c_u_r"
                    + " ON "
                    + MeasurementTables.CountUniqueReportingContract.TABLE
                    + "("
                    + MeasurementTables.CountUniqueReportingContract.REPORT_ID
                    + ", "
                    + MeasurementTables.CountUniqueReportingContract.REPORTING_ORIGIN
                    + ")";

    public MeasurementDbMigratorV46() {
        super(46);
    }

    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        Objects.requireNonNull(db, "db cannot be null");
        db.execSQL(CREATE_TABLE_COUNT_UNIQUE_REPORTING_V46);
        db.execSQL(CREATE_INDEX);
    }
}
