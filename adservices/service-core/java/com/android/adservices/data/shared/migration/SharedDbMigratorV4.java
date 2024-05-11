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
package com.android.adservices.data.shared.migration;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import com.android.adservices.LogUtil;
import com.android.adservices.data.enrollment.EnrollmentTables;
import com.android.internal.annotations.VisibleForTesting;

/**
 * Migrates Shared DB from user version 3 to 4. This upgrade adds 'enrolled_site' and
 * 'enrolled_apis' column to the Enrollment table.
 */
public class SharedDbMigratorV4 extends AbstractSharedDbMigrator {

    public SharedDbMigratorV4() {
        super(4);
    }

    @Override
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
    public void performMigration(@NonNull SQLiteDatabase db) {
        MigrationHelpers.addTextColumnsIfAbsent(
                db,
                EnrollmentTables.EnrollmentDataContract.TABLE,
                new String[] {
                    EnrollmentTables.EnrollmentDataContract.ENROLLED_SITE,
                    EnrollmentTables.EnrollmentDataContract.ENROLLED_APIS
                });
        migrateEnrolledAPIsEnrolledSite(db);
    }

    private void migrateEnrolledAPIsEnrolledSite(@NonNull SQLiteDatabase db) {
        try (Cursor cursor =
                db.query(
                        /* table= */ EnrollmentTables.EnrollmentDataContract.TABLE,
                        /* columns= */ new String[] {
                            EnrollmentTables.EnrollmentDataContract.ENROLLMENT_ID,
                            EnrollmentTables.EnrollmentDataContract.COMPANY_ID,
                            EnrollmentTables.EnrollmentDataContract.ENCRYPTION_KEY_URL
                        },
                        /*selection*/ null,
                        /* selectionArgs= */ null,
                        /* groupBy= */ null,
                        /* having= */ null,
                        /* orderBy= */ null,
                        /* limit= */ null)) {
            while (cursor.moveToNext()) {
                ContentValues values = new ContentValues();
                String enrollmentId =
                        cursor.getString(
                                cursor.getColumnIndex(
                                        EnrollmentTables.EnrollmentDataContract.ENROLLMENT_ID));
                // Migrating data from company ID and encryption URL columns as they already
                // contain enrolled api and enrolled site data
                values.put(
                        EnrollmentTables.EnrollmentDataContract.ENROLLED_APIS,
                        cursor.getString(
                                cursor.getColumnIndex(
                                        EnrollmentTables.EnrollmentDataContract.COMPANY_ID)));
                values.put(
                        EnrollmentTables.EnrollmentDataContract.ENROLLED_SITE,
                        cursor.getString(
                                cursor.getColumnIndex(
                                        EnrollmentTables.EnrollmentDataContract
                                                .ENCRYPTION_KEY_URL)));
                long rows =
                        db.update(
                                EnrollmentTables.EnrollmentDataContract.TABLE,
                                values,
                                EnrollmentTables.EnrollmentDataContract.ENROLLMENT_ID + " = ?",
                                new String[] {enrollmentId});
                if (rows == -1) {
                    LogUtil.d("Failed to migrate enrolled_apis and enrolled_site data.");
                }
            }
        }
    }
}
