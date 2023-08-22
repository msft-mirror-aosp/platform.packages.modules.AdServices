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

import static org.junit.Assert.assertThrows;

import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.topics.GetTopicsRequest;
import android.adservices.topics.GetTopicsResponse;
import android.adservices.topics.Topic;
import android.adservices.topics.TopicsManager;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.OutcomeReceiverForTests;
import com.android.adservices.common.RequiresLowRamDevice;
import com.android.adservices.common.RequiresSdkLevelAtLeastS;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

// TODO(b/243062789): Test should not use CountDownLatch or Sleep.
@RunWith(JUnit4.class)
public class TopicsManagerTest {
    private static final String TAG = "TopicsManagerTest";
    // The JobId of the Epoch Computation.
    private static final int EPOCH_JOB_ID = 2;

    // Override the Epoch Job Period to this value to speed up the epoch computation.
    private static final long TEST_EPOCH_JOB_PERIOD_MS = 5000;
    // Expected model versions.
    private static final long EXPECTED_MODEL_VERSION = 4L;
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
    private static final int ON_DEVICE_CLASSIFIER_TYPE = 1;
    private static final int PRECOMPUTED_CLASSIFIER_TYPE = 2;

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
            AdservicesTestHelper.getAdServicesPackageName(sContext, TAG);

    // Assert message statements.
    private static final String INCORRECT_MODEL_VERSION_MESSAGE =
            "Incorrect model version detected. Please repo sync, build and install the new apex.";
    private static final String INCORRECT_TAXONOMY_VERSION_MESSAGE =
            "Incorrect taxonomy version detected. Please repo sync, build and install the new"
                    + " apex.";

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAnyLevel();

    // Skip the test if it runs on unsupported platforms.
    @Rule(order = 1)
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    // Sets flags used in the test (and automatically reset them at the end)
    @Rule(order = 2)
    public final AdServicesFlagsSetterRule flags = AdServicesFlagsSetterRule.forTopicsE2ETests();

    @Before
    public void setup() throws Exception {
        // Kill adservices process to avoid interfering from other tests.
        AdservicesTestHelper.killAdservicesProcess(ADSERVICES_PACKAGE_NAME);

        // We need to skip 3 epochs so that if there is any usage from other test runs, it will
        // not be used for epoch retrieval.
        Thread.sleep(3 * TEST_EPOCH_JOB_PERIOD_MS);

        flags.setTopicsEpochJobPeriodMsForTests(TEST_EPOCH_JOB_PERIOD_MS);

        // We need to turn off random topic so that we can verify the returned topic.
        flags.setTopicsPercentageForRandomTopicForTests(TEST_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC);

        // TODO(b/263297331): Handle rollback support for R and S.
    }

    @Test
    // @RequiresGlobalKillSwitchDisabled // TODO(b/284971005): re-add when it uses the rule / runner
    public void testTopicsManager_testTopicsKillSwitch() {
        // Override Topics kill switch to disable Topics API.
        flags.setTopicsKillSwitch(true);

        // Set classifier flag to use precomputed-then-on-device classifier.
        flags.setTopicsClassifierType(DEFAULT_CLASSIFIER_TYPE);

        // Default classifier uses the precomputed list first, then on-device classifier.
        AdvertisingTopicsClient advertisingTopicsClient =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk1")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .setUseGetMethodToCreateManagerInstance(false)
                        .build();

        // As the kill switch for Topics API is enabled, we should expect failure here.
        Exception e =
                assertThrows(
                        ExecutionException.class, () -> advertisingTopicsClient.getTopics().get());
        assertThat(e).hasCauseThat().isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testTopicsManager_disableDirectAppCalls_testEmptySdkNameRequests() {
        flags.setTopicsDisableDirectAppCalls(true);

        AdvertisingTopicsClient advertisingTopicsClient =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .setUseGetMethodToCreateManagerInstance(false)
                        .build();

        Exception e =
                assertThrows(
                        ExecutionException.class, () -> advertisingTopicsClient.getTopics().get());
        assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testTopicsManager_testOnDeviceKillSwitch_shouldUsePrecomputedList()
            throws Exception {
        // Override Topics on device classifier kill switch to disable on device classifier.
        flags.setTopicsOnDeviceClassifierKillSwitch(true);

        // Set classifier flag to use on-device classifier.
        flags.setTopicsClassifierType(ON_DEVICE_CLASSIFIER_TYPE);

        // The Test App has 1 SDK: sdk5
        // sdk3 calls the Topics API.
        AdvertisingTopicsClient advertisingTopicsClient5 =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk5")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .setUseGetMethodToCreateManagerInstance(false)
                        .build();

        // At beginning, Sdk5 receives no topic.
        GetTopicsResponse sdk5Result = advertisingTopicsClient5.getTopics().get();
        assertThat(sdk5Result.getTopics()).isEmpty();

        // Now force the Epoch Computation Job. This should be done in the same epoch for
        // callersCanLearnMap to have the entry for processing.
        forceEpochComputationJob();

        // Wait to the next epoch. We will not need to do this after we implement the fix in
        // go/rb-topics-epoch-scheduling
        Thread.sleep(TEST_EPOCH_JOB_PERIOD_MS);

        // Since the sdk5 called the Topics API in the previous Epoch, it should receive some topic.
        sdk5Result = advertisingTopicsClient5.getTopics().get();
        assertThat(sdk5Result.getTopics()).isNotEmpty();

        // We only have 5 topics classified by the classifier.
        // The app will be assigned one random topic from one of these 5 topics.
        assertThat(sdk5Result.getTopics()).hasSize(1);
        Topic topic = sdk5Result.getTopics().get(0);

        // Expected asset versions to be bundled in the build.
        // If old assets are being picked up, repo sync, build and install the new apex again.
        assertWithMessage(INCORRECT_MODEL_VERSION_MESSAGE)
                .that(topic.getModelVersion())
                .isEqualTo(EXPECTED_MODEL_VERSION);
        assertWithMessage(INCORRECT_TAXONOMY_VERSION_MESSAGE)
                .that(topic.getTaxonomyVersion())
                .isEqualTo(EXPECTED_TAXONOMY_VERSION);

        // Topic should be from the precomputed list and not the on device classifier due to the
        // override.
        List<Integer> expectedTopTopicIds = Arrays.asList(10147, 10253, 10175, 10254, 10333);
        assertThat(topic.getTopicId()).isIn(expectedTopTopicIds);
    }

    @Test
    public void testTopicsManager_runDefaultClassifier_usingGetMethodToCreateManager()
            throws Exception {
        testTopicsManager_runDefaultClassifier(/* useGetMethodToCreateManager */ true);
    }

    @Test
    public void testTopicsManager_runDefaultClassifier() throws Exception {
        testTopicsManager_runDefaultClassifier(/* useGetMethodToCreateManager */ false);
    }

    private void testTopicsManager_runDefaultClassifier(boolean useGetMethodToCreateManager)
            throws Exception {
        // Set classifier flag to use precomputed-then-on-device classifier.
        flags.setTopicsClassifierType(DEFAULT_CLASSIFIER_TYPE);

        // Default classifier uses the precomputed list first, then on-device classifier.
        // The Test App has 2 SDKs: sdk1 calls the Topics API and sdk2 does not.
        // Sdk1 calls the Topics API.
        AdvertisingTopicsClient advertisingTopicsClient1 =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk1")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .setUseGetMethodToCreateManagerInstance(useGetMethodToCreateManager)
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

        // We only have 1 test app which has 5 classification topics: 10175,10147,10254,10333,10253)
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
        assertThat(topic.getTopicId()).isIn(Arrays.asList(10175, 10147, 10254, 10333, 10253));

        // Sdk 2 did not call getTopics API. So it should not receive any topic.
        AdvertisingTopicsClient advertisingTopicsClient2 =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk2")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .setUseGetMethodToCreateManagerInstance(useGetMethodToCreateManager)
                        .build();

        GetTopicsResponse sdk2Result2 = advertisingTopicsClient2.getTopics().get();
        assertThat(sdk2Result2.getTopics()).isEmpty();
    }

    @Test
    public void testTopicsManager_runOnDeviceClassifier_usingGetMethodToCreateManager()
            throws Exception {
        testTopicsManager_runOnDeviceClassifier(true);
    }

    @Test
    public void testTopicsManager_runOnDeviceClassifier() throws Exception {
        testTopicsManager_runOnDeviceClassifier(false);
    }

    private void testTopicsManager_runOnDeviceClassifier(boolean useGetMethodToCreateManager)
            throws Exception {
        // Set classifier flag to use on-device classifier.
        flags.setTopicsClassifierType(ON_DEVICE_CLASSIFIER_TYPE);

        // Set number of top labels returned by the on-device classifier to 5.
        flags.setTopicsClassifierNumberOfTopLabels(TEST_CLASSIFIER_NUMBER_OF_TOP_LABELS);
        // Remove classifier threshold by setting it to 0.
        flags.setTopicsClassifierThreshold(TEST_CLASSIFIER_THRESHOLD);

        // The Test App has 1 SDK: sdk3
        // sdk3 calls the Topics API.
        AdvertisingTopicsClient advertisingTopicsClient3 =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk3")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .setUseGetMethodToCreateManagerInstance(useGetMethodToCreateManager)
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

        // Top 5 classifications for empty string with v4 model are:
        // S-: [10420, 10189, 10301, 10230, 10010].
        // T+: [10166, 10010, 10301, 10230, 10184].
        // V4 model uses package name as part of input, which differs between
        // versions for back-compat, changing the returned topics for each version.
        // This is computed by running the model on the device; topics are checked
        // depending on whether the package name is that for S- or T+.
        // Returned topic is one of the 5 classification topics of the test app.
        List<Integer> expectedTopTopicIds;
        if (ADSERVICES_PACKAGE_NAME.contains("ext.services")) {
            expectedTopTopicIds = Arrays.asList(10420, 10189, 10301, 10230, 10010);
        } else {
            expectedTopTopicIds = Arrays.asList(10166, 10010, 10301, 10230, 10184);
        }
        assertThat(topic.getTopicId()).isIn(expectedTopTopicIds);
    }

    @Test
    public void testTopicsManager_runPrecomputedClassifier_usingGetMethodToCreateManager()
            throws Exception {
        testTopicsManager_runPrecomputedClassifier(/* useGetMethodToCreateManager = */ true);
    }

    @Test
    public void testTopicsManager_runPrecomputedClassifier() throws Exception {
        testTopicsManager_runPrecomputedClassifier(/* useGetMethodToCreateManager = */ false);
    }

    private void testTopicsManager_runPrecomputedClassifier(boolean useGetMethodToCreateManager)
            throws Exception {
        // Set classifier flag to use precomputed classifier.
        flags.setTopicsClassifierType(PRECOMPUTED_CLASSIFIER_TYPE);

        // The Test App has 1 SDK: sdk4
        // sdk4 calls the Topics API.
        AdvertisingTopicsClient advertisingTopicsClient4 =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk4")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .setUseGetMethodToCreateManagerInstance(useGetMethodToCreateManager)
                        .build();

        // At beginning, Sdk4 receives no topic.
        GetTopicsResponse sdk4Result = advertisingTopicsClient4.getTopics().get();
        assertThat(sdk4Result.getTopics()).isEmpty();

        // Now force the Epoch Computation Job. This should be done in the same epoch for
        // callersCanLearnMap to have the entry for processing.
        forceEpochComputationJob();

        // Wait to the next epoch. We will not need to do this after we implement the fix in
        // go/rb-topics-epoch-scheduling
        Thread.sleep(TEST_EPOCH_JOB_PERIOD_MS);

        // Since the sdk4 called the Topics API in the previous Epoch, it should receive some topic.
        sdk4Result = advertisingTopicsClient4.getTopics().get();
        assertThat(sdk4Result.getTopics()).isNotEmpty();

        // We only have 5 topics classified by the precomputed classifier.
        // The app will be assigned one random topic from one of these 5 topics.
        assertThat(sdk4Result.getTopics()).hasSize(1);
        Topic topic = sdk4Result.getTopics().get(0);

        // Expected asset versions to be bundled in the build.
        // If old assets are being picked up, repo sync, build and install the new apex again.
        assertWithMessage(INCORRECT_MODEL_VERSION_MESSAGE)
                .that(topic.getModelVersion())
                .isEqualTo(EXPECTED_MODEL_VERSION);
        assertWithMessage(INCORRECT_TAXONOMY_VERSION_MESSAGE)
                .that(topic.getTaxonomyVersion())
                .isEqualTo(EXPECTED_TAXONOMY_VERSION);

        // Top 5 topic ids as listed in precomputed_app_list.csv
        List<Integer> expectedTopTopicIds = Arrays.asList(10147, 10253, 10175, 10254, 10333);
        assertThat(topic.getTopicId()).isIn(expectedTopTopicIds);
    }

    @Test
    @RequiresLowRamDevice
    @RequiresSdkLevelAtLeastS(reason = "OutcomeReceiver is not available on R")
    public void testGetTopics_lowRamDevice() throws Exception {
        TopicsManager manager = TopicsManager.get(sContext);
        assertWithMessage("manager").that(manager).isNotNull();
        OutcomeReceiverForTests<GetTopicsResponse> receiver = new OutcomeReceiverForTests<>();

        assertThrows(
                IllegalStateException.class,
                () ->
                        manager.getTopics(
                                new GetTopicsRequest.Builder().build(),
                                CALLBACK_EXECUTOR,
                                receiver));

        // TODO(b/295235571): remove assertThrows above and instead check the callback:
        if (false) {
            receiver.assertFailure(IllegalStateException.class);
        }
    }

    /** Forces JobScheduler to run the Epoch Computation job */
    private void forceEpochComputationJob() {
        ShellUtils.runShellCommand(
                "cmd jobscheduler run -f" + " " + ADSERVICES_PACKAGE_NAME + " " + EPOCH_JOB_ID);
    }
}
