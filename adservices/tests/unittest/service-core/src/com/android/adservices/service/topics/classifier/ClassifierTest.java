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

import android.content.Context;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

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
 * Topic Classifier Test {@link Classifier}
 */
public class ClassifierTest {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static Classifier sClassifier;

    @Before
    public void setUp() throws IOException {
        sClassifier = Classifier.getInstance(sContext);
    }

    @Test
    public void checkExistingApp() {
        // Using What's App. This app has 5 classification topics.
        List<String> expectedWhatsAppTopics =
                Arrays.asList(
                        "/Internet & Telecom/Email & Messaging/Voice & Video Chat",
                        "/Beauty & Fitness/Fitness/Yoga & Pilates",
                        "/Internet & Telecom/Email & Messaging/Text & Instant Messaging",
                        "/Games/Computer & Video Games/Simulation Games",
                        "/Online Communities/Social Networks");

        Map<String, List<String>> expectedAppTopicsResponse = new HashMap<>();
        expectedAppTopicsResponse.put("com.whatsapp", expectedWhatsAppTopics);

        // TODO(b/226470370): Convert the app to lower case in Epoch Processing
        Map<String, List<String>> testResponse =
                sClassifier.classify(
                        new HashSet<>(Arrays.asList("com.whatsapp")));

        // The correct response body should be exactly the same as expectedAppTopicsResponse
        assertThat(testResponse).isEqualTo(expectedAppTopicsResponse);
    }

    @Test
    public void checkNonExistingApp() {
        // Check the non-existing app "random_app"
        Map<String, List<String>> testResponse =
                sClassifier.classify(
                        new HashSet<>(Arrays.asList("random_app")));

        // The topics list of "random_app" should be empty
        assertThat(testResponse.get("random_app").size())
                .isEqualTo(0);
    }

    @Test
    public void checkEmptyStringApp() {
        // Check if input contains empty string or null
        Map<String, List<String>> testResponse =
                sClassifier.classify(
                        new HashSet<>(Arrays.asList("", null)));

        // The response body should be empty
        assertThat(testResponse.size()).isEqualTo(0);
    }

    @Test
    public void checkEmptyAppList() {
        // Check if input is empty
        Map<String, List<String>> testResponse =
                sClassifier.classify(
                        new HashSet<>(new ArrayList<>()));

        // The response body should be empty
        assertThat(testResponse.size()).isEqualTo(0);
    }

    @Test
    public void checkGetTopTopicsLegalInput() {
        // construction the appTopics map so that when sorting by the number of occurrences,
        // the order of topics are:
        //topic1, topic2, topic3, topic4, topic5, ...,
        Map<String, List<String>> appTopics = new HashMap<>();
        appTopics.put("app1", Arrays.asList(
                "topic1", "topic2", "topic3", "topic4", "topic5"));
        appTopics.put("app2", Arrays.asList(
                "topic1", "topic2", "topic3", "topic4", "topic5"));
        appTopics.put("app3", Arrays.asList(
                "topic1", "topic2", "topic3", "topic4", "topic15"));
        appTopics.put("app4", Arrays.asList(
                "topic1", "topic2", "topic3", "topic13", "topic16"));
        appTopics.put("app5", Arrays.asList(
                "topic1", "topic10", "topic12", "topic14", "topic20"));

        // This test case should return top 5 topics from appTopics and 1 random topic
        List<String> testResponse =
                sClassifier.getTopTopics(appTopics,
                        /* numberOfTopTopics = */ 5,
                        /* numberOfRandomTopics = */ 1);

        assertThat(testResponse.get(0)).isEqualTo("topic1");
        assertThat(testResponse.get(1)).isEqualTo("topic2");
        assertThat(testResponse.get(2)).isEqualTo("topic3");
        assertThat(testResponse.get(3)).isEqualTo("topic4");
        assertThat(testResponse.get(4)).isEqualTo("topic5");
        // Check the random topic is not empty
        // The random topic is at the end
        assertThat(testResponse.get(5)).isNotEmpty();
    }

    @Test
    public void checkGetTopTopicsLargeTopTopicsInput() {
        Map<String, List<String>> appTopics = new HashMap<>();
        appTopics.put("app1", Arrays.asList("topic1", "topic2", "topic3", "topic4", "topic5"));

        // We only have 5 topics but requesting for 15 topics,
        // so we will pad them with 10 random topics.
        List<String> testResponse =
                sClassifier.getTopTopics(appTopics,
                        /* numberOfTopTopics = */ 15,
                        /* numberOfRandomTopics = */ 1);

        // The response body should contains 11 topics
        assertThat(testResponse.size()).isEqualTo(16);
    }

    @Test
    public void checkGetTopTopicsZeroTopTopics() {
        Map<String, List<String>> appTopics = new HashMap<>();
        appTopics.put("app1", Arrays.asList("topic1", "topic2", "topic3", "topic4", "topic5"));

        // This test case should throw an IllegalArgumentException if numberOfTopTopics is 0
        assertThrows(
                IllegalArgumentException.class,
                () -> sClassifier.getTopTopics(appTopics,
                        /* numberOfTopTopics = */ 0,
                        /* numberOfRandomTopics = */ 1)
        );
    }

    @Test
    public void checkGetTopTopicsZeroRandomTopics() {
        Map<String, List<String>> appTopics = new HashMap<>();
        appTopics.put("app1", Arrays.asList("topic1", "topic2", "topic3", "topic4", "topic5"));

        // This test case should throw an IllegalArgumentException if numberOfRandomTopics is 0
        assertThrows(
                IllegalArgumentException.class,
                () -> sClassifier.getTopTopics(appTopics,
                        /* numberOfTopTopics = */ 3,
                        /* numberOfRandomTopics = */ 0)
        );
    }

    @Test
    public void checkGetTopTopicsNegativeTopTopics() {
        Map<String, List<String>> appTopics = new HashMap<>();
        appTopics.put("app1", Arrays.asList("topic1", "topic2", "topic3", "topic4", "topic5"));

        // This test case should throw an IllegalArgumentException if numberOfTopTopics is negative
        assertThrows(
                IllegalArgumentException.class,
                () -> sClassifier.getTopTopics(appTopics,
                        /* numberOfTopTopics = */ -5,
                        /* numberOfRandomTopics = */ 1)
        );
    }

    @Test
    public void checkGetTopTopicsNegativeRandomTopics() {
        Map<String, List<String>> appTopics = new HashMap<>();
        appTopics.put("app1", Arrays.asList("topic1", "topic2", "topic3", "topic4", "topic5"));

        // This test case should throw an IllegalArgumentException
        // if numberOfRandomTopics is negative
        assertThrows(
                IllegalArgumentException.class,
                () -> sClassifier.getTopTopics(appTopics,
                        /* numberOfTopTopics = */ 3,
                        /* numberOfRandomTopics = */ -1)
        );
    }
}
