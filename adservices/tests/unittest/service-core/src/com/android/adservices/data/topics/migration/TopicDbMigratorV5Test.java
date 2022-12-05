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

package com.android.adservices.data.topics.migration;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.topics.TopicsTables;

import org.junit.Test;

/** Unit tests for {@link TopicDbMigratorV5} */
public class TopicDbMigratorV5Test {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    // The database is created with V2 and will migrate to V5.
    private final TopicsDbHelperV2 mTopicsDbHelper = TopicsDbHelperV2.getInstance(sContext);

    @Test
    public void testDbMigrationFromV2ToV5() {
        SQLiteDatabase db = mTopicsDbHelper.getWritableDatabase();

        // TopicContributors table doesn't exist in V2
        assertThat(
                        DbTestUtil.doesTableExistAndColumnCountMatch(
                                db,
                                TopicsTables.TopicContributorsContract.TABLE,
                                /* number Of Columns */ 4))
                .isFalse();

        // Use transaction here so that the changes in the performMigration is committed.
        // In normal DB upgrade, the DB will commit the change automatically.
        db.beginTransaction();
        // Upgrade the db V5 by using TopicDbMigratorV5
        new TopicDbMigratorV5().performMigration(db);
        // Commit the schema change
        db.setTransactionSuccessful();
        db.endTransaction();

        // TopicContributors table exists in V5
        db = mTopicsDbHelper.getReadableDatabase();
        assertThat(
                        DbTestUtil.doesTableExistAndColumnCountMatch(
                                db,
                                TopicsTables.TopicContributorsContract.TABLE,
                                /* number Of Columns */ 4))
                .isTrue();
    }
}
