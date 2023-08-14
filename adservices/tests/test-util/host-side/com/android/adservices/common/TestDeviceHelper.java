/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.Objects;

/**
 * Provides static that is statically shared between the test artifacts.
 *
 * <p>This class is mostly needed because often the {@code ITestDevice} is not available when such
 * artifacts are instantiated, but it also provides other {@code ITestDevice}-related helpers.
 */
final class TestDeviceHelper {

    private static final Logger sLogger =
            new Logger(ConsoleLogger.getInstance(), TestDeviceHelper.class);

    private static final ThreadLocal<ITestDevice> sDevice = new ThreadLocal<>();

    public static void setTestDevice(ITestDevice device) {
        sDevice.set(Objects.requireNonNull(device));
    }

    public static ITestDevice getTestDevice() {
        ITestDevice device = sDevice.get();
        if (device == null) {
            throw new IllegalStateException(
                    "setTestDevice() not set yet - test must either explicitly call it @Before, or"
                            + " extend AdServicesHostSideTestCase");
        }
        return device;
    }

    @FormatMethod
    public static String runShellCommand(@FormatString String cmdFmt, @Nullable Object... cmdArgs)
            throws DeviceNotAvailableException {
        return runShellCommand(getTestDevice(), cmdFmt, cmdArgs);
    }

    @FormatMethod
    public static String runShellCommand(
            ITestDevice device, @FormatString String cmdFmt, @Nullable Object... cmdArgs)
            throws DeviceNotAvailableException {
        String cmd = String.format(cmdFmt, cmdArgs);
        String result = device.executeShellCommand(cmd);
        sLogger.d("runShellCommand(%s): %s", cmd, result);
        return result;
    }

    private TestDeviceHelper() {
        throw new UnsupportedOperationException("Provides only static methods");
    }
}
