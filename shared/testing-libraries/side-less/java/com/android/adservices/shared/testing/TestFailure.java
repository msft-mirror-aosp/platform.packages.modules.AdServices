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

import java.io.PrintStream;
import java.io.PrintWriter;

// TODO(b/328682831): add unit tests
/** Exception used to wrap a test failure and provide more information on why it failed. */
@SuppressWarnings("OverrideThrowableToString")
public final class TestFailure extends Exception {

    // TODO(b/328682831): rename to extraNnfo or something like that (same on other places)
    private final String mDump;

    // TODO(b/324919960): make it package-protected again or make sure it's unit tested.
    public TestFailure(Throwable cause, String dumpDescription, StringBuilder dump) {
        super(
                "Test failed (see " + dumpDescription + " below the stack trace)",
                cause,
                /* enableSuppression= */ false,
                /* writableStackTrace= */ false);
        mDump = dump.toString();
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

    // toString() is overridden to remove the AbstractAdServicesFlagsSetterRule$ from the name
    @Override
    public String toString() {
        return getClass().getSimpleName() + ": " + getMessage();
    }
}
