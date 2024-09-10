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
package com.android.adservices.shared.testing.concurrency;

import android.os.Handler;
import android.os.Looper;

import com.android.adservices.shared.testing.AndroidLogger;
import com.android.adservices.shared.testing.Nullable;
import com.android.adservices.shared.util.Preconditions;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.Objects;

/** Helper class for concurrency-related needs. */
public final class DeviceSideConcurrencyHelper {

    private static final ConcurrencyHelper sConcurrencyHelper =
            new ConcurrencyHelper(AndroidLogger.getInstance());

    /**
     * Starts a new thread and runs {@code r} on it after {@code timeoutMs} ms.
     *
     * @return the new thread.
     */
    public static Thread startNewThread(Runnable r) {
        return getConcurrencyHelper().startNewThread(r);
    }

    /**
     * Starts a new thread and runs {@code r} on it after {@code delayMs} ms.
     *
     * @return the new thread.
     */
    public static Thread runAsync(long delayMs, Runnable r) {
        return getConcurrencyHelper().runAsync(delayMs, r);
    }

    /**
     * Sleeps for the given amount of time, logging the reason and catching the {@link
     * InterruptedException} (if thrown while sleeping).
     */
    @FormatMethod
    public static void sleep(
            long timeMs, @FormatString String reasonFmt, @Nullable Object... reasonArgs) {
        getConcurrencyHelper().sleep(timeMs, reasonFmt, reasonArgs);
    }

    /**
     * Sleeps for the given amount of time, logging the reason.
     *
     * <p>Useful mostly on tests that must explicitly catch {@link InterruptedException} - in most
     * cases, tests should call {@link #sleep(long, String, Object...)} instead.
     */
    @FormatMethod
    public static void sleepOnly(
            long timeMs, @FormatString String reasonFmt, @Nullable Object... reasonArgs)
            throws InterruptedException {
        getConcurrencyHelper().sleepOnly(timeMs, reasonFmt, reasonArgs);
    }

    /** Runs the given runnable in the main thread. */
    public static void runOnMainThread(Runnable r) {
        Objects.requireNonNull(r, "runnable cannot be run");
        Looper looper = Looper.getMainLooper();
        Preconditions.checkState(looper != null, "Looper.getMainLooper() returned null");
        new Handler(looper).post(r);
    }

    @VisibleForTesting
    static ConcurrencyHelper getConcurrencyHelper() {
        return sConcurrencyHelper;
    }

    private DeviceSideConcurrencyHelper() {
        throw new UnsupportedOperationException("Contains only static members");
    }
}
