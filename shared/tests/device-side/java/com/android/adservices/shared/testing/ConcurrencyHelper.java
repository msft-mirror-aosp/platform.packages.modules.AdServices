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

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

// TODO(b/I343510637): add unit tests / javadoc
public final class ConcurrencyHelper {

    private static final AtomicInteger sThreadId = new AtomicInteger();

    private static final String TAG = ConcurrencyHelper.class.getSimpleName();

    public static void runAsync(long timeoutMs, Runnable r) {
        Runnable sleepingBeauty =
                () -> {
                    Log.v(
                            TAG,
                            String.format(
                                    Locale.ENGLISH,
                                    "Sleeping %d ms on %s",
                                    timeoutMs,
                                    Thread.currentThread()));
                    // TODO(b/I343510637): use Thread.sleep() so it can be moved to side-less
                    SystemClock.sleep(timeoutMs);
                    Log.v(TAG, "Woke up");
                    r.run();
                    Log.v(TAG, "Done");
                };
        startNewThread(sleepingBeauty);
    }

    public static void runOnMainThread(Runnable r) {
        new Handler(Looper.getMainLooper()).post(r);
    }

    /** Start a new thread to run the given runnable. */
    public static Thread startNewThread(Runnable r) {
        String threadName = TAG + "-runLaterThread-" + sThreadId.incrementAndGet();
        Thread thread = new Thread(r, threadName);
        Log.v(
                TAG,
                String.format(Locale.ENGLISH, "Starting new thread (%s) to run %s", threadName, r));
        thread.start();
        return thread;
    }

    private ConcurrencyHelper() {
        throw new UnsupportedOperationException("Contains only static members");
    }
}
