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

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.LogUtil;
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
public class TopicsManagerTest {
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

    // Used to get the package name. Copied over from com.android.adservices.AdServicesCommon
    private static final String TOPICS_SERVICE_NAME = "android.adservices.TOPICS_SERVICE";
    private static final String ADSERVICES_PACKAGE_NAME = getAdServicesPackageName();

    @Before
    public void setup() throws Exception {
        // We need to skip 3 epochs so that if there is any usage from other test runs, it will
        // not be used for epoch retrieval.
        Thread.sleep(3 * TEST_EPOCH_JOB_PERIOD_MS);

        overrideDisableTopicsEnrollmentCheck("1");
        overrideEpochPeriod(TEST_EPOCH_JOB_PERIOD_MS);

        // We need to turn off random topic so that we can verify the returned topic.
        overridePercentageForRandomTopic(TEST_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC);

        // We need to turn the Consent Manager into debug mode
        overrideConsentManagerDebugMode();

        // Turn off MDD to avoid model mismatching
        switchOnAndOffMDD(true);
    }

    @After
    public void teardown() {
        // Reset back the original values.
        overrideDisableTopicsEnrollmentCheck("0");
        overrideEpochPeriod(TOPICS_EPOCH_JOB_PERIOD_MS);
        overridePercentageForRandomTopic(TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC);
        switchOnAndOffMDD(false);
    }

    @Test
    public void testTopicsManager() throws Exception {
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
        assertThat(topic.getTopicId()).isIn(Arrays.asList(10147,10253,10175,10254,10333));

        assertThat(topic.getModelVersion()).isAtLeast(1L);
        assertThat(topic.getTaxonomyVersion()).isAtLeast(1L);

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

    // Switch on/off for MDD service. Default value is false, which means MDD is enabled.
    private void switchOnAndOffMDD(boolean isSwitchedOff) {
        ShellUtils.runShellCommand(
                "setprop debug.adservices.mdd_background_task_kill_switch " + isSwitchedOff);
    }

    // Override the flag to disable Topics enrollment check.
    private void overrideDisableTopicsEnrollmentCheck(String val) {
        // Setting it to 1 here disables the Topics' enrollment check.
        ShellUtils.runShellCommand(
                "setprop debug.adservices.disable_topics_enrollment_check " + val);
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

    // Override the Consent Manager behaviour - Consent Given
    private void overrideConsentManagerDebugMode() {
        ShellUtils.runShellCommand("setprop debug.adservices.consent_manager_debug_mode true");
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
            LogUtil.e(
                    "Failed to find resolveInfo for adServices service. Intent action: "
                            + TOPICS_SERVICE_NAME);
            return null;
        }

        if (resolveInfos.size() > 1) {
            LogUtil.e(
                    "Found multiple services (%1$s) for the same intent action (%2$s)",
                    TOPICS_SERVICE_NAME, resolveInfos.toString());
            return null;
        }

        final ServiceInfo serviceInfo = resolveInfos.get(0).serviceInfo;
        if (serviceInfo == null) {
            LogUtil.e("Failed to find serviceInfo for adServices service");
            return null;
        }

        return serviceInfo.packageName;
    }
}
