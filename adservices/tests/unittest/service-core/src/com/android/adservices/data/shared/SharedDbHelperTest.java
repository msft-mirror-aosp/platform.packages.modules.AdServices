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

package com.android.adservices.data.shared;

import static com.android.adservices.data.measurement.migration.MigrationTestHelper.populateDb;
import static com.android.adservices.data.measurement.migration.MigrationTestHelper.verifyDataInDb;
import static com.android.adservices.data.shared.migration.MigrationTestHelper.createReferenceDbAtVersion;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.DbTestUtil;
import com.android.adservices.data.DbHelper;
import com.android.adservices.data.DbHelperTest;
import com.android.adservices.data.DbHelperV1;
import com.android.adservices.data.enrollment.EnrollmentTables;
import com.android.adservices.data.shared.migration.ContentValueFixtures;
import com.android.adservices.data.shared.migration.SharedDbMigratorV2;
import com.android.adservices.data.shared.migration.SharedDbMigratorV3;
import com.android.adservices.data.shared.migration.SharedDbMigratorV4;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class SharedDbHelperTest {

    private static final String MIGRATION_DB_REFERENCE_NAME =
            "adservices_shared_db_migrate_reference.db";
    private static final String OLD_TEST_DB_NAME = "old_test_db.db";
    private static final String SHARED_DB_NAME = "adservices_shared_db_test.db";
    private static final int ENROLLMENT_OLD_DB_FINAL_VERSION = 7;
    protected static final Context sContext = ApplicationProvider.getApplicationContext();

    @Mock private Flags mMockFlags;
    private MockitoSession mStaticMockSession;

    @Before
    public void setup() {
        Stream.of(MIGRATION_DB_REFERENCE_NAME, OLD_TEST_DB_NAME, SHARED_DB_NAME)
                .map(sContext::getDatabasePath)
                .filter(File::exists)
                .forEach(File::delete);

        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .startMocking();

        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
    }

    @After
    public void teardown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testNewInstall() {
        SharedDbHelper sharedDbHelper =
                new SharedDbHelper(
                        sContext,
                        SHARED_DB_NAME,
                        SharedDbHelper.DATABASE_VERSION_V3,
                        DbTestUtil.getDbHelperForTest());
        SQLiteDatabase db = sharedDbHelper.safeGetWritableDatabase();
        SQLiteDatabase referenceLatestDb =
                createReferenceDbAtVersion(
                        sContext, MIGRATION_DB_REFERENCE_NAME, SharedDbHelper.DATABASE_VERSION_V3);
        DbTestUtil.assertDatabasesEqual(referenceLatestDb, db);
    }

    @Test
    public void testNewInstall_sharedDbV4() {
        SharedDbHelper sharedDbHelper =
                new SharedDbHelper(
                        sContext,
                        SHARED_DB_NAME,
                        SharedDbHelper.DATABASE_VERSION_V4,
                        DbTestUtil.getDbHelperForTest());
        SQLiteDatabase db = sharedDbHelper.safeGetWritableDatabase();
        SQLiteDatabase referenceLatestDb =
                createReferenceDbAtVersion(
                        sContext, MIGRATION_DB_REFERENCE_NAME, SharedDbHelper.DATABASE_VERSION_V4);
        DbTestUtil.assertDatabasesEqual(referenceLatestDb, db);
    }

    @Test
    public void testEnrollmentTableMigrationFromOldDatabase() {
        DbHelperV1 dbHelperV1 = new DbHelperV1(sContext, OLD_TEST_DB_NAME, 1);
        SQLiteDatabase db = dbHelperV1.safeGetWritableDatabase();

        assertEquals(1, db.getVersion());

        DbHelper dbHelper =
                new DbHelper(sContext, OLD_TEST_DB_NAME, ENROLLMENT_OLD_DB_FINAL_VERSION);
        SQLiteDatabase oldDb = dbHelper.safeGetWritableDatabase();

        assertEquals(ENROLLMENT_OLD_DB_FINAL_VERSION, oldDb.getVersion());

        SharedDbHelper sharedDbHelper =
                new SharedDbHelper(
                        sContext, SHARED_DB_NAME, SharedDbHelper.DATABASE_VERSION_V3, dbHelper);
        SQLiteDatabase actualMigratedDb = sharedDbHelper.safeGetWritableDatabase();

        SQLiteDatabase referenceLatestDb =
                createReferenceDbAtVersion(
                        sContext, MIGRATION_DB_REFERENCE_NAME, SharedDbHelper.DATABASE_VERSION_V3);
        DbTestUtil.assertDatabasesEqual(referenceLatestDb, actualMigratedDb);
        DbHelperTest.assertEnrollmentTableDoesNotExist(oldDb);
    }

    @Test
    public void testMigrationDataIntegrityToV1FromOldDatabase() {
        DbHelperV1 dbHelperV1 = new DbHelperV1(sContext, OLD_TEST_DB_NAME, 1);
        SQLiteDatabase db = dbHelperV1.safeGetWritableDatabase();

        assertEquals(1, db.getVersion());

        DbHelper dbHelper =
                new DbHelper(sContext, OLD_TEST_DB_NAME, ENROLLMENT_OLD_DB_FINAL_VERSION);
        SQLiteDatabase oldDb = dbHelper.safeGetWritableDatabase();

        assertEquals(ENROLLMENT_OLD_DB_FINAL_VERSION, oldDb.getVersion());
        // Sorted map because in case we need to add in order to avoid FK Constraints
        Map<String, List<ContentValues>> fakeData = createMigrationFakeDataDistinctSites();

        populateDb(oldDb, fakeData);
        SharedDbHelper sharedDbHelper =
                new SharedDbHelper(
                        sContext, SHARED_DB_NAME, SharedDbHelper.DATABASE_VERSION_V3, dbHelper);
        SQLiteDatabase newDb = sharedDbHelper.safeGetWritableDatabase();
        DbHelperTest.assertEnrollmentTableDoesNotExist(oldDb);
        SQLiteDatabase referenceLatestDb =
                createReferenceDbAtVersion(
                        sContext, MIGRATION_DB_REFERENCE_NAME, SharedDbHelper.DATABASE_VERSION_V3);
        DbTestUtil.assertDatabasesEqual(referenceLatestDb, newDb);
        assertEquals(SharedDbHelper.DATABASE_VERSION_V3, newDb.getVersion());
        verifyDataInDb(newDb, fakeData);
        emptyTables(newDb, EnrollmentTables.ENROLLMENT_TABLES);
        emptyTables(oldDb, EnrollmentTables.ENROLLMENT_TABLES);
    }

    @Test
    public void testMigrationExcludesDuplicatesSites() {
        DbHelperV1 dbHelperV1 = new DbHelperV1(sContext, OLD_TEST_DB_NAME, 1);
        SQLiteDatabase db = dbHelperV1.safeGetWritableDatabase();

        assertEquals(1, db.getVersion());

        DbHelper dbHelper =
                new DbHelper(sContext, OLD_TEST_DB_NAME, ENROLLMENT_OLD_DB_FINAL_VERSION);
        SQLiteDatabase oldDb = dbHelper.safeGetWritableDatabase();

        assertEquals(ENROLLMENT_OLD_DB_FINAL_VERSION, oldDb.getVersion());
        // Sorted map because in case we need to add in order to avoid FK Constraints
        Map<String, List<ContentValues>> preFakeData = createMigrationFakeDataFull();
        Map<String, List<ContentValues>> postFakeData = createPostMigrationFakeDataFull();
        populateDb(oldDb, preFakeData);
        SharedDbHelper sharedDbHelper =
                new SharedDbHelper(
                        sContext, SHARED_DB_NAME, SharedDbHelper.DATABASE_VERSION_V3, dbHelper);
        SQLiteDatabase newDb = sharedDbHelper.safeGetWritableDatabase();
        DbHelperTest.assertEnrollmentTableDoesNotExist(oldDb);
        SQLiteDatabase referenceLatestDb =
                createReferenceDbAtVersion(
                        sContext, MIGRATION_DB_REFERENCE_NAME, SharedDbHelper.DATABASE_VERSION_V3);
        DbTestUtil.assertDatabasesEqual(referenceLatestDb, newDb);
        assertEquals(SharedDbHelper.DATABASE_VERSION_V3, newDb.getVersion());
        verifyDataInDb(newDb, postFakeData);
        emptyTables(newDb, EnrollmentTables.ENROLLMENT_TABLES);
        emptyTables(oldDb, EnrollmentTables.ENROLLMENT_TABLES);
    }

    private Map<String, List<ContentValues>> createMigrationFakeDataFull() {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        tableRowsMap.put(
                EnrollmentTables.EnrollmentDataContract.TABLE,
                ContentValueFixtures.generateFullSiteEnrollmentListV1());
        return tableRowsMap;
    }

    private Map<String, List<ContentValues>> createMigrationFakeDataDistinctSites() {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        tableRowsMap.put(
                EnrollmentTables.EnrollmentDataContract.TABLE,
                ContentValueFixtures.generateDistinctSiteEnrollmentListV1());
        return tableRowsMap;
    }

    private Map<String, List<ContentValues>> createPostMigrationFakeDataFull() {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        List<ContentValues> enrollmentRows = new ArrayList<>();
        enrollmentRows.add(ContentValueFixtures.generateEnrollmentUniqueExampleContentValuesV1());
        tableRowsMap.put(EnrollmentTables.EnrollmentDataContract.TABLE, enrollmentRows);
        return tableRowsMap;
    }

    private void emptyTables(SQLiteDatabase db, String[] tables) {
        Arrays.stream(tables).forEach((table) -> db.delete(table, null, null));
    }

    @Test
    public void testOnUpgrade_sharedDbMigrationV4() {
        SharedDbHelper dbHelper = spy(DbTestUtil.getSharedDbHelperForTest());
        SQLiteDatabase db = Mockito.mock(SQLiteDatabase.class);

        // Do not actually perform queries but verify the invocation.
        SharedDbMigratorV4 sharedDbMigratorV4 = spy(new SharedDbMigratorV4());
        Mockito.doNothing().when(sharedDbMigratorV4).performMigration(db);
        Mockito.doReturn(List.of(sharedDbMigratorV4)).when(dbHelper).getOrderedDbMigrators();

        // Negative case - target version 4 is not in (oldVersion, newVersion]
        dbHelper.onUpgrade(db, /* oldVersion */ 1, /* new Version */ 3);
        verify(sharedDbMigratorV4, Mockito.never()).performMigration(db);

        // Positive case - target version 4 is in (oldVersion, newVersion]
        dbHelper.onUpgrade(db, /* oldVersion */ 1, /* new Version */ 4);
        verify(sharedDbMigratorV4).performMigration(db);
    }

    @Test
    public void testOnUpgrade_sharedDbMigration_V2_V3_V4() {
        SharedDbHelper sharedDbHelper = spy(DbTestUtil.getSharedDbHelperForTest());
        SQLiteDatabase db = Mockito.mock(SQLiteDatabase.class);

        // Do not actually perform queries but verify the invocation.
        SharedDbMigratorV2 sharedDbMigratorV2 = spy(new SharedDbMigratorV2());
        SharedDbMigratorV3 sharedDbMigratorV3 = spy(new SharedDbMigratorV3());
        SharedDbMigratorV4 sharedDbMigratorV4 = spy(new SharedDbMigratorV4());
        Mockito.doNothing().when(sharedDbMigratorV2).performMigration(db);
        Mockito.doNothing().when(sharedDbMigratorV3).performMigration(db);
        Mockito.doNothing().when(sharedDbMigratorV4).performMigration(db);

        Mockito.doReturn(List.of(sharedDbMigratorV2, sharedDbMigratorV3, sharedDbMigratorV4))
                .when(sharedDbHelper)
                .getOrderedDbMigrators();

        // Negative case
        sharedDbHelper.onUpgrade(db, /* oldVersion */ 1, /* new Version */ 1);
        verify(sharedDbMigratorV2, Mockito.never()).performMigration(db);
        verify(sharedDbMigratorV3, Mockito.never()).performMigration(db);
        verify(sharedDbMigratorV4, Mockito.never()).performMigration(db);

        // Positive case - 1 -> 4 should use all migrators
        sharedDbHelper.onUpgrade(db, /* oldVersion */ 1, /* new Version */ 4);
        verify(sharedDbMigratorV2, times(1)).performMigration(db);
        verify(sharedDbMigratorV3, times(1)).performMigration(db);
        verify(sharedDbMigratorV4, times(1)).performMigration(db);

        // Positive case - 1 -> 3 should use V2, V3 migrators only
        sharedDbHelper.onUpgrade(db, /* oldVersion */ 1, /* new Version */ 3);
        verify(sharedDbMigratorV2, times(2)).performMigration(db);
        verify(sharedDbMigratorV3, times(2)).performMigration(db);
        verify(sharedDbMigratorV4, times(1)).performMigration(db);

        // Positive case - 3 -> 4 should use V4 migrator only
        sharedDbHelper.onUpgrade(db, /* oldVersion */ 3, /* new Version */ 4);
        verify(sharedDbMigratorV2, times(2)).performMigration(db);
        verify(sharedDbMigratorV3, times(2)).performMigration(db);
        verify(sharedDbMigratorV4, times(2)).performMigration(db);
    }

    @Test
    public void testSupportsEnrollmentAPISchemaColumns() {
        SharedDbHelper sharedDbHelperV3 =
                new SharedDbHelper(
                        sContext,
                        SHARED_DB_NAME,
                        SharedDbHelper.DATABASE_VERSION_V3,
                        DbTestUtil.getDbHelperForTest());
        assertThat(sharedDbHelperV3.supportsEnrollmentAPISchemaColumns()).isFalse();

        SharedDbHelper sharedDbHelperV4 =
                new SharedDbHelper(
                        sContext,
                        SHARED_DB_NAME,
                        SharedDbHelper.DATABASE_VERSION_V4,
                        DbTestUtil.getDbHelperForTest());
        assertThat(sharedDbHelperV4.supportsEnrollmentAPISchemaColumns()).isTrue();
    }

    @Test
    public void testGetDatabaseVersionToCreate() {
        // Test feature flag is off
        Mockito.when(mMockFlags.getSharedDatabaseSchemaVersion4Enabled()).thenReturn(false);
        assertThat(SharedDbHelper.getDatabaseVersionToCreate()).isEqualTo(3);

        // Test feature flag is on
        Mockito.when(mMockFlags.getSharedDatabaseSchemaVersion4Enabled()).thenReturn(true);
        assertThat(SharedDbHelper.getDatabaseVersionToCreate()).isEqualTo(4);
    }
}
