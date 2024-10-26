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

package com.android.tests.sandbox.adid;

import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.SdkSandboxDeviceSupportedRule;
import android.os.Bundle;

import com.android.adservices.common.AdServicesCtsTestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/*
 * Test AdId API running within the Sandbox.
 */
public final class SandboxedAdIdManagerTest extends AdServicesCtsTestCase
        implements CtsSandboxedAdIdTestFlags {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final String SDK_NAME = "com.android.tests.providers.adidsdk";

    private static final int LOAD_SDK_FROM_INTERNET_TIMEOUT_SEC = 60;
    private static final int FOREGROUND_ACTIVITY_BROADCAST_WAITING_TIMEOUT_MS = 10_000;

    @Rule(order = 0)
    public final SdkSandboxDeviceSupportedRule supportedRule = new SdkSandboxDeviceSupportedRule();

    @Before
    public void setup() throws TimeoutException, InterruptedException {
        // The setup for this test:
        // SandboxedAdIdManagerTest is the test app. It will load the adidsdk into the Sandbox.
        // The adidsdk (running within the Sandbox) will query AdId API and verify that the correct
        // adid are returned.
        // After adidsdk verifies the result, it will communicate back to the
        // SandboxedAdIdManagerTest via the loadSdk's callback.
        // In this test, we use the loadSdk's callback as a 2-way communications between the Test
        // app (this class) and the Sdk running within the Sandbox process.

        // Start a foreground activity
        SimpleActivity.startAndWaitForSimpleActivity(
                mContext, Duration.ofMillis(FOREGROUND_ACTIVITY_BROADCAST_WAITING_TIMEOUT_MS));
    }

    @After
    public void shutDown() {
        SimpleActivity.stopSimpleActivity(mContext);
    }

    @Test
    public void loadSdkAndRunAdIdApi() {
        final SdkSandboxManager sdkSandboxManager =
                sContext.getSystemService(SdkSandboxManager.class);

        final FakeLoadSdkCallback callback =
                new FakeLoadSdkCallback(LOAD_SDK_FROM_INTERNET_TIMEOUT_SEC);

        sdkSandboxManager.loadSdk(SDK_NAME, new Bundle(), CALLBACK_EXECUTOR, callback);

        // This verifies that the adidsdk in the Sandbox gets back the correct adid.
        // If the adidsdk did not get correct adid, it will trigger the callback.onLoadSdkError.
        callback.assertLoadSdkIsSuccessful("Load SDK from internet");
    }
}
