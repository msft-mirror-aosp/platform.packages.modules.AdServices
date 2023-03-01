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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Flag "enable_database_schema_version_5" was added to guard Topics API newly added table
 * "TopicContributorsTable".
 *
 * <p>This test is to verify when the flag is changed as false -> true -> false, database will be
 * created successfully. That says, it handles DB Upgrade and DB Downgrade correctly.
 */
@RunWith(JUnit4.class)
public class TopicContributorsTableEnableDisableTest {
    private static final String LOG_TAG = "adservices";
    // The JobId of the Epoch Computation.
    private static final int EPOCH_JOB_ID = 2;

    // Override the Epoch Job Period to this value to speed up the epoch computation.
    private static final long TEST_EPOCH_JOB_PERIOD_MS = 3000;

    // As adb commands and broadcast processing require time to execute, add this waiting time to
    // allow them to have enough time to be executed. This helps to reduce the test flaky.
    private static final long EXECUTION_WAITING_TIME = 300;

    // Default Epoch Period.
    private static final long TOPICS_EPOCH_JOB_PERIOD_MS = 7 * 86_400_000; // 7 days.

    // Use 0 percent for random topic in the test so that we can verify the returned topic.
    private static final int TEST_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC = 0;
    private static final int TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC = 5;

    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private static final String ADSERVICES_PACKAGE_NAME =
            AdservicesCtsHelper.getAdServicesPackageName(sContext, LOG_TAG);

    @Before
    public void setup() throws Exception {
        // Skip the test if it runs on unsupported platforms.
        Assume.assumeTrue(AdservicesCtsHelper.isDeviceSupported());

        // We need to skip 4 epochs so that if there is any usage from other test runs, it will
        // not be used for epoch retrieval.
        Thread.sleep(4 * TEST_EPOCH_JOB_PERIOD_MS);

        overrideEpochPeriod(TEST_EPOCH_JOB_PERIOD_MS);
        // We need to turn off random topic so that we can verify the returned topic.
        overridePercentageForRandomTopic(TEST_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC);
        // Override API rate limit to allow consecutive Topics API calls.
        overrideApiRateLimit(10);

        // Set initial state of TopicsContributorsTable as disabled
        enableTopicContributorsTable(false);
        // TODO(b/263297331): Handle rollback support for R and S.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            overrideConsentSourceOfTruth(/* PPAPI_ONLY */ 1);
        }
    }

    @After
    public void teardown() {
        overrideEpochPeriod(TOPICS_EPOCH_JOB_PERIOD_MS);
        overridePercentageForRandomTopic(TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC);
        overrideApiRateLimit(1);
        overrideConsentSourceOfTruth(null);
    }

    @Test
    public void testTableEnabledAndDisabled() throws Exception {
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

        // topic is one of the 5 classification topics of the Test App.
        assertThat(topic.getTopicId()).isIn(Arrays.asList(10147, 10253, 10175, 10254, 10333));
        assertThat(topic.getModelVersion()).isAtLeast(1L);
        assertThat(topic.getTaxonomyVersion()).isAtLeast(1L);

        // Enable TopicContributorsTable and Database should do upgrade.
        enableTopicContributorsTable(true);
        // Kill Adservices to 1) allow database to re-create 2) clear CacheManager, so it will query
        // database again.
        killAdServices();
        Thread.sleep(EXECUTION_WAITING_TIME);

        // Verify database is able to query. Skip checking detailed topics result.
        sdk1Result = advertisingTopicsClient1.getTopics().get();
        assertThat(sdk1Result.getTopics()).isNotEmpty();

        // Disable TopicContributorsTable and Database should do downgrade.
        enableTopicContributorsTable(false);
        // Kill Adservices to 1) allow database to re-create 2) clear CacheManager, so it will query
        // database again.
        killAdServices();
        Thread.sleep(EXECUTION_WAITING_TIME);

        // Verify database is able to query. Skip checking detailed topics result.
        sdk1Result = advertisingTopicsClient1.getTopics().get();
        assertThat(sdk1Result.getTopics()).isNotEmpty();
    }

    // Enable/disable TopicContributorsTable
    private void enableTopicContributorsTable(boolean isEnabled) {
        ShellUtils.runShellCommand(
                "device_config put adservices enable_database_schema_version_5 " + isEnabled);
    }

    // Override API rate limit to allow consecutive Topics API calls.
    private void overrideApiRateLimit(int times) {
        ShellUtils.runShellCommand(
                "setprop debug.adservices.sdk_request_permits_per_second " + times);
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

    // Force stop AdServices API.
    public void killAdServices() {
        // adb shell am force-stop com.google.android.adservices.api
        ShellUtils.runShellCommand("am force-stop" + " " + ADSERVICES_PACKAGE_NAME);
    }

    private void overrideConsentSourceOfTruth(Integer value) {
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth " + value);
    }
}
