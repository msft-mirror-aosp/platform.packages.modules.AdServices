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

import static com.android.adservices.common.DbTestUtil.getDbHelperForTest;
import static com.android.adservices.data.measurement.migration.MigrationTestHelper.populateDb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import com.android.adservices.data.measurement.MeasurementDbHelper;
import com.android.adservices.data.measurement.MeasurementTables;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV33Test extends MeasurementDbMigratorTestBase {
    @Test
    public void performMigration_v32ToV33WithData_maintainsDataIntegrity() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        32,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String trigger1Id = UUID.randomUUID().toString();
        String source1Id = UUID.randomUUID().toString();
        Map<String, List<ContentValues>> fakeData = createFakeDataV32(trigger1Id, source1Id);
        populateDb(db, fakeData);
        // Execution
        getTestSubject().performMigration(db, 32, 33);
        // Assertion
        Map<String, Set<String>> columnsToBeSkipped =
                Map.of(
                        MeasurementTables.TriggerContract.TABLE,
                        Set.of(MeasurementTables.TriggerContract.ID));
        MigrationTestHelper.verifyDataInDb(db, fakeData, new HashMap<>(), columnsToBeSkipped);
        List<Pair<String, String>> tableAndNewColumnPairs = new ArrayList<>();
        tableAndNewColumnPairs.add(
                new Pair<>(
                        MeasurementTables.TriggerContract.TABLE,
                        MeasurementTables.TriggerContract.TRIGGER_CONTEXT_ID));
        tableAndNewColumnPairs.add(
                new Pair<>(
                        MeasurementTables.AggregateReport.TABLE,
                        MeasurementTables.AggregateReport.TRIGGER_CONTEXT_ID));
        tableAndNewColumnPairs.forEach(
                pair -> {
                    try (Cursor cursor =
                            db.query(
                                    pair.first,
                                    new String[] {pair.second},
                                    null,
                                    null,
                                    null,
                                    null,
                                    null)) {
                        assertEquals(1, cursor.getCount());
                        while (cursor.moveToNext()) {
                            assertNull(cursor.getString(cursor.getColumnIndex(pair.second)));
                        }
                    }
                });
    }

    private Map<String, List<ContentValues>> createFakeDataV32(
            String trigger1Id, String source1Id) {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();

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
                ContentValueFixtures.generateAggregateReportContentValuesV32();
        // Satisfy the foreign key constraints on the Aggregate Report table.
        aggregateReport1.put(MeasurementTables.AggregateReport.TRIGGER_ID, trigger1Id);
        aggregateReport1.put(MeasurementTables.AggregateReport.SOURCE_ID, source1Id);
        aggregateReportRows.add(aggregateReport1);
        return aggregateReportRows;
    }

    @NotNull
    private static List<ContentValues> getSourceRows(String source1Id) {
        List<ContentValues> sourceRows = new ArrayList<>();
        ContentValues source1 = ContentValueFixtures.generateSourceContentValuesV32();
        source1.put(MeasurementTables.SourceContract.ID, source1Id);
        sourceRows.add(source1);
        return sourceRows;
    }

    @NotNull
    private static List<ContentValues> getTriggerRows(String trigger1Id) {
        List<ContentValues> triggerRows = new ArrayList<>();
        ContentValues trigger1 = ContentValueFixtures.generateTriggerContentValuesV32();
        trigger1.put(MeasurementTables.TriggerContract.ID, trigger1Id);
        triggerRows.add(trigger1);
        return triggerRows;
    }

    @Override
    int getTargetVersion() {
        return 33;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV33();
    }
}
