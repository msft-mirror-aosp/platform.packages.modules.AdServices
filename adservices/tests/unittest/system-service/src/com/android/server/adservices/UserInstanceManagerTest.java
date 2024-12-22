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

package com.android.server.adservices;

import static com.android.adservices.shared.testing.common.DumpHelper.dump;
import static com.android.server.adservices.data.topics.TopicsTables.DUMMY_MODEL_VERSION;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import android.adservices.topics.Topic;
import android.app.adservices.topics.TopicParcel;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.server.adservices.consent.AppConsentManager;
import com.android.server.adservices.consent.ConsentManager;
import com.android.server.adservices.data.topics.TopicsDao;
import com.android.server.adservices.data.topics.TopicsDbHelper;
import com.android.server.adservices.data.topics.TopicsDbTestUtil;
import com.android.server.adservices.data.topics.TopicsTables;
import com.android.server.adservices.rollback.RollbackHandlingManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Set;

/** Tests for {@link UserInstanceManager} */
public final class UserInstanceManagerTest extends AdServicesMockitoTestCase {

    private static final String TOPICS_DAO_DUMP = "D'OHump!";
    private static final int TEST_MODULE_VERSION = 339990000;

    private final String mTestBasePath = mContext.getFilesDir().getAbsolutePath();
    private final TopicsDbHelper mDBHelper = TopicsDbTestUtil.getDbHelperForTest();
    private TopicsDao mTopicsDao;

    private UserInstanceManager mUserInstanceManager;

    @Mock private TopicsDao mMockTopicsDao; // used to test dump

    @Before
    public void setup() throws IOException {
        mTopicsDao = new TopicsDao(mDBHelper);
        mUserInstanceManager = new UserInstanceManager(mTopicsDao, mTestBasePath);
    }

    @After
    public void tearDown() {
        // We need tear down this instance since it can have underlying persisted Data Store.
        mUserInstanceManager.tearDownForTesting();

        // Delete all data in the database
        TopicsDbTestUtil.deleteTable(TopicsTables.BlockedTopicsContract.TABLE);
    }

    @Test
    public void testGetOrCreateUserConsentManagerInstance() throws IOException {
        ConsentManager consentManager0 =
                mUserInstanceManager.getOrCreateUserConsentManagerInstance(/* userIdentifier */ 0);

        ConsentManager consentManager1 =
                mUserInstanceManager.getOrCreateUserConsentManagerInstance(/* userIdentifier */ 1);

        AppConsentManager appConsentManager0 =
                mUserInstanceManager.getOrCreateUserAppConsentManagerInstance(0);

        AppConsentManager appConsentManager1 =
                mUserInstanceManager.getOrCreateUserAppConsentManagerInstance(1);

        RollbackHandlingManager rollbackHandlingManager0 =
                mUserInstanceManager.getOrCreateUserRollbackHandlingManagerInstance(
                        0, TEST_MODULE_VERSION);

        RollbackHandlingManager rollbackHandlingManager1 =
                mUserInstanceManager.getOrCreateUserRollbackHandlingManagerInstance(
                        1, TEST_MODULE_VERSION);

        // One instance per user.
        assertThat(
                        mUserInstanceManager.getOrCreateUserConsentManagerInstance(
                                /* userIdentifier */ 0))
                .isNotSameInstanceAs(consentManager1);
        assertThat(mUserInstanceManager.getOrCreateUserAppConsentManagerInstance(0))
                .isNotSameInstanceAs(appConsentManager1);
        assertThat(
                        mUserInstanceManager.getOrCreateUserRollbackHandlingManagerInstance(
                                0, TEST_MODULE_VERSION))
                .isNotSameInstanceAs(rollbackHandlingManager1);

        // Creating instance once per user.
        assertThat(
                        mUserInstanceManager.getOrCreateUserConsentManagerInstance(
                                /* userIdentifier */ 0))
                .isSameInstanceAs(consentManager0);
        assertThat(
                        mUserInstanceManager.getOrCreateUserConsentManagerInstance(
                                /* userIdentifier */ 1))
                .isSameInstanceAs(consentManager1);
        assertThat(mUserInstanceManager.getOrCreateUserAppConsentManagerInstance(0))
                .isSameInstanceAs(appConsentManager0);
        assertThat(mUserInstanceManager.getOrCreateUserAppConsentManagerInstance(1))
                .isSameInstanceAs(appConsentManager1);

        assertThat(
                        mUserInstanceManager.getOrCreateUserRollbackHandlingManagerInstance(
                                0, TEST_MODULE_VERSION))
                .isSameInstanceAs(rollbackHandlingManager0);
        assertThat(
                        mUserInstanceManager.getOrCreateUserRollbackHandlingManagerInstance(
                                1, TEST_MODULE_VERSION))
                .isSameInstanceAs(rollbackHandlingManager1);
    }

    @Test
    public void testGetOrCreateUserBlockedTopicsManagerInstance() {
        final int user0 = 0;
        final int user1 = 1;

        BlockedTopicsManager blockedTopicsManager0 =
                mUserInstanceManager.getOrCreateUserBlockedTopicsManagerInstance(user0);
        BlockedTopicsManager blockedTopicsManager1 =
                mUserInstanceManager.getOrCreateUserBlockedTopicsManagerInstance(user1);

        // One instance per user.
        assertThat(blockedTopicsManager0).isNotSameInstanceAs(blockedTopicsManager1);

        // Creating instance once per user.
        assertThat(mUserInstanceManager.getOrCreateUserBlockedTopicsManagerInstance(user0))
                .isSameInstanceAs(blockedTopicsManager0);
        assertThat(mUserInstanceManager.getOrCreateUserBlockedTopicsManagerInstance(user1))
                .isSameInstanceAs(blockedTopicsManager1);
    }

    @Test
    public void testRecordRemoveBlockedTopicsMultipleUsers() {
        final int user0 = 0;
        final int user1 = 1;
        final TopicParcel topicParcel =
                new TopicParcel.Builder()
                        .setTopicId(/* topicId */ 1)
                        .setTaxonomyVersion(/* taxonomyVersion */ 1)
                        .setModelVersion(DUMMY_MODEL_VERSION)
                        .build();

        BlockedTopicsManager blockedTopicsManager0 =
                mUserInstanceManager.getOrCreateUserBlockedTopicsManagerInstance(user0);
        BlockedTopicsManager blockedTopicsManager1 =
                mUserInstanceManager.getOrCreateUserBlockedTopicsManagerInstance(user1);

        // Record topic for user 0
        blockedTopicsManager0.recordBlockedTopic(List.of(topicParcel));
        assertThat(blockedTopicsManager0.retrieveAllBlockedTopics())
                .isEqualTo(List.of(topicParcel));
        assertThat(blockedTopicsManager1.retrieveAllBlockedTopics()).isEmpty();

        // Record topic also for user 1 and remove topic for user0;
        blockedTopicsManager1.recordBlockedTopic(List.of(topicParcel));
        blockedTopicsManager0.removeBlockedTopic(topicParcel);
        assertThat(blockedTopicsManager0.retrieveAllBlockedTopics()).isEmpty();
        assertThat(blockedTopicsManager1.retrieveAllBlockedTopics())
                .isEqualTo(List.of(topicParcel));
    }

    @Test
    public void testDeleteConsentManagerInstance() throws Exception {
        int userIdentifier = 0;
        mUserInstanceManager.getOrCreateUserConsentManagerInstance(userIdentifier);
        assertThat(mUserInstanceManager.getUserConsentManagerInstance(userIdentifier)).isNotNull();
        mUserInstanceManager.deleteUserInstance(userIdentifier);

        assertThat(mUserInstanceManager.getUserConsentManagerInstance(userIdentifier)).isNull();
    }

    @Test
    public void testDeleteConsentManagerInstance_userIdentifierNotPresent() throws Exception {
        int userIdentifier = 0;
        mUserInstanceManager.getOrCreateUserConsentManagerInstance(userIdentifier);
        assertThat(mUserInstanceManager.getUserConsentManagerInstance(userIdentifier)).isNotNull();
        int userIdentifierNotPresent = 3;

        mUserInstanceManager.deleteUserInstance(userIdentifierNotPresent);

        assertThat(mUserInstanceManager.getUserConsentManagerInstance(userIdentifier)).isNotNull();
    }

    @Test
    public void testDeleteAllDataWhenDeletingUserInstance() throws Exception {
        final int topicId1 = 1;
        final int topicId2 = 2;
        final int user0 = 0;
        final int user1 = 1;
        final long taxonomyVersion = 1L;
        final long modelVersion = 1L;
        Topic topicToBlock1 = new Topic(taxonomyVersion, modelVersion, topicId1);
        Topic topicToBlock2 = new Topic(taxonomyVersion, modelVersion, topicId2);
        mTopicsDao.recordBlockedTopic(List.of(topicToBlock1), user0);
        mTopicsDao.recordBlockedTopic(List.of(topicToBlock2), user0);
        mTopicsDao.recordBlockedTopic(List.of(topicToBlock1), user1);

        Set<Topic> blockedTopics0 = mTopicsDao.retrieveAllBlockedTopics(user0);
        Set<Topic> blockedTopics1 = mTopicsDao.retrieveAllBlockedTopics(user1);

        // Make sure that what we write to db is equal to what we read from db.
        assertThat(blockedTopics0).hasSize(2);
        assertThat(blockedTopics0).containsExactly(topicToBlock1, topicToBlock2);
        assertThat(blockedTopics1).hasSize(1);
        assertThat(blockedTopics1).containsExactly(topicToBlock1);

        mUserInstanceManager.deleteUserInstance(user0);

        // User 0 should have no blocked topics and User 1 should still have 1 blocked topic
        assertThat(mTopicsDao.retrieveAllBlockedTopics(user0)).isEmpty();
        assertThat(blockedTopics1).hasSize(1);
        assertThat(blockedTopics1).containsExactly(topicToBlock1);
    }

    @Test
    public void testDump() throws Exception {
        doAnswer(
                        inv -> {
                            ((PrintWriter) inv.getArgument(0)).println(TOPICS_DAO_DUMP);
                            return null;
                        })
                .when(mMockTopicsDao)
                .dump(any(), any(), any());
        UserInstanceManager mgr = new UserInstanceManager(mMockTopicsDao, mTestBasePath);

        String[] args = new String[0];
        String dump = dump(pw -> mgr.dump(pw, args));

        // Content doesn't matter much, we just wanna make sure it doesn't crash (for example,
        // by using the wrong %s / %d tokens) and that its components are dumped
        assertWithMessage("content of dump()").that(dump).contains(TOPICS_DAO_DUMP);

        // TODO(b/280677793): dump content of other managers (it might be simpler to do so on each
        // method above, using expectThat() (instead of assertThat())
    }
}
