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

import static com.android.adservices.shared.meta_testing.LogEntry.Subject.logEntry;
import static com.android.adservices.shared.testing.concurrency.SyncCallbackSettings.DEFAULT_TIMEOUT_MS;
import static com.android.adservices.shared.testing.concurrency.AbstractSyncCallback.LOG_TAG;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.FakeLogger;
import com.android.adservices.shared.meta_testing.LogEntry;
import com.android.adservices.shared.testing.Logger.LogLevel;
import com.android.adservices.shared.testing.Logger.RealLogger;
import com.android.adservices.shared.testing.SharedSidelessTestCase;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class SidelessSyncCallbackTest extends SharedSidelessTestCase {

    private static final AtomicInteger sThreadId = new AtomicInteger();

    private static final long SMALLER_TIMEOUT_MS = DEFAULT_TIMEOUT_MS / 10;

    private final FakeLogger mFakeLogger = new FakeLogger();

    private final ConcreteSidelessTestSyncCallback mCallback =
            new ConcreteSidelessTestSyncCallback(mFakeLogger);

    @Test
    public void testGetSettings() {
        SyncCallbackSettings settings = SyncCallbackSettings.newDefaultSettings();
        AbstractSidelessTestSyncCallback callback =
                new AbstractSidelessTestSyncCallback(mFakeLogger, settings) {};

        expect.withMessage("getSettings()").that(callback.getSettings()).isSameInstanceAs(settings);
    }

    @Test
    public void testAssertCalled() throws Exception {
        expect.withMessage("%s.setCalled() before", mCallback).that(mCallback.isCalled()).isFalse();
        runLater(SMALLER_TIMEOUT_MS, () -> mCallback.setCalled());

        mCallback.assertCalled();
        expect.withMessage("%s.setCalled() after", mCallback).that(mCallback.isCalled()).isTrue();
    }

    @Test
    public void testAssertCalled_timedOut() throws Exception {
        expect.withMessage("%s.setCalled() after", mCallback).that(mCallback.isCalled()).isFalse();
    }

    @Test
    public void testUnsupportedMethods() throws Exception {
        assertThrows(UnsupportedOperationException.class, () -> mCallback.waitCalled());
        assertThrows(
                UnsupportedOperationException.class,
                () -> mCallback.waitCalled(42, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testLogE() throws Exception {
        mCallback.logE("%d D'OH!s", 42);

        ImmutableList<LogEntry> logEntries = mFakeLogger.getEntries();
        assertWithMessage("log entries").that(logEntries).hasSize(1);
        expect.withMessage("logged message")
                .about(logEntry())
                .that(logEntries.get(0))
                .hasLevel(LogLevel.ERROR)
                .hasTag(LOG_TAG)
                .hasMessage(mCallback + ": 42 D'OH!s");
    }

    @Test
    public void testLogD() throws Exception {
        mCallback.logD("Dude: %s", "Sweet!");

        ImmutableList<LogEntry> logEntries = mFakeLogger.getEntries();
        assertWithMessage("log entries").that(logEntries).hasSize(1);
        expect.withMessage("logged message")
                .about(logEntry())
                .that(logEntries.get(0))
                .hasLevel(LogLevel.DEBUG)
                .hasTag(LOG_TAG)
                .hasMessage("[" + mCallback.getId() + "]: Dude: Sweet!");
    }

    @Test
    public void testLogV() throws Exception {
        mCallback.logV("Vuve: %s", "Sddeet!");

        ImmutableList<LogEntry> logEntries = mFakeLogger.getEntries();
        assertWithMessage("log entries").that(logEntries).hasSize(1);
        expect.withMessage("logged message")
                .about(logEntry())
                .that(logEntries.get(0))
                .hasLevel(LogLevel.VERBOSE)
                .hasTag(LOG_TAG)
                .hasMessage(mCallback + ": Vuve: Sddeet!");
    }

    private static final class ConcreteSidelessTestSyncCallback
            extends AbstractSidelessTestSyncCallback {
        ConcreteSidelessTestSyncCallback(RealLogger realLogger) {
            super(realLogger, SyncCallbackSettings.newDefaultSettings());
        }
    }

    // TODO(b/285014040): move to ConcurrencyHelper
    private void runLater(long when, Runnable r) {
        startNewThread(
                () -> {
                    sleep(when, "runLater()");
                    r.run();
                });
    }

    // TODO(b/285014040): move to ConcurrencyHelper
    private Thread startNewThread(Runnable r) {
        String threadName = mLog.getTag() + "-runLaterThread-" + sThreadId.incrementAndGet();
        mLog.d("Starting new thread (%s) to run %s", threadName, r);
        Thread thread = new Thread(() -> r.run());
        thread.start();
        return thread;
    }

    // TODO(b/285014040): use sleep from super class
    private void sleep(long durationMs, String msg) {
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
}
