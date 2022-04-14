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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import android.util.Pair;

import androidx.test.filters.SmallTest;

import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link com.android.adservices.service.topics.CacheManager} */
@SmallTest
public final class CacheManagerTest {
    private static final String TAG = "CacheManagerTest";

    private final Flags mFlags = FlagsFactory.getFlagsForTest();

    @Mock TopicsDao mMockTopicsDao;
    @Mock EpochManager mMockEpochManager;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGetTopics_emptyCache() {
        // The cache is empty when first created.
        CacheManager cacheManager = new CacheManager(mMockEpochManager, mMockTopicsDao, mFlags);

        List<Topic> topics = cacheManager.getTopics(
                /* numberOfLookBackEpochs = */ 3,
                "app", "sdk");

        assertThat(topics).isEmpty();
    }

    @Test
    public void testGetTopics() {
        // The cache is empty.
        CacheManager cacheManager = new CacheManager(mMockEpochManager, mMockTopicsDao, mFlags);

        // Assume the current epochId is 4L, we will load cache for returned topics in the last 3
        // epochs: epochId in {3, 2, 1}.
        long currentEpochId = 4L;
        when(mMockEpochManager.getCurrentEpochId()).thenReturn(currentEpochId);

        // EpochId 1
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsForEpoch1 = new HashMap<>();
        Topic topic1 = new Topic("topic1", /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 1L);
        Topic topic2 = new Topic("topic2", /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 1L);
        Topic topic3 = new Topic("topic3", /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 1L);
        Topic topic4 = new Topic("topic4", /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 1L);
        Topic topic5 = new Topic("topic5", /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 1L);

        returnedAppSdkTopicsForEpoch1.put(Pair.create("app1", ""), topic1);
        returnedAppSdkTopicsForEpoch1.put(Pair.create("app1", "sdk1"), topic1);
        returnedAppSdkTopicsForEpoch1.put(Pair.create("app1", "sdk2"), topic1);

        returnedAppSdkTopicsForEpoch1.put(Pair.create("app2", "sdk1"), topic2);
        returnedAppSdkTopicsForEpoch1.put(Pair.create("app2", "sdk3"), topic2);
        returnedAppSdkTopicsForEpoch1.put(Pair.create("app2", "sdk4"), topic2);

        returnedAppSdkTopicsForEpoch1.put(Pair.create("app3", "sdk1"), topic3);

        returnedAppSdkTopicsForEpoch1.put(Pair.create("app5", "sdk1"), topic5);
        returnedAppSdkTopicsForEpoch1.put(Pair.create("app5", "sdk5"), topic5);

        // EpochId 2
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsForEpoch2 = new HashMap<>();

        returnedAppSdkTopicsForEpoch2.put(Pair.create("app1", ""), topic2);
        returnedAppSdkTopicsForEpoch2.put(Pair.create("app1", "sdk1"), topic2);
        returnedAppSdkTopicsForEpoch2.put(Pair.create("app1", "sdk2"), topic2);

        returnedAppSdkTopicsForEpoch2.put(Pair.create("app2", "sdk1"), topic3);
        returnedAppSdkTopicsForEpoch2.put(Pair.create("app2", "sdk3"), topic3);
        returnedAppSdkTopicsForEpoch2.put(Pair.create("app2", "sdk4"), topic3);

        returnedAppSdkTopicsForEpoch2.put(Pair.create("app3", "sdk1"), topic4);

        returnedAppSdkTopicsForEpoch2.put(Pair.create("app5", "sdk1"), topic1);
        returnedAppSdkTopicsForEpoch2.put(Pair.create("app5", "sdk5"), topic1);

        // EpochId 3
        // epochId == 3 does not have any topics. This could happen if the epoch computation failed
        // or the device was offline and no epoch computation was done.
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsForEpoch3 = new HashMap<>();

        Map<Long, Map<Pair<String, String>, Topic>> cache = new HashMap<>();
        cache.put(/* epochId = */ 1L, returnedAppSdkTopicsForEpoch1);
        cache.put(/* epochId = */ 2L, returnedAppSdkTopicsForEpoch2);
        cache.put(/* epochId = */ 3L, returnedAppSdkTopicsForEpoch3);

        when(mMockTopicsDao.retrieveReturnedTopics(eq(currentEpochId),
                eq(mFlags.getTopicsNumberOfLookBackEpochs() + 1))).thenReturn(cache);

        cacheManager.loadCache();

        // Now look at epochId == 3 only by setting numberOfLookBackEpochs == 1.
        // Since the epochId 3 has empty cache, the results are always empty.
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 1,
                "app1", "")).isEmpty();
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 1,
                "app1", "sdk1")).isEmpty();
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 1,
                "app1", "sdk2")).isEmpty();

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 1,
                "app3", "sdk1")).isEmpty();

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 1,
                "app4", "sdk1")).isEmpty();

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 1,
                "app5", "sdk1")).isEmpty();

        // Now look at epochId in {3, 2} only by setting numberOfLookBackEpochs = 2.
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 2,
                "app1", "")).isEqualTo(Arrays.asList(topic2));
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 2,
                "app1", "sdk1")).isEqualTo(Arrays.asList(topic2));
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 2,
                "app1", "sdk2")).isEqualTo(Arrays.asList(topic2));

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 2,
                "app3", "sdk1")).isEqualTo(Arrays.asList(topic4));

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 2,
                "app4", "sdk1")).isEmpty();

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 2,
                "app5", "sdk1")).isEqualTo(Arrays.asList(topic1));

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 2,
                "app5", "sdk5")).isEqualTo(Arrays.asList(topic1));

        // Now look at epochId in [1,..,3] by setting numberOfLookBackEpochs = 3.
        assertThat(cacheManager.getTopics(
                /* numberOfLookBackEpochs = */ 3,
                "app1", "")).isEqualTo(Arrays.asList(topic2, topic1));
        assertThat(cacheManager.getTopics(
                /* numberOfLookBackEpochs = */ 3,
                "app1", "sdk1")).isEqualTo(Arrays.asList(topic2, topic1));
        assertThat(cacheManager.getTopics(
                /* numberOfLookBackEpochs = */ 3,
                "app1", "sdk2")).isEqualTo(Arrays.asList(topic2, topic1));

        assertThat(cacheManager.getTopics(
                /* numberOfLookBackEpochs = */ 3,
                "app3", "sdk1")).isEqualTo(Arrays.asList(topic4, topic3));

        assertThat(cacheManager.getTopics(
                /* numberOfLookBackEpochs = */ 3,
                "app4", "sdk1")).isEmpty();

        assertThat(cacheManager.getTopics(
                /* numberOfLookBackEpochs = */ 3,
                "app5", "sdk1")).isEqualTo(Arrays.asList(topic1, topic5));
    }

    @Test
    public void testGetTopics_failToLoadFromDb() {
        // The cache is empty when first created.
        CacheManager cacheManager = new CacheManager(mMockEpochManager, mMockTopicsDao, mFlags);

        long currentEpochId = 4L;
        // Fail to load from DB will have empty cache.
        Map<Long, Map<Pair<String, String>, Topic>> emptyCache = new HashMap<>();
        when(mMockTopicsDao.retrieveReturnedTopics(eq(currentEpochId),
                eq(mFlags.getTopicsNumberOfLookBackEpochs() + 1))).thenReturn(emptyCache);

        List<Topic> topics = cacheManager.getTopics(
                /* numberOfLookBackEpochs = */ 3,
                "app", "sdk");

        assertThat(topics).isEmpty();
    }
}
