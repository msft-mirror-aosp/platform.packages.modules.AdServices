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

package com.android.tests.sandbox.measurement;

import static com.google.common.truth.Truth.assertThat;

import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/*
 * Test Measurement APIs running within the Sandbox.
 */
@RunWith(JUnit4.class)
public class SandboxedMeasurementManagerTest {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final String SDK_NAME = "com.android.tests.providers.sdkmeasurement";

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    @Before
    public void setup() throws TimeoutException {
        // Start a foreground activity
        SimpleActivity.startAndWaitForSimpleActivity(sContext, Duration.ofMillis(1000));
    }

    @After
    public void shutDown() {
        SimpleActivity.stopSimpleActivity(sContext);
    }

    @Test
    public void loadSdkAndRunMeasurementApi() {
        // The setup for this test:
        // SandboxedMeasurementManagerTest is the test app.
        // It will load the SdkMeasurement into the Sandbox.
        // The SdkMeasurement (running within the Sandbox) will call all Measurement APIs and verify
        // no errors are thrown.
        // After SdkMeasurement finishes, it will communicate back to the
        // SandboxedMeasurementManagerTest via the loadSdk's callback.
        // In this test, we use the loadSdk's callback as a 2-way communications between the Test
        // app (this class) and the Sdk running within the Sandbox process.

        // We need to turn the Consent Manager into debug mode to simulate grant Consent
        overrideConsentManagerDebugMode();

        // Allow sandbox package name to be able to execute Measurement APIs
        allowSandboxPackageNameAccessMeasurementApis();

        final SdkSandboxManager sdkSandboxManager =
                sContext.getSystemService(SdkSandboxManager.class);

        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        // Load SdkMeasurement
        sdkSandboxManager.loadSdk(SDK_NAME, new Bundle(), CALLBACK_EXECUTOR, callback);

        // This verifies SdkMeasurement finished without errors.
        // callback.isLoadSdkSuccessful returns true if there were no errors.
        assertThat(callback.isLoadSdkSuccessful()).isTrue();

        // Reset back the original values.
        resetAllowSandboxPackageNameAccessMeasurementApis();
        resetOverrideConsentManagerDebugMode();
    }

    private void allowSandboxPackageNameAccessMeasurementApis() {
        final String sdkSbxName = "com.google.android.sdksandbox";
        ShellUtils.runShellCommand(
                "device_config put adservices ppapi_app_allow_list " + sdkSbxName);
        ShellUtils.runShellCommand(
                "device_config put adservices web_context_client_allow_list " + sdkSbxName);
    }

    private void overrideConsentManagerDebugMode() {
        ShellUtils.runShellCommand("setprop debug.adservices.consent_manager_debug_mode true");
    }

    private void resetAllowSandboxPackageNameAccessMeasurementApis() {
        ShellUtils.runShellCommand("device_config put adservices ppapi_app_allow_list null");
        ShellUtils.runShellCommand(
                "device_config put adservices web_context_client_allow_list null");
    }

    private void resetOverrideConsentManagerDebugMode() {
        ShellUtils.runShellCommand("setprop debug.adservices.consent_manager_debug_mode null");
    }
}
