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
package com.android.adservices.service.shell;

import static com.android.adservices.service.shell.AbstractShellCommand.ERROR_TEMPLATE_INVALID_ARGS;
import static com.android.adservices.service.shell.AbstractShellCommand.RESULT_GENERIC_ERROR;
import static com.android.adservices.service.shell.AbstractShellCommand.RESULT_OK;
import static com.android.adservices.service.shell.EchoCommand.HELP_ECHO;

import android.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.android.adservices.service.common.AppManifestConfigHelper;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import com.google.common.base.Supplier;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Objects;

// TODO(b/308009734): STOPSHIP - document that it's up to each command implementation to check about
// caller's permission, whether device is userdebug, etc...

// TODO(b/308009734): arg parsing logic mostly copied from on BasicShellCommandHandler, it might be
// worth to refactor it once the handler is called by both system_server and service.

/**
 * Service-side version of {@code cmd adservices_manager}.
 *
 * <p>By convention, methods implementing commands should be prefixed with {@code run}.
 */
public final class AdServicesShellCommandHandler {
    @VisibleForTesting static final String ERROR_EMPTY_COMMAND = "Must provide a non-empty command";

    @VisibleForTesting static final String CMD_SHORT_HELP = "-h";
    @VisibleForTesting static final String CMD_HELP = "help";

    // TODO(b/308009734): Move CMD_IS_ALLOWED_XXX constants and shell commands to
    // CommonShellCommandFactory
    @VisibleForTesting
    static final String CMD_IS_ALLOWED_ATTRIBUTION_ACCESS = "is-allowed-attribution-access";

    @VisibleForTesting
    static final String CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS =
            "is-allowed-custom-audiences-access";

    @VisibleForTesting
    static final String CMD_IS_ALLOWED_TOPICS_ACCESS = "is-allowed-topics-access";

    @VisibleForTesting
    static final String HELP_IS_ALLOWED_ATTRIBUTION_ACCESS =
            CMD_IS_ALLOWED_ATTRIBUTION_ACCESS
                    + " <package_name> <enrollment_id>\n"
                    + "    Checks if the given enrollment id is allowed to use the Attribution "
                    + "APIs in the given app.";

    @VisibleForTesting
    static final String HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS =
            CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS
                    + " <package_name> <enrollment_id>\n"
                    + "    Checks if the given enrollment id is allowed to use the Custom "
                    + "Audience APIs in the given app.";

    @VisibleForTesting
    static final String HELP_IS_ALLOWED_TOPICS_ACCESS =
            CMD_IS_ALLOWED_TOPICS_ACCESS
                    + " <package_name> <enrollment_id> <using_sdk_sandbox>\n"
                    + "    Checks if the given enrollment id is allowed to use the Topics APIs in"
                    + " the given app, when using SDK sandbox or not.";

    // TODO(b/280460130): use adservice helpers for tag name / logging methods
    private static final String TAG = "AdServicesShellCmd";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    // Add more per API shell factory implementations as we create them.
    @VisibleForTesting
    static final Supplier<ShellCommandFactory[]> DEFAULT_FACTORIES_SUPPLIER =
            () ->
                    new ShellCommandFactory[] {
                        CommonShellCommandFactory.getInstance(),
                        CustomAudienceShellCommandFactory.getInstance(),
                    };

    private final PrintWriter mOut;
    private final PrintWriter mErr;
    private final Supplier<ShellCommandFactory[]> mFactoriesSupplier;

    private String[] mArgs;
    private int mArgPos;
    private String mCurArgData;

    /** If PrintWriter {@code err} is not provided, we use {@code out} for the {@code err}. */
    public AdServicesShellCommandHandler(PrintWriter out) {
        this(out, /* err= */ out);
    }

    public AdServicesShellCommandHandler(PrintWriter out, PrintWriter err) {
        this(out, err, DEFAULT_FACTORIES_SUPPLIER);
    }

    @VisibleForTesting
    AdServicesShellCommandHandler(
            PrintWriter out, PrintWriter err, Supplier<ShellCommandFactory[]> factoriesSupplier) {
        mOut = Objects.requireNonNull(out, "out cannot be null");
        mErr = Objects.requireNonNull(err, "err cannot be null");
        mFactoriesSupplier =
                Objects.requireNonNull(factoriesSupplier, "factoriesSupplier cannot be null");
    }

    /** Runs the given command ({@code args[0]}) and optional arguments */
    public int run(String... args) {
        Objects.requireNonNull(args, "args cannot be null");
        Preconditions.checkArgument(
                args.length >= 1, "must have at least one argument (the command itself)");
        if (DEBUG) {
            Log.d(TAG, "run(): " + Arrays.toString(args));
        }
        mArgs = args;
        String cmd = getNextArg();

        int res = RESULT_GENERIC_ERROR;
        try {
            res = onCommand(cmd);
            if (DEBUG) {
                Log.d(TAG, "Executed command " + cmd);
            }
        } catch (Throwable e) {
            // TODO(b/308009734): need to test this
            mErr.printf("Exception occurred while executing %s\n", Arrays.toString(mArgs));
            e.printStackTrace(mOut);
        } finally {
            if (DEBUG) {
                Log.d(TAG, "Flushing output");
            }
            mOut.flush();
            mErr.flush();
        }
        if (DEBUG) {
            Log.d(TAG, "Sending command result: " + res);
        }
        return res;
    }

    /******************
     * Helper methods *
     ******************/

    @Nullable
    private String getNextArg() {
        if (mArgs == null) {
            throw new IllegalStateException("INTERNAL ERROR: getNextArg() called before run()");
        }
        if (mCurArgData != null) {
            String arg = mCurArgData;
            mCurArgData = null;
            return arg;
        } else if (mArgPos < mArgs.length) {
            return mArgs[mArgPos++];
        } else {
            return null;
        }
    }

    private boolean hasExactNumberOfArgs(int expected) {
        return mArgs.length == expected + 1; // adds +1 for the cmd itself
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
        mErr.printf(ERROR_TEMPLATE_INVALID_ARGS, Arrays.toString(mArgs), syntax);
        return RESULT_GENERIC_ERROR;
    }

    /****************************************************************************
     * Commands - for each new one, add onHelp(), onCommand(), and runCommand() *
     ****************************************************************************/

    private static void onHelp(PrintWriter pw) {
        pw.printf("%s\n\n", HELP_ECHO);
        pw.printf("%s\n\n", HELP_IS_ALLOWED_ATTRIBUTION_ACCESS);
        pw.printf("%s\n\n", HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS);
        pw.printf("%s\n\n", HELP_IS_ALLOWED_TOPICS_ACCESS);
        pw.printf("%s\n\n", CustomAudienceListCommand.HELP);
        pw.printf("%s\n\n", CustomAudienceViewCommand.HELP);
    }

    private int onCommand(String cmd) {
        switch (cmd) {
            case CMD_SHORT_HELP:
            case CMD_HELP:
                onHelp(mOut);
                return RESULT_OK;
            case "":
                mErr.println(ERROR_EMPTY_COMMAND);
                return RESULT_GENERIC_ERROR;
            case CMD_IS_ALLOWED_ATTRIBUTION_ACCESS:
            case CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS:
            case CMD_IS_ALLOWED_TOPICS_ACCESS:
                return runIsAllowedApiAccess(cmd);
            default:
                // TODO (b/308009734): Move other shell commands implement ICommand interface.
                ShellCommand shellCommand = null;
                for (ShellCommandFactory factory : mFactoriesSupplier.get()) {
                    shellCommand = factory.getShellCommand(cmd);
                    if (shellCommand != null) {
                        break;
                    }
                }

                if (shellCommand == null) {
                    mErr.printf("Unknown command: %s\n", cmd);
                    mErr.println("Use -h for help.");
                    return RESULT_GENERIC_ERROR;
                }
                return shellCommand.run(mOut, mErr, mArgs);
        }
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
                isValid = AppManifestConfigHelper.isAllowedAttributionAccess(pkgName, enrollmentId);
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
                                pkgName, enrollmentId);
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
                                usesSdkSandbox, pkgName, enrollmentId);
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
        mOut.println(isValid);
        return RESULT_OK;
    }
}
