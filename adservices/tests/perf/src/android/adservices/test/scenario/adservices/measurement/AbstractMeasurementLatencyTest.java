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
import android.adservices.clients.measurement.MeasurementClient;
import android.adservices.measurement.MeasurementManager;
import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.OutcomeReceiver;
import android.platform.test.rule.CleanPackageRule;
import android.platform.test.rule.DropCachesRule;
import android.platform.test.rule.KillAppsRule;
import android.provider.DeviceConfig;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.CompatAdServicesTestUtils;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.base.Stopwatch;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.RuleChain;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AbstractMeasurementLatencyTest {
    protected static final String TAG = "Measurement";
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final String SERVER_BASE_URI = "https://rb-measurement.com";
    private static final String SOURCE_PATH = "/source";
    protected static final MeasurementClient MEASUREMENT_CLIENT =
            new MeasurementClient.Builder()
                    .setContext(CONTEXT)
                    .setExecutor(CALLBACK_EXECUTOR)
                    .build();

    protected static final MeasurementManager MEASUREMENT_MANAGER =
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    ? CONTEXT.getSystemService(MeasurementManager.class)
                    : MeasurementManager.get(CONTEXT);
    private static UiDevice sDevice;

    @Rule
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

    @BeforeClass
    public static void setup() throws Exception {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.WRITE_DEVICE_CONFIG);
    }

    protected void runRegisterSource(String testClassName, String testName) {
        final String path = SERVER_BASE_URI + SOURCE_PATH;

        Stopwatch timer = Stopwatch.createStarted();

        MEASUREMENT_MANAGER.registerSource(
                Uri.parse(path),
                /* inputEvent */ null,
                CALLBACK_EXECUTOR,
                new OutcomeReceiver<Object, Exception>() {
                    @Override
                    public void onResult(@NonNull Object ignoredResult) {
                        timer.stop();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        timer.stop();
                        Assert.fail();
                    }
                });

        Log.i(TAG, generateLogLabel(testClassName, testName, timer.elapsed(TimeUnit.MILLISECONDS)));
    }

    protected void runGetMeasurementApiStatus(String testClassName, String testName) {
        Stopwatch timer = Stopwatch.createStarted();

        MEASUREMENT_MANAGER.getMeasurementApiStatus(
                CALLBACK_EXECUTOR,
                new OutcomeReceiver<Integer, Exception>() {
                    @Override
                    public void onResult(@NonNull Integer ignoredResult) {
                        timer.stop();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        timer.stop();
                        Assert.fail();
                    }
                });

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

    protected void warmupAdServices() throws Exception {
        final String path = SERVER_BASE_URI + SOURCE_PATH;
        MEASUREMENT_MANAGER.registerSource(
                Uri.parse(path),
                /* inputEvent */ null,
                CALLBACK_EXECUTOR,
                new OutcomeReceiver<Object, Exception>() {
                    @Override
                    public void onResult(@NonNull Object ignoredResult) {}

                    @Override
                    public void onError(@NonNull Exception error) {
                        Assert.fail();
                    }
                });
    }

    private static UiDevice getUiDevice() {
        if (sDevice == null) {
            sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        }
        return sDevice;
    }

    protected static void setFlagsForMeasurement() throws Exception {
        // Override consent manager behavior to give user consent.
        getUiDevice()
                .executeShellCommand("setprop debug.adservices.consent_manager_debug_mode true");

        // Override the flag to allow current package to call APIs.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "ppapi_app_allow_list",
                "*",
                /* makeDefault */ false);

        // Override the flag to allow current package to call delete API.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "web_context_client_allow_list",
                "*",
                /* makeDefault */ false);

        // Override global kill switch.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "global_kill_switch",
                Boolean.toString(false),
                /* makeDefault */ false);

        // Override measurement kill switch.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "measurement_kill_switch",
                Boolean.toString(false),
                /* makeDefault */ false);

        // Override measurement registration job kill switch.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "measurement_job_registration_job_queue_kill_switch",
                Boolean.toString(false),
                /* makeDefault */ false);

        // Disable enrollment checks.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "disable_measurement_enrollment_check",
                Boolean.toString(true),
                /* makeDefault */ false);

        // Disable foreground checks.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "measurement_enforce_foreground_status_register_source",
                Boolean.toString(true),
                /* makeDefault */ false);

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "measurement_enforce_foreground_status_register_trigger",
                Boolean.toString(true),
                /* makeDefault */ false);

        // Set flag to pre seed enrollment.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "enable_enrollment_test_seed",
                Boolean.toString(true),
                /* makeDefault */ false);

        // Set flag not match origin.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "measurement_enforce_enrollment_origin_match",
                Boolean.toString(false),
                /* makeDefault */ false);

        // Set flags for back-compat AdServices functionality for Android S-.
        if (!SdkLevel.isAtLeastT()) {
            CompatAdServicesTestUtils.setFlags();
        }
    }
}
