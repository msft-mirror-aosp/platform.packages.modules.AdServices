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

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Precomputed Topics Classifier Test {@link PrecomputedClassifier}.
 */
public class PrecomputedClassifierTest {
    private static final String TEST_LABELS_FILE_PATH = "classifier/labels_test_topics.txt";
    private static final String TEST_APPS_FILE_PATH =
            "classifier/precomputed_test_app_list_chrome_topics.csv";
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final PrecomputedLoader sPrecomputedLoader =
            new PrecomputedLoader(sContext, TEST_LABELS_FILE_PATH, TEST_APPS_FILE_PATH);

    private PrecomputedClassifier sPrecomputedClassifier;

    @Before
    public void setUp() throws IOException {
        sPrecomputedClassifier = new PrecomputedClassifier(sPrecomputedLoader);
    }

    @Test
    public void testClassify_existingApp() {
        // Using What's App. This app has 1 classification topic.
        List<Integer> expectedWhatsAppTopics = Arrays.asList(1379);

        Map<String, List<Integer>> expectedAppTopicsResponse = new HashMap<>();
        expectedAppTopicsResponse.put("com.whatsapp", expectedWhatsAppTopics);

        // TODO(b/226470370): Convert the app to lower case in Epoch Processing
        Map<String, List<Integer>> testResponse =
                sPrecomputedClassifier.classify(new HashSet<>(Arrays.asList("com.whatsapp")));

        // The correct response body should be exactly the same as expectedAppTopicsResponse
        assertThat(testResponse).isEqualTo(expectedAppTopicsResponse);
    }

    @Test
    public void testClassify_nonExistingApp() {
        // Check the non-existing app "random_app"
        Map<String, List<Integer>> testResponse =
                sPrecomputedClassifier.classify(new HashSet<>(Arrays.asList("random_app")));

        // The topics list of "random_app" should be empty
        assertThat(testResponse.get("random_app")).isEmpty();
    }

    @Test
    public void testClassify_emptyStringApp() {
        // Check if input contains empty string or null
        Map<String, List<Integer>> testResponse =
                sPrecomputedClassifier.classify(new HashSet<>(Arrays.asList("", null)));

        // The response body should be empty
        assertThat(testResponse).isEmpty();
    }

    @Test
    public void testClassify_emptyAppList() {
        // Check if input is empty
        Map<String, List<Integer>> testResponse =
                sPrecomputedClassifier.classify(new HashSet<>(new ArrayList<>()));

        // The response body should be empty
        assertThat(testResponse).isEmpty();
    }

    @Test
    public void testGetTopTopics_legalInput() {
        // construction the appTopics map so that when sorting by the number of occurrences,
        // the order of topics are:
        //topic1, topic2, topic3, topic4, topic5, ...,
        Map<String, List<Integer>> appTopics = new HashMap<>();
        appTopics.put("app1", Arrays.asList(1, 2, 3, 4, 5));
        appTopics.put("app2", Arrays.asList(1, 2, 3, 4, 5));
        appTopics.put("app3", Arrays.asList(1, 2, 3, 4, 15));
        appTopics.put("app4", Arrays.asList(1, 2, 3, 13, 16));
        appTopics.put("app5", Arrays.asList(1, 10, 12, 14, 20));

        // This test case should return top 5 topics from appTopics and 1 random topic
        List<Integer> testResponse =
                sPrecomputedClassifier.getTopTopics(
                        appTopics, /* numberOfTopTopics = */ 5, /* numberOfRandomTopics = */ 1);

        assertThat(testResponse.get(0)).isEqualTo(1);
        assertThat(testResponse.get(1)).isEqualTo(2);
        assertThat(testResponse.get(2)).isEqualTo(3);
        assertThat(testResponse.get(3)).isEqualTo(4);
        assertThat(testResponse.get(4)).isEqualTo(5);
        // Check the random topic is not empty
        // The random topic is at the end
        assertThat(testResponse.get(5)).isNotNull();
    }
}
