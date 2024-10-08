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
package com.android.adservices.shared.testing;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.adservices.shared.testing.shell.CommandResult;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.INativeDevice;
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
public final class TestDeviceHelper {

    /** Same as android.content.Intent */
    public static final String ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED";

    private static final Logger sLogger =
            new Logger(ConsoleLogger.getInstance(), TestDeviceHelper.class);

    private static final ThreadLocal<ITestDevice> sDevice = new ThreadLocal<>();

    /** Sets the singleton. */
    public static void setTestDevice(ITestDevice device) {
        Objects.requireNonNull(device, "device cannot be null");
        sLogger.i("Setting singleton as %s", device);
        sDevice.set(device);
    }

    /** Gets the singleton. */
    public static ITestDevice getTestDevice() {
        ITestDevice device = sDevice.get();
        if (device == null) {
            throw new IllegalStateException(
                    "setTestDevice() not set yet - test must either explicitly call it @Before, or"
                            + " extend AdServicesHostSideTestCase");
        }
        return device;
    }

    // cmdFmt must be final because it's being passed to a method taking @FormatString

    /**
     * Executes AdServices shell command and returns the standard output, using the singleton
     * device.
     */
    @FormatMethod
    public static String runShellCommand(
            @FormatString final String cmdFmt, @Nullable Object... cmdArgs) {
        return runShellCommand(getTestDevice(), cmdFmt, cmdArgs);
    }

    /**
     * Executes AdServices shell command and returns the standard output, using the given device.
     */
    @FormatMethod
    public static String runShellCommand(
            ITestDevice device, @FormatString String cmdFmt, @Nullable Object... cmdArgs) {
        String cmd = String.format(cmdFmt, cmdArgs);
        String result;
        try {
            result = device.executeShellCommand(cmd);
        } catch (DeviceNotAvailableException e) {
            throw new DeviceUnavailableException(e);
        }
        sLogger.d("runShellCommand(%s): %s", cmd, result);
        return result;
    }

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

    /** Gets the device API level. */
    public static int getApiLevel() {
        int apiLevel = call(INativeDevice::getApiLevel);

        if (apiLevel == INativeDevice.UNKNOWN_API_LEVEL) {
            throw new DeviceUnavailableException("Unable to get API level from device");
        }

        return apiLevel;
    }

    /** Gets the given system property. */
    public static String getProperty(String name) {
        return call(device -> device.getProperty(name));
    }

    /** Sets the given system property. */
    public static void setProperty(String name, String value) {
        run(device -> device.setProperty(name, value));
    }

    /** Starts an activity (without specifying the user). */
    public static void startActivity(String intent) {
        String startActivityMsg = runShellCommand("am start -a %s", intent);
        assertWithMessage("result of starting %s", intent)
                .that(startActivityMsg)
                .doesNotContain("Error: Activity not started, unable to resolve Intent");
    }

    /**
     * Starts an activity and ensures that the activity has fully loaded and completed. (By using
     * the flag {@code -w})
     *
     * @param packageName the package name of the app to launch the activity on
     * @param className the class name of the activity to launch
     */
    public static void startActivityWaitUntilCompletion(String packageName, String className) {
        runShellCommand("am start -W -n %s/.%s", packageName, className);
    }

    /** Enable the given component and return the result of the shell command */
    public static String enableComponent(String packageName, String className) {
        return runShellCommand("pm enable %s/%s", packageName, className);
    }

    /**
     * Enable the given component for the given userId only and return the result of the shell
     * command
     */
    public static String enableComponent(String packageName, String className, int userId) {
        return runShellCommand("pm enable --user %d %s/%s", userId, packageName, className);
    }

    /**
     * @return true if intent is in the pm list of active receivers, false otherwise
     */
    public static boolean isActiveReceiver(String intent, String packageName, String className) {
        String receivers = runShellCommand("pm query-receivers --components -a %s", intent);

        String packageAndFullClass = packageName + "/" + className;
        if (receivers.contains(packageAndFullClass)) {
            return true;
        }

        // check for the abbreviated name, with the package name substring with the class name
        // replaced by "."
        if (className.startsWith(packageName)) {
            String packageAndShortClass =
                    packageName + "/" + className.replaceFirst(packageName, ".");
            return receivers.contains(packageAndShortClass);
        }

        return false;
    }

    public static final class DeviceUnavailableException extends IllegalStateException {
        public DeviceUnavailableException(DeviceNotAvailableException cause) {
            super(cause);
        }

        public DeviceUnavailableException(String cause) {
            super(cause);
        }
    }

    /**
     * Runs the given command without throwing a checked exception.
     *
     * @throws DeviceUnavailableException if device is not available.
     */
    public static <T> T call(Command<T> command) {
        try {
            return command.run(getTestDevice());
        } catch (DeviceNotAvailableException e) {
            throw new DeviceUnavailableException(e);
        }
    }

    /**
     * Runs the given command without throwing a checked exception.
     *
     * @throws DeviceUnavailableException if device is not available.
     */
    public static void run(VoidCommand command) {
        try {
            command.run(getTestDevice());
        } catch (DeviceNotAvailableException e) {
            throw new DeviceUnavailableException(e);
        }
    }

    /**
     * Abstraction for a command that returns a result.
     *
     * @param <T> type of command
     */
    public interface Command<T> {
        /** Run Forrest, run! */
        T run(ITestDevice device) throws DeviceNotAvailableException;
    }

    /** Abstraction for a command that does not return a result. */
    public interface VoidCommand {
        /** Run Forrest, run! */
        void run(ITestDevice device) throws DeviceNotAvailableException;
    }

    private TestDeviceHelper() {
        throw new UnsupportedOperationException("Provides only static methods");
    }
}
