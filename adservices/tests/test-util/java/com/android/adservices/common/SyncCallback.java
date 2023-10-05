/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.adservices.common;

import static com.google.common.truth.Truth.assertWithMessage;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.internal.util.Preconditions;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// TODO(b/302757068): add unit tests (and/or convert tests from OutcomeReceiverForTestsTest)
/**
 * Helper used to block until a success (or failure) callback is received.
 *
 * <p>It's abstract as the typical usage on tests would be some callback interface (like {@code
 * OutcomeReceiver}), so the actual test artifact would be subclasses. In fact, your test most
 * likely won't extend this class directly, but subclasses like {@link ExceptionFailureSyncCallback}
 * or {@link IntFailureSyncCallback}.
 *
 * <p>Callers typically call {@link #assertResultReceived()} or {@link #assertFailureReceived()} to
 * assert the expected outcome.
 *
 * @param <T> type of the object received on success.
 * @param <E> type of the object received on error.
 */
public abstract class SyncCallback<T, E> {

    private static final String TAG = SyncCallback.class.getSimpleName();

    /** Default time (used when not specified by constructor). */
    public static final int DEFAULT_TIMEOUT_MS = 5_000;

    private final CountDownLatch mLatch = new CountDownLatch(1);
    private final int mTimeoutMs;

    private @Nullable E mError;
    private @Nullable T mResult;
    private @Nullable String mMethodCalled;
    private long mEpoch = SystemClock.elapsedRealtime();

    /** Default constructor, uses {@link #DEFAULT_TIMEOUT_MS} for timeout. */
    protected SyncCallback() {
        this(DEFAULT_TIMEOUT_MS);
    }

    /** Constructor with a custom timeout to wait for the outcome. */
    protected SyncCallback(int timeoutMs) {
        mTimeoutMs = timeoutMs;
    }

    /**
     * Returns the maximum time the {@code assert...} methods will wait for an outcome before
     * failing.
     */
    protected int getMaxTimeoutMs() {
        return mTimeoutMs;
    }

    /**
     * Sets a successful result as outcome.
     *
     * @throws IllegalStateException if {@link #injectResult(T)} or {@link #injectError(E)} was
     *     already called.
     */
    protected final void injectResult(T result) {
        mResult = result;
        setMethodCalled("injectResult", result);
    }

    /**
     * Sets an error result as outcome.
     *
     * @throws IllegalStateException if {@link #injectResult(T)} or {@link #injectError(E)} was
     *     already called.
     */
    protected final void injectError(E error) {
        mError = error;
        setMethodCalled("injectError", error);
    }

    /**
     * Asserts that {@link #injectResult(Object)} was called, waiting up to {@link
     * #getMaxTimeoutMs()} milliseconds before failing (if not called).
     *
     * @return the result
     */
    public final T assertResultReceived() throws InterruptedException {
        assertReceived();
        assertWithMessage("result").that(mResult).isNotNull();
        assertWithMessage("error").that(mError).isNull();
        return mResult;
    }

    /**
     * Asserts that {@link #injectError(Exception)} was called, waiting up to {@link
     * #getMaxTimeoutMs()} milliseconds before failing (if not called).
     *
     * @return the error
     */
    public final E assertErrorReceived() throws InterruptedException {
        assertReceived();
        assertWithMessage("result").that(mResult).isNull();
        assertWithMessage("error").that(mError).isNotNull();
        return mError;
    }

    /**
     * Asserts that either {@link #injectResult(Object)} or {@link #injectError(Exception)} was
     * called, waiting up to {@link #getMaxTimeoutMs()} milliseconds before failing (if not called).
     */
    public final void assertReceived() throws InterruptedException {
        Log.v(TAG, "waiting " + mTimeoutMs + " until called");
        boolean called = mLatch.await(mTimeoutMs, TimeUnit.MILLISECONDS);
        Preconditions.checkState(called, "Callback not received in %d ms", mTimeoutMs);
    }

    /** Gets the error returned by {@link #injectError(E)}. */
    public final @Nullable E getErrorReceived() {
        return mError;
    }

    /** Gets the result returned by {@link #injectResult(T)}. */
    public final @Nullable T getResultReceived() {
        return mResult;
    }

    private void setMethodCalled(String method, Object arg) {
        String methodCalled = method + "(" + arg + ")";
        long delta = SystemClock.elapsedRealtime() - mEpoch;
        Log.v(TAG, methodCalled + " in " + delta + "ms");
        if (mMethodCalled != null) {
            throw new IllegalStateException(methodCalled + " called after " + mMethodCalled);
        }
        mMethodCalled = methodCalled;
        mLatch.countDown();
    }
}
