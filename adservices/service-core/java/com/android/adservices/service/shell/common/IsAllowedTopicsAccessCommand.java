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

package com.android.adservices.service.shell.common;

import static com.android.adservices.service.shell.AdServicesShellCommandHandler.TAG;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_IS_ALLOWED_TOPICS_ACCESS;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_SUCCESS;

import android.util.Log;

import com.android.adservices.service.common.AppManifestConfigHelper;
import com.android.adservices.service.shell.AbstractShellCommand;
import com.android.adservices.service.shell.ShellCommandResult;

import java.io.PrintWriter;

/**
 * Command to check if the given enrollment id is allowed to use the Topics APIs in the given app,
 * when using SDK sandbox or not.
 */
public final class IsAllowedTopicsAccessCommand extends AbstractShellCommand {
    public static final String CMD_IS_ALLOWED_TOPICS_ACCESS = "is-allowed-topics-access";
    public static final String HELP_IS_ALLOWED_TOPICS_ACCESS =
            CMD_IS_ALLOWED_TOPICS_ACCESS
                    + " <package_name> <enrollment_id> <using_sdk_sandbox>\n"
                    + "    Checks if the given enrollment id is allowed to use the Topics APIs in"
                    + " the given app, when using SDK sandbox or not.";

    @Override
    public ShellCommandResult run(PrintWriter out, PrintWriter err, String[] args) {
        // Command name followed by 3 args: package name, enrollment id, uses sdksandbox.
        if (args.length != 4) {
            return invalidArgsError(
                    HELP_IS_ALLOWED_TOPICS_ACCESS, err, COMMAND_IS_ALLOWED_TOPICS_ACCESS, args);
        }
        String pkgName = args[1];
        String enrollmentId = args[2];
        String usesSdkSandboxStr = args[3];
        Boolean usesSdkSandbox = toBoolean(usesSdkSandboxStr);

        if (usesSdkSandbox == null) {
            return invalidArgsError(
                    HELP_IS_ALLOWED_TOPICS_ACCESS, err, COMMAND_IS_ALLOWED_TOPICS_ACCESS, args);
        }
        boolean isValid =
                AppManifestConfigHelper.isAllowedTopicsAccess(
                        usesSdkSandbox, pkgName, enrollmentId);
        Log.i(
                TAG,
                String.format(
                        "isAllowedTopicAccess(%s, %b, %s: %b)",
                        pkgName, usesSdkSandbox, enrollmentId, isValid));
        out.println(isValid);
        return toShellCommandResult(RESULT_SUCCESS, COMMAND_IS_ALLOWED_TOPICS_ACCESS);
    }

    @Override
    public String getCommandName() {
        return CMD_IS_ALLOWED_TOPICS_ACCESS;
    }

    @Override
    public int getMetricsLoggerCommand() {
        return COMMAND_IS_ALLOWED_TOPICS_ACCESS;
    }

    @Override
    public String getCommandHelp() {
        return HELP_IS_ALLOWED_TOPICS_ACCESS;
    }
}
