/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.sdksandbox;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Binder;
import android.os.Process;
import android.os.UserHandle;

import com.android.modules.utils.BasicShellCommandHandler;
import com.android.sdksandbox.ISdkSandboxService;

import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class SdkSandboxShellCommand extends BasicShellCommandHandler {

    private final SdkSandboxManagerService mService;
    private final Context mContext;

    private int mUserId = UserHandle.CURRENT.getIdentifier();
    private CallingInfo mCallingInfo;

    SdkSandboxShellCommand(SdkSandboxManagerService service, Context context) {
        mService = service;
        mContext = context;
    }

    @Override
    public int onCommand(String cmd) {
        int callingUid = Binder.getCallingUid();
        if (callingUid != Process.ROOT_UID && callingUid != Process.SHELL_UID) {
            throw new SecurityException("sdk_sandbox shell command is only callable by ADB");
        }
        final long token = Binder.clearCallingIdentity();

        int result;
        try {
            if (cmd == null) {
                result = handleDefaultCommands(null);
            } else {
                switch (cmd) {
                    case "start":
                        result = runStart();
                        break;
                    case "stop":
                        result = runStop();
                        break;
                    default:
                        result = handleDefaultCommands(cmd);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        return result;
    }

    private void handleSandboxArguments() {
        String opt;
        while ((opt = getNextOption()) != null) {
            if (opt.equals("--user")) {
                mUserId = parseUserArg(getNextArgRequired());
            } else {
                throw new IllegalArgumentException("Unknown option: " + opt);
            }
        }

        if (mUserId == UserHandle.CURRENT.getIdentifier()) {
            mUserId = mContext.getUser().getIdentifier();
        }

        String callingPackageName = getNextArgRequired();
        try {
            ApplicationInfo info = mContext.getPackageManager().getApplicationInfoAsUser(
                    callingPackageName, /* flags */ 0, UserHandle.of(mUserId));

            if ((info.flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
                throw new IllegalArgumentException(
                        "Package " + callingPackageName + " must be debuggable.");
            }
            mCallingInfo = new CallingInfo(info.uid, callingPackageName);
        } catch (NameNotFoundException e) {
            throw new IllegalArgumentException(
                    "No such package " + callingPackageName + " for user " + mUserId);
        }
    }

    private int parseUserArg(String arg) {
        switch (arg) {
            case "all":
                throw new IllegalArgumentException("Cannot run sdk_sandbox command for user 'all'");
            case "current":
                return mContext.getUser().getIdentifier();
            default:
                try {
                    return Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Bad user number: " + arg);
                }
        }
    }

    /** Callback for binding sandbox. Provides blocking interface {@link #isSuccessful()}. */
    private class LatchSandboxServiceConnectionCallback
            implements SdkSandboxManagerService.SandboxServiceConnection.Callback {

        private final CountDownLatch mLatch = new CountDownLatch(1);
        private boolean mSuccess = false;
        public static final int SANDBOX_BIND_TIMEOUT_S = 5;

        @Override
        public void onBindingSuccessful(ISdkSandboxService service) {
            mLatch.countDown();
            mSuccess = true;
        }

        @Override
        public void onBindingFailed() {
            mLatch.countDown();
        }

        public boolean isSuccessful() {
            try {
                boolean completed = mLatch.await(SANDBOX_BIND_TIMEOUT_S, TimeUnit.SECONDS);
                if (!completed) {
                    getErrPrintWriter()
                            .println(
                                    "Error: Sdk sandbox failed to start in "
                                            + SANDBOX_BIND_TIMEOUT_S
                                            + " seconds");
                    return false;
                }
                if (!mSuccess) {
                    getErrPrintWriter().println("Error: Sdk sandbox failed to start");
                    return false;
                }
                return true;
            } catch (InterruptedException e) {
                return false;
            }
        }
    }

    private int runStart() {
        handleSandboxArguments();
        if (mService.isSdkSandboxServiceRunning(mCallingInfo)) {
            getErrPrintWriter().println("Error: Sdk sandbox already running for "
                    + mCallingInfo.getPackageName() + " and user " + mUserId);
            return -1;
        }

        LatchSandboxServiceConnectionCallback callback =
                new LatchSandboxServiceConnectionCallback();

        mService.startSdkSandbox(mCallingInfo, callback);

        return callback.isSuccessful() ? 0 : -1;
    }

    private int runStop() {
        handleSandboxArguments();
        if (!mService.isSdkSandboxServiceRunning(mCallingInfo)) {
            getErrPrintWriter().println("Sdk sandbox not running for "
                    + mCallingInfo.getPackageName() + " and user " + mUserId);
            return -1;
        }
        mService.stopSdkSandboxService(mCallingInfo, "Shell command 'sdk_sandbox stop' issued");
        return 0;
    }

    @Override
    public void onHelp() {
        final PrintWriter pw = getOutPrintWriter();
        pw.println("SDK sandbox (sdk_sandbox) commands: ");
        pw.println("    help: ");
        pw.println("        Prints this help text.");
        pw.println();
        pw.println("    start [--user <USER_ID> | current] <PACKAGE>");
        pw.println("        Start the SDK sandbox for the app <PACKAGE>. Options are:");
        pw.println("        --user <USER_ID> | current: Specify user for app; uses current user");
        pw.println("            if not specified");
        pw.println();
        pw.println("    stop [--user <USER_ID> | current] <PACKAGE>");
        pw.println("        Stop the SDK sandbox for the app <PACKAGE>. Options are:");
        pw.println("        --user <USER_ID> | current: Specify user for app; uses current user");
        pw.println("            if not specified");
    }
}
