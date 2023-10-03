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

import android.os.OutcomeReceiver;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.internal.util.Preconditions;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Simple implementation of {@link OutcomeReceiver} for tests.
 *
 * <p>Callers typically call {@link #assertSuccess()} or {@link #assertFailure(Class)} to assert the
 * expected result.
 */
public final class OutcomeReceiverForTests<T> implements OutcomeReceiver<T, Exception> {

    private static final String TAG = OutcomeReceiverForTests.class.getSimpleName();

    private static final int DEFAULT_TIMEOUT_MS = 500;

    private final CountDownLatch mLatch = new CountDownLatch(1);
    private final int mTimeoutMs;

    private @Nullable Exception mError;
    private @Nullable T mResult;
    private @Nullable String mMethodCalled;
    private long mEpoch = SystemClock.elapsedRealtime();

    /** Default constructor, uses {@link #DEFAULT_TIMEOUT_MS} for timeout. */
    public OutcomeReceiverForTests() {
        this(DEFAULT_TIMEOUT_MS);
    }

    /** Constructor with a custom timeout to wait for the outcome. */
    public OutcomeReceiverForTests(int timeoutMs) {
        mTimeoutMs = timeoutMs;
    }

    @Override
    public void onError(@Nullable Exception error) {
        mError = error;
        setMethodCalled("onError", error);
    }

    @Override
    public void onResult(@Nullable T result) {
        mResult = result;
        setMethodCalled("onResult", result);
    }

    /**
     * Returns the maximum time the {@code assert...} methods will wait for an outcome before
     * failing.
     */
    public int getTimeoutMs() {
        return mTimeoutMs;
    }

    /**
     * Asserts that {@link #onResult(Object)} was called, waiting up to {@link #getTimeoutMs()}
     * milliseconds before failing (if not called).
     *
     * @return the result
     */
    public T assertSuccess() throws InterruptedException {
        assertCalled();
        assertWithMessage("result").that(mResult).isNotNull();
        assertWithMessage("error").that(mError).isNull();
        return mResult;
    }

    /**
     * Asserts that {@link #onError(Exception)} was called, waiting up to {@link #getTimeoutMs()}
     * milliseconds before failing (if not called).
     *
     * @return the error
     */
    public <E extends Exception> E assertFailure(Class<E> expectedClass)
            throws InterruptedException {
        Preconditions.checkArgument(expectedClass != null, "expectedClass cannot be null");

        assertCalled();
        assertWithMessage("result").that(mResult).isNull();
        assertWithMessage("error").that(mError).isInstanceOf(expectedClass);
        return expectedClass.cast(mError);
    }

    /**
     * Asserts that either {@link #onResult(Object)} or {@link #onError(Exception)} was called,
     * waiting up to {@link #getTimeoutMs()} milliseconds before failing (if not called).
     */
    public void assertCalled() throws InterruptedException {
        Log.v(TAG, "waiting " + mTimeoutMs + " until called");
        boolean called = mLatch.await(mTimeoutMs, TimeUnit.MILLISECONDS);
        if (!called) {
            throw new IllegalStateException("Outcome not received in " + mTimeoutMs + "ms");
        }
    }

    /** Gets the error returned by {@link #onError(Exception)}. */
    public @Nullable Exception getError() {
        return mError;
    }

    /** Gets the result returned by {@link #onResult(Object)}. */
    public T getResult() {
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
