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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.topics.GetTopicsResult;
import android.util.Pair;

import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsDao;
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
    private TopicsWorker mTopicsWorker;

    @Mock private EpochManager mMockEpochManager;
    @Mock private TopicsDao mMockTopicsDao;
    @Mock private Flags mMockFlags;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        CacheManager cacheManager = CacheManager.getInstanceForTest(mMockEpochManager,
                mMockTopicsDao,
                mMockFlags);
        mTopicsWorker = TopicsWorker.getInstanceForTest(mMockEpochManager, cacheManager,
                mMockFlags);
    }

    @Test
    public void testGetTopics() {
        final long epochId = 4L;
        final int numberOfLookBackEpochs = 3;
        final Pair<String, String> appSdkKey = Pair.create("app", "sdk");
        Topic topic1 = new Topic("topic1", /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 4L);
        Topic topic2 = new Topic("topic2", /* taxonomyVersion = */ 2L,
                /* modelVersion = */ 5L);
        Topic topic3 = new Topic("topic3", /* taxonomyVersion = */ 3L,
                /* modelVersion = */ 6L);
        Topic[] topics = {topic1, topic2, topic3};
        Map<Long, Map<Pair<String, String>, Topic>> returnedTopicsFromDb = new HashMap<>();
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            returnedTopicsFromDb.put(epochId - numEpoch, new HashMap<>());
            returnedTopicsFromDb.get(epochId - numEpoch).put(appSdkKey, topics[numEpoch - 1]);
        }

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);
        when(mMockTopicsDao.retrieveReturnedTopics(
                /* epochId */ anyLong(), /* numberOfLookBackEpochs */ anyInt()))
                .thenReturn(returnedTopicsFromDb);

        // Real Cache Manager requires loading cache before getTopics() being called.
        mTopicsWorker.loadCache();
        verify(mMockTopicsDao, times(1))
                .retrieveReturnedTopics(/* epochId */ anyLong(),
                        /* numberOfLookBackEpochs */ anyInt());

        GetTopicsResult getTopicsResult = mTopicsWorker.getTopics("app", "sdk");

        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(RESULT_OK)
                        .setTaxonomyVersions(Arrays.asList(1L, 2L, 3L))
                        .setModelVersions(Arrays.asList(4L, 5L, 6L))
                        .setTopics(Arrays.asList("topic1", "topic2", "topic3"))
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
        Map<Long, Map<Pair<String, String>, Topic>> returnedTopicsFromDb = new HashMap<>();

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId);
        when(mMockTopicsDao.retrieveReturnedTopics(/* epochId */ anyLong(),
                /* numberOfLookBackEpochs */ anyInt()))
                .thenReturn(returnedTopicsFromDb);

        // Don't load anything in cache to make it empty
        mTopicsWorker.loadCache();
        verify(mMockTopicsDao, times(1))
                .retrieveReturnedTopics(/* epochId */ anyLong(),
                        /* numberOfLookBackEpochs */ anyInt());

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
        Topic topic1 = new Topic("topic1", /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 4L);
        Topic[] topics = {topic1};
        Map<Long, Map<Pair<String, String>, Topic>> returnedTopicsFromDb = new HashMap<>();
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            returnedTopicsFromDb.put(epochId - numEpoch, new HashMap<>());
            returnedTopicsFromDb.get(epochId - numEpoch).put(appSdkKey, topics[numEpoch - 1]);
        }

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);
        when(mMockTopicsDao.retrieveReturnedTopics(/* epochId */ anyLong(),
                /* numberOfLookBackEpochs */ anyInt()))
                .thenReturn(returnedTopicsFromDb);

        // Real Cache Manager requires loading cache before getTopics() being called.
        mTopicsWorker.loadCache();
        verify(mMockTopicsDao, times(1))
                .retrieveReturnedTopics(/* epochId */ anyLong(),
                        /* numberOfLookBackEpochs */ anyInt());

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
        Topic topic1 = new Topic("topic1", /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 4L);
        Topic[] topics = {topic1};
        Map<Long, Map<Pair<String, String>, Topic>> returnedTopicsFromDb = new HashMap<>();
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            returnedTopicsFromDb.put(epochId - numEpoch, new HashMap<>());
            returnedTopicsFromDb.get(epochId - numEpoch).put(appSdkKey, topics[numEpoch - 1]);
        }

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);
        when(mMockTopicsDao.retrieveReturnedTopics(/* epochId */ anyLong(),
                /* numberOfLookBackEpochs */ anyInt()))
                .thenReturn(returnedTopicsFromDb);

        // Real Cache Manager requires loading cache before getTopics() being called.
        mTopicsWorker.loadCache();
        verify(mMockTopicsDao, times(1))
                .retrieveReturnedTopics(/* epochId */ anyLong(),
                        /* numberOfLookBackEpochs */ anyInt());

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
    public void testLoadCache() {
        final long epochId = 4L;
        final int numberOfLookBackEpochs = 1;
        final Pair<String, String> appSdkKey = Pair.create("app", "sdk");
        Topic topic1 = new Topic("topic1", /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 4L);
        Topic[] topics = {topic1};
        Map<Long, Map<Pair<String, String>, Topic>> returnedTopicsFromDb = new HashMap<>();
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            returnedTopicsFromDb.put(epochId - numEpoch, new HashMap<>());
            returnedTopicsFromDb.get(epochId - numEpoch).put(appSdkKey, topics[numEpoch - 1]);
        }

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);
        when(mMockTopicsDao.retrieveReturnedTopics(/* epochId */ anyLong(),
                /* numberOfLookBackEpochs */ anyInt()))
                .thenReturn(returnedTopicsFromDb);

        mTopicsWorker.loadCache();
        verify(mMockTopicsDao).retrieveReturnedTopics(/* epochId */ anyLong(),
                        /* numberOfLookBackEpochs */ anyInt());
    }

    @Test
    public void testComputeEpoch() {
        mTopicsWorker.computeEpoch();
        verify(mMockEpochManager, times(1)).processEpoch();
    }
}
