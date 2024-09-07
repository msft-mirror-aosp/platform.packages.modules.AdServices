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

import static com.android.adservices.common.DbTestUtil.doesTableExistAndColumnCountMatch;
import static com.android.adservices.common.DbTestUtil.getDbHelperForTest;
import static com.android.adservices.data.measurement.migration.MigrationTestHelper.populateDb;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementDbHelper;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.data.measurement.MeasurementTables.AggregateReport;
import com.android.adservices.data.measurement.MeasurementTables.SourceContract;
import com.android.adservices.data.measurement.MeasurementTables.TriggerContract;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV43Test extends MeasurementDbMigratorTestBase {

    private static final String AGGREGATE_REPORT_ATTRIBUTION_API = "attribution-reporting";

    @Test
    public void performMigration_v42ToV43WithData_maintainsDataIntegrity() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        42,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Map<String, List<ContentValues>> fakeData = createFakeDataV42();
        populateDb(db, fakeData);

        // Execution
        getTestSubject().performMigration(db, 42, 43);

        // Assertion
        MigrationTestHelper.verifyDataInDb(db, fakeData);

        // Check that new columns are initialized
        validateNewTextColumn(
                db,
                MeasurementTables.SourceContract.TABLE,
                SourceContract.AGGREGATE_DEBUG_REPORTING,
                null);
        validateNewIntColumn(
                db,
                MeasurementTables.SourceContract.TABLE,
                SourceContract.AGGREGATE_DEBUG_REPORT_CONTRIBUTIONS,
                0L);
        validateNewTextColumn(
                db,
                MeasurementTables.TriggerContract.TABLE,
                TriggerContract.AGGREGATE_DEBUG_REPORTING,
                null);
        validateNewTextColumn(
                db,
                MeasurementTables.AggregateReport.TABLE,
                AggregateReport.API,
                AGGREGATE_REPORT_ATTRIBUTION_API);
        assertThat(
                        doesTableExistAndColumnCountMatch(
                                db,
                                MeasurementTables.AggregatableDebugReportBudgetTrackerContract
                                        .TABLE,
                                7))
                .isTrue();
    }

    private Map<String, List<ContentValues>> createFakeDataV42() {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();

        String trigger1Id = UUID.randomUUID().toString();
        String source1Id = UUID.randomUUID().toString();

        List<ContentValues> triggerRows = getTriggerRows(trigger1Id);
        tableRowsMap.put(MeasurementTables.TriggerContract.TABLE, triggerRows);

        List<ContentValues> sourceRows = getSourceRows(source1Id);
        tableRowsMap.put(MeasurementTables.SourceContract.TABLE, sourceRows);

        List<ContentValues> aggregateReportRows = getAggregateReportRows(trigger1Id, source1Id);
        tableRowsMap.put(MeasurementTables.AggregateReport.TABLE, aggregateReportRows);

        return tableRowsMap;
    }

    @NotNull
    private static List<ContentValues> getAggregateReportRows(String trigger1Id, String source1Id) {
        List<ContentValues> aggregateReportRows = new ArrayList<>();
        ContentValues aggregateReport1 =
                ContentValueFixtures.generateAggregateReportContentValuesV33();

        aggregateReport1.put(MeasurementTables.AggregateReport.TRIGGER_ID, trigger1Id);
        aggregateReport1.put(MeasurementTables.AggregateReport.SOURCE_ID, source1Id);
        aggregateReportRows.add(aggregateReport1);
        return aggregateReportRows;
    }

    @NotNull
    private static List<ContentValues> getSourceRows(String source1Id) {
        List<ContentValues> sourceRows = new ArrayList<>();
        ContentValues source1 = ContentValueFixtures.generateSourceContentValuesV40();
        source1.put(MeasurementTables.SourceContract.ID, source1Id);
        sourceRows.add(source1);
        return sourceRows;
    }

    @NotNull
    private static List<ContentValues> getTriggerRows(String trigger1Id) {
        List<ContentValues> triggerRows = new ArrayList<>();
        ContentValues trigger1 = ContentValueFixtures.generateTriggerContentValuesV34();
        trigger1.put(MeasurementTables.TriggerContract.ID, trigger1Id);
        triggerRows.add(trigger1);
        return triggerRows;
    }

    private static void validateNewTextColumn(
            SQLiteDatabase db, String table, String column, String expected) {
        try (Cursor cursor = db.query(table, new String[] {column}, null, null, null, null, null)) {
            assertThat(cursor.getCount()).isEqualTo(1);
            while (cursor.moveToNext()) {
                String actual = cursor.getString(cursor.getColumnIndex(column));
                assertThat(expected).isEqualTo(actual);
            }
        }
    }

    private static void validateNewIntColumn(
            SQLiteDatabase db, String table, String column, long expected) {
        try (Cursor cursor = db.query(table, new String[] {column}, null, null, null, null, null)) {
            assertThat(cursor.getCount()).isEqualTo(1);
            while (cursor.moveToNext()) {
                long actual = cursor.getInt(cursor.getColumnIndex(column));
                assertThat(expected).isEqualTo(actual);
            }
        }
    }

    @Override
    int getTargetVersion() {
        return 43;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV43();
    }
}
