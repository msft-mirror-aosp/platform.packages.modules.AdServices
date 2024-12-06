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

package com.android.adservices.data.customaudience;

import static android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL;

import static com.android.adservices.data.customaudience.CustomAudienceDatabase.MIGRATION_7_8;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.app.Instrumentation;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;

import androidx.room.testing.MigrationTestHelper;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.Objects;

public final class CustomAudienceDatabaseMigrationTest extends AdServicesUnitTestCase {
    private static final String TEST_DB = "migration-test";
    private static final Instrumentation INSTRUMENTATION =
            InstrumentationRegistry.getInstrumentation();

    private static final String COLUMN_NAME_NAME = "name";

    private static final String QUERY_TABLES_FROM_SQL_MASTER =
            "SELECT * FROM sqlite_master WHERE type='table' AND name='%s';";

    @Rule(order = 1)
    public MigrationTestHelper helper =
            new MigrationTestHelper(INSTRUMENTATION, CustomAudienceDatabase.class);

    @Test
    public void testMigration2To3() throws IOException {
        final String customAudienceOverrideTable = "custom_audience_overrides";
        try (SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 2)) {
            ContentValues contentValuesV2 = new ContentValues();
            contentValuesV2.put("owner", CustomAudienceFixture.VALID_OWNER);
            contentValuesV2.put("buyer", CommonFixture.VALID_BUYER_1.toString());
            contentValuesV2.put("name", CustomAudienceFixture.VALID_NAME);
            contentValuesV2.put("app_package_name", CommonFixture.TEST_PACKAGE_NAME_1);
            contentValuesV2.put("bidding_logic", "Whatever js");
            contentValuesV2.put("trusted_bidding_data", "a whatever json");
            db.insert(customAudienceOverrideTable, CONFLICT_FAIL, contentValuesV2);
        }
        // Re-open the database with version 3.
        try (SupportSQLiteDatabase db = helper.runMigrationsAndValidate(TEST_DB, 3, true)) {
            Cursor c = db.query("SELECT * FROM " + customAudienceOverrideTable);
            assertThat(c.getCount()).isEqualTo(1);
            c.moveToFirst();
            int biddingLogicVersionIndex = c.getColumnIndex("bidding_logic_version");
            assertThat(c.isNull(biddingLogicVersionIndex)).isTrue();

            ContentValues contentValuesV3 = new ContentValues();
            contentValuesV3.put("owner", CustomAudienceFixture.VALID_OWNER);
            contentValuesV3.put("buyer", CommonFixture.VALID_BUYER_2.toString());
            contentValuesV3.put("name", CustomAudienceFixture.VALID_NAME);
            contentValuesV3.put("app_package_name", CommonFixture.TEST_PACKAGE_NAME_2);
            contentValuesV3.put("bidding_logic", "Whatever js");
            contentValuesV3.put("bidding_logic_version", 2L);
            contentValuesV3.put("trusted_bidding_data", "a whatever json");
            db.insert(customAudienceOverrideTable, CONFLICT_FAIL, contentValuesV3);
            c =
                    db.query(
                            "SELECT * FROM "
                                    + customAudienceOverrideTable
                                    + " WHERE buyer = '"
                                    + CommonFixture.VALID_BUYER_2
                                    + "'");
            assertThat(c.getCount()).isEqualTo(1);
            c.moveToFirst();
            assertThat(c.getLong(c.getColumnIndex("bidding_logic_version"))).isEqualTo(2L);
            assertThat(c.getString(c.getColumnIndex("app_package_name")))
                    .isEqualTo(CommonFixture.TEST_PACKAGE_NAME_2);
        }
    }

    @Test
    public void testMigration5To6() throws IOException {
        String customAudienceTable = "custom_audience";

        String auctionServerRequestFlagsColumnName = "auction_server_request_flags";
        String debuggableColumnName = "debuggable";

        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 5);
        Cursor cursor = db.query(String.format(QUERY_TABLES_FROM_SQL_MASTER, customAudienceTable));
        // The table should already exist
        assertThat(cursor.getCount()).isEqualTo(1);

        cursor = db.query("PRAGMA table_info(custom_audience)");
        // Columns should not yet exist
        assertThat(checkIfColumnExists(cursor, auctionServerRequestFlagsColumnName)).isFalse();
        cursor.moveToFirst();
        assertThat(checkIfColumnExists(cursor, debuggableColumnName)).isFalse();

        // Re-open the database with version 6
        db = helper.runMigrationsAndValidate(TEST_DB, 6, true);
        cursor = db.query(String.format(QUERY_TABLES_FROM_SQL_MASTER, customAudienceTable));
        assertThat(cursor.getCount()).isEqualTo(1);
        cursor.moveToFirst();

        assertThat(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_NAME)))
                .isEqualTo(customAudienceTable);

        cursor = db.query("PRAGMA table_info(custom_audience)");
        // Columns should now exist
        assertThat(checkIfColumnExists(cursor, auctionServerRequestFlagsColumnName)).isTrue();
        cursor.moveToFirst();
        assertThat(checkIfColumnExists(cursor, debuggableColumnName)).isTrue();

        cursor.close();
    }

    @Test
    public void testAutoMigration7To8() throws IOException {
        // Create DB with v7.
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 7);

        // The table added in v7 should already exist.
        Cursor cursor =
                db.query(
                        String.format(
                                QUERY_TABLES_FROM_SQL_MASTER,
                                DBScheduledCustomAudienceUpdate.TABLE_NAME));
        assertThat(cursor.getCount()).isEqualTo(1);
        cursor.close();

        // Column added in v8 should not exist, yet.
        TableInfo info = TableInfo.read(db, DBScheduledCustomAudienceUpdate.TABLE_NAME);
        assertThat(info.columns).doesNotContainKey("is_debuggable");

        // Close DB before attempting migrations.
        db.close();

        // Attempt to re-open the database with v8 auto migration and assert success.
        db = helper.runMigrationsAndValidate(TEST_DB, 8, true);
        info = TableInfo.read(db, DBScheduledCustomAudienceUpdate.TABLE_NAME);
        assertThat(info.columns).containsKey("is_debuggable");
        db.close();
    }

    @Test
    public void testManualMigration7To8() throws IOException {
        // Create DB with v7.
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 7);

        // The table added in v7 should already exist.
        Cursor cursor =
                db.query(
                        String.format(
                                QUERY_TABLES_FROM_SQL_MASTER,
                                DBScheduledCustomAudienceUpdate.TABLE_NAME));
        assertThat(cursor.getCount()).isEqualTo(1);
        cursor.close();

        // Column added in v8 should not exist, yet.
        TableInfo info = TableInfo.read(db, DBScheduledCustomAudienceUpdate.TABLE_NAME);
        assertThat(info.columns).doesNotContainKey("is_debuggable");

        // Close DB before attempting migrations.
        db.close();

        // Attempt to re-open the database with v8 manual migration and assert success.
        db = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8);
        info = TableInfo.read(db, DBScheduledCustomAudienceUpdate.TABLE_NAME);
        assertThat(info.columns).containsKey("is_debuggable");
        db.close();
    }

    // Explicitly reproducing issue from b/336515306 caused due to "is_debuggable" column already
    // existing before migration and testing the fix.
    @Test
    public void testManualMigration7To8_withDuplicateColumn() throws IOException {
        // Create DB with v7.
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 7);

        // The table added in v7 should already exist.
        Cursor cursor =
                db.query(
                        String.format(
                                QUERY_TABLES_FROM_SQL_MASTER,
                                DBScheduledCustomAudienceUpdate.TABLE_NAME));
        assertThat(cursor.getCount()).isEqualTo(1);
        cursor.close();

        // Column added in v8 should not exist, yet.
        TableInfo info = TableInfo.read(db, DBScheduledCustomAudienceUpdate.TABLE_NAME);
        assertThat(info.columns).doesNotContainKey("is_debuggable");

        // Add v8 column before migration, effectively sabotaging v8 auto migration.
        db.execSQL(
                "ALTER TABLE `scheduled_custom_audience_update` ADD COLUMN `is_debuggable` INTEGER"
                        + " NOT NULL DEFAULT false");
        info = TableInfo.read(db, DBScheduledCustomAudienceUpdate.TABLE_NAME);
        assertThat(info.columns).containsKey("is_debuggable");

        // Close DB before attempting migrations.
        db.close();

        // Attempt to re-open the database with v8 auto migration and assert failure.
        Exception thrown =
                assertThrows(
                        SQLiteException.class,
                        () -> helper.runMigrationsAndValidate(TEST_DB, 8, true));
        assertThat(thrown).hasMessageThat().contains("duplicate column name: is_debuggable");

        // Attempt to re-open the database with v8 manual migration and assert success.
        db = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8);
        info = TableInfo.read(db, DBScheduledCustomAudienceUpdate.TABLE_NAME);
        assertThat(info.columns).containsKey("is_debuggable");
        db.close();
    }

    @Test
    public void testAutoMigration9To10() throws IOException {
        // Create DB with v9.
        String scheduledCustomAudienceUpdateTable = "scheduled_custom_audience_update";
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 9);

        ContentValues contentValues = new ContentValues();
        contentValues.put("update_id", 1L);
        contentValues.put("owner", "");
        contentValues.put("buyer", "");
        contentValues.put("update_uri", "");
        contentValues.put("creation_time", "");
        contentValues.put("scheduled_time", "");
        contentValues.put("is_debuggable", false);
        db.insert(scheduledCustomAudienceUpdateTable, CONFLICT_FAIL, contentValues);

        // The table added in v9 should already exist.
        Cursor cursor =
                db.query(
                        String.format(
                                QUERY_TABLES_FROM_SQL_MASTER,
                                DBScheduledCustomAudienceUpdate.TABLE_NAME));
        assertThat(cursor.getCount()).isEqualTo(1);
        cursor.close();

        // Column added in v10 should not exist, yet.
        TableInfo info = TableInfo.read(db, DBScheduledCustomAudienceUpdate.TABLE_NAME);
        assertThat(info.columns).doesNotContainKey("allow_schedule_in_response");

        // Table added in v10 should not exist, yet.
        cursor =
                db.query(
                        String.format(
                                QUERY_TABLES_FROM_SQL_MASTER, DBCustomAudienceToLeave.TABLE_NAME));
        assertThat(cursor.getCount()).isEqualTo(0);
        cursor.close();

        // Close DB before attempting migrations.
        db.close();

        // Attempt to re-open the database with v10 auto migration.
        db = helper.runMigrationsAndValidate(TEST_DB, 10, true);

        // Check if the new column is created and assert success.
        info = TableInfo.read(db, DBScheduledCustomAudienceUpdate.TABLE_NAME);
        assertThat(info.columns).containsKey("allow_schedule_in_response");

        // Table added in v10 should exist.
        cursor =
                db.query(
                        String.format(
                                QUERY_TABLES_FROM_SQL_MASTER, DBCustomAudienceToLeave.TABLE_NAME));
        assertThat(cursor.getCount()).isEqualTo(1);
        cursor.close();

        // Check if value of the new column has the default value for already existing columns and
        // assert success.
        cursor = db.query("SELECT * FROM " + scheduledCustomAudienceUpdateTable);
        cursor.moveToFirst();
        assertThat(cursor.getInt(cursor.getColumnIndex("allow_schedule_in_response"))).isEqualTo(0);
        db.close();
    }

    @Test
    public void testAutoMigration10To11() throws IOException {
        // Create DB with v10.
        String componentAdDataTable = "component_ad_data";
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 10);

        // The table should not exist in v10
        Cursor cursor = db.query(String.format(QUERY_TABLES_FROM_SQL_MASTER, componentAdDataTable));
        assertThat(cursor.getCount()).isEqualTo(0);
        cursor.close();

        // Close DB before attempting migrations.
        db.close();

        // Attempt to re-open the database with v11 auto migration.
        db = helper.runMigrationsAndValidate(TEST_DB, 11, true);

        // The table should exist in v11
        cursor = db.query(String.format(QUERY_TABLES_FROM_SQL_MASTER, componentAdDataTable));
        assertThat(cursor.getCount()).isEqualTo(1);
        cursor.close();

        db.close();
    }

    private boolean checkIfColumnExists(Cursor cursor, String name) {
        boolean columnExists = false;
        if (cursor.moveToFirst()) {
            do {
                if (Objects.equals(cursor.getString(1), name)) {
                    columnExists = true;
                    break;
                }
            } while (cursor.moveToNext());
        }
        return columnExists;
    }
}
