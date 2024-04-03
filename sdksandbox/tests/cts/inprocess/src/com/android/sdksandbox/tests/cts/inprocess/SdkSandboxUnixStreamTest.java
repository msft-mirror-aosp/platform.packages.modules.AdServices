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

import android.app.sdksandbox.testutils.SdkSandboxDeviceSupportedRule;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;

/** Tests for the instrumentation running the Sdk sanbdox tests. */
@RunWith(JUnit4.class)
public class SdkSandboxUnixStreamTest {

    @Rule
    public final SdkSandboxDeviceSupportedRule supportedRule = new SdkSandboxDeviceSupportedRule();

    @Before
    public void setUp() {
        assertAppLaunchSuccessful(
                SystemUtil.runShellCommandOrThrow(
                        "am start --user current -W -S --activity-reorder-to-front "
                                + "com.android.socketapp/.SocketApp"));
        assertAppIsRunning();
        assertAppSandboxIsRunning();
    }

    @After
    public void tearDown() {
        // Ensure the app or the sandbox did not crash as a result of the test.
        assertAppIsRunning();
        assertAppSandboxIsRunning();
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

    private static void assertAppLaunchSuccessful(String output) {
        assertThat(output).contains("LaunchState: COLD");
    }

    private static void assertAppIsRunning() {
        assertWithMessage("SocketApp is not running")
                .that(SystemUtil.runShellCommand("ps -A"))
                .contains("com.android.socketapp\n");
    }

    private static void assertAppSandboxIsRunning() {
        assertWithMessage("SocketApp is not running")
                .that(SystemUtil.runShellCommand("ps -A"))
                .contains("com.android.socketapp_sdk_sandbox\n");
    }
}
