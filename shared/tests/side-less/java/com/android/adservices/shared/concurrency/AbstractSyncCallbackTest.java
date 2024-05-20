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
package com.android.adservices.shared.concurrency;

import static org.junit.Assert.assertThrows;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.android.adservices.shared.testing.SharedSidelessTestCase;

import org.junit.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class AbstractSyncCallbackTest extends SharedSidelessTestCase {

    private static final AtomicInteger sThreadId = new AtomicInteger();

    private static final int WAIT_TIMEOUT_MS = 500;

    private final ConcreteSyncCallback mSingleCallCallback = new ConcreteSyncCallback();

    @Test
    public void testIsCalled() {
        expect.withMessage("%s.isCalled() initially", mSingleCallCallback)
                .that(mSingleCallCallback.isCalled())
                .isFalse();

        mSingleCallCallback.setCalled();

        expect.withMessage("%s.isCalled() after setCalled()", mSingleCallCallback)
                .that(mSingleCallCallback.isCalled())
                .isTrue();
        assertLogSetCalled(1);
    }

    @Test
    public void testSetCalled_multipleTimes() {
        mSingleCallCallback.setCalled();
        mSingleCallCallback.setCalled();

        expect.withMessage("%s.isCalled() after setCalled() (twice)", mSingleCallCallback)
                .that(mSingleCallCallback.isCalled())
                .isTrue();
        assertLogSetCalled(2);
    }

    @Test
    public void testWaitCalledWithTimeout_called() throws Exception {
        runLater(WAIT_TIMEOUT_MS / 2, () -> mSingleCallCallback.setCalled());

        mSingleCallCallback.waitCalled(WAIT_TIMEOUT_MS, MILLISECONDS);

        expect.withMessage("%s.isCalled() after setCalled()", mSingleCallCallback)
                .that(mSingleCallCallback.isCalled())
                .isTrue();
        assertLogWaitWithTimeoutCalled();
    }

    @Test
    public void testWaitCalledWithTimeout_neverCalled() throws Exception {
        IllegalStateException thrown =
                assertThrows(
                        IllegalStateException.class,
                        () -> mSingleCallCallback.waitCalled(WAIT_TIMEOUT_MS, MILLISECONDS));

        expect.withMessage("timeout exception on %s.waitCalled()", mSingleCallCallback)
                .that(thrown)
                .hasMessageThat()
                .contains(WAIT_TIMEOUT_MS + " " + MILLISECONDS);
        expect.withMessage("%s.isCalled() after setCalled()", mSingleCallCallback)
                .that(mSingleCallCallback.isCalled())
                .isFalse();
        assertLogWaitWithTimeoutCalled();
    }

    @Test
    public void testWaitCalledWithoutTimeout_called() throws Exception {
        runLater(WAIT_TIMEOUT_MS / 2, () -> mSingleCallCallback.setCalled());

        mSingleCallCallback.waitCalled();

        expect.withMessage("%s.isCalled() after setCalled()", mSingleCallCallback)
                .that(mSingleCallCallback.isCalled())
                .isTrue();
        assertLogWaitCalled();
    }

    @Test
    public void testWaitCalledWithoutTimeout_interrupted() throws Exception {
        ArrayBlockingQueue<Throwable> actualFailureQueue = new ArrayBlockingQueue<Throwable>(1);

        // Must run it in another thread so it can be interrupted
        Thread thread =
                startNewThread(
                        () -> {
                            try {
                                mSingleCallCallback.waitCalled();
                            } catch (Throwable t) {
                                actualFailureQueue.offer(t);
                            }
                        });
        thread.interrupt();

        Throwable actualFailure = actualFailureQueue.poll(WAIT_TIMEOUT_MS, MILLISECONDS);

        expect.withMessage("thrown exception")
                .that(actualFailure)
                .isInstanceOf(InterruptedException.class);
        assertLogWaitCalled();
    }

    @Test
    public void testToString() {
        String toString1 = mSingleCallCallback.toString();
        expect.withMessage("toString() before setCalled()")
                .that(toString1)
                .matches("^\\[ConcreteSyncCallback:.*missingCalls=1.*\\]$");

        ConcreteSyncCallback callback2 = new ConcreteSyncCallback();
        String toString2 = callback2.toString();

        // Ids should be different
        expect.withMessage("toString2").that(toString2).isNotEqualTo(toString1);
        // But everything else should be the same
        expect.withMessage("toString2")
                .that(toString2)
                .matches("^\\[ConcreteSyncCallback:.*missingCalls=1.*\\]$");

        // missingCalls should have changed after setCalled()
        mSingleCallCallback.setCalled();
        expect.withMessage("toString() after setCalled()")
                .that(mSingleCallCallback.toString())
                .matches("^\\[ConcreteSyncCallback:.*missingCalls=0.*\\]$");
    }

    @Test
    public void testCustomizeToString() {
        AbstractSyncCallback callback =
                new AbstractSyncCallback() {
                    protected void customizeToString(StringBuilder string) {
                        string.append("I AM GROOT");
                    }
                    ;
                };

        expect.withMessage("customized toString() ")
                .that(callback.toString())
                .contains("I AM GROOT");
    }

    @Test
    public void testMultipleCalls() throws Exception {
        ConcreteSyncCallback charmCallback = new ConcreteSyncCallback(3);

        charmCallback.setCalled();
        expect.withMessage("%s.isCalled() after call #1", charmCallback)
                .that(charmCallback.isCalled())
                .isFalse();
        charmCallback.setCalled();
        expect.withMessage("%s.isCalled() after call #2", charmCallback)
                .that(charmCallback.isCalled())
                .isFalse();

        runLater(WAIT_TIMEOUT_MS / 2, () -> charmCallback.setCalled());
        charmCallback.waitCalled();

        // 3rd time is a charm!
        expect.withMessage("%s.isCalled() after call #3", charmCallback)
                .that(charmCallback.isCalled())
                .isTrue();
    }

    private void runLater(int when, Runnable r) {
        startNewThread(
                () -> {
                    sleep(when, "runLater()");
                    r.run();
                });
    }

    private Thread startNewThread(Runnable r) {
        String threadName = mLog.getTag() + "-runLaterThread-" + sThreadId.incrementAndGet();
        mLog.d("Starting new thread (%s) to run %s", threadName, r);
        Thread thread = new Thread(() -> r.run());
        thread.start();
        return thread;
    }

    // TODO(b/285014040): use sleep from super class
    private void sleep(int durationMs, String msg) {
        String threadName = Thread.currentThread().getName();
        try {
            mLog.d("Napping %d ms on %s. Reason: %s", durationMs, threadName, msg);
            Thread.sleep(durationMs);
            mLog.v("Little Suzie (%s) woke up", threadName);
        } catch (InterruptedException e) {
            mLog.w(e, "Interrupted while napping for %s", msg);
            Thread.currentThread().interrupt();
        }
    }

    private void assertLogSetCalled(int numberTimes) {
        expect.withMessage("number of calls to %s.logSetCalled()", mSingleCallCallback)
                .that(mSingleCallCallback.numberOfLogSetCalledCalls)
                .isEqualTo(numberTimes);
    }

    private void assertLogWaitCalled() {
        expect.withMessage("number of calls to %s.logWaitCalled()", mSingleCallCallback)
                .that(mSingleCallCallback.numberOfLogWaitCalls)
                .isEqualTo(1);
    }

    private void assertLogWaitWithTimeoutCalled() {
        TimeUnit unit = mSingleCallCallback.unitOnLogWaitWithTimeOutCall;
        boolean called = unit != null;
        expect.withMessage("%s.logWaitCalled(timeout, unit) called", mSingleCallCallback)
                .that(called)
                .isTrue();
        if (called) {
            expect.withMessage(
                            "timeout on %s.logWaitCalled(timeout, unit) called",
                            mSingleCallCallback)
                    .that(mSingleCallCallback.timeoutOnLogWaitWithTimeOutCall)
                    .isEqualTo(WAIT_TIMEOUT_MS);
            expect.withMessage(
                            "unit on %s.logWaitCalled(timeout, unit) called", mSingleCallCallback)
                    .that(unit)
                    .isEqualTo(MILLISECONDS);
        }
    }

    private static final class ConcreteSyncCallback extends AbstractSyncCallback {
        public int numberOfLogSetCalledCalls;
        public int numberOfLogWaitCalls;

        // For simplicity, we're assuming it will be called just once
        public long timeoutOnLogWaitWithTimeOutCall;
        public TimeUnit unitOnLogWaitWithTimeOutCall;

        private ConcreteSyncCallback() {
            this(1);
        }

        private ConcreteSyncCallback(int numberOfExpectedCalls) {
            super(numberOfExpectedCalls);
        }

        @Override
        protected void logSetCalled() {
            numberOfLogSetCalledCalls++;
        }

        @Override
        protected void logWaitCalled() throws InterruptedException {
            numberOfLogWaitCalls++;
        }

        @Override
        protected void logWaitCalled(long timeout, TimeUnit unit) throws InterruptedException {
            timeoutOnLogWaitWithTimeOutCall = timeout;
            unitOnLogWaitWithTimeOutCall = unit;
        }
    }
}
