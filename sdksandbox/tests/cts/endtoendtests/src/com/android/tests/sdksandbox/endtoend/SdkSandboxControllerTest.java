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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;

import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.sdkprovider.SdkSandboxController;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.SdkSandboxDeviceSupportedRule;
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

/** End-to-end tests of {@link SdkSandboxController} APIs. */
@RunWith(JUnit4.class)
public class SdkSandboxControllerTest {

    @Rule(order = 0)
    public final SdkSandboxDeviceSupportedRule supportedRule = new SdkSandboxDeviceSupportedRule();

    @Rule(order = 1)
    public final ActivityScenarioRule<TestActivity> activityScenarioRule =
            new ActivityScenarioRule<>(TestActivity.class);

    private static final String SDK_NAME = "com.android.ctssdkprovider";

    private SdkSandboxManager mSdkSandboxManager;
    private ICtsSdkProviderApi mSdk;
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    @Before
    public void setup() {
        mSdkSandboxManager = mContext.getSystemService(SdkSandboxManager.class);
    }

    @After
    public void tearDown() {
        try {
            mSdkSandboxManager.unloadSdk(SDK_NAME);
        } catch (Exception ignored) {
        }
    }

    @Test
    public void testGetClientPackageName() throws Exception {
        loadSdk();
        assertThat(mSdk.getClientPackageName()).isEqualTo(mContext.getPackageName());
    }

    private void loadSdk() {
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        assertNotNull(callback.getSandboxedSdk());
        assertNotNull(callback.getSandboxedSdk().getInterface());
        mSdk = ICtsSdkProviderApi.Stub.asInterface(callback.getSandboxedSdk().getInterface());
    }
}
