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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.MockRandom;
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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Unit tests for {@link com.android.adservices.service.topics.CacheManager} */
@SmallTest
public final class CacheManagerTest {
    @SuppressWarnings({"unused"})
    private static final String TAG = "CacheManagerTest";

    @SuppressWarnings({"unused"})
    private final Context mContext = ApplicationProvider.getApplicationContext();

    private TopicsDao mTopicsDao;

    @Mock Flags mMockFlags;
    @Mock EpochManager mMockEpochManager;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        // Erase all existing data.
        DbTestUtil.deleteTable(TopicsTables.TaxonomyContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.AppClassificationTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.CallerCanLearnTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.TopTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.ReturnedTopicContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.UsageHistoryContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.AppUsageHistoryContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.BlockedTopicsContract.TABLE);

        DbHelper dbHelper = DbTestUtil.getDbHelperForTest();
        mTopicsDao = new TopicsDao(dbHelper);

        // Erase all existing data.
        DbTestUtil.deleteTable(TopicsTables.TaxonomyContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.AppClassificationTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.CallerCanLearnTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.TopTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.ReturnedTopicContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.UsageHistoryContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.AppUsageHistoryContract.TABLE);
    }

    @Test
    public void testGetTopics_emptyCache() {
        // The cache is empty when first created.
        CacheManager cacheManager = new CacheManager(mMockEpochManager, mTopicsDao, mMockFlags);

        List<Topic> topics = cacheManager.getTopics(/* numberOfLookBackEpochs = */ 3, "app", "sdk");

        assertThat(topics).isEmpty();
    }

    @Test
    public void testGetTopics() {
        // The cache is empty.
        CacheManager cacheManager = new CacheManager(mMockEpochManager, mTopicsDao, mMockFlags);

        // Assume the current epochId is 4L, we will load cache for returned topics in the last 3
        // epochs: epochId in {3, 2, 1}.
        long currentEpochId = 4L;
        when(mMockEpochManager.getCurrentEpochId()).thenReturn(currentEpochId);
        // Mock Flags to make it independent of configuration
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(3);

        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic3 = Topic.create(/* topic */ 3, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic4 = Topic.create(/* topic */ 4, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic5 = Topic.create(/* topic */ 5, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);

        // EpochId 1
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap1 = new HashMap<>();
        returnedAppSdkTopicsMap1.put(Pair.create("app1", ""), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic1);

        returnedAppSdkTopicsMap1.put(Pair.create("app2", "sdk1"), topic2);
        returnedAppSdkTopicsMap1.put(Pair.create("app2", "sdk3"), topic2);
        returnedAppSdkTopicsMap1.put(Pair.create("app2", "sdk4"), topic2);

        returnedAppSdkTopicsMap1.put(Pair.create("app3", "sdk1"), topic3);

        returnedAppSdkTopicsMap1.put(Pair.create("app5", "sdk1"), topic5);
        returnedAppSdkTopicsMap1.put(Pair.create("app5", "sdk5"), topic5);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 1L, returnedAppSdkTopicsMap1);

        // EpochId 2
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap2 = new HashMap<>();

        returnedAppSdkTopicsMap2.put(Pair.create("app1", ""), topic2);
        returnedAppSdkTopicsMap2.put(Pair.create("app1", "sdk1"), topic2);
        returnedAppSdkTopicsMap2.put(Pair.create("app1", "sdk2"), topic2);

        returnedAppSdkTopicsMap2.put(Pair.create("app2", "sdk1"), topic3);
        returnedAppSdkTopicsMap2.put(Pair.create("app2", "sdk3"), topic3);
        returnedAppSdkTopicsMap2.put(Pair.create("app2", "sdk4"), topic3);

        returnedAppSdkTopicsMap2.put(Pair.create("app3", "sdk1"), topic4);

        returnedAppSdkTopicsMap2.put(Pair.create("app5", "sdk1"), topic1);
        returnedAppSdkTopicsMap2.put(Pair.create("app5", "sdk5"), topic1);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L, returnedAppSdkTopicsMap2);

        // EpochId 3
        // epochId == 3 does not have any topics. This could happen if the epoch computation failed
        // or the device was offline and no epoch computation was done.

        cacheManager.loadCache();

        verify(mMockEpochManager).getCurrentEpochId();
        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();

        // Now look at epochId == 3 only by setting numberOfLookBackEpochs == 1.
        // Since the epochId 3 has empty cache, the results are always empty.
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 1, "app1", "")).isEmpty();
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 1, "app1", "sdk1"))
                .isEmpty();
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 1, "app1", "sdk2"))
                .isEmpty();

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 1, "app3", "sdk1"))
                .isEmpty();

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 1, "app4", "sdk1"))
                .isEmpty();

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 1, "app5", "sdk1"))
                .isEmpty();

        // Now look at epochId in {3, 2} only by setting numberOfLookBackEpochs = 2.
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 2, "app1", ""))
                .isEqualTo(Collections.singletonList(topic2));
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 2, "app1", "sdk1"))
                .isEqualTo(Collections.singletonList(topic2));
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 2, "app1", "sdk2"))
                .isEqualTo(Collections.singletonList(topic2));

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 2, "app3", "sdk1"))
                .isEqualTo(Collections.singletonList(topic4));

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 2, "app4", "sdk1"))
                .isEmpty();

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 2, "app5", "sdk1"))
                .isEqualTo(Collections.singletonList(topic1));

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 2, "app5", "sdk5"))
                .isEqualTo(Collections.singletonList(topic1));

        // Now look at epochId in [1,..,3] by setting numberOfLookBackEpochs = 3.
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 3, "app1", ""))
                .containsExactlyElementsIn(Arrays.asList(topic2, topic1));
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 3, "app1", "sdk1"))
                .containsExactlyElementsIn(Arrays.asList(topic2, topic1));
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 3, "app1", "sdk2"))
                .containsExactlyElementsIn(Arrays.asList(topic2, topic1));

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 3, "app3", "sdk1"))
                .containsExactlyElementsIn(Arrays.asList(topic4, topic3));

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 3, "app4", "sdk1"))
                .isEmpty();

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 3, "app5", "sdk1"))
                .containsExactlyElementsIn(Arrays.asList(topic1, topic5));
    }

    @Test
    public void testGetTopics_someTopicsBlocked() {
        // The cache is empty.
        CacheManager cacheManager = new CacheManager(mMockEpochManager, mTopicsDao, mMockFlags);

        // Assume the current epochId is 4L, we will load cache for returned topics in the last 3
        // epochs: epochId in {3, 2, 1}.
        long currentEpochId = 4L;
        when(mMockEpochManager.getCurrentEpochId()).thenReturn(currentEpochId);
        // Mock Flags to make it independent of configuration
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(3);

        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic3 = Topic.create(/* topic */ 3, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic4 = Topic.create(/* topic */ 4, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic5 = Topic.create(/* topic */ 5, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);

        // EpochId 1
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap1 = new HashMap<>();
        returnedAppSdkTopicsMap1.put(Pair.create("app1", ""), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic1);

        returnedAppSdkTopicsMap1.put(Pair.create("app2", "sdk1"), topic2);
        returnedAppSdkTopicsMap1.put(Pair.create("app2", "sdk3"), topic2);
        returnedAppSdkTopicsMap1.put(Pair.create("app2", "sdk4"), topic2);

        returnedAppSdkTopicsMap1.put(Pair.create("app3", "sdk1"), topic3);

        returnedAppSdkTopicsMap1.put(Pair.create("app5", "sdk1"), topic5);
        returnedAppSdkTopicsMap1.put(Pair.create("app5", "sdk5"), topic5);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 1L, returnedAppSdkTopicsMap1);

        // EpochId 2
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap2 = new HashMap<>();

        returnedAppSdkTopicsMap2.put(Pair.create("app1", ""), topic2);
        returnedAppSdkTopicsMap2.put(Pair.create("app1", "sdk1"), topic2);
        returnedAppSdkTopicsMap2.put(Pair.create("app1", "sdk2"), topic2);

        returnedAppSdkTopicsMap2.put(Pair.create("app2", "sdk1"), topic3);
        returnedAppSdkTopicsMap2.put(Pair.create("app2", "sdk3"), topic3);
        returnedAppSdkTopicsMap2.put(Pair.create("app2", "sdk4"), topic3);

        returnedAppSdkTopicsMap2.put(Pair.create("app3", "sdk1"), topic4);

        returnedAppSdkTopicsMap2.put(Pair.create("app5", "sdk1"), topic1);
        returnedAppSdkTopicsMap2.put(Pair.create("app5", "sdk5"), topic1);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L, returnedAppSdkTopicsMap2);

        // EpochId 3
        // epochId == 3 does not have any topics. This could happen if the epoch computation failed
        // or the device was offline and no epoch computation was done.

        // block topic 2 and 4
        mTopicsDao.recordBlockedTopic(topic2);
        mTopicsDao.recordBlockedTopic(topic4);

        cacheManager.loadCache();

        verify(mMockEpochManager).getCurrentEpochId();
        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();

        // Now look at epochId == 3 only by setting numberOfLookBackEpochs == 1.
        // Since the epochId 3 has empty cache, the results are always empty.
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 1, "app1", "")).isEmpty();
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 1, "app1", "sdk1"))
                .isEmpty();
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 1, "app1", "sdk2"))
                .isEmpty();

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 1, "app3", "sdk1"))
                .isEmpty();

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 1, "app4", "sdk1"))
                .isEmpty();

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 1, "app5", "sdk1"))
                .isEmpty();

        // Now look at epochId in {3, 2} only by setting numberOfLookBackEpochs = 2.
        // Should return topic2, but it's blocked - so emptyList is expected.
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 2, "app1", "")).isEmpty();
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 2, "app1", "sdk1"))
                .isEmpty();
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 2, "app1", "sdk2"))
                .isEmpty();

        // Should return topic4, but it's blocked - so emptyList is expected.
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 2, "app3", "sdk1"))
                .isEmpty();

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 2, "app4", "sdk1"))
                .isEmpty();

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 2, "app5", "sdk1"))
                .isEqualTo(Collections.singletonList(topic1));

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 2, "app5", "sdk5"))
                .isEqualTo(Collections.singletonList(topic1));

        // Now look at epochId in [1,..,3] by setting numberOfLookBackEpochs = 3.
        // Should return topic1 and topic2, but topic2 is blocked - so only topic1 is expected.
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 3, "app1", ""))
                .containsExactlyElementsIn(Arrays.asList(topic1));
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 3, "app1", "sdk1"))
                .containsExactlyElementsIn(Arrays.asList(topic1));
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 3, "app1", "sdk2"))
                .containsExactlyElementsIn(Arrays.asList(topic1));

        // Should return topic3 and topic4, but topic4 is blocked - so only topic3 is expected.
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 3, "app3", "sdk1"))
                .containsExactlyElementsIn(Arrays.asList(topic3));

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 3, "app4", "sdk1"))
                .isEmpty();

        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 3, "app5", "sdk1"))
                .containsExactlyElementsIn(Arrays.asList(topic1, topic5));
    }

    @Test
    public void testGetTopics_shuffling() {
        // In order to test the shuffling behavior, set the test case as looking back to 3 epochs
        // with 2 app/sdk pairs. Therefore, to get Topics for epoch 1 (current is 4) will return 3
        // topics for each app/sdk pair.

        // Mock the random with pre-defined values
        // The way how Collections.shuffle works is from the last element in the list, swap it with
        // a random index. Note index 0 won't be processed as it always swaps with itself
        // So for a list with size 3,
        // if (2,1) is passed into random object, the order will persist
        // if (0,1) is passed in, the order will be reversed. (swap index 2 with 0, rest remains the
        // same)

        // Since returned topics list is generated backwards from epoch 3 to epoch 1, if topics for
        // each epoch is (epoch 1 -> topic 1), (epoch 2 -> topic 2), (epoch 3 -> topic 3)
        // Without shuffling, returned topics list will be (topic3, topic2, topic1) for both app/sdk
        // pair 1
        // and app/sdk/ pair 2
        // With shuffling against above mocked random object, app/sdk pair 1 should remain the
        // order,
        // and app/sdk pair 2 should have returned topics list as (topic1, topic2, topic3)
        MockRandom mockRandom =
                new MockRandom(
                        new long[] {
                            /* first list */
                            2, 1, /* second list */ 0, 1
                        });

        // The cache is empty.
        CacheManager cacheManager = new CacheManager(mMockEpochManager, mTopicsDao, mMockFlags);

        // Assume the current epochId is 4L, we will load cache for returned topics in the last 3
        // epochs: epochId in {3, 2, 1}.
        long currentEpochId = 4L;
        when(mMockEpochManager.getCurrentEpochId()).thenReturn(currentEpochId);
        // Mock Flags to make it independent of configuration
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(3);

        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic3 = Topic.create(/* topic */ 3, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        // EpochId 1
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap1 = new HashMap<>();
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic1);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 1L, returnedAppSdkTopicsMap1);

        // EpochId 2
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic2);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic2);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L, returnedAppSdkTopicsMap1);

        // EpochId 3
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic3);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic3);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 3L, returnedAppSdkTopicsMap1);

        cacheManager.loadCache();

        verify(mMockEpochManager).getCurrentEpochId();
        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();

        // Look back to epoch 1
        // As described above, (app1, sdk1) should have returned topics in order (3, 2, 1), while
        // (app2, sdk2) should have returned topics in order (1, 2, 3).
        assertThat(
                        cacheManager.getTopics(
                                /* numberOfLookBackEpochs = */ 3, "app1", "sdk1", mockRandom))
                .isEqualTo(Arrays.asList(topic3, topic2, topic1));
        assertThat(
                        cacheManager.getTopics(
                                /* numberOfLookBackEpochs = */ 3, "app1", "sdk2", mockRandom))
                .isEqualTo(Arrays.asList(topic1, topic2, topic3));
    }

    @Test
    public void testGetTopics_duplicateTopics() {
        // The cache is empty.
        CacheManager cacheManager = new CacheManager(mMockEpochManager, mTopicsDao, mMockFlags);

        // Assume the current epochId is 4L, we will load cache for returned topics in the last 3
        // epochs: epochId in {3, 2, 1}.
        long currentEpochId = 4L;
        when(mMockEpochManager.getCurrentEpochId()).thenReturn(currentEpochId);
        // Mock Flags to make it independent of configuration
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(3);

        Topic topic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);
        Topic topic2 =
                Topic.create(/* topic */ 2, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);
        Topic topic3 =
                Topic.create(/* topic */ 3, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);

        // EpochId 1
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap1 = new HashMap<>();
        returnedAppSdkTopicsMap1.put(Pair.create("app1", ""), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic1);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 1L, returnedAppSdkTopicsMap1);

        // EpochId 2
        returnedAppSdkTopicsMap1.put(Pair.create("app1", ""), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic2);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic2);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L, returnedAppSdkTopicsMap1);

        // EpochId 3
        returnedAppSdkTopicsMap1.put(Pair.create("app1", ""), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic2);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic3);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 3L, returnedAppSdkTopicsMap1);

        cacheManager.loadCache();

        verify(mMockEpochManager).getCurrentEpochId();
        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();

        // Now look at epochId == 3 only by setting numberOfLookBackEpochs == 1.
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 1, "app1", ""))
                .isEqualTo(Collections.singletonList(topic1));
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 1, "app1", "sdk1"))
                .isEqualTo(Collections.singletonList(topic2));
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 1, "app1", "sdk2"))
                .isEqualTo(Collections.singletonList(topic3));

        // Now look at epochId == [2,3] by setting numberOfLookBackEpochs == 2.
        // Note for (app1, sdk1), both Epoch 2 and Epoch 3 have same value topic 2, which should be
        // deduplicated.
        // Same things for (app1, ""), both Epoch 2 and Epoch 3 have same value topic 1, which
        // should be deduplicated.
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 2, "app1", ""))
                .isEqualTo(Collections.singletonList(topic1));
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 2, "app1", "sdk1"))
                .isEqualTo(Collections.singletonList(topic2));
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 2, "app1", "sdk2"))
                .containsExactlyElementsIn(Arrays.asList(topic2, topic3));

        // Now look at epochId == [1,2,3] by setting numberOfLookBackEpochs == 3.
        // Note for (app1, ""), Epoch1, Epoch 2 and Epoch 3 have same value topic 1, which should be
        // deduplicated.
        // Note for (app1, sdk1), both Epoch 2 and Epoch 3 have same value topic 2, and Epoch 1 has
        // value topic 1, so it should return (topic1, topic2).
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 3, "app1", ""))
                .isEqualTo(Collections.singletonList(topic1));
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 3, "app1", "sdk1"))
                .containsExactlyElementsIn(Arrays.asList(topic1, topic2));
        assertThat(cacheManager.getTopics(/* numberOfLookBackEpochs = */ 3, "app1", "sdk2"))
                .containsExactlyElementsIn(Arrays.asList(topic1, topic2, topic3));
    }

    // Currently SQLException is not thrown. This test needs to be uplifted after SQLException gets
    // handled.
    // TODO(b/230669931): Handle SQLException.
    @Test
    public void testGetTopics_failToLoadFromDb() {
        // The cache is empty when first created.
        CacheManager cacheManager = new CacheManager(mMockEpochManager, mTopicsDao, mMockFlags);

        // Fail to load from DB will have empty cache.

        List<Topic> topics = cacheManager.getTopics(/* numberOfLookBackEpochs = */ 3, "app", "sdk");

        assertThat(topics).isEmpty();
    }

    @Test
    public void testDump() {
        // Trigger the dump to verify no crash
        CacheManager cacheManager = new CacheManager(mMockEpochManager, mTopicsDao, mMockFlags);

        PrintWriter printWriter = new PrintWriter(new Writer() {
            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {

            }

            @Override
            public void flush() throws IOException {

            }

            @Override
            public void close() throws IOException {

            }
        });
        String[] args = new String[]{};
        cacheManager.dump(printWriter, args);
    }

    @Test
    public void testGetKnownTopicsWithConsent_noBlockedTopics() {
        // The cache is empty.
        CacheManager cacheManager = new CacheManager(mMockEpochManager, mTopicsDao, mMockFlags);

        // Assume the current epochId is 4L, we will load cache for returned topics in the last 3
        // epochs: epochId in {3, 2, 1}.
        long currentEpochId = 4L;
        when(mMockEpochManager.getCurrentEpochId()).thenReturn(currentEpochId);
        // Mock Flags to make it independent of configuration
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(3);

        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic4 = Topic.create(/* topic */ 4, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);

        // EpochId 1
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap1 = new HashMap<>();
        returnedAppSdkTopicsMap1.put(Pair.create("app1", ""), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic1);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 1L, returnedAppSdkTopicsMap1);

        // EpochId 2
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap2 = new HashMap<>();
        returnedAppSdkTopicsMap2.put(Pair.create("app3", "sdk1"), topic2);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L, returnedAppSdkTopicsMap2);

        // EpochId 3
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap3 = new HashMap<>();
        returnedAppSdkTopicsMap3.put(Pair.create("app2", "sdk1"), topic4);
        returnedAppSdkTopicsMap3.put(Pair.create("app2", "sdk3"), topic4);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 3L, returnedAppSdkTopicsMap3);

        cacheManager.loadCache();

        verify(mMockEpochManager).getCurrentEpochId();
        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();

        assertThat(cacheManager.getKnownTopicsWithConsent())
                .containsExactly(topic1, topic2, topic4);
    }

    @Test
    public void testGetKnownTopicsWithConsent_blockSomeTopics() {
        // The cache is empty.
        CacheManager cacheManager = new CacheManager(mMockEpochManager, mTopicsDao, mMockFlags);

        // Assume the current epochId is 4L, we will load cache for returned topics in the last 3
        // epochs: epochId in {3, 2, 1}.
        long currentEpochId = 4L;
        when(mMockEpochManager.getCurrentEpochId()).thenReturn(currentEpochId);
        // Mock Flags to make it independent of configuration
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(3);

        Topic topic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);
        Topic topic2 =
                Topic.create(/* topic */ 2, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);
        Topic topic4 =
                Topic.create(/* topic */ 4, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);

        // EpochId 1
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap1 = new HashMap<>();
        returnedAppSdkTopicsMap1.put(Pair.create("app1", ""), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic1);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 1L, returnedAppSdkTopicsMap1);

        // EpochId 2
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap2 = new HashMap<>();
        returnedAppSdkTopicsMap2.put(Pair.create("app3", "sdk1"), topic2);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L, returnedAppSdkTopicsMap2);

        // EpochId 3
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap3 = new HashMap<>();
        returnedAppSdkTopicsMap3.put(Pair.create("app2", "sdk1"), topic4);
        returnedAppSdkTopicsMap3.put(Pair.create("app2", "sdk3"), topic4);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 3L, returnedAppSdkTopicsMap3);

        // Block Topics
        mTopicsDao.recordBlockedTopic(topic2);

        cacheManager.loadCache();

        verify(mMockEpochManager).getCurrentEpochId();
        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();

        assertThat(cacheManager.getKnownTopicsWithConsent()).containsExactly(topic1, topic4);
    }

    @Test
    public void testGetKnownTopicsWithConsent_blockAllTopics() {
        // The cache is empty.
        CacheManager cacheManager = new CacheManager(mMockEpochManager, mTopicsDao, mMockFlags);

        // Assume the current epochId is 4L, we will load cache for returned topics in the last 3
        // epochs: epochId in {3, 2, 1}.
        long currentEpochId = 4L;
        when(mMockEpochManager.getCurrentEpochId()).thenReturn(currentEpochId);
        // Mock Flags to make it independent of configuration
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(3);

        Topic topic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);
        Topic topic2 =
                Topic.create(/* topic */ 2, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);
        Topic topic4 =
                Topic.create(/* topic */ 4, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);

        // EpochId 1
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap1 = new HashMap<>();
        returnedAppSdkTopicsMap1.put(Pair.create("app1", ""), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic1);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 1L, returnedAppSdkTopicsMap1);

        // EpochId 2
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap2 = new HashMap<>();
        returnedAppSdkTopicsMap2.put(Pair.create("app3", "sdk1"), topic2);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L, returnedAppSdkTopicsMap2);

        // EpochId 3
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap3 = new HashMap<>();
        returnedAppSdkTopicsMap3.put(Pair.create("app2", "sdk1"), topic4);
        returnedAppSdkTopicsMap3.put(Pair.create("app2", "sdk3"), topic4);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 3L, returnedAppSdkTopicsMap3);

        // Block Topics
        mTopicsDao.recordBlockedTopic(topic1);
        mTopicsDao.recordBlockedTopic(topic2);
        mTopicsDao.recordBlockedTopic(topic4);

        cacheManager.loadCache();

        verify(mMockEpochManager).getCurrentEpochId();
        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();

        assertThat(cacheManager.getKnownTopicsWithConsent()).isEqualTo(Collections.emptyList());
    }

    @Test
    public void testClearAllTopicsData() {
        // The cache is empty.
        CacheManager cacheManager = new CacheManager(mMockEpochManager, mTopicsDao, mMockFlags);

        Topic topic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);
        Topic topic2 =
                Topic.create(/* topic */ 2, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);
        Topic topic3 =
                Topic.create(/* topic */ 3, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);
        Topic topic4 =
                Topic.create(/* topic */ 4, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);
        Topic topic5 =
                Topic.create(/* topic */ 5, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);
        Topic topic6 =
                Topic.create(/* topic */ 6, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);

        final String app1 = "app1";
        final String app2 = "app2";
        final String sdk1 = "sdk1";
        final String sdk2 = "sdk2";
        final long epochId1 = 1L;
        final long epochId2 = 1L;

        List<String> tableExclusionList = List.of(TopicsTables.BlockedTopicsContract.TABLE);

        // Persist data into tables
        // Below implementation may insert duplicate data into tables, but the duplicated data
        // should be handled correctly
        for (long epochId : new long[] {epochId1, epochId2}) {
            for (String app : new String[] {app1, app2}) {
                for (String sdk : new String[] {sdk1, sdk2}) {
                    mTopicsDao.recordUsageHistory(epochId, app, sdk);
                    mTopicsDao.recordAppUsageHistory(epochId, app);

                    mTopicsDao.persistReturnedAppTopicsMap(
                            epochId, Map.of(Pair.create(app, sdk), topic1));
                    mTopicsDao.persistReturnedAppTopicsMap(
                            epochId, Map.of(Pair.create(app, sdk), topic2));
                }
            }
            mTopicsDao.persistAppClassificationTopics(
                    epochId,
                    Map.of(
                            app1,
                            Arrays.asList(topic1, topic2),
                            app2,
                            Arrays.asList(topic1, topic2)));

            mTopicsDao.persistCallerCanLearnTopics(epochId, Map.of(topic1, Set.of(app1, sdk1)));
            mTopicsDao.persistCallerCanLearnTopics(epochId, Map.of(topic2, Set.of(app2, sdk2)));

            mTopicsDao.persistTopTopics(
                    epochId, List.of(topic1, topic2, topic3, topic4, topic5, topic6));
        }
        mTopicsDao.recordBlockedTopic(topic1);
        mTopicsDao.recordBlockedTopic(topic2);

        // Delete all tables except excluded ones.
        cacheManager.clearAllTopicsData(tableExclusionList);

        for (long epochId : new long[] {epochId1, epochId2}) {
            assertThat(mTopicsDao.retrieveAppUsageMap(epochId)).isEmpty();
            assertThat(mTopicsDao.retrieveAppSdksUsageMap(epochId)).isEmpty();
            assertThat(mTopicsDao.retrieveAppClassificationTopics(epochId)).isEmpty();
            assertThat(mTopicsDao.retrieveTopTopics(epochId)).isEmpty();

            // BlockedTopics Table is not cleared
            assertThat(mTopicsDao.retrieveAllBlockedTopics()).isNotEmpty();
        }
        assertThat(
                        mTopicsDao.retrieveCallerCanLearnTopicsMap(
                                /* current Epoch Id */ 3, /* look back Epochs */ 3))
                .isEmpty();
        assertThat(
                        mTopicsDao.retrieveReturnedTopics(
                                /* current Epoch Id */ 3, /* look back Epochs */ 3))
                .isEmpty();

        cacheManager.clearAllTopicsData(Collections.emptyList());
        assertThat(mTopicsDao.retrieveAllBlockedTopics()).isEmpty();

        // Also verify no topics will be returned as a second check
        cacheManager.loadCache();
        for (String app : new String[] {app1, app2}) {
            for (String sdk : new String[] {sdk1, sdk2}) {
                assertThat(cacheManager.getTopics((int) epochId2 + 1, app, sdk)).isEmpty();
            }
        }
    }
}
