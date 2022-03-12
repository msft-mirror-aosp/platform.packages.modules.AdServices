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

import android.util.Pair;

import androidx.test.filters.SmallTest;

import com.android.adservices.data.topics.Topic;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link com.android.adservices.topics.CacheManager} */
@SmallTest
public final class CacheManagerTest {
    private static final String TAG = "CacheManagerTest";

    @Test
    public void testGetTopics_emptyCache() {
        // The cache is empty.
        CacheManager cacheManager = CacheManager.getInstance();
        List<Topic> topics = cacheManager.getTopics(/* epochId = */ 1L,
                /* numberOfLookBackEpochs = */ 3,
                "app", "sdk");
        assertThat(topics).isEmpty();
    }

    @Test
    public void testGetTopics() {
        // The cache is empty.
        CacheManager cacheManager = CacheManager.getInstance();

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

        Map<Long, Map<Pair<String, String>, Topic>> cache = new HashMap<>();
        cache.put(/* epochId = */ 1L, returnedAppSdkTopicsForEpoch1);
        cache.put(/* epochId = */ 2L, returnedAppSdkTopicsForEpoch2);
        // epochId == 3 does not have any topics. This could happen if the epoch computation failed
        // or the device was offline and no epoch computation was done.
        cache.put(/* epochId = */ 3L, new HashMap<>());

        cacheManager.updateCache(cache);
        // Now look at epochId == 1 only.
        assertThat(cacheManager.getTopics(/* epochId = */ 1L,
                /* numberOfLookBackEpochs = */ 3,
                "app1", "")).isEqualTo(Arrays.asList(topic1));
        assertThat(cacheManager.getTopics(/* epochId = */ 1L,
                /* numberOfLookBackEpochs = */ 3,
                "app1", "sdk1")).isEqualTo(Arrays.asList(topic1));
        assertThat(cacheManager.getTopics(/* epochId = */ 1L,
                /* numberOfLookBackEpochs = */ 3,
                "app1", "sdk2")).isEqualTo(Arrays.asList(topic1));

        assertThat(cacheManager.getTopics(/* epochId = */ 1L,
                /* numberOfLookBackEpochs = */ 3,
                "app3", "sdk1")).isEqualTo(Arrays.asList(topic3));

        assertThat(cacheManager.getTopics(/* epochId = */ 1L,
                /* numberOfLookBackEpochs = */ 3,
                "app4", "sdk1")).isEmpty();

        assertThat(cacheManager.getTopics(/* epochId = */ 1L,
                /* numberOfLookBackEpochs = */ 3,
                "app5", "sdk1")).isEqualTo(Arrays.asList(topic5));


        // Now look at epochId in [1,..,3].
        assertThat(cacheManager.getTopics(/* epochId = */ 3L,
                /* numberOfLookBackEpochs = */ 3,
                "app1", "")).isEqualTo(Arrays.asList(topic2, topic1));
        assertThat(cacheManager.getTopics(/* epochId = */ 3L,
                /* numberOfLookBackEpochs = */ 3,
                "app1", "sdk1")).isEqualTo(Arrays.asList(topic2, topic1));
        assertThat(cacheManager.getTopics(/* epochId = */ 3L,
                /* numberOfLookBackEpochs = */ 3,
                "app1", "sdk2")).isEqualTo(Arrays.asList(topic2, topic1));

        assertThat(cacheManager.getTopics(/* epochId = */ 3L,
                /* numberOfLookBackEpochs = */ 3,
                "app3", "sdk1")).isEqualTo(Arrays.asList(topic4, topic3));

        assertThat(cacheManager.getTopics(/* epochId = */ 3L,
                /* numberOfLookBackEpochs = */ 3,
                "app4", "sdk1")).isEmpty();

        assertThat(cacheManager.getTopics(/* epochId = */ 3L,
                /* numberOfLookBackEpochs = */ 3,
                "app5", "sdk1")).isEqualTo(Arrays.asList(topic1, topic5));
    }
}
