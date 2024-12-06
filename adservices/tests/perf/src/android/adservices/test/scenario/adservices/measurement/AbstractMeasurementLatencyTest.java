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

package android.adservices.test.scenario.adservices.measurement;

import android.Manifest;
import android.adservices.measurement.MeasurementManager;
import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.OutcomeReceiver;
import android.platform.test.rule.CleanPackageRule;
import android.platform.test.rule.DropCachesRule;
import android.platform.test.rule.KillAppsRule;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.service.DebugFlagsConstants;
import com.android.adservices.service.FlagsConstants;

import com.google.common.base.Stopwatch;
import com.google.common.truth.Truth;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.RuleChain;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AbstractMeasurementLatencyTest {
    protected static final String TAG = "Measurement";
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final String SERVER_BASE_URI = "https://rb-measurement.com";
    private static final String SOURCE_PATH = "/source";
    private static final long API_TIMEOUT_SECONDS = 5;

    protected static final MeasurementManager MEASUREMENT_MANAGER =
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    ? CONTEXT.getSystemService(MeasurementManager.class)
                    : MeasurementManager.get(CONTEXT);

    @Rule(order = 0)
    public RuleChain rules =
            RuleChain.outerRule(
                            new CleanPackageRule(
                                    AdservicesTestHelper.getAdServicesPackageName(
                                            ApplicationProvider.getApplicationContext()),
                                    /* clearOnStarting */ true,
                                    /* clearOnFinished */ false))
                    .around(
                            new KillAppsRule(
                                    AdservicesTestHelper.getAdServicesPackageName(
                                            ApplicationProvider.getApplicationContext())))
                    .around(new DropCachesRule());

    @Rule(order = 1)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests().setCompatModeFlags();

    @BeforeClass
    public static void setup() throws Exception {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.WRITE_DEVICE_CONFIG,
                        Manifest.permission.WRITE_ALLOWLISTED_DEVICE_CONFIG);
    }

    protected void runRegisterSource(String testClassName, String testName) throws Exception {
        final String path = SERVER_BASE_URI + SOURCE_PATH;

        Stopwatch timer = Stopwatch.createStarted();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        MEASUREMENT_MANAGER.registerSource(
                Uri.parse(path),
                /* inputEvent */ null,
                CALLBACK_EXECUTOR,
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Object ignoredResult) {
                        timer.stop();
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        timer.stop();
                        Assert.fail();
                        countDownLatch.countDown();
                    }
                });

        Truth.assertThat(countDownLatch.await(API_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        Log.i(TAG, generateLogLabel(testClassName, testName, timer.elapsed(TimeUnit.MILLISECONDS)));
    }

    protected void runGetMeasurementApiStatus(String testClassName, String testName)
            throws InterruptedException {
        Stopwatch timer = Stopwatch.createStarted();
        CountDownLatch countDownLatch = new CountDownLatch(1);

        MEASUREMENT_MANAGER.getMeasurementApiStatus(
                CALLBACK_EXECUTOR,
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Integer ignoredResult) {
                        timer.stop();
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        timer.stop();
                        Assert.fail();
                        countDownLatch.countDown();
                    }
                });

        Truth.assertThat(countDownLatch.await(API_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        Log.i(TAG, generateLogLabel(testClassName, testName, timer.elapsed(TimeUnit.MILLISECONDS)));
    }

    private String generateLogLabel(String className, String testName, long elapsedMs) {
        return "("
                + "MEASUREMENT_LATENCY_"
                + className
                + "#"
                + testName
                + ": "
                + elapsedMs
                + " ms)";
    }

    protected void warmupAdServices() {
        final String path = SERVER_BASE_URI + SOURCE_PATH;

        MEASUREMENT_MANAGER.registerSource(
                Uri.parse(path),
                /* inputEvent */ null,
                CALLBACK_EXECUTOR,
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Object ignoredResult) {}

                    @Override
                    public void onError(@NonNull Exception error) {
                        Assert.fail();
                    }
                });
    }

    protected void setFlagsForMeasurement() throws Exception {
        // Override consent manager behavior to give user consent.
        flags.setDebugFlag(DebugFlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE, true);

        // Override adid kill switch.
        flags.setFlag(FlagsConstants.KEY_ADID_KILL_SWITCH, false);

        // Override the flag to allow current package to call APIs.
        flags.setPpapiAppAllowList(FlagsConstants.ALLOWLIST_ALL);

        // Override the flag to allow current package to call delete API.
        flags.setMsmtWebContextClientAllowList(FlagsConstants.ALLOWLIST_ALL);

        // Override the flag for the global kill switch.
        flags.setFlag(FlagsConstants.KEY_GLOBAL_KILL_SWITCH, false);

        // Override measurement kill switch.
        flags.setFlag(FlagsConstants.KEY_MEASUREMENT_KILL_SWITCH, false);

        // Override measurement registration job kill switch.
        flags.setFlag(FlagsConstants.KEY_MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH, false);

        // Disable enrollment checks.
        flags.setFlag(FlagsConstants.KEY_DISABLE_MEASUREMENT_ENROLLMENT_CHECK, true);

        // Disable foreground checks.
        flags.setFlag(
                FlagsConstants.KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCE, true);

        flags.setFlag(
                FlagsConstants.KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_TRIGGER, true);

        // Set flag to pre seed enrollment.
        flags.setFlag(FlagsConstants.KEY_ENABLE_ENROLLMENT_TEST_SEED, true);
    }
}
