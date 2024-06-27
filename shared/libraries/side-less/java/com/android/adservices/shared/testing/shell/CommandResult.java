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

import java.util.Objects;

/** Contains the result of a shell command. */
public final class CommandResult {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_FINISHED = "FINISHED";

    private final String mOut;
    private final String mErr;
    private final String mCommandStatus;

    public CommandResult(String out, String err, String commandStatus) {
        mOut = Objects.requireNonNull(out);
        mErr = Objects.requireNonNull(err);
        mCommandStatus = Objects.requireNonNull(commandStatus);
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
                "CommandResult [out=%s, err=%s, status=%s]", mOut, mErr, mCommandStatus);
    }

    public boolean isCommandRunning() {
        return mCommandStatus.equals(STATUS_RUNNING);
    }
}
