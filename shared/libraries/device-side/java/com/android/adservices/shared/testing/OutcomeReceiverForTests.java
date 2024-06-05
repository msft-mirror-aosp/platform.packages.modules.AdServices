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
package com.android.adservices.shared.testing;

import android.os.OutcomeReceiver;

import com.android.adservices.shared.testing.concurrency.FailableOnResultSyncCallback;
import com.android.adservices.shared.testing.concurrency.SyncCallbackSettings;

/**
 * Simple implementation of {@link OutcomeReceiver} for tests.
 *
 * <p>Callers typically call {@link #assertSuccess()} or {@link #assertFailure(Class)} to assert the
 * expected result.
 *
 * @param <T> type of outcome
 */
public final class OutcomeReceiverForTests<T> extends FailableOnResultSyncCallback<T, Exception>
        implements OutcomeReceiver<T, Exception> {

    /**
     * Default constructor, uses {@link SyncCallbackSettings#DEFAULT_TIMEOUT_MS} for timeout and
     * fails if the {@code inject...} method is called in the main thread.
     */
    public OutcomeReceiverForTests() {
        super();
    }

    /** Custom constructor. */
    public OutcomeReceiverForTests(SyncCallbackSettings settings) {
        super(settings);
    }

    @Override
    public void onError(Exception error) {
        injectFailure(error);
    }

    /**
     * Asserts that {@link #onResult(Object)} was called, waiting up to {@link #getTimeoutMs()}
     * milliseconds before failing (if not called).
     *
     * @return the result
     */
    public T assertSuccess() throws InterruptedException {
        return assertResultReceived();
    }

    /** Gets the error returned by {@link #onError(Exception)}. */
    public @Nullable Exception getError() {
        return getFailure();
    }

    /**
     * Asserts that {@link #onError(Exception)} was called, waiting up to {@code timeoutMs}
     * milliseconds before failing (if not called).
     *
     * @return the error
     */
    public <E extends Exception> E assertFailure(Class<E> expectedClass)
            throws InterruptedException {
        return assertFailureReceived(expectedClass);
    }
}
