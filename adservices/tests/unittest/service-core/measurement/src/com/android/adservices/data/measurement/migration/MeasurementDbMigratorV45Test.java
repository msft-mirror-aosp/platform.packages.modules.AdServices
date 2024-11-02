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
public class MeasurementDbMigratorV45Test extends MeasurementDbMigratorTestBase {
    @Test
    public void performMigration_v44ToV45WithData_maintainsDataIntegrity() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        44,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Map<String, List<ContentValues>> fakeData = createFakeDataV44();
        populateDb(db, fakeData);

        // Execution
        getTestSubject().performMigration(db, /* oldVersion= */ 44, /* newVersion= */ 45);

        // Assertion
        MigrationTestHelper.verifyDataInDb(db, fakeData);

        // Check that new columns are initialized
        validateNewTextColumn(
                db, MeasurementTables.TriggerContract.TABLE, TriggerContract.NAMED_BUDGETS, null);
        assertThat(
                        doesTableExistAndColumnCountMatch(
                                db,
                                MeasurementTables.SourceNamedBudgetContract.TABLE,
                                /* columnCount= */ 4))
                .isTrue();
    }

    private Map<String, List<ContentValues>> createFakeDataV44() {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();

        List<ContentValues> triggerRows = getTriggerRows(UUID.randomUUID().toString());
        tableRowsMap.put(MeasurementTables.TriggerContract.TABLE, triggerRows);

        return tableRowsMap;
    }

    @NotNull
    private static List<ContentValues> getTriggerRows(String triggerId) {
        List<ContentValues> triggerRows = new ArrayList<>();
        ContentValues trigger = ContentValueFixtures.generateTriggerContentValuesV42();
        trigger.put(MeasurementTables.TriggerContract.ID, triggerId);
        triggerRows.add(trigger);
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

    @Override
    int getTargetVersion() {
        return 45;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV45();
    }
}
