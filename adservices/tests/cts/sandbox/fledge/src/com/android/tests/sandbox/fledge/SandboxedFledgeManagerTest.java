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


import android.Manifest;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.SdkSandboxDeviceSupportedRule;
import android.content.Context;
import android.os.Bundle;
import android.os.Process;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.service.FlagsConstants;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

public class SandboxedFledgeManagerTest extends AdServicesCtsTestCase {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final String SDK_NAME = "com.android.tests.providers.sdkfledge";

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    private boolean mHasAccessToDevOverrides;

    private String mAccessStatus;

    @Rule(order = 0)
    public final SdkSandboxDeviceSupportedRule supportedRule = new SdkSandboxDeviceSupportedRule();

    @Rule(order = 10)
    public AdServicesDeviceSupportedRule mAdServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    @Before
    public void setup() throws TimeoutException {
        DevContextFilter devContextFilter = DevContextFilter.create(sContext);
        DevContext devContext = DevContextFilter.create(sContext).createDevContext(Process.myUid());
        boolean isDebuggable = devContextFilter.isDebuggable(devContext.getCallingAppPackageName());
        boolean isDeveloperMode = devContextFilter.isDeveloperMode();
        mHasAccessToDevOverrides = devContext.getDevOptionsEnabled();
        mAccessStatus =
                String.format("Debuggable: %b\n", isDebuggable)
                        + String.format("Developer options on: %b", isDeveloperMode);

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.WRITE_DEVICE_CONFIG);

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

        final SdkSandboxManager sdkSandboxManager =
                sContext.getSystemService(SdkSandboxManager.class);
        Assert.assertNotNull("SdkSandboxManager should not be null", sdkSandboxManager);
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        sdkSandboxManager.loadSdk(SDK_NAME, new Bundle(), CALLBACK_EXECUTOR, callback);

        callback.assertLoadSdkIsSuccessful();
    }

    @Override
    protected AdServicesFlagsSetterRule getAdServicesFlagsSetterRule() {
        return AdServicesFlagsSetterRule.forAllApisEnabledTests()
                .setAdServicesEnabled(true)
                .setAllLogcatTags()
                .setFlag(FlagsConstants.KEY_ENFORCE_ISOLATE_MAX_HEAP_SIZE, false)
                .setFlag(FlagsConstants.KEY_ISOLATE_MAX_HEAP_SIZE_BYTES, 0)
                .setFlag(FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK, true);
    }
}
