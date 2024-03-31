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

import com.google.common.base.Supplier;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.Objects;

/** Helper to run the AdServices service side shell command and return output and error. */
public abstract class AbstractAdServicesShellCommandHelper {
    protected static final String TAG = "AdServicesShellCommand";

    @VisibleForTesting
    static final String SHELL_ACTIVITY_NAME = "com.android.adservices.shell.ShellCommandActivity";

    private static final String SHELL_ACTIVITY_INTENT =
            "android.adservices.BACK_COMPACT_SHELL_COMMAND";

    private static final String INVALID_COMMAND_OUTPUT = "Unknown command:";

    private static final String CMD_ARGS = "cmd-args";
    private static final String GET_RESULT_ARG = "get-result";

    @VisibleForTesting
    static final String ADSERVICES_MANAGER_SERVICE_CHECK = "service check adservices_manager";

    private static final long WAIT_SAMPLE_INTERVAL_MILLIS = 1000;
    private static final long TIMEOUT_ACTIVITY_FINISH_MILLIS = 3000;

    private final Logger mLog;
    private final AbstractDeviceSupportHelper mAdServicesHelper;

    protected AbstractAdServicesShellCommandHelper(
            AbstractDeviceSupportHelper abstractAdServicesHelper, RealLogger logger) {
        mAdServicesHelper = abstractAdServicesHelper;
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
     * <p>For device R,S, we enable and start shell command activity and then call dumpsys to run
     * the shell command. The dumpsys output produces both error and output as part of single
     * string.
     */
    @FormatMethod
    public String runCommand(@FormatString String cmdFmt, @Nullable Object... cmdArgs) {
        int level = getDeviceApiLevel();
        if (level >= AndroidSdk.TM) {
            // For Android T, Check if the service adservices_manager is published or not. If
            // it's not published, use sdk_sandbox to run the shell command.
            String cmd =
                    (level == AndroidSdk.TM && !isAdServicesManagerServicePublished())
                            ? String.format(
                                    "cmd sdk_sandbox adservices %s", String.format(cmdFmt, cmdArgs))
                            : String.format(
                                    "cmd adservices_manager %s", String.format(cmdFmt, cmdArgs));
            String res = runShellCommand(cmd);
            mLog.d("Output for command %s: %s", cmd, res);
            return res;
        }
        return runShellCommandRS(String.format(cmdFmt, cmdArgs));
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
     * <p>For device R,S, we enable and start the shell command activity and then call dumpsys to
     * run the shell command. The dumpsys output produces both error and output as part of single
     * string. We will populate the {@link CommandResult} {@code out} field with this string. The
     * caller would need to infer from the {@code out} field whether it's actually standard output
     * or error.
     */
    @FormatMethod
    public CommandResult runCommandRwe(@FormatString String cmdFmt, @Nullable Object... cmdArgs) {
        int level = getDeviceApiLevel();
        if (level >= AndroidSdk.TM) {
            // For Android T, Check if the service adservices_manager is published or not. If
            // it's not published, use sdk_sandbox to run the shell command.
            // For Android T, Check if the service adservices_manager is published or not. If
            // it's not published, use sdk_sandbox to run the shell command.
            String cmd =
                    (level == AndroidSdk.TM && !isAdServicesManagerServicePublished())
                            ? String.format(
                                    "cmd sdk_sandbox adservices %s", String.format(cmdFmt, cmdArgs))
                            : String.format(
                                    "cmd adservices_manager %s", String.format(cmdFmt, cmdArgs));
            CommandResult res = runShellCommandRwe(cmd);
            mLog.d("Output for command %s: %s", cmd, res);
            return res;
        }

        String res = runShellCommandRS(String.format(cmdFmt, cmdArgs));
        return new CommandResult(res, "");
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

    private String runShellCommandRS(String cmd) {
        String[] argsList = cmd.split(" ");
        String args = String.join(",", argsList);
        String res = runShellCommand(startShellActivity(args));
        mLog.d("Output for command %s: %s", startShellActivity(args), res);

        String componentName =
                String.format(
                        "%s/%s", mAdServicesHelper.getAdServicesPackageName(), SHELL_ACTIVITY_NAME);
        res = runShellCommand(runDumpsysShellCommand(componentName));
        mLog.d("Output for command %s: %s", runDumpsysShellCommand(componentName), res);
        String out = parseResultFromDumpsys(res);

        checkShellCommandActivityFinished(componentName);
        return out;
    }

    /* Parses the output from dumpsys.

       Sample dumpsys output:
        TASK 10145:com.google.android.ext.services id=13 userId=0
        ACTIVITY com.google.android.ext.services/com.android.adservices.shell.ShellCommandActivity
        hello

       parsed Output: hello
    */
    @VisibleForTesting
    String parseResultFromDumpsys(String res) {
        String separator = "\n";
        String[] splitStr = res.split(separator);
        if (splitStr.length < 3) {
            return res;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < splitStr.length; i++) {
            sb = i > 2 ? sb.append("\n" + splitStr[i]) : sb.append(splitStr[i]);
        }
        return sb.toString();
    }

    @VisibleForTesting
    String runDumpsysShellCommand(String componentName) {
        return String.format("dumpsys activity %s cmd %s", componentName, GET_RESULT_ARG);
    }

    private String startShellActivity(String args) {
        return String.format(
                "am start -W -a %s --esa %s %s", SHELL_ACTIVITY_INTENT, CMD_ARGS, args);
    }

    boolean isAdServicesManagerServicePublished() {
        String out = runShellCommand(ADSERVICES_MANAGER_SERVICE_CHECK);
        return !out.contains("not found");
    }

    private void checkShellCommandActivityFinished(String componentName) {
        mLog.d("Checking if ShellCommandActivity is finished");
        tryWaitForSuccess(
                () -> {
                    String res = runShellCommand(runDumpsysShellCommand(componentName));
                    mLog.d("Output for command %s: %s", runDumpsysShellCommand(componentName), res);
                    return res.contains(INVALID_COMMAND_OUTPUT);
                },
                "Failed to finish ShellCommandActivity",
                TIMEOUT_ACTIVITY_FINISH_MILLIS);
    }

    // TODO(b/328107990): Create a generic method and move this to a CTS helper class.
    private void tryWaitForSuccess(
            Supplier<Boolean> successCondition, String failureMessage, long maxTimeoutMillis) {
        long epoch = System.currentTimeMillis();
        while (System.currentTimeMillis() - epoch <= maxTimeoutMillis) {
            try {
                mLog.d("Sleep for %dms before we check for result", WAIT_SAMPLE_INTERVAL_MILLIS);
                Thread.sleep(WAIT_SAMPLE_INTERVAL_MILLIS);
                if (successCondition.get()) {
                    mLog.d("ShellCommandActivity is finished");
                    return;
                }
            } catch (InterruptedException e) {
                mLog.e("Thread interrupted, %s", failureMessage);
                Thread.currentThread().interrupt();
            }
        }
        mLog.e("Timeout %dms happened, %s", maxTimeoutMillis, failureMessage);
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
