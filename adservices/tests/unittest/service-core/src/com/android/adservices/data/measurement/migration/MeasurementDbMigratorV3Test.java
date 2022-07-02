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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.measurement.MeasurementTables;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV3Test extends AbstractMeasurementDbMigratorTestBase {

    private interface TriggerV2 {
        String ID = UUID.randomUUID().toString();
        Long DEDUP_KEY = null;
        long TRIGGER_DATA = 1L;
        long PRIORITY = 345678L;
        int STATUS = 0;
        long TRIGGER_TIME = 8640000000L;
        Uri ATTRIBUTION_DESTINATION = Uri.parse("android-app://com.destination");
        Uri REGISTRANT = Uri.parse("android-app://com.registrant");
        Uri AD_TECH_DOMAIN = Uri.parse("https://com.example");
        String AGGREGATE_TRIGGER_DATA =
                "["
                    + "{"
                        + "\"key_piece\":\"0xA80\","
                        + "\"source_keys\":[\"geoValue\",\"noMatch\"]"
                    + "}"
                + "]";
        String AGGREGATE_VALUES =
                "{"
                    + "\"campaignCounts\":32768,"
                    + "\"geoValue\":1664"
                + "}";
    }

    private interface AggregateReportV2 {
        String ID = UUID.randomUUID().toString();
        String PRIVACY_BUDGET_KEY = "privacy_budget_key";
        Uri PUBLISHER = Uri.parse("android-app://com.registrant");
        Uri ATTRIBUTION_DESTINATION = Uri.parse("android-app://com.destination");
        long SOURCE_REGISTRATION_TIME = 8640000000L;
        long SCHEDULED_REPORT_TIME = 8640000000L;
        Uri REPORTING_ORIGIN = Uri.parse("https://com.example");
        String DEBUG_CLEARTEXT_PAYLOAD = "{"
                + "\"operation\":\"histogram\""
                + "\"data\":["
                        + "{\"bucket\":\"1369\",\"value\":32768},"
                        + "{\"bucket\":\"3461\",\"value\":1664}"
                    + "]"
                + "}";
    }

    private static final String AGGREGATE_REPORT_API_VERSION = "0.1";

    @Test
    public void performMigration_success() {
        // Setup
        DbHelper dbHelper = getDbHelper(2);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        insertTriggerV2(db);
        insertAggregateReportV2(db);

        // Execution
        new MeasurementDbMigratorV3().performMigration(db, 2, 3);
        // To mimic real onUpgrade behaviour. Without closing the db, changes don't reflect.
        db.close();

        // Verify
        db = dbHelper.getReadableDatabase();
        try (Cursor cursor =
                db.query(
                        MeasurementTables.TriggerContract.TABLE,
                        null, null, null, null, null, null, null)) {
            assertTrue(cursor.moveToNext());
            verifyTriggerMigration(cursor);
        }
        try (Cursor cursor =
                db.query(
                        MeasurementTables.AggregateReport.TABLE,
                        null, null, null, null, null, null, null)) {
            assertTrue(cursor.moveToNext());
            verifyAggregateReportMigration(cursor);
        }
    }

    private void verifyTriggerMigration(Cursor cursor) {
        assertEquals(TriggerV2.ID,
                cursor.getString(cursor.getColumnIndex(MeasurementTables.TriggerContract.ID)));
        assertEquals(TriggerV2.ATTRIBUTION_DESTINATION.toString(),
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION)));
        assertEquals(TriggerV2.TRIGGER_TIME,
                cursor.getLong(
                        cursor.getColumnIndex(MeasurementTables.TriggerContract.TRIGGER_TIME)));
        assertEquals(TriggerV2.STATUS,
                cursor.getInt(cursor.getColumnIndex(MeasurementTables.TriggerContract.STATUS)));
        assertEquals(TriggerV2.AD_TECH_DOMAIN.toString(),
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.TriggerContract.AD_TECH_DOMAIN)));
        assertEquals(TriggerV2.REGISTRANT.toString(),
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.TriggerContract.REGISTRANT)));
        assertEquals(TriggerV2.AGGREGATE_TRIGGER_DATA,
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA)));
        assertEquals(TriggerV2.AGGREGATE_VALUES,
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.TriggerContract.AGGREGATE_VALUES)));
        assertEventTriggersData(
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.TriggerContract.EVENT_TRIGGERS)));
    }

    private void verifyAggregateReportMigration(Cursor cursor) {
        assertEquals(AggregateReportV2.ID,
                cursor.getString(cursor.getColumnIndex(MeasurementTables.AggregateReport.ID)));
        assertEquals(AggregateReportV2.PUBLISHER.toString(),
                cursor.getString(cursor.getColumnIndex(
                        MeasurementTables.AggregateReport.PUBLISHER)));
        assertEquals(AggregateReportV2.ATTRIBUTION_DESTINATION.toString(),
                cursor.getString(cursor.getColumnIndex(
                        MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION)));
        assertEquals(AggregateReportV2.SOURCE_REGISTRATION_TIME,
                cursor.getLong(cursor.getColumnIndex(
                        MeasurementTables.AggregateReport.SOURCE_REGISTRATION_TIME)));
        assertEquals(AggregateReportV2.SCHEDULED_REPORT_TIME,
                cursor.getLong(cursor.getColumnIndex(
                        MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME)));
        assertEquals(AggregateReportV2.REPORTING_ORIGIN.toString(),
                cursor.getString(cursor.getColumnIndex(
                        MeasurementTables.AggregateReport.REPORTING_ORIGIN)));
        assertEquals(AggregateReportV2.DEBUG_CLEARTEXT_PAYLOAD,
                cursor.getString(cursor.getColumnIndex(
                        MeasurementTables.AggregateReport.DEBUG_CLEARTEXT_PAYLOAD)));
        assertEquals(AGGREGATE_REPORT_API_VERSION,
                cursor.getString(cursor.getColumnIndex(
                        MeasurementTables.AggregateReport.API_VERSION)));
    }

    private void insertTriggerV2(SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.TriggerContract.ID, TriggerV2.ID);
        values.put(MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION,
                TriggerV2.ATTRIBUTION_DESTINATION.toString());
        values.put(MeasurementTables.TriggerContract.TRIGGER_TIME, TriggerV2.TRIGGER_TIME);
        values.put(MeasurementTables.TriggerContract.DEPRECATED_EVENT_TRIGGER_DATA,
                TriggerV2.TRIGGER_DATA);
        values.put(MeasurementTables.TriggerContract.DEPRECATED_DEDUP_KEY, TriggerV2.DEDUP_KEY);
        values.put(MeasurementTables.TriggerContract.DEPRECATED_PRIORITY, TriggerV2.PRIORITY);
        values.put(MeasurementTables.TriggerContract.STATUS, TriggerV2.STATUS);
        values.put(MeasurementTables.TriggerContract.AD_TECH_DOMAIN,
                TriggerV2.AD_TECH_DOMAIN.toString());
        values.put(MeasurementTables.TriggerContract.REGISTRANT, TriggerV2.REGISTRANT.toString());
        values.put(MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA,
                TriggerV2.AGGREGATE_TRIGGER_DATA);
        values.put(MeasurementTables.TriggerContract.AGGREGATE_VALUES, TriggerV2.AGGREGATE_VALUES);

        long rowId = db.insert(MeasurementTables.TriggerContract.TABLE, null, values);

        if (rowId == -1) {
            fail();
        }
    }

    private void insertAggregateReportV2(SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AggregateReport.ID, AggregateReportV2.ID);
        values.put(MeasurementTables.AggregateReport.PUBLISHER,
                AggregateReportV2.PUBLISHER.toString());
        values.put(MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION,
                AggregateReportV2.ATTRIBUTION_DESTINATION.toString());
        values.put(MeasurementTables.AggregateReport.SOURCE_REGISTRATION_TIME,
                AggregateReportV2.SOURCE_REGISTRATION_TIME);
        values.put(MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME,
                AggregateReportV2.SCHEDULED_REPORT_TIME);
        values.put(MeasurementTables.AggregateReport.DEPRECATED_PRIVACY_BUDGET_KEY,
                AggregateReportV2.PRIVACY_BUDGET_KEY);
        values.put(MeasurementTables.AggregateReport.REPORTING_ORIGIN,
                AggregateReportV2.REPORTING_ORIGIN.toString());
        values.put(MeasurementTables.AggregateReport.DEBUG_CLEARTEXT_PAYLOAD,
                AggregateReportV2.DEBUG_CLEARTEXT_PAYLOAD);

        long rowId = db.insert(MeasurementTables.AggregateReport.TABLE, null, values);

        if (rowId == -1) {
            fail();
        }
    }

    @Override
    int getTargetVersion() {
        return 3;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV3();
    }

    private void assertEventTriggersData(String eventTriggers) {
        try {
            JSONArray arr = new JSONArray(eventTriggers);
            JSONObject obj = arr.getJSONObject(0);
            long dedupKey = obj.optLong("deduplication_key");
            assertTrue(obj.has("deduplication_key") && dedupKey == 0);
            assertTrue(obj.optLong("priority") == TriggerV2.PRIORITY);
            assertTrue(obj.optLong("trigger_data") == TriggerV2.TRIGGER_DATA);
        } catch (JSONException e) {
            assertTrue("Parsing eventTriggers failed! eventTriggers: " + eventTriggers, false);
        }
    }
}
