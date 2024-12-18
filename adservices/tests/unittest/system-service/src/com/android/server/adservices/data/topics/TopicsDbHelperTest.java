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

import static com.android.adservices.shared.testing.common.DumpHelper.assertDumpHasPrefix;
import static com.android.adservices.shared.testing.common.DumpHelper.dump;
import static com.android.server.adservices.data.topics.TopicsDbTestUtil.doesTableExistAndColumnCountMatch;

import static com.google.common.truth.Truth.assertWithMessage;

import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

/** Unit test to test class {@link TopicsDbHelper} */
public final class TopicsDbHelperTest extends AdServicesUnitTestCase {

    @Test
    public void testOnCreate() {
        SQLiteDatabase db = TopicsDbTestUtil.getDbHelperForTest().safeGetReadableDatabase();

        assertWithMessage("db").that(db).isNotNull();
        assertWithMessage("doesTableExistAndColumnCountMatch()")
                .that(doesTableExistAndColumnCountMatch(db, "blocked_topics", 4))
                .isTrue();
    }

    @Test
    public void testDump() throws Exception {
        String prefix = "fixed, pre is:";
        TopicsDbHelper dao = new TopicsDbHelper(mContext);

        String dump = dump(pw -> dao.dump(pw, prefix, /* args= */ null));

        // Content doesn't matter much, we just wanna make sure it doesn't crash (for example,
        // by using the wrong %s / %d tokens) and every line dumps the prefix
        assertDumpHasPrefix(dump, prefix);
    }
}
