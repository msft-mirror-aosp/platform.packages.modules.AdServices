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

package com.android.tests.sdksandbox.disablede2e;

import static androidx.lifecycle.Lifecycle.State;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.sdksandbox.AppOwnedSdkSandboxInterface;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.SystemProperties;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SdkSandboxManagerDisabledTest {

    @Rule(order = 0)
    public final ActivityScenarioRule<TestActivity> mRule =
            new ActivityScenarioRule<>(TestActivity.class);

    private ActivityScenario<TestActivity> mScenario;

    private SdkSandboxManager mSdkSandboxManager;

    @Before
    public void setup() throws Exception {
        // Sandbox is enabled on emulators irrespective of the killswitch
        Assume.assumeFalse(isEmulator());
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mSdkSandboxManager = context.getSystemService(SdkSandboxManager.class);
        mScenario = mRule.getScenario();
    }

    @Test
    public void testLoadSdk_sdkSandboxDisabledErrorCode() throws Exception {
        String sdkName = "com.android.ctssdkprovider";
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsUnsuccessful();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_SDK_SANDBOX_DISABLED);
        assertThat(mSdkSandboxManager.getSandboxedSdks()).isEmpty();
    }

    @Test
    public void testRegisterAndUnregisterAppOwnedSdkSandboxInterface() throws Exception {
        assertThat(mSdkSandboxManager.getAppOwnedSdkSandboxInterfaces()).isEmpty();
        String sdkName = "com.android.ctssdkprovider";
        mSdkSandboxManager.registerAppOwnedSdkSandboxInterface(
                new AppOwnedSdkSandboxInterface(sdkName, 0, new Binder()));
        assertThat(mSdkSandboxManager.getAppOwnedSdkSandboxInterfaces()).isNotEmpty();
        mSdkSandboxManager.unregisterAppOwnedSdkSandboxInterface(sdkName);
        assertThat(mSdkSandboxManager.getAppOwnedSdkSandboxInterfaces()).isEmpty();
    }

    @Test
    public void testStartSdkSandboxActivity_noSandboxRunningError() throws Exception {
        assertThat(mScenario.getState()).isEqualTo(State.RESUMED);
        mScenario.onActivity(
                clientActivity -> {
                    // Throws SecurityException/UnsupportedOperationException.
                    assertThrows(
                            Exception.class,
                            () ->
                                    mSdkSandboxManager.startSdkSandboxActivity(
                                            clientActivity, new Binder()));
                });
    }

    private static boolean isEmulator() {
        return SystemProperties.getBoolean("ro.boot.qemu", false);
    }
}
