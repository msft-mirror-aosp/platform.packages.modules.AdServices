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
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public class TopicsManagerTest {
    private static final String TAG = "TopicsManagerTest";
    private static final String SERVICE_APK_NAME = "com.android.adservices.api";

    // The JobId of the Epoch Computation.
    private static final int EPOCH_JOB_ID = 2;

    // Override the Epoch Job Period to this value to speed up the epoch computation.
    private static final long TEST_EPOCH_JOB_PERIOD_MS = 3000;

    // Default Epoch Period.
    private static final long TOPICS_EPOCH_JOB_PERIOD_MS = 7 * 86_400_000; // 7 days.

    // Use 0 percent for random topic in the test so that we can verify the returned topic.
    private static final int TEST_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC = 0;
    private static final int TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC = 5;

    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    @Before
    public void setup() throws InterruptedException {
        killPpApiProcess();
        // We need to skip 3 epochs so that if there is any usage from other test runs, it will
        // not be used for epoch retrieval.
        Thread.sleep(3 * TEST_EPOCH_JOB_PERIOD_MS);
    }

    @Test
    public void testTopicsManager() throws Exception {
        overrideEpochPeriod(TEST_EPOCH_JOB_PERIOD_MS);

        // We need to turn off random topic so that we can verify the returned topic.
        overridePercentageForRandomTopic(TEST_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC);

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
        assertThat(sdk1Result.getTaxonomyVersions()).isEmpty();
        assertThat(sdk1Result.getModelVersions()).isEmpty();
        assertThat(sdk1Result.getTopics()).isEmpty();

        // Wait to the next epoch. We will not need to do this after we implement the fix in
        // go/rb-topics-epoch-scheduling
        Thread.sleep(TEST_EPOCH_JOB_PERIOD_MS);

        // Now force the Epoch Computation Job.
        forceEpochComputationJob();

        // Since the sdk1 called the Topics API in the previous Epoch, it should receive some topic.
        sdk1Result = advertisingTopicsClient1.getTopics().get();
        assertThat(sdk1Result.getTaxonomyVersions()).isNotEmpty();
        assertThat(sdk1Result.getModelVersions()).isNotEmpty();
        assertThat(sdk1Result.getTopics()).isNotEmpty();

        // We only have 1 test app which has 5 classification topics: "1740", "529", "911", "14",
        // "590".
        // These 5 classification topics will become top 5 topics of the epoch since there is
        // no other apps calling Topics API.
        // The app will be assigned one random topic from one of these 5 topics.
        assertThat(sdk1Result.getTopics()).hasSize(1);
        String topic = sdk1Result.getTopics().get(0);

        // topic is one of the 5 classification topics of the Test App.
        assertThat(topic).isIn(Arrays.asList("1740", "529", "911", "14", "590"));

        // Sdk 2 did not call getTopics API. So it should not receive any topic.
        AdvertisingTopicsClient advertisingTopicsClient2 = new AdvertisingTopicsClient.Builder()
                .setContext(sContext)
                .setSdkName("sdk2")
                .setExecutor(CALLBACK_EXECUTOR)
                .build();

        GetTopicsResponse sdk2Result2 = advertisingTopicsClient2.getTopics().get();
        assertThat(sdk2Result2.getTaxonomyVersions()).isEmpty();
        assertThat(sdk2Result2.getModelVersions()).isEmpty();
        assertThat(sdk2Result2.getTopics()).isEmpty();

        // Reset back the original values.
        overrideEpochPeriod(TOPICS_EPOCH_JOB_PERIOD_MS);
        overridePercentageForRandomTopic(TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC);
    }

    // Override the Epoch Period to shorten the Epoch Length in the test.
    private void overrideEpochPeriod(long overrideEpochPeriod) {
        ShellUtils.runShellCommand("setprop debug.adservices.topics_epoch_job_period_ms "
                + overrideEpochPeriod);
    }

    // Override the Percentage For Random Topic in the test.
    private void overridePercentageForRandomTopic(long overridePercentage) {
        ShellUtils.runShellCommand(
                "setprop debug.adservices.topics_percentage_for_random_topics "
                + overridePercentage);
    }

    private void killPpApiProcess() {
        ShellUtils.runShellCommand("su 0 killall -9 com.google.android.adservices.api");
    }

    /** Forces JobScheduler to run the Epoch Computation job */
    private void forceEpochComputationJob() throws Exception {
        ShellUtils.runShellCommand("cmd jobscheduler run -f"
                + " " + SERVICE_APK_NAME + " " + EPOCH_JOB_ID);
    }
}
