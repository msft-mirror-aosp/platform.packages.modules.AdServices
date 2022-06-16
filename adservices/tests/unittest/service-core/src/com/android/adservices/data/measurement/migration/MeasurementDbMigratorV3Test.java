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

import static com.android.adservices.data.measurement.migration.MeasurementDbMigratorV3.TriggerV2Extension;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.service.measurement.Trigger;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV3Test extends AbstractMeasurementDbMigratorTestBase {

    private static final String TOP_LEVEL_FILTERS_JSON_STRING =
            "{\n"
                    + "  \"key_1x\": [\"value_1\", \"value_2\"],\n"
                    + "  \"key_2x\": [\"value_1\", \"value_2\"]\n"
                    + "}\n";

    private static final String EVENT_TRIGGERS =
            "[{\"deduplication_key\":2345678,\"priority\":345678,\"trigger_data\":1}]";

    private static final TriggerV2Extension V2_TRIGGER_EXTENSION =
            new TriggerV2Extension(2345678L, 1L, 345678L);

    private static final Trigger TRIGGER =
            new Trigger.Builder()
                    .setAdTechDomain(Uri.parse("https://example.com"))
                    .setAttributionDestination(Uri.parse("https://example.com/aD"))
                    .setId("1")
                    .setTriggerTime(5L)
                    .setStatus(Trigger.Status.PENDING)
                    .setRegistrant(Uri.parse("android-app://com.example.abc"))
                    .setAggregateTriggerData("[{\"key11\": \"val11\"}]")
                    .setAggregateValues("[{\"key12\": \"val12\"}]")
                    .setFilters(TOP_LEVEL_FILTERS_JSON_STRING)
                    .setEventTriggers(EVENT_TRIGGERS)
                    .build();

    @Test
    public void performMigration_success() {
        // Setup
        DbHelper dbHelper = getDbHelper(2);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        insertTriggerV2(db, TRIGGER, V2_TRIGGER_EXTENSION);

        // Execution
        new MeasurementDbMigratorV3().performMigration(db, 2, 3);
        // To mimic real onUpgrade behaviour. Without closing the db, changes don't reflect.
        db.close();

        // Verify
        db = dbHelper.getReadableDatabase();
        try (Cursor cursor =
                db.query(
                        MeasurementTables.TriggerContract.TABLE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)) {
            assertTrue(cursor.moveToNext());
            compareCursorValues(TRIGGER, cursor);
        }
    }

    private void compareCursorValues(Trigger trigger, Cursor cursor) {
        assertEquals(
                trigger.getId(),
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
        assertEventTriggersDataDisregardOrder(
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.TriggerContract.EVENT_TRIGGERS)));
    }

    private void insertTriggerV2(
            SQLiteDatabase db, Trigger trigger, TriggerV2Extension triggerV2Extension) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.TriggerContract.ID, trigger.getId());
        values.put(
                MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION,
                trigger.getAttributionDestination().toString());
        values.put(MeasurementTables.TriggerContract.TRIGGER_TIME, trigger.getTriggerTime());
        values.put(
                MeasurementTables.TriggerContract.DEPRECATED_EVENT_TRIGGER_DATA,
                triggerV2Extension.getEventTriggerData());
        values.put(
                MeasurementTables.TriggerContract.DEPRECATED_DEDUP_KEY,
                triggerV2Extension.getDedupKey());
        values.put(
                MeasurementTables.TriggerContract.DEPRECATED_PRIORITY,
                triggerV2Extension.getPriority());
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

    @Override
    int getTargetVersion() {
        return 3;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV3();
    }

    private void assertEventTriggersDataDisregardOrder(String eventTriggers) {
        assertTrue(
                eventTriggers.contains("\"deduplication_key\":2345678")
                        && eventTriggers.contains("\"priority\":345678")
                        && eventTriggers.contains("\"trigger_data\":1"));
    }
}
