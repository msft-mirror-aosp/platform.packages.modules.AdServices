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

import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.Logger.RealLogger;
import com.android.adservices.shared.testing.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Helper class for concurrency-related needs.
 *
 * <p><b>Note:</b>this class is not directly visible to tests, they should use static methods from
 * {@link com.android.adservices.shared.testing.concurrency.DeviceSideConcurrencyHelper} instead.
 */
final class ConcurrencyHelper {

    private static final AtomicInteger sThreadId = new AtomicInteger();

    private final Logger mLogger;
    private final Sleeper mSleeper;

    ConcurrencyHelper(RealLogger realLogger) {
        this(realLogger, napTimeMs -> Thread.sleep(napTimeMs));
    }

    @VisibleForTesting
    ConcurrencyHelper(RealLogger realLogger, Sleeper sleeper) {
        mLogger = new Logger(realLogger, ConcurrencyHelper.class);
        mSleeper = sleeper;
    }

    Thread runAsync(long delayMs, Runnable r) {
        Objects.requireNonNull(r, "Runnable cannot be null");
        return startNewThread(() -> sleep(delayMs, r, "runAsync() call"));
    }

    Thread startNewThread(Runnable r) {
        Objects.requireNonNull(r, "Runnable cannot be null");
        String threadName = "ConcurrencyHelper-runLaterThread-" + sThreadId.incrementAndGet();
        Thread thread = new Thread(r, threadName);
        mLogger.v("Starting new thread (%s) to run %s", threadName, r);
        thread.start();
        return thread;
    }

    @FormatMethod
    void sleep(long timeMs, @FormatString String reasonFmt, @Nullable Object... reasonArgs) {
        sleep(timeMs, /* postSleepRunnable= */ null, reasonFmt, reasonArgs);
    }

    @FormatMethod
    void sleepOnly(long timeMs, @FormatString String reasonFmt, @Nullable Object... reasonArgs)
            throws InterruptedException {
        sleepAndLog(timeMs, reasonFmt, reasonArgs);
    }

    @FormatMethod
    private void sleepAndLog(
            long timeMs, @FormatString String reasonFmt, @Nullable Object... reasonArgs)
            throws InterruptedException {
        String reason =
                String.format(
                        Objects.requireNonNull(reasonFmt, "reasonFmt cannot be null"), reasonArgs);

        mLogger.i(
                "Napping %dms on thread %s. Reason: %s",
                timeMs, Thread.currentThread().toString(), reason);
        mSleeper.sleep(timeMs);
        mLogger.i("Little Suzie woke up!");
    }

    @FormatMethod
    private void sleep(
            long timeMs,
            @Nullable Runnable postSleepRunnable,
            @FormatString String reasonFmt,
            @Nullable Object... reasonArgs) {
        try {
            sleepAndLog(timeMs, reasonFmt, reasonArgs);
            if (postSleepRunnable != null) {
                postSleepRunnable.run();
            }
        } catch (InterruptedException e) {
            mLogger.e(e, "Thread %s interrupted while sleeping", Thread.currentThread().toString());
            Thread.currentThread().interrupt();
        }
    }

    /** Abstraction used to test runAsync() when interrupted */
    interface Sleeper {
        void sleep(long napTimeMs) throws InterruptedException;
    }
}
