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

import android.adservices.common.AdServicesOutcomeReceiver;

import com.android.adservices.shared.testing.Nullable;
import com.android.adservices.shared.testing.concurrency.FailableResultSyncCallback;

/**
 * Stub implementation of {@link AdServicesOutcomeReceiver} for tests.
 *
 * <p>See javadoc for individual methods on {@link
 * com.android.adservices.shared.testing.OutcomeReceiverForTests}.
 *
 * @param <T> type of outcome
 */
public final class AdServicesOutcomeReceiverForTests<T>
        extends FailableResultSyncCallback<T, Exception>
        implements AdServicesOutcomeReceiver<T, Exception> {

    @Override
    public void onResult(T result) {
        injectResult(result);
    }

    @Override
    public void onError(Exception error) {
        injectFailure(error);
    }

    /** {@link com.android.adservices.shared.testing.OutcomeReceiverForTests#assertSuccess()}. */
    public T assertSuccess() throws InterruptedException {
        return assertResultReceived();
    }

    /**
     * {@link com.android.adservices.shared.testing.OutcomeReceiverForTests#assertFailure(Class)}.
     */
    public <E extends Exception> E assertFailure(Class<E> expectedClass)
            throws InterruptedException {
        return assertFailureReceived(expectedClass);
    }

    /** Same as {@link #assertFailureReceived()}. */
    public Exception assertErrorReceived() throws InterruptedException {
        return assertFailureReceived();
    }

    /** {@link com.android.adservices.shared.testing.OutcomeReceiverForTests#getError()}. */
    public @Nullable Exception getError() {
        return getFailure();
    }
}
