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

import android.annotation.Nullable;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.android.adservices.api.R;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.shell.AdServicesShellCommandHandler;
import com.android.adservices.service.shell.AdservicesShellCommandFactorySupplier;
import com.android.adservices.service.stats.AdServicesLoggerImpl;

import com.google.common.util.concurrent.ListeningExecutorService;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    private ListeningExecutorService mExecutorService;
    private Flags mFlags;

    private final StringWriter mStringWriter = new StringWriter();
    private final PrintWriter mPrintWriter = new PrintWriter(mStringWriter);
    private final CountDownLatch mLatch = new CountDownLatch(1);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.shell_command_activity);

        runShellCommand(FlagsFactory.getFlags(), AdServicesExecutors.getLightWeightExecutor());
    }

    private void runShellCommand(Flags flags, ListeningExecutorService executorService) {
        mFlags = flags;
        mExecutorService = executorService;
        if (!mFlags.getAdServicesShellCommandEnabled()) {
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
                        mPrintWriter,
                        new AdservicesShellCommandFactorySupplier(),
                        AdServicesLoggerImpl.getInstance());
        var unused =
                mExecutorService.submit(
                        () -> {
                            int res = handler.run(args);
                            Log.d(TAG, "Shell command completed with status: " + res);
                            mLatch.countDown();
                        });
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
        boolean enabled = mFlags.getAdServicesShellCommandEnabled();
        if (!enabled) {
            Log.e(
                    TAG,
                    String.format(
                            "dump(%s) called on ShellCommandActivity when shell"
                                    + " command flag was disabled",
                            Arrays.toString(args)));
            return;
        }

        if (args.length == 2 && args[1].equals(GET_RESULT_ARG)) {
            waitAndGetResult(writer);
        } else {
            writer.printf(
                    "Invalid args %s. Supported args are 'cmd get-result'\n",
                    Arrays.toString(args));
        }
        finishSelf();
    }

    private void waitAndGetResult(PrintWriter writer) {
        try {
            if (mLatch.await(MAX_COMMAND_DURATION_MILLIS, TimeUnit.MILLISECONDS)) {
                writer.printf("%s", mStringWriter.toString());
            } else {
                writer.printf(
                        "Elapsed time: %d Millisecond. Timeout occurred , failed to complete "
                                + "shell command\n",
                        MAX_COMMAND_DURATION_MILLIS);
            }
        } catch (InterruptedException e) {
            writer.println("Thread interrupted, failed to complete shell command");
            Thread.currentThread().interrupt();
        }
    }

    private void finishSelf() {
        mPrintWriter.close();
        finish();
    }
}
