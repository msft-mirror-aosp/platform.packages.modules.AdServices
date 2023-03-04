/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.data.measurement;

import static com.android.adservices.data.DbTestUtil.doesIndexExist;
import static com.android.adservices.data.DbTestUtil.doesTableExistAndColumnCountMatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.DbHelperTest;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.measurement.migration.AbstractMeasurementDbMigratorTestBase;
import com.android.adservices.data.measurement.migration.ContentValueFixtures;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MeasurementDbHelperTest {

    protected static final Context sContext = ApplicationProvider.getApplicationContext();

    private static final float FLOAT_COMPARISON_EPSILON = 0.00005f;

    @Test
    public void testNewInstall() {
        MeasurementDbHelper measurementDbHelper =
                new MeasurementDbHelper(
                        sContext,
                        "msmt_test_db",
                        MeasurementDbHelper.CURRENT_DATABASE_VERSION,
                        DbTestUtil.getDbHelperForTest());
        SQLiteDatabase db = measurementDbHelper.safeGetWritableDatabase();
        verifyLatestSchema(db);
    }

    @Test
    public void testMigrationFromOldDatabase() {
        String dbName = "test_db-1";
        String migrationDbName = "msmt_db_migrate-1";
        DbHelperV1 dbHelperV1 = new DbHelperV1(sContext, dbName, 1);
        SQLiteDatabase db = dbHelperV1.safeGetWritableDatabase();

        assertEquals(1, db.getVersion());

        DbHelper dbHelper = new DbHelper(sContext, dbName, DbHelper.CURRENT_DATABASE_VERSION);
        SQLiteDatabase oldDb = dbHelper.safeGetWritableDatabase();

        assertEquals(6, oldDb.getVersion());

        MeasurementDbHelper measurementDbHelper =
                new MeasurementDbHelper(
                        sContext,
                        migrationDbName,
                        MeasurementDbHelper.CURRENT_DATABASE_VERSION,
                        dbHelper);
        verifyLatestSchema(measurementDbHelper.safeGetWritableDatabase());
        DbHelperTest.assertMeasurementTablesDoNotExist(oldDb);
    }

    @Test
    public void testMigrationDataIntegrityToV6FromOldDatabase() {
        String dbName = "test_db-2";
        String migrationDbName = "msmt_db_migrate-2";
        DbHelperV1 dbHelperV1 = new DbHelperV1(sContext, dbName, 1);
        SQLiteDatabase db = dbHelperV1.safeGetWritableDatabase();

        assertEquals(1, db.getVersion());

        DbHelper dbHelper = new DbHelper(sContext, dbName, DbHelper.CURRENT_DATABASE_VERSION);
        SQLiteDatabase oldDb = dbHelper.safeGetWritableDatabase();

        assertEquals(6, oldDb.getVersion());
        // Sorted map because we want source/trigger to be inserted before other tables to
        // respect foreign key constraints
        Map<String, List<ContentValues>> fakeData = createFakeData();

        populateOldDb(oldDb, fakeData);
        MeasurementDbHelper measurementDbHelper =
                new MeasurementDbHelper(
                        sContext,
                        migrationDbName,
                        MeasurementDbHelper.OLD_DATABASE_FINAL_VERSION,
                        dbHelper);
        SQLiteDatabase newDb = measurementDbHelper.safeGetWritableDatabase();
        DbHelperTest.assertMeasurementTablesDoNotExist(oldDb);
        verifyLatestSchema(newDb);
        assertEquals(MeasurementDbHelper.OLD_DATABASE_FINAL_VERSION, newDb.getVersion());
        verifyNewDb(newDb, fakeData);
        emptyTables(newDb, MeasurementTables.V6_TABLES);
    }

    private Map<String, List<ContentValues>> createFakeData() {

        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        // Source Table
        List<ContentValues> sourceRows = new ArrayList<>();
        ContentValues source1 = ContentValueFixtures.generateSourceContentValuesV6();
        source1.put(MeasurementTables.SourceContract.ID, UUID.randomUUID().toString());
        sourceRows.add(source1);
        sourceRows.add(ContentValueFixtures.generateSourceContentValuesV6());
        tableRowsMap.put(MeasurementTables.SourceContract.TABLE, sourceRows);

        // Trigger Table
        List<ContentValues> triggerRows = new ArrayList<>();
        ContentValues trigger1 = ContentValueFixtures.generateTriggerContentValuesV6();
        trigger1.put(MeasurementTables.TriggerContract.ID, UUID.randomUUID().toString());
        triggerRows.add(trigger1);
        triggerRows.add(ContentValueFixtures.generateTriggerContentValuesV6());
        tableRowsMap.put(MeasurementTables.TriggerContract.TABLE, triggerRows);

        // Event Report Table
        List<ContentValues> eventReportRows = new ArrayList<>();
        ContentValues eventReport1 = ContentValueFixtures.generateEventReportContentValuesV6();
        eventReport1.put(MeasurementTables.EventReportContract.ID, UUID.randomUUID().toString());
        eventReportRows.add(eventReport1);
        eventReportRows.add(ContentValueFixtures.generateEventReportContentValuesV6());
        tableRowsMap.put(MeasurementTables.EventReportContract.TABLE, eventReportRows);

        // Aggregate Report Table
        List<ContentValues> aggregateReportRows = new ArrayList<>();
        ContentValues aggregateReport1 =
                ContentValueFixtures.generateAggregateReportContentValuesV6();
        aggregateReport1.put(MeasurementTables.AggregateReport.ID, UUID.randomUUID().toString());
        aggregateReportRows.add(aggregateReport1);
        aggregateReportRows.add(ContentValueFixtures.generateAggregateReportContentValuesV6());
        tableRowsMap.put(MeasurementTables.AggregateReport.TABLE, aggregateReportRows);

        // Attribution table
        List<ContentValues> attributionRows = new ArrayList<>();
        ContentValues attribution1 = ContentValueFixtures.generateAttributionContentValuesV6();
        attribution1.put(MeasurementTables.AttributionContract.ID, UUID.randomUUID().toString());
        attributionRows.add(attribution1);
        attributionRows.add(ContentValueFixtures.generateAttributionContentValuesV6());
        tableRowsMap.put(MeasurementTables.AttributionContract.TABLE, attributionRows);

        // Aggregate Encryption Key table
        List<ContentValues> aggregateEncryptionKeyRows = new ArrayList<>();
        ContentValues aggregateEncryptionKey1 =
                ContentValueFixtures.generateAggregateEncryptionKeyContentValuesV6();
        aggregateEncryptionKey1.put(
                MeasurementTables.AggregateEncryptionKey.ID, UUID.randomUUID().toString());
        aggregateEncryptionKeyRows.add(aggregateEncryptionKey1);
        aggregateEncryptionKeyRows.add(
                ContentValueFixtures.generateAggregateEncryptionKeyContentValuesV6());
        tableRowsMap.put(
                MeasurementTables.AggregateEncryptionKey.TABLE, aggregateEncryptionKeyRows);

        // Debug Report Table
        List<ContentValues> debugReportRows = new ArrayList<>();
        ContentValues debugReport1 = ContentValueFixtures.generateDebugReportContentValuesV6();
        debugReport1.put(MeasurementTables.DebugReportContract.ID, UUID.randomUUID().toString());
        debugReportRows.add(debugReport1);
        debugReportRows.add(ContentValueFixtures.generateDebugReportContentValuesV6());
        tableRowsMap.put(MeasurementTables.DebugReportContract.TABLE, debugReportRows);

        // Async Registration Table
        List<ContentValues> asyncRegistrationRows = new ArrayList<>();
        ContentValues asyncRegistration1 =
                ContentValueFixtures.generateAsyncRegistrationContentValuesV6();
        asyncRegistration1.put(
                MeasurementTables.AsyncRegistrationContract.ID, UUID.randomUUID().toString());
        asyncRegistrationRows.add(asyncRegistration1);
        asyncRegistrationRows.add(ContentValueFixtures.generateAsyncRegistrationContentValuesV6());
        tableRowsMap.put(MeasurementTables.AsyncRegistrationContract.TABLE, asyncRegistrationRows);

        return tableRowsMap;
    }

    private void emptyTables(SQLiteDatabase db, String[] tables) {
        Arrays.stream(tables)
                .forEach(
                        (table) -> {
                            db.delete(table, null, null);
                        });
    }

    private void verifyNewDb(SQLiteDatabase newDb, Map<String, List<ContentValues>> fakeData) {
        fakeData.forEach(
                (table, rows) -> {
                    List<ContentValues> newRows = new ArrayList<>();
                    try (Cursor cursor =
                            newDb.query(table, null, null, null, null, null, null, null)) {
                        while (cursor.moveToNext()) {
                            newRows.add(
                                    AbstractMeasurementDbMigratorTestBase.cursorRowToContentValues(
                                            cursor));
                        }
                    }
                    rows.sort(
                            (c1, c2) ->
                                    String.CASE_INSENSITIVE_ORDER.compare(
                                            c1.getAsString("_id"), c2.getAsString("_id")));
                    newRows.sort(
                            (c1, c2) ->
                                    String.CASE_INSENSITIVE_ORDER.compare(
                                            c1.getAsString("_id"), c2.getAsString("_id")));

                    assertEquals(table + " row count matching failed", rows.size(), newRows.size());

                    for (int i = 0; i < rows.size(); i++) {
                        ContentValues expected = rows.get(i);
                        ContentValues actual = newRows.get(i);
                        assertTrue(
                                String.format(
                                        "Table: %s, Row: %d, Expected: %s, Actual: %s",
                                        table, i, expected, actual),
                                doContentValueMatch(expected, actual));
                    }
                });
    }

    private void populateOldDb(SQLiteDatabase oldDb, Map<String, List<ContentValues>> fakeData) {
        fakeData.forEach(
                (table, rows) -> {
                    rows.forEach(
                            (row) -> {
                                assertNotEquals(-1, oldDb.insert(table, null, row));
                            });
                });
    }

    private void verifyLatestSchema(SQLiteDatabase db) {
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_source", 31));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_trigger", 19));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_async_registration_contract", 18));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_event_report", 17));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_attribution", 10));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_aggregate_report", 14));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_aggregate_encryption_key", 4));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_debug_report", 4));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_xna_ignored_sources", 2));
        assertTrue(doesIndexExist(db, "idx_msmt_source_ad_ei_et"));
        assertTrue(doesIndexExist(db, "idx_msmt_source_p_ad_wd_s_et"));
        assertTrue(doesIndexExist(db, "idx_msmt_trigger_ad_ei_tt"));
        assertTrue(doesIndexExist(db, "idx_msmt_source_et"));
        assertTrue(doesIndexExist(db, "idx_msmt_trigger_tt"));
        assertTrue(doesIndexExist(db, "idx_msmt_attribution_ss_so_ds_do_ei_tt"));
    }

    private boolean doContentValueMatch(ContentValues values1, ContentValues values2) {
        for (Map.Entry<String, Object> element : values1.valueSet()) {
            String key1 = element.getKey();
            Object value1 = element.getValue();
            if (!values2.containsKey(key1)) {
                return false;
            }
            Object value2 = values2.get(key1);
            if (value1.equals(value2)) {
                continue;
            }
            if (value1 instanceof Number
                    && !nearlyEqual(
                            ((Number) value1).floatValue(),
                            ((Number) value2).floatValue(),
                            FLOAT_COMPARISON_EPSILON)) {
                return false;
            }
        }
        return true;
    }

    public static boolean nearlyEqual(float a, float b, float epsilon) {
        if (a == b) {
            return true;
        }
        final float absA = Math.abs(a);
        final float absB = Math.abs(b);
        final float diff = Math.abs(a - b);

        return diff / (absA + absB) < epsilon;
    }
}
