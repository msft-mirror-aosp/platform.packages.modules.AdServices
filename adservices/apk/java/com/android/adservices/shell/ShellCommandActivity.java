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

package com.android.adservices.shell;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.adservices.api.R;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.shell.AdServicesShellCommandHandler;
import com.android.adservices.service.shell.AdservicesShellCommandFactorySupplier;
import com.android.adservices.service.stats.AdServicesLoggerImpl;

import com.google.common.util.concurrent.ListeningExecutorService;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Activity to run shell command for Android R/S.
 *
 * <p>It is disabled by default in manifest and is also flag guarded. It doesn't have a launcher
 * icon. It is first enabled using shell commands. We start an activity by sending an intent with
 * all the arguments to run the adservices shell command. Then we use dumpsys to get the result of
 * the already run command. After querying the result, the activity is closed and restored back to
 * disabled state. Essentially Activity is only alive for the duration of the one command and once
 * dump cmd is run, it is closed.
 */
@RequiresApi(Build.VERSION_CODES.S)
public final class ShellCommandActivity extends Activity {
    // TODO(b/308009734): Add data/info to the UI (input, status, output).
    // TODO(b/308009734): Add an option to startForResult() that way CTS can use this instead of
    // dumpsys.
    private static final String TAG = "AdServicesShellCommand";
    private static final String CMD_ARGS = "cmd-args";
    private static final String GET_RESULT_ARG = "get-result";
    // This timeout is used to get result using dumpsys. The timeout should not be more than 5000
    // millisecond. Otherwise dumpsys will throw timeout exception.
    private static final long MAX_COMMAND_DURATION_MILLIS = 3000L;

    private static final String SHELL_COMMAND_ACTIVITY_DUMP = "-- ShellCommandActivity dump --";

    private static final String COMMAND_STATUS = "CommandStatus";
    private static final String COMMAND_RES = "CommandRes";
    private static final String COMMAND_OUT = "CommandOut";
    private static final String COMMAND_ERR = "CommandErr";

    private static final int STATUS_RUNNING = 1;
    private static final int STATUS_FINISHED = 2;
    private static final int STATUS_TIMEOUT = 3;

    @IntDef(value = {STATUS_RUNNING, STATUS_FINISHED, STATUS_TIMEOUT})
    @Retention(RetentionPolicy.SOURCE)
    private @interface Status {}

    private final StringWriter mOutSw = new StringWriter();
    private final PrintWriter mOutPw = new PrintWriter(mOutSw);

    private final StringWriter mErrSw = new StringWriter();
    private final PrintWriter mErrPw = new PrintWriter(mErrSw);

    private final CountDownLatch mLatch = new CountDownLatch(1);

    private int mRes = -1;
    private ListeningExecutorService mExecutorService;
    private boolean mShellCommandEnabled;

    private void runShellCommand() {
        if (!mShellCommandEnabled) {
            Log.e(TAG, "Activity started when shell command is disabled");
            finishSelf();
            return;
        }

        Intent intent = getIntent();
        String[] args = intent.getStringArrayExtra(CMD_ARGS);
        if (args == null || args.length == 0) {
            Log.e(TAG, "command args is null or empty");
            finishSelf();
            return;
        }
        AdServicesShellCommandHandler handler =
                new AdServicesShellCommandHandler(
                        mOutPw,
                        mErrPw,
                        new AdservicesShellCommandFactorySupplier(),
                        AdServicesLoggerImpl.getInstance());
        var unused =
                mExecutorService.submit(
                        () -> {
                            mRes = handler.run(args);
                            Log.d(TAG, "Shell command completed with status: " + mRes);
                            mLatch.countDown();
                        });
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.shell_command_activity);

        mShellCommandEnabled = DebugFlags.getInstance().getAdServicesShellCommandEnabled();
        mExecutorService = AdServicesExecutors.getLightWeightExecutor();
        runShellCommand();
    }

    private @Status int waitAndGetResult(PrintWriter writer) {
        try {
            if (mLatch.await(MAX_COMMAND_DURATION_MILLIS, TimeUnit.MILLISECONDS)) {
                formatAndPrintResult(STATUS_FINISHED, mOutSw.toString(), mErrSw.toString(), writer);
                return STATUS_FINISHED;
            }
            Log.e(
                    TAG,
                    String.format(
                            "Elapsed time: %d Millisecond. Command is still running. Please"
                                    + " retry by calling get-result shell command\n",
                            MAX_COMMAND_DURATION_MILLIS));
            formatAndPrintResult(STATUS_RUNNING, mOutSw.toString(), mErrSw.toString(), writer);
            return STATUS_RUNNING;
        } catch (InterruptedException e) {
            writer.println("Thread interrupted, failed to complete shell command");
            Thread.currentThread().interrupt();
        }
        return STATUS_FINISHED;
    }

    @Override
    public void dump(
            String prefix,
            @Nullable FileDescriptor fd,
            PrintWriter writer,
            @Nullable String[] args) {
        // Get the result after the starting activity using dumpsys.
        if (args != null && args.length > 0 && args[0].equals("cmd")) {
            getShellCommandResult(args, writer);
            return;
        }

        super.dump(prefix, fd, writer, args);
    }

    private void getShellCommandResult(String[] args, PrintWriter writer) {
        if (!mShellCommandEnabled) {
            Log.e(
                    TAG,
                    String.format(
                            "dump(%s) called on ShellCommandActivity when shell"
                                    + " command flag was disabled",
                            Arrays.toString(args)));
            return;
        }

        if (args.length == 2 && args[1].equals(GET_RESULT_ARG)) {
            @Status int status = waitAndGetResult(writer);
            if (status != STATUS_RUNNING) {
                finishSelf();
            }
        } else {
            writer.printf(
                    "Invalid args %s. Supported args are 'cmd get-result'\n",
                    Arrays.toString(args));
            finishSelf();
        }
    }

    /*
    Example formatted result:
      -- ShellCommandActivity dump --
      CommandStatus: FINISHED
      CommandRes: 0
      CommandOut:
        hello
      CommandErr:
        Something went wrong
     */
    private void formatAndPrintResult(
            @Status int status, String out, String err, PrintWriter writer) {
        writer.println(SHELL_COMMAND_ACTIVITY_DUMP);
        writer.printf("%s: %s\n", COMMAND_STATUS, statusToString(status));

        if (status == STATUS_FINISHED || status == STATUS_TIMEOUT) {
            writer.printf("%s: %d\n", COMMAND_RES, mRes);
        }
        if (!out.isEmpty()) {
            indentAndPrefixStringWithSpace(out, COMMAND_OUT, writer);
        }
        if (!err.isEmpty()) {
            indentAndPrefixStringWithSpace(err, COMMAND_ERR, writer);
        }
    }

    private void indentAndPrefixStringWithSpace(String input, String name, PrintWriter writer) {
        String formattedOut =
                Arrays.stream(input.strip().split("\n"))
                        .collect(Collectors.joining("\n  ", "  ", ""));
        writer.printf("%s:\n%s\n", name, formattedOut);
    }

    private String statusToString(int status) {
        switch (status) {
            case STATUS_RUNNING:
                return "RUNNING";
            case STATUS_FINISHED:
                return "FINISHED";
            case STATUS_TIMEOUT:
                return "TIMEOUT";
            default:
                return "UNKNOWN-" + status;
        }
    }

    private void finishSelf() {
        mOutPw.close();
        finish();
    }
}
