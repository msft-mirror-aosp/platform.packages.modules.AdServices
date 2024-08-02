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
import android.util.Pair;

import com.android.adservices.data.measurement.MeasurementDbHelper;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.service.measurement.Trigger;

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
public class MeasurementDbMigratorV32Test extends MeasurementDbMigratorTestBase {
    @Test
    public void performMigration_v31ToV32WithData_maintainsDataIntegrity() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        31,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String trigger1Id = UUID.randomUUID().toString();
        Map<String, List<ContentValues>> fakeData = createFakeDataSourceV31(trigger1Id);
        populateDb(db, fakeData);
        // Execution
        getTestSubject().performMigration(db, 31, 32);
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
                        MeasurementTables.TriggerContract
                                .AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG));
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
                        assertEquals(2, cursor.getCount());
                        while (cursor.moveToNext()) {
                            assertEquals(
                                    Trigger.SourceRegistrationTimeConfig.INCLUDE.name(),
                                    cursor.getString(cursor.getColumnIndex(pair.second)));
                        }
                    }
                });
    }

    private Map<String, List<ContentValues>> createFakeDataSourceV31(String trigger1Id) {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        List<ContentValues> triggerRows = new ArrayList<>();
        ContentValues trigger1 = ContentValueFixtures.generateTriggerContentValuesV31();
        trigger1.put(MeasurementTables.TriggerContract.ID, trigger1Id);
        triggerRows.add(trigger1);
        triggerRows.add(ContentValueFixtures.generateTriggerContentValuesV31());
        tableRowsMap.put(MeasurementTables.TriggerContract.TABLE, triggerRows);
        return tableRowsMap;
    }

    @Override
    int getTargetVersion() {
        return 32;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV32();
    }
}
