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

import static com.android.adservices.data.shared.migration.MigrationTestHelper.createReferenceDbAtVersion;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbTestUtil;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.stream.Stream;

public class SharedDbHelperTest {

    private static final String MIGRATION_DB_REFERENCE_NAME =
            "adservices_shared_db_migrate_reference.db";
    private static final String OLD_TEST_DB_NAME = "old_test_db.db";
    private static final String SHARED_DB_NAME = "adservices_shared_db_test.db";
    protected static final Context sContext = ApplicationProvider.getApplicationContext();

    @Before
    public void setup() {
        Stream.of(MIGRATION_DB_REFERENCE_NAME, OLD_TEST_DB_NAME, SHARED_DB_NAME)
                .map(sContext::getDatabasePath)
                .filter(File::exists)
                .forEach(File::delete);
    }

    @Test
    public void testNewInstall() {
        SharedDbHelper sharedDbHelper =
                new SharedDbHelper(
                        sContext,
                        SHARED_DB_NAME,
                        SharedDbHelper.CURRENT_DATABASE_VERSION,
                        DbTestUtil.getDbHelperForTest());
        SQLiteDatabase db = sharedDbHelper.safeGetWritableDatabase();
        SQLiteDatabase referenceLatestDb =
                createReferenceDbAtVersion(
                        sContext,
                        MIGRATION_DB_REFERENCE_NAME,
                        SharedDbHelper.CURRENT_DATABASE_VERSION);
        DbTestUtil.assertDatabasesEqual(referenceLatestDb, db);
    }
}
