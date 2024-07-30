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

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.android.adservices.shared.testing.SidelessTestCase;

import org.junit.Test;

import java.util.concurrent.ArrayBlockingQueue;

public final class ConcurrencyHelperTest extends SidelessTestCase {

    private static final long SLEEP_TIMEOUT_MS = 100;

    private final ConcurrencyHelper mHelper = new ConcurrencyHelper(mRealLogger);

    @Test
    public void testRunASync_null() throws Exception {
        assertThrows(NullPointerException.class, () -> mHelper.runAsync(SLEEP_TIMEOUT_MS, null));
    }

    @Test
    public void testRunASync() throws Exception {
        // TODO(b/342448771): should use ResultSyncCallback instead, but that class is defined in
        // the device-side project.
        ArrayBlockingQueue<ExecutionInfo> holder = new ArrayBlockingQueue<>(1);

        long before = System.currentTimeMillis();

        Thread t =
                mHelper.runAsync(
                        SLEEP_TIMEOUT_MS,
                        () -> {
                            ExecutionInfo executionInfo = new ExecutionInfo();
                            executionInfo.time = System.currentTimeMillis();
                            executionInfo.thread = Thread.currentThread();

                            mLog.d(
                                    "Called after %dms on thread %s",
                                    executionInfo.time, executionInfo.thread);
                            holder.offer(executionInfo);
                        });

        expect.withMessage("thread").that(t).isNotNull();
        ExecutionInfo executionInfo = holder.poll(SLEEP_TIMEOUT_MS + 100, MILLISECONDS);
        if (executionInfo == null) {
            fail("runnable not called");
        }
        expect.withMessage("execution thread").that(executionInfo.thread).isSameInstanceAs(t);
        expect.withMessage("execution time (should have slept at least %sms)", SLEEP_TIMEOUT_MS)
                .that(executionInfo.time)
                .isAtLeast(before + SLEEP_TIMEOUT_MS);
    }

    @Test
    public void testStartNewThread_null() {
        assertThrows(NullPointerException.class, () -> mHelper.startNewThread(null));
    }

    @Test
    public void testStartNewThread() throws Exception {
        // TODO(b/342448771): should use ResultSyncCallback instead, but that class is defined in
        // the device-side project.
        ArrayBlockingQueue<Thread> holder = new ArrayBlockingQueue<>(1);

        Thread t = mHelper.startNewThread(() -> holder.offer(Thread.currentThread()));

        expect.withMessage("thread").that(t).isNotNull();
        Thread executionThread = holder.poll(SLEEP_TIMEOUT_MS + 100, MILLISECONDS);
        if (executionThread == null) {
            fail("runnable not called");
        }
        expect.withMessage("execution thread").that(executionThread).isSameInstanceAs(t);
    }

    private static final class ExecutionInfo {
        public Thread thread;
        public long time;
    }
}
