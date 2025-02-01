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

import static com.google.common.truth.Truth.assertThat;

import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.service.common.compat.FileCompatUtils;

import org.junit.Test;

import java.util.Arrays;

public final class DbHelperMeasurementTest extends AdServicesUnitTestCase {
    @Test
    public void testOnUpgrade_measurementMigration_tablesExist() {
        String dbName = FileCompatUtils.getAdservicesFilename("test_db");
        DbHelperV1 dbHelperV1 = new DbHelperV1(mContext, dbName, 1);
        SQLiteDatabase db = dbHelperV1.safeGetWritableDatabase();

        assertThat(db.getVersion()).isEqualTo(1);

        DbHelper dbHelper = new DbHelper(mContext, dbName, DATABASE_VERSION_7);
        dbHelper.onUpgrade(db, 1, DATABASE_VERSION_7);
        assertMeasurementSchema(db);
    }

    @Test
    public void testOnUpgrade_measurementMigration_tablesDoNotExist() {
        String dbName = FileCompatUtils.getAdservicesFilename("test_db_2");
        DbHelperV1 dbHelperV1 = new DbHelperV1(mContext, dbName, 1);
        SQLiteDatabase db = dbHelperV1.safeGetWritableDatabase();

        assertThat(db.getVersion()).isEqualTo(1);
        Arrays.stream(MeasurementTables.V1_TABLES).forEach((table) -> dropTable(db, table));

        DbHelper dbHelper = new DbHelper(mContext, dbName, DATABASE_VERSION_7);
        dbHelper.onUpgrade(db, 1, DATABASE_VERSION_7);
        assertMeasurementTablesDoNotExist(db);
    }

    private void dropTable(SQLiteDatabase db, String table) {
        db.execSQL("DROP TABLE IF EXISTS '" + table + "'");
    }

    private void assertMeasurementSchema(SQLiteDatabase db) {
        assertThat(doesTableExistAndColumnCountMatch(db, "msmt_source", 31)).isTrue();
        assertThat(doesTableExistAndColumnCountMatch(db, "msmt_trigger", 19)).isTrue();
        assertThat(doesTableExistAndColumnCountMatch(db, "msmt_async_registration_contract", 18))
                .isTrue();
        assertThat(doesTableExistAndColumnCountMatch(db, "msmt_event_report", 17)).isTrue();
        assertThat(doesTableExistAndColumnCountMatch(db, "msmt_attribution", 10)).isTrue();
        assertThat(doesTableExistAndColumnCountMatch(db, "msmt_aggregate_report", 14)).isTrue();
        assertThat(doesTableExistAndColumnCountMatch(db, "msmt_aggregate_encryption_key", 4))
                .isTrue();
        assertThat(doesTableExistAndColumnCountMatch(db, "enrollment_data", 8)).isTrue();
        assertThat(doesTableExistAndColumnCountMatch(db, "msmt_debug_report", 4)).isTrue();
        assertThat(doesTableExistAndColumnCountMatch(db, "msmt_xna_ignored_sources", 2)).isTrue();
        assertThat(doesIndexExist(db, "idx_msmt_source_ad_ei_et")).isTrue();
        assertThat(doesIndexExist(db, "idx_msmt_source_p_ad_wd_s_et")).isTrue();
        assertThat(doesIndexExist(db, "idx_msmt_trigger_ad_ei_tt")).isTrue();
        assertThat(doesIndexExist(db, "idx_msmt_source_et")).isTrue();
        assertThat(doesIndexExist(db, "idx_msmt_trigger_tt")).isTrue();
        assertThat(doesIndexExist(db, "idx_msmt_attribution_ss_so_ds_do_ei_tt")).isTrue();
    }
}
