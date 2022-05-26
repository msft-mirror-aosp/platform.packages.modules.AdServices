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

package com.android.adservices.service.topics.classifier;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.google.common.collect.ImmutableSet;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * PrecomputedLoader Test {@link PrecomputedLoader}
 */
public class PrecomputedLoaderTest {
    private static final String sLabelsFilePath =
            "classifier/labels_chrome_topics.txt";
    private static final String sTestAppFilePath =
            "classifier/precomputed_test_app_list_chrome_topics.csv";
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final PrecomputedLoader sPrecomputedLoader =
            new PrecomputedLoader(sContext, sLabelsFilePath, sTestAppFilePath);

    @Test
    public void checkLoadedLabels() throws IOException {
        ImmutableSet<Integer> labels = sPrecomputedLoader.retrieveLabels();
        // Check size of list
        // The labels.txt contains 350 topics
        assertThat(labels.size()).isEqualTo(349);

        // Check some labels
        assertThat(labels).containsAtLeast(1693, 1030, 565, 1770);
    }

    @Test
    public void checkLoadedAppTopics() throws IOException {
        Map<String, List<Integer>> appTopic = sPrecomputedLoader.retrieveAppClassificationTopics();
        // Check size of map
        // The app topics file contains 1000 apps + 11 sample apps + 2 test valid topics' apps.
        assertThat(appTopic.size()).isEqualTo(1013);

        // Check whatsApp, chrome and a sample app topics in map
        List<Integer> whatsAppTopics =
                Arrays.asList(1379);
        assertThat(appTopic.get("com.whatsapp")).isEqualTo(whatsAppTopics);

        List<Integer> chromeTopics =
                Arrays.asList(304);
        assertThat(appTopic.get("com.android.chrome")).isEqualTo(chromeTopics);

        List<Integer> sampleAppTopics =
                Arrays.asList(1379, 1142, 669, 16, 14);
        assertThat(appTopic.get("com.example.adservices.samples.topics.sampleapp"))
                .isEqualTo(sampleAppTopics);

        List<Integer> sampleApp4Topics =
                Arrays.asList(529, 1739, 1266, 1013, 315);
        assertThat(appTopic.get("com.example.adservices.samples.topics.sampleapp4"))
                .isEqualTo(sampleApp4Topics);

        // Check if all sample apps have 5 unique topics
        for (int appIndex = 1; appIndex <= 10; appIndex++) {
            assertThat(new HashSet<>(appTopic.get(
                    "com.example.adservices.samples.topics.sampleapp" + appIndex)).size())
                    .isEqualTo(5);
        }

        // Verify that the topics from the file are valid:
        // the valid topic is one of the topic in the labels file.
        // The invalid topics will not be loaded in the app topics map.
        List<Integer> validTestApp1Topics =
                Arrays.asList(211, 78, 16);
        assertThat(appTopic.get("com.example.adservices.valid.topics.testapp1"))
                .isEqualTo(validTestApp1Topics);

        List<Integer> validTestApp2Topics =
                Arrays.asList(1209, 1266);
        assertThat(appTopic.get("com.example.adservices.valid.topics.testapp2"))
                .isEqualTo(validTestApp2Topics);
    }
}
