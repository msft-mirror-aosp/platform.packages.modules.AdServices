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

package com.android.adservices.data.shared.migration;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

import com.android.adservices.data.shared.SharedDbSchemaTrail;

import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MigrationTestHelper {
    private static final float FLOAT_COMPARISON_EPSILON = 0.00005f;

    public static SQLiteDatabase createReferenceDbAtVersion(
            Context context, String dbName, int version) {
        EmptySqliteOpenHelper dbHelper = new EmptySqliteOpenHelper(context, dbName);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        for (String createStatement :
                SharedDbSchemaTrail.getCreateTableStatementsByVersion(version)) {
            db.execSQL(createStatement);
        }

        return db;
    }

    /** Populate fate into DB. */
    public static void populateDb(SQLiteDatabase db, Map<String, List<ContentValues>> fakeData) {
        fakeData.forEach(
                (table, rows) ->
                        rows.forEach(
                                (row) -> assertThat(db.insert(table, null, row)).isNotEqualTo(-1)));
    }

    /** Verify data in DB is as expected. */
    public static void verifyDataInDb(
            SQLiteDatabase newDb, Map<String, List<ContentValues>> fakeData) {
        verifyDataInDb(newDb, fakeData, ImmutableMap.of(), ImmutableMap.of());
    }

    /**
     * @param newDb Database from where the data should be read
     * @param fakeData Data to verified
     * @param droppedKeys Columns that have been dropped
     * @param columnsToBeSkipped Columns that shouldn't be match and need custom matching logic.
     */
    public static void verifyDataInDb(
            SQLiteDatabase newDb,
            Map<String, List<ContentValues>> fakeData,
            Map<String, Set<String>> droppedKeys,
            Map<String, Set<String>> columnsToBeSkipped) {
        fakeData.forEach(
                (table, rows) -> {
                    List<ContentValues> newRows = new ArrayList<>();
                    try (Cursor cursor =
                            newDb.query(table, null, null, null, null, null, null, null)) {
                        while (cursor.moveToNext()) {
                            newRows.add(cursorRowToContentValues(cursor));
                        }
                    }

                    assertWithMessage("%s row count matching failed", table)
                            .that(rows.size())
                            .isEqualTo(newRows.size());

                    for (int i = 0; i < rows.size(); i++) {
                        ContentValues expected = rows.get(i);
                        ContentValues actual = newRows.get(i);
                        assertWithMessage(
                                        "Table: %s, Row: %s, Expected: %s, Actual: %s",
                                        table, i, expected, actual)
                                .that(
                                        doContentValueMatch(
                                                expected,
                                                actual,
                                                droppedKeys.getOrDefault(table, Set.of()),
                                                columnsToBeSkipped.getOrDefault(table, Set.of())))
                                .isTrue();
                    }
                });
    }

    /**
     * Create our own method instead of using DatabaseUtils.cursorRowToContentValues because that
     * one reads every type from the cursor as a String.
     */
    public static ContentValues cursorRowToContentValues(Cursor cursor) {
        String[] columns = cursor.getColumnNames();
        ContentValues values = new ContentValues();
        for (int i = 0; i < columns.length; i++) {
            switch (cursor.getType(i)) {
                case Cursor.FIELD_TYPE_FLOAT:
                    values.put(columns[i], cursor.getDouble(i));
                    break;
                case Cursor.FIELD_TYPE_INTEGER:
                    values.put(columns[i], cursor.getLong(i));
                    break;
                case Cursor.FIELD_TYPE_STRING:
                default:
                    values.put(columns[i], cursor.getString(i));
                    break;
            }
        }
        return values;
    }

    // 'Expected' here are the original "fake values" seeding the datastore. If we have a datastore
    // upgrade that's dropping columns, the "fake values" may still contain those values.
    private static boolean doContentValueMatch(
            ContentValues expected,
            ContentValues actual,
            Set<String> droppedKeys,
            Set<String> columnsToBeSkipped) {
        for (Map.Entry<String, Object> expectedElement : expected.valueSet()) {
            String expectedKey = expectedElement.getKey();
            Object expectedValue = expectedElement.getValue();
            if (droppedKeys.contains(expectedKey)) {
                if (actual.containsKey(expectedKey)) {
                    return false;
                }
                continue;
            }
            if (columnsToBeSkipped.contains(expectedKey)) {
                continue;
            }
            if (!actual.containsKey(expectedKey)) {
                return false;
            }
            Object actualValue = actual.get(expectedKey);
            if (expectedValue.equals(actualValue)) {
                continue;
            }
            if (expectedValue instanceof Number
                    && nearlyEqual(
                            ((Number) expectedValue).floatValue(),
                            ((Number) actualValue).floatValue(),
                            FLOAT_COMPARISON_EPSILON)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean nearlyEqual(float a, float b, float epsilon) {
        if (a == b) {
            return true;
        }
        final float absA = Math.abs(a);
        final float absB = Math.abs(b);
        final float diff = Math.abs(a - b);

        return diff / (absA + absB) < epsilon;
    }

    private static class EmptySqliteOpenHelper extends SQLiteOpenHelper {
        private static final int DB_VERSION = 1;

        private EmptySqliteOpenHelper(@NonNull Context context, @NonNull String dbName) {
            super(context, dbName, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            // No-op
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // No-op
        }
    }
}
