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
public final class SdkSandboxLifecycleHostTest extends BaseHostJUnit4Test {

    private static final String APP_PACKAGE = "com.android.sdksandbox.app";
    private static final String APP_2_PACKAGE = "com.android.sdksandbox.app2";

    private static final String APP_SHARED_PACKAGE = "com.android.sdksandbox.shared.app1";
    private static final String APP_SHARED_ACTIVITY = "SdkSandboxTestSharedActivity";
    private static final String APP_SHARED_2_PACKAGE = "com.android.sdksandbox.shared.app2";

    private static final String APP_ACTIVITY = "SdkSandboxTestActivity";
    private static final String APP_2_ACTIVITY = "SdkSandboxTestActivity2";

    private static final String CODE_APK = "TestCodeProvider.apk";
    private static final String CODE_APK_2 = "TestCodeProvider2.apk";

    private static final String APP_2_PROCESS_NAME = "com.android.sdksandbox.processname";
    private static final String SANDBOX_2_PROCESS_NAME = APP_2_PROCESS_NAME
                                                            + "_sdk_sandbox";
    /**
     * process name for app1 is not defined and it takes the package name by default
     */
    private static final String SANDBOX_1_PROCESS_NAME = APP_PACKAGE + "_sdk_sandbox";

    private void clearProcess(String pkg) throws Exception {
        getDevice().executeShellCommand(String.format("pm clear %s", pkg));
    }

    private void startActivity(String pkg, String activity) throws Exception {
        getDevice().executeShellCommand(String.format("am start -W -n %s/.%s", pkg, activity));
    }

    private void killApp(String pkg) throws Exception {
        getDevice().executeShellCommand(String.format("am force-stop %s", pkg));
    }

    @Before
    public void setUp() throws Exception {
        assertThat(getBuild()).isNotNull();
        assertThat(getDevice()).isNotNull();

        // Ensure neither app is currently running
        for (String pkg : new String[]{APP_PACKAGE, APP_2_PACKAGE}) {
            clearProcess(pkg);
        }

        // Workaround for autoTeardown which removes packages installed in test
        for (String apk : new String[]{CODE_APK, CODE_APK_2}) {
            if (!isPackageInstalled(apk)) {
                installPackage(apk, "-d");
            }
        }
    }

    @Test
    public void testSdkSandboxIsDestroyedOnAppDestroy() throws Exception {
        startActivity(APP_PACKAGE, APP_ACTIVITY);
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE);
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

        killApp(APP_PACKAGE);
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).doesNotContain(APP_PACKAGE);
        assertThat(processDump).doesNotContain(SANDBOX_1_PROCESS_NAME);
    }

    @Test
    public void testSdkSandboxIsCreatedPerApp() throws Exception {
        startActivity(APP_PACKAGE, APP_ACTIVITY);
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE);
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

        startActivity(APP_2_PACKAGE, APP_2_ACTIVITY);
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_2_PROCESS_NAME);
        assertThat(processDump).contains(SANDBOX_2_PROCESS_NAME);
        assertThat(processDump).contains(APP_PACKAGE);
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

        killApp(APP_2_PACKAGE);
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).doesNotContain(APP_2_PROCESS_NAME);
        assertThat(processDump).doesNotContain(SANDBOX_2_PROCESS_NAME);
        assertThat(processDump).contains(APP_PACKAGE);
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);
    }

    @Test
    public void testAppAndSdkSandboxAreKilledOnLoadedSdkUpdate() throws Exception {
        startActivity(APP_PACKAGE, APP_ACTIVITY);

        // Should see app/sdk sandbox running
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE);

        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

        // Update package loaded by app
        installPackage(CODE_APK, "-d");

        // Should no longer see app/sdk sandbox running
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).doesNotContain(APP_PACKAGE);
        assertThat(processDump).doesNotContain(SANDBOX_1_PROCESS_NAME);
    }

    @Test
    public void testAppAndSdkSandboxAreKilledForNonLoadedSdkUpdate() throws Exception {
        // Have the app load the first SDK.
        startActivity(APP_PACKAGE, APP_ACTIVITY);

        // Should see app/sdk sandbox running
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE);
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

        // Update package consumed by the app, but not loaded into the sandbox.
        installPackage(CODE_APK_2, "-d");

        // Should no longer see app/sdk sandbox running
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).doesNotContain(APP_PACKAGE);
        assertThat(processDump).doesNotContain(SANDBOX_1_PROCESS_NAME);
    }

    @Test
    public void testAppsWithSharedUidCanLoadSameSdk() throws Exception {
        startActivity(APP_SHARED_PACKAGE, APP_SHARED_ACTIVITY);
        assertThat(runDeviceTests(APP_SHARED_2_PACKAGE,
                "com.android.sdksandbox.shared.app2.SdkSandboxTestSharedApp2",
                "testLoadSdkIsSuccessful")).isTrue();
    }
}
