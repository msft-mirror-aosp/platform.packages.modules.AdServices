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
package com.android.adservices.common;

import com.android.adservices.common.AbstractAdServicesShellCommandHelper.CommandResult;
import com.android.adservices.shared.testing.ConsoleLogger;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.Nullable;
import com.android.adservices.shared.testing.TestDeviceHelper;
import com.android.adservices.shared.testing.TestDeviceHelper.DeviceUnavailableException;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

/**
 * TODO(b/338425309): split out from TestDeviceHelper to remove adservices-specific dependencies -
 * should refactor the latter instead.
 */
public final class AdServicesTestDeviceHelper {

    /** Intent used to launch AdServices settings */
    public static final String ADSERVICES_SETTINGS_INTENT = "android.adservices.ui.SETTINGS";

    private static final Logger sLogger =
            new Logger(ConsoleLogger.getInstance(), TestDeviceHelper.class);

    // cmdFmt must be final because it's being passed to a method taking @FormatString
    /**
     * Executes AdServices shell command and returns the standard output and standard error wrapped
     * in a {@link CommandResult}.
     */
    @FormatMethod
    public static CommandResult runShellCommandRwe(
            @FormatString final String cmdFmt, @Nullable Object... cmdArgs) {
        return runShellCommandRwe(TestDeviceHelper.getTestDevice(), cmdFmt, cmdArgs);
    }

    /**
     * Executes AdServices shell command and returns the standard output and standard error wrapped
     * in a {@link CommandResult}.
     */
    @FormatMethod
    public static CommandResult runShellCommandRwe(
            ITestDevice device, @FormatString String cmdFmt, @Nullable Object... cmdArgs) {
        String cmd = String.format(cmdFmt, cmdArgs);
        com.android.tradefed.util.CommandResult result;
        try {
            result = device.executeShellV2Command(cmd);
        } catch (DeviceNotAvailableException e) {
            throw new DeviceUnavailableException(e);
        }
        sLogger.d("runShellCommandRwe(%s): %s", cmd, result);
        return asCommandResult(result);
    }

    private static CommandResult asCommandResult(com.android.tradefed.util.CommandResult input) {
        String out = input.getStdout() != null ? input.getStdout().strip() : "";
        String err = input.getStderr() != null ? input.getStderr().strip() : "";
        return new CommandResult(out, err);
    }

    private AdServicesTestDeviceHelper() {
        throw new UnsupportedOperationException("Provides only static methods");
    }
}
