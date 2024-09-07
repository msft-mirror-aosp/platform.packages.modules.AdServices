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
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_IS_ALLOWED_CUSTOM_AUDIENCE_ACCESS;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_SUCCESS;

import android.util.Log;

import com.android.adservices.service.common.AppManifestConfigHelper;
import com.android.adservices.service.shell.AbstractShellCommand;
import com.android.adservices.service.shell.ShellCommandResult;

import java.io.PrintWriter;

public final class IsAllowedCustomAudiencesAccessCommand extends AbstractShellCommand {

    public static final String CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS =
            "is-allowed-custom-audiences-access";
    public static final String HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS =
            CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS
                    + " <package_name> <enrollment_id>\n"
                    + "    Checks if the given enrollment id is allowed to use the Custom "
                    + "Audience APIs in the given app.";

    @Override
    public ShellCommandResult run(PrintWriter out, PrintWriter err, String[] args) {
        // Command name followed by 2 args: package name, enrollment id.
        if (args.length != 3) {
            return invalidArgsError(
                    HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS,
                    err,
                    COMMAND_IS_ALLOWED_CUSTOM_AUDIENCE_ACCESS,
                    args);
        }
        String pkgName = args[1];
        String enrollmentId = args[2];

        boolean isValid =
                AppManifestConfigHelper.isAllowedCustomAudiencesAccess(pkgName, enrollmentId);
        Log.i(
                TAG,
                String.format(
                        "isAllowedCustomAudiencesAccess(%s, %s: %b)",
                        pkgName, enrollmentId, isValid));
        out.println(isValid);
        return toShellCommandResult(RESULT_SUCCESS, COMMAND_IS_ALLOWED_CUSTOM_AUDIENCE_ACCESS);
    }

    @Override
    public String getCommandName() {
        return CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS;
    }

    @Override
    public int getMetricsLoggerCommand() {
        return COMMAND_IS_ALLOWED_CUSTOM_AUDIENCE_ACCESS;
    }

    @Override
    public String getCommandHelp() {
        return HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS;
    }
}
