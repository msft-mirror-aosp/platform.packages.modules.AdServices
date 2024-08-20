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

package com.android.adservices.data;

import android.database.Cursor;

import androidx.sqlite.db.SupportSQLiteDatabase;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

/** Helper reading table schema from SQLite database. */
class SqliteSchemaExtractor {

    static Map<String, Map<String, SqliteColumnInfo>> getTableSchema(SupportSQLiteDatabase db) {
        Cursor c =
                db.query(
                        "SELECT name,sql FROM sqlite_master WHERE type='table' AND name not in"
                                + " ('room_master_table', 'android_metadata', 'sqlite_sequence')");
        c.moveToFirst();
        ImmutableMap.Builder<String, Map<String, SqliteColumnInfo>> tables =
                new ImmutableMap.Builder<>();
        do {
            String name = c.getString(0);
            tables.put(name, extractTableSchema(db, name));
        } while (c.moveToNext());
        return tables.build();
    }

    /**
     * Extract table schema from db using pragma table info.
     *
     * @param db the DB to extract schema from
     * @param tableName table name to get schema
     * @return Table schema mapping by column name
     * @see <a href="https://www.sqlite.org/pragma.html#pragma_table_info">SQLite pragma table
     *     info</a>
     */
    private static Map<String, SqliteColumnInfo> extractTableSchema(
            SupportSQLiteDatabase db, String tableName) {
        Cursor c =
                db.query(
                        "SELECT name,type,`notnull`,dflt_value,pk FROM pragma_table_info('"
                                + tableName
                                + "')");
        c.moveToFirst();
        ImmutableMap.Builder<String, SqliteColumnInfo> columnInfos = new ImmutableMap.Builder<>();
        do {
            String name = c.getString(0);
            String type = c.getString(1);
            boolean notNull = c.getInt(2) == 1;
            Object defaultValue = extractDefaultValue(type, c);
            int primaryKey = c.getInt(4);
            columnInfos.put(
                    name,
                    SqliteColumnInfo.builder()
                            .setName(name)
                            .setType(type)
                            .setNotnull(notNull)
                            .setDefaultValue(defaultValue)
                            .setPrimaryKey(primaryKey)
                            .build());
        } while (c.moveToNext());
        return columnInfos.build();
    }

    private static Object extractDefaultValue(String type, Cursor c) {
        if (c.isNull(3)) {
            return null;
        }
        switch (type) {
            case "TEXT":
                return c.getString(3);
            case "BLOB":
                return c.getBlob(3);
            case "INTEGER":
                return c.getInt(3);
            case "REAL":
                return c.getDouble(3);
            default:
                throw new IllegalArgumentException("Unknown SQLite type.");
        }
    }
}
