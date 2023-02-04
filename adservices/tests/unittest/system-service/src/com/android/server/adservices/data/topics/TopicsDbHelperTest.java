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

package com.android.server.adservices.data.topics;

import static com.android.server.adservices.data.topics.TopicsDbTestUtil.doesTableExistAndColumnCountMatch;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.database.sqlite.SQLiteDatabase;

import org.junit.Test;

/** Unit test to test class {@link TopicsDbHelper} */
public class TopicsDbHelperTest {
    @Test
    public void testOnCreate() {
        SQLiteDatabase db = TopicsDbTestUtil.getDbHelperForTest().safeGetReadableDatabase();
        assertNotNull(db);
        assertTrue(doesTableExistAndColumnCountMatch(db, "blocked_topics", 4));
    }
}
