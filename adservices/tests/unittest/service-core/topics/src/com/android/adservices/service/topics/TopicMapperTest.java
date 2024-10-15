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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.data.topics.Topic;

import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.mockito.Mock;

/** Tests for {@link TopicsMapper}. */
public final class TopicMapperTest extends AdServicesMockitoTestCase {
    @Mock private Context mContext;
    @Mock private Resources mResources;

    /** Test single {@link Topic} to Android resource id mapper. */
    @Test
    public void getResourceIdByTopicTest() {
        int topicId = 1;
        long taxonomyId = 1L;
        long modelVersion = 1L;
        int expectedResourceId = 123;
        when(mResources.getIdentifier(any(), any(), any())).thenReturn(expectedResourceId);
        when(mContext.getResources()).thenReturn(mResources);

        int resourceId =
                TopicsMapper.getResourceIdByTopic(
                        Topic.create(topicId, taxonomyId, modelVersion), mContext);

        assertThat(resourceId).isEqualTo(expectedResourceId);
    }

    /** Test a list of {@link Topic}s to Android resources id mapper. */
    @Test
    public void getResourceIdsByTopicListTest() {
        int firstTopicId = 1;
        int secondTopicId = 2;
        long taxonomyId = 1L;
        long modelVersion = 1L;
        int firstTopicExpectedResourceId = 1;
        int secondTopicExpectedResourceId = 2;
        Topic firstTopic = Topic.create(firstTopicId, taxonomyId, modelVersion);
        Topic secondTopic = Topic.create(secondTopicId, taxonomyId, modelVersion);
        when(mResources.getIdentifier(any(), any(), any()))
                .thenReturn(firstTopicExpectedResourceId)
                .thenReturn(secondTopicExpectedResourceId);
        when(mContext.getResources()).thenReturn(mResources);

        ImmutableList<Integer> topicsListContainingResourceId =
                TopicsMapper.getResourcesIdMapByTopicsList(
                        ImmutableList.of(firstTopic, secondTopic), mContext);

        assertThat(topicsListContainingResourceId)
                .containsExactly(firstTopicExpectedResourceId, secondTopicExpectedResourceId);
    }
}
