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

import com.android.adservices.data.topics.Topic;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Precomputed Topics Classifier Test {@link PrecomputedClassifier}.
 */
public class PrecomputedClassifierTest {
    private static final String TEST_LABELS_FILE_PATH = "classifier/labels_test_topics.txt";
    private static final String TEST_APPS_FILE_PATH =
            "classifier/precomputed_test_app_list.csv";
    private static final String TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH =
            "classifier/classifier_test_assets_metadata.json";

    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private PrecomputedClassifier sPrecomputedClassifier;
    private ModelManager mModelManager;

    @Before
    public void setUp() throws IOException {
        mModelManager = ModelManager.getInstance(sContext);
        sPrecomputedClassifier = new PrecomputedClassifier(mModelManager);
    }

    @Test
    public void testGetInstance() {
        PrecomputedClassifier firstInstance = PrecomputedClassifier.getInstance(sContext);
        PrecomputedClassifier secondInstance = PrecomputedClassifier.getInstance(sContext);

        assertThat(firstInstance).isNotNull();
        assertThat(secondInstance).isNotNull();
        // Verify singleton behaviour.
        assertThat(firstInstance).isEqualTo(secondInstance);
    }

    @Test
    public void testClassify_existingApp() {
        // Using What's App. This app has 1 classification topic.
        List<Topic> expectedWhatsAppTopics = createTopics(Arrays.asList(222));

        Map<String, List<Topic>> expectedAppTopicsResponse = new HashMap<>();
        expectedAppTopicsResponse.put("com.whatsapp", expectedWhatsAppTopics);

        // TODO(b/226470370): Convert the app to lower case in Epoch Processing
        Map<String, List<Topic>> testResponse =
                sPrecomputedClassifier.classify(new HashSet<>(Arrays.asList("com.whatsapp")));

        // The correct response body should be exactly the same as expectedAppTopicsResponse
        assertThat(testResponse).isEqualTo(expectedAppTopicsResponse);
    }

    @Test
    public void testClassify_nonExistingApp() {
        // Check the non-existing app "random_app"
        Map<String, List<Topic>> testResponse =
                sPrecomputedClassifier.classify(new HashSet<>(Arrays.asList("random_app")));

        // The topics list of "random_app" should be empty
        assertThat(testResponse.get("random_app")).isEmpty();
    }

    @Test
    public void testClassify_emptyStringApp() {
        // Check if input contains empty string or null
        Map<String, List<Topic>> testResponse =
                sPrecomputedClassifier.classify(new HashSet<>(Arrays.asList("", null)));

        // The response body should be empty
        assertThat(testResponse).isEmpty();
    }

    @Test
    public void testClassify_emptyAppList() {
        // Check if input is empty
        Map<String, List<Topic>> testResponse =
                sPrecomputedClassifier.classify(new HashSet<>(new ArrayList<>()));

        // The response body should be empty
        assertThat(testResponse).isEmpty();
    }

    @Test
    public void testGetTopTopics_legalInput() {
        // construction the appTopics map so that when sorting by the number of occurrences,
        // the order of topics are:
        // topic1, topic2, topic3, topic4, topic5, ...,
        Map<String, List<Topic>> appTopics = new HashMap<>();
        appTopics.put("app1", createTopics(Arrays.asList(1, 2, 3, 4, 5)));
        appTopics.put("app2", createTopics(Arrays.asList(1, 2, 3, 4, 5)));
        appTopics.put("app3", createTopics(Arrays.asList(1, 2, 3, 4, 16)));
        appTopics.put("app4", createTopics(Arrays.asList(1, 2, 3, 13, 17)));
        appTopics.put("app5", createTopics(Arrays.asList(1, 2, 11, 14, 18)));
        appTopics.put("app6", createTopics(Arrays.asList(1, 10, 12, 15, 19)));

        // This test case should return top 5 topics from appTopics and 1 random topic
        List<Topic> testResponse =
                sPrecomputedClassifier.getTopTopics(
                        appTopics, /* numberOfTopTopics = */ 5, /* numberOfRandomTopics = */ 1);

        assertThat(testResponse.get(0)).isEqualTo(createTopic(1));
        assertThat(testResponse.get(1)).isEqualTo(createTopic(2));
        assertThat(testResponse.get(2)).isEqualTo(createTopic(3));
        assertThat(testResponse.get(3)).isEqualTo(createTopic(4));
        assertThat(testResponse.get(4)).isEqualTo(createTopic(5));
        // Check the random topic is not empty
        // The random topic is at the end
        assertThat(testResponse.get(5)).isNotNull();
    }

    private Topic createTopic(int topicId) {
        return Topic.create(
                topicId,
                sPrecomputedClassifier.getLabelsVersion(),
                sPrecomputedClassifier.getModelVersion());
    }

    private List<Topic> createTopics(List<Integer> topicIds) {
        return topicIds.stream().map(this::createTopic).collect(Collectors.toList());
    }
}
