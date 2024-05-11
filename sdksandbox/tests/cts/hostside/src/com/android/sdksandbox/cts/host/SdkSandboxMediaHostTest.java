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

import android.app.sdksandbox.hosttestutils.DeviceSupportHostUtils;
import android.platform.test.annotations.LargeTest;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class SdkSandboxMediaHostTest extends BaseHostJUnit4Test {

    private static final String TEST_APP_PACKAGE_NAME = "com.android.sdksandbox.cts.app";
    private static final String TEST_APP_APK_NAME = "CtsSdkSandboxHostTestApp.apk";
    public static final int TIME_OUT = 600_000;

    private final DeviceSupportHostUtils mDeviceSupportUtils = new DeviceSupportHostUtils(this);

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
        assumeTrue("Device supports SdkSandbox", mDeviceSupportUtils.isSdkSandboxSupported());
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
     * b/335809734 for context.
     */
    @Test
    @LargeTest // Reboot device
    public void testAudioFocus_AfterReboot() throws Exception {
        installPackage(TEST_APP_APK_NAME);

        getDevice().reboot();
        getDevice().waitForBootComplete(TIME_OUT);

        runPhase("testAudioFocus");
    }
}
