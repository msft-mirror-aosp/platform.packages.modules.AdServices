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

import java.util.Locale;
import java.util.Objects;

/** Abstracts the output from a Shell Command. */
public final class ShellCommandOutput {

    /* Convenience constant for a command with an empty result (output and error). */
    public static final ShellCommandOutput EMPTY_RESULT = new ShellCommandOutput("");

    private final String mOut;
    private final String mErr;

    /** Convenience constructor for when the error output is not available. */
    public ShellCommandOutput(String out) {
        this(Objects.requireNonNull(out, "out cannot be null"), /* err= */ "");
    }

    private ShellCommandOutput(Builder builder) {
        this(builder.mOut, builder.mErr);
    }

    private ShellCommandOutput(String out, String err) {
        mOut = out;
        mErr = err;
    }

    /* Gets the standard output. */
    @Nullable
    public String getOut() {
        return mOut;
    }

    /* Gets the standard err. */
    @Nullable
    public String getErr() {
        return mErr;
    }

    @Override
    public String toString() {
        if (!isEmptyOrNull(mErr) && !isEmptyOrNull(mOut)) {
            return String.format(Locale.ENGLISH, "ShellCommandOutput[out=%s, err=%s]", mOut, mErr);
        }
        return isEmptyOrNull(mErr) ? mOut : mErr;
    }

    private static boolean isEmptyOrNull(@Nullable String string) {
        return string == null || string.isEmpty();
    }

    /** Builder for {@link #ShellCommandOutput} objects. */
    public static final class Builder {
        private String mOut;
        private String mErr;

        /** Sets the standard output. */
        public Builder setOut(String out) {
            mOut = Objects.requireNonNull(out, "out cannot be null");
            return this;
        }

        /** Sets the standard error. */
        public Builder setErr(String err) {
            mErr = Objects.requireNonNull(err, "err cannot be null");
            return this;
        }

        /**
         * Builds a new {@link #ShellCommandOutput()} instance.
         *
         * @throws IllegalStateException if neither {@link #setOut(String)} nor {@link
         *     #setErr(String)} was called (with valid values).
         */
        public ShellCommandOutput build() {
            if (mOut == null && mErr == null) {
                throw new IllegalStateException("must set out or err");
            }
            return new ShellCommandOutput(this);
        }
    }
}
