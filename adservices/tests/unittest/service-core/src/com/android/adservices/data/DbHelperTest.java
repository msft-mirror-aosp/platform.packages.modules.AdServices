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
        assertTrue(doesTableExist("topics_taxonomy"));
        assertTrue(doesTableExist("topics_app_classification_topics"));
        assertTrue(doesTableExist("topics_caller_can_learn_topic"));
        assertTrue(doesTableExist("topics_top_topics"));
        assertTrue(doesTableExist("topics_returned_topics"));
        assertTrue(doesTableExist("topics_usage_history"));
    }

    public boolean doesTableExist(String tableName) {
        String query =
                "select DISTINCT tbl_name from sqlite_master where tbl_name = '" + tableName + "'";
        Cursor cursor = DbHelper.getInstance(sContext).safeGetReadableDatabase()
                .rawQuery(query, null);
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                return true;
            }
        }
        return false;
    }
}
