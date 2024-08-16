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

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import com.android.adservices.data.measurement.MeasurementDbHelper;
import com.android.adservices.data.measurement.MeasurementTables;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV35Test extends MeasurementDbMigratorTestBase {
    @Test
    public void performMigration_v34ToV35WithData_maintainsDataIntegrity() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        34,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Map<String, List<ContentValues>> fakeData = createFakeDataSourceV34();
        populateDb(db, fakeData);
        // Execution
        getTestSubject().performMigration(db, 34, 35);
        // Assertion
        MigrationTestHelper.verifyDataInDb(db, fakeData);
        // Check that new columns are initialized with null value
        List<Pair<String, String>> tableAndNewColumnPairs =
                List.of(
                        Pair.create(
                                MeasurementTables.AttributionContract.TABLE,
                                MeasurementTables.AttributionContract.REPORT_ID));
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
                        assertNotEquals(0, cursor.getCount());
                        while (cursor.moveToNext()) {
                            assertNull(cursor.getString(cursor.getColumnIndex(pair.second)));
                        }
                    }
                });

        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.SourceAttributionScopeContract.TABLE, 2));
    }

    private Map<String, List<ContentValues>> createFakeDataSourceV34() {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        // Source Table
        ContentValues source1 = ContentValueFixtures.generateSourceContentValuesV34();
        tableRowsMap.put(MeasurementTables.SourceContract.TABLE, List.of(source1));
        ContentValues trigger1 = ContentValueFixtures.generateTriggerContentValuesV34();
        tableRowsMap.put(MeasurementTables.TriggerContract.TABLE, List.of(trigger1));
        ContentValues attribution1 = ContentValueFixtures.generateAttributionContentValuesV29();
        ContentValues attribution2 = ContentValueFixtures.generateAttributionContentValuesV29();
        List<ContentValues> attributionRows = new ArrayList<>();
        attribution1.put(MeasurementTables.AttributionContract.ID, "1");
        attribution2.put(MeasurementTables.AttributionContract.ID, "2");
        attributionRows.add(attribution1);
        attributionRows.add(attribution2);
        tableRowsMap.put(MeasurementTables.AttributionContract.TABLE, attributionRows);
        return tableRowsMap;
    }

    @Override
    int getTargetVersion() {
        return 35;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV35();
    }
}
