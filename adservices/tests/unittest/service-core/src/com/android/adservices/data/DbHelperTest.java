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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.quality.Strictness;

@RunWith(MockitoJUnitRunner.class)
public class DbHelperTest {
    protected static final Context sContext = ApplicationProvider.getApplicationContext();

    private MockitoSession mStaticMockSession;

    @Mock private Flags mMockFlags;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(ErrorLogUtil.class)
                        .strictness(Strictness.WARN)
                        .startMocking();
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
    }

    @After
    public void teardown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testOnCreate() {
        SQLiteDatabase db = getDbHelperForTest().safeGetReadableDatabase();
        assertNotNull(db);
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_taxonomy", 4));
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_app_classification_topics", 6));
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_caller_can_learn_topic", 6));
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_top_topics", 10));
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_returned_topics", 8));
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_usage_history", 3));
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_app_usage_history", 3));
        assertTrue(doesTableExistAndColumnCountMatch(db, "enrollment_data", 8));
        assertMeasurementTablesDoNotExist(db);
    }

    public static void assertEnrollmentTableDoesNotExist(SQLiteDatabase db) {
        assertFalse(doesTableExist(db, "enrollment-data"));
    }

    @Test
    public void testGetDbFileSize() {
        final String databaseName = FileCompatUtils.getAdservicesFilename("testsize.db");
        DbHelper dbHelper = new DbHelper(sContext, databaseName, 1);

        // Create database
        dbHelper.getReadableDatabase();

        // Verify size should be more than 0 bytes as database was created
        Assert.assertTrue(dbHelper.getDbFileSize() > 0);

        // Delete database file
        sContext.getDatabasePath(databaseName).delete();

        // Verify database does not exist anymore
        Assert.assertEquals(-1, dbHelper.getDbFileSize());
    }

    @Test
    public void onOpen_appliesForeignKeyConstraint() {
        // dbHelper.onOpen gets called implicitly
        SQLiteDatabase db = getDbHelperForTest().safeGetReadableDatabase();
        try (Cursor cursor = db.rawQuery("PRAGMA foreign_keys", null)) {
            cursor.moveToNext();
            assertEquals(1, cursor.getLong(0));
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
