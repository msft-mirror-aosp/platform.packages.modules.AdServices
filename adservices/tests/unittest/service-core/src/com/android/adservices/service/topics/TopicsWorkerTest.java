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
package com.android.adservices.service.topics;

import static android.adservices.topics.TopicsManager.RESULT_OK;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.topics.GetTopicsResult;
import android.content.Context;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.service.Flags;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Unit test for {@link TopicsWorker}. */
public class TopicsWorkerTest {
    @SuppressWarnings({"unused"})
    private final Context mContext = ApplicationProvider.getApplicationContext();

    private TopicsWorker mTopicsWorker;
    private TopicsDao mTopicsDao;

    @Mock private EpochManager mMockEpochManager;
    @Mock private Flags mMockFlags;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        // Clean DB before each test
        DbTestUtil.deleteTable(TopicsTables.ReturnedTopicContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.BlockedTopicsContract.TABLE);

        DbHelper dbHelper = DbTestUtil.getDbHelperForTest();
        mTopicsDao = new TopicsDao(dbHelper);
        CacheManager cacheManager = new CacheManager(mMockEpochManager, mTopicsDao, mMockFlags);
        BlockedTopicsManager blockedTopicsManager = new BlockedTopicsManager(mTopicsDao);
        mTopicsWorker =
                new TopicsWorker(mMockEpochManager, cacheManager, blockedTopicsManager, mMockFlags);
    }

    @Test
    public void testGetTopics() {
        final long epochId = 4L;
        final int numberOfLookBackEpochs = 3;
        final Pair<String, String> appSdkKey = Pair.create("app", "sdk");
        Topic topic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion = */ 1L, /* modelVersion = */ 4L);
        Topic topic2 =
                Topic.create(/* topic */ 2, /* taxonomyVersion = */ 2L, /* modelVersion = */ 5L);
        Topic topic3 =
                Topic.create(/* topic */ 3, /* taxonomyVersion = */ 3L, /* modelVersion = */ 6L);
        Topic[] topics = {topic1, topic2, topic3};
        // persist returned topics into DB
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            Topic currentTopic = topics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap = new HashMap<>();
            returnedAppSdkTopicsMap.put(appSdkKey, currentTopic);
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, returnedAppSdkTopicsMap);
        }

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        // Real Cache Manager requires loading cache before getTopics() being called.
        mTopicsWorker.loadCache();

        GetTopicsResult getTopicsResult = mTopicsWorker.getTopics("app", "sdk");

        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(RESULT_OK)
                        .setTaxonomyVersions(Arrays.asList(1L, 2L, 3L))
                        .setModelVersions(Arrays.asList(4L, 5L, 6L))
                        .setTopics(Arrays.asList(1, 2, 3))
                        .build();

        // Since the returned topic list is shuffled, elements have to be verified separately
        assertThat(getTopicsResult.getResultCode())
                .isEqualTo(expectedGetTopicsResult.getResultCode());
        assertThat(getTopicsResult.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTaxonomyVersions());
        assertThat(getTopicsResult.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getModelVersions());
        assertThat(getTopicsResult.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTopics());

        // loadcache() and getTopics() in CacheManager calls this mock
        verify(mMockEpochManager, times(2)).getCurrentEpochId();
        // getTopics in CacheManager and TopicsWorker calls this mock
        verify(mMockFlags, times(2)).getTopicsNumberOfLookBackEpochs();
    }

    @Test
    public void testGetTopics_emptyCache() {
        final long epochId = 4L;

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId);

        // // There is no returned Topics persisted in the DB so cache is empty
        mTopicsWorker.loadCache();

        GetTopicsResult getTopicsResult = mTopicsWorker.getTopics("app", "sdk");

        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(RESULT_OK)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();

        // Since the returned topic list is shuffled, elements have to be verified separately
        assertThat(getTopicsResult.getResultCode())
                .isEqualTo(expectedGetTopicsResult.getResultCode());
        assertThat(getTopicsResult.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTaxonomyVersions());
        assertThat(getTopicsResult.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getModelVersions());
        assertThat(getTopicsResult.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTopics());

        // loadcache() and getTopics() in CacheManager calls this mock
        verify(mMockEpochManager, times(2)).getCurrentEpochId();
        // getTopics in CacheManager and TopicsWorker calls this mock
        verify(mMockFlags, times(2)).getTopicsNumberOfLookBackEpochs();
    }

    @Test
    public void testGetTopics_appNotInCache() {
        final long epochId = 4L;
        final int numberOfLookBackEpochs = 1;
        final Pair<String, String> appSdkKey = Pair.create("app", "sdk");
        Topic topic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion = */ 1L, /* modelVersion = */ 4L);
        Topic[] topics = {topic1};
        // persist returned topics into DB
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            Topic currentTopic = topics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap = new HashMap<>();
            returnedAppSdkTopicsMap.put(appSdkKey, currentTopic);
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, returnedAppSdkTopicsMap);
        }

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        // Real Cache Manager requires loading cache before getTopics() being called.
        mTopicsWorker.loadCache();

        GetTopicsResult getTopicsResult = mTopicsWorker.getTopics("app_not_in_cache", "sdk");

        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(RESULT_OK)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();

        assertThat(getTopicsResult).isEqualTo(expectedGetTopicsResult);

        // loadcache() and getTopics() in CacheManager calls this mock
        verify(mMockEpochManager, times(2)).getCurrentEpochId();
        // getTopics in CacheManager and TopicsWorker calls this mock
        verify(mMockFlags, times(2)).getTopicsNumberOfLookBackEpochs();
    }

    @Test
    public void testGetTopics_sdkNotInCache() {
        final long epochId = 4L;
        final int numberOfLookBackEpochs = 1;
        final Pair<String, String> appSdkKey = Pair.create("app", "sdk");
        Topic topic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion = */ 1L, /* modelVersion = */ 4L);
        Topic[] topics = {topic1};
        // persist returned topics into DB
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            Topic currentTopic = topics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap = new HashMap<>();
            returnedAppSdkTopicsMap.put(appSdkKey, currentTopic);
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, returnedAppSdkTopicsMap);
        }

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        // Real Cache Manager requires loading cache before getTopics() being called.
        mTopicsWorker.loadCache();

        GetTopicsResult getTopicsResult = mTopicsWorker.getTopics("app", "sdk_not_in_cache");

        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(RESULT_OK)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();

        assertThat(getTopicsResult).isEqualTo(expectedGetTopicsResult);

        // loadcache() and getTopics() in CacheManager calls this mock
        verify(mMockEpochManager, times(2)).getCurrentEpochId();
        // getTopics in CacheManager and TopicsWorker calls this mock
        verify(mMockFlags, times(2)).getTopicsNumberOfLookBackEpochs();
    }

    @Test
    public void testRecordUsage() {
        mTopicsWorker.recordUsage("app", "sdk");
        verify(mMockEpochManager, only()).recordUsageHistory(eq("app"), eq("sdk"));
    }

    @Test
    public void testComputeEpoch() {
        mTopicsWorker.computeEpoch();
        verify(mMockEpochManager, times(1)).processEpoch();
    }

    @Test
    public void testGetKnownTopicsWithConsent_oneTopicBlocked() {
        final long lastEpoch = 3;
        final int numberOfLookBackEpochs = 3;
        final Pair<String, String> appSdkKey = Pair.create("app", "sdk");
        Topic topic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion = */ 1L, /* modelVersion = */ 4L);
        Topic topic2 =
                Topic.create(/* topic */ 2, /* taxonomyVersion = */ 2L, /* modelVersion = */ 5L);
        Topic topic3 =
                Topic.create(/* topic */ 3, /* taxonomyVersion = */ 3L, /* modelVersion = */ 6L);
        Topic[] topics = {topic1, topic2, topic3};
        // persist returned topics into Db
        // populate topics for different epochs to get realistic state of the Db for testing
        // blocked topics.
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            Topic currentTopic = topics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap = new HashMap<>();
            returnedAppSdkTopicsMap.put(appSdkKey, currentTopic);
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, returnedAppSdkTopicsMap);
        }
        when(mMockEpochManager.getCurrentEpochId()).thenReturn(lastEpoch);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);
        Topic blockedTopic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion = */ 3L, /* modelVersion = */ 6L);
        mTopicsDao.recordBlockedTopic(blockedTopic1);

        mTopicsWorker.loadCache();
        ImmutableList<Topic> knownTopicsWithConsent = mTopicsWorker.getKnownTopicsWithConsent();
        ImmutableList<Topic> topicsWithRevokedConsent = mTopicsWorker.getTopicsWithRevokedConsent();

        // there is only one blocked topic.
        assertThat(topicsWithRevokedConsent).hasSize(1);
        assertThat(topicsWithRevokedConsent).containsExactly(blockedTopic1);
        // out of 3 existing topics, 2 of them are not blocked.
        assertThat(knownTopicsWithConsent).hasSize(2);
        assertThat(knownTopicsWithConsent).containsExactly(topic2, topic3);
    }

    @Test
    public void testGetKnownTopicsWithConsent_allTopicsBlocked() {
        final long lastEpoch = 3;
        final int numberOfLookBackEpochs = 3;
        final Pair<String, String> appSdkKey = Pair.create("app", "sdk");
        Topic topic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion = */ 1L, /* modelVersion = */ 4L);
        Topic topic2 =
                Topic.create(/* topic */ 2, /* taxonomyVersion = */ 2L, /* modelVersion = */ 5L);
        Topic topic3 =
                Topic.create(/* topic */ 3, /* taxonomyVersion = */ 3L, /* modelVersion = */ 6L);
        Topic[] topics = {topic1, topic2, topic3};
        // persist returned topics into DB
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            Topic currentTopic = topics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap = new HashMap<>();
            returnedAppSdkTopicsMap.put(appSdkKey, currentTopic);
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, returnedAppSdkTopicsMap);
        }
        when(mMockEpochManager.getCurrentEpochId()).thenReturn(lastEpoch);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);
        // block all topics
        mTopicsDao.recordBlockedTopic(topic1);
        mTopicsDao.recordBlockedTopic(topic2);
        mTopicsDao.recordBlockedTopic(topic3);

        mTopicsWorker.loadCache();
        ImmutableList<Topic> knownTopicsWithConsent = mTopicsWorker.getKnownTopicsWithConsent();

        assertThat(knownTopicsWithConsent).isEmpty();
    }

    @Test
    public void testTopicsWithRevokedConsent() {
        Topic blockedTopic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion = */ 1L, /* modelVersion = */ 4L);
        Topic blockedTopic2 =
                Topic.create(/* topic */ 2, /* taxonomyVersion = */ 2L, /* modelVersion = */ 5L);
        Topic blockedTopic3 =
                Topic.create(/* topic */ 3, /* taxonomyVersion = */ 3L, /* modelVersion = */ 6L);
        Topic[] blockedTopics = {blockedTopic1, blockedTopic2, blockedTopic3};
        // block all blockedTopics
        mTopicsDao.recordBlockedTopic(blockedTopic1);
        mTopicsDao.recordBlockedTopic(blockedTopic2);
        mTopicsDao.recordBlockedTopic(blockedTopic3);

        // persist one not blocked topic.
        final Pair<String, String> appSdkKey = Pair.create("app", "sdk");
        Topic topic1 =
                Topic.create(/* topic */ 4, /* taxonomyVersion = */ 1L, /* modelVersion = */ 4L);
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap = new HashMap<>();
        returnedAppSdkTopicsMap.put(appSdkKey, topic1);
        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 1, returnedAppSdkTopicsMap);

        mTopicsWorker.loadCache();
        ImmutableList<Topic> topicsWithRevokedConsent = mTopicsWorker.getTopicsWithRevokedConsent();

        assertThat(topicsWithRevokedConsent).hasSize(blockedTopics.length);
        assertThat(topicsWithRevokedConsent).containsExactly(blockedTopics);
    }

    @Test
    public void testTopicsWithRevokedConsent_noTopicsBlocked() {
        final int numberOfLookBackEpochs = 3;
        final Pair<String, String> appSdkKey = Pair.create("app", "sdk");
        Topic topic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion = */ 1L, /* modelVersion = */ 4L);
        Topic topic2 =
                Topic.create(/* topic */ 2, /* taxonomyVersion = */ 2L, /* modelVersion = */ 5L);
        Topic topic3 =
                Topic.create(/* topic */ 3, /* taxonomyVersion = */ 3L, /* modelVersion = */ 6L);
        Topic[] topics = {topic1, topic2, topic3};
        // persist returned topics into DB
        // populate topics for different epochs to get realistic state of the Db for testing
        // blocked topics.
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            Topic currentTopic = topics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap = new HashMap<>();
            returnedAppSdkTopicsMap.put(appSdkKey, currentTopic);
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, returnedAppSdkTopicsMap);
        }

        mTopicsWorker.loadCache();
        ImmutableList<Topic> topicsWithRevokedConsent = mTopicsWorker.getTopicsWithRevokedConsent();

        assertThat(topicsWithRevokedConsent).isEmpty();
    }

    @Test
    public void testRevokeConsent() {
        Topic topic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion = */ 1L, /* modelVersion = */ 4L);
        mTopicsWorker.loadCache();
        mTopicsWorker.revokeConsentForTopic(topic1);

        ImmutableList<Topic> topicsWithRevokedConsent = mTopicsWorker.getTopicsWithRevokedConsent();

        assertThat(topicsWithRevokedConsent).hasSize(1);
        assertThat(topicsWithRevokedConsent).containsExactly(topic1);

        // TODO(b/234214293): add checks on getTopics method.
    }

    @Test
    public void testRevokeAndRestoreConsent() {
        Topic topic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion = */ 1L, /* modelVersion = */ 4L);
        mTopicsWorker.loadCache();

        // Revoke consent for topic1
        mTopicsWorker.revokeConsentForTopic(topic1);
        ImmutableList<Topic> topicsWithRevokedConsent = mTopicsWorker.getTopicsWithRevokedConsent();

        assertThat(topicsWithRevokedConsent).hasSize(1);
        assertThat(topicsWithRevokedConsent).containsExactly(topic1);

        // Restore consent for topic1
        mTopicsWorker.restoreConsentForTopic(topic1);
        topicsWithRevokedConsent = mTopicsWorker.getTopicsWithRevokedConsent();

        assertThat(topicsWithRevokedConsent).isEmpty();

        // TODO(b/234214293): add checks on getTopics method.
    }
}
