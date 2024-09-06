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

package com.android.sdksandbox.cts.app;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;

import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.SdkLifecycleHelper;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import com.android.ctssdkprovider.ICtsSdkProviderApi;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CtsSdkSandboxMediaTestApp {

    private static final String SDK_NAME = "com.android.ctssdkprovider";

    @Rule
    public final ActivityScenarioRule<TestActivity> activityScenarioRule =
            new ActivityScenarioRule<>(TestActivity.class);

    private Context mContext;
    private SdkLifecycleHelper mSdkLifecycleHelper;
    private SdkSandboxManager mSdkSandboxManager;

    @Before
    public void setup() {
        mContext = ApplicationProvider.getApplicationContext();
        mSdkLifecycleHelper = new SdkLifecycleHelper(mContext);
        mSdkSandboxManager = mContext.getSystemService(SdkSandboxManager.class);
        assertThat(mSdkSandboxManager).isNotNull();

        activityScenarioRule.getScenario();
        mSdkLifecycleHelper.unloadSdk(SDK_NAME);
    }

    @After
    public void tearDown() {
        // unload SDK to fix flakiness
        if (mSdkLifecycleHelper != null) {
            mSdkLifecycleHelper.unloadSdk(SDK_NAME);
        }
    }

    @Test
    public void testAudioFocus() throws Exception {
        ICtsSdkProviderApi sdk = loadSdk();
        int result = sdk.requestAudioFocus();
        assertThat(result).isEqualTo(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    }

    private ICtsSdkProviderApi loadSdk() {
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        assertNotNull(callback.getSandboxedSdk());
        assertNotNull(callback.getSandboxedSdk().getInterface());
        return ICtsSdkProviderApi.Stub.asInterface(callback.getSandboxedSdk().getInterface());
    }
}
