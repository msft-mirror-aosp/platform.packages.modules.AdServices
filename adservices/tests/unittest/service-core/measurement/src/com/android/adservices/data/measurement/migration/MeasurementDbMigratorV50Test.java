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

import static com.android.adservices.common.DbTestUtil.getDbHelperForTest;

import static org.junit.Assert.assertEquals;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementDbHelper;
import com.android.adservices.data.measurement.MeasurementTables;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV50Test extends MeasurementDbMigratorTestBase {
    private static final Long NULL_LONG = null;

    private static final String[][] INSERTED_KEY_VALUE_DATA = {
        // data_type, key, value
        {"AGGREGATE_REPORT_RETRY_COUNT", "AGGREGATE_REPORT_RETRY_COUNT", "12"},
        {"DEBUG_EVENT_REPORT_RETRY_COUNT", "DEBUG_EVENT_REPORT_RETRY_COUNT", null},
        {"JOB_NEXT_EXECUTION_TIME", "JOB_NEXT_EXECUTION_TIME", String.valueOf(NULL_LONG)}
    };

    private static final String[][] MIGRATED_KEY_VALUE_DATA = {
        // data_type, key, value
        {"AGGREGATE_REPORT_RETRY_COUNT", "AGGREGATE_REPORT_RETRY_COUNT", "12"},
        {"DEBUG_EVENT_REPORT_RETRY_COUNT", "DEBUG_EVENT_REPORT_RETRY_COUNT", null},
        {"JOB_NEXT_EXECUTION_TIME", "JOB_NEXT_EXECUTION_TIME", null}
    };

    @Test
    public void performMigration_v49ToV50_success() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        49,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Execution
        insertKeyValueData(db);
        getTestSubject().performMigration(db, 49, 50);

        // Verify
        assertKeyValueDataMigration(db);

        db.close();
    }

    @Override
    int getTargetVersion() {
        return 50;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV50();
    }

    private static void insertKeyValueData(SQLiteDatabase db) {
        for (int i = 0; i < INSERTED_KEY_VALUE_DATA.length; i++) {
            insertKeyValueData(
                    db,
                    INSERTED_KEY_VALUE_DATA[i][0],
                    INSERTED_KEY_VALUE_DATA[i][1],
                    INSERTED_KEY_VALUE_DATA[i][2]);
        }
    }

    private static void insertKeyValueData(
            SQLiteDatabase db, String dataType, String key, String value) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.KeyValueDataContract.DATA_TYPE, dataType);
        values.put(MeasurementTables.KeyValueDataContract.KEY, key);
        values.put(MeasurementTables.KeyValueDataContract.VALUE, value);
        db.insert(MeasurementTables.KeyValueDataContract.TABLE, null, values);
    }

    private static void assertKeyValueDataMigration(SQLiteDatabase db) {
        Cursor cursor =
                db.query(
                        MeasurementTables.KeyValueDataContract.TABLE,
                        new String[] {
                            MeasurementTables.KeyValueDataContract.DATA_TYPE,
                            MeasurementTables.KeyValueDataContract.KEY,
                            MeasurementTables.KeyValueDataContract.VALUE
                        },
                        null,
                        null,
                        null,
                        null,
                        /* orderBy */ MeasurementTables.KeyValueDataContract.DATA_TYPE,
                        null);
        int count = 0;
        while (cursor.moveToNext()) {
            assertKeyValueDataMigrated(cursor);
            count += 1;
        }
        assertEquals(INSERTED_KEY_VALUE_DATA.length, count);
    }

    private static void assertKeyValueDataMigrated(Cursor cursor) {
        int i = cursor.getPosition();
        assertEquals(
                MIGRATED_KEY_VALUE_DATA[i][0],
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.KeyValueDataContract.DATA_TYPE)));
        assertEquals(
                MIGRATED_KEY_VALUE_DATA[i][1],
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.KeyValueDataContract.KEY)));
        assertEquals(
                MIGRATED_KEY_VALUE_DATA[i][2],
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.KeyValueDataContract.VALUE)));
    }
}
