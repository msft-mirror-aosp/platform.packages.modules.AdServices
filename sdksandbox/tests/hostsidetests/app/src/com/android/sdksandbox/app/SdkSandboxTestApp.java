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

package com.android.sdksandbox.app;

import static com.google.common.truth.Truth.assertThat;

import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SdkSandboxTestApp {

    private static final String SDK_NAME = "com.android.testcode";
    private static final String SDK_2_NAME = "com.android.testcode2";

    private SdkSandboxManager mSdkSandboxManager;

    @Before
    public void setup() {
        mSdkSandboxManager =
                ApplicationProvider.getApplicationContext()
                        .getSystemService(SdkSandboxManager.class);
        assertThat(mSdkSandboxManager).isNotNull();
    }

    @Test
    public void testLoadMultipleSdks() throws Exception {
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();

        FakeLoadSdkCallback callback2 = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_2_NAME, new Bundle(), Runnable::run, callback2);
        assertThat(callback2.isLoadSdkSuccessful()).isTrue();
    }
}
