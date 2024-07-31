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

    ConcurrencyHelper(RealLogger realLogger) {
        mLogger = new Logger(realLogger, ConcurrencyHelper.class);
    }

    Thread runAsync(long timeoutMs, Runnable r) {
        Objects.requireNonNull(r);
        Runnable sleepingBeauty =
                () -> {
                    String threadName = Thread.currentThread().getName();
                    mLogger.v("Sleeping %d ms on %s", timeoutMs, threadName);
                    try {
                        Thread.sleep(timeoutMs);
                        mLogger.v("Woke up. Running %s", r);
                        r.run();
                        mLogger.v("Done");
                    } catch (InterruptedException e) {
                        mLogger.e(
                                e, "%s interrupted while sleeping - NOT running %s", threadName, r);
                        return;
                    }
                };
        return startNewThread(sleepingBeauty);
    }

    Thread startNewThread(Runnable r) {
        Objects.requireNonNull(r);
        String threadName = "ConcurrencyHelper-runLaterThread-" + sThreadId.incrementAndGet();
        Thread thread = new Thread(r, threadName);
        mLogger.v("Starting new thread (%s) to run %s", threadName, r);
        thread.start();
        return thread;
    }
}
