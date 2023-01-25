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
import static org.junit.Assert.assertNotNull;
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

    private static final String[][] INSERTED_ASYNC_REGISTRATION = {
        // id, enrollment_id
        {"1", "enrollment-id-1"}
    };

    private static final String[][] INSERTED_SOURCE = {
        // id, expiry
        {"1", "1673464509232"}
    };

    private static final String[][] INSERTED_TRIGGER = {
        // id, attribution_destination
        {"1", "android-app://com.android.app"}
    };

    private static final String[][] MIGRATED_ASYNC_REGISTRATION = {
        // id, enrollment_id
        {"1", "enrollment-id-1"}
    };

    private static final String[][] MIGRATED_SOURCE = {
        /* id, expiry, event_report_window, aggregatable_report_window, shared_aggregation_keys,
        install_time */
        {"1", "1673464509232", "1673464509232", "1673464509232", null, null}
    };

    private static final String[][] MIGRATED_TRIGGER = {
        // id, attribution_destination, attribution_config, adtech_bit_mapping
        {"1", "android-app://com.android.app", null, null}
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
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.AsyncRegistrationContract.TABLE, 17));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.SourceContract.TABLE, 25));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.TriggerContract.TABLE, 16));

        insertAsyncRegistrations(db);
        insertSources(db);
        insertTriggers(db);
        new MeasurementDbMigratorV6().performMigration(db, 3, 6);
        db.close();

        // Verify
        db = dbHelper.getReadableDatabase();
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.AsyncRegistrationContract.TABLE, 18));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.SourceContract.TABLE, 31));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.TriggerContract.TABLE, 18));
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.XnaIgnoredSourcesContract.TABLE, 2));
        assertAsyncRegistrationMigration(db);
        assertSourceMigration(db);
        assertTriggerMigration(db);

        db.close();
    }

    @Test
    public void performMigration_twiceToSameVersion() throws JSONException {
        // Setup
        DbHelper dbHelper = getDbHelper(1);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Execution
        new MeasurementDbMigratorV2().performMigration(db, 1, 2);
        new MeasurementDbMigratorV3().performMigration(db, 2, 3);

        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.AsyncRegistrationContract.TABLE, 17));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.SourceContract.TABLE, 25));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.TriggerContract.TABLE, 16));

        insertAsyncRegistrations(db);
        insertSources(db);
        insertTriggers(db);
        new MeasurementDbMigratorV6().performMigration(db, 3, 6);
        // Perform migration again.
        new MeasurementDbMigratorV6().performMigration(db, 6, 6);

        // To mimic real onUpgrade behaviour. Without closing the db, changes don't reflect.
        db.close();

        // Verify
        db = dbHelper.getReadableDatabase();
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.AsyncRegistrationContract.TABLE, 18));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.SourceContract.TABLE, 31));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.TriggerContract.TABLE, 18));
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.XnaIgnoredSourcesContract.TABLE, 2));
        assertAsyncRegistrationMigration(db);
        assertSourceMigration(db);
        assertTriggerMigration(db);

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

    private static void insertAsyncRegistrations(SQLiteDatabase db) {
        for (int i = 0; i < INSERTED_ASYNC_REGISTRATION.length; i++) {
            insertAsyncRegistration(
                    db, INSERTED_ASYNC_REGISTRATION[i][0], INSERTED_ASYNC_REGISTRATION[i][1]);
        }
    }

    private static void insertSources(SQLiteDatabase db) {
        for (int i = 0; i < INSERTED_SOURCE.length; i++) {
            insertSource(db, INSERTED_SOURCE[i][0], INSERTED_SOURCE[i][1]);
        }
    }

    private static void insertTriggers(SQLiteDatabase db) {
        for (int i = 0; i < INSERTED_TRIGGER.length; i++) {
            insertTrigger(db, INSERTED_TRIGGER[i][0], INSERTED_TRIGGER[i][1]);
        }
    }

    private static void insertAsyncRegistration(SQLiteDatabase db, String id, String enrollmentId) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AsyncRegistrationContract.ID, id);
        values.put(MeasurementTables.AsyncRegistrationContract.ENROLLMENT_ID, enrollmentId);
        db.insert(MeasurementTables.AsyncRegistrationContract.TABLE, null, values);
    }

    private static void insertSource(SQLiteDatabase db, String id, String expiry) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceContract.ID, id);
        values.put(MeasurementTables.SourceContract.EXPIRY_TIME, Long.valueOf(expiry));
        db.insert(MeasurementTables.SourceContract.TABLE, null, values);
    }

    private static void insertTrigger(SQLiteDatabase db, String id, String attributionDestination) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.TriggerContract.ID, id);
        values.put(
                MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION, attributionDestination);
        db.insert(MeasurementTables.TriggerContract.TABLE, null, values);
    }

    private static void assertAsyncRegistrationMigration(SQLiteDatabase db) {
        Cursor cursor =
                db.query(
                        MeasurementTables.AsyncRegistrationContract.TABLE,
                        new String[] {
                            MeasurementTables.AsyncRegistrationContract.ID,
                            MeasurementTables.AsyncRegistrationContract.ENROLLMENT_ID,
                            MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID
                        },
                        null,
                        null,
                        null,
                        null,
                        /* orderBy */ MeasurementTables.AsyncRegistrationContract.ID,
                        null);
        while (cursor.moveToNext()) {
            assertAsyncRegistrationMigrated(cursor);
        }
    }

    private static void assertSourceMigration(SQLiteDatabase db) {
        Cursor cursor =
                db.query(
                        MeasurementTables.SourceContract.TABLE,
                        new String[] {
                            MeasurementTables.SourceContract.ID,
                            MeasurementTables.SourceContract.EXPIRY_TIME,
                            MeasurementTables.SourceContract.EVENT_REPORT_WINDOW,
                            MeasurementTables.SourceContract.AGGREGATABLE_REPORT_WINDOW,
                            MeasurementTables.SourceContract.REGISTRATION_ID,
                            MeasurementTables.SourceContract.SHARED_AGGREGATION_KEYS,
                            MeasurementTables.SourceContract.INSTALL_TIME
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

    private static void assertTriggerMigration(SQLiteDatabase db) {
        Cursor cursor =
                db.query(
                        MeasurementTables.TriggerContract.TABLE,
                        new String[] {
                            MeasurementTables.TriggerContract.ID,
                            MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION,
                            MeasurementTables.TriggerContract.ATTRIBUTION_CONFIG,
                            MeasurementTables.TriggerContract.ADTECH_BIT_MAPPING
                        },
                        null,
                        null,
                        null,
                        null,
                        /* orderBy */ MeasurementTables.TriggerContract.ID,
                        null);
        while (cursor.moveToNext()) {
            assertTriggerMigrated(cursor);
        }
    }

    private static void assertAsyncRegistrationMigrated(Cursor cursor) {
        int i = cursor.getPosition();
        assertEquals(
                MIGRATED_ASYNC_REGISTRATION[i][0],
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.AsyncRegistrationContract.ID)));
        assertEquals(
                MIGRATED_ASYNC_REGISTRATION[i][1],
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTables.AsyncRegistrationContract.ENROLLMENT_ID)));
        assertNotNull(
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID)));
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
        assertEquals(
                MIGRATED_SOURCE[i][4],
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTables.SourceContract.SHARED_AGGREGATION_KEYS)));
        assertEquals(
                MIGRATED_SOURCE[i][5],
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.SourceContract.INSTALL_TIME)));
        assertNotNull(
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.SourceContract.REGISTRATION_ID)));
    }

    private static void assertTriggerMigrated(Cursor cursor) {
        int i = cursor.getPosition();
        assertEquals(
                MIGRATED_TRIGGER[i][0],
                cursor.getString(cursor.getColumnIndex(MeasurementTables.TriggerContract.ID)));
        assertEquals(
                MIGRATED_TRIGGER[i][1],
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION)));
        assertEquals(
                MIGRATED_TRIGGER[i][2],
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTables.TriggerContract.ATTRIBUTION_CONFIG)));
        assertEquals(
                MIGRATED_TRIGGER[i][3],
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTables.TriggerContract.ADTECH_BIT_MAPPING)));
    }
}
