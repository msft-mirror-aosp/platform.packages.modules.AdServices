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
import com.android.adservices.shared.testing.Nullable;
import com.android.adservices.shared.testing.device.AbstractDeviceGateway;
import com.android.adservices.shared.testing.device.ShellCommandInput;
import com.android.adservices.shared.testing.device.ShellCommandOutput;

import com.google.common.truth.Expect;
import com.google.common.truth.StandardSubjectBuilder;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FakeDeviceGateway extends AbstractDeviceGateway {

    private final Map<String, ShellCommandOutput> mResultExpectations = new HashMap<>();
    private final List<String> mCalls = new ArrayList<>();

    @Nullable private Level mSdkLevel;

    /** Sets what the given command will return. */
    @FormatMethod
    public void onCommand(String result, @FormatString String cmdFmt, @Nullable Object... cmdArgs) {
        Objects.requireNonNull(result, "result cannot be null");
        Objects.requireNonNull(cmdFmt, "cmdFmt cannot be null");
        String cmd = String.format(cmdFmt, cmdArgs);

        mLog.i("expectCalled: %s => %s", cmd, result);
        mResultExpectations.put(cmd, new ShellCommandOutput(result));
    }

    /** Sets what the given command will return. */
    public void onCommand(ShellCommandInput input, ShellCommandOutput output) {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(output, "output cannot be null");

        mLog.i("expectCalled: %s => %s", input, output);
        mResultExpectations.put(input.getCommand(), output);
    }

    /** Expects that the given command was called. */
    @FormatMethod
    public void expectCalled(
            StandardSubjectBuilder expect,
            @FormatString String cmdFmt,
            @Nullable Object... cmdArgs) {
        expectCalled(expect, new ShellCommandInput(cmdFmt, cmdArgs));
    }

    /** Expects that the given command was called. */
    public void expectCalled(StandardSubjectBuilder expect, ShellCommandInput input) {
        Objects.requireNonNull(input, "input cannot be null");
        String cmd = input.getCommand();
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

    /**
     * Sets the SDK Level of the device.
     *
     * @return itself, so it can be nested
     */
    public FakeDeviceGateway setSdkLevel(Level level) {
        Objects.requireNonNull(level, "level cannot be null");
        mSdkLevel = level;
        return this;
    }

    @Override
    public ShellCommandOutput runShellCommandRwe(ShellCommandInput input) {
        Objects.requireNonNull(input, "input cannot be null");

        mLog.i("runShellCommandRwe(): running %s", input);

        mCalls.add(input.getCommand());

        ShellCommandOutput output = mResultExpectations.get(input.getCommand());
        mLog.i("runShellCommandRwe(): returning %s", output);

        return output;
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
                + ", mResultExpectations="
                + mResultExpectations
                + ", mCalls="
                + mCalls
                + "]";
    }
}
