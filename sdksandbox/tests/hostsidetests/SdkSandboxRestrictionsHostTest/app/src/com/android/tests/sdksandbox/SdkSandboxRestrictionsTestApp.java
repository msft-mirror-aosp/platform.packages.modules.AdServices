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

package com.android.tests.sdksandbox;

import static android.app.sdksandbox.SdkSandboxManager.EXTRA_DISPLAY_ID;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_HEIGHT_IN_PIXELS;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_HOST_TOKEN;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_WIDTH_IN_PIXELS;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.FakeRequestSurfacePackageCallback;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.provider.DeviceConfig;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@RunWith(JUnit4.class)
public class SdkSandboxRestrictionsTestApp {
    private SdkSandboxManager mSdkSandboxManager;
    private static final String BUNDLE_KEY_PHASE_NAME = "phase-name";
    private static final String ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS =
            "enforce_broadcast_receiver_restrictions";

    private static final String SDK_PACKAGE = "com.android.tests.sdkprovider.restrictionstest";

    @Rule
    public final ActivityScenarioRule mRule =
            new ActivityScenarioRule<>(SdkSandboxEmptyActivity.class);

    @Before
    public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        mSdkSandboxManager = context.getSystemService(
                SdkSandboxManager.class);
        assertThat(mSdkSandboxManager).isNotNull();
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.WRITE_DEVICE_CONFIG);
    }

    // Run a phase of the test inside the SDK loaded for this app
    private void runPhaseInsideSdk(String phaseName, FakeRequestSurfacePackageCallback callback) {
        Bundle params = new Bundle();
        params.putString(BUNDLE_KEY_PHASE_NAME, phaseName);
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 500);
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putInt(EXTRA_DISPLAY_ID, 0);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());

        mSdkSandboxManager.requestSurfacePackage(SDK_PACKAGE, params, Runnable::run, callback);
    }

    /**
     * Tests that the correct restrictions are applied when the SDK sandbox process tries to
     * register a broadcast receiver. This behavior depends on the value of a {@link DeviceConfig}
     * property.
     */
    @Test
    public void testSdkSandboxBroadcastRestrictions() throws Exception {
        mRule.getScenario();

        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_SDK_SANDBOX,
                ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS, "true", false);
        // Allow time for DeviceConfig change to propagate.
        Thread.sleep(1000);
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();

        FakeRequestSurfacePackageCallback surfacePackageCallback1 =
                new FakeRequestSurfacePackageCallback();
        runPhaseInsideSdk("testSdkSandboxBroadcastRestrictions", surfacePackageCallback1);
        assertThat(surfacePackageCallback1.isRequestSurfacePackageSuccessful()).isFalse();

        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_SDK_SANDBOX,
                ENFORCE_BROADCAST_RECEIVER_RESTRICTIONS, "false", false);
        // Allow time for DeviceConfig change to propagate.
        Thread.sleep(1000);
        FakeRequestSurfacePackageCallback surfacePackageCallback2 =
                new FakeRequestSurfacePackageCallback();
        runPhaseInsideSdk("testSdkSandboxBroadcastRestrictions", surfacePackageCallback2);
        assertThat(surfacePackageCallback2.isRequestSurfacePackageSuccessful()).isTrue();
    }
}
