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
package com.android.adservices.shared.testing.device;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.testing.HostSideTestCase;
import com.android.adservices.shared.testing.TestDeviceHelper;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class HostSideDeviceGatewayTest extends HostSideTestCase {

    private final HostSideDeviceGateway mDeviceGateway = new HostSideDeviceGateway();

    @Test
    public void testGetSdkLevel() {
        var level = mDeviceGateway.getSdkLevel();

        expect.withMessage("getSdkLevel()").that(level).isNotNull();
        expect.withMessage("level.getLevel()")
                .that(level.getLevel())
                .isEqualTo(TestDeviceHelper.getApiLevel());
    }

    @Test
    public void testRunShellCommandRwe_nullInput() {
        assertThrows(NullPointerException.class, () -> mDeviceGateway.runShellCommandRwe(null));
    }

    // TODO(b/368682447, b/324491698): tests below should mock ITestDevice instead, so they don't
    // rely on the command implementation

    @Test
    public void testRunShellCommandRwe_withFormatting_noErr() {
        ShellCommandInput input = new ShellCommandInput("echo %s %s %s!", "I", "am", "Groot");

        var output = mDeviceGateway.runShellCommandRwe(input);

        assertWithMessage("output").that(output).isNotNull();

        expect.withMessage("getOut()").that(output.getOut()).isEqualTo("I am Groot!");
        expect.withMessage("getErr()").that(output.getErr()).isEmpty();
    }

    @Test
    public void testRunShellCommandRwe_noFormatting_noErr() {
        ShellCommandInput input = new ShellCommandInput("echo I am Groot!");

        var output = mDeviceGateway.runShellCommandRwe(input);

        assertWithMessage("output").that(output).isNotNull();
        expect.withMessage("getOut()").that(output.getOut()).isEqualTo("I am Groot!");
        expect.withMessage("getErr()").that(output.getErr()).isEmpty();
    }

    @Test
    public void testRunShellCommandRwe_withFormatting_errOnly() {
        ShellCommandInput input = new ShellCommandInput("Homer says: %s!", "DOH");

        var output = mDeviceGateway.runShellCommandRwe(input);

        assertWithMessage("output").that(output).isNotNull();
        expect.withMessage("getOut()").that(output.getOut()).isEmpty();
        expect.withMessage("getErr()")
                .that(output.getErr())
                .isEqualTo("/system/bin/sh: Homer: inaccessible or not found");
    }

    @Test
    public void testRunShellCommandRwe_noFormatting_errOnly() {
        ShellCommandInput input = new ShellCommandInput("Homer says: DOH!");

        var output = mDeviceGateway.runShellCommandRwe(input);

        assertWithMessage("output").that(output).isNotNull();
        expect.withMessage("getOut()").that(output.getOut()).isEmpty();
        expect.withMessage("getErr()")
                .that(output.getErr())
                .isEqualTo("/system/bin/sh: Homer: inaccessible or not found");
    }

    // TODO(b/368682447, b/324491698): add tests for both stdout and stderr
}
