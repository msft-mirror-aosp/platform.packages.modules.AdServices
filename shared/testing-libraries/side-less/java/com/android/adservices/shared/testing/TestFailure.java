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
import com.google.common.collect.ImmutableList;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Exception used to wrap a test failure and provide more information on why it failed. */
@SuppressWarnings("OverrideThrowableToString")
public final class TestFailure extends Exception {

    @VisibleForTesting
    static final String MESSAGE_TEMPLATE = "Test failed (see %s below the stack trace)";

    private final List<String> mExtraInfo = new ArrayList<>();

    /**
     * Throws a {@link TestFailure}.
     *
     * @throws the {@code cause} itself (with the {@code extraInfo} added to it if it's already a
     *     {@link TestFailure}, or a new {@link TestFailure} with the given {@code cause} and {@code
     *     extraInfo}.
     */
    public static void throwTestFailure(Throwable cause, String extraInfo) throws TestFailure {
        Objects.requireNonNull(cause, "cause cannot be null");
        Objects.requireNonNull(extraInfo, "extraInfo cannot be null");

        if (cause instanceof TestFailure) {
            TestFailure testFailure = (TestFailure) cause;
            testFailure.mExtraInfo.add(extraInfo);
            throw testFailure;
        }

        throw new TestFailure(cause, extraInfo);
    }

    private TestFailure(Throwable cause, String extraInfo) {
        super(
                String.format(Locale.ENGLISH, MESSAGE_TEMPLATE, extraInfo),
                cause,
                /* enableSuppression= */ false,
                /* git log= */ false);
        mExtraInfo.add(extraInfo);
    }

    /**
     * @deprecated should call {@link #throwTestFailure(Throwable, String)} instead.
     */
    @Deprecated
    public TestFailure(Throwable cause, String dumpDescription, StringBuilder dump) {
        super(
                String.format(
                        Locale.ENGLISH,
                        MESSAGE_TEMPLATE,
                        Objects.requireNonNull(dumpDescription, "dumpDescription cannot be null")),
                Objects.requireNonNull(cause, "cause cannot be null"),
                /* enableSuppression= */ false,
                /* writableStackTrace= */ false);
        mExtraInfo.add(Objects.requireNonNull(dump, "dump cannot be null").toString());
    }

    @Override
    public StackTraceElement[] getStackTrace() {
        return getCause().getStackTrace();
    }

    @Override
    public void printStackTrace(PrintWriter s) {
        super.printStackTrace(s);
        mExtraInfo.forEach(extraInfo -> s.println(extraInfo));
    }

    @Override
    public void printStackTrace(PrintStream s) {
        super.printStackTrace(s);
        mExtraInfo.forEach(extraInfo -> s.println(extraInfo));
    }

    /** Gets the extra info added to the original exception. */
    public ImmutableList<String> getExtraInfo() {
        return ImmutableList.copyOf(mExtraInfo);
    }

    // toString() is overridden to remove the package name
    @Override
    public String toString() {
        return getClass().getSimpleName() + ": " + getMessage();
    }
}
