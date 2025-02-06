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

import static com.android.adservices.service.FlagsConstants.KEY_ADID_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_MEASUREMENT_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_STATUS_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_KILL_SWITCH;

import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.SdkSandboxDeviceSupportedRule;
import android.os.Bundle;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/*
 * Test Measurement APIs running within the Sandbox.
 */
@SetFlagDisabled(KEY_ADID_KILL_SWITCH)
@SetFlagDisabled(KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH)
@SetFlagDisabled(KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH)
@SetFlagDisabled(KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH)
@SetFlagDisabled(KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH)
@SetFlagDisabled(KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH)
@SetFlagDisabled(KEY_MEASUREMENT_API_STATUS_KILL_SWITCH)
@SetFlagDisabled(KEY_MEASUREMENT_KILL_SWITCH)
@SetFlagEnabled(KEY_DISABLE_MEASUREMENT_ENROLLMENT_CHECK)
public final class SandboxedMeasurementManagerTest extends CtsSandboxedMeasurementManagerTestCase {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final String SDK_NAME = "com.android.tests.providers.sdkmeasurement";

    @Rule(order = 0)
    public final SdkSandboxDeviceSupportedRule supportedRule = new SdkSandboxDeviceSupportedRule();

    @Before
    public void setup() throws TimeoutException {
        // Kill adservices process to avoid interfering from other tests.
        AdservicesTestHelper.killAdservicesProcess(mContext);
        // Start a foreground activity
        SimpleActivity.startAndWaitForSimpleActivity(mContext, Duration.ofMillis(10_000));
    }

    @After
    public void shutDown() {
        SimpleActivity.stopSimpleActivity(mContext);
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

        SdkSandboxManager sdkSandboxManager = mContext.getSystemService(SdkSandboxManager.class);

        // The enrolled URLs should time out when registering to them, because we don't control
        // them; each timeout is 5 seconds, plus some wiggle room
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback(25);

        // Load SdkMeasurement
        sdkSandboxManager.loadSdk(SDK_NAME, new Bundle(), CALLBACK_EXECUTOR, callback);

        // This verifies SdkMeasurement finished without errors.
        callback.assertLoadSdkIsSuccessful();
    }
}