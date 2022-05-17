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

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/**
 * TopicId to TopicName utility test {@link TopicIdNameUtil}
 */
public class TopicIdNameUtilTest {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static TopicIdNameUtil sTopicIdNameUtil;

    @Before
    public void setUp() throws IOException {
        sTopicIdNameUtil = new TopicIdNameUtil(sContext);
    }

    @Test
    public void checkLoadedTopicIdToNameMap() throws IOException {
        ImmutableMap<Integer, String> topicIdToName = sTopicIdNameUtil.retrieveTopicIdToName();

        // Check size of map
        // The topicId to topicName file contains 349 topics.
        assertThat(topicIdToName.size()).isEqualTo(349);

        // Check some topicIds to topicNames in the map
        assertThat(topicIdToName.get(1013)).isEqualTo("/Autos & Vehicles/Classic Vehicles");

        assertThat(topicIdToName.get(1299)).isEqualTo(
                "/Science/Machine Learning & Artificial Intelligence");

        assertThat(topicIdToName.get(1808)).isEqualTo(
                "/Beauty & Fitness/Face & Body Care/Antiperspirants, Deodorants & Body Sprays");
    }
}
