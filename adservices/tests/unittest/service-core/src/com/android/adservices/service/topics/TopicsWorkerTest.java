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

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit test for {@link TopicsWorker}.
 */
public class TopicsWorkerTest {
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

        DbHelper dbHelper = DbTestUtil.getDbHelperForTest();
        mTopicsDao = new TopicsDao(dbHelper);
        CacheManager cacheManager = new CacheManager(mMockEpochManager,
                mTopicsDao,
                mMockFlags);
        mTopicsWorker = new TopicsWorker(mMockEpochManager,
                cacheManager,
                mMockFlags);
    }

    @Test
    public void testGetTopics() {
        final long epochId = 4L;
        final int numberOfLookBackEpochs = 3;
        final Pair<String, String> appSdkKey = Pair.create("app", "sdk");
        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 4L);
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion = */ 2L,
                /* modelVersion = */ 5L);
        Topic topic3 = Topic.create(/* topic */ 3, /* taxonomyVersion = */ 3L,
                /* modelVersion = */ 6L);
        Topic[] topics = {topic1, topic2, topic3};
        // persist returned topics into DB
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            Topic currentTopic = topics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, Integer> returnedAppSdkTopicsMap = new HashMap<>();
            returnedAppSdkTopicsMap.put(appSdkKey, currentTopic.getTopic());
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, currentTopic.getTaxonomyVersion(),
                    currentTopic.getModelVersion(), returnedAppSdkTopicsMap);
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

        assertThat(getTopicsResult).isEqualTo(expectedGetTopicsResult);

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

        assertThat(getTopicsResult).isEqualTo(expectedGetTopicsResult);

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
        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 4L);
        Topic[] topics = {topic1};
        // persist returned topics into DB
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            Topic currentTopic = topics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, Integer> returnedAppSdkTopicsMap = new HashMap<>();
            returnedAppSdkTopicsMap.put(appSdkKey, currentTopic.getTopic());
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, currentTopic.getTaxonomyVersion(),
                    currentTopic.getModelVersion(), returnedAppSdkTopicsMap);
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
        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 4L);
        Topic[] topics = {topic1};
        // persist returned topics into DB
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            Topic currentTopic = topics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, Integer> returnedAppSdkTopicsMap = new HashMap<>();
            returnedAppSdkTopicsMap.put(appSdkKey, currentTopic.getTopic());
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, currentTopic.getTaxonomyVersion(),
                    currentTopic.getModelVersion(), returnedAppSdkTopicsMap);
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
}
