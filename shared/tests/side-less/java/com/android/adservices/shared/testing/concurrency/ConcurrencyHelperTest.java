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

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.android.adservices.shared.meta_testing.FakeRealLogger;
import com.android.adservices.shared.testing.LogEntry;
import com.android.adservices.shared.testing.Logger.LogLevel;
import com.android.adservices.shared.testing.SidelessTestCase;

import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.function.ThrowingRunnable;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ConcurrencyHelperTest extends SidelessTestCase {

    private static final long SLEEP_TIMEOUT_MS = 100;

    private final FakeRealLogger mFakeLogger = new FakeRealLogger();

    private final ConcurrencyHelper mHelper = new ConcurrencyHelper(mFakeLogger);

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
    public void testRunASync_interrupted() throws Exception {
        InterruptedException e = new InterruptedException("D'OH!");
        AtomicBoolean called = new AtomicBoolean();
        ConcurrencyHelper helper =
                new ConcurrencyHelper(
                        mFakeLogger,
                        napTimeMs -> {
                            throw e;
                        });

        Thread t = helper.runAsync(SLEEP_TIMEOUT_MS, () -> called.set(true));

        assertWithMessage("result of runAsync()").that(t).isNotNull();
        expect.withMessage("runnable called when thread is interrupted")
                .that(called.get())
                .isFalse();
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

    @SuppressWarnings("FormatStringAnnotation")
    @Test
    public void testSleepOnly_nullMessage() {
        assertThrows(NullPointerException.class, () -> mHelper.sleepOnly(SLEEP_TIMEOUT_MS, null));
    }

    @Test
    public void testSleep_noArgsMessage() throws Throwable {
        testSleepNoArgsMessage(
                () -> mHelper.sleep(SLEEP_TIMEOUT_MS, "Sweet Dreams!"), "Sweet Dreams!");
    }

    @Test
    public void testSleepOnly_noArgsMessage() throws Throwable {
        testSleepNoArgsMessage(
                () -> mHelper.sleepOnly(SLEEP_TIMEOUT_MS, "Sweet Dreams!"), "Sweet Dreams!");
    }

    private void testSleepNoArgsMessage(ThrowingRunnable testCommand, String reason)
            throws Throwable {
        long timeBefore = System.currentTimeMillis();
        String threadName = Thread.currentThread().toString();

        testCommand.run();

        long delta = System.currentTimeMillis() - timeBefore;
        expect.withMessage("sleep time").that(delta).isAtLeast(SLEEP_TIMEOUT_MS);

        ImmutableList<LogEntry> logEntries = mFakeLogger.getEntries();
        assertWithMessage("log entries").that(logEntries).hasSize(2);
        LogEntry logBefore = logEntries.get(0);
        expect.withMessage("log before").that(logBefore.level).isEqualTo(LogLevel.INFO);
        expect.withMessage("log before")
                .that(logBefore.message)
                .isEqualTo(
                        "Napping "
                                + SLEEP_TIMEOUT_MS
                                + "ms on thread "
                                + threadName
                                + ". Reason: "
                                + reason);
        LogEntry logAfter = logEntries.get(1);
        expect.withMessage("log after.level").that(logAfter.level).isEqualTo(LogLevel.INFO);
        expect.withMessage("log after").that(logAfter.message).isEqualTo("Little Suzie woke up!");
    }

    @Test
    public void testSleep_formattedMessage() throws Throwable {
        testSleepFormattedMessage(
                () -> mHelper.sleep(SLEEP_TIMEOUT_MS, "Sweet Dreams %s!", "are made of this"),
                "Sweet Dreams are made of this!");
    }

    @Test
    public void testSleepOnly_formattedMessage() throws Throwable {
        testSleepFormattedMessage(
                () -> mHelper.sleepOnly(SLEEP_TIMEOUT_MS, "Sweet Dreams %s!", "are made of this"),
                "Sweet Dreams are made of this!");
    }

    private void testSleepFormattedMessage(ThrowingRunnable testCommand, String reason)
            throws Throwable {
        long timeBefore = System.currentTimeMillis();
        String threadName = Thread.currentThread().toString();

        testCommand.run();

        long delta = System.currentTimeMillis() - timeBefore;
        expect.withMessage("sleep time").that(delta).isAtLeast(SLEEP_TIMEOUT_MS);

        ImmutableList<LogEntry> logEntries = mFakeLogger.getEntries();
        assertWithMessage("log entries").that(logEntries).hasSize(2);
        LogEntry logBefore = logEntries.get(0);
        expect.withMessage("log before").that(logBefore.level).isEqualTo(LogLevel.INFO);
        expect.withMessage("log before")
                .that(logBefore.message)
                .isEqualTo(
                        "Napping "
                                + SLEEP_TIMEOUT_MS
                                + "ms on thread "
                                + threadName
                                + ". Reason: "
                                + reason);
        LogEntry logAfter = logEntries.get(1);
        expect.withMessage("log after").that(logAfter.level).isEqualTo(LogLevel.INFO);
        expect.withMessage("log after").that(logAfter.message).isEqualTo("Little Suzie woke up!");
    }

    // NOTE: it would be simpler to use a Sleeper that throws (as testRunASync_interrupted() does),
    // but interrupting the thread is more realistic (for example, the test can assert
    // Thread.currentThread().interrupt() was called)
    @Test
    public void testSleep_interrupted() throws Exception {
        long napForeverTimeMs = Long.MAX_VALUE;
        long reasonableTimeoutMs = 5_000;

        // TODO(b/342448771): should use ResultSyncCallback instead, but that class is defined in
        // the device-side project.
        ArrayBlockingQueue<Thread> threadHolder = new ArrayBlockingQueue<>(1);
        CountDownLatch threadInterruptedLatch = new CountDownLatch(1);
        AtomicBoolean threadInterrupted = new AtomicBoolean();

        // Need to sleep in a bg thread, so it can be interrupted
        Thread t =
                new Thread(
                        () -> {
                            threadHolder.offer(Thread.currentThread());

                            mHelper.sleep(napForeverTimeMs, "Don't bother me, I'm sleeping!");
                            // Make sure thread is interrupted - need to set it here as it would
                            // return false when
                            // it's check down below (in the main thread)
                            threadInterrupted.set(Thread.currentThread().isInterrupted());

                            threadInterruptedLatch.countDown();
                        },
                        "Temp thread for testSleep_interrupted()");
        t.start();

        // Barrier 1: wait until Thread's runnable started()
        Thread sleepThread = threadHolder.poll(reasonableTimeoutMs, MILLISECONDS);
        if (sleepThread == null) {
            fail("thread not properly started");
        }
        String threadName = sleepThread.toString();

        sleepThread.interrupt();

        // Barrier 2: wait until sleep() returned
        if (!threadInterruptedLatch.await(reasonableTimeoutMs, MILLISECONDS)) {
            fail("Thread " + threadName + " not interrupted in " + reasonableTimeoutMs + "ms");
        }

        // Make sure Thread.currentThread().interrupt() was called
        expect.withMessage("thread %s interrupted", threadName)
                .that(threadInterrupted.get())
                .isTrue();

        // Assert log messages
        ImmutableList<LogEntry> logEntries = mFakeLogger.getEntries();
        assertWithMessage("log entries").that(logEntries).hasSize(2);
        LogEntry logBefore = logEntries.get(0);
        expect.withMessage("log before").that(logBefore.level).isEqualTo(LogLevel.INFO);
        expect.withMessage("log before")
                .that(logBefore.message)
                .isEqualTo(
                        "Napping "
                                + napForeverTimeMs
                                + "ms on thread "
                                + threadName
                                + ". Reason: Don't bother me, I'm sleeping!");
        LogEntry logAfter = logEntries.get(1);
        expect.withMessage("log after").that(logAfter.level).isEqualTo(LogLevel.ERROR);
        expect.withMessage("log after")
                .that(logAfter.throwable)
                .isInstanceOf(InterruptedException.class);
        expect.withMessage("log after")
                .that(logAfter.message)
                .isEqualTo("Thread " + threadName + " interrupted while sleeping");
    }

    private static final class ExecutionInfo {
        public Thread thread;
        public long time;
    }
}
