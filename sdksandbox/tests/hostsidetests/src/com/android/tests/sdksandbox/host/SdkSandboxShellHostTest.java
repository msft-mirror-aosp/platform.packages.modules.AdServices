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

import android.app.sdksandbox.hosttestutils.AwaitUtils;
import android.app.sdksandbox.hosttestutils.SdkSandboxDeviceSupportedHostRule;

import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class SdkSandboxShellHostTest extends BaseHostJUnit4Test {

    @Rule(order = 0)
    public final SdkSandboxDeviceSupportedHostRule deviceSupportRule =
            new SdkSandboxDeviceSupportedHostRule(this);

    private static final String DEBUGGABLE_APP_PACKAGE = "com.android.sdksandbox.debuggable";
    private static final String DEBUGGABLE_APP_ACTIVITY = "SdkSandboxTestDebuggableActivity";

    private static final String APP_PACKAGE = "com.android.sdksandbox.app";
    private static final String APP_ACTIVITY = "SdkSandboxTestActivity";

    private static final String DEBUGGABLE_APP_SANDBOX_NAME = DEBUGGABLE_APP_PACKAGE
      + "_sdk_sandbox";
    private static final String APP_SANDBOX_NAME = APP_PACKAGE + "_sdk_sandbox";
    private final HashSet<Integer> mOriginalUsers = new HashSet<>();

    /** Root device for all tests. */
    @BeforeClassWithInfo
    public static void beforeClassWithDevice(TestInformation testInfo) throws Exception {
        assertThat(testInfo.getDevice().enableAdbRoot()).isTrue();
    }

    /** UnRoot device after all tests. */
    @AfterClassWithInfo
    public static void afterClassWithDevice(TestInformation testInfo) throws Exception {
        testInfo.getDevice().disableAdbRoot();
    }

    @Before
    public void setUp() throws Exception {
        assertThat(getBuild()).isNotNull();
        assertThat(getDevice()).isNotNull();

        // Ensure neither app is currently running
        for (String pkg : new String[]{APP_PACKAGE, DEBUGGABLE_APP_PACKAGE}) {
            clearProcess(pkg);
            getDevice().executeShellV2Command(String.format("cmd deviceidle whitelist +%s", pkg));
        }

        mOriginalUsers.addAll(getDevice().listUsers());
    }

    @After
    public void tearDown() throws Exception {
        for (Integer userId : getDevice().listUsers()) {
            if (!mOriginalUsers.contains(userId)) {
                getDevice().removeUser(userId);
            }
        }
        mOriginalUsers.clear();

        // Ensure all apps are not in allowlist.
        for (String pkg : new String[] {APP_PACKAGE, DEBUGGABLE_APP_PACKAGE}) {
            getDevice().executeShellV2Command(String.format("cmd deviceidle whitelist -%s", pkg));
        }
    }

    @Test
    public void testStartAndStopSdkSandboxSucceedsForDebuggableApp() throws Exception {
        CommandResult output = getDevice().executeShellV2Command(
                String.format("cmd sdk_sandbox start %s", DEBUGGABLE_APP_PACKAGE));
        assertThat(output.getStderr()).isEmpty();
        assertThat(output.getStatus()).isEqualTo(CommandStatus.SUCCESS);

        waitForProcessStart(DEBUGGABLE_APP_SANDBOX_NAME);

        output = getDevice().executeShellV2Command(
                String.format("cmd sdk_sandbox stop %s", DEBUGGABLE_APP_PACKAGE));
        assertThat(output.getStderr()).isEmpty();
        assertThat(output.getStatus()).isEqualTo(CommandStatus.SUCCESS);

        waitForProcessDeath(DEBUGGABLE_APP_SANDBOX_NAME);
    }

    @Test
    public void testStartSdkSandboxFailsForNonDebuggableApp() throws Exception {
        CommandResult output = getDevice().executeShellV2Command(
                String.format("cmd sdk_sandbox start %s", APP_PACKAGE));
        assertThat(output.getStatus()).isEqualTo(CommandStatus.FAILED);

        String processDump = getDevice().executeShellCommand("ps -A");
        assertThat(processDump).doesNotContain(APP_SANDBOX_NAME);
    }

    @Test
    public void testStartSdkSandboxFailsForIncorrectUser() throws Exception {
        int otherUserId = getDevice().createUser("TestUser_" + System.currentTimeMillis());
        CommandResult output = getDevice().executeShellV2Command(
                String.format("cmd sdk_sandbox start --user %s %s",
                        otherUserId, DEBUGGABLE_APP_PACKAGE));
        assertThat(output.getStatus()).isEqualTo(CommandStatus.FAILED);

        String processDump = getDevice().executeShellCommand("ps -A");
        assertThat(processDump).doesNotContain(DEBUGGABLE_APP_SANDBOX_NAME);
    }

    @Test
    public void testStopSdkSandboxSucceedsForRunningDebuggableApp() throws Exception {
        startActivity(DEBUGGABLE_APP_PACKAGE, DEBUGGABLE_APP_ACTIVITY);
        waitForProcessStart(DEBUGGABLE_APP_SANDBOX_NAME);

        CommandResult output =
                getDevice()
                        .executeShellV2Command(
                                String.format(
                                        "cmd sdk_sandbox stop --user %s %s",
                                        getDevice().getCurrentUser(), DEBUGGABLE_APP_PACKAGE));
        assertThat(output.getStderr()).isEmpty();
        assertThat(output.getStatus()).isEqualTo(CommandStatus.SUCCESS);

        waitForProcessDeath(DEBUGGABLE_APP_SANDBOX_NAME);
    }

    @Test
    public void testStartSdkSandboxFailsForInvalidPackage() throws Exception {
        String invalidPackage = "com.android.sdksandbox.nonexistent";
        CommandResult output = getDevice().executeShellV2Command(
                String.format("cmd sdk_sandbox start %s", invalidPackage));
        assertThat(output.getStatus()).isEqualTo(CommandStatus.FAILED);
    }

    @Test
    public void testStopSdkSandboxFailsForNonDebuggableApp() throws Exception {
        startActivity(APP_PACKAGE, APP_ACTIVITY);
        waitForProcessStart(APP_SANDBOX_NAME);

        CommandResult output = getDevice().executeShellV2Command(
                String.format("cmd sdk_sandbox stop %s", APP_PACKAGE));
        assertThat(output.getStatus()).isEqualTo(CommandStatus.FAILED);

        String processDump = getDevice().executeShellCommand("ps -A");
        assertThat(processDump).contains(APP_SANDBOX_NAME);
    }

    @Test
    public void testStopSdkSandboxFailsForIncorrectUser() throws Exception {
        startActivity(DEBUGGABLE_APP_PACKAGE, DEBUGGABLE_APP_ACTIVITY);
        waitForProcessStart(DEBUGGABLE_APP_SANDBOX_NAME);

        int otherUserId = getDevice().createUser("TestUser_" + System.currentTimeMillis());
        CommandResult output = getDevice().executeShellV2Command(String.format(
                "cmd sdk_sandbox stop --user %s %s", otherUserId, DEBUGGABLE_APP_PACKAGE));
        assertThat(output.getStatus()).isEqualTo(CommandStatus.FAILED);

        String processDump = getDevice().executeShellCommand("ps -A");
        assertThat(processDump).contains(DEBUGGABLE_APP_SANDBOX_NAME);
    }

    private void clearProcess(String pkg) throws Exception {
        getDevice().executeShellCommand(String.format("pm clear %s", pkg));
    }

    private void startActivity(String pkg, String activity) throws Exception {
        getDevice().executeShellCommand(String.format("am start -W -n %s/.%s", pkg, activity));
    }

    private void waitForProcessStart(String processName) throws Exception {
        AwaitUtils.waitFor(
                () -> {
                    String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
                    return processDump.contains(processName);
                },
                "Process " + processName + " has not started.");
    }

    private void waitForProcessDeath(String processName) throws Exception {
        AwaitUtils.waitFor(
                () -> {
                    String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
                    return !processDump.contains(processName);
                },
                "Process " + processName + " has not died.");
    }
}
