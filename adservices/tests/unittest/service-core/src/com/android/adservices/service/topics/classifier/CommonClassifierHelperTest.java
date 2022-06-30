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

import static com.android.adservices.service.topics.classifier.CommonClassifierHelper.computeClassifierAssetChecksum;
import static com.android.adservices.service.topics.classifier.CommonClassifierHelper.getAssetsMetadata;
import static com.android.adservices.service.topics.classifier.CommonClassifierHelper.getTopTopics;
import static com.android.adservices.service.topics.classifier.CommonClassifierHelper.retrieveLabels;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockRandom;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

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
    private static final String TEST_PRECOMPUTED_FILE_PATH =
            "classifier/precomputed_test_app_list.csv";
    private static final String TEST_CLASSIFIER_ASSETS_METADATA_PATH =
            "classifier/classifier_test_assets_metadata.json";
    private static final String PRODUCTION_LABELS_FILE_PATH = "classifier/labels_topics.txt";
    private static final String PRODUCTION_PRECOMPUTED_FILE_PATH =
            "classifier/precomputed_app_list.csv";
    private static final String PRODUCTION_CLASSIFIER_ASSETS_METADATA_PATH =
            "classifier/classifier_assets_metadata.json";
    private ImmutableList<Integer> testLabels;
    private ImmutableMap<String, ImmutableMap<String, String>> testClassifierAssetsMetadata;
    private ImmutableList<Integer> productionLabels;
    private ImmutableMap<String, ImmutableMap<String, String>> productionClassifierAssetsMetadata;

    @Before
    public void setUp() {
        testLabels = retrieveLabels(sContext.getAssets(), TEST_LABELS_FILE_PATH);
        testClassifierAssetsMetadata = getAssetsMetadata(
                sContext.getAssets(), TEST_CLASSIFIER_ASSETS_METADATA_PATH);
        productionLabels = retrieveLabels(sContext.getAssets(), PRODUCTION_LABELS_FILE_PATH);
        productionClassifierAssetsMetadata = getAssetsMetadata(
                sContext.getAssets(), PRODUCTION_CLASSIFIER_ASSETS_METADATA_PATH);
    }

    @Test
    public void testRetrieveLabels_successfulRead() {
        // Test the labels list in test assets
        // Check size of list.
        // The labels_test_topics.txt contains 349 topics.
        assertThat(testLabels.size()).isEqualTo(349);

        // Check some labels.
        assertThat(testLabels).containsAtLeast(5, 100, 250, 349);


        // Test the labels list in production assets
        // Check size of list.
        // The labels_topics.txt contains 349 topics.
        assertThat(productionLabels.size()).isEqualTo(349);

        // Check some labels.
        assertThat(productionLabels).containsAtLeast(10, 200, 270, 320);
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
                        testLabels,
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
                        testLabels,
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
                                testLabels,
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
                                testLabels,
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
                                testLabels,
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
                                testLabels,
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
                        testLabels,
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
                        testLabels,
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
        MockRandom mockRandom = new MockRandom(new long[] {20, 100, 300});

        Map<String, List<Integer>> appTopics = new HashMap<>();
        // We label app1 with the first 5 topicIds in topics list.
        appTopics.put("app1", Arrays.asList(253, 146, 277, 59, 127));

        // Test the random topic with labels file in test assets.
        List<Integer> testResponse =
                getTopTopics(
                        appTopics,
                        testLabels,
                        mockRandom,
                        /* numberOfTopTopics = */ 5,
                        /* numberOfRandomTopics = */ 1);

        // The response body should contain 5 topics + 1 random topic.
        assertThat(testResponse.size()).isEqualTo(6);

        // In the following test, we need to verify that the mock random integer index
        // can match the correct topic in classifier/precomputed_test_app_list_chrome_topics.csv.
        // "random = n, topicId = m" means this topicId m is from the nth (0-indexed)
        // topicId in the topics list.
        // random = 20, topicId = 21
        assertThat(testResponse.get(5)).isEqualTo(21);

        // Test the random topic with labels file in production assets.
        List<Integer> productionResponse =
                getTopTopics(
                        appTopics,
                        productionLabels,
                        new MockRandom(new long[] {50, 100, 300}),
                        /* numberOfTopTopics = */ 5,
                        /* numberOfRandomTopics = */ 1);

        // The response body should contain 5 topics + 1 random topic.
        assertThat(productionResponse.size()).isEqualTo(6);

        // In the following test, we need to verify that the mock random integer index
        // can match the correct topic in classifier/precomputed_app_list_chrome_topics.csv.
        // "random = n, topicId = m" means this topicId m is from the nth (0-indexed)
        // topicId in the topics list.
        // random = 20, topicId = 21
        assertThat(productionResponse.get(5)).isEqualTo(51);
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
        appTopics.put("app1", Arrays.asList(34, 89, 69, 349, 241));

        List<Integer> testResponse =
                getTopTopics(
                        appTopics,
                        testLabels,
                        mockRandom,
                        /* numberOfTopTopics = */ 5,
                        /* numberOfRandomTopics = */ 7);

        // The response body should contain 5 topics + 7 random topic.
        assertThat(testResponse.size()).isEqualTo(12);

        // In the following tests, we need to verify that the mock random integer index
        // can match the correct topic in classifier/precomputed_test_app_list_chrome_topics.csv.
        // "random = n, topicId = m" means this topicId m is from the nth (0-indexed)
        // topicId in the topics list.
        // random = 10, topicId = 11
        assertThat(testResponse.get(5)).isEqualTo(11);

        // random = 20, topicId = 21
        assertThat(testResponse.get(6)).isEqualTo(21);

        // random = 50, topicId = 51
        assertThat(testResponse.get(7)).isEqualTo(51);

        // random = 75, topicId = 76
        assertThat(testResponse.get(8)).isEqualTo(76);

        // random = 100, topicId = 101
        assertThat(testResponse.get(9)).isEqualTo(101);

        // random = 300, topicId = 301
        assertThat(testResponse.get(10)).isEqualTo(301);

        // random = 500, size of labels list is 349,
        // index should be 500 % 349 = 151, topicId = 152
        assertThat(testResponse.get(11)).isEqualTo(152);
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
        // 2, 6, 11, 26, 101. These topicIds are the same as the topicIds in the
        // classifier/precomputed_test_app_list_chrome_topics.csv corresponding to
        // the first five indices in the MockRandomArray.
        appTopics.put("app1", Arrays.asList(2, 6, 11, 26, 101));

        List<Integer> testResponse =
                getTopTopics(
                        appTopics,
                        testLabels,
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
        // in the MockRandom array topicId, i.e. random = 300, topicId = 301
        assertThat(testResponse.get(5)).isEqualTo(301);
    }

    @Test
    public void testGetTestClassifierAssetsMetadata_correctFormat() {
        // There should contain 6 assets and 1 property in classifier_test_assets_metadata.json.
        // The asset without "asset_name" or "property" will not be stored in the map.
        assertThat(testClassifierAssetsMetadata).hasSize(7);

        // The property of metadata with correct format should contain 3 attributions:
        // "taxonomy_type", "taxonomy_version", "updated_date".
        // The key name of property is "version_info"
        assertThat(testClassifierAssetsMetadata.get("version_info")).hasSize(3);
        assertThat(testClassifierAssetsMetadata.get("version_info").keySet()).containsExactly(
                "taxonomy_type", "taxonomy_version", "updated_date");

        // The property "version_info" should have attribution "taxonomy_version"
        // and its value should be "1.0".
        assertThat(testClassifierAssetsMetadata.get("version_info").get("taxonomy_version"))
                .isEqualTo("1.0");

        // The property "version_info" should have attribution "taxonomy_type"
        // and its value should be "chrome".
        assertThat(testClassifierAssetsMetadata.get("version_info").get("taxonomy_type"))
                .isEqualTo("chrome");

        // The metadata of 1 asset with correct format should contain 4 attributions:
        // "asset_version", "path", "checksum", "updated_date".
        // Check if "labels_topics" asset has the correct format.
        assertThat(testClassifierAssetsMetadata.get("labels_topics")).hasSize(4);
        assertThat(testClassifierAssetsMetadata.get("labels_topics").keySet()).containsExactly(
                "asset_version", "path", "checksum", "updated_date");

        // The asset "labels_topics" should have attribution "asset_version" and its value should be
        // "1.0"
        assertThat(testClassifierAssetsMetadata.get("labels_topics").get("asset_version"))
                .isEqualTo("1.0");

        // The asset "labels_topics" should have attribution "path" and its value should be
        // "assets/classifier/labels_test_topics.txt"
        assertThat(testClassifierAssetsMetadata.get("labels_topics").get("path"))
                .isEqualTo("assets/classifier/labels_test_topics.txt");

        // The asset "labels_topics" should have attribution "updated_date" and its value should be
        // "2022-06-15"
        assertThat(testClassifierAssetsMetadata.get("labels_topics").get("updated_date"))
                .isEqualTo("2022-06-15");

        // There should contain 4 metadata attributions in asset "topic_id_to_name"
        assertThat(testClassifierAssetsMetadata.get("topic_id_to_name")).hasSize(4);

        // The asset "topic_id_to_name" should have attribution "path" and its value should be
        // "assets/classifier/topic_id_to_name.csv"
        assertThat(testClassifierAssetsMetadata.get("topic_id_to_name").get("path"))
                .isEqualTo("assets/classifier/topic_id_to_name.csv");

        // The asset "precomputed_app_list" should have attribution "checksum" and
        // its value should be "50a5ea88e8789d689544a988668aacc7814feff2f6393e5497dad4e08416b0da"
        assertThat(testClassifierAssetsMetadata.get("precomputed_app_list").get("checksum"))
                .isEqualTo("50a5ea88e8789d689544a988668aacc7814feff2f6393e5497dad4e08416b0da");
    }

    @Test
    public void testGetProductionClassifierAssetsMetadata_correctFormat() {
        // There should contain 4 assets and 1 property in classifier_assets_metadata.json.
        assertThat(productionClassifierAssetsMetadata).hasSize(5);

        // The property of metadata in production metadata should contain 4 attributions:
        // "taxonomy_type", "taxonomy_version", "updated_date".
        // The key name of property is "version_info"
        assertThat(productionClassifierAssetsMetadata.get("version_info")).hasSize(3);
        assertThat(productionClassifierAssetsMetadata.get("version_info").keySet())
                .containsExactly(
                        "taxonomy_type", "taxonomy_version", "updated_date");

        // The property "version_info" should have attribution "taxonomy_version"
        // and its value should be "1.0".
        assertThat(productionClassifierAssetsMetadata.get("version_info").get("taxonomy_version"))
                .isEqualTo("1.0");

        // The property "version_info" should have attribution "taxonomy_type"
        // and its value should be "chrome".
        assertThat(productionClassifierAssetsMetadata.get("version_info").get("taxonomy_type"))
                .isEqualTo("chrome");

        // The metadata of 1 asset in production metadata should contain 4 attributions:
        // "asset_version", "path", "checksum", "updated_date".
        // Check if "labels_topics" asset has the correct format.
        assertThat(productionClassifierAssetsMetadata.get("labels_topics")).hasSize(4);
        assertThat(productionClassifierAssetsMetadata.get("labels_topics").keySet())
                .containsExactly("asset_version", "path", "checksum", "updated_date");

        // The asset "labels_topics" should have attribution "asset_version" and its value should be
        // "1.0"
        assertThat(productionClassifierAssetsMetadata.get("labels_topics").get("asset_version"))
                .isEqualTo("1.0");

        // The asset "labels_topics" should have attribution "path" and its value should be
        // "assets/classifier/labels_topics.txt"
        assertThat(productionClassifierAssetsMetadata.get("labels_topics").get("path"))
                .isEqualTo("assets/classifier/labels_topics.txt");

        // The asset "labels_topics" should have attribution "updated_date" and its value should be
        // "2022-06-15"
        assertThat(productionClassifierAssetsMetadata.get("labels_topics").get("updated_date"))
                .isEqualTo("2022-06-15");

        // There should contain 5 metadata attributions in asset "topic_id_to_name"
        assertThat(productionClassifierAssetsMetadata.get("topic_id_to_name")).hasSize(4);

        // The asset "topic_id_to_name" should have attribution "path" and its value should be
        // "assets/classifier/topic_id_to_name.csv"
        assertThat(productionClassifierAssetsMetadata.get("topic_id_to_name").get("path"))
                .isEqualTo("assets/classifier/topic_id_to_name.csv");

        // The asset "precomputed_app_list" should have attribution "checksum" and
        // its value should be "ee6518e087897eec372d31a76296fb5285a2202b66d784e98047085673a37ea3"
        assertThat(productionClassifierAssetsMetadata.get("precomputed_app_list").get("checksum"))
                .isEqualTo("ee6518e087897eec372d31a76296fb5285a2202b66d784e98047085673a37ea3");
    }

    @Test
    public void testGetTestClassifierAssetsMetadata_wrongFormat() {
        // There should contain 1 metadata attributions in asset "test_asset1",
        // because it doesn't have "checksum" and "updated_date"
        assertThat(testClassifierAssetsMetadata.get("test_asset1")).hasSize(1);

        // The asset "test_asset1" should have attribution "path" and its value should be
        // "assets/classifier/test1"
        assertThat(testClassifierAssetsMetadata.get("test_asset1").get("path"))
                .isEqualTo("assets/classifier/test1");

        // There should contain 4 metadata attributions in asset "test_asset2",
        // because "redundant_field1" and "redundant_field2" are not correct attributions.
        assertThat(testClassifierAssetsMetadata.get("test_asset2")).hasSize(4);

        // The asset "test_asset2" should have attribution "path" and its value should be
        // "assets/classifier/test2"
        assertThat(testClassifierAssetsMetadata.get("test_asset2").get("path"))
                .isEqualTo("assets/classifier/test2");

        // The asset "test_asset2" shouldn't have redundant attribution "redundant_field1"
        assertThat(testClassifierAssetsMetadata.get("test_asset2"))
                .doesNotContainKey("redundant_field1");
    }

    @Test
    public void testComputeTestAssetChecksum() {
        // Compute SHA256 checksum of labels topics file in test assets and check the result
        // can match the checksum saved in the test classifier assets metadata file.
        String labelsTestTopicsChecksum = computeClassifierAssetChecksum(
                sContext.getAssets(), TEST_LABELS_FILE_PATH);
        assertThat(labelsTestTopicsChecksum).isEqualTo(
                testClassifierAssetsMetadata.get("labels_topics").get("checksum"));

        // Compute SHA256 checksum of precomputed apps topics file in test assets
        // and check the result can match the checksum saved in the classifier assets metadata file.
        String precomputedAppsTestChecksum = computeClassifierAssetChecksum(
                sContext.getAssets(), TEST_PRECOMPUTED_FILE_PATH);
        assertThat(precomputedAppsTestChecksum).isEqualTo(
                testClassifierAssetsMetadata.get("precomputed_app_list").get("checksum"));
    }

    @Test
    public void testComputeProductionAssetChecksum() {
        // Compute SHA256 checksum of labels topics file in production assets and check the result
        // can match the checksum saved in the production classifier assets metadata file.
        String labelsProductionTopicsChecksum = computeClassifierAssetChecksum(
                sContext.getAssets(), PRODUCTION_LABELS_FILE_PATH);
        assertThat(labelsProductionTopicsChecksum).isEqualTo(
                productionClassifierAssetsMetadata.get("labels_topics").get("checksum"));

        // Compute SHA256 checksum of precomputed apps topics file in production assets
        // and check the result can match the checksum saved in the classifier assets metadata file.
        String precomputedAppsProductionChecksum = computeClassifierAssetChecksum(
                sContext.getAssets(), PRODUCTION_PRECOMPUTED_FILE_PATH);
        assertThat(precomputedAppsProductionChecksum).isEqualTo(
                productionClassifierAssetsMetadata.get("precomputed_app_list").get("checksum"));
    }
}
