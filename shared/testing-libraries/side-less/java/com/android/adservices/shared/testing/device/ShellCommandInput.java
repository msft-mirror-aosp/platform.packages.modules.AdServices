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

import com.android.adservices.shared.testing.Nullable;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.Locale;
import java.util.Objects;

/** Abstracts the input to a Shell Command. */
public final class ShellCommandInput {

    private final String mCmd;

    /** Default constructor. */
    @FormatMethod
    public ShellCommandInput(@FormatString String cmdFmt, @Nullable Object... cmdArgs) {
        Objects.requireNonNull(cmdFmt, "cmdFmt cannot be null");
        mCmd = String.format(Locale.ENGLISH, cmdFmt, cmdArgs);
    }

    /** Gets the command. */
    public String getCommand() {
        return mCmd;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mCmd);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        ShellCommandInput other = (ShellCommandInput) obj;
        return Objects.equals(mCmd, other.mCmd);
    }

    @Override
    public String toString() {
        return mCmd;
    }
}
