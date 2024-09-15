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

import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.DbTestUtil;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.service.FlagsFactory;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.Set;

/** Unit tests for {@link TopicsDbMigratorV7} */
@MockStatic(FlagsFactory.class)
public final class TopicsDbMigratorV7Test extends AdServicesExtendedMockitoTestCase {
    // The database is created with V6 and will migrate to V7.
    private final TopicsDbHelperV6 mTopicsDbHelper = TopicsDbHelperV6.getInstance();

    @Before
    public void setup() {
        mocker.mockGetFlags(mMockFlags);
    }

    @Test
    public void testDbMigrationFromV6ToV7() {
        // Enable DB Schema Flag
        SQLiteDatabase db = mTopicsDbHelper.getWritableDatabase();

        // TopicContributors table doesn't exist in V6
        assertThat(
                        DbTestUtil.doesTableExistAndColumnCountMatch(
                                db,
                                TopicsTables.TopicContributorsContract.TABLE,
                                /* columnCount= */ 4))
                .isFalse();

        // Use transaction here so that the changes in the performMigration is committed.
        // In normal DB upgrade, the DB will commit the change automatically.
        db.beginTransaction();
        // Upgrade the db V7 by using TopicDbMigratorV7
        new TopicsDbMigratorV7().performMigration(db);
        // Commit the schema change
        db.setTransactionSuccessful();
        db.endTransaction();

        // TopicContributors table exists in V7
        db = mTopicsDbHelper.getReadableDatabase();
        assertThat(
                        DbTestUtil.doesTableExistAndColumnCountMatch(
                                db,
                                TopicsTables.TopicContributorsContract.TABLE,
                                /* columnCount= */ 4))
                .isTrue();

        // Test dated db is cleared when upgrading.
        db.beginTransaction();
        db = mTopicsDbHelper.getWritableDatabase();
        // Insert an entry into the table and verify it.
        TopicsDao topicsDao = new TopicsDao(mTopicsDbHelper);
        final long epochId = 1;
        Map<Integer, Set<String>> topicContributorsMap = Map.of(/* topicId */ 1, Set.of("app"));
        topicsDao.persistTopicContributors(epochId, topicContributorsMap);
        assertThat(topicsDao.retrieveTopicToContributorsMap(epochId))
                .isEqualTo(topicContributorsMap);

        new TopicsDbMigratorV7().performMigration(db);
        // Commit the schema change
        db.setTransactionSuccessful();
        db.endTransaction();

        // TopicContributorTable should be empty when getting upgraded again.
        assertThat(topicsDao.retrieveTopicToContributorsMap(epochId)).isEmpty();
    }
}
