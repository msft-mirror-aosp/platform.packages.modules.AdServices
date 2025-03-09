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

import static com.android.adservices.common.DbTestUtil.doesTableExistAndColumnCountMatch;
import static com.android.adservices.common.DbTestUtil.getDbHelperForTest;
import static com.android.adservices.data.measurement.migration.MigrationTestHelper.populateDb;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.android.adservices.data.measurement.MeasurementDbHelper;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.service.measurement.CountUniqueMetadata;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV47Test extends MeasurementDbMigratorTestBase {
    @Test
    public void performMigration_v46ToV47WithData_maintainsDataIntegrity() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        46,
                        getDbHelperForTest());

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Execution
        getTestSubject().performMigration(db, /* oldVersion= */ 46, /* newVersion= */ 47);

        // Populate Table with Data
        CountUniqueMetadata.Builder builder = new CountUniqueMetadata.Builder();
        builder.setKey("key1");
        builder.setValue(1);
        builder.setReportingOrigin(Uri.parse("https://test.bar/count-unique-reporting"));
        builder.setExpirationTime(1726874188156L);

        CountUniqueMetadata metadata = builder.build();
        Map<String, List<ContentValues>> fakeData = createFakeDataV47(metadata);
        populateDb(db, fakeData);

        // Check that new columns are initialized
        validateNewTextColumn(
                db,
                MeasurementTables.CountUniqueMetadataContract.TABLE,
                MeasurementTables.CountUniqueMetadataContract.KEY,
                metadata.getKey());
        validateNewTextColumn(
                db,
                MeasurementTables.CountUniqueMetadataContract.TABLE,
                MeasurementTables.CountUniqueMetadataContract.VALUE,
                metadata.getValue().toString());
        validateNewTextColumn(
                db,
                MeasurementTables.CountUniqueMetadataContract.TABLE,
                MeasurementTables.CountUniqueMetadataContract.REPORTING_ORIGIN,
                metadata.getReportingOrigin().toString());
        validateNewTextColumn(
                db,
                MeasurementTables.CountUniqueMetadataContract.TABLE,
                MeasurementTables.CountUniqueMetadataContract.EXPIRATION_TIME,
                metadata.getExpirationTime().toString());

        // Validation
        assertThat(
                        doesTableExistAndColumnCountMatch(
                                db,
                                MeasurementTables.CountUniqueMetadataContract.TABLE,
                                /* columnCount= */ 4))
                .isTrue();
    }

    private Map<String, List<ContentValues>> createFakeDataV47(CountUniqueMetadata metadata) {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        List<ContentValues> countUniqueMetadataRows = getRows(metadata);
        tableRowsMap.put(
                MeasurementTables.CountUniqueMetadataContract.TABLE, countUniqueMetadataRows);
        return tableRowsMap;
    }

    @NotNull
    private static List<ContentValues> getRows(CountUniqueMetadata metadataObj) {
        List<ContentValues> countUniqueMetadataRows = new ArrayList<>();
        ContentValues metadata = new ContentValues();
        metadata.put(MeasurementTables.CountUniqueMetadataContract.KEY, metadataObj.getKey());
        metadata.put(MeasurementTables.CountUniqueMetadataContract.VALUE, metadataObj.getValue());
        metadata.put(
                MeasurementTables.CountUniqueMetadataContract.REPORTING_ORIGIN,
                metadataObj.getReportingOrigin().toString());
        metadata.put(
                MeasurementTables.CountUniqueMetadataContract.EXPIRATION_TIME,
                metadataObj.getExpirationTime());
        countUniqueMetadataRows.add(metadata);
        return countUniqueMetadataRows;
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
        return 47;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV47();
    }
}
