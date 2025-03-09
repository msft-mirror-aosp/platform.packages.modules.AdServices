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

import com.android.adservices.shared.testing.AndroidSdk.Level;
import com.android.adservices.shared.testing.ConsoleLogger;
import com.android.adservices.shared.testing.TestDeviceHelper;
import com.android.adservices.shared.testing.TestDeviceHelper.DeviceUnavailableException;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.util.CommandResult;

import java.util.Objects;

public final class HostSideDeviceGateway extends AbstractDeviceGateway {

    public HostSideDeviceGateway() {
        super(ConsoleLogger.getInstance());
    }

    @Override
    public Level getSdkLevel() {
        return Level.forLevel(TestDeviceHelper.getApiLevel());
    }

    @Override
    public ShellCommandOutput runShellCommandRwe(ShellCommandInput input) {
        Objects.requireNonNull(input, "input cannot be null");
        var device = TestDeviceHelper.getTestDevice();
        String cmd = input.getCommand();
        CommandResult result;

        try {
            result = device.executeShellV2Command(cmd);
        } catch (DeviceNotAvailableException e) {
            // TODO(b/368682447, b/324491698): should unit test this exception as well
            throw new DeviceUnavailableException(e);
        }
        String out = result.getStdout() != null ? result.getStdout().trim() : "";
        String err = result.getStderr() != null ? result.getStderr().trim() : "";

        return new ShellCommandOutput.Builder().setOut(out).setErr(err).build();
    }
}
