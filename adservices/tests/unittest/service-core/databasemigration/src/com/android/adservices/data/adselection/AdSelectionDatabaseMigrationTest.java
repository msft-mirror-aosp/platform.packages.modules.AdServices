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
import java.util.List;

public final class AdSelectionDatabaseMigrationTest extends AdServicesUnitTestCase {
    private static final String QUERY_TABLES_FROM_SQL_MASTER =
            "SELECT * FROM sqlite_master WHERE type='table' AND name='%s';";
    private static final String COLUMN_NAME_NAME = "name";
    private static final String TEST_DB = "migration-test";
    private static final Instrumentation INSTRUMENTATION =
            InstrumentationRegistry.getInstrumentation();

    @Rule(order = 1)
    public MigrationTestHelper helper =
            new MigrationTestHelper(INSTRUMENTATION, AdSelectionDatabase.class);

    @Test
    public void testMigration1To2() throws IOException {
        String adSelectionOverridesTable = "ad_selection_from_outcomes_overrides";

        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 1);
        validateTableDoesNotExistsInDatabase(db, adSelectionOverridesTable);

        // Re-open the database with version 2 and provide MIGRATION_1_2 as the migration process.
        db = helper.runMigrationsAndValidate(TEST_DB, 2, true);
        validateTableExistsInDatabase(db, adSelectionOverridesTable);
    }

    @Test
    public void testMigration2To3() throws IOException {
        String registeredAdInteractionsTable = "registered_ad_interactions";

        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 2);
        validateTableDoesNotExistsInDatabase(db, registeredAdInteractionsTable);

        // Re-open the database with version 3 and provide MIGRATION_2_3 as the migration process.
        db = helper.runMigrationsAndValidate(TEST_DB, 3, true);
        validateTableExistsInDatabase(db, registeredAdInteractionsTable);
    }

    @Test
    public void testMigration3To4() throws IOException {
        String adSelectionTable = "ad_selection";

        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 3);
        validateTableExistsInDatabase(db, adSelectionTable);

        // Re-open the database with version 4 and provide MIGRATION_3_4 as the migration process.
        db = helper.runMigrationsAndValidate(TEST_DB, 4, true);
        validateTableExistsInDatabase(db, adSelectionTable);
    }

    @Test
    public void testMigration4To5() throws IOException {
        String adSelectionTable = "ad_selection";

        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 4);
        validateTableExistsInDatabase(db, adSelectionTable);

        // Re-open the database with version 4 and provide MIGRATION_4_5 as the migration process.
        db = helper.runMigrationsAndValidate(TEST_DB, 5, true);
        validateTableExistsInDatabase(db, adSelectionTable);
    }

    @Test
    public void testMigration6To7() throws IOException {
        String adSelectionTable = "ad_selection";
        String adSelectionInitializationTable = "ad_selection_initialization";
        String adSelectionResultTable = "ad_selection_result";
        String reportingDataTable = "reporting_data";

        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 6);
        // The ad selection table should already exist
        validateTableExistsInDatabase(db, adSelectionTable);
        validateTablesDoNotExistInDatabase(
                db,
                List.of(
                        adSelectionInitializationTable,
                        adSelectionResultTable,
                        reportingDataTable));

        // Re-open the database with version 7 and provide MIGRATION_6_7 as the migration process.
        db = helper.runMigrationsAndValidate(TEST_DB, 7, true);
        validateTablesExistsInDatabase(
                db,
                List.of(
                        adSelectionTable,
                        adSelectionInitializationTable,
                        adSelectionResultTable,
                        reportingDataTable));
    }

    @Test
    public void testMigration7To8() throws IOException {
        String adSelectionTable = "ad_selection";
        String reportingComputationInfoTable = "reporting_computation_info";

        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 7);
        // The ad selection table should already exist
        validateTableExistsInDatabase(db, adSelectionTable);
        validateTableDoesNotExistsInDatabase(db, reportingComputationInfoTable);

        // Re-open the database with version 8 and provide MIGRATION_7_8 as the migration process.
        db = helper.runMigrationsAndValidate(TEST_DB, 8, true);
        validateTableExistsInDatabase(db, adSelectionTable);
        validateTableExistsInDatabase(db, reportingComputationInfoTable);
    }

    @Test
    public void testMigration8To9() throws IOException {
        List<String> existingTables =
                List.of(
                        "ad_selection",
                        "buyer_decision_logic",
                        "ad_selection_overrides",
                        "ad_selection_from_outcomes_overrides",
                        "registered_ad_interactions",
                        "ad_selection_buyer_logic_overrides",
                        "reporting_data",
                        "ad_selection_initialization",
                        "ad_selection_result",
                        "reporting_computation_info");
        String consentedDebugConfigurationTable = "consented_debug_configuration";

        SupportSQLiteDatabase database = helper.createDatabase(TEST_DB, 8);
        validateTablesExistsInDatabase(database, existingTables);
        validateTableDoesNotExistsInDatabase(database, consentedDebugConfigurationTable);

        // Re-open the database with version 9 and provide MIGRATION_8_9 as the migration process.
        database = helper.runMigrationsAndValidate(TEST_DB, 9, true);
        validateTablesExistsInDatabase(database, existingTables);
        validateTableExistsInDatabase(database, consentedDebugConfigurationTable);
    }

    private void validateTablesExistsInDatabase(SupportSQLiteDatabase db, List<String> tableNames) {
        tableNames.forEach(tableName -> validateTableExistsInDatabase(db, tableName));
    }

    private void validateTableExistsInDatabase(SupportSQLiteDatabase db, String tableName) {
        Cursor cursor = db.query(String.format(QUERY_TABLES_FROM_SQL_MASTER, tableName));
        assertThat(cursor.getCount()).isEqualTo(1);
        cursor.moveToFirst();
        assertThat(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_NAME))).isEqualTo(tableName);
    }

    private void validateTablesDoNotExistInDatabase(
            SupportSQLiteDatabase db, List<String> tableNames) {
        tableNames.forEach(tableName -> validateTableDoesNotExistsInDatabase(db, tableName));
    }

    private void validateTableDoesNotExistsInDatabase(SupportSQLiteDatabase db, String tableName) {
        Cursor cursor = db.query(String.format(QUERY_TABLES_FROM_SQL_MASTER, tableName));
        assertThat(cursor.getCount()).isEqualTo(0);
    }
}
