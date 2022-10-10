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

import static com.android.adservices.data.DbTestUtil.doesIndexExist;
import static com.android.adservices.data.DbTestUtil.doesTableExistAndColumnCountMatch;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Assert;
import org.junit.Test;

public class DbHelperTest {
    protected static final Context sContext = ApplicationProvider.getApplicationContext();

    @Test
    public void testOnCreate() {
        SQLiteDatabase db = DbTestUtil.getDbHelperForTest().safeGetReadableDatabase();
        assertNotNull(db);
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_taxonomy", 4));
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_app_classification_topics", 6));
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_caller_can_learn_topic", 6));
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_top_topics", 10));
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_returned_topics", 7));
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_usage_history", 3));
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_app_usage_history", 3));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_source", 22));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_trigger", 12));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_async_registration_contract", 15));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_event_report", 14));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_attribution", 8));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_aggregate_report", 11));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_aggregate_encryption_key", 4));
        assertTrue(doesTableExistAndColumnCountMatch(db, "enrollment_data", 8));
        assertTrue(doesIndexExist(db, "idx_msmt_source_ad_ei_et"));
        assertTrue(doesIndexExist(db, "idx_msmt_source_p_ad_wd_s_et"));
        assertTrue(doesIndexExist(db, "idx_msmt_trigger_ad_ei_tt"));
        assertTrue(doesIndexExist(db, "idx_msmt_source_et"));
        assertTrue(doesIndexExist(db, "idx_msmt_trigger_tt"));
        assertTrue(doesIndexExist(db, "idx_msmt_attribution_ss_so_ds_do_ei_tt"));
    }

    @Test
    public void testGetDbFileSize() {
        final String databaseName = "testsize.db";
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
}
