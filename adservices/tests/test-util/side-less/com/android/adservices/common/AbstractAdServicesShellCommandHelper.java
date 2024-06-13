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

import com.android.adservices.shared.testing.AndroidSdk;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.Logger.RealLogger;
import com.android.adservices.shared.testing.Nullable;
import com.android.adservices.shared.testing.shell.CommandResult;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.base.Supplier;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

/** Helper to run the AdServices service side shell command and return output and error. */
public abstract class AbstractAdServicesShellCommandHelper {
    protected static final String TAG = "AdServicesShellCommand";

    @VisibleForTesting
    static final String SHELL_ACTIVITY_NAME = "com.android.adservices.shell.ShellCommandActivity";

    private static final String SHELL_ACTIVITY_INTENT =
            "android.adservices.BACK_COMPACT_SHELL_COMMAND";

    private static final String INVALID_COMMAND_OUTPUT = "Unknown command:";

    private static final String SHELL_COMMAND_ACTIVITY_DUMP_STR = "-- ShellCommandActivity dump --";
    private static final String COMMAND_OUT = "CommandOut:";
    private static final String COMMAND_ERR = "CommandErr:";
    private static final String COMMAND_STATUS = "CommandStatus:";
    private static final String STATUS_FINISHED = "FINISHED";

    private static final String CMD_ARGS = "cmd-args";
    private static final String GET_RESULT_ARG = "get-result";

    @VisibleForTesting
    static final String ADSERVICES_MANAGER_SERVICE_CHECK = "service check adservices_manager";

    private static final long WAIT_SAMPLE_INTERVAL_MILLIS = 1000;
    private static final long TIMEOUT_ACTIVITY_FINISH_MILLIS = 3000;

    // Max number of times we run the get-result shell command if the command is still running.
    private static final long GET_RESULT_COMMAND_RETRY = 4;

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
        CommandResult commandResult = runShellCommandRS(String.format(cmdFmt, cmdArgs));
        return commandResult.getOut();
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

        return runShellCommandRS(String.format(cmdFmt, cmdArgs));
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

    private CommandResult runShellCommandRS(String cmd) {
        String[] argsList = cmd.split("\\s+");
        String args = String.join(",", argsList);
        String res = runShellCommand(startShellActivity(args));
        mLog.d("Output for command %s: %s", startShellActivity(args), res);

        String componentName =
                String.format(
                        "%s/%s", mAdServicesHelper.getAdServicesPackageName(), SHELL_ACTIVITY_NAME);
        CommandResult commandRes = runGetResultShellCommand(componentName);

        checkShellCommandActivityFinished(componentName);
        return commandRes;
    }

    /* Parses the output from dumpsys.

       Sample dumpsys output:
        TASK 10145:com.google.android.ext.services id=13 userId=0
        ACTIVITY com.google.android.ext.services/com.android.adservices.shell.ShellCommandActivity
        -- ShellCommandActivity dump --
        CommandStatus: FINISHED
        CommandRes: 0
        CommandOut:
          hello
       parsed Output: CommandResult(hello,"")
    */

    @VisibleForTesting
    CommandResult parseResultFromDumpsys(String res) {
        String separator = "\n";
        String[] splitStr = res.split(separator);
        int len = splitStr.length;

        boolean activityDumpPresent = false;
        String out = "";
        String err = "";
        String commandStatus = STATUS_FINISHED;
        for (int i = 0; i < len; i++) {
            if (splitStr[i].equals(SHELL_COMMAND_ACTIVITY_DUMP_STR)) {
                activityDumpPresent = true;
            } else if (activityDumpPresent && splitStr[i].startsWith(COMMAND_STATUS)) {
                commandStatus = splitStr[i].substring(COMMAND_STATUS.length()).strip();
            } else {
                if (activityDumpPresent && splitStr[i].equals(COMMAND_OUT)) {
                    i++;
                    StringBuilder outBuilder = new StringBuilder();
                    for (; i < len && splitStr[i].startsWith("  "); i++) {
                        if (splitStr[i].length() > 2) {
                            outBuilder.append(splitStr[i].substring(2));
                        }
                        outBuilder.append('\n');
                    }
                    out = outBuilder.toString().strip();
                }

                if (i < len && activityDumpPresent && splitStr[i].equals(COMMAND_ERR)) {
                    i++;
                    StringBuilder errBuilder = new StringBuilder();
                    for (; i < len && splitStr[i].startsWith("  "); i++) {
                        if (splitStr[i].length() > 2) {
                            errBuilder.append(splitStr[i].substring(2));
                        }
                        errBuilder.append("\n");
                    }
                    err = errBuilder.toString().strip();
                }
            }
        }

        // Return original input if activity dump string not present.
        if (!activityDumpPresent) {
            return new CommandResult(res, "", commandStatus);
        }
        return new CommandResult(out, err, commandStatus);
    }

    @VisibleForTesting
    String getDumpsysGetResultShellCommand(String componentName) {
        return String.format("dumpsys activity %s cmd %s", componentName, GET_RESULT_ARG);
    }

    private CommandResult runGetResultShellCommand(String componentName) {
        CommandResult commandRes = new CommandResult("", "");
        for (int i = 0; i < GET_RESULT_COMMAND_RETRY; i++) {
            String res = runShellCommand(getDumpsysGetResultShellCommand(componentName));
            mLog.d(
                    "Output for command %s running %d times: %s ",
                    getDumpsysGetResultShellCommand(componentName), i + 1, res);
            commandRes = parseResultFromDumpsys(res);
            if (!commandRes.isCommandRunning()) {
                return commandRes;
            }
        }
        return commandRes;
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
                    String res = runShellCommand(getDumpsysGetResultShellCommand(componentName));
                    mLog.d(
                            "Output for command %s: %s",
                            getDumpsysGetResultShellCommand(componentName), res);
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
}
