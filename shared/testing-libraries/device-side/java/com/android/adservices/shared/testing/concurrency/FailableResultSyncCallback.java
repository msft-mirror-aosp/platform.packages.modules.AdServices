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

import static com.android.adservices.shared.testing.concurrency.ResultSyncCallback.getImmutableList;
import static com.android.adservices.shared.util.Preconditions.checkState;

import android.os.IBinder;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * {@code SyncCallback} use to return an object (result) or a failure.
 *
 * @param <R> type of the object received on success.
 * @param <F> type of the object received on failure.
 */
public class FailableResultSyncCallback<R, F> extends AbstractSyncCallback
        implements IResultSyncCallback<R> {

    @VisibleForTesting
    public static final String INJECT_RESULT_OR_FAILURE = "injectResult() or injectFailure()";

    @VisibleForTesting
    public static final String MSG_WRONG_ERROR_RECEIVED =
            "expected error of type %s, but received %s";

    private final ResultSyncCallback<ResultOrFailure<R, F>> mCallback;

    public FailableResultSyncCallback() {
        this(SyncCallbackFactory.newSettingsBuilder().build());
    }

    public FailableResultSyncCallback(SyncCallbackSettings settings) {
        super(settings);

        mCallback = new ResultSyncCallback<>(this, settings);
    }

    /**
     * Sets a failure as the outcome of the callback.
     *
     * @throws IllegalStateException if {@link #injectResult(R)} or {@link #injectError(F)} was
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
     * <p>NOTE: it returns the result of the first call, which is sufficient for most use cases - if
     * you're expecting multiple calls, you can get the further ones using {@link #getFailures()}.
     *
     * @return the first failure passed to {@link #injectFailure(Object)} or {@code null} if {@link
     *     #injectResult(Object)} was called first.
     */
    public final @Nullable F assertFailureReceived() throws InterruptedException {
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
     * Gets first failure returned by {@link #injectFailure(Object)} (or {@code null} if {@link
     * #injectResult(Object)} was called first).
     */
    public final @Nullable F getFailure() {
        var resultOrFailure = mCallback.getResult();
        return resultOrFailure == null ? null : resultOrFailure.failure;
    }

    // NOTE: cannot use Guava's ImmutableList because it doesn't support null elements
    /**
     * Gets the result of all calls to {@link #injectFailure(Object)}, in order.
     *
     * @return immutable list with all failures
     */
    public final List<F> getFailures() {
        return getImmutableList(
                mCallback.getResults().stream()
                        .filter(rof -> !rof.isResult)
                        .map(rof -> rof.failure)
                        .collect(Collectors.toList()));
    }

    @Override
    public final void injectResult(R result) {
        mCallback.injectResult(
                new ResultOrFailure<>(/* isResult= */ true, result, /* failure= */ null));
    }

    @Override
    public final R getResult() {
        var resultOrFailure = mCallback.getResult();
        return resultOrFailure == null ? null : resultOrFailure.result;
    }

    @Override
    public List<R> getResults() {
        return getImmutableList(
                mCallback.getResults().stream()
                        .filter(rof -> rof.isResult)
                        .map(rof -> rof.result)
                        .collect(Collectors.toList()));
    }

    @Override
    public final R assertResultReceived() throws InterruptedException {
        return internalAssertResultReceived();
    }

    /**
     * "Real" implementation of {@link #assertResultReceived()}, can be overridden by subclasses.
     */
    protected R internalAssertResultReceived() throws InterruptedException {
        assertCalled();
        return getResult();
    }

    protected List<R> internalAssertResultsReceived() throws InterruptedException {
        assertCalled();
        return getResults();
    }

    @Override
    public final int getNumberActualCalls() {
        return mCallback.getNumberActualCalls();
    }

    @Override
    public final void assertCalled() throws InterruptedException {
        mCallback.internalAssertCalled(mSettings.getMaxTimeoutMs());
    }

    @Override
    public IBinder asBinder() {
        return null;
    }

    @Override
    protected void customizeToString(StringBuilder string) {
        super.customizeToString(string);
        if (!isCalled()) {
            // "(no result yet)" is already added by mCallback
            string.append(" (no failure yet)");
        }
        // NOTE: ideally we should replace the result=... by failure=... (when there is a failure),
        // but that would be hard to implement - and realistically, who cares?
        mCallback.customizeToString(string);
    }

    private static final class ResultOrFailure<T, F> {
        public final boolean isResult;
        public final @Nullable T result;
        public final @Nullable F failure;

        ResultOrFailure(boolean isResult, @Nullable T result, @Nullable F failure) {
            this.isResult = isResult;
            this.result = result;
            this.failure = failure;
        }

        @Override
        public String toString() {
            return String.valueOf(isResult ? result : failure);
        }
    }
}
