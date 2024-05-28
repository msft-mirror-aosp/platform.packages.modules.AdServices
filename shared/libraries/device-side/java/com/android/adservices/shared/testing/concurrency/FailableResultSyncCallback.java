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
import static com.android.adservices.shared.util.Preconditions.checkState;

import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.Locale;
import java.util.Objects;

/**
 * {@code SyncCallback} use to return an object (result) or a failure.
 *
 * @param <T> type of the object received on success.
 * @param <F> type of the object received on failure.
 */
public class FailableResultSyncCallback<T, F> implements IResultSyncCallback<T> {

    @VisibleForTesting
    public static final String INJECT_RESULT_OR_FAILURE = "injectResult() or injectFailure()";

    @VisibleForTesting
    public static final String MSG_WRONG_ERROR_RECEIVED =
            "expected error of type %s, but received %s";

    private final ResultSyncCallback<ResultOrFailure<T, F>> mCallback;

    public FailableResultSyncCallback() {
        this(SyncCallbackFactory.newSettingsBuilder().build());
    }

    public FailableResultSyncCallback(SyncCallbackSettings settings) {
        mCallback = new ResultSyncCallback<>(settings);
    }

    /**
     * Sets a failure as the outcome of the callback.
     *
     * @throws IllegalStateException if {@link #injectResult(T)} or {@link #injectError(F)} was
     *     already called.
     */
    public final void injectFailure(F failure) {
        mCallback.injectResult(
                new ResultOrFailure<>(
                        /* isResult= */ false,
                        /* result= */ null,
                        Objects.requireNonNull(failure)));
    }

    /**
     * Asserts that {@link #injectFailure(Object)} was called, waiting up to {@link
     * #getMaxTimeoutMs()} milliseconds before failing (if not called).
     *
     * @return the failure
     */
    public final F assertFailureReceived() throws InterruptedException {
        assertCalled();
        return getFailure();
    }

    /**
     * Asserts that {@link #injectFailure(Object)} was called with a class of type {@code S},
     * waiting up to {@link #getMaxTimeoutMs()} milliseconds before failing (if not called).
     *
     * @return the failure
     */
    public final <S extends F> S assertFailureReceived(Class<S> expectedClass)
            throws InterruptedException {
        Objects.requireNonNull(expectedClass);
        F failure = assertFailureReceived();
        checkState(
                expectedClass.isInstance(failure),
                MSG_WRONG_ERROR_RECEIVED,
                expectedClass,
                failure);
        return expectedClass.cast(failure);
    }

    /**
     * Gets the failure returned by {@link #injectFailure(Object)} (or {@code null} if it was not
     * called yet).
     */
    public @Nullable F getFailure() {
        var resultOrFailure = mCallback.getResult();
        return resultOrFailure == null ? null : resultOrFailure.failure;
    }

    @Override
    public final boolean isCalled() {
        return mCallback.isCalled();
    }

    @Override
    public final SyncCallbackSettings getSettings() {
        return mCallback.getSettings();
    }

    @Override
    public final void injectResult(T result) {
        mCallback.injectResult(
                new ResultOrFailure<>(/* isResult= */ true, result, /* failure= */ null));
    }

    @Override
    public final T getResult() {
        var resultOrFailure = mCallback.getResult();
        return resultOrFailure == null ? null : resultOrFailure.result;
    }

    @Override
    public final T assertResultReceived() throws InterruptedException {
        assertCalled();
        return getResult();
    }

    @Override
    public final void assertCalled() throws InterruptedException {
        try {
            mCallback.assertCalled();
        } catch (CallbackAlreadyCalledException e) {
            // Need to switch the message in the exception
            throw new CallbackAlreadyCalledException(
                    INJECT_RESULT_OR_FAILURE,
                    getResultOrValue(e.getPreviousValue()),
                    getResultOrValue(e.getNewValue()));
        }
    }

    @Override
    public final String getId() {
        return mCallback.getId();
    }

    @FormatMethod
    @Override
    public final void logE(@FormatString String msgFmt, Object... msgArgs) {
        String msg = String.format(Locale.ENGLISH, msgFmt, msgArgs);
        Log.e(LOG_TAG, String.format(Locale.ENGLISH, "%s: %s", toString(), msg));
    }

    @FormatMethod
    @Override
    public final void logD(@FormatString String msgFmt, Object... msgArgs) {
        String msg = String.format(Locale.ENGLISH, msgFmt, msgArgs);
        Log.d(
                LOG_TAG,
                String.format(
                        Locale.ENGLISH, "[%s#%s]: %s", getClass().getSimpleName(), getId(), msg));
    }

    @FormatMethod
    @Override
    public final void logV(@FormatString String msgFmt, Object... msgArgs) {
        String msg = String.format(Locale.ENGLISH, msgFmt, msgArgs);
        Log.v(LOG_TAG, String.format(Locale.ENGLISH, "%s: %s", toString(), msg));
    }

    @Override
    public IBinder asBinder() {
        return null;
    }

    @Override
    public final String toString() {
        StringBuilder string =
                mCallback.appendInfo(
                        new StringBuilder("[").append(getClass().getSimpleName()).append(": "));
        if (!isCalled()) {
            // "(no result yet)" is already added by mCallback
            string.append(" (no failure yet)");
        }
        // NOTE: ideally we should replace the result=... by failure=... (when there is a failure),
        // but that would be hard to implement - and realistically, who cares?
        return string.append(']').toString();
    }

    private static final class ResultOrFailure<T, F> {
        private boolean mIsResult;
        public @Nullable T result;
        public @Nullable F failure;

        ResultOrFailure(boolean isResult, @Nullable T result, @Nullable F failure) {
            mIsResult = isResult;
            this.result = result;
            this.failure = failure;
        }

        @Override
        public String toString() {
            return String.valueOf(mIsResult ? result : failure);
        }
    }

    private static @Nullable Object getResultOrValue(Object value) {
        if (!(value instanceof ResultOrFailure)) {
            return null;
        }
        ResultOrFailure<?, ?> rof = (ResultOrFailure<?, ?>) value;
        return rof.mIsResult ? rof.result : rof.failure;
    }
}
