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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class SdkSandboxRestrictionsHostTest  extends BaseHostJUnit4Test {

    private static final String TEST_APP_RESTRICTIONS_PACKAGE = "com.android.tests.sdksandbox";
    private static final String TEST_APP_RESTRICTIONS_APK = "SdkSandboxRestrictionsTestApp.apk";

    /**
     * Runs the given phase of a test by calling into the device.
     * Throws an exception if the test phase fails.
     * <p>
     * For example, <code>runPhase("testExample");</code>
     */
    private void runPhase(String phase) throws Exception {
        assertThat(runDeviceTests("com.android.tests.sdksandbox",
                "com.android.tests.sdksandbox.SdkSandboxRestrictionsTestApp",
                phase)).isTrue();
    }

    @Before
    public void setUp() throws Exception {
        uninstallPackage(TEST_APP_RESTRICTIONS_PACKAGE);
    }

    @After
    public void tearDown() throws Exception {
        uninstallPackage(TEST_APP_RESTRICTIONS_PACKAGE);
    }

    @Test
    public void testBroadcastRestrictions() throws Exception {
        installPackage(TEST_APP_RESTRICTIONS_APK);
        runPhase("testSdkSandboxBroadcastRestrictions");
    }
}
