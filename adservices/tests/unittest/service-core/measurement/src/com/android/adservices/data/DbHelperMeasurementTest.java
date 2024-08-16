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

package com.android.adservices.data;

import static com.android.adservices.common.DbTestUtil.assertMeasurementTablesDoNotExist;
import static com.android.adservices.common.DbTestUtil.doesIndexExist;
import static com.android.adservices.common.DbTestUtil.doesTableExistAndColumnCountMatch;
import static com.android.adservices.data.DbHelper.DATABASE_VERSION_7;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.quality.Strictness;

import java.util.Arrays;

@RunWith(MockitoJUnitRunner.class)
public class DbHelperMeasurementTest {
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
    public void testOnUpgrade_measurementMigration_tablesExist() {
        String dbName = FileCompatUtils.getAdservicesFilename("test_db");
        DbHelperV1 dbHelperV1 = new DbHelperV1(sContext, dbName, 1);
        SQLiteDatabase db = dbHelperV1.safeGetWritableDatabase();

        assertEquals(1, db.getVersion());

        DbHelper dbHelper = new DbHelper(sContext, dbName, DATABASE_VERSION_7);
        dbHelper.onUpgrade(db, 1, DATABASE_VERSION_7);
        assertMeasurementSchema(db);
    }

    @Test
    public void testOnUpgrade_measurementMigration_tablesDoNotExist() {
        String dbName = FileCompatUtils.getAdservicesFilename("test_db_2");
        DbHelperV1 dbHelperV1 = new DbHelperV1(sContext, dbName, 1);
        SQLiteDatabase db = dbHelperV1.safeGetWritableDatabase();

        assertEquals(1, db.getVersion());
        Arrays.stream(MeasurementTables.V1_TABLES).forEach((table) -> dropTable(db, table));

        DbHelper dbHelper = new DbHelper(sContext, dbName, DATABASE_VERSION_7);
        dbHelper.onUpgrade(db, 1, DATABASE_VERSION_7);
        assertMeasurementTablesDoNotExist(db);
    }

    private void dropTable(SQLiteDatabase db, String table) {
        db.execSQL("DROP TABLE IF EXISTS '" + table + "'");
    }

    private void assertMeasurementSchema(SQLiteDatabase db) {
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_source", 31));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_trigger", 19));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_async_registration_contract", 18));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_event_report", 17));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_attribution", 10));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_aggregate_report", 14));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_aggregate_encryption_key", 4));
        assertTrue(doesTableExistAndColumnCountMatch(db, "enrollment_data", 8));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_debug_report", 4));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_xna_ignored_sources", 2));
        assertTrue(doesIndexExist(db, "idx_msmt_source_ad_ei_et"));
        assertTrue(doesIndexExist(db, "idx_msmt_source_p_ad_wd_s_et"));
        assertTrue(doesIndexExist(db, "idx_msmt_trigger_ad_ei_tt"));
        assertTrue(doesIndexExist(db, "idx_msmt_source_et"));
        assertTrue(doesIndexExist(db, "idx_msmt_trigger_tt"));
        assertTrue(doesIndexExist(db, "idx_msmt_attribution_ss_so_ds_do_ei_tt"));
    }
}
