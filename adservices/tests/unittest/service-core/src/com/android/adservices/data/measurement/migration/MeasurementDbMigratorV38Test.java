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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementDbHelper;
import com.android.adservices.data.measurement.MeasurementTables;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV38Test extends MeasurementDbMigratorTestBase {
    @Test
    public void performMigration_v37ToV38WithData_maintainsDataIntegrity() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        37,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Map<String, List<ContentValues>> fakeData = createFakeDataSourceV36();
        populateDb(db, fakeData);
        // Execution
        getTestSubject().performMigration(db, 37, 38);
        // Assertion
        MigrationTestHelper.verifyDataInDb(db, fakeData);
        // Check that new column is initialized with zero
        try (Cursor cursor =
                db.query(
                        MeasurementTables.SourceContract.TABLE,
                        new String[] {MeasurementTables.SourceContract.DESTINATION_LIMIT_PRIORITY},
                        null,
                        null,
                        null,
                        null,
                        null)) {
            assertEquals(2, cursor.getCount());
            while (cursor.moveToNext()) {
                int defaultDestinationLimitPriority =
                        cursor.getInt(
                                cursor.getColumnIndex(
                                        MeasurementTables.SourceContract
                                                .DESTINATION_LIMIT_PRIORITY));
                assertEquals(0, defaultDestinationLimitPriority);
            }
        }
    }

    private Map<String, List<ContentValues>> createFakeDataSourceV36() {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        // Source Table
        String sourceId = UUID.randomUUID().toString();
        ContentValues source = ContentValueFixtures.generateSourceContentValuesV36();
        source.put(MeasurementTables.SourceContract.ID, sourceId);
        tableRowsMap.put(
                MeasurementTables.SourceContract.TABLE,
                List.of(
                        source,
                        ContentValueFixtures.generateSourceContentValuesV36()));
        return tableRowsMap;
    }

    @Override
    int getTargetVersion() {
        return 38;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV38();
    }
}
