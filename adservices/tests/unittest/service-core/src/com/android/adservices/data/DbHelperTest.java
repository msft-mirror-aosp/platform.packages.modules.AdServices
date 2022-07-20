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
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_source", 21));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_trigger", 11));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_adtech_urls", 2));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_event_report", 12));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_attribution_rate_limit", 6));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_aggregate_report", 9));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_aggregate_encryption_key", 4));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_enrollment_data", 8));
        assertTrue(doesIndexExist(db, "idx_msmt_source_ad_rt_et"));
        assertTrue(doesIndexExist(db, "idx_msmt_trigger_ad_rt_tt"));
        assertTrue(doesIndexExist(db, "idx_msmt_source_et"));
        assertTrue(doesIndexExist(db, "idx_msmt_trigger_tt"));
        assertTrue(doesIndexExist(db, "idx_msmt_attribution_rate_limit_ss_ds_tt"));
    }
}
