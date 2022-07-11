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

package com.android.tests.sdksandbox.host;

import static com.google.common.truth.Truth.assertThat;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class SdkSandboxHostTest extends BaseHostJUnit4Test {

    private static final String APP_PACKAGE = "com.android.sdksandbox.app";
    private static final String APP_TEST_CLASS = APP_PACKAGE + ".SdkSandboxTestApp";

    private static final String SDK_APK = "TestCodeProvider.apk";

    /**
     * Runs the given phase of a test by calling into the device. Throws an exception if the test
     * phase fails.
     *
     * <p>For example, <code>runPhase("testExample");</code>
     */
    private void runPhase(String phase) throws Exception {
        assertThat(runDeviceTests(APP_PACKAGE, APP_TEST_CLASS, phase)).isTrue();
    }

    @Before
    public void setUp() throws Exception {
        assertThat(getBuild()).isNotNull();
        assertThat(getDevice()).isNotNull();

        // Ensure the app is not currently running
        getDevice().executeShellCommand(String.format("pm clear %s", APP_PACKAGE));

        // Workaround for autoTeardown which removes packages installed in test
        if (!isPackageInstalled(SDK_APK)) {
            installPackage(SDK_APK, "-d");
        }
    }

    @Test
    public void testReloadingSdkAfterKillingSandboxIsSuccessful() throws Exception {
        // Have the app load multiple SDKs and bring up the sandbox
        runPhase("testLoadMultipleSdks");

        final String sandboxProcessName = APP_PACKAGE + "_sdk_sandbox";
        final String sandboxPid =
                getDevice().executeShellCommand(String.format("pidof -s %s", sandboxProcessName));
        // Kill the sandbox
        getDevice().executeShellCommand(String.format("kill -9 %s", sandboxPid));
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).doesNotContain(sandboxProcessName);

        // Loading both SDKs again should succeed
        runPhase("testLoadMultipleSdks");
    }
}
