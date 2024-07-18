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

import static com.android.adservices.data.DbTestUtil.getDbHelperForTest;
import static com.android.adservices.data.measurement.migration.MigrationTestHelper.populateDb;

import static com.google.common.truth.Truth.assertThat;

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
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public final class MeasurementDbMigratorV40Test extends MeasurementDbMigratorTestBase {
    @Test
    public void performMigration_v39ToV40WithData_maintainsDataIntegrity() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        39,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Map<String, List<ContentValues>> fakeData = createFakeDataSourceV39();
        populateDb(db, fakeData);
        // Execution
        getTestSubject().performMigration(db, 39, 40);
        // Assertion
        MigrationTestHelper.verifyDataInDb(db, fakeData);
        // Check that new column is initialized with null
        List<Pair<String, String>> tableAndNewColumnPairs = new ArrayList<>();
        tableAndNewColumnPairs.add(
                new Pair<>(
                        MeasurementTables.SourceContract.TABLE,
                        MeasurementTables.SourceContract.EVENT_LEVEL_EPSILON));
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
                        assertThat(cursor.getCount()).isEqualTo(1);
                        while (cursor.moveToNext()) {
                            assertThat(cursor.getString(cursor.getColumnIndex(pair.second)))
                                    .isNull();
                        }
                    }
                });
    }

    private Map<String, List<ContentValues>> createFakeDataSourceV39() {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        // Source Table
        String sourceId = UUID.randomUUID().toString();
        ContentValues source = ContentValueFixtures.generateSourceContentValuesV39();
        source.put(MeasurementTables.SourceContract.ID, sourceId);
        tableRowsMap.put(MeasurementTables.SourceContract.TABLE, List.of(source));
        return tableRowsMap;
    }

    @Override
    int getTargetVersion() {
        return 40;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV40();
    }
}
