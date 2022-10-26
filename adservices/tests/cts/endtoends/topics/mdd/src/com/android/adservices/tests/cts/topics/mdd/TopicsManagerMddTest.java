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

import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.topics.GetTopicsResponse;
import android.adservices.topics.Topic;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(JUnit4.class)
public class TopicsManagerMddTest {
    private static final String TAG = "TopicsManagerMddTest";
    // The JobId of the Epoch Computation.
    private static final int EPOCH_JOB_ID = 2;
    // Job ID for Mdd Wifi Charging Task.
    public static final int MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID = 14;

    // Override the Epoch Job Period to this value to speed up the epoch computation.
    private static final long TEST_EPOCH_JOB_PERIOD_MS = 3000;
    // Waiting time for assets to be downloaded after triggering MDD job.
    private static final long TEST_MDD_DOWNLOAD_WAIT_TIME_MS = 10000;

    // Default Epoch Period.
    private static final long TOPICS_EPOCH_JOB_PERIOD_MS = 7 * 86_400_000; // 7 days.

    // Classifier test constants.
    private static final int TEST_CLASSIFIER_NUMBER_OF_TOP_LABELS = 5;
    // Each app is given topics with a confidence score between 0.0 to 1.0 float value. This
    // denotes how confident are you that a particular topic t1 is related to the app x that is
    // classified.
    // Threshold value for classifier confidence set to 0 to allow all topics and avoid filtering.
    private static final float TEST_CLASSIFIER_THRESHOLD = 0.0f;
    // classifier_type flag for ON_DEVICE_CLASSIFIER.
    private static final int ON_DEVICE_CLASSIFIER = 1;
    // Manifest file that points to CTS test assets:
    // http://google3/wireless/android/adservices/mdd/topics_classifier/cts_test_1/
    // These assets are have asset version set to 0 for verification in tests.
    private static final String TEST_MDD_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-topics-classifier/1043"
                    + "/784a7d30e5ac9fd6410fb017b05f392f67e9659a";

    // Classifier default constants.
    private static final int DEFAULT_CLASSIFIER_NUMBER_OF_TOP_LABELS = 3;
    // Threshold value for classifier confidence set back to the default.
    private static final float DEFAULT_CLASSIFIER_THRESHOLD = 0.1f;
    // PRECOMPUTED_THEN_ON_DEVICE_CLASSIFIER
    private static final int DEFAULT_CLASSIFIER_TYPE = 3;
    private static final String DEFAULT_MDD_MANIFEST_FILE_URL =
            "https://dl.google.com/mdi-serving/adservices/topics_classifier/manifest_configs/2"
                    + "/manifest_config_1661376643699.binaryproto";

    // Use 0 percent for random topic in the test so that we can verify the returned topic.
    private static final int TEST_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC = 0;
    private static final int TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC = 5;

    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    // Used to get the package name. Copied over from com.android.adservices.AdServicesCommon
    private static final String TOPICS_SERVICE_NAME = "android.adservices.TOPICS_SERVICE";
    private static final String ADSERVICES_PACKAGE_NAME = getAdServicesPackageName();

    @Before
    public void setup() throws Exception {
        // We need to skip 3 epochs so that if there is any usage from other test runs, it will
        // not be used for epoch retrieval.
        Thread.sleep(3 * TEST_EPOCH_JOB_PERIOD_MS);

        overrideEpochPeriod(TEST_EPOCH_JOB_PERIOD_MS);
        // We need to turn off random topic so that we can verify the returned topic.
        overridePercentageForRandomTopic(TEST_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC);
    }

    @After
    public void teardown() {
        overrideEpochPeriod(TOPICS_EPOCH_JOB_PERIOD_MS);
        overridePercentageForRandomTopic(TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC);
    }

    @Test
    public void testTopicsManager_downloadModelViaMdd_runOnDeviceClassifier() throws Exception {
        // Set up test flags for on-device classification.
        setupFlagsForOnDeviceClassifier();

        // Override manifest URL for Mdd.
        overrideMddManifestFileURL(TEST_MDD_MANIFEST_FILE_URL);
        // Download assets via Mdd for testing.
        triggerMddToDownload();
        // Wait for 10 seconds for the assets to be downloaded.
        Thread.sleep(TEST_MDD_DOWNLOAD_WAIT_TIME_MS);

        // The Test App has 1 SDK: sdk1
        // sdk1 calls the Topics API.
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

        // Since the sdk3 called the Topics API in the previous Epoch, it should receive some topic.
        sdk1Result = advertisingTopicsClient1.getTopics().get();
        assertThat(sdk1Result.getTopics()).isNotEmpty();

        // We only have 5 topics classified by the on-device classifier.
        // The app will be assigned one random topic from one of these 5 topics.
        assertThat(sdk1Result.getTopics()).hasSize(1);
        Topic topic = sdk1Result.getTopics().get(0);

        // Top 5 classifications for empty string with v1 model are [10230, 10253, 10227, 10250,
        // 10257]. This is
        // computed by running the model on the device for empty string.
        // topic is one of the 5 classification topics of the Test App.
        List<Integer> expectedTopTopicIds = Arrays.asList(10230, 10253, 10227, 10250, 10257);
        assertThat(topic.getTopicId()).isIn(expectedTopTopicIds);

        // Verify assets are from the downloaded assets. These assets have asset version set to 0
        // for verification.
        // Test assets downloaded for CTS test:
        // http://google3/wireless/android/adservices/mdd/topics_classifier/cts_test_1/
        assertThat(topic.getModelVersion()).isAtLeast(0L);
        assertThat(topic.getTaxonomyVersion()).isAtLeast(0L);

        // Clean up test flags setup for on-device classification.
        cleanupFlagsForOnDeviceClassifier();

        // Reset Mdd manifest file url to default
        overrideMddManifestFileURL(DEFAULT_MDD_MANIFEST_FILE_URL);
    }

    // Setup test flag values for on-device classifier.
    private void setupFlagsForOnDeviceClassifier() {
        // Set classifier flag to use on-device classifier.
        overrideClassifierType(ON_DEVICE_CLASSIFIER);

        // Set number of top labels returned by the on-device classifier to 5.
        overrideClassifierNumberOfTopLabels(TEST_CLASSIFIER_NUMBER_OF_TOP_LABELS);
        // Remove classifier threshold by setting it to 0.
        overrideClassifierThreshold(TEST_CLASSIFIER_THRESHOLD);
    }

    // Reset test flags used for on-device classifier.
    private void cleanupFlagsForOnDeviceClassifier() {
        // Set classifier flag back to default.
        overrideClassifierType(DEFAULT_CLASSIFIER_TYPE);

        // Set number of top labels returned by the on-device classifier back to default.
        overrideClassifierNumberOfTopLabels(DEFAULT_CLASSIFIER_NUMBER_OF_TOP_LABELS);
        // Set classifier threshold back to default.
        overrideClassifierThreshold(DEFAULT_CLASSIFIER_THRESHOLD);
    }

    // Override the flag to set manifest url for Mdd.
    private void overrideMddManifestFileURL(String val) {
        ShellUtils.runShellCommand(
                "device_config put adservices mdd_topics_classifier_manifest_file_url " + val);
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

    private void triggerMddToDownload() {
        // Forces JobScheduler to run Mdd.
        ShellUtils.runShellCommand(
                "cmd jobscheduler run -f"
                        + " "
                        + ADSERVICES_PACKAGE_NAME
                        + " "
                        + MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID);
    }

    /** Forces JobScheduler to run the Epoch Computation job */
    private void forceEpochComputationJob() {
        ShellUtils.runShellCommand(
                "cmd jobscheduler run -f" + " " + ADSERVICES_PACKAGE_NAME + " " + EPOCH_JOB_ID);
    }

    // Used to get the package name. Copied over from com.android.adservices.AndroidServiceBinder
    private static String getAdServicesPackageName() {
        final Intent intent = new Intent(TOPICS_SERVICE_NAME);
        final List<ResolveInfo> resolveInfos =
                sContext.getPackageManager()
                        .queryIntentServices(intent, PackageManager.MATCH_SYSTEM_ONLY);

        if (resolveInfos == null || resolveInfos.isEmpty()) {
            Log.e(
                    TAG,
                    "Failed to find resolveInfo for adServices service. Intent action: "
                            + TOPICS_SERVICE_NAME);
            return null;
        }

        if (resolveInfos.size() > 1) {
            Log.e(
                    TAG,
                    String.format(
                            "Found multiple services (%1$s) for the same intent action (%2$s)",
                            TOPICS_SERVICE_NAME, resolveInfos));
            return null;
        }

        final ServiceInfo serviceInfo = resolveInfos.get(0).serviceInfo;
        if (serviceInfo == null) {
            Log.e(TAG, "Failed to find serviceInfo for adServices service");
            return null;
        }

        return serviceInfo.packageName;
    }
}
