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

package com.android.adservices.shared.testing.shell;

import com.google.common.annotations.VisibleForTesting;

import java.util.Objects;

// TODO(b/324491698): use ShellCommandOutput instead (or factor it to use it inside)
/** Contains the result of a shell command. */
public final class CommandResult {

    // TODO(b/324491698): make them package protected when moved to c.a.a.s.t.device packages
    @VisibleForTesting public static final String STATUS_RUNNING = "RUNNING";
    @VisibleForTesting public static final String STATUS_FINISHED = "FINISHED";

    private final String mOut;
    private final String mErr;
    private final String mCommandStatus;

    public CommandResult(String out, String err, String commandStatus) {
        mOut = Objects.requireNonNull(out, "out cannot be null");
        mErr = Objects.requireNonNull(err, "err cannot be null");
        mCommandStatus = Objects.requireNonNull(commandStatus, "status cannot be null");
    }

    public CommandResult(String out, String err) {
        this(out, err, STATUS_FINISHED);
    }

    public String getOut() {
        return mOut;
    }

    public String getErr() {
        return mErr;
    }

    public String getCommandStatus() {
        return mCommandStatus;
    }

    @Override
    public String toString() {
        return String.format(
                "CommandResult[out=%s, err=%s, status=%s]", mOut, mErr, mCommandStatus);
    }

    public boolean isCommandRunning() {
        return mCommandStatus.equals(STATUS_RUNNING);
    }
}
