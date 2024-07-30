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
public class MeasurementDbMigratorV36Test extends MeasurementDbMigratorTestBase {
    @Test
    public void performMigration_v35ToV36WithData_maintainsDataIntegrity() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        35,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Map<String, List<ContentValues>> fakeData = createFakeDataSourceV35();
        populateDb(db, fakeData);
        // Execution
        getTestSubject().performMigration(db, 35, 36);
        // Assertion
        MigrationTestHelper.verifyDataInDb(db, fakeData);
        // Check that new columns are initialized with null value
        try (Cursor cursor =
                db.query(
                        MeasurementTables.SourceContract.TABLE,
                        new String[] {
                            MeasurementTables.SourceContract.REINSTALL_REATTRIBUTION_WINDOW
                        },
                        null,
                        null,
                        null,
                        null,
                        null)) {
            assertEquals(1, cursor.getCount());
            while (cursor.moveToNext()) {
                int defaultReinstallReattributionWindow =
                        cursor.getInt(
                                cursor.getColumnIndex(
                                        MeasurementTables.SourceContract
                                                .REINSTALL_REATTRIBUTION_WINDOW));
                assertEquals(0, defaultReinstallReattributionWindow);
            }
        }
    }

    private Map<String, List<ContentValues>> createFakeDataSourceV35() {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        // Source Table
        String sourceId = UUID.randomUUID().toString();
        ContentValues source = ContentValueFixtures.generateSourceContentValuesV35();
        source.put(MeasurementTables.SourceContract.ID, sourceId);
        tableRowsMap.put(MeasurementTables.SourceContract.TABLE, List.of(source));
        return tableRowsMap;
    }

    @Override
    int getTargetVersion() {
        return 36;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV36();
    }
}
