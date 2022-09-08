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

import static com.android.adservices.service.topics.classifier.CommonClassifierHelper.getTopTopics;
import static com.android.adservices.service.topics.classifier.CommonClassifierHelper.retrieveLabels;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockRandom;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Tests for {@link CommonClassifierHelper}.
 *
 * <p><b> Note: Some tests in this test class are depend on the ordering of topicIds in
 * adservices/tests/unittest/service-core/assets/classifier/labels_test_topics.txt, because we will
 * use Random() or MockRandom() to generate random integer index to get random topicIds. Topics will
 * be selected from the topics list in order by their index in the topics list. </b>
 */
public class CommonClassifierHelperTest {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String TEST_LABELS_FILE_PATH = "classifier/labels_test_topics.txt";
    private ImmutableList<Integer> mLabels;

    @Before
    public void setUp() {
        mLabels = retrieveLabels(sContext.getAssets(), TEST_LABELS_FILE_PATH);
    }

    @Test
    public void testRetrieveLabels_successfulRead() {
        ImmutableList<Integer> labels = retrieveLabels(sContext.getAssets(), TEST_LABELS_FILE_PATH);
        // Check size of list.
        // The labels.txt contains 350 topics.
        assertThat(labels.size()).isEqualTo(349);

        // Check some labels.
        assertThat(labels).containsAtLeast(1693, 1030, 565, 1770);
    }

    @Test
    public void testRetrieveLabels_emptyListReturnedOnException() {
        ImmutableList<Integer> labels =
                retrieveLabels(sContext.getAssets(), "Incorrect File Name!");
        // Check empty list returned.
        assertThat(labels).isEmpty();
    }

    @Test
    public void testGetTopTopics_legalInput() {
        // construction the appTopics map so that when sorting by the number of occurrences,
        // the order of topics are:
        // topic1, topic2, topic3, topic4, topic5, ...,
        Map<String, List<Integer>> appTopics = new HashMap<>();
        appTopics.put("app1", Arrays.asList(1, 2, 3, 4, 5));
        appTopics.put("app2", Arrays.asList(1, 2, 3, 4, 5));
        appTopics.put("app3", Arrays.asList(1, 2, 3, 4, 15));
        appTopics.put("app4", Arrays.asList(1, 2, 3, 13, 16));
        appTopics.put("app5", Arrays.asList(1, 10, 12, 14, 20));

        // This test case should return top 5 topics from appTopics and 1 random topic
        List<Integer> testResponse =
                getTopTopics(
                        appTopics,
                        mLabels,
                        new Random(),
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
    public void testGetTopTopics_largeTopTopicsInput() {
        Map<String, List<Integer>> appTopics = new HashMap<>();
        appTopics.put("app1", Arrays.asList(1, 2, 3, 4, 5));

        // We only have 5 topics but requesting for 15 topics,
        // so we will pad them with 10 random topics.
        List<Integer> testResponse =
                getTopTopics(
                        appTopics,
                        mLabels,
                        new Random(),
                        /* numberOfTopTopics = */ 15,
                        /* numberOfRandomTopics = */ 1);

        // The response body should contain 11 topics.
        assertThat(testResponse.size()).isEqualTo(16);
    }

    @Test
    public void testGetTopTopics_zeroTopTopics() {
        Map<String, List<Integer>> appTopics = new HashMap<>();
        appTopics.put("app1", Arrays.asList(1, 2, 3, 4, 5));

        // This test case should throw an IllegalArgumentException if numberOfTopTopics is 0.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        getTopTopics(
                                appTopics,
                                mLabels,
                                new Random(),
                                /* numberOfTopTopics = */ 0,
                                /* numberOfRandomTopics = */ 1));
    }

    @Test
    public void testGetTopTopics_zeroRandomTopics() {
        Map<String, List<Integer>> appTopics = new HashMap<>();
        appTopics.put("app1", Arrays.asList(1, 2, 3, 4, 5));

        // This test case should throw an IllegalArgumentException if numberOfRandomTopics is 0.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        getTopTopics(
                                appTopics,
                                mLabels,
                                new Random(),
                                /* numberOfTopTopics = */ 3,
                                /* numberOfRandomTopics = */ 0));
    }

    @Test
    public void testGetTopTopics_negativeTopTopics() {
        Map<String, List<Integer>> appTopics = new HashMap<>();
        appTopics.put("app1", Arrays.asList(1, 2, 3, 4, 5));

        // This test case should throw an IllegalArgumentException if numberOfTopTopics is negative.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        getTopTopics(
                                appTopics,
                                mLabels,
                                new Random(),
                                /* numberOfTopTopics = */ -5,
                                /* numberOfRandomTopics = */ 1));
    }

    @Test
    public void testGetTopTopics_negativeRandomTopics() {
        Map<String, List<Integer>> appTopics = new HashMap<>();
        appTopics.put("app1", Arrays.asList(1, 2, 3, 4, 5));

        // This test case should throw an IllegalArgumentException
        // if numberOfRandomTopics is negative.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        getTopTopics(
                                appTopics,
                                mLabels,
                                new Random(),
                                /* numberOfTopTopics = */ 3,
                                /* numberOfRandomTopics = */ -1));
    }

    @Test
    public void testGetTopTopics_emptyAppTopicsMap() {
        Map<String, List<Integer>> appTopics = new HashMap<>();

        // The device does not have an app, an empty top topics list should be returned.
        List<Integer> testResponse =
                getTopTopics(
                        appTopics,
                        mLabels,
                        new Random(),
                        /* numberOfTopTopics = */ 5,
                        /* numberOfRandomTopics = */ 1);

        // The response body should be empty.
        assertThat(testResponse).isEmpty();
    }

    @Test
    public void testGetTopTopics_emptyTopicInEachApp() {
        Map<String, List<Integer>> appTopics = new HashMap<>();

        // app1 and app2 do not have any classification topics.
        appTopics.put("app1", new ArrayList<>());
        appTopics.put("app2", new ArrayList<>());

        // The device have some apps but the topic corresponding to the app cannot be obtained.
        // In this test case, an empty top topics list should be returned.
        List<Integer> testResponse =
                getTopTopics(
                        appTopics,
                        mLabels,
                        new Random(),
                        /* numberOfTopTopics = */ 5,
                        /* numberOfRandomTopics = */ 1);

        // The response body should be empty
        assertThat(testResponse).isEmpty();
    }

    @Test
    public void testGetTopTopics_selectSingleRandomTopic() {
        // In this test, in order to make test result to be deterministic so CommonClassifierHelper
        // has to be mocked to get a random topic. However, real CommonClassifierHelper need to
        // be tested as well. Therefore, real methods will be called for the other top topics.
        //
        // Initialize MockRandom. Append 3 random positive integers (20, 100, 300) to MockRandom
        // array,
        // their corresponding topicIds in the topics list will not overlap with
        // the topicIds of app1 below.
        MockRandom mockRandom = new MockRandom(new long[] {20, 100, 30});

        Map<String, List<Integer>> appTopics = new HashMap<>();
        // We label app1 with the first 5 topicIds in topics list.
        appTopics.put("app1", Arrays.asList(1013, 89, 69, 512, 1376));

        List<Integer> testResponse =
                getTopTopics(
                        appTopics,
                        mLabels,
                        mockRandom,
                        /* numberOfTopTopics = */ 5,
                        /* numberOfRandomTopics = */ 1);

        // The response body should contain 5 topics + 1 random topic.
        assertThat(testResponse.size()).isEqualTo(6);

        // In the following test, we need to verify that the mock random integer index
        // can match the correct topic in classifier/precomputed_test_app_list_chrome_topics.csv.
        // "random = n, topicId = m" means this topicId m is from the nth (0-indexed)
        // topicId in the topics list.
        // random = 20, topicId = 541
        assertThat(testResponse.get(5)).isEqualTo(541);
    }

    @Test
    public void testGetTopTopics_selectMultipleRandomTopic() {
        // In this test, in order to make test result to be deterministic so CommonClassifierHelper
        // has to be mocked to get some random topics. However, real CommonClassifierHelper need to
        // be tested as well. Therefore, real methods will be called for the other top topics.
        //
        // Initialize MockRandom. Randomly select 7 indices in MockRandom, their corresponding
        // topicIds in the topics list
        // will not overlap with the topicIds of app1 below. 500 in MockRandom exceeds the length
        // of topics list, so what it represents should be 151st (500 % 349 = 151) topicId
        // in topics list.
        MockRandom mockRandom = new MockRandom(new long[] {10, 20, 50, 75, 100, 300, 500});

        Map<String, List<Integer>> appTopics = new HashMap<>();
        // The topicId we use is verticals4 and its index range is from 0 to 1918.
        // We label app1 with the first 5 topicIds in topics list.
        appTopics.put("app1", Arrays.asList(1013, 89, 69, 512, 1376));

        List<Integer> testResponse =
                getTopTopics(
                        appTopics,
                        mLabels,
                        mockRandom,
                        /* numberOfTopTopics = */ 5,
                        /* numberOfRandomTopics = */ 7);

        // The response body should contain 5 topics + 7 random topic.
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
    public void testGetTopTopics_selectDuplicateRandomTopic() {
        // In this test, in order to make test result to be deterministic so CommonClassifierHelper
        // has to be mocked to get a random topic. However, real CommonClassifierHelper need to
        // be tested as well. Therefore, real methods will be called for the other top topics.
        //
        // Initialize MockRandom. Randomly select 6 indices in MockRandom, their first 5
        // corresponding topicIds
        // in the topics list will overlap with the topicIds of app1 below.
        MockRandom mockRandom = new MockRandom(new long[] {1, 5, 10, 25, 100, 300});

        Map<String, List<Integer>> appTopics = new HashMap<>();

        // If the random topic duplicates with the real topic, then pick another random
        // one until no duplicates. In this test, we will let app1 have five topicIds of
        // 89, 612, 1852, 959, 1289. These topicIds are the same as the topicIds in the
        // classifier/precomputed_test_app_list_chrome_topics.csv corresponding to
        // the first five indices in the MockRandomArray.
        appTopics.put("app1", Arrays.asList(89, 612, 1852, 959, 1289));

        List<Integer> testResponse =
                getTopTopics(
                        appTopics,
                        mLabels,
                        mockRandom,
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
