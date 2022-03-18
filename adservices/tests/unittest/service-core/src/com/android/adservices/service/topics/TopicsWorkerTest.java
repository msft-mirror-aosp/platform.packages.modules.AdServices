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

import static android.adservices.topics.GetTopicsResponse.RESULT_OK;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.topics.GetTopicsResponse;

import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.AdServicesConfig;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Unit test for {@link TopicsWorker}.
 */
public class TopicsWorkerTest {
    private TopicsWorker mTopicsWorker;

    @Mock private EpochManager mMockEpochManager;
    @Mock private CacheManager mMockCacheManager;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mTopicsWorker = TopicsWorker.getInstanceForTest(mMockEpochManager, mMockCacheManager);
    }

    @Test
    public void testGetTopics() {
        List<Topic> topics = new ArrayList<>();
        topics.add(new Topic("topic1", /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 4L));
        topics.add(new Topic("topic2", /* taxonomyVersion = */ 2L,
                /* modelVersion = */ 5L));
        topics.add(new Topic("topic3", /* taxonomyVersion = */ 3L,
                /* modelVersion = */ 6L));

        when(mMockCacheManager.getTopics(
                eq(AdServicesConfig.getTopicsNumberOfLookBackEpochs()),
                eq("app"), eq("sdk")))
                .thenReturn(topics);

        GetTopicsResponse getTopicsResponse = mTopicsWorker.getTopics("app", "sdk");

        GetTopicsResponse expectedGetTopicsResponse = new GetTopicsResponse.Builder()
                .setResultCode(RESULT_OK)
                .setTaxonomyVersions(Arrays.asList(1L, 2L, 3L))
                .setModelVersions(Arrays.asList(4L, 5L, 6L))
                .setTopics(Arrays.asList("topic1", "topic2", "topic3"))
                .build();

        assertThat(getTopicsResponse).isEqualTo(expectedGetTopicsResponse);

        verify(mMockCacheManager, only()).getTopics(
                eq(AdServicesConfig.getTopicsNumberOfLookBackEpochs()),
                eq("app"), eq("sdk"));
    }

    @Test
    public void testGetTopics_emptyCache() {
        // Empty cache.
        when(mMockCacheManager.getTopics(
                eq(AdServicesConfig.getTopicsNumberOfLookBackEpochs()),
                eq("app"), eq("sdk")))
                .thenReturn(new ArrayList<>());

        GetTopicsResponse getTopicsResponse = mTopicsWorker.getTopics("app", "sdk");

        GetTopicsResponse expectedGetTopicsResponse = new GetTopicsResponse.Builder()
                .setResultCode(RESULT_OK)
                .setTaxonomyVersions(Arrays.asList())
                .setModelVersions(Arrays.asList())
                .setTopics(Arrays.asList())
                .build();

        assertThat(getTopicsResponse).isEqualTo(expectedGetTopicsResponse);

        verify(mMockCacheManager, only()).getTopics(
                eq(AdServicesConfig.getTopicsNumberOfLookBackEpochs()),
                eq("app"), eq("sdk"));
    }

    @Test
    public void testGetTopics_appNotInCache() {
        List<Topic> topics = new ArrayList<>();
        topics.add(new Topic("topic1", /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 4L));
        topics.add(new Topic("topic2", /* taxonomyVersion = */ 2L,
                /* modelVersion = */ 5L));
        topics.add(new Topic("topic3", /* taxonomyVersion = */ 3L,
                /* modelVersion = */ 6L));

        when(mMockCacheManager.getTopics(
                eq(AdServicesConfig.getTopicsNumberOfLookBackEpochs()),
                eq("app"), eq("sdk")))
                .thenReturn(topics);

        GetTopicsResponse getTopicsResponse = mTopicsWorker.getTopics("app_not_in_cache",
                "sdk");

        GetTopicsResponse expectedGetTopicsResponse = new GetTopicsResponse.Builder()
                .setResultCode(RESULT_OK)
                .setTaxonomyVersions(Arrays.asList())
                .setModelVersions(Arrays.asList())
                .setTopics(Arrays.asList())
                .build();

        assertThat(getTopicsResponse).isEqualTo(expectedGetTopicsResponse);

        verify(mMockCacheManager, only()).getTopics(
                eq(AdServicesConfig.getTopicsNumberOfLookBackEpochs()),
                eq("app_not_in_cache"), eq("sdk"));
    }

    @Test
    public void testGetTopics_sdkNotInCache() {
        List<Topic> topics = new ArrayList<>();
        topics.add(new Topic("topic1", /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 4L));
        topics.add(new Topic("topic2", /* taxonomyVersion = */ 2L,
                /* modelVersion = */ 5L));
        topics.add(new Topic("topic3", /* taxonomyVersion = */ 3L,
                /* modelVersion = */ 6L));

        when(mMockCacheManager.getTopics(
                eq(AdServicesConfig.getTopicsNumberOfLookBackEpochs()),
                eq("app"), eq("sdk")))
                .thenReturn(topics);

        GetTopicsResponse getTopicsResponse = mTopicsWorker.getTopics("app",
                "sdk_not_in_cache");

        GetTopicsResponse expectedGetTopicsResponse = new GetTopicsResponse.Builder()
                .setResultCode(RESULT_OK)
                .setTaxonomyVersions(Arrays.asList())
                .setModelVersions(Arrays.asList())
                .setTopics(Arrays.asList())
                .build();

        assertThat(getTopicsResponse).isEqualTo(expectedGetTopicsResponse);

        verify(mMockCacheManager, only()).getTopics(
                eq(AdServicesConfig.getTopicsNumberOfLookBackEpochs()),
                eq("app"), eq("sdk_not_in_cache"));
    }

    @Test
    public void testRecordUsage() {
        mTopicsWorker.recordUsage("app", "sdk");
        verify(mMockEpochManager, only()).recordUsageHistory(eq("app"), eq("sdk"));
    }

    @Test
    public void testLoadCache() {
        mTopicsWorker.loadCache();
        verify(mMockCacheManager, only()).loadCache();
    }
}
