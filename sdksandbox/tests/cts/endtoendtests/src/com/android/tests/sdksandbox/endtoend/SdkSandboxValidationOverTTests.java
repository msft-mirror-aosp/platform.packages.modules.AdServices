/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tests.sdksandbox.endtoend;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SdkSandboxValidationOverTTests extends SandboxKillerBeforeTest {
    @Rule
    public final ActivityScenarioRule<TestActivity> mRule =
            new ActivityScenarioRule<>(TestActivity.class);

    @Test
    public void testSdkSandboxDisabledOnT() throws Exception {
        // Sandbox is enabled only in U+. This is a validation test to not allow running tests on T.
        assumeTrue(VERSION.SDK_INT == VERSION_CODES.TIRAMISU);

        String sdkName = "com.android.ctssdkprovider";
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        SdkSandboxManager sdkSandboxManager = context.getSystemService(SdkSandboxManager.class);
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        sdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsUnsuccessful();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_SDK_SANDBOX_DISABLED);
    }
}
