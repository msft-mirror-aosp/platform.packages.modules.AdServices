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

import com.android.adservices.shared.testing.concurrency.FailableOnResultSyncCallback;
import com.android.adservices.shared.testing.concurrency.SyncCallbackFactory;
import com.android.adservices.shared.testing.concurrency.SyncCallbackSettings;

/**
 * Custom {@code SyncCallback} where the error type is an {@code int}.
 *
 * <p>Callers typically call {@link #assertSuccess()} or {@link #assertFailed(int)} to assert the
 * expected result.
 *
 * <p>Modeled on {@code android.adservices.topics.IGetTopicsCallback}, so {@code
 * SyncGetTopicsCallback} just extends it.
 *
 * @param <T> type of the object received on success.
 */
public abstract class IntFailureSyncCallback<T> extends FailableOnResultSyncCallback<T, Integer> {

    /** Default constructor. */
    public IntFailureSyncCallback() {
        this(SyncCallbackFactory.newDefaultSettings());
    }

    /** Fully customizable constructor. */
    public IntFailureSyncCallback(SyncCallbackSettings settings) {
        super(settings);
    }

    /** Constructor with a custom timeout to wait for the outcome. */
    public IntFailureSyncCallback(long timeoutMs) {
        this(SyncCallbackFactory.newSettingsBuilder().setMaxTimeoutMs(timeoutMs).build());
    }

    // NOTE: this method is needed otherwise subclasses that implement a Binder object that
    // takes an int code wouldn't compile.
    /** Sets the outcome as a failure (of the specific {@code code}. */
    public final void onFailure(int code) {
        super.onFailure(Integer.valueOf(code));
    }

    /**
     * Waits until {@link #onFailure(int)} is called and assert it was called with {@code
     * expectedCode}.
     */
    public final void assertFailed(int expectedCode) throws InterruptedException {
        int actualCode = assertFailureReceived();
        if (actualCode != expectedCode) {
            throw new IllegalStateException(
                    "Expected code " + expectedCode + ", but it failed with code " + actualCode);
        }
    }

    /** Convenience method for {@link #assertResultReceived()} . */
    public final T assertSuccess() throws InterruptedException {
        return assertResultReceived();
    }
}
