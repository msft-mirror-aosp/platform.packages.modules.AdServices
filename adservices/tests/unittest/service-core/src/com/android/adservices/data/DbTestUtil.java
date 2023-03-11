/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.measurement.MeasurementDbHelper;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

public final class DbTestUtil {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String DATABASE_NAME_FOR_TEST = "adservices_test.db";
    private static final String MSMT_DATABASE_NAME_FOR_TEST = "adservices_msmt_test.db";

    private static DbHelper sSingleton;
    private static MeasurementDbHelper sMsmtSingleton;

    /** Erases all data from the table rows */
    public static void deleteTable(String tableName) {
        SQLiteDatabase db = getDbHelperForTest().safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        db.delete(tableName, /* whereClause= */ null, /* whereArgs= */ null);
    }

    /**
     * Create an instance of database instance for testing.
     *
     * @return a test database
     */
    public static DbHelper getDbHelperForTest() {
        synchronized (DbHelper.class) {
            if (sSingleton == null) {
                sSingleton =
                        new DbHelper(sContext, DATABASE_NAME_FOR_TEST, DbHelper.DATABASE_VERSION);
            }
            return sSingleton;
        }
    }

    public static MeasurementDbHelper getMeasurementDbHelperForTest() {
        synchronized (MeasurementDbHelper.class) {
            if (sMsmtSingleton == null) {
                sMsmtSingleton =
                        new MeasurementDbHelper(
                                sContext,
                                MSMT_DATABASE_NAME_FOR_TEST,
                                MeasurementDbHelper.CURRENT_DATABASE_VERSION,
                                getDbHelperForTest());
            }
            return sMsmtSingleton;
        }
    }

    /** Return true if table exists in the DB and column count matches. */
    public static boolean doesTableExistAndColumnCountMatch(
            SQLiteDatabase db, String tableName, int columnCount) {
        final Set<String> tableColumns = getTableColumns(db, tableName);
        int actualCol = tableColumns.size();
        Log.d("DbTestUtil_log_test,", " table name: " + tableName + " column count: " + actualCol);
        return tableColumns.size() == columnCount;
    }

    /** Returns column names of the table. */
    public static Set<String> getTableColumns(SQLiteDatabase db, String tableName) {
        String query =
                "select p.name from sqlite_master s "
                        + "join pragma_table_info(s.name) p "
                        + "where s.tbl_name = '"
                        + tableName
                        + "'";
        Cursor cursor = db.rawQuery(query, null);
        if (cursor == null) {
            throw new IllegalArgumentException("Cursor is null.");
        }

        ImmutableSet.Builder<String> tableColumns = ImmutableSet.builder();
        while (cursor.moveToNext()) {
            tableColumns.add(cursor.getString(0));
        }

        return tableColumns.build();
    }

    /** Return true if the given index exists in the DB. */
    public static boolean doesIndexExist(SQLiteDatabase db, String index) {
        String query = "SELECT * FROM sqlite_master WHERE type='index' and name='" + index + "'";
        Cursor cursor = db.rawQuery(query, null);
        return cursor != null && cursor.getCount() > 0;
    }

    public static boolean doesTableExist(SQLiteDatabase db, String table) {
        String query = "SELECT * FROM sqlite_master WHERE type='table' and name='" + table + "'";
        Cursor cursor = db.rawQuery(query, null);
        return cursor != null && cursor.getCount() > 0;
    }

    /** Return test database name */
    public static String getDatabaseNameForTest() {
        return DATABASE_NAME_FOR_TEST;
    }
}
