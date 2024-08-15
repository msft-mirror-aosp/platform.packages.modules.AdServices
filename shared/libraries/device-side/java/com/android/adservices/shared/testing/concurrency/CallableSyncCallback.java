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

import com.android.adservices.shared.testing.util.ThrowingCallable;

import java.util.Objects;

/**
 * {@code SyncCallback} that returns the result of a {@code Callable}, automatically reporting its
 * exception as failure.
 *
 * @param <R> type of the object received on success.
 */
public final class CallableSyncCallback<R> extends FailableResultSyncCallback<R, Throwable> {

    public CallableSyncCallback() {
        super();
    }

    public CallableSyncCallback(SyncCallbackSettings settings) {
        super(settings);
    }

    @Override
    protected R internalAssertResultReceived() throws InterruptedException {
        R result = super.internalAssertResultReceived();
        Throwable failure = getFailure();
        if (failure != null) {
            throw new IllegalStateException("Received failure instead", failure);
        }
        return result;
    }

    /**
     * Injects the result of {@code Callable} to the callback, propagating its exception if it
     * throws.
     */
    public void injectCallable(ThrowingCallable<R> callable) {
        Objects.requireNonNull(callable, "callable cannot be null");
        try {
            injectResult(callable.call());
        } catch (Throwable t) {
            injectFailure(t);
        }
    }
}
