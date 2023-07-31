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

package com.android.tests.sdksandbox.endtoend;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.DeviceSupportUtils;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.os.Bundle;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.ctssdkprovider.ICtsSdkProviderApi;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SdkSandboxStorageTest {

    private static final String SDK_NAME_1 = "com.android.ctssdkprovider";

    @Rule public final ActivityScenarioRule mRule = new ActivityScenarioRule<>(TestActivity.class);

    private SdkSandboxManager mSdkSandboxManager;

    @Before
    public void setup() {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        assumeTrue(DeviceSupportUtils.isSdkSandboxSupported(context));
        mSdkSandboxManager = context.getSystemService(SdkSandboxManager.class);
        mRule.getScenario();
    }

    @After
    public void teardown() {
        try {
            mSdkSandboxManager.unloadSdk(SDK_NAME_1);
        } catch (Exception ignored) {
        }
    }

    // Verify that the SDK is able to use the Room library for storage.
    @Test
    public void testSdkSandboxRoomDatabaseAccess() throws Exception {
        loadSdk().checkRoomDatabaseAccess();
    }

    @Test
    public void testSdkSandboxCanUseSharedPreferences() throws Exception {
        loadSdk().checkCanUseSharedPreferences();
    }

    // Helper method to load SDK_NAME_1
    private ICtsSdkProviderApi loadSdk() {
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME_1, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();

        final SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();
        assertNotNull(sandboxedSdk);
        return ICtsSdkProviderApi.Stub.asInterface(callback.getSandboxedSdk().getInterface());
    }
}
