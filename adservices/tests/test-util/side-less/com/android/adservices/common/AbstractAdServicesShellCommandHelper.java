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

import com.android.adservices.common.Logger.RealLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.Objects;

/** Helper to run the AdServices service side shell command and return output and error. */
public abstract class AbstractAdServicesShellCommandHelper {
    protected static final String TAG = "AdServicesShellCommand";

    @VisibleForTesting
    static final String START_SHELL_COMMAND_SERVICE =
            "am start-foreground-service -a android.adservices.SHELL_COMMAND_SERVICE";

    @VisibleForTesting
    static final String ADSERVICES_MANAGER_SERVICE_CHECK = "service check adservices_manager";

    private static final String FOREGROUND_SERVICE_ERROR =
            "Failed to start shell command foreground service";

    private static final int SLEEP_INTERVAL_IN_MILLIS = 2000;
    private static final int MAX_WAIT_TIME_IN_MILLIS = 10000;

    private final Logger mLog;

    protected AbstractAdServicesShellCommandHelper(RealLogger logger) {
        mLog = new Logger(logger, TAG);
    }

    /**
     * Executes AdServices shell command and returns the standard output.
     *
     * <p>Before running the shell command, ensure {@code adservices_shell_command_enabled} flag is
     * enabled.
     *
     * <p>For device T+, adservices_manager binds to the shell command service, runs the shell
     * command and returns standard output.
     *
     * <p>For device R,S, we start the shell command service and then call dumpsys to run the shell
     * command. The dumpsys output produces both error and output as part of single string.
     */
    @FormatMethod
    public String runCommand(@FormatString String cmdFmt, @Nullable Object... cmdArgs) {
        int level = getDeviceApiLevel();
        if (level >= AndroidSdk.TM) {
            // For Android T, Check if the service adservices_manager is published or not. If
            // it's not published, use sdk_sandbox to run the shell command.
            if (level == AndroidSdk.TM && !isAdServicesManagerServicePublished()) {
                return runShellCommand(
                        String.format(
                                "cmd sdk_sandbox adservices %s", String.format(cmdFmt, cmdArgs)));
            }
            return runShellCommand(
                    String.format("cmd adservices_manager %s", String.format(cmdFmt, cmdArgs)));
        }

        if (!startShellCommandForegroundService()) {
            return FOREGROUND_SERVICE_ERROR;
        }

        String res = runShellCommand(runDumpsysShellCommand(String.format(cmdFmt, cmdArgs)));
        String parsedOut = parseResultFromDumpsys(res);
        return parsedOut;
    }

    /**
     * Executes AdServices shell command and returns the standard output and standard error wrapped
     * in a {@link CommandResult}.
     *
     * <p>Before running the shell command, ensure {@code adservices_shell_command_enabled} flag is
     * enabled.
     *
     * <p>For device T+, adservices_manager binds to the shell command service, runs the shell
     * command and returns both standard output and standard error as part of {@link CommandResult}.
     *
     * <p>For device R,S, we start the shell command service and then call dumpsys to run the shell
     * command. The dumpsys output produces both error and output as part of single string. We will
     * populate the {@link CommandResult} {@code out} field with this string. The caller would need
     * to infer from the {@code out} field whether it's actually standard output or error.
     */
    @FormatMethod
    public CommandResult runCommandRwe(@FormatString String cmdFmt, @Nullable Object... cmdArgs) {
        int level = getDeviceApiLevel();
        if (level >= AndroidSdk.TM) {
            // For Android T, Check if the service adservices_manager is published or not. If
            // it's not published, use sdk_sandbox to run the shell command.
            if (level == AndroidSdk.TM && !isAdServicesManagerServicePublished()) {
                return runShellCommandRwe(
                        String.format(
                                "cmd sdk_sandbox adservices %s", String.format(cmdFmt, cmdArgs)));
            }
            return runShellCommandRwe(
                    String.format("cmd adservices_manager %s", String.format(cmdFmt, cmdArgs)));
        }

        if (!startShellCommandForegroundService()) {
            return new CommandResult("", FOREGROUND_SERVICE_ERROR);
        }

        String res = runShellCommand(runDumpsysShellCommand(String.format(cmdFmt, cmdArgs)));
        String parsedOut = parseResultFromDumpsys(res);
        return new CommandResult(parsedOut, "");
    }

    private boolean startShellCommandForegroundService() {
        runShellCommand(START_SHELL_COMMAND_SERVICE);
        for (int i = SLEEP_INTERVAL_IN_MILLIS;
                i <= MAX_WAIT_TIME_IN_MILLIS;
                i += SLEEP_INTERVAL_IN_MILLIS) {
            try {
                Thread.sleep(SLEEP_INTERVAL_IN_MILLIS);
                if (checkShellCommandServiceStarted()) {
                    return true;
                }
            } catch (InterruptedException e) {
                mLog.e("Thread interrupted, current time in millisecond : %d", i);
                Thread.currentThread().interrupt();
            }
        }
        return false;
    }

    /** Executes a shell command and returns the standard output. */
    // TODO(b/324491698): Provide an abstraction for runShellCommand.
    protected abstract String runShellCommand(String cmd);

    /**
     * Executes a shell command and returns the standard output and standard error wrapped in a
     * {@link CommandResult}.
     */
    protected abstract CommandResult runShellCommandRwe(String cmd);

    // TODO(b/324491709): Provide an abstraction for sdk device level.
    /** Gets the device API level. */
    protected abstract int getDeviceApiLevel();

    private boolean checkShellCommandServiceStarted() {
        // Run echo command and check output if service is started or not.
        String out = runShellCommand(String.format(runDumpsysShellCommand("echo hello")));
        return parseResultFromDumpsys(out).contains("hello");
    }

    /* Retrieves the output from dumpsys after 'Client:'. If 'Client:' is missing, we return the
       input String.

       Sample dumpsys output:
        com.google.android.adservices.api/com.android.adservices.shell.AdServicesShellCommandService
         4e0649c pid=5303 user=0
          Client:
            hello

       parsed Output: hello
    */
    @VisibleForTesting
    String parseResultFromDumpsys(String res) {

        String strSeparator = "Client:\n";
        int index = res.indexOf(strSeparator);
        if (index == -1) {
            return res;
        }
        return res.substring(index + strSeparator.length());
    }

    @VisibleForTesting
    String runDumpsysShellCommand(String cmd) {
        return String.format(
                "dumpsys activity service com.google.android.adservices.api/com.android"
                        + ".adservices.shell.AdServicesShellCommandService cmd %s",
                cmd);
    }

    boolean isAdServicesManagerServicePublished() {
        String out = runShellCommand(ADSERVICES_MANAGER_SERVICE_CHECK);
        return !out.contains("not found");
    }

    /** Contains the result of a shell command. */
    public static final class CommandResult {
        private final String mOut;
        private final String mErr;

        public CommandResult(String out, String err) {
            mOut = Objects.requireNonNull(out);
            mErr = Objects.requireNonNull(err);
        }

        public String getOut() {
            return mOut;
        }

        public String getErr() {
            return mErr;
        }

        @Override
        public String toString() {
            return String.format("CommandResult [out=%s, err=%s]", mOut, mErr);
        }
    }
}
