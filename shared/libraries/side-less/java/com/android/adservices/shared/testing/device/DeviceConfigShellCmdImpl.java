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
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.Logger.RealLogger;

import java.util.Locale;
import java.util.Objects;

public final class DeviceConfigShellCmdImpl implements DeviceConfig {

    private static final String CMD_DEVICE_CONFIG = "device_config";

    private final Logger mLog;

    private final DeviceGateway mGateway;

    public DeviceConfigShellCmdImpl(RealLogger realLogger, DeviceGateway gateway) {
        mGateway = Objects.requireNonNull(gateway, "gateway cannot be null");
        mLog = new Logger(realLogger, getClass());
    }

    @Override
    public void setSyncDisabledMode(SyncDisabledModeForTest mode) {
        Objects.requireNonNull(mode, "mode cannot be null");

        Level sdkLevel = mGateway.getSdkLevel();
        if (!sdkLevel.isAtLeast(Level.S)) {
            mLog.w(
                    "setSyncDisabledMode(%s): ignoring on device with SDK level %d",
                    mode, sdkLevel.getLevel());
            return;
        }

        ShellCommandInput input =
                new ShellCommandInput(
                        "%s set_sync_disabled_for_tests %s",
                        CMD_DEVICE_CONFIG, mode.getShellCommandString());
        ShellCommandOutput output = mGateway.runShellCommandRwe(input);

        if (!output.getOut().isEmpty() || !output.getErr().isEmpty()) {
            throw new InvalidShellCommandResultException(input, output);
        }
    }

    @Override
    public SyncDisabledModeForTest getSyncDisabledMode() {
        Level sdkLevel = mGateway.getSdkLevel();
        if (!sdkLevel.isAtLeast(Level.S)) {
            mLog.w(
                    "getSyncDisabledMode(): returning UNSUPPORTED on device with SDK level %d",
                    sdkLevel.getLevel());
            return SyncDisabledModeForTest.UNSUPPORTED;
        }

        String subCmd =
                sdkLevel.isAtLeast(Level.T)
                        ? "get_sync_disabled_for_tests"
                        : "is_sync_disabled_for_tests";
        ShellCommandInput input = new ShellCommandInput("%s %s", CMD_DEVICE_CONFIG, subCmd);
        ShellCommandOutput output = mGateway.runShellCommandRwe(input);

        String result = null;
        try {
            result = output.getOut();
            return DeviceConfig.SyncDisabledModeForTest.valueOf(result.toUpperCase(Locale.ENGLISH));
        } catch (Exception e) {
            throw new InvalidShellCommandResultException(input, output);
        }
    }
}
