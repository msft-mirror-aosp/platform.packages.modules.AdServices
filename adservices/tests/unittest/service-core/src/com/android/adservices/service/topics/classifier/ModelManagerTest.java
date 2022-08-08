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

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Model Manager Test {@link ModelManager}. */
public class ModelManagerTest {

    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private ImmutableList<Integer> mProductionLabels;
    private ImmutableMap<String, ImmutableMap<String, String>> mProductionClassifierAssetsMetadata;
    private ImmutableMap<String, ImmutableMap<String, String>> mTestClassifierAssetsMetadata;
    private ModelManager mModelManager;
    private static final String TEST_LABELS_FILE_PATH = "classifier/labels_test_topics.txt";
    private static final String TEST_APPS_FILE_PATH = "classifier/precomputed_test_app_list.csv";
    private static final String TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH =
            "classifier/classifier_test_assets_metadata.json";
    private static final String MODEL_FILE_PATH = "classifier/model.tflite";

    @Test
    public void testRetrieveLabels_successfulRead() {
        // Test the labels list in production assets
        // Check size of list.
        // The labels_topics.txt contains 349 topics.
        mModelManager = ModelManager.getInstance(sContext);
        mProductionLabels = mModelManager.retrieveLabels();
        assertThat(mProductionLabels.size()).isEqualTo(349);

        // Check some labels.
        assertThat(mProductionLabels).containsAtLeast(10, 200, 270, 320);
    }

    @Test
    public void testRetrieveLabels_emptyListReturnedOnException() {
        mModelManager =
                new ModelManager(
                        sContext,
                        "WrongFilePath",
                        "WrongFilePath",
                        "WrongFilePath",
                        "WrongFilePath");
        mProductionLabels = mModelManager.retrieveLabels();
        ImmutableList<Integer> labels = mModelManager.retrieveLabels();
        // Check empty list returned.
        assertThat(labels).isEmpty();
    }

    @Test
    public void testLoadedAppTopics() {
        mModelManager =
                new ModelManager(
                        sContext,
                        TEST_LABELS_FILE_PATH,
                        TEST_APPS_FILE_PATH,
                        TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        MODEL_FILE_PATH);
        Map<String, List<Integer>> appTopic = mModelManager.retrieveAppClassificationTopics();
        // Check size of map
        // The app topics file contains 1000 apps + 11 sample apps + 2 test valid topics' apps
        // + 1 end2end test app.
        assertThat(appTopic.size()).isEqualTo(1015);

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
        List<Integer> sampleApp4Topics = Arrays.asList(253, 146, 277, 59, 127);
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

    @Test
    public void testGetProductionClassifierAssetsMetadata_correctFormat() {
        mModelManager = ModelManager.getInstance(sContext);
        mProductionClassifierAssetsMetadata = mModelManager.retrieveClassifierAssetsMetadata();
        // There should contain 4 assets and 1 property in classifier_assets_metadata.json.
        assertThat(mProductionClassifierAssetsMetadata).hasSize(5);

        // The property of metadata in production metadata should contain 4 attributions:
        // "taxonomy_type", "taxonomy_version", "updated_date".
        // The key name of property is "version_info"
        assertThat(mProductionClassifierAssetsMetadata.get("version_info")).hasSize(3);
        assertThat(mProductionClassifierAssetsMetadata.get("version_info").keySet())
                .containsExactly("taxonomy_type", "taxonomy_version", "updated_date");

        // The property "version_info" should have attribution "taxonomy_version"
        // and its value should be "1".
        assertThat(mProductionClassifierAssetsMetadata.get("version_info").get("taxonomy_version"))
                .isEqualTo("1");

        // The property "version_info" should have attribution "taxonomy_type"
        // and its value should be "chrome".
        assertThat(mProductionClassifierAssetsMetadata.get("version_info").get("taxonomy_type"))
                .isEqualTo("chrome");

        // The metadata of 1 asset in production metadata should contain 4 attributions:
        // "asset_version", "path", "checksum", "updated_date".
        // Check if "labels_topics" asset has the correct format.
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics")).hasSize(4);
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics").keySet())
                .containsExactly("asset_version", "path", "checksum", "updated_date");

        // The asset "labels_topics" should have attribution "asset_version" and its value should be
        // "1"
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics").get("asset_version"))
                .isEqualTo("1");

        // The asset "labels_topics" should have attribution "path" and its value should be
        // "assets/classifier/labels_topics.txt"
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics").get("path"))
                .isEqualTo("assets/classifier/labels_topics.txt");

        // The asset "labels_topics" should have attribution "updated_date" and its value should be
        // "2022-06-15"
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics").get("updated_date"))
                .isEqualTo("2022-06-15");

        // There should contain 5 metadata attributions in asset "topic_id_to_name"
        assertThat(mProductionClassifierAssetsMetadata.get("topic_id_to_name")).hasSize(4);

        // The asset "topic_id_to_name" should have attribution "path" and its value should be
        // "assets/classifier/topic_id_to_name.csv"
        assertThat(mProductionClassifierAssetsMetadata.get("topic_id_to_name").get("path"))
                .isEqualTo("assets/classifier/topic_id_to_name.csv");

        // The asset "precomputed_app_list" should have attribution "checksum" and
        // its value should be "f155c15126a475f03329edda23d6cc90e1227d9d8fddb8318b9acc88b6d0fffe"
        assertThat(mProductionClassifierAssetsMetadata.get("precomputed_app_list").get("checksum"))
                .isEqualTo("f155c15126a475f03329edda23d6cc90e1227d9d8fddb8318b9acc88b6d0fffe");
    }

    @Test
    public void testGetTestClassifierAssetsMetadata_wrongFormat() {
        mModelManager =
                new ModelManager(
                        sContext,
                        TEST_LABELS_FILE_PATH,
                        TEST_APPS_FILE_PATH,
                        TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        MODEL_FILE_PATH);
        mTestClassifierAssetsMetadata = mModelManager.retrieveClassifierAssetsMetadata();
        // There should contain 1 metadata attributions in asset "test_asset1",
        // because it doesn't have "checksum" and "updated_date"
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
