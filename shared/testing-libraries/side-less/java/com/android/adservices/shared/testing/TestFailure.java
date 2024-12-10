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
package com.android.adservices.shared.testing;

import com.google.common.annotations.VisibleForTesting;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.Objects;

/** Exception used to wrap a test failure and provide more information on why it failed. */
@SuppressWarnings("OverrideThrowableToString")
public final class TestFailure extends Exception {

    @VisibleForTesting
    static final String MESSAGE_TEMPLATE = "Test failed (see %s below the stack trace)";

    // TODO(b/383404021): rename to extraInfo or something like that (same on constructor)
    private final String mDump;

    public TestFailure(Throwable cause, String dumpDescription, StringBuilder dump) {
        super(
                String.format(
                        Locale.ENGLISH,
                        MESSAGE_TEMPLATE,
                        Objects.requireNonNull(dumpDescription, "dumpDescription cannot be null")),
                cause,
                /* enableSuppression= */ false,
                /* writableStackTrace= */ false);
        mDump = Objects.requireNonNull(dump, "dump cannot be null").toString();
        setStackTrace(cause.getStackTrace());
    }

    @Override
    public void printStackTrace(PrintWriter s) {
        super.printStackTrace(s);
        s.println(mDump);
    }

    @Override
    public void printStackTrace(PrintStream s) {
        super.printStackTrace(s);
        s.println(mDump);
    }

    public String getExtraInfo() {
        return mDump;
    }

    // toString() is overridden to remove the package name
    @Override
    public String toString() {
        return getClass().getSimpleName() + ": " + getMessage();
    }
}
