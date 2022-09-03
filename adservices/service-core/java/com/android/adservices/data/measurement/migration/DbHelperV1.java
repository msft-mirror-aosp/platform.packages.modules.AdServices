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

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.enrollment.EnrollmentTables;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.data.topics.TopicsTables;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Snapshot of DBHelper at Version 1 */
class DbHelperV1 extends DbHelper {

    /**
     * @param context the context
     * @param dbName Name of database to query
     * @param dbVersion db version
     */
    DbHelperV1(Context context, String dbName, int dbVersion) {
        super(context, dbName, dbVersion);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        for (String sql : TopicsTables.CREATE_STATEMENTS) {
            db.execSQL(sql);
        }
        for (String sql : MEASUREMENT_CREATE_STATEMENTS_V1) {
            db.execSQL(sql);
        }
        for (String sql : MeasurementTables.CREATE_INDEXES) {
            db.execSQL(sql);
        }
        for (String sql : EnrollmentTables.CREATE_STATEMENTS) {
            db.execSQL(sql);
        }
    }

    // Snapshot of Measurement Table Create Statements at Version 1
    private static final List<String> MEASUREMENT_CREATE_STATEMENTS_V1 =
            Collections.unmodifiableList(
                    Arrays.asList(
                            MeasurementTables.CREATE_TABLE_SOURCE_V1,
                            MeasurementTables.CREATE_TABLE_TRIGGER_V1,
                            MeasurementTables.CREATE_TABLE_EVENT_REPORT_V1,
                            MeasurementTables.CREATE_TABLE_ATTRIBUTION_V1,
                            MeasurementTables.CREATE_TABLE_AGGREGATE_REPORT_V1,
                            MeasurementTables.CREATE_TABLE_AGGREGATE_ENCRYPTION_KEY_V1,
                            MeasurementTables.CREATE_TABLE_ASYNC_REGISTRATION_V1));
}
