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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
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

    private static final String SANDBOX_SHARED_1_PROCESS_NAME = APP_SHARED_PACKAGE + "_sdk_sandbox";
    private static final String SANDBOX_SHARED_2_PROCESS_NAME =
            APP_SHARED_2_PACKAGE + "_sdk_sandbox";

    private boolean mWasRoot;

    private void startActivity(String pkg, String activity) throws Exception {
        getDevice().executeShellCommand(String.format("am start -W -n %s/.%s", pkg, activity));
    }

    private void killApp(String pkg) throws Exception {
        getDevice().executeShellCommand(String.format("am force-stop %s", pkg));
        waitForProcessDeath(pkg + '\n');
    }

    @Before
    public void setUp() throws Exception {
        assertThat(getBuild()).isNotNull();
        assertThat(getDevice()).isNotNull();

        mWasRoot = getDevice().isAdbRoot();
        getDevice().enableAdbRoot();
    }

    @After
    public void tearDown() throws Exception {
        cleanUpAppAndSandboxProcesses();

        if (!mWasRoot) {
            getDevice().disableAdbRoot();
        }
    }

    @Test
    public void testSdkSandboxIsDestroyedOnAppDestroy() throws Exception {
        startActivity(APP_PACKAGE, APP_ACTIVITY);
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE + '\n');
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

        killApp(APP_PACKAGE);
        waitForProcessDeath(SANDBOX_1_PROCESS_NAME);
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).doesNotContain(APP_PACKAGE + '\n');
        assertThat(processDump).doesNotContain(SANDBOX_1_PROCESS_NAME);

        // Wait 5 seconds to ensure that the sandbox has not restarted dying.
        Thread.sleep(5000);
        waitForProcessDeath(SANDBOX_1_PROCESS_NAME);
    }

    @Test
    public void testSdkSandboxIsCreatedPerApp() throws Exception {
        startActivity(APP_PACKAGE, APP_ACTIVITY);
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE + '\n');
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

        startActivity(APP_2_PACKAGE, APP_2_ACTIVITY);
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_2_PROCESS_NAME + '\n');
        assertThat(processDump).contains(SANDBOX_2_PROCESS_NAME);
        assertThat(processDump).contains(APP_PACKAGE + '\n');
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

        killApp(APP_2_PACKAGE);
        // Wait a bit to allow sandbox death
        waitForProcessDeath(SANDBOX_2_PROCESS_NAME);
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).doesNotContain(APP_2_PROCESS_NAME + '\n');
        assertThat(processDump).doesNotContain(SANDBOX_2_PROCESS_NAME);
        assertThat(processDump).contains(APP_PACKAGE + '\n');
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);
    }

    @Test
    public void testAppAndSdkSandboxAreKilledOnLoadedSdkUpdate() throws Exception {
        startActivity(APP_PACKAGE, APP_ACTIVITY);

        // Should see app/sdk sandbox running
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE + '\n');
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

        // Update package loaded by app
        installPackage(CODE_APK, "-d");
        waitForProcessDeath(SANDBOX_1_PROCESS_NAME);

        // Should no longer see app/sdk sandbox running
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).doesNotContain(APP_PACKAGE + '\n');
        assertThat(processDump).doesNotContain(SANDBOX_1_PROCESS_NAME);
    }

    @Test
    public void testAppAndSdkSandboxAreKilledForNonLoadedSdkUpdate() throws Exception {
        // Have the app load the first SDK.
        startActivity(APP_2_PACKAGE, APP_2_ACTIVITY);

        // Should see app/sdk sandbox running
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_2_PROCESS_NAME + '\n');
        assertThat(processDump).contains(SANDBOX_2_PROCESS_NAME);

        // Update package consumed by the app, but not loaded into the sandbox.
        installPackage(CODE_APK_2, "-d");
        waitForProcessDeath(SANDBOX_2_PROCESS_NAME);

        // Should no longer see app/sdk sandbox running
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).doesNotContain(APP_2_PROCESS_NAME + '\n');
        assertThat(processDump).doesNotContain(SANDBOX_2_PROCESS_NAME);
    }

    @Test
    public void testAppsWithSharedUidCanLoadSameSdk() throws Exception {
        startActivity(APP_SHARED_PACKAGE, APP_SHARED_ACTIVITY);
        assertThat(runDeviceTests(APP_SHARED_2_PACKAGE,
                "com.android.sdksandbox.shared.app2.SdkSandboxTestSharedApp2",
                "testLoadSdkIsSuccessful")).isTrue();
    }

    @Test
    public void testAppsWithSharedUid_AllSandboxesDieWhenOneAppDies() throws Exception {
        startActivity(APP_SHARED_PACKAGE, APP_SHARED_ACTIVITY);
        assertThat(runDeviceTests(APP_SHARED_2_PACKAGE,
                "com.android.sdksandbox.shared.app2.SdkSandboxTestSharedApp2",
                "testLoadSdkIsSuccessful")).isTrue();

        // APP_SHARED_2_PACKAGE dies after running device-side tests, and sandbox for
        // APP_SHARED_PACKAGE should also die since they share the same uid
        waitForProcessDeath(SANDBOX_SHARED_1_PROCESS_NAME);
        waitForProcessDeath(SANDBOX_SHARED_2_PROCESS_NAME);

        // Neither of the sandboxes should be respawned later
        Thread.sleep(5000);
        waitForProcessDeath(SANDBOX_SHARED_1_PROCESS_NAME);
        waitForProcessDeath(SANDBOX_SHARED_2_PROCESS_NAME);
    }

    @Test
    public void testSandboxIsKilledWhenKillswitchEnabled() throws Exception {
        try {
            getDevice()
                    .executeShellCommand("device_config put adservices disable_sdk_sandbox false");
            startActivity(APP_PACKAGE, APP_ACTIVITY);
            String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
            assertThat(processDump).contains(APP_PACKAGE + '\n');
            assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

            getDevice()
                    .executeShellCommand("device_config put adservices disable_sdk_sandbox true");
            waitForProcessDeath(SANDBOX_1_PROCESS_NAME);

            processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
            assertThat(processDump).contains(APP_PACKAGE + '\n');
            assertThat(processDump).doesNotContain(SANDBOX_1_PROCESS_NAME);
        } finally {
            getDevice().executeShellCommand("cmd sdk_sandbox set-state --enabled");
        }
    }

    @Test
    public void testBackgroundingAppReducesSandboxPriority() throws Exception {
        startActivity(APP_PACKAGE, APP_ACTIVITY);

        // Should see app/sdk sandbox running
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE + '\n');
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

        int initialSandboxOomScoreAdj = getOomScoreAdj(SANDBOX_1_PROCESS_NAME);

        // Navigate to home screen to send both apps to the background.
        getDevice().executeShellCommand("input keyevent KEYCODE_HOME");

        // Wait for app to be backgrounded and unbinding of sandbox to complete.
        Thread.sleep(5000);

        // Should see app/sdk sandbox running
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE + '\n');
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

        int finalSandboxOomScoreAdj = getOomScoreAdj(SANDBOX_1_PROCESS_NAME);
        // The higher the oom adj score, the lower the priority of the process.
        assertThat(finalSandboxOomScoreAdj).isGreaterThan(initialSandboxOomScoreAdj);
    }

    @Test
    public void testSandboxReconnectsAfterDeath() throws Exception {
        startActivity(APP_PACKAGE, APP_ACTIVITY);

        // Should see app/sdk sandbox running
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE + '\n');
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

        String initialSandboxPid = getDevice().getProcessPid(SANDBOX_1_PROCESS_NAME);
        getDevice().executeShellCommand("kill -9 " + initialSandboxPid);

        Thread.sleep(5000);

        // The sandbox gets restarted, so it should still be running
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE + '\n');
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

        String finalSandboxPid = getDevice().getProcessPid(SANDBOX_1_PROCESS_NAME);
        assertThat(initialSandboxPid).isNotEqualTo(finalSandboxPid);

        int initialSandboxOomScoreAdj = getOomScoreAdj(SANDBOX_1_PROCESS_NAME);

        // Navigate to home screen to send both apps to the background.
        getDevice().executeShellCommand("input keyevent KEYCODE_HOME");

        // Wait for app to be backgrounded and unbinding of sandbox to complete.
        Thread.sleep(2000);

        // Should see app/sdk sandbox running
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE + '\n');
        assertThat(processDump).contains(SANDBOX_1_PROCESS_NAME);

        int finalSandboxOomScoreAdj = getOomScoreAdj(SANDBOX_1_PROCESS_NAME);
        // The higher the oom adj score, the lower the priority of the process.
        assertThat(finalSandboxOomScoreAdj).isGreaterThan(initialSandboxOomScoreAdj);
    }

    private void cleanUpAppAndSandboxProcesses() throws Exception {
        for (String pkg :
                new String[] {
                    APP_PACKAGE, APP_2_PACKAGE, APP_SHARED_PACKAGE, APP_SHARED_2_PACKAGE
                }) {
            killApp(pkg);
        }

        // Ensure no sandbox is currently running
        for (String sandbox :
                new String[] {
                    SANDBOX_1_PROCESS_NAME,
                    SANDBOX_2_PROCESS_NAME,
                    SANDBOX_SHARED_1_PROCESS_NAME,
                    SANDBOX_SHARED_2_PROCESS_NAME
                }) {
            waitForProcessDeath(sandbox);
        }
    }

    private int getOomScoreAdj(String processName) throws DeviceNotAvailableException {
        String pid = getDevice().getProcessPid(processName);
        String oomScoreAdj =
                getDevice().executeShellCommand("cat /proc/" + pid + "/oom_score_adj").trim();
        return Integer.parseInt(oomScoreAdj);
    }

    private void waitForProcessDeath(String processName) throws Exception {
        int timeElapsed = 0;
        while (timeElapsed <= 30000) {
            final String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
            if (processDump.contains(processName)) {
                Thread.sleep(1000);
                timeElapsed += 1000;
                continue;
            }
            return;
        }
        throw new AssertionError("Process " + processName + " has not died.");
    }
}
