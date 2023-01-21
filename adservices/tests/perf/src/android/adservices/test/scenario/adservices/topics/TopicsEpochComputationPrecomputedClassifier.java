/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.adservices.test.scenario.adservices.topics;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.topics.GetTopicsResponse;
import android.adservices.topics.Topic;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.platform.test.scenario.annotation.Scenario;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Crystalball test for Topics API to test epoch computation using the Precomputed classifier. */
@Scenario
@RunWith(JUnit4.class)
public class TopicsEpochComputationPrecomputedClassifier {
    private static final String TAG = "TopicsEpochComputation";

    // Metric name for Crystalball test
    private static final String EPOCH_COMPUTATION_DURATION = "EPOCH_COMPUTATION_DURATION";

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

    private static final DateTimeFormatter LOG_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private static final String EPOCH_COMPUTATION_START_LOG = "Start of Epoch Computation";

    private static final String EPOCH_COMPUTATION_END_LOG = "End of Epoch Computation";

    private static final String EPOCH_START_TIMESTAMP_KEY = "start";

    private static final String EPOCH_STOP_TIMESTAMP_KEY = "end";

    @Before
    public void setup() throws Exception {
        // We need to skip 3 epochs so that if there is any usage from other test runs, it will
        // not be used for epoch retrieval.
        Thread.sleep(3 * TEST_EPOCH_JOB_PERIOD_MS);

        overridingBeforeTest();
    }

    @After
    public void teardown() {
        overridingAfterTest();
    }

    @Test
    public void testEpochComputation() throws Exception {
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

    private void overridingBeforeTest() {
        disableGlobalKillSwitch();
        disableTopicsKillSwitch();
        overridingAdservicesLoggingLevel("VERBOSE");

        overrideDisableTopicsEnrollmentCheck("1");
        overrideEpochPeriod(TEST_EPOCH_JOB_PERIOD_MS);

        // We need to turn off random topic so that we can verify the returned topic.
        overridePercentageForRandomTopic(TEST_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC);

        // We need to turn the Consent Manager into debug mode
        overrideConsentManagerDebugMode();

        // Turn off MDD to avoid model mismatching
        disableMddBackgroundTasks(true);

        // Use bundled files for classifier
        overrideClassifierForceUseBundledFiles(true);
    }

    private void overridingAfterTest() {
        overrideDisableTopicsEnrollmentCheck("0");
        overrideEpochPeriod(TOPICS_EPOCH_JOB_PERIOD_MS);
        overridePercentageForRandomTopic(TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC);
        disableMddBackgroundTasks(false);
        overrideClassifierForceUseBundledFiles(false);
        overridingAdservicesLoggingLevel("INFO");
    }

    // Switch on/off for MDD service. Default value is false, which means MDD is enabled.
    private void disableMddBackgroundTasks(boolean isSwitchedOff) {
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
    private void forceEpochComputationJob() throws Exception {
        Instant startTime = Clock.systemUTC().instant();
        ShellUtils.runShellCommand(
                "cmd jobscheduler run -f" + " " + ADSERVICES_PACKAGE_NAME + " " + EPOCH_JOB_ID);
        long epoch_computation_duration =
                processLogCatStreamToGetMetricMap(getMetricsEvents(startTime));
        Log.i(TAG, "(" + EPOCH_COMPUTATION_DURATION + ": " + epoch_computation_duration + ")");
    }

    /** Return AdServices(EpochManager) logs that will be used to build the test metrics. */
    public InputStream getMetricsEvents(Instant startTime) throws IOException {
        ProcessBuilder pb =
                new ProcessBuilder(
                        Arrays.asList(
                                "logcat",
                                "-s",
                                "adservices:V",
                                "-t",
                                LOG_TIME_FORMATTER.format(startTime),
                                "|",
                                "grep",
                                "Epoch"));
        return pb.start().getInputStream();
    }

    /**
     * Filters the start and end log for the epoch computation and based on that calculates the
     * duration of epoch computation. If we fail to parse the start or end log for epoch
     * computation, we catch ParseException and in the end throw an exception.
     *
     * @param inputStream the logcat stream which contains start and end time info for the epoch
     *     computation
     * @return the value of epoch computation latency
     * @throws Exception if the test failed to get the time point for epoch computation's start and
     *     end.
     */
    private Long processLogCatStreamToGetMetricMap(InputStream inputStream) throws Exception {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        Map<String, Long> output = new HashMap<String, Long>();
        bufferedReader
                .lines()
                .filter(
                        line ->
                                line.contains(EPOCH_COMPUTATION_START_LOG)
                                        || line.contains(EPOCH_COMPUTATION_END_LOG))
                .forEach(
                        line -> {
                            if (line.contains(EPOCH_COMPUTATION_START_LOG)) {
                                try {
                                    output.put(
                                            EPOCH_START_TIMESTAMP_KEY, getTimestampFromLog(line));
                                } catch (ParseException e) {
                                    Log.e(
                                            TAG,
                                            String.format(
                                                    "Caught ParseException when fetching start"
                                                            + " time for epoch computation: %s",
                                                    e.toString()));
                                }
                            } else {
                                try {
                                    output.put(EPOCH_STOP_TIMESTAMP_KEY, getTimestampFromLog(line));
                                } catch (ParseException e) {
                                    Log.e(
                                            TAG,
                                            String.format(
                                                    "Caught ParseException when fetching end time"
                                                            + " for epoch computation: %s",
                                                    e.toString()));
                                }
                            }
                        });

        if (output.containsKey(EPOCH_START_TIMESTAMP_KEY)
                && output.containsKey(EPOCH_STOP_TIMESTAMP_KEY)) {
            return output.get(EPOCH_STOP_TIMESTAMP_KEY) - output.get(EPOCH_START_TIMESTAMP_KEY);
        }
        throw new Exception("Cannot get the time of Epoch Computation's start and end");
    }

    /**
     * Parses the timestamp from the log. Example log: 10-06 17:58:20.173 14950 14966 D adservices:
     * Start of Epoch Computation
     */
    private static Long getTimestampFromLog(String log) throws ParseException {
        String[] words = log.split(" ");
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd hh:mm:ss.SSS");
        Date parsedDate = dateFormat.parse(words[0] + " " + words[1]);
        return parsedDate.getTime();
    }

    private void overridingAdservicesLoggingLevel(String loggingLevel) {
        ShellUtils.runShellCommand("setprop log.tag.adservices %s", loggingLevel);
    }

    // Override global_kill_switch to ignore the effect of actual PH values.
    private void disableGlobalKillSwitch() {
        ShellUtils.runShellCommand("device_config put adservices global_kill_switch false");
    }

    // Override topics_kill_switch to ignore the effect of actual PH values.
    private void disableTopicsKillSwitch() {
        ShellUtils.runShellCommand("device_config put adservices topics_kill_switch false");
    }

    // Override to force use bundled files.
    private void overrideClassifierForceUseBundledFiles(boolean enable) {
        ShellUtils.runShellCommand(
                "device_config put adservices classifier_force_use_bundled_files " + enable);
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
