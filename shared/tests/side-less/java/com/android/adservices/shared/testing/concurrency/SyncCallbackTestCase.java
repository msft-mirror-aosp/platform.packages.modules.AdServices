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

import static com.android.adservices.shared.testing.ConcurrencyHelper.runAsync;
import static com.android.adservices.shared.testing.ConcurrencyHelper.startNewThread;
import static com.android.adservices.shared.testing.concurrency.SyncCallback.LOG_TAG;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.android.adservices.shared.meta_testing.FakeLogger;
import com.android.adservices.shared.meta_testing.LogEntry;
import com.android.adservices.shared.testing.Logger.LogLevel;
import com.android.adservices.shared.testing.Nullable;
import com.android.adservices.shared.testing.SharedSidelessTestCase;

import com.google.common.collect.ImmutableList;

import org.junit.AssumptionViolatedException;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Base class for all {@code SyncCallback} implementations. */
public abstract class SyncCallbackTestCase<CB extends SyncCallback> extends SharedSidelessTestCase {

    protected static final long INJECTION_TIMEOUT_MS = 200;
    protected static final long CALLBACK_TIMEOUT_MS = INJECTION_TIMEOUT_MS + 400;

    protected final FakeLogger mFakeLogger = new FakeLogger();

    private final SyncCallbackSettings.Builder mFakeLoggerSettingsBuilder =
            new SyncCallbackSettings.Builder(mFakeLogger);
    private final SyncCallbackSettings.Builder mDefaultSettingsBuilder =
            mFakeLoggerSettingsBuilder
                    .setFailIfCalledOnMainThread(supportsFailIfCalledOnMainThread())
                    .setMaxTimeoutMs(CALLBACK_TIMEOUT_MS);

    protected final SyncCallbackSettings mDefaultSettings = mDefaultSettingsBuilder.build();

    /**
     * Gets a new callback to be used in the test.
     *
     * <p>Each call should return a different object.
     */
    protected abstract CB newCallback(SyncCallbackSettings settings);

    /**
     * Checks whether the callback supports being constructor with a {@link SyncCallbackSettings
     * settings} object that supports {@link SyncCallbackSettings#isFailIfCalledOnMainThread()
     * failing if called in the main thread}.
     *
     * @return {@code true} by default.
     */
    protected boolean supportsFailIfCalledOnMainThread() {
        return true;
    }

    /** Makes sure subclasses provide distinct callbacks, as some tests rely on that. */
    @Test
    public final void testNewCallback() {
        CB callback1 = newCallback(mDefaultSettings);
        expect.withMessage("1st callback").that(callback1).isNotNull();

        CB callback2 = newCallback(mDefaultSettings);
        expect.withMessage("2nd callback").that(callback2).isNotNull();
        expect.withMessage("2nd callback").that(callback2).isNotSameInstanceAs(callback1);
    }

    @Test
    public final void testHasExpectedConstructors() throws Exception {
        CB callback = newCallback(mDefaultSettings);
        @SuppressWarnings("unchecked")
        Class<CB> callbackClass = (Class<CB>) callback.getClass();

        Constructor<CB> defaultConstructor = getConstructor(callbackClass);
        expect.withMessage("Default constructor (%s)", callbackClass)
                .that(defaultConstructor)
                .isNotNull();

        Constructor<CB> settingsConstructor =
                getConstructor(callbackClass, SyncCallbackSettings.class);
        expect.withMessage("%s(SyncCallbackSettings) constructor", callbackClass)
                .that(settingsConstructor)
                .isNotNull();
    }

    @Test
    public final void testConstructor_cannotFailOnMainThread() throws Exception {
        assumeCannotFailIfCalledOnMainThread();
        SyncCallbackSettings settings =
                mFakeLoggerSettingsBuilder.setFailIfCalledOnMainThread(true).build();

        assertThrows(IllegalArgumentException.class, () -> newCallback(settings));
    }

    @Nullable
    private Constructor<CB> getConstructor(Class<CB> callbackClass, Class<?>... parameterTypes) {
        try {
            return callbackClass.getDeclaredConstructor(parameterTypes);
        } catch (Exception e) {
            mLog.e("Failed to get constructor for class %s: %s", callbackClass, e);
            return null;
        }
    }

    @Test
    public final void testGetSettings() {
        CB callback = newCallback(mDefaultSettings);

        expect.withMessage("getSettings()")
                .that(callback.getSettings())
                .isSameInstanceAs(mDefaultSettings);
    }

    @Test
    public final void testGetId() {
        CB callback1 = newCallback(mDefaultSettings);
        String id1 = callback1.getId();
        expect.withMessage("id").that(id1).isNotNull();

        CB callback2 = newCallback(mDefaultSettings);
        String id2 = callback2.getId();
        expect.withMessage("id2").that(id2).isNotNull();
        expect.withMessage("id2").that(id2).isNotEqualTo(id1);
    }

    @Test
    public final void testAssertCalled() throws Exception {
        var callback = newCallback(mDefaultSettings);
        assumeCallbackSupportsSetCalled(callback);
        var log = new LogChecker(callback);

        // Check state before
        expectIsCalledAndNumberCalls(callback, "before setCalled()", false, 0);

        Thread t = runAsync(INJECTION_TIMEOUT_MS, () -> callback.setCalled());
        callback.assertCalled();

        // Check state after
        expectIsCalledAndNumberCalls(callback, "after setCalled()", true, 1);

        expectLoggedCalls(
                log.d("setCalled() called on " + t.getName()),
                log.v("setCalled() returning"),
                log.d("assertCalled() called on " + currentThread().getName()),
                log.v("assertCalled() returning"));

        // Further calls - make sure number actual calls keeps increasing
        // (don't need to call on bg because it's already called)
        callback.setCalled();
        expect.withMessage("%s.getNumberActualCalls() after 2nd call", callback)
                .that(callback.getNumberActualCalls())
                .isEqualTo(2);
    }

    @Test
    public final void testAssertCalled_neverCalled() throws Exception {
        var callback = newCallback(mDefaultSettings);
        assumeCallbackSupportsSetCalled(callback);
        var log = new LogChecker(callback);

        var thrown =
                assertThrows(SyncCallbackTimeoutException.class, () -> callback.assertCalled());

        expect.withMessage("e.getTimeout()")
                .that(thrown.getTimeout())
                .isEqualTo(mDefaultSettings.getMaxTimeoutMs());
        expect.withMessage("e.getUnit()()").that(thrown.getUnit()).isEqualTo(TimeUnit.MILLISECONDS);

        expectIsCalledAndNumberCalls(callback, "after setCalled()", false, 0);
        expectLoggedCalls(
                log.d("assertCalled() called on " + currentThread().getName()),
                log.e("assertCalled() failed: " + thrown));
    }

    @Test
    public final void testAssertCalled_interrupted() throws Exception {
        var callback = newCallback(mDefaultSettings);
        assumeCallbackSupportsSetCalled(callback);
        var log = new LogChecker(callback);
        ArrayBlockingQueue<Throwable> actualFailureQueue = new ArrayBlockingQueue<>(1);

        // Must run it in another thread so it can be interrupted
        Thread thread =
                startNewThread(
                        () -> {
                            try {
                                callback.assertCalled();
                            } catch (Throwable t) {
                                actualFailureQueue.offer(t);
                            }
                        });
        thread.interrupt();

        Throwable thrown = actualFailureQueue.poll(CALLBACK_TIMEOUT_MS, MILLISECONDS);
        expect.withMessage("thrown exception")
                .that(thrown)
                .isInstanceOf(InterruptedException.class);

        expectIsCalledAndNumberCalls(callback, "after interrupted", false, 0);
        expectLoggedCalls(
                log.d("assertCalled() called on " + thread.getName()),
                log.e("assertCalled() failed: " + thrown));
    }

    @Test
    public final void testAssertCalled_multipleCalls() throws Exception {
        SyncCallbackSettings settings = mDefaultSettingsBuilder.setExpectedNumberCalls(2).build();
        CB callback = newCallback(settings);
        assumeCallbackSupportsSetCalled(callback);

        // 1st call
        runAsync(INJECTION_TIMEOUT_MS, () -> callback.setCalled());
        assertThrows(SyncCallbackTimeoutException.class, () -> callback.assertCalled());

        expectIsCalledAndNumberCalls(callback, "after 1st setCalled()", false, 1);

        // 2nd call
        runAsync(INJECTION_TIMEOUT_MS, () -> callback.setCalled());
        callback.assertCalled();

        expectIsCalledAndNumberCalls(callback, "after 2nd setCalled()", true, 2);

        // Further calls - make sure number actual calls keeps increasing
        // (don't need to call on bg because it's already called)
        callback.setCalled();
        expectIsCalledAndNumberCalls(callback, "after 3rd setCalled()", true, 3);
    }

    @Test
    public final void testAssertCalled_multipleCallsFromMultipleCallbacks_firstFinishFirst()
            throws Exception {
        SyncCallbackSettings settings = mDefaultSettingsBuilder.setExpectedNumberCalls(4).build();
        CB callback1 = newCallback(settings);
        CB callback2 = newCallback(settings);
        assumeCallbackSupportsSetCalled(callback1);

        // 1st call on 1st callback
        runAsync(INJECTION_TIMEOUT_MS, () -> callback1.setCalled());
        assertThrows(SyncCallbackTimeoutException.class, () -> callback1.assertCalled());
        assertThrows(SyncCallbackTimeoutException.class, () -> callback2.assertCalled());
        expectIsCalled(callback1, "after 1st call on 1st callback", false);
        expectIsCalled(callback2, "after 1st call on 1st callback", false);

        // 1st call on 2nd callback
        runAsync(INJECTION_TIMEOUT_MS, () -> callback2.setCalled());
        assertThrows(SyncCallbackTimeoutException.class, () -> callback1.assertCalled());
        assertThrows(SyncCallbackTimeoutException.class, () -> callback2.assertCalled());
        expectIsCalled(callback1, "after 1st call on 2nd callback", false);
        expectIsCalled(callback2, "after 1st call on 2nd callback", false);

        // 2nd call on 2nd callback
        runAsync(INJECTION_TIMEOUT_MS, () -> callback2.setCalled());
        assertThrows(SyncCallbackTimeoutException.class, () -> callback1.assertCalled());
        assertThrows(SyncCallbackTimeoutException.class, () -> callback2.assertCalled());
        expectIsCalled(callback1, "after 2nd call on 2nd callback", false);
        expectIsCalled(callback2, "after 2nd call on 2nd callback", false);

        // 2nd call on 1st callback
        runAsync(INJECTION_TIMEOUT_MS, () -> callback1.setCalled());
        callback1.assertCalled();
        callback2.assertCalled();
        expectIsCalledAndNumberCalls(callback1, "after 2nd call on 1st callback", true, 2);
        expectIsCalledAndNumberCalls(callback2, "after 2nd call on 1st callback", true, 2);

        // Further calls - make sure number actual calls keeps increasing
        // (don't need to call on bg because it's already called)
        callback1.setCalled();
        expectIsCalledAndNumberCalls(callback1, "after 3rd call on 1st callback", true, 3);
        expectIsCalledAndNumberCalls(callback2, "after 3rd call on 1st callback", true, 2);
        callback2.setCalled();
        expectIsCalledAndNumberCalls(callback1, "after 3rd call on 2nd callback", true, 3);
        expectIsCalledAndNumberCalls(callback2, "after 3rd call on 2nd callback", true, 3);
    }

    @Test
    public final void testAssertCalled_multipleCallsFromMultipleCallbacks_secondFinishFirst()
            throws Exception {
        SyncCallbackSettings settings = mDefaultSettingsBuilder.setExpectedNumberCalls(4).build();
        CB callback1 = newCallback(settings);
        CB callback2 = newCallback(settings);
        assumeCallbackSupportsSetCalled(callback1);

        // 1st call on 1st callback
        runAsync(INJECTION_TIMEOUT_MS, () -> callback1.setCalled());
        assertThrows(SyncCallbackTimeoutException.class, () -> callback1.assertCalled());
        assertThrows(SyncCallbackTimeoutException.class, () -> callback2.assertCalled());
        expectIsCalled(callback1, "after 1st call on 1st callback", false);
        expectIsCalled(callback2, "after 1st call on 1st callback", false);

        // 1st call on 2nd callback
        runAsync(INJECTION_TIMEOUT_MS, () -> callback2.setCalled());
        assertThrows(SyncCallbackTimeoutException.class, () -> callback1.assertCalled());
        assertThrows(SyncCallbackTimeoutException.class, () -> callback2.assertCalled());
        expectIsCalled(callback1, "after 1st call on 2nd callback", false);
        expectIsCalled(callback2, "after 1st call on 2nd callback", false);

        // 2nd call on 1st callback
        runAsync(INJECTION_TIMEOUT_MS, () -> callback1.setCalled());
        assertThrows(SyncCallbackTimeoutException.class, () -> callback1.assertCalled());
        assertThrows(SyncCallbackTimeoutException.class, () -> callback2.assertCalled());
        expectIsCalled(callback1, "after 2nd call on 1st callback", false);
        expectIsCalled(callback2, "after 2nd call on 1st callback", false);

        // 2nd call on 2nd callback
        runAsync(INJECTION_TIMEOUT_MS, () -> callback2.setCalled());
        callback1.assertCalled();
        callback2.assertCalled();
        expectIsCalledAndNumberCalls(callback1, "after 2nd call on 2nd callback", true, 2);
        expectIsCalledAndNumberCalls(callback2, "after 2nd call on 2nd callback", true, 2);

        // Further calls - make sure number actual calls keeps increasing
        // (don't need to call on bg because it's already called)
        callback1.setCalled();
        expectIsCalledAndNumberCalls(callback1, "after 3rd call on 1st callback", true, 3);
        expectIsCalledAndNumberCalls(callback2, "after 3rd call on 1st callback", true, 2);
        callback2.setCalled();
        expectIsCalledAndNumberCalls(callback1, "after 3rd call on 2nd callback", true, 3);
        expectIsCalledAndNumberCalls(callback2, "after 3rd call on 2nd callback", true, 3);
    }

    @Test
    public final void testAssertCalled_failsWhenCalledOnMainThread() throws Exception {
        assumeCanFailIfCalledOnMainThread();
        SyncCallbackSettings settings =
                new SyncCallbackSettings.Builder(mFakeLogger, () -> Boolean.TRUE)
                        .setMaxTimeoutMs(CALLBACK_TIMEOUT_MS)
                        .setFailIfCalledOnMainThread(true)
                        .build();

        var callback = newCallback(settings);
        assumeCallbackSupportsSetCalled(callback);
        var log = new LogChecker(callback);

        // setCalled() passes...
        // NOTE: not really the main thread, as it's emulated
        Thread mainThread = runAsync(INJECTION_TIMEOUT_MS, () -> callback.setCalled());

        // ...but then assertCalled() should fails
        var thrown = assertThrows(CalledOnMainThreadException.class, () -> callback.assertCalled());
        expect.withMessage("thrown")
                .that(thrown)
                .hasMessageThat()
                .contains("setCalled() called on main thread (" + mainThread.getName() + ")");
        expect.withMessage("toString() after thrown")
                .that(callback.toString())
                .contains("onAssertCalledException=" + thrown);

        expectIsCalledAndNumberCalls(callback, "after setCalled()", true, 1);

        expectLoggedCalls(
                log.d("setCalled() called on " + mainThread.getName()),
                        log.v("setCalled() returning"),
                log.d("assertCalled() called on " + currentThread().getName()),
                        log.e("assertCalled() failed: " + thrown));
    }


    /** Ignore the test if the callback supports {@code assertCalled()}. */
    protected final void assumeCallbackSupportsSetCalled(SyncCallback callback) {
        if (!callback.supportsSetCalled()) {
            assertThrows(UnsupportedOperationException.class, () -> callback.setCalled());
            throw new AssumptionViolatedException("Callback doesn't support setCalled()");
        }
    }

    /** Helper method to assert the value of {@code isCalled()}. */
    protected final void expectIsCalled(
            SyncCallback callback, String when, boolean expectedIsCalled) {
        expect.withMessage("%s.isCalled() %s", callback, when)
                .that(callback.isCalled())
                .isEqualTo(expectedIsCalled);
    }

    /**
     * Helper method to assert the value of {@code isCalled()} and {@code getNumberActualCalls()}.
     */
    protected final void expectIsCalledAndNumberCalls(
            SyncCallback callback, String when, boolean expectedIsCalled, int expectedNumberCalls) {
        expectIsCalled(callback, when, expectedIsCalled);
        expect.withMessage("%s.getNumberActualCalls() %s", callback, when)
                .that(callback.getNumberActualCalls())
                .isEqualTo(expectedNumberCalls);
    }

    /** Helper methods to assert calls to the callback {@code logX()} methods. */
    public final void expectLoggedCalls(@Nullable LogEntry... expectedEntries) {
        ImmutableList<LogEntry> entries = mFakeLogger.getEntries();
        expect.withMessage("log entries").that(entries).containsExactlyElementsIn(expectedEntries);
    }

    private void assumeCannotFailIfCalledOnMainThread() {
        assumeFalse(
                "callback can fail if called on main thread", supportsFailIfCalledOnMainThread());
    }

    private void assumeCanFailIfCalledOnMainThread() {
        assumeTrue(
                "callback cannot fail if called on main thread",
                supportsFailIfCalledOnMainThread());
    }

    /**
     * Helper class used to to assert calls to the callback {@code logX()} methods.
     *
     * <p><b>NOTE: </b>must be called BEFORE the callback is used, as it will change what's returned
     * by {@code toString()}.
     */
    protected static final class LogChecker {

        public final AbstractSyncCallback callback;

        public LogChecker(SyncCallback callback) {
            if (!(callback instanceof AbstractSyncCallback)) {
                throw new IllegalArgumentException(
                        "Not an instance of AbstractSyncCallback: " + callback);
            }
            this.callback = AbstractSyncCallback.class.cast(callback);
            this.callback.setCustomizedToString(", IGNORING_VOLATILE_FIELDS");
        }

        public LogEntry e(String expectedMessage) {
            return new LogEntry(LogLevel.ERROR, LOG_TAG, callback + ": " + expectedMessage);
        }

        public LogEntry d(String expectedMessage) {
            return new LogEntry(
                    LogLevel.DEBUG, LOG_TAG, callback.toStringLite() + ": " + expectedMessage);
        }

        public LogEntry v(String expectedMessage) {
            return new LogEntry(LogLevel.VERBOSE, LOG_TAG, callback + ": " + expectedMessage);
        }
    }
}