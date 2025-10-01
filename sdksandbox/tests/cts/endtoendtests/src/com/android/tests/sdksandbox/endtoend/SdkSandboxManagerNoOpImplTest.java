/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.app.sdksandbox.AppOwnedSdkSandboxInterface;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.flags.Flags;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.FakeRequestSurfacePackageCallback;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

@RunWith(JUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_SDK_SANDBOX_NO_OP_IMPL)
public class SdkSandboxManagerNoOpImplTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private SdkSandboxManager mSdkSandboxManager;

    @Before
    public void setup() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mSdkSandboxManager = context.getSystemService(SdkSandboxManager.class);
    }

    @Test
    public void testGetSdkSandboxState() {
        int state = SdkSandboxManager.getSdkSandboxState();
        assertThat(state).isEqualTo(SdkSandboxManager.SDK_SANDBOX_STATE_DISABLED);
    }

    @Test
    public void testLoadSdk() {
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        mSdkSandboxManager.loadSdk("test", new Bundle(), Runnable::run, callback);

        callback.assertLoadSdkIsUnsuccessful();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_SDK_SANDBOX_DISABLED);
    }

    @Test
    public void testGetSandboxedSdks() {
        assertThat(mSdkSandboxManager.getSandboxedSdks()).isEmpty();
    }

    @Test
    public void testRequestSurfacePackage() {
        final FakeRequestSurfacePackageCallback surfacePackageCallback =
                new FakeRequestSurfacePackageCallback();

        mSdkSandboxManager.requestSurfacePackage(
                "test", new Bundle(), Runnable::run, surfacePackageCallback);

        assertThat(surfacePackageCallback.isRequestSurfacePackageSuccessful()).isFalse();
        assertThat(surfacePackageCallback.getSurfacePackageErrorCode())
                .isEqualTo(SdkSandboxManager.REQUEST_SURFACE_PACKAGE_SDK_NOT_LOADED);
    }

    @Test
    public void testAppOwnedSdkSandboxInterface() {
        mSdkSandboxManager.registerAppOwnedSdkSandboxInterface(
                new AppOwnedSdkSandboxInterface(
                        "test", /* version= */ 0, /* interfaceIBinder= */ new Binder()));

        List<AppOwnedSdkSandboxInterface> appOwnedSdkSandboxInterfaces =
                mSdkSandboxManager.getAppOwnedSdkSandboxInterfaces();
        assertThat(appOwnedSdkSandboxInterfaces.size()).isEqualTo(1);
        assertThat(appOwnedSdkSandboxInterfaces.getFirst().getName()).isEqualTo("test");

        mSdkSandboxManager.unregisterAppOwnedSdkSandboxInterface("test");
        appOwnedSdkSandboxInterfaces = mSdkSandboxManager.getAppOwnedSdkSandboxInterfaces();
        assertThat(appOwnedSdkSandboxInterfaces.size()).isEqualTo(0);
    }
}
