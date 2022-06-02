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

import static org.junit.Assert.assertThrows;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockRandom;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Precomputed Topics Classifier Test {@link PrecomputedClassifier}.
 *
 * <p><b>
 *     Note: Some tests in this test class are depend on the ordering of topicIds in
 *     adservices/tests/unittest/service-core/assets/classifier/labels_test_topics.txt,
 *     because we will use Random() or MockRandom() to generate random integer index
 *     to get random topicIds. Topics will be selected from the topics list in order
 *     by their index in the topics list.
 * </b>
 */
public class PrecomputedClassifierTest {
    private static final String sLabelsFilePath =
            "classifier/labels_test_topics.txt";
    private static final String sTestAppFilePath =
            "classifier/precomputed_test_app_list_chrome_topics.csv";
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final PrecomputedLoader sPrecomputedLoader =
            new PrecomputedLoader(sContext, sLabelsFilePath, sTestAppFilePath);

    private PrecomputedClassifier sPrecomputedClassifier;

    @Before
    public void setUp() throws IOException {
        sPrecomputedClassifier = new PrecomputedClassifier(sPrecomputedLoader, new Random());
    }

    @Test
    public void testExistingApp() {
        // Using What's App. This app has 1 classification topic.
        List<Integer> expectedWhatsAppTopics =
                Arrays.asList(1379);

        Map<String, List<Integer>> expectedAppTopicsResponse = new HashMap<>();
        expectedAppTopicsResponse.put("com.whatsapp", expectedWhatsAppTopics);

        // TODO(b/226470370): Convert the app to lower case in Epoch Processing
        Map<String, List<Integer>> testResponse =
                sPrecomputedClassifier.classify(
                        new HashSet<>(Arrays.asList("com.whatsapp")));

        // The correct response body should be exactly the same as expectedAppTopicsResponse
        assertThat(testResponse).isEqualTo(expectedAppTopicsResponse);
    }

    @Test
    public void testNonExistingApp() {
        // Check the non-existing app "random_app"
        Map<String, List<Integer>> testResponse =
                sPrecomputedClassifier.classify(
                        new HashSet<>(Arrays.asList("random_app")));

        // The topics list of "random_app" should be empty
        assertThat(testResponse.get("random_app")).isEmpty();
    }

    @Test
    public void testEmptyStringApp() {
        // Check if input contains empty string or null
        Map<String, List<Integer>> testResponse =
                sPrecomputedClassifier.classify(
                        new HashSet<>(Arrays.asList("", null)));

        // The response body should be empty
        assertThat(testResponse).isEmpty();
    }

    @Test
    public void testEmptyAppList() {
        // Check if input is empty
        Map<String, List<Integer>> testResponse =
                sPrecomputedClassifier.classify(
                        new HashSet<>(new ArrayList<>()));

        // The response body should be empty
        assertThat(testResponse).isEmpty();
    }

    @Test
    public void testGetTopTopicsLegalInput() {
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
                sPrecomputedClassifier.getTopTopics(appTopics,
                        /* numberOfTopTopics = */ 5,
                        /* numberOfRandomTopics = */ 1);

        assertThat(testResponse.get(0)).isEqualTo(1);
        assertThat(testResponse.get(1)).isEqualTo(2);
        assertThat(testResponse.get(2)).isEqualTo(3);
        assertThat(testResponse.get(3)).isEqualTo(4);
        assertThat(testResponse.get(4)).isEqualTo(5);
        // Check the random topic is not empty
        // The random topic is at the end
        assertThat(testResponse.get(5)).isNotNull();
    }

    @Test
    public void testGetTopTopicsLargeTopTopicsInput() {
        Map<String, List<Integer>> appTopics = new HashMap<>();
        appTopics.put("app1", Arrays.asList(1, 2, 3, 4, 5));

        // We only have 5 topics but requesting for 15 topics,
        // so we will pad them with 10 random topics.
        List<Integer> testResponse =
                sPrecomputedClassifier.getTopTopics(appTopics,
                        /* numberOfTopTopics = */ 15,
                        /* numberOfRandomTopics = */ 1);

        // The response body should contains 11 topics
        assertThat(testResponse.size()).isEqualTo(16);
    }

    @Test
    public void testGetTopTopicsZeroTopTopics() {
        Map<String, List<Integer>> appTopics = new HashMap<>();
        appTopics.put("app1", Arrays.asList(1, 2, 3, 4, 5));

        // This test case should throw an IllegalArgumentException if numberOfTopTopics is 0
        assertThrows(
                IllegalArgumentException.class,
                () -> sPrecomputedClassifier.getTopTopics(appTopics,
                        /* numberOfTopTopics = */ 0,
                        /* numberOfRandomTopics = */ 1)
        );
    }

    @Test
    public void testGetTopTopicsZeroRandomTopics() {
        Map<String, List<Integer>> appTopics = new HashMap<>();
        appTopics.put("app1", Arrays.asList(1, 2, 3, 4, 5));

        // This test case should throw an IllegalArgumentException if numberOfRandomTopics is 0
        assertThrows(
                IllegalArgumentException.class,
                () -> sPrecomputedClassifier.getTopTopics(appTopics,
                        /* numberOfTopTopics = */ 3,
                        /* numberOfRandomTopics = */ 0)
        );
    }

    @Test
    public void testGetTopTopicsNegativeTopTopics() {
        Map<String, List<Integer>> appTopics = new HashMap<>();
        appTopics.put("app1", Arrays.asList(1, 2, 3, 4, 5));

        // This test case should throw an IllegalArgumentException if numberOfTopTopics is negative
        assertThrows(
                IllegalArgumentException.class,
                () -> sPrecomputedClassifier.getTopTopics(appTopics,
                        /* numberOfTopTopics = */ -5,
                        /* numberOfRandomTopics = */ 1)
        );
    }

    @Test
    public void testGetTopTopicsNegativeRandomTopics() {
        Map<String, List<Integer>> appTopics = new HashMap<>();
        appTopics.put("app1", Arrays.asList(1, 2, 3, 4, 5));

        // This test case should throw an IllegalArgumentException
        // if numberOfRandomTopics is negative
        assertThrows(
                IllegalArgumentException.class,
                () -> sPrecomputedClassifier.getTopTopics(appTopics,
                        /* numberOfTopTopics = */ 3,
                        /* numberOfRandomTopics = */ -1)
        );
    }

    @Test
    public void testGetTopTopicsWithEmptyAppTopicsMap() {
        Map<String, List<Integer>> appTopics = new HashMap<>();

        // The device does not have an app, an empty top topics list should be returned.
        List<Integer> testResponse =
                sPrecomputedClassifier.getTopTopics(appTopics,
                        /* numberOfTopTopics = */ 5,
                        /* numberOfRandomTopics = */ 1);

        // The response body should be empty
        assertThat(testResponse).isEmpty();
    }

    @Test
    public void testGetTopTopicsWithEmptyTopicInEachApp() {
        Map<String, List<Integer>> appTopics = new HashMap<>();

        // app1 and app2 do not have any classification topics.
        appTopics.put("app1", new ArrayList<>());
        appTopics.put("app2", new ArrayList<>());

        // The device have some apps but the topic corresponding to the app cannot be obtained.
        // In this test case, an empty top topics list should be returned.
        List<Integer> testResponse =
                sPrecomputedClassifier.getTopTopics(appTopics,
                        /* numberOfTopTopics = */ 5,
                        /* numberOfRandomTopics = */ 1);

        // The response body should be empty
        assertThat(testResponse).isEmpty();
    }

    @Test
    public void testSelectSingleRandomTopic() throws IOException {
        // In this test, in order to make test result to be deterministic so PrecomputedClassifier
        // has to be mocked to get a random topic. However, real PrecomputedClassifier need to
        // be tested as well. Therefore, real methods will be called for the other top topics.
        //
        // Initialize the PrecomputedClassifier with given MockRandom.
        //
        // Append 3 random positive integers (20, 100, 300) to MockRandom array,
        // their corresponding topicIds in the topics list will not overlap with
        // the topicIds of app1 below.
        PrecomputedClassifier precomputedClassifier =
                new PrecomputedClassifier(sPrecomputedLoader,
                        new MockRandom(new long[]{20, 100, 300}));

        Map<String, List<Integer>> appTopics = new HashMap<>();
        // We label app1 with the first 5 topicIds in topics list.
        appTopics.put("app1", Arrays.asList(1013, 89, 69, 512, 1376));

        List<Integer> testResponse =
                precomputedClassifier.getTopTopics(appTopics,
                        /* numberOfTopTopics = */ 5,
                        /* numberOfRandomTopics = */ 1);

        // The response body should contains 5 topics + 1 random topic
        assertThat(testResponse.size()).isEqualTo(6);

        // In the following test, we need to verify that the mock random integer index
        // can match the correct topic in classifier/precomputed_test_app_list_chrome_topics.csv.
        // "random = n, topicId = m" means this topicId m is from the nth (0-indexed)
        // topicId in the topics list.
        // random = 20, topicId = 541
        assertThat(testResponse.get(5)).isEqualTo(541);
    }

    @Test
    public void testSelectMultipleRandomTopic() throws IOException {
        // In this test, in order to make test result to be deterministic so PrecomputedClassifier
        // has to be mocked to get some random topics. However, real PrecomputedClassifier need to
        // be tested as well. Therefore, real methods will be called for the other top topics.
        //
        // Initialize the PrecomputedClassifier with given mockRandom
        //
        // Randomly select 7 indexes in MockRandom, their corresponding topicIds in the topics list
        // will not overlap with the topicIds of app1 below. 500 in MockRandom exceeds the length
        // of topics list, so what it represents should be 151st (500 % 349 = 151) topicId
        // in topics list.
        PrecomputedClassifier precomputedClassifier =
                new PrecomputedClassifier(sPrecomputedLoader,
                        new MockRandom(new long[]{10, 20, 50, 75, 100, 300, 500}));

        Map<String, List<Integer>> appTopics = new HashMap<>();
        // The topicId we use is verticals4 and its index range is from 0 to 1918.
        // We label app1 with the first 5 topicIds in topics list.
        appTopics.put("app1", Arrays.asList(1013, 89, 69, 512, 1376));

        List<Integer> testResponse =
                precomputedClassifier.getTopTopics(appTopics,
                        /* numberOfTopTopics = */ 5,
                        /* numberOfRandomTopics = */ 7);

        // The response body should contains 5 topics + 7 random topic
        assertThat(testResponse.size()).isEqualTo(12);

        // In the following tests, we need to verify that the mock random integer index
        // can match the correct topic in classifier/precomputed_test_app_list_chrome_topics.csv.
        // "random = n, topicId = m" means this topicId m is from the nth (0-indexed)
        // topicId in the topics list.
        // random = 10, topicId = 1852
        assertThat(testResponse.get(5)).isEqualTo(1852);

        // random = 20, topicId = 541
        assertThat(testResponse.get(6)).isEqualTo(541);

        // random = 50, topicId = 1007
        assertThat(testResponse.get(7)).isEqualTo(1007);

        // random = 75, topicId = 1596
        assertThat(testResponse.get(8)).isEqualTo(1596);

        // random = 100, topicId = 1289
        assertThat(testResponse.get(9)).isEqualTo(1289);

        // random = 300, topicId = 273
        assertThat(testResponse.get(10)).isEqualTo(273);

        // random = 500, size of labels list is 349,
        // index should be 500 % 349 = 151, topicId = 1023
        assertThat(testResponse.get(11)).isEqualTo(1023);
    }

    @Test
    public void testSelectDuplicateRandomTopic() throws IOException {
        // In this test, in order to make test result to be deterministic so PrecomputedClassifier
        // has to be mocked to get a random topic. However, real PrecomputedClassifier need to
        // be tested as well. Therefore, real methods will be called for the other top topics.
        //
        // Initialize the PrecomputedClassifier with given mockRandom
        // Randomly select 6 indexes in MockRandom, their first 5 corresponding topicIds
        // in the topics list will overlap with the topicIds of app1 below.
        PrecomputedClassifier precomputedClassifier =
                new PrecomputedClassifier(sPrecomputedLoader,
                        new MockRandom(new long[]{1, 5, 10, 25, 100, 300}));

        Map<String, List<Integer>> appTopics = new HashMap<>();

        // If the random topic duplicates with the real topic, then pick another random
        // one until no duplicates. In this test, we will let app1 have five topicIds of
        // 89, 612, 1852, 959, 1289. These topicIds are the same as the topicIds in the
        // classifier/precomputed_test_app_list_chrome_topics.csv corresponding to
        // the first five indexes in the MockRandomArray.
        appTopics.put("app1", Arrays.asList(89, 612, 1852, 959, 1289));

        List<Integer> testResponse =
                precomputedClassifier.getTopTopics(appTopics,
                        /* numberOfTopTopics = */ 5,
                        /* numberOfRandomTopics = */ 1);

        // The response body should contain 5 topics + 1 random topic
        assertThat(testResponse.size()).isEqualTo(6);

        // In the following tests, we need to verify that the mock random integer index
        // can match the correct topic in classifier/precomputed_test_app_list_chrome_topics.csv.
        // "random = n, topicId = m" means this topicId m is from the nth (0-indexed)
        // topicId in the topics list.
        // In this test, if we want to select a random topic that does not repeat,
        // we should select the one corresponding to the sixth index
        // in the MockRandom array topicId, i.e. random = 300, topicId = 273
        assertThat(testResponse.get(5)).isEqualTo(273);
    }
}
