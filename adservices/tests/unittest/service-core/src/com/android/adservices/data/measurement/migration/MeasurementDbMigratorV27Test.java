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
package com.android.adservices.data.measurement.migration;

import static com.android.adservices.common.DbTestUtil.getDbHelperForTest;
import static com.android.adservices.data.measurement.migration.MigrationTestHelper.populateDb;

import static org.junit.Assert.assertEquals;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementDbHelper;
import com.android.adservices.data.measurement.MeasurementTables;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV27Test extends MeasurementDbMigratorTestBase {
    @Test
    public void performMigration_v26ToV27WithData_maintainsDataIntegrity() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        26,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        populateSource(db, ContentValueFixtures.AggregateReportValues.SOURCE_ID);
        populateTrigger(db, ContentValueFixtures.AggregateReportValues.TRIGGER_ID);

        Map<String, List<ContentValues>> fakeData = createFakeDataV26();
        populateDb(db, fakeData);
        // Execution
        getTestSubject().performMigration(db, 26, 27);
        // Assertion
        MigrationTestHelper.verifyDataInDb(db, fakeData);
        // Check that new column is initialized to 0
        try (Cursor cursor =
                db.query(
                        MeasurementTables.AggregateReport.TABLE,
                        new String[] {MeasurementTables.AggregateReport.IS_FAKE_REPORT},
                        null,
                        null,
                        null,
                        null,
                        null)) {
            assertEquals(2, cursor.getCount());
            while (cursor.moveToNext()) {
                int isFakeReport =
                        cursor.getInt(
                                cursor.getColumnIndex(
                                        MeasurementTables.AggregateReport.IS_FAKE_REPORT));
                assertEquals(0, isFakeReport);
            }
        }
    }

    private void populateSource(SQLiteDatabase db, String sourceId) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceContract.ID, sourceId);
        db.insert(MeasurementTables.SourceContract.TABLE, /* nullColumnHack= */ null, values);
    }

    private void populateTrigger(SQLiteDatabase db, String triggerId) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.TriggerContract.ID, triggerId);
        db.insert(MeasurementTables.TriggerContract.TABLE, /* nullColumnHack= */ null, values);
    }

    private Map<String, List<ContentValues>> createFakeDataV26() {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        // Aggregate Report Table
        List<ContentValues> aggregateReportRows = new ArrayList<>();
        ContentValues aggregateReport1 =
                ContentValueFixtures.generateAggregateReportContentValuesV20();
        aggregateReport1.put(MeasurementTables.AggregateReport.ID, UUID.randomUUID().toString());
        aggregateReportRows.add(aggregateReport1);
        aggregateReportRows.add(ContentValueFixtures.generateAggregateReportContentValuesV20());
        tableRowsMap.put(MeasurementTables.AggregateReport.TABLE, aggregateReportRows);

        return tableRowsMap;
    }

    @Override
    int getTargetVersion() {
        return 27;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV27();
    }
}
