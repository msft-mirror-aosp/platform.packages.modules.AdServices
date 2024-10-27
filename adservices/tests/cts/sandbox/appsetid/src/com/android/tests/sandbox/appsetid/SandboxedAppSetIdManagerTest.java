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

package com.android.tests.sandbox.appsetid;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.SdkSandboxDeviceSupportedRule;
import android.os.Bundle;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.shared.testing.network.NetworkConnectionHelper;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/*
 * Test AppSetId API running within the Sandbox.
 */
public final class SandboxedAppSetIdManagerTest extends AdServicesCtsTestCase
        implements CtsSandboxedAppSetIdITestFlags {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final String SDK_NAME = "com.android.tests.providers.appsetidsdk";

    private static final int LOAD_SDK_FROM_INTERNET_TIMEOUT_SEC = 60;

    @Rule(order = 0)
    public final SdkSandboxDeviceSupportedRule supportedRule = new SdkSandboxDeviceSupportedRule();

    @Before
    public void setup() throws TimeoutException, InterruptedException {
        // The setup for this test:
        // SandboxedAppSetIdManagerTest is the test app. It will load the appsetidsdk into the
        // Sandbox.
        // The appsetidsdk (running within the Sandbox) will query AppSetId API and verify that the
        // correct
        // appsetid are returned.
        // After appsetidsdk verifies the result, it will communicate back to the
        // SandboxedAppSetIdManagerTest via the loadSdk's callback.
        // In this test, we use the loadSdk's callback as a 2-way communications between the Test
        // app (this class) and the Sdk running within the Sandbox process.

        // Start a foreground activity
        SimpleActivity.startAndWaitForSimpleActivity(mContext, Duration.ofMillis(1000));
    }

    @After
    public void shutDown() {
        SimpleActivity.stopSimpleActivity(mContext);
    }

    @Test
    public void loadSdkAndRunAppSetIdApi() {
        Assume.assumeTrue(NetworkConnectionHelper.isInternetConnected(mContext));
        Assume.assumeTrue(NetworkConnectionHelper.isInternetAvailable());

        SdkSandboxManager sdkSandboxManager = mContext.getSystemService(SdkSandboxManager.class);
        assertWithMessage("SdkSandboxManager").that(sdkSandboxManager).isNotNull();

        FakeLoadSdkCallback callback = new FakeLoadSdkCallback(LOAD_SDK_FROM_INTERNET_TIMEOUT_SEC);

        sdkSandboxManager.loadSdk(SDK_NAME, new Bundle(), CALLBACK_EXECUTOR, callback);

        // This verifies that the appsetidsdk in the Sandbox gets back the correct appsetid.
        // If the appsetidsdk did not get correct appsetid, it will trigger the
        // callback.onLoadSdkError.
        callback.assertLoadSdkIsSuccessful("Load SDK from internet");
    }
}
