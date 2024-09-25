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

import android.os.Build;
import android.platform.test.annotations.DisabledOnRavenwood;

import com.android.adservices.shared.SharedUnitTestCase;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.RequiresSdkRange;

import org.junit.Test;

public final class DeviceGatewayImplTest extends SharedUnitTestCase {

    private final DeviceGatewayImpl mDeviceGateway = new DeviceGatewayImpl();

    @Test
    public void testGetSdkLevel() {
        var level = mDeviceGateway.getSdkLevel();

        expect.withMessage("getSdkLevel()").that(level).isNotNull();
        expect.withMessage("level.getLevel()")
                .that(level.getLevel())
                .isEqualTo(Build.VERSION.SDK_INT);
    }

    @Test
    public void testRunShellCommandRwe_nullInput() {
        assertThrows(NullPointerException.class, () -> mDeviceGateway.runShellCommandRwe(null));
    }

    // TODO(b/368682447, b/324491698): tests below should mock UiAutomation instead, so they don't
    // rely on the command implementation

    @Test
    // TODO(b/335935200): figure out how to use it on Ravenwood
    @DisabledOnRavenwood(reason = "Instrumentation.getUiAutomation() not supported")
    public void testRunShellCommandRwe_withFormatting_noErr() {
        ShellCommandInput input = new ShellCommandInput("echo %s %s %s!", "I", "am", "Groot");

        var output = mDeviceGateway.runShellCommandRwe(input);

        assertWithMessage("output").that(output).isNotNull();

        expect.withMessage("getOut()").that(output.getOut()).isEqualTo("I am Groot!");
        expect.withMessage("getErr()").that(output.getErr()).isEmpty();
    }

    @Test
    // TODO(b/335935200): figure out how to use it on Ravenwood
    @DisabledOnRavenwood(reason = "Instrumentation.getUiAutomation() not supported")
    public void testRunShellCommandRwe_noFormatting_noErr() {
        ShellCommandInput input = new ShellCommandInput("echo I am Groot!");

        var output = mDeviceGateway.runShellCommandRwe(input);

        assertWithMessage("output").that(output).isNotNull();
        expect.withMessage("getOut()").that(output.getOut()).isEqualTo("I am Groot!");
        expect.withMessage("getErr()").that(output.getErr()).isEmpty();
    }

    // TODO(b/368704903): commands used below are inconsistent with host-side tests as binder call
    // would fail (and reading output hangs) if using a command that does not exist
    @Test
    @RequiresSdkLevelAtLeastT(
            reason = "UIAutomation.executeShellCommandRwe() is only available on T+")
    // TODO(b/335935200): figure out how to use it on Ravenwood
    @DisabledOnRavenwood(reason = "Instrumentation.getUiAutomation() not supported")
    public void testRunShellCommandRwe_withFormatting_errOnly() {
        ShellCommandInput input = new ShellCommandInput("device_config %s", "DOH");

        var output = mDeviceGateway.runShellCommandRwe(input);

        assertWithMessage("output").that(output).isNotNull();
        expect.withMessage("getOut()").that(output.getOut()).isEmpty();
        expect.withMessage("getErr()").that(output.getErr()).isEqualTo("Invalid command: DOH");
    }

    @Test
    @RequiresSdkLevelAtLeastT(
            reason = "UIAutomation.executeShellCommandRwe() is only available on T+")
    // TODO(b/335935200): figure out how to use it on Ravenwood
    @DisabledOnRavenwood(reason = "Instrumentation.getUiAutomation() not supported")
    public void testRunShellCommandRwe_noFormatting_errOnly() {
        ShellCommandInput input = new ShellCommandInput("device_config DOH");

        var output = mDeviceGateway.runShellCommandRwe(input);

        assertWithMessage("output").that(output).isNotNull();
        expect.withMessage("getOut()").that(output.getOut()).isEmpty();
        expect.withMessage("getErr()").that(output.getErr()).isEqualTo("Invalid command: DOH");
    }

    @Test
    @RequiresSdkRange(
            atMost = Build.VERSION_CODES.S_V2,
            reason = "Uses UIAutomation.executeShellCommand(), which doesn't support stderr")
    // TODO(b/335935200): figure out how to use it on Ravenwood
    @DisabledOnRavenwood(reason = "Instrumentation.getUiAutomation() not supported")
    public void testRunShellCommandRwe_withFormatting_errOnly_SMinus() {
        ShellCommandInput input = new ShellCommandInput("device_config %s", "DOH");

        var output = mDeviceGateway.runShellCommandRwe(input);

        assertWithMessage("output").that(output).isNotNull();
        expect.withMessage("getOut()").that(output.getOut()).isEmpty();
        expect.withMessage("getErr()").that(output.getErr()).isEmpty();
    }

    @Test
    @RequiresSdkRange(
            atMost = Build.VERSION_CODES.S_V2,
            reason = "Uses UIAutomation.executeShellCommand(), which doesn't support stderr")
    // TODO(b/335935200): figure out how to use it on Ravenwood
    @DisabledOnRavenwood(reason = "Instrumentation.getUiAutomation() not supported")
    public void testRunShellCommandRwe_noFormatting_errOnly_SMinus() {
        ShellCommandInput input = new ShellCommandInput("device_config DOH");

        var output = mDeviceGateway.runShellCommandRwe(input);

        assertWithMessage("output").that(output).isNotNull();
        expect.withMessage("getOut()").that(output.getOut()).isEmpty();
        expect.withMessage("getErr()").that(output.getErr()).isEmpty();
    }
    // TODO(b/368682447, b/324491698): add tests for both stdout and stderr
}
