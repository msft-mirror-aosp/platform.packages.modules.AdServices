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

package com.android.adservices.data.signals;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.database.Cursor;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.collect.ImmutableList;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class ProtectedSignalsDatabaseMigrationTest {
    private static final String TEST_DB = "migration-test";
    private static final Instrumentation INSTRUMENTATION =
            InstrumentationRegistry.getInstrumentation();

    @Rule
    public MigrationTestHelper helper =
            new MigrationTestHelper(INSTRUMENTATION, ProtectedSignalsDatabase.class);

    @Test
    public void testMigrate1To2() throws IOException {
        try (SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 1)) {
            List<String> tables = listTables(db);
            assertTrue(tables.contains(DBProtectedSignal.TABLE_NAME));
            assertFalse(tables.contains(DBEncoderLogicMetadata.TABLE_NAME));
            assertFalse(tables.contains(DBEncodedPayload.TABLE_NAME));
            assertFalse(tables.contains(DBEncoderEndpoint.TABLE_NAME));
        }
        // Re-open the database with version 3.
        try (SupportSQLiteDatabase db = helper.runMigrationsAndValidate(TEST_DB, 2, true)) {
            List<String> tables = listTables(db);
            assertTrue(tables.contains(DBProtectedSignal.TABLE_NAME));
            assertTrue(tables.contains(DBEncoderLogicMetadata.TABLE_NAME));
            assertTrue(tables.contains(DBEncodedPayload.TABLE_NAME));
            assertTrue(tables.contains(DBEncoderEndpoint.TABLE_NAME));
        }
    }

    private static List<String> listTables(SupportSQLiteDatabase db) {
        Cursor c = db.query("SELECT name FROM sqlite_master WHERE type='table' order by name");
        c.moveToFirst();
        ImmutableList.Builder<String> tables = new ImmutableList.Builder<>();
        do {
            tables.add(c.getString(0));
        } while (c.moveToNext());
        return tables.build();
    }
}
