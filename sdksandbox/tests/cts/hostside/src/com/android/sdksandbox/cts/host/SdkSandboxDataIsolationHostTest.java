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

package com.android.sdksandbox.cts.host;

import static com.google.common.truth.Truth.assertThat;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class SdkSandboxDataIsolationHostTest extends BaseHostJUnit4Test {

    private static final String APP_PACKAGE = "com.android.sdksandbox.cts.app";
    private static final String APP_APK = "CtsSdkSandboxHostTestApp.apk";
    private static final String APP_TEST_CLASS = APP_PACKAGE + ".SdkSandboxDataIsolationTestApp";

    private static final String APP_2_PACKAGE = "com.android.sdksandbox.cts.app2";
    private static final String APP_2_APK = "CtsSdkSandboxHostTestApp2.apk";

    /**
     * Runs the given phase of a test by calling into the device. Throws an exception if the test
     * phase fails.
     *
     * <p>For example, <code>runPhase("testExample");</code>
     */
    private void runPhase(String phase) throws Exception {
        assertThat(runDeviceTests(APP_PACKAGE, APP_TEST_CLASS, phase)).isTrue();
    }

    private void runPhase(
            String phase, String instrumentationArgKey, String instrumentationArgValue)
            throws Exception {
        runDeviceTests(
                new DeviceTestRunOptions(APP_PACKAGE)
                        .setDevice(getDevice())
                        .setTestClassName(APP_TEST_CLASS)
                        .setTestMethodName(phase)
                        .addInstrumentationArg(instrumentationArgKey, instrumentationArgValue));
    }

    @Before
    public void setUp() throws Exception {
        // These tests run on system user
        uninstallPackage(APP_PACKAGE);
        uninstallPackage(APP_2_PACKAGE);
    }

    @After
    public void tearDown() throws Exception {
        uninstallPackage(APP_PACKAGE);
        uninstallPackage(APP_2_PACKAGE);
    }

    @Test
    public void testAppCannotAccessAnySandboxDirectories() throws Exception {
        installPackage(APP_APK);
        installPackage(APP_2_APK);

        runPhase("testAppCannotAccessAnySandboxDirectories");
    }

    /** Test whether an SDK can access its provided data directories after data isolation. */
    @Test
    public void testSdkSandboxDataIsolation_SandboxCanAccessItsDirectory() throws Exception {
        installPackage(APP_APK);
        runPhase("testSdkSandboxDataIsolation_SandboxCanAccessItsDirectory");
    }
}
