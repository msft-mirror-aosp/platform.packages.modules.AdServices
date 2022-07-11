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

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** PrecomputedLoader Test {@link PrecomputedLoader}. */
public class PrecomputedLoaderTest {
    private static final String TEST_LABELS_FILE_PATH = "classifier/labels_test_topics.txt";
    private static final String TEST_APPS_FILE_PATH =
            "classifier/precomputed_test_app_list.csv";
    private static final String TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH =
            "classifier/classifier_test_assets_metadata.json";
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final PrecomputedLoader sPrecomputedLoader =
            new PrecomputedLoader(
                    sContext,
                    TEST_LABELS_FILE_PATH,
                    TEST_APPS_FILE_PATH,
                    TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH);

    @Test
    public void testRetrieveLabels_successfulRead() {
        ImmutableList<Integer> labels = sPrecomputedLoader.retrieveLabels();
        // Check size of list.
        // The labels.txt contains 350 topics.
        assertThat(labels.size()).isEqualTo(349);

        // Check some labels.
        assertThat(labels).containsAtLeast(15, 75, 150, 349);
    }

    @Test
    public void testLoadedAppTopics() {
        Map<String, List<Integer>> appTopic = sPrecomputedLoader.retrieveAppClassificationTopics();
        // Check size of map
        // The app topics file contains 1000 apps + 11 sample apps + 2 test valid topics' apps
        // + 1 end2end test app.
        assertThat(appTopic.size()).isEqualTo(1014);

        // Check whatsApp, chrome and a sample app topics in map
        // The topicId of "com.whatsapp" in assets/precomputed_test_app_list.csv
        // is 222
        List<Integer> whatsAppTopics = Arrays.asList(222);
        assertThat(appTopic.get("com.whatsapp")).isEqualTo(whatsAppTopics);

        // The topicId of "com.android.chrome" in assets/precomputed_test_app_list.csv
        // is 148
        List<Integer> chromeTopics = Arrays.asList(148);
        assertThat(appTopic.get("com.android.chrome")).isEqualTo(chromeTopics);

        // The topicIds of "com.example.adservices.samples.topics.sampleapp" in
        // assets/precomputed_test_app_list.csv are 222, 223, 116, 243, 254
        String sampleAppPrefix = "com.example.adservices.samples.topics.sampleapp";
        List<Integer> sampleAppTopics = Arrays.asList(222, 223, 116, 243, 254);
        assertThat(appTopic.get(sampleAppPrefix)).isEqualTo(sampleAppTopics);

        // The topicIds of "com.example.adservices.samples.topics.sampleapp4" in
        // assets/precomputed_test_app_list.csv are 253, 146, 277, 59,127
        List<Integer> sampleApp4Topics = Arrays.asList(253, 146, 277, 59,127);
        assertThat(appTopic.get(sampleAppPrefix + "4")).isEqualTo(sampleApp4Topics);

        // Check if all sample apps have 5 unique topics
        for (int appIndex = 1; appIndex <= 10; appIndex++) {
            assertThat(new HashSet<>(appTopic.get(sampleAppPrefix + appIndex)).size()).isEqualTo(5);
        }

        // Verify that the topics from the file are valid:
        // the valid topic is one of the topic in the labels file.
        // The invalid topics will not be loaded in the app topics map.
        String validTestAppPrefix = "com.example.adservices.valid.topics.testapp";

        // The valid topicIds of "com.example.adservices.valid.topics.testapp1" in
        // assets/precomputed_test_app_list.csv are 211, 78, 16
        List<Integer> validTestApp1Topics = Arrays.asList(211, 78, 16);
        assertThat(appTopic.get(validTestAppPrefix + "1")).isEqualTo(validTestApp1Topics);

        // The valid topicIds of "com.example.adservices.valid.topics.testapp2" in
        // assets/precomputed_test_app_list.csv are 143, 15
        List<Integer> validTestApp2Topics = Arrays.asList(143, 15);
        assertThat(appTopic.get(validTestAppPrefix + "2")).isEqualTo(validTestApp2Topics);
    }
}
