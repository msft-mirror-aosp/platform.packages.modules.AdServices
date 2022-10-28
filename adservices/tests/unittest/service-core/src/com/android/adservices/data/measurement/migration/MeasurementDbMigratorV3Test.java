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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV3Test extends AbstractMeasurementDbMigratorTestBase {

    private static final String[][] INSERTED_EVENT_REPORT_DATA = {
        // id, destination, sourceId
        {"1", "https://example.com", "random one"},
        {"2", "http://will-be-trimmed.example.com", "random two"},
        {"3", "http://example.com/will-be-trimmed", "random three"},
        {"4", "android-app://com.android.app/will-be-trimmed", "random four"},
        {"5", "android-app://com.another.android.app", "random five"},
    };

    private static final String[][] MIGRATED_EVENT_REPORT_DATA = {
        // id, destination, sourceEventId
        {"1", "https://example.com", "random one"},
        {"2", "http://example.com", "random two"},
        {"3", "http://example.com", "random three"},
        {"4", "android-app://com.android.app", "random four"},
        {"5", "android-app://com.another.android.app", "random five"},
    };

    @Test
    public void performMigration_success_v2ToV3() {
        // Setup
        DbHelper dbHelper = getDbHelper(1);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Execution
        new MeasurementDbMigratorV2().performMigration(db, 1, 2);

        insertEventReports(db);

        new MeasurementDbMigratorV3().performMigration(db, 2, 3);
        // To mimic real onUpgrade behaviour. Without closing the db, changes don't reflect.
        db.close();

        // Verify
        db = dbHelper.getReadableDatabase();
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.AsyncRegistrationContract.TABLE, 16));
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.EventReportContract.TABLE, 16));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.AggregateReport.TABLE, 13));
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.AttributionContract.TABLE, 10));
        assertEventReportMigration(db);
    }

    @Override
    int getTargetVersion() {
        return 3;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV3();
    }

    private static void insertEventReports(SQLiteDatabase db) {
        for (int i = 0; i < INSERTED_EVENT_REPORT_DATA.length; i++) {
            insertEventReport(
                    db,
                    INSERTED_EVENT_REPORT_DATA[i][0],
                    INSERTED_EVENT_REPORT_DATA[i][1],
                    INSERTED_EVENT_REPORT_DATA[i][2]);
        }
    }

    private static void assertEventReportMigration(SQLiteDatabase db) {
        Cursor cursor = db.query(
                MeasurementTables.EventReportContract.TABLE,
                new String[] {
                    MeasurementTables.EventReportContract.ID,
                    MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION,
                    MeasurementTables.EventReportContract.SOURCE_EVENT_ID
                },
                null, null, null, null,
                /* orderBy */ MeasurementTables.EventReportContract.ID,
                null);
        while (cursor.moveToNext()) {
            assertEventReportMigrated(cursor);
        }
    }

    private static void insertEventReport(SQLiteDatabase db, String id, String destination,
            String sourceId) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.EventReportContract.ID, id);
        values.put(MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION, destination);
        values.put(MeasurementTables.EventReportContract.SOURCE_ID, sourceId);
        db.insert(MeasurementTables.EventReportContract.TABLE, null, values);
    }

    private static void assertEventReportMigrated(Cursor cursor) {
        int i = cursor.getPosition();
        assertEquals(MIGRATED_EVENT_REPORT_DATA[i][0], cursor.getString(cursor.getColumnIndex(
                MeasurementTables.EventReportContract.ID)));
        assertEquals(MIGRATED_EVENT_REPORT_DATA[i][1], cursor.getString(cursor.getColumnIndex(
                MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION)));
        assertEquals(MIGRATED_EVENT_REPORT_DATA[i][2], cursor.getString(cursor.getColumnIndex(
                MeasurementTables.EventReportContract.SOURCE_EVENT_ID)));
    }
}
