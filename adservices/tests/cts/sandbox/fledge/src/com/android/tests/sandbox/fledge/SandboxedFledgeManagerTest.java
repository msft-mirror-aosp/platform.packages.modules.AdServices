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

package com.android.tests.sandbox.fledge;

import static com.google.common.truth.Truth.assertWithMessage;

import android.Manifest;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.os.Bundle;
import android.os.Process;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.service.PhFlagsFixture;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

public class SandboxedFledgeManagerTest {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final String SDK_NAME = "com.android.tests.providers.sdkfledge";

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    private DevContext mDevContext;

    private boolean mHasAccessToDevOverrides;

    private String mAccessStatus;

    @Before
    public void setup() throws TimeoutException {
        DevContextFilter devContextFilter = DevContextFilter.create(sContext);
        mDevContext = DevContextFilter.create(sContext).createDevContext(Process.myUid());
        boolean isDebuggable =
                devContextFilter.isDebuggable(mDevContext.getCallingAppPackageName());
        boolean isDeveloperMode = devContextFilter.isDeveloperMode();
        mHasAccessToDevOverrides = mDevContext.getDevOptionsEnabled();
        mAccessStatus =
                String.format("Debuggable: %b\n", isDebuggable)
                        + String.format("Developer options on: %b", isDeveloperMode);

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.WRITE_DEVICE_CONFIG);

        // Enable CTS to be run with versions of WebView < M105
        PhFlagsFixture.overrideEnforceIsolateMaxHeapSize(false);
        PhFlagsFixture.overrideIsolateMaxHeapSizeBytes(0);

        PhFlagsFixture.overrideSdkRequestPermitsPerSecond(Integer.MAX_VALUE);
        makeTestProcessForeground();
        PhFlagsFixture.overrideFledgeEnrollmentCheck(true);
        overrideConsentManagerDebugMode(true);
    }

    /**
     * Starts a foreground activity to make the test process a foreground one to pass PPAPI and SDK
     * Sandbox checks
     */
    private void makeTestProcessForeground() throws TimeoutException {
        SimpleActivity.startAndWaitForSimpleActivity(sContext, Duration.ofMillis(500));
    }

    @After
    public void shutDown() {
        SimpleActivity.stopSimpleActivity(sContext);
        overrideConsentManagerDebugMode(false);
    }

    @Test
    public void loadSdkAndRunFledgeFlow() {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        final SdkSandboxManager sdkSandboxManager =
                sContext.getSystemService(SdkSandboxManager.class);

        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        sdkSandboxManager.loadSdk(SDK_NAME, new Bundle(), CALLBACK_EXECUTOR, callback);

        assertWithMessage(
                        callback.isLoadSdkSuccessful()
                                ? "Callback was successful"
                                : "Callback failed with message " + callback.getLoadSdkErrorMsg())
                .that(callback.isLoadSdkSuccessful())
                .isTrue();
    }

    private void overrideConsentManagerDebugMode(boolean enable) {
        ShellUtils.runShellCommand(
                "setprop debug.adservices.consent_manager_debug_mode %s", enable);
    }
}
