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
package com.android.adservices.shared.testing;

import static com.android.adservices.shared.testing.SdkSandbox.State.DISABLED;
import static com.android.adservices.shared.testing.SdkSandbox.State.ENABLED;
import static com.android.adservices.shared.testing.SdkSandbox.State.UNKNOWN;
import static com.android.adservices.shared.testing.SdkSandbox.State.UNSUPPORTED;

import com.android.adservices.shared.testing.AndroidSdk.Level;
import com.android.adservices.shared.testing.Logger.RealLogger;
import com.android.adservices.shared.testing.device.DeviceGateway;
import com.android.adservices.shared.testing.device.InvalidShellCommandResultException;
import com.android.adservices.shared.testing.device.ShellCommandInput;
import com.android.adservices.shared.testing.device.ShellCommandOutput;

import java.util.Objects;
import java.util.regex.Pattern;

/** Implementation that uses Shell to call {@code cmd sdk_sandbox} commands . */
public final class SdkSandboxShellCmdImpl implements SdkSandbox {

    private static final String SDK_SANDBOX = "sdk_sandbox";
    private static final String SUB_CMD_SET_STATE = "set-state";
    private static final String KILLSWITCH_ENABLED_DUMP_PREFIX = "Killswitch enabled: ";
    private static final Pattern KILLSWITCH_ENABLED_DUMP_PATTERN =
            Pattern.compile(".*" + KILLSWITCH_ENABLED_DUMP_PREFIX + "(?<ks>.*)");

    private final Logger mLog;
    private final DeviceGateway mGateway;

    public SdkSandboxShellCmdImpl(RealLogger realLogger, DeviceGateway gateway) {
        mGateway = Objects.requireNonNull(gateway, "gateway cannot be null");
        mLog = new Logger(realLogger, getClass());
    }

    @Override
    public State getState() {
        if (!isSupported()) {
            return UNSUPPORTED;
        }

        // NOTE: ideally there should be `cmd skd_sandbox get-state`, but it's probably not worth to
        // add one just for this purpose when that info is available through dumpsys...
        String dump = mGateway.runShellCommand("dumpsys %s", SDK_SANDBOX);
        for (String line : dump.split("\n")) {
            var matcher = KILLSWITCH_ENABLED_DUMP_PATTERN.matcher(line);
            if (matcher.matches()) {
                mLog.d("getState(): found line to be parsed: %s", line);
                String killSwitchEnabled = matcher.group("ks");
                return Boolean.valueOf(killSwitchEnabled) ? DISABLED : ENABLED;
            }
        }
        mLog.w(
                "getState(): returning UNKNOWN as dump output didn't have line containing '%s:"
                        + " BOOLEAN':\n"
                        + "%s",
                KILLSWITCH_ENABLED_DUMP_PREFIX, dump);
        return UNKNOWN;
    }

    @Override
    public SdkSandboxShellCmdImpl setState(State state) {
        Objects.requireNonNull(state, "state cannot be null");
        if (!state.isSettable()) {
            throw new IllegalArgumentException("invalid state: " + state);
        }
        if (!isSupported()) {
            mLog.d("setState(%s): ignoring when not supported", state);
            return this;
        }

        ShellCommandInput input =
                new ShellCommandInput(
                        "cmd %s %s %s", SDK_SANDBOX, SUB_CMD_SET_STATE, asCmdArg(state));
        ShellCommandOutput output = mGateway.runShellCommandRwe(input);
        if (!output.getOut().isEmpty()) {
            throw new InvalidShellCommandResultException(input, output);
        }
        return this;
    }

    private boolean isSupported() {
        return mGateway.getSdkLevel().isAtLeast(Level.T);
    }

    private static String asCmdArg(State state) {
        switch (state) {
            case ENABLED:
                return "--enabled";
            case DISABLED:
                return "--reset";
            default:
                throw new IllegalArgumentException("Invalid state: " + state);
        }
    }
}
