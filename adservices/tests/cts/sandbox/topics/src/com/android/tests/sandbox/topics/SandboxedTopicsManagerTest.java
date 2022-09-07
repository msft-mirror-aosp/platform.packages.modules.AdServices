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

package com.android.tests.sandbox.topics;

import static com.google.common.truth.Truth.assertThat;

import android.annotation.NonNull;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/*
 * Test Topics API running within the Sandbox.
 */
@RunWith(JUnit4.class)
public class SandboxedTopicsManagerTest {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final String SDK_NAME = "com.android.tests.providers.sdk1";
    // Used to get the package name. Copied over from com.android.adservices.AdServicesCommon
    private static final String TOPICS_SERVICE_NAME = "android.adservices.TOPICS_SERVICE";

    // The JobId of the Epoch Computation.
    private static final int EPOCH_JOB_ID = 2;

    // Override the Epoch Job Period to this value to speed up the epoch computation.
    private static final long TEST_EPOCH_JOB_PERIOD_MS = 6000;

    // Allow SDK to start as it takes longer after enabling more checks.
    private static final long EXECUTION_WAITING_TIME = 1000;

    // Default Epoch Period.
    private static final long TOPICS_EPOCH_JOB_PERIOD_MS = 7 * 86_400_000; // 7 days.

    // Use 0 percent for random topic in the test so that we can verify the returned topic.
    private static final int TEST_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC = 0;
    private static final int TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC = 5;

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    @Before
    public void setup() throws TimeoutException {
        // Start a foreground activity
        SimpleActivity.startAndWaitForSimpleActivity(sContext, Duration.ofMillis(1000));
        overridingBeforeTest();
    }

    @After
    public void shutDown() {
        overridingAfterTest();
        SimpleActivity.stopSimpleActivity(sContext);
    }

    @Test
    public void loadSdkAndRunTopicsApi() throws Exception {
        final SdkSandboxManager sdkSandboxManager =
                sContext.getSystemService(SdkSandboxManager.class);

        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        sdkSandboxManager.loadSdk(SDK_NAME, new Bundle(), CALLBACK_EXECUTOR, callback);

        Thread.sleep(EXECUTION_WAITING_TIME);

        // Now force the Epoch Computation Job. This should be done in the same epoch for
        // callersCanLearnMap to have the entry for processing.
        forceEpochComputationJob();

        // Wait to the next epoch.
        Thread.sleep(TEST_EPOCH_JOB_PERIOD_MS);

        // This verifies that the Sdk1 in the Sandbox gets back the correct topic.
        // If the Sdk1 did not get correct topic, it will trigger the callback.onLoadSdkError
        assertThat(callback.isLoadSdkSuccessful()).isTrue();
    }

    private void overridingBeforeTest() {
        overridingAdservicesLoggingLevel("VERBOSE");
        // Turn off MDD to avoid model mismatching
        disableMddBackgroundTasks(true);
        overrideDisableTopicsEnrollmentCheck("1");
        // The setup for this test:
        // SandboxedTopicsManagerTest is the test app. It will load the Sdk1 into the Sandbox.
        // The Sdk1 (running within the Sandbox) will query Topics API and verify that the correct
        // Topics are returned.
        // After Sdk1 verifies the result, it will communicate back to the
        // SandboxedTopicsManagerTest via the loadSdk's callback.
        // In this test, we use the loadSdk's callback as a 2-way communications between the Test
        // app (this class) and the Sdk running within the Sandbox process.

        // We need to turn the Consent Manager into debug mode
        overrideConsentManagerDebugMode();

        overrideEpochPeriod(TEST_EPOCH_JOB_PERIOD_MS);

        // We need to turn off random topic so that we can verify the returned topic.
        overridePercentageForRandomTopic(TEST_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC);
    }

    // Reset back the original values.
    private void overridingAfterTest() {
        overrideDisableTopicsEnrollmentCheck("0");
        overrideEpochPeriod(TOPICS_EPOCH_JOB_PERIOD_MS);
        overridePercentageForRandomTopic(TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC);
        disableMddBackgroundTasks(false);
        overridingAdservicesLoggingLevel("INFO");
    }

    // Override the flag to disable Topics enrollment check.
    private void overrideDisableTopicsEnrollmentCheck(String val) {
        // Setting it to 1 here disables the Topics enrollment check.
        ShellUtils.runShellCommand(
                "setprop debug.adservices.disable_topics_enrollment_check " + val);
    }

    // Override the Epoch Period to shorten the Epoch Length in the test.
    private void overrideEpochPeriod(long overrideEpochPeriod) {
        ShellUtils.runShellCommand(
                "setprop debug.adservices.topics_epoch_job_period_ms " + overrideEpochPeriod);
    }

    // Override the Consent Manager behaviour - Consent Given
    private void overrideConsentManagerDebugMode() {
        ShellUtils.runShellCommand("setprop debug.adservices.consent_manager_debug_mode true");
    }

    // Override the Percentage For Random Topic in the test.
    private void overridePercentageForRandomTopic(long overridePercentage) {
        ShellUtils.runShellCommand(
                "setprop debug.adservices.topics_percentage_for_random_topics "
                        + overridePercentage);
    }

    // Switch on/off for MDD service. Default value is false, which means MDD is enabled.
    private void disableMddBackgroundTasks(boolean isSwitchedOff) {
        ShellUtils.runShellCommand(
                "setprop debug.adservices.mdd_background_task_kill_switch " + isSwitchedOff);
    }

    /** Forces JobScheduler to run the Epoch Computation job */
    private void forceEpochComputationJob() {
        ShellUtils.runShellCommand(
                "cmd jobscheduler run -f" + " " + getAdServicesPackageName() + " " + EPOCH_JOB_ID);
    }

    private void overridingAdservicesLoggingLevel(String loggingLevel) {
        ShellUtils.runShellCommand("setprop log.tag.adservices %s", loggingLevel);
    }

    // Used to get the package name. Copied over from com.android.adservices.AndroidServiceBinder
    @NonNull
    private static String getAdServicesPackageName() {
        final Intent intent = new Intent(TOPICS_SERVICE_NAME);
        final List<ResolveInfo> resolveInfos =
                sContext.getPackageManager()
                        .queryIntentServices(intent, PackageManager.MATCH_SYSTEM_ONLY);

        if (resolveInfos == null || resolveInfos.isEmpty()) {
            String errorMsg =
                    "Failed to find resolveInfo for adServices service. Intent action: "
                            + TOPICS_SERVICE_NAME;
            throw new IllegalStateException(errorMsg);
        }

        if (resolveInfos.size() > 1) {
            String errorMsg = "Found multiple services for the same intent action. ";
            throw new IllegalStateException(errorMsg);
        }

        final ServiceInfo serviceInfo = resolveInfos.get(0).serviceInfo;
        if (serviceInfo == null) {
            String errorMsg = "Failed to find serviceInfo for adServices service. ";
            throw new IllegalStateException(errorMsg);
        }

        return serviceInfo.packageName;
    }
}
