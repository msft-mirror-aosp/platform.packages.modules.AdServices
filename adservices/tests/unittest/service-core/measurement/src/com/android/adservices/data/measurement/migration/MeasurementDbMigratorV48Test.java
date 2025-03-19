/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV48Test extends MeasurementDbMigratorTestBase {

    @Test
    public void performMigration_v47ToV48WithData_maintainsDataIntegrity() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        47,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Map<String, List<ContentValues>> fakeData = createFakeDataV47();
        populateDb(db, fakeData);
        // Execution
        getTestSubject().performMigration(db, 47, 48);
        // Assertion
        MigrationTestHelper.verifyDataInDb(db, fakeData);
        // Check that new columns are initialized
        List<Pair<String, String>> tableAndNewColumnPairs = new ArrayList<>();
        tableAndNewColumnPairs.add(
                new Pair<>(
                        MeasurementTables.CountUniqueReportingContract.TABLE,
                        MeasurementTables.CountUniqueReportingContract.DEBUG_REPORT_STATUS));
        tableAndNewColumnPairs.add(
                new Pair<>(
                        MeasurementTables.CountUniqueReportingContract.TABLE,
                        MeasurementTables.CountUniqueReportingContract.ENROLLMENT_ID));
        tableAndNewColumnPairs.add(
                new Pair<>(
                        MeasurementTables.CountUniqueReportingContract.TABLE,
                        MeasurementTables.CountUniqueReportingContract.CONTRIBUTION_VALUE));
        tableAndNewColumnPairs.add(
                new Pair<>(
                        MeasurementTables.CountUniqueReportingContract.TABLE,
                        MeasurementTables.CountUniqueReportingContract.CONTRIBUTION_TIME));

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

    private Map<String, List<ContentValues>> createFakeDataV47() {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();

        List<ContentValues> metadataRows = getCountUniqueMetadataRows();
        tableRowsMap.put(MeasurementTables.CountUniqueMetadataContract.TABLE, metadataRows);

        List<ContentValues> reportingRows = getCountUniqueReportingRows();
        tableRowsMap.put(MeasurementTables.CountUniqueReportingContract.TABLE, reportingRows);
        return tableRowsMap;
    }

    @NotNull
    private static List<ContentValues> getCountUniqueMetadataRows() {
        List<ContentValues> metadataRows = new ArrayList<>();
        ContentValues metadata = ContentValueFixtures.generateCountUniqueMetadataV47();
        metadataRows.add(metadata);
        return metadataRows;
    }

    @NotNull
    private static List<ContentValues> getCountUniqueReportingRows() {
        List<ContentValues> reportingRows = new ArrayList<>();
        ContentValues report = ContentValueFixtures.generateCountUniqueReportingV46();
        report.put(
                MeasurementTables.CountUniqueReportingContract.REPORT_ID,
                UUID.randomUUID().toString());
        reportingRows.add(report);
        return reportingRows;
    }

    @Override
    int getTargetVersion() {
        return 48;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV48();
    }
}
