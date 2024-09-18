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

import static com.android.adservices.common.DbTestUtil.getDbHelperForTest;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.encryptionkey.EncryptionKeyTables;
import com.android.adservices.data.enrollment.EnrollmentTables;
import com.android.adservices.data.shared.SharedDbHelper;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SharedDbMigratorV2Test extends SharedDbMigratorTestBase {

    /**
     * @return shared db target version.
     */
    @Override
    int getTargetVersion() {
        return 2;
    }

    /**
     * @return shared db migrator for version2.
     */
    @Override
    AbstractSharedDbMigrator getTestSubject() {
        return new SharedDbMigratorV2();
    }

    /** Unit test for shared db migration from version 1 to 2. */
    @Test
    public void performMigration_v1ToV2_createEncryptionKeyTable() {
        // Set up
        SharedDbHelper dbHelper =
                new SharedDbHelper(
                        mContext, SHARED_DATABASE_NAME_FOR_MIGRATION, 1, getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Map<String, List<ContentValues>> fakeData = createFakeDataV1();
        MigrationTestHelper.populateDb(db, fakeData);

        // Execution
        getTestSubject().performMigration(db, 1, 2);

        // Assertion
        assertThat(SharedDbHelper.hasAllTables(db, EnrollmentTables.ENROLLMENT_TABLES)).isTrue();
        assertThat(SharedDbHelper.hasAllTables(db, EncryptionKeyTables.ENCRYPTION_KEY_TABLES))
                .isTrue();
        MigrationTestHelper.verifyDataInDb(db, fakeData);
    }

    /** Create fake data for shared db version 1, contains data in enrollment table. */
    private Map<String, List<ContentValues>> createFakeDataV1() {
        Map<String, List<ContentValues>> tableRowMap = new LinkedHashMap<>();
        // Enrollment table.
        List<ContentValues> enrollmentRows = new ArrayList<>();
        ContentValues enrollmentData =
                ContentValueFixtures.generateEnrollmentDefaultExampleContentValuesV1();
        enrollmentRows.add(enrollmentData);
        tableRowMap.put(EnrollmentTables.EnrollmentDataContract.TABLE, enrollmentRows);
        return tableRowMap;
    }
}
