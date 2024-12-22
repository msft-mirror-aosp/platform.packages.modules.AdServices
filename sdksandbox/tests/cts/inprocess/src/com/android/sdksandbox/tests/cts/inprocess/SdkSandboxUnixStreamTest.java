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

package com.android.sdksandbox.tests.cts.inprocess;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.app.ActivityManager;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.sdksandbox.DeviceSupportedBaseTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/** Tests for the instrumentation running the Sdk sanbdox tests. */
@RunWith(JUnit4.class)
public class SdkSandboxUnixStreamTest extends DeviceSupportedBaseTest {

    @Before
    public void setUp() {
        SystemUtil.runShellCommandOrThrow(
                "am start --user current -W -S --activity-reorder-to-front "
                        + "com.android.socketapp/.SocketApp");
        assertAppAndSandboxRunning();
    }

    @Test
    public void testUnixStreamSocket_sandboxToSelf() throws IOException {
        String socketName = "SameProcessSocket";

        try (LocalServerSocket serverSocket = new LocalServerSocket(socketName);
                LocalSocket sendSocket = new LocalSocket()) {

            sendSocket.connect(new LocalSocketAddress(socketName));
            try (LocalSocket receiveSocket = serverSocket.accept()) {
                assertThat(receiveSocket).isNotNull();

                // Test trivial read and write
                sendSocket.getOutputStream().write(42);
                assertThat(receiveSocket.getInputStream().read()).isEqualTo(42);
            }
        }
    }

    @Test
    public void testUnixStreamSocket_sandboxToApp() throws IOException {
        LocalSocketAddress appSocket = new LocalSocketAddress("SocketApp");

        try (LocalSocket socketToApp = new LocalSocket()) {
            assertThrows(
                    "The socket should exist already.",
                    IOException.class,
                    () -> socketToApp.bind(appSocket));
            assertThrows(
                    "Connection to a different app socket should be forbidden.",
                    IOException.class,
                    () -> socketToApp.connect(appSocket));
        }
    }

    @Test
    public void testUnixStreamSocket_sandboxToSandbox() throws IOException {
        LocalSocketAddress sandboxSocket = new LocalSocketAddress("SocketSdkProvider");

        try (LocalSocket socketToSandbox = new LocalSocket()) {
            assertThrows(
                    "The socket should exist already.",
                    IOException.class,
                    () -> socketToSandbox.bind(sandboxSocket));
            assertThrows(
                    "Connection to a different sandbox socket should be forbidden.",
                    IOException.class,
                    () -> socketToSandbox.connect(sandboxSocket));
        }
    }

    private static void assertAppAndSandboxRunning() {
        if (SdkLevel.isAtLeastV()) {
            List<String> runningProcesses = getRunningProcesses();
            assertWithMessage("No running processes").that(runningProcesses).isNotNull();
            assertWithMessage("SocketApp is not running")
                    .that(runningProcesses)
                    .contains("com.android.socketapp");
            assertWithMessage("SocketApp sandbox is not running")
                    .that(runningProcesses)
                    .contains("com.android.socketapp_sdk_sandbox");
        } else {
            // On pre-V devices instrumentation for sandbox test is not set up correctly, and
            // ActivityManager#getRunningAppProcesses does not return the correct list.
            assertWithMessage("SocketApp is not running")
                    .that(SystemUtil.runShellCommand("pgrep -lfx com.android.socketapp"))
                    .contains("com.android.socketapp\n");
            assertWithMessage("SocketApp sandbox is not running")
                    .that(
                            SystemUtil.runShellCommand(
                                    "pgrep -lfx com.android.socketapp_sdk_sandbox"))
                    .contains("com.android.socketapp_sdk_sandbox\n");
        }
    }

    private static List<String> getRunningProcesses() {
        final ActivityManager activityManager =
                InstrumentationRegistry.getInstrumentation()
                        .getTargetContext()
                        .getSystemService(ActivityManager.class);
        return SystemUtil.runWithShellPermissionIdentity(
                        () -> activityManager.getRunningAppProcesses(),
                        android.Manifest.permission.REAL_GET_TASKS)
                .stream()
                .map(app -> app.processName)
                .collect(Collectors.toList());
    }
}
