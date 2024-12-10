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

import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_ADSERVICES_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.tests.sandbox.fledge.SandboxedFledgeManagerTest.PROPERTY_DISABLE_SDK_SANDBOX;

import static com.google.common.truth.Truth.assertWithMessage;

import android.Manifest;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.SdkSandboxDeviceSupportedRule;
import android.os.Bundle;
import android.os.Process;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.annotations.SetPpapiAppAllowList;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

@SetFlagEnabled(KEY_ADSERVICES_ENABLED)
@SetFlagEnabled(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
@SetFlagDisabled(PROPERTY_DISABLE_SDK_SANDBOX)
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
@SetPpapiAppAllowList
public final class SandboxedFledgeManagerTest extends CtsSandboxedFledgeManagerTestCase {
    public static final String PROPERTY_DISABLE_SDK_SANDBOX = "disable_sdk_sandbox";

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final String SDK_NAME = "com.android.tests.providers.sdkfledge";

    private boolean mHasAccessToDevOverrides;

    private String mAccessStatus;

    @Rule(order = 0)
    public final SdkSandboxDeviceSupportedRule supportedRule = new SdkSandboxDeviceSupportedRule();

    @Before
    public void setup() throws TimeoutException {
        DevContextFilter devContextFilter =
                DevContextFilter.create(mContext, /* developerModeFeatureEnabled= */ false);
        DevContext devContext = devContextFilter.createDevContext(Process.myUid());
        boolean isDebuggable = devContextFilter.isDebuggable(devContext.getCallingAppPackageName());
        boolean isDeveloperMode = devContextFilter.isDeviceDevOptionsEnabledOrDebuggable();
        mHasAccessToDevOverrides = devContext.getDeviceDevOptionsEnabled();
        mAccessStatus =
                String.format("Debuggable: %b\n", isDebuggable)
                        + String.format("Developer options on: %b", isDeveloperMode);

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.WRITE_DEVICE_CONFIG,
                        Manifest.permission.WRITE_ALLOWLISTED_DEVICE_CONFIG);

        makeTestProcessForeground();

        // Kill AdServices process
        AdservicesTestHelper.killAdservicesProcess(sContext);
    }

    /**
     * Starts a foreground activity to make the test process a foreground one to pass PPAPI and SDK
     * Sandbox checks
     */
    private void makeTestProcessForeground() throws TimeoutException {
        SimpleActivity.startAndWaitForSimpleActivity(sContext, Duration.ofSeconds(1));
    }

    @After
    public void shutDown() {
        SimpleActivity.stopSimpleActivity(sContext);
    }

    @Test
    public void loadSdkAndRunFledgeFlow() {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        SdkSandboxManager sdkSandboxManager = sContext.getSystemService(SdkSandboxManager.class);
        assertWithMessage("SdkSandboxManager should not be null")
                .that(sdkSandboxManager)
                .isNotNull();

        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        sdkSandboxManager.loadSdk(SDK_NAME, new Bundle(), CALLBACK_EXECUTOR, callback);

        callback.assertLoadSdkIsSuccessful();
    }
}
