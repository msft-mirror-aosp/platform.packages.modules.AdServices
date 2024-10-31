/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.adservices.shared.testing.Logger.LogLevel;
import com.android.adservices.shared.testing.Logger.RealLogger;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.io.PrintStream;
import java.util.Objects;

/**
 * Simple implementation of {@link RealLogger} that logs messages on {@code System.out} and errors
 * on {@code System.err}, so it can be used on "side-less" tests (as device-side and host-side
 * classes would be using {@code AndroidLogger} or {@code ConsoleLogger} respectively).
 */
public final class StandardStreamsLogger implements RealLogger {

    private static final StandardStreamsLogger sInstance = new StandardStreamsLogger();

    public static StandardStreamsLogger getInstance() {
        return sInstance;
    }

    private final PrintStream mOut;
    private final PrintStream mErr;

    private StandardStreamsLogger() {
        this(System.out, System.err);
    }

    @VisibleForTesting
    StandardStreamsLogger(PrintStream out, PrintStream err) {
        mOut = Objects.requireNonNull(out, "out cannot be null");
        mErr = Objects.requireNonNull(err, "err cannot be null");
    }

    @Override
    @FormatMethod
    public void log(LogLevel level, String tag, @FormatString String msgFmt, Object... msgArgs) {
        String msg = String.format(msgFmt, msgArgs);

        mOut.printf("%s %s: %s\n", tag, level, msg);
    }

    @Override
    @FormatMethod
    public void log(
            LogLevel level,
            String tag,
            Throwable throwable,
            @FormatString String msgFmt,
            Object... msgArgs) {
        String msg = String.format(msgFmt, msgArgs);

        mErr.printf("%s %s: %s\n", tag, level, msg);
        throwable.printStackTrace(mErr);
    }

    @Override
    public String toString() {
        return StandardStreamsLogger.class.getSimpleName();
    }
}
