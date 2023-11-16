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

import android.annotation.Nullable;
import android.content.Context;
import android.os.Binder;
import android.text.TextUtils;
import android.util.Log;

import com.android.adservices.service.common.AppManifestConfigHelper;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.BasicShellCommandHandler;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

// TODO(b/308009734): STOPSHIP - document that it's up to each command implementation to check about
// caller's permission, whether device is userdebug, etc...
/**
 * Service-side version of {@code cmd adservices_manager}.
 *
 * <p>By convention, methods implementing commands should be prefixed with {@code run}.
 */
public final class AdServicesShellCommandHandler extends BasicShellCommandHandler {

    private static final String TAG = AdServicesShellCommandHandler.class.getSimpleName();

    @VisibleForTesting static final String CMD_ECHO = "echo";

    @VisibleForTesting
    static final String CMD_IS_ALLOWED_ATTRIBUTION_ACCESS = "is-allowed-attribution-access";

    @VisibleForTesting
    static final String CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS =
            "is-allowed-custom-audiences-access";

    @VisibleForTesting
    static final String CMD_IS_ALLOWED_TOPICS_ACCESS = "is-allowed-topics-access";

    @VisibleForTesting
    static final String HELP_ECHO =
            CMD_ECHO + " <message> - prints the given message (useful to check cmd is working).";

    @VisibleForTesting
    static final String HELP_IS_ALLOWED_ATTRIBUTION_ACCESS =
            CMD_IS_ALLOWED_ATTRIBUTION_ACCESS
                    + " <package_name> <enrollment_id> - checks if the given enrollment id is"
                    + " allowed to use the Attribution APIs in the given app.";

    @VisibleForTesting
    static final String HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS =
            CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS
                    + " <package_name> <enrollment_id> - checks if the given enrollment id is"
                    + " allowed to use the Custom Audience APIs in the given app.";

    @VisibleForTesting
    static final String HELP_IS_ALLOWED_TOPICS_ACCESS =
            CMD_IS_ALLOWED_TOPICS_ACCESS
                    + " <package_name> <enrollment_id> <using_sdk_sandbox>- checks if the given"
                    + " enrollment id is allowed to use the Topics APIs in the given app, when"
                    + " using SDK sandbox or not.";

    @VisibleForTesting
    static final String ERROR_EMPTY_COMMAND = "Must provide a non-empty command\n";

    @VisibleForTesting
    static final String ERROR_TEMPLATE_INVALID_ARGS = "Invalid cmd (%s). Syntax: %s";

    private static final int RESULT_OK = 0;
    private static final int RESULT_GENERIC_ERROR = -1;

    private final Context mContext;
    private final FileDescriptor mFd;

    // Used only on tests (otherwise it would be hard to get content of FileDescriptor)
    @Nullable private final Supplier<PrintWriter> mSupplier;

    public AdServicesShellCommandHandler(Context context, FileDescriptor fd) {
        this(context, Objects.requireNonNull(fd, "fd cannot be null"), /* supplier= */ null);
    }

    @VisibleForTesting
    AdServicesShellCommandHandler(Context context, Supplier<PrintWriter> supplier) {
        this(
                context,
                FileDescriptor.out,
                Objects.requireNonNull(supplier, "supplier cannot be null"));
    }

    private AdServicesShellCommandHandler(
            Context context, FileDescriptor fd, Supplier<PrintWriter> supplier) {
        mContext = Objects.requireNonNull(context, "context cannot be null");
        mFd = fd;
        mSupplier = supplier;
    }

    @Override
    public PrintWriter getOutPrintWriter() {
        if (mSupplier != null) {
            PrintWriter supplied = mSupplier.get();
            Log.i(TAG, "getOutPrintWriter(): returning supplied object: " + supplied);
            return supplied;
        }
        return super.getOutPrintWriter();
    }

    /** Runs the given command ({@code args[0]}) and optional arguments */
    public int run(String... args) {
        Objects.requireNonNull(args, "args cannot be null");
        // TODO(b/303886367): use Preconditions
        if (args.length < 1) {
            throw new IllegalArgumentException(
                    "must have at least one argument (the command itself)");
        }

        return super.exec(/* target= */ null, /* in= */ null, /* out= */ mFd, /* err= */ mFd, args);
    }

    @Override
    public int exec(
            Binder target,
            FileDescriptor in,
            FileDescriptor out,
            FileDescriptor err,
            String[] args) {
        throw new UnsupportedOperationException("not exposed");
    }

    @Override
    public void onHelp() {
        PrintWriter pw = getOutPrintWriter();

        pw.println(HELP_ECHO);
        pw.println(HELP_IS_ALLOWED_ATTRIBUTION_ACCESS);
        pw.println(HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS);
        pw.println(HELP_IS_ALLOWED_TOPICS_ACCESS);
    }

    @Override
    public int onCommand(String cmd) {
        Log.d(TAG, "onCommand: " + cmd);
        switch (cmd) {
            case CMD_ECHO:
                return runEcho();
            case CMD_IS_ALLOWED_ATTRIBUTION_ACCESS:
            case CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS:
            case CMD_IS_ALLOWED_TOPICS_ACCESS:
                return runIsAllowedApiAccess(cmd);
            case "":
                getOutPrintWriter().print(ERROR_EMPTY_COMMAND);
                return RESULT_GENERIC_ERROR;
            default:
                return handleDefaultCommands(cmd);
        }
    }

    private int runEcho() {
        if (!hasExactNumberOfArgs(1)) {
            return invalidArgsError(HELP_ECHO);
        }
        String message = getNextArg();
        if (TextUtils.isEmpty(message)) {
            return invalidArgsError(HELP_ECHO);
        }

        Log.i(TAG, "runEcho: message='" + message + "'");
        getOutPrintWriter().println(message);
        return RESULT_OK;
    }

    private int runIsAllowedApiAccess(String cmd) {
        int expectedArgs = 2; // first 2 args are common for all of them
        String helpMsg = null;
        switch (cmd) {
            case CMD_IS_ALLOWED_ATTRIBUTION_ACCESS:
                helpMsg = HELP_IS_ALLOWED_ATTRIBUTION_ACCESS;
                break;
            case CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS:
                helpMsg = HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS;
                break;
            case CMD_IS_ALLOWED_TOPICS_ACCESS:
                expectedArgs = 3;
                helpMsg = HELP_IS_ALLOWED_TOPICS_ACCESS;
                break;
        }
        if (!hasExactNumberOfArgs(expectedArgs)) {
            return invalidArgsError(helpMsg);
        }
        String pkgName = getNextArg();
        if (TextUtils.isEmpty(pkgName)) {
            return invalidArgsError(helpMsg);
        }
        String enrollmentId = getNextArg();
        if (TextUtils.isEmpty(enrollmentId)) {
            return invalidArgsError(helpMsg);
        }

        boolean isValid = false;
        switch (cmd) {
            case CMD_IS_ALLOWED_ATTRIBUTION_ACCESS:
                isValid =
                        AppManifestConfigHelper.isAllowedAttributionAccess(
                                mContext, pkgName, enrollmentId);
                Log.i(
                        TAG,
                        "isAllowedAttributionAccess("
                                + pkgName
                                + ", "
                                + enrollmentId
                                + ": "
                                + isValid);
                break;
            case CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS:
                isValid =
                        AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                mContext, pkgName, enrollmentId);
                Log.i(
                        TAG,
                        "isAllowedCustomAudiencesAccess("
                                + pkgName
                                + ", "
                                + enrollmentId
                                + ": "
                                + isValid);
                break;
            case CMD_IS_ALLOWED_TOPICS_ACCESS:
                Boolean usesSdkSandbox = getNextBooleanArg();
                if (usesSdkSandbox == null) {
                    return invalidArgsError(HELP_IS_ALLOWED_TOPICS_ACCESS);
                }
                isValid =
                        AppManifestConfigHelper.isAllowedTopicsAccess(
                                mContext, usesSdkSandbox, pkgName, enrollmentId);
                Log.i(
                        TAG,
                        "isAllowedTopicAccess("
                                + pkgName
                                + ", "
                                + usesSdkSandbox
                                + ", "
                                + enrollmentId
                                + ": "
                                + isValid);
                break;
        }
        getOutPrintWriter().println(isValid);
        return RESULT_OK;
    }

    private boolean hasExactNumberOfArgs(int expected) {
        return getAllArgs().length == expected + 1; // adds +1 for the cmd itself
    }

    @Nullable
    private Boolean getNextBooleanArg() {
        String arg = getNextArg();
        if (TextUtils.isEmpty(arg)) {
            return null;
        }
        // Boolean.parse returns false when it's invalid
        switch (arg.trim().toLowerCase()) {
            case "true":
                return Boolean.TRUE;
            case "false":
                return Boolean.FALSE;
            default:
                return null;
        }
    }

    private int invalidArgsError(String syntax) {
        getOutPrintWriter()
                .println(
                        String.format(
                                ERROR_TEMPLATE_INVALID_ARGS,
                                Arrays.toString(getAllArgs()),
                                syntax));
        return RESULT_GENERIC_ERROR;
    }
}
