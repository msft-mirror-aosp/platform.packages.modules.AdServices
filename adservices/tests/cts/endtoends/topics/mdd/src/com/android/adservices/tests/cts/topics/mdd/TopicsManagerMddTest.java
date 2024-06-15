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

package com.android.adservices.tests.cts.topics.mdd;

import static com.android.adservices.service.FlagsConstants.KEY_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_EPOCH_JOB_PERIOD_MS;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC;
import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.topics.GetTopicsResponse;
import android.adservices.topics.Topic;

import com.android.adservices.common.AdServicesSupportHelper;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;
import com.android.adservices.shared.testing.annotations.SetLongFlag;
import com.android.adservices.shared.testing.annotations.SetStringFlag;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Run GetTopics API with following steps.
 * <li>Bind AdvertisingTopicsClient to allow background MDD jobs.
 * <li>Override MDD URI and trigger download.
 * <li>Unbind and re-bind AdvertisingTopicsClient to pick new downloaded assets.
 * <li>Verify topics are from the downloaded assets.
 */
@SetIntegerFlag(
        name = KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC,
        value = TopicsManagerMddTest.TEST_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC)
@SetLongFlag(
        name = KEY_TOPICS_EPOCH_JOB_PERIOD_MS,
        value = TopicsManagerMddTest.TEST_EPOCH_JOB_PERIOD_MS)
@SetStringFlag(
        name = KEY_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL,
        value = TopicsManagerMddTest.TEST_MDD_MANIFEST_FILE_URL)
public final class TopicsManagerMddTest extends CtsAdServicesMddTestCase {
    // The JobId of the Epoch Computation.
    private static final int EPOCH_JOB_ID = 2;
    // Job ID for Mdd Wifi Charging Task.
    public static final int MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID = 14;

    // Override the Epoch Job Period to this value to speed up the epoch computation.
    static final long TEST_EPOCH_JOB_PERIOD_MS = 3000;

    // Use 0 percent for random topic in the test so that we can verify the returned topic.
    static final int TEST_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC = 0;

    // Manifest file that points to CTS test assets:
    // http://google3/wireless/android/adservices/mdd/topics_classifier/cts_test_1/
    // These assets are have asset version set to 0 for verification in tests.
    static final String TEST_MDD_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-topics-classifier/2055/f8026ab834d1a287920b9a4ffe7bb1f04d200885";

    // Waiting time for assets to be downloaded after triggering MDD job.
    private static final long TEST_MDD_DOWNLOAD_WAIT_TIME_MS = 45000;

    // Waiting time for the AdvertisingTopicsClient to unbind.
    private static final long TEST_UNBIND_WAIT_TIME = 3000;

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private final String mAdServicesPackageName =
            AdServicesSupportHelper.getInstance().getAdServicesPackageName();

    @Before
    public void setup() throws Exception {
        // Kill AdServices process.
        AdservicesTestHelper.killAdservicesProcess(mAdServicesPackageName);

        // We need to skip 3 epochs so that if there is any usage from other test runs, it will
        // not be used for epoch retrieval.
        Thread.sleep(3 * TEST_EPOCH_JOB_PERIOD_MS);
    }

    @Test
    @Ignore("b/299573314")
    public void testTopicsManager_downloadModelViaMdd_runPrecomputedClassifier() throws Exception {
        // The Test App has 1 SDK: sdk1
        // sdk1 calls the Topics API.
        AdvertisingTopicsClient advertisingTopicsClient1 =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk1")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        // Call Topics API to bind TOPICS_SERVICE.
        GetTopicsResponse sdk1Result = advertisingTopicsClient1.getTopics().get();
        assertThat(sdk1Result.getTopics()).isEmpty();

        // Force epoch computation to pick up the model if download is complete. If not, trigger
        // pending download.
        forceEpochComputationJob();

        // Force to trigger the pending downloads in background.
        triggerAndWaitForMddToFinishDownload();

        // Kill AdServices API to unbind TOPICS_SERVICE.
        AdservicesTestHelper.killAdservicesProcess(mAdServicesPackageName);

        // Wait for AdvertisingTopicsClient to unbind.
        Thread.sleep(TEST_UNBIND_WAIT_TIME);

        // Create a new AdvertisingTopicsClient to bind TOPICS_SERVICE again.
        advertisingTopicsClient1 =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk1")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        // At beginning, Sdk1 receives no topic.
        sdk1Result = advertisingTopicsClient1.getTopics().get();
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

        // We only have 5 topics classified by the on-device classifier.
        // The app will be assigned one random topic from one of these 5 topics.
        assertThat(sdk1Result.getTopics()).hasSize(1);
        Topic topic = sdk1Result.getTopics().get(0);

        // Top 5 classifications for  with v1 model are [10301, 10302, 10303, 10304, 10305].
        List<Integer> expectedTopTopicIds = Arrays.asList(10301, 10302, 10303, 10304, 10305);
        assertThat(topic.getTopicId()).isIn(expectedTopTopicIds);

        // Verify assets are from the downloaded assets. These assets have asset version set to 0
        // for verification.
        // Test assets downloaded for CTS test:
        // http://google3/wireless/android/adservices/mdd/topics_classifier/cts_test_1/
        assertThat(topic.getModelVersion()).isEqualTo(0L);
        assertThat(topic.getTaxonomyVersion()).isEqualTo(0L);
    }

    private void triggerAndWaitForMddToFinishDownload() throws InterruptedException {
        // Forces JobScheduler to run Mdd.
        runShellCommand(
                "cmd jobscheduler run -f"
                        + " "
                        + mAdServicesPackageName
                        + " "
                        + MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID);

        // Wait for TEST_MDD_DOWNLOAD_WAIT_TIME_MS seconds for the assets to be downloaded.
        Thread.sleep(TEST_MDD_DOWNLOAD_WAIT_TIME_MS);
    }

    /** Forces JobScheduler to run the Epoch Computation job */
    private void forceEpochComputationJob() {
        runShellCommand("cmd jobscheduler run -f %s %d", mAdServicesPackageName, EPOCH_JOB_ID);
    }
}
