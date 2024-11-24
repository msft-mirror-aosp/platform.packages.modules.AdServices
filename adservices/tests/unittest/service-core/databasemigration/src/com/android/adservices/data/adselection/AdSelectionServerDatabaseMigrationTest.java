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

package com.android.adservices.data.adselection;

import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.database.Cursor;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.Objects;

public final class AdSelectionServerDatabaseMigrationTest extends AdServicesUnitTestCase {
    private static final String QUERY_TABLES_FROM_SQL_MASTER =
            "SELECT * FROM sqlite_master WHERE type='table' AND name='%s';";
    private static final String COLUMN_NAME_NAME = "name";
    private static final String TEST_DB = "migration-test";
    private static final Instrumentation INSTRUMENTATION =
            InstrumentationRegistry.getInstrumentation();

    @Rule(order = 1)
    public MigrationTestHelper helper =
            new MigrationTestHelper(INSTRUMENTATION, AdSelectionServerDatabase.class);

    @Test
    public void testMigration1To2() throws IOException {
        String reportingUrisTable = "auction_server_ad_selection";

        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 1);
        Cursor c = db.query(String.format(QUERY_TABLES_FROM_SQL_MASTER, reportingUrisTable));
        assertThat(c.getCount()).isEqualTo(0);

        // Re-open the database with version 2 and provide MIGRATION_1_2 as the migration process.
        db = helper.runMigrationsAndValidate(TEST_DB, 2, true);
        c = db.query(String.format(QUERY_TABLES_FROM_SQL_MASTER, reportingUrisTable));
        assertThat(c.getCount()).isEqualTo(1);
        c.moveToFirst();
        assertThat(c.getString(c.getColumnIndex(COLUMN_NAME_NAME))).isEqualTo(reportingUrisTable);
    }

    @Test
    public void testMigration2To3() throws IOException {
        String auctionServerAdSelection = "auction_server_ad_selection";
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 2);
        Cursor cursor =
                db.query(String.format(QUERY_TABLES_FROM_SQL_MASTER, auctionServerAdSelection));
        // The table should already exist
        assertThat(cursor.getCount()).isEqualTo(1);

        // Re-open the database with version 3 and provide MIGRATION_2_3 as the migration process.
        db = helper.runMigrationsAndValidate(TEST_DB, 3, true);
        cursor = db.query(String.format(QUERY_TABLES_FROM_SQL_MASTER, auctionServerAdSelection));
        assertThat(cursor.getCount()).isEqualTo(1);
        cursor.moveToFirst();

        assertThat(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_NAME)))
                .isEqualTo(auctionServerAdSelection);

        cursor = db.query("PRAGMA table_info(encryption_context)");
        boolean creationInstantColumnExists = false;
        if (cursor.moveToFirst()) {
            do {
                if (Objects.equals(cursor.getString(1), "creation_instant")) {
                    creationInstantColumnExists = true;
                    break;
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        assertThat(creationInstantColumnExists).isTrue();
    }

    @Test
    public void testMigration3To4() throws IOException {
        String protectedServersEncryptionConfigTable = "protected_servers_encryption_config";

        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 3);
        Cursor c =
                db.query(
                        String.format(
                                QUERY_TABLES_FROM_SQL_MASTER,
                                protectedServersEncryptionConfigTable));
        assertThat(c.getCount()).isEqualTo(0);

        // Re-open the database with version 4 and provide MIGRATION_3_4 as the migration process.
        db = helper.runMigrationsAndValidate(TEST_DB, 4, true);
        c =
                db.query(
                        String.format(
                                QUERY_TABLES_FROM_SQL_MASTER,
                                protectedServersEncryptionConfigTable));
        assertThat(c.getCount()).isEqualTo(1);
        c.moveToFirst();
        assertThat(c.getString(c.getColumnIndex(COLUMN_NAME_NAME)))
                .isEqualTo(protectedServersEncryptionConfigTable);
    }

    @Test
    public void testMigration4To5() throws IOException {
        String auctionServerAdSelection = "auction_server_ad_selection";
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 4);
        Cursor cursor =
                db.query(String.format(QUERY_TABLES_FROM_SQL_MASTER, auctionServerAdSelection));
        // The table should already exist
        assertThat(cursor.getCount()).isEqualTo(1);

        // Re-open the database with version 5 and provide MIGRATION_4_5 as the migration process.
        db = helper.runMigrationsAndValidate(TEST_DB, 5, true);
        cursor = db.query(String.format(QUERY_TABLES_FROM_SQL_MASTER, auctionServerAdSelection));
        assertThat(cursor.getCount()).isEqualTo(1);
        cursor.moveToFirst();

        assertThat(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_NAME)))
                .isEqualTo(auctionServerAdSelection);

        cursor = db.query("PRAGMA table_info(encryption_context)");
        boolean hasMediaTypeChangedColumnExists = false;
        if (cursor.moveToFirst()) {
            do {
                if (Objects.equals(cursor.getString(1), "has_media_type_changed")) {
                    hasMediaTypeChangedColumnExists = true;
                    break;
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        assertThat(hasMediaTypeChangedColumnExists).isTrue();
    }
}
