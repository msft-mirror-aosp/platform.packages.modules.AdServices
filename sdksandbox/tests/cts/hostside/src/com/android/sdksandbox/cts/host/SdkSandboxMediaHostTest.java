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

package com.android.sdksandbox.cts.host;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.sdksandbox.hosttestutils.SdkSandboxDeviceSupportedHostRule;
import android.platform.test.annotations.LargeTest;

import com.android.modules.utils.build.testing.DeviceSdkLevel;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class SdkSandboxMediaHostTest extends BaseHostJUnit4Test {

    @Rule(order = 0)
    public final SdkSandboxDeviceSupportedHostRule deviceSupportRule =
            new SdkSandboxDeviceSupportedHostRule(this);

    private static final String TEST_APP_PACKAGE_NAME = "com.android.sdksandbox.cts.app";
    private static final String TEST_APP_APK_NAME = "CtsSdkSandboxHostTestApp.apk";
    private static final int TIME_OUT_MS = 600_000;
    private static final long WAIT_AFTER_REBOOT_MS = 10_000;

    private DeviceSdkLevel mDeviceSdkLevel;

    /**
     * Runs the given phase of a test by calling into the device. Throws an exception if the test
     * phase fails.
     *
     * <p>For example, <code>runPhase("testExample");</code>
     */
    private void runPhase(String phase) throws Exception {
        assertThat(
                        runDeviceTests(
                                TEST_APP_PACKAGE_NAME,
                                TEST_APP_PACKAGE_NAME + ".CtsSdkSandboxMediaTestApp",
                                phase))
                .isTrue();
    }

    @Before
    public void setUp() throws Exception {
        mDeviceSdkLevel = new DeviceSdkLevel(getDevice());
        uninstallPackage(TEST_APP_PACKAGE_NAME);
    }

    @After
    public void tearDown() throws Exception {
        uninstallPackage(TEST_APP_PACKAGE_NAME);
    }

    @Test
    public void testAudioFocus() throws Exception {
        installPackage(TEST_APP_APK_NAME);
        runPhase("testAudioFocus");
    }

    /**
     * Test that AppOps permissions for sandbox uids correctly initialised after device reboot. See
     * b/335809734 for context. Test is meant for V+ devices only - audio focus after reboot was
     * broken in 24Q2 (last U release) - see b/345381409#comment6
     */
    @Test
    @LargeTest // Reboot device
    public void testAudioFocus_AfterReboot() throws Exception {
        assumeTrue("Test is meant for V+ devices only", mDeviceSdkLevel.isDeviceAtLeastV());
        installPackage(TEST_APP_APK_NAME);

        getDevice().reboot();
        getDevice().waitForBootComplete(TIME_OUT_MS);
        // Allow 10 seconds after boot to avoid zygote contention.
        Thread.sleep(WAIT_AFTER_REBOOT_MS);

        // Explicitly update device config to ensure SDK Sandbox is enabled
        getDevice().executeShellCommand("device_config put adservices disable_sdk_sandbox false");

        runPhase("testAudioFocus");
    }
}