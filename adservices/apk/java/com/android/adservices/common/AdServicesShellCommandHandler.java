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
import android.os.Binder;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.BasicShellCommandHandler;

import java.io.FileDescriptor;
import java.io.PrintWriter;
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
    static final String HELP_ECHO =
            CMD_ECHO + " <message> - prints the given message (useful to check cmd is working)";

    @VisibleForTesting
    static final String ERROR_EMPTY_COMMAND = "Must provide a non-empty command\n";

    @VisibleForTesting static final String ERROR_ECHO_EMPTY = "Must provide a non-empty message\n";

    @VisibleForTesting
    static final String ERROR_ECHO_HIGHLANDER = "Echo args: There can be only one!\n";

    private static final int RESULT_OK = 0;
    private static final int RESULT_GENERIC_ERROR = -1;

    private final FileDescriptor mFd;

    // Used only on tests (otherwise it would be hard to get content of FileDescriptor)
    @Nullable private final Supplier<PrintWriter> mSupplier;

    public AdServicesShellCommandHandler(FileDescriptor fd) {
        mFd = Objects.requireNonNull(fd, "fd cannot be null");
        mSupplier = null;
    }

    @VisibleForTesting
    AdServicesShellCommandHandler(Supplier<PrintWriter> supplier) {
        mFd = FileDescriptor.out;
        mSupplier = Objects.requireNonNull(supplier, "fd cannot be null");
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
    }

    @Override
    public int onCommand(String cmd) {
        Log.d(TAG, "onCommand: " + cmd);
        switch (cmd) {
            case "echo":
                return runEcho();
            case "":
                getOutPrintWriter().print(ERROR_EMPTY_COMMAND);
                return RESULT_GENERIC_ERROR;
            default:
                return handleDefaultCommands(cmd);
        }
    }

    private int runEcho() {
        PrintWriter pw = getOutPrintWriter();
        String message = getNextArg();

        if (TextUtils.isEmpty(message)) {
            pw.print(ERROR_ECHO_EMPTY);
            return RESULT_GENERIC_ERROR;
        }
        if (!TextUtils.isEmpty(getNextArg())) {
            pw.print(ERROR_ECHO_HIGHLANDER);
            return RESULT_GENERIC_ERROR;
        }

        Log.i(TAG, "runEcho: message='" + message + "'");
        pw.println(message);
        return RESULT_OK;
    }
}
