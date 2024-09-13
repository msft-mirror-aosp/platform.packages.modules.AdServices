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

import static com.android.adservices.common.DbTestUtil.assertMeasurementTablesDoNotExist;
import static com.android.adservices.common.DbTestUtil.doesTableExist;
import static com.android.adservices.common.DbTestUtil.doesTableExistAndColumnCountMatch;
import static com.android.adservices.common.DbTestUtil.getDbHelperForTest;
import static com.android.adservices.data.DbHelper.DATABASE_VERSION_7;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_WRITE_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

@SpyStatic(FlagsFactory.class)
public final class DbHelperTest extends AdServicesExtendedMockitoTestCase {
    @Before
    public void setup() {
        mocker.mockGetFlags(mMockFlags);
    }

    @Test
    public void testOnCreate() {
        SQLiteDatabase db = getDbHelperForTest().safeGetReadableDatabase();
        assertNotNull(db);
        expect.that(doesTableExistAndColumnCountMatch(db, "topics_taxonomy", 4)).isTrue();
        expect.that(doesTableExistAndColumnCountMatch(db, "topics_app_classification_topics", 6))
                .isTrue();
        expect.that(doesTableExistAndColumnCountMatch(db, "topics_caller_can_learn_topic", 6))
                .isTrue();
        expect.that(doesTableExistAndColumnCountMatch(db, "topics_top_topics", 10)).isTrue();
        expect.that(doesTableExistAndColumnCountMatch(db, "topics_returned_topics", 8)).isTrue();
        expect.that(doesTableExistAndColumnCountMatch(db, "topics_usage_history", 3)).isTrue();
        expect.that(doesTableExistAndColumnCountMatch(db, "topics_app_usage_history", 3)).isTrue();
        expect.that(doesTableExistAndColumnCountMatch(db, "enrollment_data", 8)).isTrue();
        assertMeasurementTablesDoNotExist(db);
    }

    public static void assertEnrollmentTableDoesNotExist(SQLiteDatabase db) {
        assertFalse(doesTableExist(db, "enrollment-data"));
    }

    @Test
    public void testGetDbFileSize() {
        final String databaseName = FileCompatUtils.getAdservicesFilename("testsize.db");
        DbHelper dbHelper = new DbHelper(mContext, databaseName, 1);

        // Create database
        dbHelper.getReadableDatabase();

        // Verify size should be more than 0 bytes as database was created
        expect.that(dbHelper.getDbFileSize()).isGreaterThan(0);

        // Delete database file
        mContext.getDatabasePath(databaseName).delete();

        // Verify database does not exist anymore
        expect.that(dbHelper.getDbFileSize()).isEqualTo(-1);
    }

    @Test
    public void onOpen_appliesForeignKeyConstraint() {
        // dbHelper.onOpen gets called implicitly
        SQLiteDatabase db = getDbHelperForTest().safeGetReadableDatabase();
        try (Cursor cursor = db.rawQuery("PRAGMA foreign_keys", null)) {
            cursor.moveToNext();
            expect.that(cursor.getLong(0)).isEqualTo(1);
        }
    }

    @Test
    public void testOnDowngrade() {
        DbHelper dbHelper = spy(getDbHelperForTest());
        SQLiteDatabase db = mock(SQLiteDatabase.class);

        // Verify no error if downgrading db from current version to V1
        dbHelper.onDowngrade(db, DATABASE_VERSION_7, 1);
    }

    @Test
    public void testSafeGetReadableDatabase_exceptionOccurs_validatesErrorLogging() {
        DbHelper dbHelper = spy(getDbHelperForTest());
        Throwable tr = new SQLiteException();
        Mockito.doThrow(tr).when(dbHelper).getReadableDatabase();
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));

        SQLiteDatabase db = dbHelper.safeGetReadableDatabase();

        assertNull(db);
        ExtendedMockito.verify(
                () ->
                        ErrorLogUtil.e(
                                tr,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON));
    }

    @Test
    public void testSafeGetWriteDatabase_exceptionOccurs_validatesErrorLogging() {
        DbHelper dbHelper = spy(getDbHelperForTest());
        Throwable tr = new SQLiteException();
        Mockito.doThrow(tr).when(dbHelper).getWritableDatabase();
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));

        SQLiteDatabase db = dbHelper.safeGetWritableDatabase();

        assertNull(db);
        ExtendedMockito.verify(
                () ->
                        ErrorLogUtil.e(
                                tr,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_WRITE_EXCEPTION,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON));
    }
}
