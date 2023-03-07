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

package com.android.adservices.tests.cts.topics;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.topics.GetTopicsResponse;
import android.adservices.topics.Topic;
import android.content.Context;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdservicesCtsHelper;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(JUnit4.class)
public class TopicsManagerTest {
    private static final String TAG = "TopicsManagerTest";
    // The JobId of the Epoch Computation.
    private static final int EPOCH_JOB_ID = 2;

    // Override the Epoch Job Period to this value to speed up the epoch computation.
    private static final long TEST_EPOCH_JOB_PERIOD_MS = 3000;
    // Expected model versions.
    private static final long EXPECTED_MODEL_VERSION = 3L;
    // Expected taxonomy version.
    private static final long EXPECTED_TAXONOMY_VERSION = 2L;

    // Default Epoch Period.
    private static final long TOPICS_EPOCH_JOB_PERIOD_MS = 7 * 86_400_000; // 7 days.

    // Classifier test constants.
    private static final int TEST_CLASSIFIER_NUMBER_OF_TOP_LABELS = 5;
    // Each app is given topics with a confidence score between 0.0 to 1.0 float value. This
    // denotes how confident are you that a particular topic t1 is related to the app x that is
    // classified.
    // Threshold value for classifier confidence set to 0 to allow all topics and avoid filtering.
    private static final float TEST_CLASSIFIER_THRESHOLD = 0.0f;
    // ON_DEVICE_CLASSIFIER
    private static final int TEST_CLASSIFIER_TYPE = 1;

    // Classifier default constants.
    private static final int DEFAULT_CLASSIFIER_NUMBER_OF_TOP_LABELS = 3;
    // Threshold value for classifier confidence set back to the default.
    private static final float DEFAULT_CLASSIFIER_THRESHOLD = 0.1f;
    // PRECOMPUTED_THEN_ON_DEVICE_CLASSIFIER
    private static final int DEFAULT_CLASSIFIER_TYPE = 3;

    // Use 0 percent for random topic in the test so that we can verify the returned topic.
    private static final int TEST_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC = 0;
    private static final int TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC = 5;

    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private static final String ADSERVICES_PACKAGE_NAME =
            AdservicesCtsHelper.getAdServicesPackageName(sContext, TAG);

    // Assert message statements.
    private static final String INCORRECT_MODEL_VERSION_MESSAGE =
            "Incorrect model version detected. Please repo sync, build and install the new apex.";
    private static final String INCORRECT_TAXONOMY_VERSION_MESSAGE =
            "Incorrect taxonomy version detected. Please repo sync, build and install the new"
                    + " apex.";

    @Before
    public void setup() throws Exception {
        // Skip the test if it runs on unsupported platforms.
        Assume.assumeTrue(AdservicesCtsHelper.isDeviceSupported());

        // We need to skip 3 epochs so that if there is any usage from other test runs, it will
        // not be used for epoch retrieval.
        Thread.sleep(3 * TEST_EPOCH_JOB_PERIOD_MS);

        overrideEpochPeriod(TEST_EPOCH_JOB_PERIOD_MS);
        // We need to turn off random topic so that we can verify the returned topic.
        overridePercentageForRandomTopic(TEST_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC);
        // TODO(b/263297331): Handle rollback support for R and S.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            overrideConsentSourceOfTruth(/* PPAPI_ONLY */ 1);
        }
    }

    @After
    public void teardown() {
        overrideEpochPeriod(TOPICS_EPOCH_JOB_PERIOD_MS);
        overridePercentageForRandomTopic(TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC);
        overrideConsentSourceOfTruth(null);
    }

    @Test
    public void testTopicsManager_runDefaultClassifier() throws Exception {
        // Set classifier flag to use precomputed-then-on-device classifier.
        overrideClassifierType(DEFAULT_CLASSIFIER_TYPE);

        // Default classifier uses the precomputed list first, then on-device classifier.
        // The Test App has 2 SDKs: sdk1 calls the Topics API and sdk2 does not.
        // Sdk1 calls the Topics API.
        AdvertisingTopicsClient advertisingTopicsClient1 =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk1")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        // At beginning, Sdk1 receives no topic.
        GetTopicsResponse sdk1Result = advertisingTopicsClient1.getTopics().get();
        assertThat(sdk1Result.getTopics()).isEmpty();

        // Now force the Epoch Computation Job. This should be done in the same epoch for
        // callersCanLearnMap to have the entry for processing.
        forceEpochComputationJob();

        // Wait to the next epoch. We will not need to do this after we implement the fix in
        // go/rb-topics-epoch-scheduling
        Thread.sleep(TEST_EPOCH_JOB_PERIOD_MS);

        // Since the sdk1 called the Topics API in the previous Epoch, it should receive some topic.
        sdk1Result = advertisingTopicsClient1.getTopics().get();
        assertThat(sdk1Result.getTopics()).isNotEmpty();

        // We only have 1 test app which has 5 classification topics: 10147,10253,10175,10254,10333
        // in the precomputed list.
        // These 5 classification topics will become top 5 topics of the epoch since there is
        // no other apps calling Topics API.
        // The app will be assigned one random topic from one of these 5 topics.
        assertThat(sdk1Result.getTopics()).hasSize(1);
        Topic topic = sdk1Result.getTopics().get(0);

        // Expected asset versions to be bundled in the build.
        // If old assets are being picked up, repo sync, build and install the new apex again.
        assertWithMessage(INCORRECT_MODEL_VERSION_MESSAGE)
                .that(topic.getModelVersion())
                .isEqualTo(EXPECTED_MODEL_VERSION);
        assertWithMessage(INCORRECT_TAXONOMY_VERSION_MESSAGE)
                .that(topic.getTaxonomyVersion())
                .isEqualTo(EXPECTED_TAXONOMY_VERSION);

        // topic is one of the 5 classification topics of the Test App.
        assertThat(topic.getTopicId()).isIn(Arrays.asList(10147, 10253, 10175, 10254, 10333));

        // Sdk 2 did not call getTopics API. So it should not receive any topic.
        AdvertisingTopicsClient advertisingTopicsClient2 =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk2")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        GetTopicsResponse sdk2Result2 = advertisingTopicsClient2.getTopics().get();
        assertThat(sdk2Result2.getTopics()).isEmpty();
    }

    @Test
    public void testTopicsManager_runOnDeviceClassifier() throws Exception {
        // Set classifier flag to use on-device classifier.
        overrideClassifierType(TEST_CLASSIFIER_TYPE);

        // Set number of top labels returned by the on-device classifier to 5.
        overrideClassifierNumberOfTopLabels(TEST_CLASSIFIER_NUMBER_OF_TOP_LABELS);
        // Remove classifier threshold by setting it to 0.
        overrideClassifierThreshold(TEST_CLASSIFIER_THRESHOLD);

        // The Test App has 1 SDK: sdk3
        // sdk3 calls the Topics API.
        AdvertisingTopicsClient advertisingTopicsClient3 =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk3")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        // At beginning, Sdk3 receives no topic.
        GetTopicsResponse sdk3Result = advertisingTopicsClient3.getTopics().get();
        assertThat(sdk3Result.getTopics()).isEmpty();

        // Now force the Epoch Computation Job. This should be done in the same epoch for
        // callersCanLearnMap to have the entry for processing.
        forceEpochComputationJob();

        // Wait to the next epoch. We will not need to do this after we implement the fix in
        // go/rb-topics-epoch-scheduling
        Thread.sleep(TEST_EPOCH_JOB_PERIOD_MS);

        // Since the sdk3 called the Topics API in the previous Epoch, it should receive some topic.
        sdk3Result = advertisingTopicsClient3.getTopics().get();
        assertThat(sdk3Result.getTopics()).isNotEmpty();

        // We only have 5 topics classified by the on-device classifier.
        // The app will be assigned one random topic from one of these 5 topics.
        assertThat(sdk3Result.getTopics()).hasSize(1);
        Topic topic = sdk3Result.getTopics().get(0);

        // Expected asset versions to be bundled in the build.
        // If old assets are being picked up, repo sync, build and install the new apex again.
        assertWithMessage(INCORRECT_MODEL_VERSION_MESSAGE)
                .that(topic.getModelVersion())
                .isEqualTo(EXPECTED_MODEL_VERSION);
        assertWithMessage(INCORRECT_TAXONOMY_VERSION_MESSAGE)
                .that(topic.getTaxonomyVersion())
                .isEqualTo(EXPECTED_TAXONOMY_VERSION);

        // Top 5 classifications for empty string with v3 model are [10230, 10228, 10253, 10232,
        // 10140]. This is computed by running the model on the device for empty string.
        // topic is one of the 5 classification topics of the Test App.
        List<Integer> expectedTopTopicIds = Arrays.asList(10230, 10228, 10253, 10232, 10140);
        assertThat(topic.getTopicId()).isIn(expectedTopTopicIds);

        // Set classifier flag back to default.
        overrideClassifierType(DEFAULT_CLASSIFIER_TYPE);

        // Set number of top labels returned by the on-device classifier back to default.
        overrideClassifierNumberOfTopLabels(DEFAULT_CLASSIFIER_NUMBER_OF_TOP_LABELS);
        // Set classifier threshold back to default.
        overrideClassifierThreshold(DEFAULT_CLASSIFIER_THRESHOLD);
    }

    // Override the flag to select classifier type.
    private void overrideClassifierType(int val) {
        ShellUtils.runShellCommand("device_config put adservices classifier_type " + val);
    }

    // Override the flag to change the number of top labels returned by on-device classifier type.
    private void overrideClassifierNumberOfTopLabels(int val) {
        ShellUtils.runShellCommand(
                "device_config put adservices classifier_number_of_top_labels " + val);
    }

    // Override the flag to change the threshold for the classifier.
    private void overrideClassifierThreshold(float val) {
        ShellUtils.runShellCommand("device_config put adservices classifier_threshold " + val);
    }

    // Override the Epoch Period to shorten the Epoch Length in the test.
    private void overrideEpochPeriod(long overrideEpochPeriod) {
        ShellUtils.runShellCommand(
                "setprop debug.adservices.topics_epoch_job_period_ms " + overrideEpochPeriod);
    }

    // Override the Percentage For Random Topic in the test.
    private void overridePercentageForRandomTopic(long overridePercentage) {
        ShellUtils.runShellCommand(
                "setprop debug.adservices.topics_percentage_for_random_topics "
                        + overridePercentage);
    }

    /** Forces JobScheduler to run the Epoch Computation job */
    private void forceEpochComputationJob() {
        ShellUtils.runShellCommand(
                "cmd jobscheduler run -f" + " " + ADSERVICES_PACKAGE_NAME + " " + EPOCH_JOB_ID);
    }

    private void overrideConsentSourceOfTruth(Integer value) {
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth " + value);
    }
}

