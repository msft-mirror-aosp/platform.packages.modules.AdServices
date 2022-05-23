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

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public class TopicsManagerTest {
    private static final String TAG = "TopicsManagerTest";
    private static final String SERVICE_APK_NAME = "com.android.adservices.api";

    // The JobId of the Epoch Computation.
    private static final int EPOCH_JOB_ID = 2;

    // Override the Epoch Job Period to this value to speed up the epoch computation.
    private static final long TEST_EPOCH_JOB_PERIOD_MS = 2000;

    // Default Epoch Period.
    private static final long TOPICS_EPOCH_JOB_PERIOD_MS = 7 * 86_400_000; // 7 days.

    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    @Test
    public void testTopicsManager() throws Exception {
        overrideEpochPeriod(TEST_EPOCH_JOB_PERIOD_MS);

        AdvertisingTopicsClient advertisingTopicsClient1 =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk1")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        // Call the Topics API.
        GetTopicsResponse unusedSdk1Result = advertisingTopicsClient1.getTopics().get();

        // Wait to the next epoch. We will not need to do this after we implement the fix in
        // go/rb-topics-epoch-scheduling
        Thread.sleep(TEST_EPOCH_JOB_PERIOD_MS);

        // Now force the Epoch Computation Job.
        forceRunJob();

        // Since the sdk1 called the Topics API in the previous Epoch, it should receive some topic.
        GetTopicsResponse sdk1Result = advertisingTopicsClient1.getTopics().get();
        assertThat(sdk1Result.getTaxonomyVersions()).isNotEmpty();
        assertThat(sdk1Result.getModelVersions()).isNotEmpty();
        assertThat(sdk1Result.getTopics()).isNotEmpty();

        // Reset back the original value.
        overrideEpochPeriod(TOPICS_EPOCH_JOB_PERIOD_MS);
    }

    // Override the Epoch Period to shorten the Epoch Length in the test.
    private void overrideEpochPeriod(long overrideEpochPeriod) {
        ShellUtils.runShellCommand("setprop debug.adservices.topics_epoch_job_period_ms "
                + overrideEpochPeriod);
    }

    /** Forces JobScheduler to run the job */
    private void forceRunJob() throws Exception {
        ShellUtils.runShellCommand("cmd jobscheduler run -f"
                + " " + SERVICE_APK_NAME + " " + EPOCH_JOB_ID);
    }
}
