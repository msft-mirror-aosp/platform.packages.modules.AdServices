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

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.data.measurement.SqliteObjectMapperWrapper;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.TriggerFixture;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.aggregation.AggregateReportFixture;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Objects;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV3Test extends AbstractMeasurementDbMigratorTestBase {

    private static final Trigger TRIGGER = TriggerFixture.getValidTrigger();
    private static final String TRIGGER_ID = UUID.randomUUID().toString();
    private static final Long DEDUP_KEY = null;
    private static final long TRIGGER_DATA = 1L;
    private static final long PRIORITY = 345678L;

    private static final AggregateReport AGGREGATE_REPORT =
            AggregateReportFixture.getValidAggregateReport();
    private static final String AGGREGATE_REPORT_ID = UUID.randomUUID().toString();
    private static final String PRIVACY_BUDGET_KEY = "privacy_budget_key";

    @Test
    public void performMigration_success() {
        // Setup
        DbHelper dbHelper = getDbHelper(2);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        insertTriggerV2(db, TRIGGER);
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
            compareCursorValues(TRIGGER, cursor);
        }
        try (Cursor cursor =
                db.query(
                        MeasurementTables.AggregateReport.TABLE,
                        null, null, null, null, null, null, null)) {
            assertTrue(cursor.moveToNext());
            verifyAggregateReportMigration(cursor);
        }
    }

    private void compareCursorValues(Trigger trigger, Cursor cursor) {
        assertEquals(
                TRIGGER_ID,
                cursor.getString(cursor.getColumnIndex(MeasurementTables.TriggerContract.ID)));
        assertEquals(
                trigger.getAttributionDestination().toString(),
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION)));
        assertEquals(
                trigger.getTriggerTime(),
                cursor.getLong(
                        cursor.getColumnIndex(MeasurementTables.TriggerContract.TRIGGER_TIME)));
        assertEquals(
                trigger.getStatus(),
                cursor.getInt(cursor.getColumnIndex(MeasurementTables.TriggerContract.STATUS)));
        assertEquals(
                trigger.getAdTechDomain().toString(),
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.TriggerContract.AD_TECH_DOMAIN)));
        assertEquals(
                trigger.getRegistrant().toString(),
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.TriggerContract.REGISTRANT)));
        assertEquals(
                trigger.getAggregateTriggerData(),
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA)));
        assertEquals(
                trigger.getAggregateValues(),
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.TriggerContract.AGGREGATE_VALUES)));
        assertEventTriggersData(
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.TriggerContract.EVENT_TRIGGERS)));
    }

    private void verifyAggregateReportMigration(Cursor cursor) {
        assertEquals(
                AGGREGATE_REPORT_ID,
                cursor.getString(cursor.getColumnIndex(MeasurementTables.AggregateReport.ID)));
        assertTrue(Objects.equals(
                AGGREGATE_REPORT,
                SqliteObjectMapperWrapper.constructAggregateReport(cursor)));
    }

    private void insertTriggerV2(SQLiteDatabase db, Trigger trigger) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.TriggerContract.ID, TRIGGER_ID);
        values.put(
                MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION,
                trigger.getAttributionDestination().toString());
        values.put(MeasurementTables.TriggerContract.TRIGGER_TIME, trigger.getTriggerTime());
        values.put(
                MeasurementTables.TriggerContract.DEPRECATED_EVENT_TRIGGER_DATA, TRIGGER_DATA);
        values.put(
                MeasurementTables.TriggerContract.DEPRECATED_DEDUP_KEY, DEDUP_KEY);
        values.put(
                MeasurementTables.TriggerContract.DEPRECATED_PRIORITY, PRIORITY);
        values.put(MeasurementTables.TriggerContract.STATUS, trigger.getStatus());
        values.put(
                MeasurementTables.TriggerContract.AD_TECH_DOMAIN,
                trigger.getAdTechDomain().toString());
        values.put(
                MeasurementTables.TriggerContract.REGISTRANT, trigger.getRegistrant().toString());
        values.put(
                MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA,
                trigger.getAggregateTriggerData());
        values.put(
                MeasurementTables.TriggerContract.AGGREGATE_VALUES, trigger.getAggregateValues());

        long rowId = db.insert(MeasurementTables.TriggerContract.TABLE, null, values);

        if (rowId == -1) {
            fail();
        }
    }

    private void insertAggregateReportV2(SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AggregateReport.ID, AGGREGATE_REPORT_ID);
        values.put(MeasurementTables.AggregateReport.PUBLISHER,
                AGGREGATE_REPORT.getPublisher().toString());
        values.put(MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION,
                AGGREGATE_REPORT.getAttributionDestination().toString());
        values.put(MeasurementTables.AggregateReport.SOURCE_REGISTRATION_TIME,
                AGGREGATE_REPORT.getSourceRegistrationTime());
        values.put(MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME,
                AGGREGATE_REPORT.getScheduledReportTime());
        values.put(MeasurementTables.AggregateReport.DEPRECATED_PRIVACY_BUDGET_KEY,
                PRIVACY_BUDGET_KEY);
        values.put(MeasurementTables.AggregateReport.REPORTING_ORIGIN,
                AGGREGATE_REPORT.getReportingOrigin().toString());
        values.put(MeasurementTables.AggregateReport.DEBUG_CLEARTEXT_PAYLOAD,
                AGGREGATE_REPORT.getDebugCleartextPayload());

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
            assertTrue(obj.optLong("priority") == PRIORITY);
            assertTrue(obj.optLong("trigger_data") == TRIGGER_DATA);
        } catch (JSONException e) {
            assertTrue("Parsing eventTriggers failed! eventTriggers: " + eventTriggers, false);
        }
    }
}
