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
package com.android.adservices.shared.meta_testing;

import com.android.adservices.shared.testing.AndroidSdk.Level;
import com.android.adservices.shared.testing.DynamicLogger;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.Nullable;
import com.android.adservices.shared.testing.device.DeviceGateway;

import com.google.common.truth.Expect;
import com.google.common.truth.StandardSubjectBuilder;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class FakeDeviceGateway implements DeviceGateway {

    private final Logger mLog = new Logger(DynamicLogger.getInstance(), getClass());
    private final Map<String, String> mExpectations = new HashMap<>();
    private final List<String> mCalls = new ArrayList<>();

    @Nullable private Level mSdkLevel;

    /** Sets what the given command will return. */
    @FormatMethod
    public void onCommand(String result, @FormatString String cmdFmt, @Nullable Object... cmdArgs) {
        Objects.requireNonNull(result, "result cannot be null");
        Objects.requireNonNull(cmdFmt, "cmdFmt cannot be null");
        String cmd = String.format(cmdFmt, cmdArgs);

        mLog.i("expectCalled: %s => %s", cmd, result);
        mExpectations.put(cmd, result);
    }

    /** Expects that the given command was called. */
    @FormatMethod
    public void expectCalled(
            StandardSubjectBuilder expect,
            @FormatString String cmdFmt,
            @Nullable Object... cmdArgs) {
        Objects.requireNonNull(expect, "expect cannot be null");
        Objects.requireNonNull(cmdFmt, "cmdFmt cannot be null");
        String cmd = String.format(Locale.ENGLISH, cmdFmt, cmdArgs);

        if (!mCalls.contains(cmd)) {
            expect.withMessage(
                            "Command %s not called (commands called so far were: %s)", cmd, mCalls)
                    .fail();
        }
    }

    /** Asserts that no command has been called so far. */
    public void expectNothingCalled(Expect expect) {
        expect.withMessage("calls so far").that(mCalls).isEmpty();
    }

    /** Sets the SDK Level of the device. */
    public void setSdkLevel(Level level) {
        Objects.requireNonNull(level, "level cannot be null");
        mSdkLevel = level;
    }

    @Override
    @FormatMethod
    public String runShellCommand(@FormatString String cmdFmt, @Nullable Object... cmdArgs) {
        Objects.requireNonNull(cmdFmt, "cmdFmt cannot be null");

        String cmd = String.format(Locale.ENGLISH, cmdFmt, cmdArgs);
        mLog.i("runShellCommand(): running %s", cmd);

        mCalls.add(cmd);

        String result = mExpectations.get(cmd);
        mLog.i("runShellCommand(): returning %s", result);

        return result;
    }

    @Override
    public Level getSdkLevel() {
        if (mSdkLevel == null) {
            throw new IllegalStateException("must call setSdkLevel() first");
        }
        return mSdkLevel;
    }

    @Override
    public String toString() {
        return "FakeDeviceGateway [mLog="
                + mLog
                + ", mSdkLevel="
                + mSdkLevel
                + ", mExpectations="
                + mExpectations
                + ", mCalls="
                + mCalls
                + "]";
    }
}
