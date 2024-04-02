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

import android.platform.test.annotations.LargeTest;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class SdkSandboxInstallationTest extends BaseHostJUnit4Test {

    private static final String SDK_PACKAGE = "com.android.sdksandbox.cts.provider";

    private static final String SDK_PROVIDER1 = SDK_PACKAGE + ".dataisolationtest";
    private static final String SDK_PROVIDER2 = SDK_PACKAGE + ".storagetest";

    public static final int TIME_OUT = 600_000;

    @Test
    @LargeTest // Reboot device
    public void testSdkSandbox_deviceReboot_sdkInstalled() throws Exception {
        assertInstalled(
                getDevice().executeShellCommand("pm dump-package " + SDK_PROVIDER1), SDK_PROVIDER1);

        assertInstalled(
                getDevice().executeShellCommand("pm dump-package " + SDK_PROVIDER2), SDK_PROVIDER2);

        getDevice().reboot();
        getDevice().waitForBootComplete(TIME_OUT);

        assertInstalled(
                getDevice().executeShellCommand("pm dump-package " + SDK_PROVIDER1), SDK_PROVIDER1);
        assertInstalled(
                getDevice().executeShellCommand("pm dump-package " + SDK_PROVIDER2), SDK_PROVIDER2);
    }

    private static void assertInstalled(String str, String provider) {
        if (str == null || !str.contains("Package [" + provider + "]")) {
            throw new AssertionError("Expected package [" + provider + "] not found at " + str);
        }
    }
}
