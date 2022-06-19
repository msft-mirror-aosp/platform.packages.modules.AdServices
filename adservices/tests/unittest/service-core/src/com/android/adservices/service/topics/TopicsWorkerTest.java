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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.topics.GetTopicsResult;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
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
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unit test for {@link TopicsWorker}. */
public class TopicsWorkerTest {
    // Spy the Context to test app reconciliation
    private final Context mContext = spy(ApplicationProvider.getApplicationContext());

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

        AppUpdateManager appUpdateManager = new AppUpdateManager(mTopicsDao);
        CacheManager cacheManager = new CacheManager(mMockEpochManager, mTopicsDao, mMockFlags);
        BlockedTopicsManager blockedTopicsManager = new BlockedTopicsManager(mTopicsDao);
        mTopicsWorker =
                new TopicsWorker(
                        mMockEpochManager,
                        cacheManager,
                        blockedTopicsManager,
                        appUpdateManager,
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

        // loadCache() and getTopics() in CacheManager calls this mock
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

        // loadCache() and getTopics() in CacheManager calls this mock
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

        // loadCache() and getTopics() in CacheManager calls this mock
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

        // loadCache() and getTopics() in CacheManager calls this mock
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
                        .setResultCode(RESULT_OK)
                        .setTaxonomyVersions(Arrays.asList(1L, 2L, 3L))
                        .setModelVersions(Arrays.asList(4L, 5L, 6L))
                        .setTopics(Arrays.asList(1, 2, 3))
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

        verify(mMockEpochManager, times(3)).getCurrentEpochId();
        verify(mMockFlags, times(3)).getTopicsNumberOfLookBackEpochs();

        // Clear all data in database belonging to app except blocked topics table
        mTopicsWorker.clearAllTopicsData(tableExclusionList);
        assertThat(mTopicsDao.retrieveAllBlockedTopics()).isNotEmpty();

        mTopicsWorker.clearAllTopicsData(Collections.emptyList());
        assertThat(mTopicsDao.retrieveAllBlockedTopics()).isEmpty();

        GetTopicsResult emptyGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(RESULT_OK)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();

        assertThat(mTopicsWorker.getTopics(app, sdk)).isEqualTo(emptyGetTopicsResult);
        assertThat(mTopicsWorker.getTopics(app, /* sdk */ "")).isEqualTo(emptyGetTopicsResult);

        // loadCache(): 1, getTopics(): 2 * 2, clearAllTopicsData(): 2
        verify(mMockEpochManager, times(7)).getCurrentEpochId();
        verify(mMockFlags, times(7)).getTopicsNumberOfLookBackEpochs();
    }

    @Test
    public void testReconcileUninstalledApps() {
        final long epochId = 4L;
        final int numberOfLookBackEpochs = 3;
        final String app1 = "app1";
        final String app2 = "app2";
        final String sdk = "sdk";

        // Topics for app1
        Topic topic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion = */ 1L, /* modelVersion = */ 4L);
        Topic topic2 =
                Topic.create(/* topic */ 2, /* taxonomyVersion = */ 2L, /* modelVersion = */ 5L);
        Topic topic3 =
                Topic.create(/* topic */ 3, /* taxonomyVersion = */ 3L, /* modelVersion = */ 6L);

        // Topics for app2
        Topic topic4 =
                Topic.create(/* topic */ 4, /* taxonomyVersion = */ 1L, /* modelVersion = */ 4L);
        Topic topic5 =
                Topic.create(/* topic */ 5, /* taxonomyVersion = */ 2L, /* modelVersion = */ 5L);
        Topic topic6 =
                Topic.create(/* topic */ 6, /* taxonomyVersion = */ 3L, /* modelVersion = */ 6L);

        // In order to mock Package Manager, context also needs to be mocked to return
        // mocked Package Manager
        PackageManager mockPackageManager = Mockito.mock(PackageManager.class);
        when(mContext.getPackageManager()).thenReturn(mockPackageManager);

        // Mock Package Manager for installed applications
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = app1;

        // Package Manager only returns app1 so that app2 is unhandled uninstalled app
        when(mockPackageManager.getInstalledApplications(Mockito.any()))
                .thenReturn(Collections.singletonList(appInfo));

        Topic[] topics1 = {topic1, topic2, topic3};
        Topic[] topics2 = {topic4, topic5, topic6};
        // persist returned topics into DB
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            Topic currentTopic1 = topics1[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap1 = new HashMap<>();
            returnedAppSdkTopicsMap1.put(Pair.create(app1, sdk), currentTopic1);
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, returnedAppSdkTopicsMap1);

            Topic currentTopic2 = topics2[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap2 = new HashMap<>();
            returnedAppSdkTopicsMap2.put(Pair.create(app2, sdk), currentTopic2);
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, returnedAppSdkTopicsMap2);

            // Since AppUpdateManager evaluates previously installed apps through App Usage, usages
            // should be persisted into database
            mTopicsDao.recordAppUsageHistory(numEpoch, app1);
            mTopicsDao.recordAppUsageHistory(numEpoch, app2);
            // Persist into AppSdkUsage table to mimic reality but this is unnecessary.
            mTopicsDao.recordUsageHistory(numEpoch, app1, sdk);
            mTopicsDao.recordUsageHistory(numEpoch, app2, sdk);
        }

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        // Real Cache Manager requires loading cache before getTopics() being called.
        mTopicsWorker.loadCache();

        verify(mMockEpochManager).getCurrentEpochId();
        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();

        // Reconcile the unhandled uninstalled apps.
        // As PackageManager is mocked, app2 will be identified as unhandled uninstalled app.
        // All data belonging to app2 will be deleted.
        mTopicsWorker.reconcileUninstalledApps(mContext);

        verify(mContext).getPackageManager();
        verify(mockPackageManager).getInstalledApplications(Mockito.any());

        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(RESULT_OK)
                        .setTaxonomyVersions(Arrays.asList(1L, 2L, 3L))
                        .setModelVersions(Arrays.asList(4L, 5L, 6L))
                        .setTopics(Arrays.asList(1, 2, 3))
                        .build();
        GetTopicsResult getTopicsResult = mTopicsWorker.getTopics(app1, sdk);
        // Since the returned topic list is shuffled, elements have to be verified separately
        assertThat(getTopicsResult.getResultCode())
                .isEqualTo(expectedGetTopicsResult.getResultCode());
        assertThat(getTopicsResult.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTaxonomyVersions());
        assertThat(getTopicsResult.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getModelVersions());
        assertThat(getTopicsResult.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTopics());

        GetTopicsResult emptyGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(RESULT_OK)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();
        assertThat((mTopicsWorker.getTopics(app2, sdk))).isEqualTo(emptyGetTopicsResult);

        // Invocations Summary
        // loadCache() : 1, reconcileInstalledApps() : 1, getTopics(): 2
        verify(mMockEpochManager, times(4)).getCurrentEpochId();
        verify(mMockFlags, times(4)).getTopicsNumberOfLookBackEpochs();
    }
}
