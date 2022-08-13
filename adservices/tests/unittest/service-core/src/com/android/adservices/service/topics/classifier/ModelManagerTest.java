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
import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Model Manager Test {@link ModelManager}. */
public class ModelManagerTest {

    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private ImmutableList<Integer> mTestLabels;
    private ImmutableList<Integer> mProductionLabels;
    private ImmutableMap<String, ImmutableMap<String, String>> mProductionClassifierAssetsMetadata;
    private ImmutableMap<String, ImmutableMap<String, String>> mTestClassifierAssetsMetadata;
    private ModelManager mTestModelManager;
    private ModelManager mProductionModelManager;
    private static final String TEST_LABELS_FILE_PATH = "classifier/labels_test_topics.txt";
    private static final String TEST_APPS_FILE_PATH = "classifier/precomputed_test_app_list.csv";
    private static final String TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH =
            "classifier/classifier_test_assets_metadata.json";
    private static final String PRODUCTION_LABELS_FILE_PATH = "classifier/labels_topics.txt";
    private static final String PRODUCTION_APPS_FILE_PATH = "classifier/precomputed_app_list.csv";
    private static final String PRODUCTION_CLASSIFIER_ASSETS_METADATA_FILE_PATH =
            "classifier/classifier_assets_metadata.json";
    private static final String MODEL_FILE_PATH = "classifier/model.tflite";

    @Before
    public void setup() {
        mTestModelManager = new ModelManager(
                sContext,
                TEST_LABELS_FILE_PATH,
                TEST_APPS_FILE_PATH,
                TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                MODEL_FILE_PATH);

        mProductionModelManager = new ModelManager(
                sContext,
                PRODUCTION_LABELS_FILE_PATH,
                PRODUCTION_APPS_FILE_PATH,
                PRODUCTION_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                MODEL_FILE_PATH);

        mTestLabels = mTestModelManager.retrieveLabels();
        mTestClassifierAssetsMetadata = mTestModelManager.retrieveClassifierAssetsMetadata();

        mProductionLabels = mProductionModelManager.retrieveLabels();
        mProductionClassifierAssetsMetadata =
                mProductionModelManager.retrieveClassifierAssetsMetadata();
    }

    @Test
    public void testRetrieveLabels_successfulRead() {
        // Test the labels list in test assets
        // Check size of list.
        // The labels_test_topics.txt contains 446 topics.
        assertThat(mTestLabels.size()).isEqualTo(446);

        // Check some labels.
        assertThat(mTestLabels).containsAtLeast(10043, 10075, 10150, 10445);


        // Test the labels list in production assets
        // Check size of list.
        // The labels_topics.txt contains 446 topics.
        assertThat(mProductionLabels.size()).isEqualTo(446);

        // Check some labels.
        assertThat(mProductionLabels).containsAtLeast(10010, 10200, 10270, 10432);
    }

    @Test
    public void testRetrieveLabels_emptyListReturnedOnException() {
        ModelManager wrongFilePathModelManager =
                new ModelManager(
                        sContext,
                        "WrongFilePath",
                        "WrongFilePath",
                        "WrongFilePath",
                        "WrongFilePath");
        mTestLabels = wrongFilePathModelManager.retrieveLabels();
        ImmutableList<Integer> labels = wrongFilePathModelManager.retrieveLabels();
        // Check empty list returned.
        assertThat(labels).isEmpty();
    }

    @Test
    public void testLoadedAppTopics() {
        Map<String, List<Integer>> appTopic = mTestModelManager.retrieveAppClassificationTopics();
        // Check size of map
        // The app topics file contains 11 sample apps + 2 test valid topics' apps
        // + 1 end2end test app.
        assertThat(appTopic.size()).isEqualTo(14);

        // The topicIds of "com.example.adservices.samples.topics.sampleapp" in
        // assets/precomputed_test_app_list.csv are 10222, 10223, 10116, 10243, 10254
        String sampleAppPrefix = "com.example.adservices.samples.topics.sampleapp";
        List<Integer> sampleAppTopics = Arrays.asList(10222, 10223, 10116, 10243, 10254);
        assertThat(appTopic.get(sampleAppPrefix)).isEqualTo(sampleAppTopics);

        // The topicIds of "com.example.adservices.samples.topics.sampleapp4" in
        // assets/precomputed_test_app_list.csv are 10253, 10146, 10227, 10390, 10413
        List<Integer> sampleApp4Topics = Arrays.asList(10253, 10146, 10227, 10390, 10413);
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
        // assets/precomputed_test_app_list.csv are 10147, 10253, 10254
        List<Integer> validTestApp1Topics = Arrays.asList(10147, 10253, 10254);
        assertThat(appTopic.get(validTestAppPrefix + "1")).isEqualTo(validTestApp1Topics);

        // The valid topicIds of "com.example.adservices.valid.topics.testapp2" in
        // assets/precomputed_test_app_list.csv are 143, 15
        List<Integer> validTestApp2Topics = Arrays.asList(10253, 10254);
        assertThat(appTopic.get(validTestAppPrefix + "2")).isEqualTo(validTestApp2Topics);
    }

    @Test
    public void testAppsWithOnlyEmptyTopics() {
        // This app has empty topic in `assets/precomputed_test_app_list.csv`
        String appWithEmptyTopics = "com.emptytopics";

        // Load precomputed labels from the test source `assets/precomputed_test_app_list.csv`
        Map<String, List<Integer>> appTopic = mTestModelManager.retrieveAppClassificationTopics();

        // Verify this entry is not present in the map.
        assertThat(appTopic).doesNotContainKey(appWithEmptyTopics);
    }

    @Test
    public void testGetTestClassifierAssetsMetadata_correctFormat() {
        // There should contain 6 assets and 1 property in classifier_test_assets_metadata.json.
        // The asset without "asset_name" or "property" will not be stored in the map.
        mTestClassifierAssetsMetadata = mTestModelManager.retrieveClassifierAssetsMetadata();
        assertThat(mTestClassifierAssetsMetadata).hasSize(7);

        // The property of metadata with correct format should contain 3 attributions:
        // "taxonomy_type", "taxonomy_version", "updated_date".
        // The key name of property is "version_info"
        assertThat(mTestClassifierAssetsMetadata.get("version_info")).hasSize(3);
        assertThat(mTestClassifierAssetsMetadata.get("version_info").keySet()).containsExactly(
                "taxonomy_type", "taxonomy_version", "updated_date");

        // The property "version_info" should have attribution "taxonomy_version"
        // and its value should be "12".
        assertThat(mTestClassifierAssetsMetadata.get("version_info").get("taxonomy_version"))
                .isEqualTo("12");

        // The property "version_info" should have attribution "taxonomy_type"
        // and its value should be "chrome_and_mobile_taxonomy".
        assertThat(mTestClassifierAssetsMetadata.get("version_info").get("taxonomy_type"))
                .isEqualTo("chrome_and_mobile_taxonomy");

        // The metadata of 1 asset with correct format should contain 4 attributions:
        // "asset_version", "path", "checksum", "updated_date".
        // Check if "labels_topics" asset has the correct format.
        assertThat(mTestClassifierAssetsMetadata.get("labels_topics")).hasSize(4);
        assertThat(mTestClassifierAssetsMetadata.get("labels_topics").keySet()).containsExactly(
                "asset_version", "path", "checksum", "updated_date");

        // The asset "labels_topics" should have attribution "asset_version" and its value should be
        // "34"
        assertThat(mTestClassifierAssetsMetadata.get("labels_topics").get("asset_version"))
                .isEqualTo("34");

        // The asset "labels_topics" should have attribution "path" and its value should be
        // "assets/classifier/labels_test_topics.txt"
        assertThat(mTestClassifierAssetsMetadata.get("labels_topics").get("path"))
                .isEqualTo("assets/classifier/labels_test_topics.txt");

        // The asset "labels_topics" should have attribution "updated_date" and its value should be
        // "2022-07-29"
        assertThat(mTestClassifierAssetsMetadata.get("labels_topics").get("updated_date"))
                .isEqualTo("2022-07-29");

        // There should contain 4 metadata attributions in asset "topic_id_to_name"
        assertThat(mTestClassifierAssetsMetadata.get("topic_id_to_name")).hasSize(4);

        // The asset "topic_id_to_name" should have attribution "path" and its value should be
        // "assets/classifier/topic_id_to_name.csv"
        assertThat(mTestClassifierAssetsMetadata.get("topic_id_to_name").get("path"))
                .isEqualTo("assets/classifier/topic_id_to_name.csv");

        // The asset "precomputed_app_list" should have attribution "checksum" and
        // its value should be "7e80025cd0836ef8465bc42031ec4ae95b3b07e325e23b58d298341e37f4fb4e"
        assertThat(mTestClassifierAssetsMetadata.get("precomputed_app_list").get("checksum"))
                .isEqualTo("7e80025cd0836ef8465bc42031ec4ae95b3b07e325e23b58d298341e37f4fb4e");
    }

    @Test
    public void testGetProductionClassifierAssetsMetadata_correctFormat() {
        // There should contain 4 assets and 1 property in classifier_assets_metadata.json.
        assertThat(mProductionClassifierAssetsMetadata).hasSize(5);

        // The property of metadata in production metadata should contain 4 attributions:
        // "taxonomy_type", "taxonomy_version", "updated_date".
        // The key name of property is "version_info"
        assertThat(mProductionClassifierAssetsMetadata.get("version_info")).hasSize(3);
        assertThat(mProductionClassifierAssetsMetadata.get("version_info").keySet())
                .containsExactly(
                        "taxonomy_type", "taxonomy_version", "updated_date");

        // The property "version_info" should have attribution "taxonomy_version"
        // and its value should be "2".
        assertThat(mProductionClassifierAssetsMetadata.get("version_info").get("taxonomy_version"))
                .isEqualTo("2");

        // The property "version_info" should have attribution "taxonomy_type"
        // and its value should be "chrome_and_mobile_taxonomy".
        assertThat(mProductionClassifierAssetsMetadata.get("version_info").get("taxonomy_type"))
                .isEqualTo("chrome_and_mobile_taxonomy");

        // The metadata of 1 asset in production metadata should contain 4 attributions:
        // "asset_version", "path", "checksum", "updated_date".
        // Check if "labels_topics" asset has the correct format.
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics")).hasSize(4);
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics").keySet())
                .containsExactly("asset_version", "path", "checksum", "updated_date");

        // The asset "labels_topics" should have attribution "asset_version" and its value should be
        // "2"
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics").get("asset_version"))
                .isEqualTo("2");

        // The asset "labels_topics" should have attribution "path" and its value should be
        // "assets/classifier/labels_topics.txt"
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics").get("path"))
                .isEqualTo("assets/classifier/labels_topics.txt");

        // The asset "labels_topics" should have attribution "updated_date" and its value should be
        // "2022-07-29"
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics").get("updated_date"))
                .isEqualTo("2022-07-29");

        // There should contain 5 metadata attributions in asset "topic_id_to_name"
        assertThat(mProductionClassifierAssetsMetadata.get("topic_id_to_name")).hasSize(4);

        // The asset "topic_id_to_name" should have attribution "path" and its value should be
        // "assets/classifier/topic_id_to_name.csv"
        assertThat(mProductionClassifierAssetsMetadata.get("topic_id_to_name").get("path"))
                .isEqualTo("assets/classifier/topic_id_to_name.csv");

        // The asset "precomputed_app_list" should have attribution "checksum" and
        // its value should be "e5d118889e7e57f1e5ed166354f3dfa81963ee7e917f98c8a687d541b9bbe489"
        assertThat(mProductionClassifierAssetsMetadata.get("precomputed_app_list").get("checksum"))
                .isEqualTo("e5d118889e7e57f1e5ed166354f3dfa81963ee7e917f98c8a687d541b9bbe489");
    }


    @Test
    public void testGetTestClassifierAssetsMetadata_wrongFormat() {
        // There should contain 1 metadata attributions in asset "test_asset1",
        // because it doesn't have "checksum" and "updated_date"
        mTestClassifierAssetsMetadata = mTestModelManager.retrieveClassifierAssetsMetadata();
        assertThat(mTestClassifierAssetsMetadata.get("test_asset1")).hasSize(1);

        // The asset "test_asset1" should have attribution "path" and its value should be
        // "assets/classifier/test1"
        assertThat(mTestClassifierAssetsMetadata.get("test_asset1").get("path"))
                .isEqualTo("assets/classifier/test1");

        // There should contain 4 metadata attributions in asset "test_asset2",
        // because "redundant_field1" and "redundant_field2" are not correct attributions.
        assertThat(mTestClassifierAssetsMetadata.get("test_asset2")).hasSize(4);

        // The asset "test_asset2" should have attribution "path" and its value should be
        // "assets/classifier/test2"
        assertThat(mTestClassifierAssetsMetadata.get("test_asset2").get("path"))
                .isEqualTo("assets/classifier/test2");

        // The asset "test_asset2" shouldn't have redundant attribution "redundant_field1"
        assertThat(mTestClassifierAssetsMetadata.get("test_asset2"))
                .doesNotContainKey("redundant_field1");
    }
}
