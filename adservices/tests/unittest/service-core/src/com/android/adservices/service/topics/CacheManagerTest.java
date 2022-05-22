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

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.service.Flags;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link com.android.adservices.service.topics.CacheManager} */
@SmallTest
public final class CacheManagerTest {
    private static final String TAG = "CacheManagerTest";
    private final Context mContext = ApplicationProvider.getApplicationContext();

    private TopicsDao mTopicsDao;

    @Mock Flags mMockFlags;
    @Mock EpochManager mMockEpochManager;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        DbHelper dbHelper = DbTestUtil.getDbHelperForTest();
        mTopicsDao = new TopicsDao(dbHelper);
    }

    @Test
    public void testGetTopics_emptyCache() {
        // The cache is empty when first created.
        CacheManager cacheManager = new CacheManager(mMockEpochManager, mTopicsDao, mMockFlags);

        List<Topic> topics = cacheManager.getTopics(
                /* numberOfLookBackEpochs = */ 3,
                "app", "sdk");

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

        // EpochId 1
        Map<Pair<String, String>, Integer> returnedAppSdkTopicsMap1 = new HashMap<>();
        returnedAppSdkTopicsMap1.put(Pair.create("app1", ""), /* topic */ 1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), /* topic */ 1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), /* topic */ 1);

        returnedAppSdkTopicsMap1.put(Pair.create("app2", "sdk1"), /* topic */ 2);
        returnedAppSdkTopicsMap1.put(Pair.create("app2", "sdk3"), /* topic */ 2);
        returnedAppSdkTopicsMap1.put(Pair.create("app2", "sdk4"), /* topic */ 2);

        returnedAppSdkTopicsMap1.put(Pair.create("app3", "sdk1"), /* topic */ 3);

        returnedAppSdkTopicsMap1.put(Pair.create("app5", "sdk1"), /* topic */ 5);
        returnedAppSdkTopicsMap1.put(Pair.create("app5", "sdk5"), /* topic */ 5);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 1L,
                /* taxonomyVersion */ 1L,
                /* modelVersion */ 1L, returnedAppSdkTopicsMap1);

        // EpochId 2
        Map<Pair<String, String>, Integer> returnedAppSdkTopicsMap2 = new HashMap<>();

        returnedAppSdkTopicsMap2.put(Pair.create("app1", ""), /* topic */ 2);
        returnedAppSdkTopicsMap2.put(Pair.create("app1", "sdk1"), /* topic */ 2);
        returnedAppSdkTopicsMap2.put(Pair.create("app1", "sdk2"), /* topic */ 2);

        returnedAppSdkTopicsMap2.put(Pair.create("app2", "sdk1"), /* topic */ 3);
        returnedAppSdkTopicsMap2.put(Pair.create("app2", "sdk3"), /* topic */ 3);
        returnedAppSdkTopicsMap2.put(Pair.create("app2", "sdk4"), /* topic */ 3);

        returnedAppSdkTopicsMap2.put(Pair.create("app3", "sdk1"), /* topic */ 4);

        returnedAppSdkTopicsMap2.put(Pair.create("app5", "sdk1"), /* topic */ 1);
        returnedAppSdkTopicsMap2.put(Pair.create("app5", "sdk5"), /* topic */ 1);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L,
                /* taxonomyVersion */ 1L,
                /* modelVersion */ 1L, returnedAppSdkTopicsMap2);

        // EpochId 3
        // epochId == 3 does not have any topics. This could happen if the epoch computation failed
        // or the device was offline and no epoch computation was done.

        cacheManager.loadCache();

        verify(mMockEpochManager).getCurrentEpochId();
        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();

        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 1L);
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 1L);
        Topic topic3 = Topic.create(/* topic */ 3, /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 1L);
        Topic topic4 = Topic.create(/* topic */ 4, /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 1L);
        Topic topic5 = Topic.create(/* topic */ 5, /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 1L);

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

    // Currently SQLException is not thrown. This test needs to be uplifted after SQLException gets
    // handled.
    // TODO(b/230669931): Handle SQLException.
    @Test
    public void testGetTopics_failToLoadFromDb() {
        // The cache is empty when first created.
        CacheManager cacheManager = new CacheManager(mMockEpochManager, mTopicsDao, mMockFlags);

        // Fail to load from DB will have empty cache.

        List<Topic> topics = cacheManager.getTopics(
                /* numberOfLookBackEpochs = */ 3,
                "app", "sdk");

        assertThat(topics).isEmpty();
    }

    @Test
    public void testDump() throws FileNotFoundException {
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
}
