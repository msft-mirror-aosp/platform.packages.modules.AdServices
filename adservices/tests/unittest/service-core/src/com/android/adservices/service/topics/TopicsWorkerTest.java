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

import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.topics.GetTopicsResult;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockRandom;
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
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Unit test for {@link com.android.adservices.service.topics.TopicsWorker}. */
public class TopicsWorkerTest {
    // Spy the Context to test app reconciliation
    private final Context mContext = spy(ApplicationProvider.getApplicationContext());

    private TopicsWorker mTopicsWorker;
    private TopicsDao mTopicsDao;
    private AppUpdateManager mAppUpdateManager;
    private CacheManager mCacheManager;
    private BlockedTopicsManager mBlockedTopicsManager;

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
        mAppUpdateManager = new AppUpdateManager(mTopicsDao, new Random(), mMockFlags);
        mCacheManager = new CacheManager(mMockEpochManager, mTopicsDao, mMockFlags);
        mBlockedTopicsManager = new BlockedTopicsManager(mTopicsDao);
        mTopicsWorker =
                new TopicsWorker(
                        mMockEpochManager,
                        mCacheManager,
                        mBlockedTopicsManager,
                        mAppUpdateManager,
                        mMockFlags);
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
                        .setResultCode(STATUS_SUCCESS)
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

        // getTopic() + loadCache() + handleSdkTopicsAssignmentForAppInstallation()
        verify(mMockEpochManager, times(3)).getCurrentEpochId();
        verify(mMockFlags, times(3)).getTopicsNumberOfLookBackEpochs();
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
                        .setResultCode(STATUS_SUCCESS)
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

        // getTopic() + loadCache() + handleSdkTopicsAssignmentForAppInstallation()
        verify(mMockEpochManager, times(3)).getCurrentEpochId();
        verify(mMockFlags, times(3)).getTopicsNumberOfLookBackEpochs();
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
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();

        assertThat(getTopicsResult).isEqualTo(expectedGetTopicsResult);

        // getTopic() + loadCache() + handleSdkTopicsAssignmentForAppInstallation()
        verify(mMockEpochManager, times(3)).getCurrentEpochId();
        verify(mMockFlags, times(3)).getTopicsNumberOfLookBackEpochs();
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
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();

        assertThat(getTopicsResult).isEqualTo(expectedGetTopicsResult);

        // getTopic() + loadCache() + handleSdkTopicsAssignmentForAppInstallation()
        verify(mMockEpochManager, times(3)).getCurrentEpochId();
        verify(mMockFlags, times(3)).getTopicsNumberOfLookBackEpochs();
    }

    @Test
    public void testGetTopics_handleSdkTopicAssignment() {
        final int numberOfLookBackEpochs = 3;
        final long currentEpochId = 5L;

        final String app = "app";
        final String sdk = "sdk";

        Pair<String, String> appOnlyCaller = Pair.create(app, /* sdk */ "");

        Topic topic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion = */ 1L, /* modelVersion = */ 4L);
        Topic topic2 =
                Topic.create(/* topic */ 2, /* taxonomyVersion = */ 2L, /* modelVersion = */ 5L);
        Topic topic3 =
                Topic.create(/* topic */ 3, /* taxonomyVersion = */ 3L, /* modelVersion = */ 6L);
        Topic[] topics = {topic1, topic2, topic3};

        // persist returned topics into DB
        for (long epoch = 0; epoch < numberOfLookBackEpochs; epoch++) {
            long epochId = currentEpochId - 1 - epoch;
            Topic topic = topics[(int) epoch];

            mTopicsDao.persistReturnedAppTopicsMap(epochId, Map.of(appOnlyCaller, topic));
            // SDK needs to be able to learn this topic in past epochs
            mTopicsDao.persistCallerCanLearnTopics(epochId, Map.of(topic, Set.of(sdk)));
        }

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(currentEpochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        GetTopicsResult getTopicsResult = mTopicsWorker.getTopics(app, sdk);

        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
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

        // Invocation Summary
        // getTopic(): 1, handleSdkTopicsAssignmentForAppInstallation(): 1 * 2
        verify(mMockEpochManager, times(3)).getCurrentEpochId();
        verify(mMockFlags, times(3)).getTopicsNumberOfLookBackEpochs();
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

        // Three topics are persisted into blocked topic table
        assertThat(topicsWithRevokedConsent).hasSize(3);
        assertThat(topicsWithRevokedConsent)
                .containsExactly(blockedTopic1, blockedTopic2, blockedTopic3);
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

    @Test
    public void testClearAllTopicsData() {
        final long epochId = 4L;
        final int numberOfLookBackEpochs = 3;
        final String app = "app";
        final String sdk = "sdk";

        List<String> tableExclusionList = List.of(TopicsTables.BlockedTopicsContract.TABLE);

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

            // Test both cases of app and app-sdk calling getTopics()
            returnedAppSdkTopicsMap.put(Pair.create(app, sdk), currentTopic);
            returnedAppSdkTopicsMap.put(Pair.create(app, /* sdk */ ""), currentTopic);
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, returnedAppSdkTopicsMap);
        }
        mTopicsDao.recordBlockedTopic(topic1);

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        // Real Cache Manager requires loading cache before getTopics() being called.
        mTopicsWorker.loadCache();

        // Verify topics are persisted in the database
        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(Arrays.asList(2L, 3L))
                        .setModelVersions(Arrays.asList(5L, 6L))
                        .setTopics(Arrays.asList(2, 3))
                        .build();
        GetTopicsResult getTopicsResultAppOnly1 = mTopicsWorker.getTopics(app, /* sdk */ "");
        GetTopicsResult getTopicsResultAppSdk1 = mTopicsWorker.getTopics(app, sdk);

        // Since the returned topic list is shuffled, elements have to be verified separately
        assertThat(getTopicsResultAppOnly1.getResultCode())
                .isEqualTo(expectedGetTopicsResult.getResultCode());
        assertThat(getTopicsResultAppOnly1.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTaxonomyVersions());
        assertThat(getTopicsResultAppOnly1.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getModelVersions());
        assertThat(getTopicsResultAppOnly1.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTopics());
        assertThat(getTopicsResultAppSdk1.getResultCode())
                .isEqualTo(expectedGetTopicsResult.getResultCode());
        assertThat(getTopicsResultAppSdk1.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTaxonomyVersions());
        assertThat(getTopicsResultAppSdk1.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getModelVersions());
        assertThat(getTopicsResultAppSdk1.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTopics());

        verify(mMockEpochManager, times(5)).getCurrentEpochId();
        // app only caller has 1 fewer invocation of getTopicsNumberOfLookBackEpochs()
        verify(mMockFlags, times(4)).getTopicsNumberOfLookBackEpochs();

        // Clear all data in database belonging to app except blocked topics table
        mTopicsWorker.clearAllTopicsData(tableExclusionList);
        assertThat(mTopicsDao.retrieveAllBlockedTopics()).isNotEmpty();

        mTopicsWorker.clearAllTopicsData(Collections.emptyList());
        assertThat(mTopicsDao.retrieveAllBlockedTopics()).isEmpty();

        GetTopicsResult emptyGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();

        assertThat(mTopicsWorker.getTopics(app, sdk)).isEqualTo(emptyGetTopicsResult);
        assertThat(mTopicsWorker.getTopics(app, /* sdk */ "")).isEqualTo(emptyGetTopicsResult);

        // Invocation Summary:
        // loadCache(): 1, getTopics(): 4 * 2, clearAllTopicsData(): 2
        verify(mMockEpochManager, times(11)).getCurrentEpochId();
        // app only caller has 1 fewer invocation of getTopicsNumberOfLookBackEpochs(), and it
        // happens twice.
        verify(mMockFlags, times(9)).getTopicsNumberOfLookBackEpochs();
    }

    @Test
    public void testReconcileApplicationUpdate() {
        final String app1 = "app1"; // regular app
        final String app2 = "app2"; // unhandled uninstalled app
        final String app3 = "app3"; // unhandled installed app
        final String app4 = "app4"; // uninstalled app but with only usage
        final String app5 = "app5"; // installed app but with only returned topic
        final String sdk = "sdk";

        final long currentEpochId = 4L;
        final long taxonomyVersion = 1L;
        final long modelVersion = 1L;
        final int numOfLookBackEpochs = 3;
        final int topicsNumberOfTopTopics = 5;
        final int topicsNumberOfRandomTopics = 1;
        final int topicsPercentageForRandomTopic = 5;

        Topic topic1 = Topic.create(/* topic */ 1, taxonomyVersion, modelVersion);
        Topic topic2 = Topic.create(/* topic */ 2, taxonomyVersion, modelVersion);
        Topic topic3 = Topic.create(/* topic */ 3, taxonomyVersion, modelVersion);
        Topic topic4 = Topic.create(/* topic */ 4, taxonomyVersion, modelVersion);
        Topic topic5 = Topic.create(/* topic */ 5, taxonomyVersion, modelVersion);
        Topic topic6 = Topic.create(/* topic */ 6, taxonomyVersion, modelVersion);
        List<Topic> topTopics = List.of(topic1, topic2, topic3, topic4, topic5, topic6);

        // In order to mock Package Manager, context also needs to be mocked to return
        // mocked Package Manager
        PackageManager mockPackageManager = Mockito.mock(PackageManager.class);
        when(mContext.getPackageManager()).thenReturn(mockPackageManager);

        // Mock Package Manager for installed applications
        // Note app2 is not here to mock uninstallation, app3 is here to mock installation
        ApplicationInfo appInfo1 = new ApplicationInfo();
        appInfo1.packageName = app1;
        ApplicationInfo appInfo3 = new ApplicationInfo();
        appInfo3.packageName = app3;
        when(mockPackageManager.getInstalledApplications(Mockito.any()))
                .thenReturn(List.of(appInfo1, appInfo3));

        // As selectAssignedTopicFromTopTopics() randomly assigns a top topic, pass in a Mocked
        // Random object to make the result deterministic.
        //
        // In this test, topic 1, 2, and 6 are supposed to be returned. For each topic, it needs 2
        // random draws: the first is to determine whether to select a random topic, the second is
        // draw the actual topic index.
        MockRandom mockRandom =
                new MockRandom(
                        new long[] {
                            topicsPercentageForRandomTopic, // Will select a regular topic
                            0, // Index of first topic
                            topicsPercentageForRandomTopic, // Will select a regular topic
                            1, // Index of second topic
                            0, // Will select a random topic
                            topicsNumberOfRandomTopics - 1 // Select the last random topic
                        });
        AppUpdateManager appUpdateManager =
                new AppUpdateManager(mTopicsDao, mockRandom, mMockFlags);

        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numOfLookBackEpochs);
        when(mMockFlags.getTopicsNumberOfTopTopics()).thenReturn(topicsNumberOfTopTopics);
        when(mMockFlags.getTopicsNumberOfRandomTopics()).thenReturn(topicsNumberOfRandomTopics);
        when(mMockFlags.getTopicsPercentageForRandomTopic())
                .thenReturn(topicsPercentageForRandomTopic);

        Topic[] topics1 = {topic1, topic2, topic3};
        Topic[] topics2 = {topic4, topic5, topic6};
        for (int numEpoch = 0; numEpoch < numOfLookBackEpochs; numEpoch++) {
            long epochId = currentEpochId - 1 - numEpoch;
            // Persist returned topics into DB
            Topic currentTopic1 = topics1[numEpoch];
            Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap1 = new HashMap<>();
            returnedAppSdkTopicsMap1.put(Pair.create(app1, sdk), currentTopic1);
            mTopicsDao.persistReturnedAppTopicsMap(epochId, returnedAppSdkTopicsMap1);

            Topic currentTopic2 = topics2[numEpoch];
            Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap2 = new HashMap<>();
            returnedAppSdkTopicsMap2.put(Pair.create(app2, sdk), currentTopic2);
            mTopicsDao.persistReturnedAppTopicsMap(epochId, returnedAppSdkTopicsMap2);

            // Persist top topics
            mTopicsDao.persistTopTopics(epochId, topTopics);

            // Since AppUpdateManager evaluates previously installed apps through App Usage, usages
            // should be persisted into database.
            //
            // Note app3 doesn't have usage as newly installation. And app4 only has usage but
            // doesn't have returned topics
            mTopicsDao.recordAppUsageHistory(epochId, app1);
            mTopicsDao.recordAppUsageHistory(epochId, app2);
            mTopicsDao.recordAppUsageHistory(epochId, app4);
            // Persist into AppSdkUsage table to mimic reality but this is unnecessary.
            mTopicsDao.recordUsageHistory(epochId, app1, sdk);
            mTopicsDao.recordUsageHistory(epochId, app2, sdk);
            mTopicsDao.recordUsageHistory(epochId, app4, sdk);
        }
        // Persist returned topic to app 5. Note that the epoch id to persist is older than
        // (currentEpochId - numOfLookBackEpochs). Therefore, app5 won't be handled as a newly
        // installed app.
        mTopicsDao.persistReturnedAppTopicsMap(
                currentEpochId - numOfLookBackEpochs - 1, Map.of(Pair.create(app5, ""), topic1));

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(currentEpochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numOfLookBackEpochs);

        // Initialize a local TopicsWorker to use mocked AppUpdateManager
        TopicsWorker topicsWorker =
                new TopicsWorker(
                        mMockEpochManager,
                        mCacheManager,
                        mBlockedTopicsManager,
                        appUpdateManager,
                        mMockFlags);
        // Reconcile the unhandled uninstalled apps.
        // As PackageManager is mocked, app2 will be identified as unhandled uninstalled app.
        // All data belonging to app2 will be deleted.
        topicsWorker.reconcileApplicationUpdate(mContext);

        // Both reconciling uninstalled apps and installed apps call these mocked functions
        verify(mContext, times(2)).getPackageManager();
        verify(mockPackageManager, times(2)).getInstalledApplications(Mockito.any());

        // App1 should get topics 1, 2, 3
        GetTopicsResult expectedGetTopicsResult1 =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(
                                Arrays.asList(taxonomyVersion, taxonomyVersion, taxonomyVersion))
                        .setModelVersions(Arrays.asList(modelVersion, modelVersion, modelVersion))
                        .setTopics(Arrays.asList(1, 2, 3))
                        .build();
        GetTopicsResult getTopicsResult1 = topicsWorker.getTopics(app1, sdk);
        // Since the returned topic list is shuffled, elements have to be verified separately
        assertThat(getTopicsResult1.getResultCode())
                .isEqualTo(expectedGetTopicsResult1.getResultCode());
        assertThat(getTopicsResult1.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult1.getTaxonomyVersions());
        assertThat(getTopicsResult1.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult1.getModelVersions());
        assertThat(getTopicsResult1.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult1.getTopics());

        // App2 is uninstalled so should return empty topics.
        GetTopicsResult emptyGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();
        assertThat((topicsWorker.getTopics(app2, sdk))).isEqualTo(emptyGetTopicsResult);

        // App4 is uninstalled so usage table should be clear. As it originally doesn't have
        // returned topic, getTopic won't be checked
        assertThat(
                        mTopicsDao.retrieveDistinctAppsFromTables(
                                List.of(TopicsTables.AppUsageHistoryContract.TABLE),
                                List.of(TopicsTables.AppUsageHistoryContract.APP)))
                .doesNotContain(app4);

        // App3 is newly installed and should topics 1, 2, 6
        GetTopicsResult expectedGetTopicsResult3 =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(
                                Arrays.asList(taxonomyVersion, taxonomyVersion, taxonomyVersion))
                        .setModelVersions(Arrays.asList(modelVersion, modelVersion, modelVersion))
                        .setTopics(Arrays.asList(1, 2, 6))
                        .build();
        GetTopicsResult getTopicsResult3 = topicsWorker.getTopics(app3, /* sdk */ "");
        // Since the returned topic list is shuffled, elements have to be verified separately
        assertThat(getTopicsResult3.getResultCode())
                .isEqualTo(expectedGetTopicsResult3.getResultCode());
        assertThat(getTopicsResult3.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult3.getTaxonomyVersions());
        assertThat(getTopicsResult3.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult3.getModelVersions());
        assertThat(getTopicsResult3.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult3.getTopics());

        // App5 has a returned topic in old epoch, so it won't be regarded as newly installed app
        // Therefore, it won't get any topic in recent epochs.
        assertThat((topicsWorker.getTopics(app5, sdk))).isEqualTo(emptyGetTopicsResult);

        // Invocations Summary
        // reconcileInstalledApps(): 1, loadCache(): 1, getTopics(): 4 * 2,
        verify(mMockEpochManager, times(10)).getCurrentEpochId();
        // app3 is passed as app only caller, so it doesn't assign topic to sdk. Therefore, the
        // invocation time for getTopicsNumberOfLookBackEpochs() is 1 time fewer.
        verify(mMockFlags, times(9)).getTopicsNumberOfLookBackEpochs();
        verify(mMockFlags).getTopicsNumberOfTopTopics();
        verify(mMockFlags).getTopicsNumberOfRandomTopics();
        verify(mMockFlags).getTopicsPercentageForRandomTopic();
    }

    @Test
    public void testDeletePackageData() {
        final long epochId = 4L;
        final int numberOfLookBackEpochs = 3;
        final String app = "app";
        final String sdk = "sdk";

        final Pair<String, String> appSdkKey = Pair.create(app, sdk);
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

        GetTopicsResult getTopicsResult = mTopicsWorker.getTopics(app, sdk);

        verify(mMockEpochManager, times(3)).getCurrentEpochId();
        verify(mMockFlags, times(3)).getTopicsNumberOfLookBackEpochs();

        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
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

        // Delete data belonging to the app
        Uri packageUri = Uri.parse("package:" + app);
        mTopicsWorker.deletePackageData(packageUri);

        GetTopicsResult emptyGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();
        assertThat((mTopicsWorker.getTopics(app, sdk))).isEqualTo(emptyGetTopicsResult);

        // Invocations Summary
        // loadCache() : 1, getTopics(): 2 * 2, deletePackageData(): 1
        verify(mMockEpochManager, times(6)).getCurrentEpochId();
        verify(mMockFlags, times(6)).getTopicsNumberOfLookBackEpochs();
    }

    @Test
    public void testHandleAppInstallation() {
        final String appName = "app";
        Uri packageUri = Uri.parse("package:" + appName);
        final long currentEpochId = 4L;
        final long taxonomyVersion = 1L;
        final long modelVersion = 1L;
        final int numOfLookBackEpochs = 3;
        final int topicsNumberOfTopTopics = 5;
        final int topicsNumberOfRandomTopics = 1;
        final int topicsPercentageForRandomTopic = 5;

        // As selectAssignedTopicFromTopTopics() randomly assigns a top topic, pass in a Mocked
        // Random object to make the result deterministic.
        //
        // In this test, topic 1, 2, and 6 are supposed to be returned. For each topic, it needs 2
        // random draws: the first is to determine whether to select a random topic, the second is
        // draw the actual topic index.
        MockRandom mockRandom =
                new MockRandom(
                        new long[] {
                            topicsPercentageForRandomTopic, // Will select a regular topic
                            0, // Index of first topic
                            topicsPercentageForRandomTopic, // Will select a regular topic
                            1, // Index of second topic
                            0, // Will select a random topic
                            topicsNumberOfRandomTopics - 1 // Select the last random topic
                        });
        AppUpdateManager appUpdateManager =
                new AppUpdateManager(mTopicsDao, mockRandom, mMockFlags);
        // Create a local TopicsWorker in order to user above local AppUpdateManager
        TopicsWorker topicsWorker =
                new TopicsWorker(
                        mMockEpochManager,
                        mCacheManager,
                        mBlockedTopicsManager,
                        appUpdateManager,
                        mMockFlags);

        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numOfLookBackEpochs);
        when(mMockFlags.getTopicsNumberOfTopTopics()).thenReturn(topicsNumberOfTopTopics);
        when(mMockFlags.getTopicsNumberOfRandomTopics()).thenReturn(topicsNumberOfRandomTopics);
        when(mMockFlags.getTopicsPercentageForRandomTopic())
                .thenReturn(topicsPercentageForRandomTopic);
        when(mMockEpochManager.getCurrentEpochId()).thenReturn(currentEpochId);

        Topic topic1 = Topic.create(/* topic */ 1, taxonomyVersion, modelVersion);
        Topic topic2 = Topic.create(/* topic */ 2, taxonomyVersion, modelVersion);
        Topic topic3 = Topic.create(/* topic */ 3, taxonomyVersion, modelVersion);
        Topic topic4 = Topic.create(/* topic */ 4, taxonomyVersion, modelVersion);
        Topic topic5 = Topic.create(/* topic */ 5, taxonomyVersion, modelVersion);
        Topic topic6 = Topic.create(/* topic */ 6, taxonomyVersion, modelVersion);
        List<Topic> topTopics = List.of(topic1, topic2, topic3, topic4, topic5, topic6);

        // Persist top topics into database for last 3 epochs
        for (long epochId = currentEpochId - 1;
                epochId >= currentEpochId - numOfLookBackEpochs;
                epochId--) {
            mTopicsDao.persistTopTopics(epochId, topTopics);
        }

        // Verify getTopics() returns nothing before calling assignTopicsToNewlyInstalledApps()
        GetTopicsResult emptyGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();
        assertThat(topicsWorker.getTopics(appName, /* sdk */ "")).isEqualTo(emptyGetTopicsResult);

        // Assign topics to past epochs
        topicsWorker.handleAppInstallation(packageUri);

        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(
                                Arrays.asList(taxonomyVersion, taxonomyVersion, taxonomyVersion))
                        .setModelVersions(Arrays.asList(modelVersion, modelVersion, modelVersion))
                        .setTopics(Arrays.asList(1, 2, 6))
                        .build();
        GetTopicsResult getTopicsResult = topicsWorker.getTopics(appName, /* sdk */ "");

        // Since the returned topic list is shuffled, elements have to be verified separately
        assertThat(getTopicsResult.getResultCode())
                .isEqualTo(expectedGetTopicsResult.getResultCode());
        assertThat(getTopicsResult.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTaxonomyVersions());
        assertThat(getTopicsResult.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getModelVersions());
        assertThat(getTopicsResult.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTopics());

        // Invocations Summary
        // loadCache() : 1, assignTopicsToNewlyInstalledApps() : 1, getTopics(): 2 * 2
        verify(mMockEpochManager, times(6)).getCurrentEpochId();
        // loadCache() : 1, assignTopicsToNewlyInstalledApps() : 1, getTopics(): 1 * 2
        verify(mMockFlags, times(4)).getTopicsNumberOfLookBackEpochs();
        verify(mMockFlags).getTopicsNumberOfTopTopics();
        verify(mMockFlags).getTopicsNumberOfRandomTopics();
        verify(mMockFlags).getTopicsPercentageForRandomTopic();
    }
}
