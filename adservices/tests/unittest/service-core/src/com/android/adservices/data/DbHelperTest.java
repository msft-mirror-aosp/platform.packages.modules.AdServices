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

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;

public class DbHelperTest {

    private static final String TAG = "DbHelperTest";

    protected static final Context sContext = ApplicationProvider.getApplicationContext();

    @Test
    public void testOnCreate() {
        assertTrue(doesTableExistAndColumnCountMatch("topics_taxonomy", 3));
        assertTrue(doesTableExistAndColumnCountMatch("topics_app_classification_topics", 6));
        assertTrue(doesTableExistAndColumnCountMatch("topics_caller_can_learn_topic", 4));
        assertTrue(doesTableExistAndColumnCountMatch("topics_top_topics", 8));
        assertTrue(doesTableExistAndColumnCountMatch("topics_returned_topics", 7));
        assertTrue(doesTableExistAndColumnCountMatch("topics_usage_history", 3));
        assertTrue(doesTableExistAndColumnCountMatch("topics_app_usage_history", 3));
        assertTrue(doesTableExistAndColumnCountMatch("msmt_source", 18));
        assertTrue(doesTableExistAndColumnCountMatch("msmt_trigger", 11));
        assertTrue(doesTableExistAndColumnCountMatch("msmt_adtech_urls", 2));
        assertTrue(doesTableExistAndColumnCountMatch("msmt_event_report", 11));
        assertTrue(doesTableExistAndColumnCountMatch("msmt_attribution_rate_limit", 6));
        assertTrue(doesIndexExist("idx_msmt_source_ad_rt_et"));
        assertTrue(doesIndexExist("idx_msmt_trigger_ad_rt_tt"));
        assertTrue(doesIndexExist("idx_msmt_source_et"));
        assertTrue(doesIndexExist("idx_msmt_trigger_tt"));
        assertTrue(doesIndexExist("idx_msmt_attribution_rate_limit_ss_ds_tt"));
    }

    public boolean doesTableExistAndColumnCountMatch(String tableName, int columnCount) {
        String query =
                "select s.tbl_name, p.name from sqlite_master s "
                        + "join pragma_table_info(s.name) p "
                        + "where s.tbl_name = '" + tableName + "'";
        Cursor cursor = DbTestUtil.getDbHelperForTest().safeGetReadableDatabase()
                .rawQuery(query, null);
        if (cursor != null) {
            if (cursor.getCount() == columnCount) {
                return true;
            }
        }
        return false;
    }

    public boolean doesIndexExist(String index) {
        String query = "SELECT * FROM sqlite_master WHERE type='index' and name='" + index + "'";
        Cursor cursor = DbTestUtil.getDbHelperForTest().safeGetReadableDatabase()
                .rawQuery(query, null);
        if (cursor != null) {
            return cursor.getCount() > 0;
        }
        return false;
    }
}
