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

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public final class AbstractSyncCallbackTest extends SharedSidelessTestCase {

    private static final AtomicInteger sThreadId = new AtomicInteger();

    private static final int LONGER_TIMEOUT_MS = 1000;
    private static final int SMALLER_TIMEOUT_MS = 100;

    private static final String MSG_SET_CALLED = "setCalled() called";
    private static final String MSG_SET_CALLED_RETURNING = "setCalled() returning";
    private static final String MSG_WAIT_CALLED = "waitCalled() called";
    private static final String MSG_WAIT_CALLED_RETURNING = "waitCalled() returning";
    private static final String MSG_WAIT_WITH_TIMEOUT_CALLED =
            "waitCalled(" + LONGER_TIMEOUT_MS + ", " + MILLISECONDS + ") called";
    private static final String MSG_WAIT_WITH_TIMEOUT_CALLED_RETURNING =
            "waitCalled(" + LONGER_TIMEOUT_MS + ", " + MILLISECONDS + ") returning";

    private final ConcreteSyncCallback mSingleCallback = new ConcreteSyncCallback();

    @Test
    public void testIsCalled() throws Exception {
        mSingleCallback.expectLogCalls(MSG_SET_CALLED, MSG_SET_CALLED_RETURNING);

        expect.withMessage("%s.isCalled() initially", mSingleCallback)
                .that(mSingleCallback.isCalled())
                .isFalse();

        mSingleCallback.setCalled();

        expect.withMessage("%s.isCalled() after setCalled()", mSingleCallback)
                .that(mSingleCallback.isCalled())
                .isTrue();

        mSingleCallback.assertLoggedCalls();
    }

    @Test
    public void testSetCalled_multipleTimes() throws Exception {
        mSingleCallback.expectLogCalls(
                MSG_SET_CALLED, MSG_SET_CALLED_RETURNING, MSG_SET_CALLED, MSG_SET_CALLED_RETURNING);

        mSingleCallback.setCalled();
        mSingleCallback.setCalled();

        expect.withMessage("%s.isCalled() after setCalled() (twice)", mSingleCallback)
                .that(mSingleCallback.isCalled())
                .isTrue();
    }

    @Test
    public void testWaitCalledWithTimeout_called() throws Exception {
        mSingleCallback.expectLogCalls(
                MSG_SET_CALLED,
                MSG_SET_CALLED_RETURNING,
                MSG_WAIT_WITH_TIMEOUT_CALLED,
                MSG_WAIT_WITH_TIMEOUT_CALLED_RETURNING);

        runLater(SMALLER_TIMEOUT_MS, () -> mSingleCallback.setCalled());

        mSingleCallback.waitCalled(LONGER_TIMEOUT_MS, MILLISECONDS);

        expect.withMessage("%s.isCalled() after setCalled()", mSingleCallback)
                .that(mSingleCallback.isCalled())
                .isTrue();

        mSingleCallback.assertLoggedCalls();
    }

    @Test
    public void testWaitCalledWithTimeout_neverCalled() throws Exception {
        mSingleCallback.expectLogCalls(
                MSG_WAIT_WITH_TIMEOUT_CALLED, MSG_WAIT_WITH_TIMEOUT_CALLED_RETURNING);

        SyncCallbackTimeoutException thrown =
                assertThrows(
                        SyncCallbackTimeoutException.class,
                        () -> mSingleCallback.waitCalled(LONGER_TIMEOUT_MS, MILLISECONDS));

        // Assert exception first
        expect.withMessage("exception.what on %s.waitCalled()", mSingleCallback)
                .that(thrown.getWhat())
                .isEqualTo(mSingleCallback.toString());
        expect.withMessage("exception.timeouton %s.waitCalled()", mSingleCallback)
                .that(thrown.getTimeout())
                .isEqualTo(LONGER_TIMEOUT_MS);
        expect.withMessage("exception.unit %s.waitCalled()", mSingleCallback)
                .that(thrown.getUnit())
                .isEqualTo(MILLISECONDS);

        // Then state
        expect.withMessage("%s.isCalled() after setCalled()", mSingleCallback)
                .that(mSingleCallback.isCalled())
                .isFalse();
        mSingleCallback.assertLoggedCalls();
    }

    @Test
    public void testWaitCalledWithoutTimeout_called() throws Exception {
        mSingleCallback.expectLogCalls(
                MSG_SET_CALLED,
                MSG_SET_CALLED_RETURNING,
                MSG_WAIT_CALLED,
                MSG_WAIT_CALLED_RETURNING);

        runLater(SMALLER_TIMEOUT_MS, () -> mSingleCallback.setCalled());

        mSingleCallback.waitCalled();

        expect.withMessage("%s.isCalled() after setCalled()", mSingleCallback)
                .that(mSingleCallback.isCalled())
                .isTrue();
        mSingleCallback.assertLoggedCalls();
    }

    @Test
    public void testWaitCalledWithoutTimeout_interrupted() throws Exception {
        mSingleCallback.expectLogCalls(MSG_WAIT_CALLED, MSG_WAIT_CALLED_RETURNING);
        ArrayBlockingQueue<Throwable> actualFailureQueue = new ArrayBlockingQueue<Throwable>(1);

        // Must run it in another thread so it can be interrupted
        Thread thread =
                startNewThread(
                        () -> {
                            try {
                                mSingleCallback.waitCalled();
                            } catch (Throwable t) {
                                actualFailureQueue.offer(t);
                            }
                        });
        thread.interrupt();

        Throwable actualFailure = actualFailureQueue.poll(LONGER_TIMEOUT_MS, MILLISECONDS);
        expect.withMessage("thrown exception")
                .that(actualFailure)
                .isInstanceOf(InterruptedException.class);
        mSingleCallback.assertLoggedCalls();
    }

    @Test
    public void testGetId() {
        String id1 = mSingleCallback.getId();
        expect.withMessage("id").that(id1).isNotNull();

        ConcreteSyncCallback callback2 = new ConcreteSyncCallback();
        String id2 = callback2.getId();
        expect.withMessage("id2").that(id2).isNotNull();
        expect.withMessage("id2").that(id2).isNotEqualTo(id1);
    }

    @Test
    public void testToString() {
        String toString1 = mSingleCallback.toString();
        expect.withMessage("toString() before setCalled()")
                .that(toString1)
                .matches("^\\[ConcreteSyncCallback.*missingCalls=1.*\\]$");

        ConcreteSyncCallback callback2 = new ConcreteSyncCallback();
        String toString2 = callback2.toString();

        // Ids should be different
        expect.withMessage("toString2").that(toString2).isNotEqualTo(toString1);
        // But everything else should be the same
        expect.withMessage("toString2")
                .that(toString2)
                .matches("^\\[ConcreteSyncCallback.*missingCalls=1.*\\]$");

        // missingCalls should have changed after setCalled()
        mSingleCallback.setCalled();
        expect.withMessage("toString() after setCalled()")
                .that(mSingleCallback.toString())
                .matches("^\\[ConcreteSyncCallback.*missingCalls=0.*\\]$");
    }

    @Test
    public void testCustomizeToString() {
        AbstractSyncCallback callback =
                new AbstractSyncCallback() {
                    protected void customizeToString(StringBuilder string) {
                        string.append("I AM GROOT");
                    }
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

        runLater(SMALLER_TIMEOUT_MS, () -> charmCallback.setCalled());
        charmCallback.waitCalled();

        // 3rd time is a charm!
        expect.withMessage("%s.isCalled() after call #3", charmCallback)
                .that(charmCallback.isCalled())
                .isTrue();
    }

    // TODO(b/285014040): move to superclass
    private void runLater(int when, Runnable r) {
        startNewThread(
                () -> {
                    sleep(when, "runLater()");
                    r.run();
                });
    }

    // TODO(b/285014040): move to superclass
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

    private final class ConcreteSyncCallback extends AbstractSyncCallback {
        private final List<String> mActuallogEntries = new ArrayList<>();
        // TODO(b/341797803): @Nullable
        private List<String> mExpectedLoggedMessages;
        // TODO(b/341797803): @Nullable
        private CountDownLatch mLoggedMessagesLatch;

        private ConcreteSyncCallback() {
            this(1);
        }

        private ConcreteSyncCallback(int numberOfExpectedCalls) {
            super(numberOfExpectedCalls);
        }

        public void expectLogCalls(String... messages) {
            mExpectedLoggedMessages = Arrays.asList(messages);
            mLog.v("expectLogCalls(): %s", mExpectedLoggedMessages);
            mLoggedMessagesLatch = new CountDownLatch(messages.length);
        }

        @Override
        @FormatMethod
        public void logD(@FormatString String msgFmt, Object... msgArgs) {
            log(msgFmt, msgArgs);
        }

        @Override
        @FormatMethod
        public void logV(@FormatString String msgFmt, Object... msgArgs) {
            log(msgFmt, msgArgs);
        }

        @FormatMethod
        private void log(@FormatString String msgFmt, Object... msgArgs) {
            String message = String.format(Locale.ENGLISH, msgFmt, msgArgs);
            mActuallogEntries.add(message);
            if (mLoggedMessagesLatch != null) {
                mLoggedMessagesLatch.countDown();
            }
        }

        public void assertLoggedCalls() throws InterruptedException {
            if (mLoggedMessagesLatch == null) {
                mLog.v("asserLoggedCalls(): skipping when mLoggedMessagesLatch is null");
                return;
            }
            if (!mLoggedMessagesLatch.await(LONGER_TIMEOUT_MS * 2, MILLISECONDS)) {
                throw new IllegalStateException(
                        "Timed out waiting for "
                                + mExpectedLoggedMessages
                                + "; so far received: "
                                + mActuallogEntries);
            }

            expect.withMessage("logged messages on %s", this)
                    .that(mActuallogEntries)
                    .containsExactlyElementsIn(mExpectedLoggedMessages);
        }
    }
}
