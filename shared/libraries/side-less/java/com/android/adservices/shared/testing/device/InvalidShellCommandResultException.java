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
package com.android.adservices.shared.testing.device;

import java.util.Objects;

/** Custom exception used to represent a Shell command that returned an unexpected result. */
@SuppressWarnings("serial")
public final class InvalidShellCommandResultException extends RuntimeException {

    private final ShellCommandInput mInput;
    private final ShellCommandOutput mOutput;

    /** Default constructor. */
    public InvalidShellCommandResultException(ShellCommandInput input, ShellCommandOutput output) {
        super("Input: '" + input + "' Output: '" + output + "'");
        mInput = Objects.requireNonNull(input, "input cannot be null");
        mOutput = Objects.requireNonNull(output, "output cannot be null");
    }

    /** Gets the input. */
    public ShellCommandInput getInput() {
        return mInput;
    }

    /** Gets the output. */
    public ShellCommandOutput getOutput() {
        return mOutput;
    }
}
