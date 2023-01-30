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

import static com.android.adservices.data.DbTestUtil.doesTableExistAndColumnCountMatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.measurement.MeasurementTables;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV6Test extends AbstractMeasurementDbMigratorTestBase {

    private static final String[][] INSERTED_SOURCE = {
        // id, expiry
        {"1", "1673464509232"}
    };

    private static final String[][] MIGRATED_SOURCE = {
        // id, expiry, event_report_window, aggregatable_report_window
        {"1", "1673464509232", "1673464509232", "1673464509232"}
    };

    @Test
    public void performMigration_success_v3ToV6() throws JSONException {
        // Setup
        DbHelper dbHelper = getDbHelper(1);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Execution
        new MeasurementDbMigratorV2().performMigration(db, 1, 2);
        new MeasurementDbMigratorV3().performMigration(db, 2, 3);
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.SourceContract.TABLE, 25));

        insertSources(db);
        new MeasurementDbMigratorV6().performMigration(db, 3, 6);
        db.close();

        // Verify
        db = dbHelper.getReadableDatabase();
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.SourceContract.TABLE, 28));
        assertSourceMigration(db);

        db.close();
    }

    @Override
    int getTargetVersion() {
        return 6;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV6();
    }

    private static void insertSources(SQLiteDatabase db) {
        for (int i = 0; i < INSERTED_SOURCE.length; i++) {
            insertSource(db, INSERTED_SOURCE[i][0], INSERTED_SOURCE[i][1]);
        }
    }

    private static void insertSource(SQLiteDatabase db, String id, String expiry) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceContract.ID, id);
        values.put(MeasurementTables.SourceContract.EXPIRY_TIME, Long.valueOf(expiry));
        db.insert(MeasurementTables.SourceContract.TABLE, null, values);
    }

    private static void assertSourceMigration(SQLiteDatabase db) {
        Cursor cursor =
                db.query(
                        MeasurementTables.SourceContract.TABLE,
                        new String[] {
                            MeasurementTables.SourceContract.ID,
                            MeasurementTables.SourceContract.EXPIRY_TIME,
                            MeasurementTables.SourceContract.EVENT_REPORT_WINDOW,
                            MeasurementTables.SourceContract.AGGREGATABLE_REPORT_WINDOW
                        },
                        null,
                        null,
                        null,
                        null,
                        /* orderBy */ MeasurementTables.SourceContract.ID,
                        null);
        while (cursor.moveToNext()) {
            assertSourceMigrated(cursor);
        }
    }

    private static void assertSourceMigrated(Cursor cursor) {
        int i = cursor.getPosition();
        assertEquals(
                MIGRATED_SOURCE[i][0],
                cursor.getString(cursor.getColumnIndex(MeasurementTables.SourceContract.ID)));
        assertEquals(
                MIGRATED_SOURCE[i][1],
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.SourceContract.EXPIRY_TIME)));
        assertEquals(
                MIGRATED_SOURCE[i][2],
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTables.SourceContract.EVENT_REPORT_WINDOW)));
        assertEquals(
                MIGRATED_SOURCE[i][3],
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTables.SourceContract.AGGREGATABLE_REPORT_WINDOW)));
    }
}
