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

import static com.android.adservices.shared.testing.concurrency.SyncCallback.LOG_TAG;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.android.adservices.shared.meta_testing.FakeLogger;
import com.android.adservices.shared.meta_testing.LogEntry;
import com.android.adservices.shared.testing.Logger.LogLevel;
import com.android.adservices.shared.testing.Logger.RealLogger;
import com.android.adservices.shared.testing.Nullable;
import com.android.adservices.shared.testing.SharedSidelessTestCase;
import com.android.adservices.shared.testing.StandardStreamsLogger;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Base class for all {@code SyncCallback} implementations. */
public abstract class SyncCallbackTestCase<CB extends SyncCallback & FreezableToString>
        extends SharedSidelessTestCase {

    protected static final long INJECTION_TIMEOUT_MS = 200;
    protected static final long CALLBACK_TIMEOUT_MS = INJECTION_TIMEOUT_MS + 400;

    protected final FakeLogger mFakeLogger = new FakeLogger();

    private final SyncCallbackSettings.Builder mFakeLoggerSettingsBuilder =
            new SyncCallbackSettings.Builder(mFakeLogger);
    private final SyncCallbackSettings.Builder mDefaultSettingsBuilder =
            mFakeLoggerSettingsBuilder
                    .setFailIfCalledOnMainThread(supportsFailIfCalledOnMainThread())
                    .setMaxTimeoutMs(CALLBACK_TIMEOUT_MS);

    // Used to set the name of the method returned by call() - cannot AtomicRefecence because
    // call() might be called it might be called AFTER assertCalled()
    private final ArrayBlockingQueue<String> mSetCalledMethodQueue = new ArrayBlockingQueue<>(1);

    protected final SyncCallbackSettings mDefaultSettings = mDefaultSettingsBuilder.build();

    protected final ConcurrencyHelper mConcurrencyHelper;

    // TODO(b/342448771): ideally should remove it, but the class hierarchy is messed up (as some
    // classes are defined on side-less but the test on device-side)
    protected SyncCallbackTestCase() {
        this(StandardStreamsLogger.getInstance());
    }

    protected SyncCallbackTestCase(RealLogger realLogger) {
        mConcurrencyHelper = new ConcurrencyHelper(realLogger);
    }

    /**
     * Gets a new callback to be used in the test.
     *
     * <p>Each call should return a different object.
     */
    protected CB newCallback(SyncCallbackSettings settings) {
        SyncCallback rawCallback = newRawCallback(settings);
        if (rawCallback == null) {
            throw new UnsupportedOperationException(
                    "Must override this method or return non-null on newRawCallback()");
        }
        @SuppressWarnings("unchecked")
        CB castCallback = (CB) (rawCallback);
        return castCallback;
    }

    /**
     * Similar to {@link #newCallback(SyncCallbackSettings)}, but should be used by tests whose
     * callback type is not available on earlier platform releases (like {@code
     * android.os.OutcomeReceiver}).
     */
    @Nullable
    protected SyncCallback newRawCallback(SyncCallbackSettings settings) {
        return null;
    }

    private CB newFrozenCallback(SyncCallbackSettings settings) {
        CB callback = newCallback(settings);
        callback.freezeToString();
        return callback;
    }

    /** Calls the callback and return the name of the called method. */
    protected final String call(CB callback) {
        String methodName = callCallback(callback);
        if (methodName == null) {
            throw new IllegalStateException(
                    "Callback " + callback + " returned null to callCallback()");
        }
        mSetCalledMethodQueue.offer(methodName);
        return methodName;
    }

    // NOTE: currently it just need one method, so we're always returning poll()
    /** Gets the name of the method returned by {@link #call(SyncCallback)}. */
    private String getSetCalledMethodName() throws InterruptedException {
        String methodName = mSetCalledMethodQueue.poll(CALLBACK_TIMEOUT_MS, MILLISECONDS);
        if (methodName == null) {
            // Shouldn't happen...
            throw new IllegalStateException(
                    "Could not infer name of setCalled() method after "
                            + CALLBACK_TIMEOUT_MS
                            + " ms");
        }
        return methodName;
    }

    /**
     * {@code testHasExpectedConstructors} expects that the callback class has 2 constructors; if it
     * uses a factory approach instead, it should override this method to return {@code true}.
     */
    protected boolean usesFactoryApproach() {
        return false;
    }

    /**
     * Abstraction to "call" the callback.
     *
     * <p>By default it will call {@code setCalled()}, but should be overridden by subclasses that
     * support results.
     *
     * @return name of the method called
     */
    protected String callCallback(CB callback) {
        if (callback instanceof ResultlessSyncCallback) {
            ((ResultlessSyncCallback) callback).setCalled();
            return "setCalled()";
        }
        throw new UnsupportedOperationException(getClass() + " must override call(callback)");
    }

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
        CB callback1 = newFrozenCallback(mDefaultSettings);
        expect.withMessage("1st callback").that(callback1).isNotNull();

        CB callback2 = newFrozenCallback(mDefaultSettings);
        expect.withMessage("2nd callback").that(callback2).isNotNull();
        expect.withMessage("2nd callback").that(callback2).isNotSameInstanceAs(callback1);
    }

    @Test
    public final void testHasExpectedConstructors() throws Exception {
        assumeFalse("callback uses factory approach", usesFactoryApproach());

        CB callback = newFrozenCallback(mDefaultSettings);
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

        assertThrows(IllegalArgumentException.class, () -> newFrozenCallback(settings));
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
        CB callback = newFrozenCallback(mDefaultSettings);

        expect.withMessage("getSettings()")
                .that(callback.getSettings())
                .isSameInstanceAs(mDefaultSettings);
    }

    @Test
    public final void testGetId() {
        CB callback1 = newFrozenCallback(mDefaultSettings);
        String id1 = callback1.getId();
        expect.withMessage("id").that(id1).isNotNull();

        CB callback2 = newFrozenCallback(mDefaultSettings);
        String id2 = callback2.getId();
        expect.withMessage("id2").that(id2).isNotNull();
        expect.withMessage("id2").that(id2).isNotEqualTo(id1);
    }

    @Test
    public final void testAssertCalled() throws Exception {
        var callback = newFrozenCallback(mDefaultSettings);
        var log = new LogChecker(callback);

        // Check state before
        expectIsCalledAndNumberCalls(callback, "before setCalled()", false, 0);

        Thread t = runAsync(INJECTION_TIMEOUT_MS, () -> call(callback));
        callback.assertCalled();

        // Check state after
        expectIsCalledAndNumberCalls(callback, "after setCalled()", true, 1);
        String setCalled = getSetCalledMethodName();
        expectLoggedCalls(
                log.d(setCalled + " called on " + t.getName()),
                log.v(setCalled + " returning"),
                log.d("assertCalled() called on " + currentThread().getName()),
                log.v("assertCalled() returning"));

        // Further calls - make sure number actual calls keeps increasing
        // (don't need to call on bg because it's already called)
        call(callback);
        expect.withMessage("%s.getNumberActualCalls() after 2nd call", callback)
                .that(callback.getNumberActualCalls())
                .isEqualTo(2);
    }

    @Test
    public final void testAssertCalled_neverCalled() throws Exception {
        var callback = newFrozenCallback(mDefaultSettings);
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
        var callback = newFrozenCallback(mDefaultSettings);
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
        CB callback = newFrozenCallback(settings);

        // 1st call
        runAsync(INJECTION_TIMEOUT_MS, () -> call(callback));
        assertThrows(SyncCallbackTimeoutException.class, () -> callback.assertCalled());

        expectIsCalledAndNumberCalls(callback, "after 1st setCalled()", false, 1);

        // 2nd call
        runAsync(INJECTION_TIMEOUT_MS, () -> call(callback));
        callback.assertCalled();

        expectIsCalledAndNumberCalls(callback, "after 2nd setCalled()", true, 2);

        // Further calls - make sure number actual calls keeps increasing
        // (don't need to call on bg because it's already called)
        call(callback);
        expectIsCalledAndNumberCalls(callback, "after 3rd setCalled()", true, 3);
    }

    @Test
    public final void testAssertCalled_multipleCallsFromMultipleCallbacks_firstFinishFirst()
            throws Exception {
        // Set a smaller timeout, as it's expect to fail multiple times
        long injectionTimeoutMs = 20;
        SyncCallbackSettings settings =
                mDefaultSettingsBuilder
                        .setExpectedNumberCalls(4)
                        .setMaxTimeoutMs(injectionTimeoutMs + 40)
                        .build();
        CB callback1 = newFrozenCallback(settings);
        CB callback2 = newFrozenCallback(settings);

        // 1st call on 1st callback
        runAsync(injectionTimeoutMs, () -> call(callback1));
        assertThrows(SyncCallbackTimeoutException.class, () -> callback1.assertCalled());
        assertThrows(SyncCallbackTimeoutException.class, () -> callback2.assertCalled());
        expectIsCalled(callback1, "after 1st call on 1st callback", false);
        expectIsCalled(callback2, "after 1st call on 1st callback", false);

        // 1st call on 2nd callback
        runAsync(injectionTimeoutMs, () -> call(callback2));
        assertThrows(SyncCallbackTimeoutException.class, () -> callback1.assertCalled());
        assertThrows(SyncCallbackTimeoutException.class, () -> callback2.assertCalled());
        expectIsCalled(callback1, "after 1st call on 2nd callback", false);
        expectIsCalled(callback2, "after 1st call on 2nd callback", false);

        // 2nd call on 2nd callback
        runAsync(injectionTimeoutMs, () -> call(callback2));
        assertThrows(SyncCallbackTimeoutException.class, () -> callback1.assertCalled());
        assertThrows(SyncCallbackTimeoutException.class, () -> callback2.assertCalled());
        expectIsCalled(callback1, "after 2nd call on 2nd callback", false);
        expectIsCalled(callback2, "after 2nd call on 2nd callback", false);

        // 2nd call on 1st callback
        runAsync(injectionTimeoutMs, () -> call(callback1));
        callback1.assertCalled();
        callback2.assertCalled();
        expectIsCalledAndNumberCalls(callback1, "after 2nd call on 1st callback", true, 2);
        expectIsCalledAndNumberCalls(callback2, "after 2nd call on 1st callback", true, 2);

        // Further calls - make sure number actual calls keeps increasing
        // (don't need to call on bg because it's already called)
        call(callback1);
        expectIsCalledAndNumberCalls(callback1, "after 3rd call on 1st callback", true, 3);
        expectIsCalledAndNumberCalls(callback2, "after 3rd call on 1st callback", true, 2);
        call(callback2);
        expectIsCalledAndNumberCalls(callback1, "after 3rd call on 2nd callback", true, 3);
        expectIsCalledAndNumberCalls(callback2, "after 3rd call on 2nd callback", true, 3);
    }

    @Test
    public final void testAssertCalled_multipleCallsFromMultipleCallbacks_secondFinishFirst()
            throws Exception {
        // Set a smaller timeout, as it's expect to fail multiple times
        long injectionTimeoutMs = 20;
        SyncCallbackSettings settings =
                mDefaultSettingsBuilder
                        .setExpectedNumberCalls(4)
                        .setMaxTimeoutMs(injectionTimeoutMs + 40)
                        .build();
        CB callback1 = newFrozenCallback(settings);
        CB callback2 = newFrozenCallback(settings);

        // 1st call on 1st callback
        runAsync(injectionTimeoutMs, () -> call(callback1));
        assertThrows(SyncCallbackTimeoutException.class, () -> callback1.assertCalled());
        assertThrows(SyncCallbackTimeoutException.class, () -> callback2.assertCalled());
        expectIsCalled(callback1, "after 1st call on 1st callback", false);
        expectIsCalled(callback2, "after 1st call on 1st callback", false);

        // 1st call on 2nd callback
        runAsync(injectionTimeoutMs, () -> call(callback2));
        assertThrows(SyncCallbackTimeoutException.class, () -> callback1.assertCalled());
        assertThrows(SyncCallbackTimeoutException.class, () -> callback2.assertCalled());
        expectIsCalled(callback1, "after 1st call on 2nd callback", false);
        expectIsCalled(callback2, "after 1st call on 2nd callback", false);

        // 2nd call on 1st callback
        runAsync(injectionTimeoutMs, () -> call(callback1));
        assertThrows(SyncCallbackTimeoutException.class, () -> callback1.assertCalled());
        assertThrows(SyncCallbackTimeoutException.class, () -> callback2.assertCalled());
        expectIsCalled(callback1, "after 2nd call on 1st callback", false);
        expectIsCalled(callback2, "after 2nd call on 1st callback", false);

        // 2nd call on 2nd callback
        runAsync(injectionTimeoutMs, () -> call(callback2));
        callback1.assertCalled();
        callback2.assertCalled();
        expectIsCalledAndNumberCalls(callback1, "after 2nd call on 2nd callback", true, 2);
        expectIsCalledAndNumberCalls(callback2, "after 2nd call on 2nd callback", true, 2);

        // Further calls - make sure number actual calls keeps increasing
        // (don't need to call on bg because it's already called)
        call(callback1);
        expectIsCalledAndNumberCalls(callback1, "after 3rd call on 1st callback", true, 3);
        expectIsCalledAndNumberCalls(callback2, "after 3rd call on 1st callback", true, 2);
        call(callback2);
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

        var callback = newFrozenCallback(settings);
        var log = new LogChecker(callback);

        // setCalled() passes...
        // NOTE: not really the main thread, as it's emulated
        Thread mainThread = runAsync(INJECTION_TIMEOUT_MS, () -> call(callback));

        String setCalled = getSetCalledMethodName();
        var thrown = assertThrows(CalledOnMainThreadException.class, () -> callback.assertCalled());
        expect.withMessage("thrown")
                .that(thrown)
                .hasMessageThat()
                .contains(setCalled + " called on main thread (" + mainThread.getName() + ")");

        expectIsCalledAndNumberCalls(callback, "after setCalled()", true, 1);

        expectLoggedCalls(
                log.d(setCalled + " called on " + mainThread.getName()),
                        log.v(setCalled + " returning"),
                log.d("assertCalled() called on " + currentThread().getName()),
                        log.e("assertCalled() failed: " + thrown));
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

    protected final Thread runAsync(long timeoutMs, Runnable r) {
        return mConcurrencyHelper.runAsync(timeoutMs, r);
    }

    protected final Thread startNewThread(Runnable r) {
        return mConcurrencyHelper.startNewThread(r);
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

    /** Helper class used to to assert calls to the callback {@code logX()} methods. */
    protected static final class LogChecker {

        private final AbstractSyncCallback mCallback;

        public LogChecker(SyncCallback callback) {
            if (!(callback instanceof AbstractSyncCallback)) {
                throw new IllegalArgumentException(
                        "Not an instance of AbstractSyncCallback: " + callback);
            }
            this.mCallback = AbstractSyncCallback.class.cast(callback);
        }

        public LogEntry e(String expectedMessage) {
            return new LogEntry(LogLevel.ERROR, LOG_TAG, mCallback + ": " + expectedMessage);
        }

        public LogEntry d(String expectedMessage) {
            return new LogEntry(
                    LogLevel.DEBUG, LOG_TAG, mCallback.toStringLite() + ": " + expectedMessage);
        }

        public LogEntry v(String expectedMessage) {
            return new LogEntry(LogLevel.VERBOSE, LOG_TAG, mCallback + ": " + expectedMessage);
        }
    }
}
