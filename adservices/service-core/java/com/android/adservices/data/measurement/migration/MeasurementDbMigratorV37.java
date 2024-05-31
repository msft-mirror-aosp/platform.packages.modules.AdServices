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

/** Migrates Measurement DB to version 37 */
public class MeasurementDbMigratorV37 extends AbstractMeasurementDbMigrator {
    private static final String[] CREATE_INDEXES = {
        "CREATE INDEX "
                + "idx_"
                + MeasurementTables.AppReportHistoryContract.TABLE
                + "_lrdt "
                + "ON "
                + MeasurementTables.AppReportHistoryContract.TABLE
                + "("
                + MeasurementTables.AppReportHistoryContract.LAST_REPORT_DELIVERED_TIME
                + ")",
        "CREATE INDEX "
                + "idx_"
                + MeasurementTables.AppReportHistoryContract.TABLE
                + "_ro_ad "
                + "ON "
                + MeasurementTables.AppReportHistoryContract.TABLE
                + "("
                + MeasurementTables.AppReportHistoryContract.REGISTRATION_ORIGIN
                + ", "
                + MeasurementTables.AppReportHistoryContract.APP_DESTINATION
                + ")",
    };

    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        db.execSQL(MeasurementTables.CREATE_TABLE_APP_REPORT_HISTORY_LATEST);
        for (String statement : CREATE_INDEXES) {
            db.execSQL(statement);
        }
    }

    public MeasurementDbMigratorV37() {
        super(37);
    }
}
