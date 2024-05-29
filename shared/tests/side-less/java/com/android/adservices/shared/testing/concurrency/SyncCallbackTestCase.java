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

import static org.junit.Assert.assertThrows;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.android.adservices.shared.meta_testing.FakeLogger;
import com.android.adservices.shared.testing.SharedSidelessTestCase;

import org.junit.AssumptionViolatedException;
import org.junit.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Base class for all {@code SyncCallback} implementations. */
public abstract class SyncCallbackTestCase<CB extends SyncCallback> extends SharedSidelessTestCase {

    private static final Supplier<Boolean> IS_MAIN_THREAD_SUPPLIER = () -> Boolean.FALSE;

    protected static final long INJECTION_TIMEOUT_MS = 200;
    protected static final long CALLBACK_TIMEOUT_MS = INJECTION_TIMEOUT_MS + 400;

    private final FakeLogger mFakeLogger = new FakeLogger();

    private final SyncCallbackSettings mDefaultSettings =
            new SyncCallbackSettings.Builder(mFakeLogger, IS_MAIN_THREAD_SUPPLIER)
                    .setMaxTimeoutMs(CALLBACK_TIMEOUT_MS)
                    .build();

    protected abstract CB newCallback(SyncCallbackSettings settings);

    @Test
    public final void testNewCallback() {
        CB callback1 = newCallback(mDefaultSettings);
        expect.withMessage("1st callback").that(callback1).isNotNull();

        CB callback2 = newCallback(mDefaultSettings);
        expect.withMessage("2nd callback").that(callback2).isNotNull();
        expect.withMessage("2nd callback").that(callback2).isNotSameInstanceAs(callback1);
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
        CB callback = newCallback(mDefaultSettings);
        assumeCallbackSupportsSetCalled(callback);

        // Check state before
        expectIsCalledAndNumberCalls(callback, "before setCalled()", false, 0);

        runAsync(INJECTION_TIMEOUT_MS, () -> callback.setCalled());
        callback.assertCalled();

        // Check state after
        expectIsCalledAndNumberCalls(callback, "after setCalled()", true, 1);

        // Further calls - make sure number actual calls keeps increasing
        // (don't need to call on bg because it's already called)
        callback.setCalled();
        expect.withMessage("%s.getNumberActualCalls() after 2nd call", callback)
                .that(callback.getNumberActualCalls())
                .isEqualTo(2);
    }

    @Test
    public final void testAssertCalled_neverCalled() throws Exception {
        CB callback = newCallback(mDefaultSettings);
        assumeCallbackSupportsSetCalled(callback);

        var thrown =
                assertThrows(SyncCallbackTimeoutException.class, () -> callback.assertCalled());

        expect.withMessage("e.getTimeout()")
                .that(thrown.getTimeout())
                .isEqualTo(mDefaultSettings.getMaxTimeoutMs());
        expect.withMessage("e.getUnit()()").that(thrown.getUnit()).isEqualTo(TimeUnit.MILLISECONDS);

        expectIsCalledAndNumberCalls(callback, "after setCalled()", false, 0);
    }

    @Test
    public final void testAssertCalled_interrupted() throws Exception {
        CB callback = newCallback(mDefaultSettings);
        assumeCallbackSupportsSetCalled(callback);
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
    }

    @Test
    public final void testAssertCalled_multipleCalls() throws Exception {
        SyncCallbackSettings settings =
                new SyncCallbackSettings.Builder(mFakeLogger, IS_MAIN_THREAD_SUPPLIER)
                        .setMaxTimeoutMs(CALLBACK_TIMEOUT_MS)
                        .setExpectedNumberCalls(2)
                        .build();
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
    public final void testAssertCalled_multipleCallsFromMultipleCallbacks() throws Exception {
        SyncCallbackSettings settings =
                new SyncCallbackSettings.Builder(mFakeLogger, IS_MAIN_THREAD_SUPPLIER)
                        .setMaxTimeoutMs(CALLBACK_TIMEOUT_MS)
                        .setExpectedNumberCalls(4)
                        .build();
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
    public final void testToString() {
        CB callback = newCallback(mDefaultSettings);

        String toString = callback.toString();

        expect.withMessage("toString()")
                .that(toString)
                .startsWith("[" + callback.getClass().getSimpleName() + ":");
        expect.withMessage("toString()").that(toString).contains("id=" + callback.getId());
        expect.withMessage("toString()")
                .that(toString)
                .contains("numberActualCalls=" + callback.getNumberActualCalls());
        expect.withMessage("toString()").that(toString).contains(mDefaultSettings.toString());
        expect.withMessage("toString()").that(toString).endsWith("]");
    }

    // TODO(b/337014024): still missing
    // - test log
    // - test runs on main thread

    /** Ignore the test if the callback supports {@code assertCalled()}. */
    protected final void assumeCallbackSupportsSetCalled(CB callback) {
        if (!callback.supportsSetCalled()) {
            assertThrows(UnsupportedOperationException.class, () -> callback.setCalled());
            throw new AssumptionViolatedException("Callback doesn't support setCalled()");
        }
    }

    /** Helper method to assert the value of {@code isCalled()}. */
    protected final void expectIsCalled(CB callback, String when, boolean expectedIsCalled) {
        expect.withMessage("%s.isCalled() %s", callback, when)
                .that(callback.isCalled())
                .isEqualTo(expectedIsCalled);
    }

    /**
     * Helper method to assert the value of {@code isCalled()} and {@code getNumberActualCalls()}.
     */
    protected final void expectIsCalledAndNumberCalls(
            CB callback, String when, boolean expectedIsCalled, int expectedNumberCalls) {
        expectIsCalled(callback, when, expectedIsCalled);
        expect.withMessage("%s.getNumberActualCalls() %s", callback, when)
                .that(callback.getNumberActualCalls())
                .isEqualTo(expectedNumberCalls);
    }
}
