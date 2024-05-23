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

import static com.android.adservices.shared.testing.concurrency.SyncCallbackSettings.DEFAULT_TIMEOUT_MS;

import androidx.annotation.Nullable;

import com.android.adservices.shared.testing.concurrency.FailableResultSyncCallback;
import com.android.adservices.shared.testing.concurrency.SyncCallbackSettings;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * @deprecated will be replaced by {@link FailableResultSyncCallback}.
 */
@Deprecated
public class SyncCallback<T, E> extends FailableResultSyncCallback<T, E> {

    @VisibleForTesting static final String TAG = SyncCallback.class.getSimpleName();

    private static final boolean DEFAULT_BEHAVIOR_FOR_FAIL_IF_CALLED_ON_MAIN_THREAD = true;

    @VisibleForTesting
    static final String MSG_WRONG_ERROR_RECEIVED = "expected error of type %s, but received %s";

    /**
     * Default constructor, uses {@link #DEFAULT_TIMEOUT_MS} for timeout and fails if the {@code
     * inject...} method is called in the main thread.
     */
    protected SyncCallback() {
        this(DEFAULT_TIMEOUT_MS, DEFAULT_BEHAVIOR_FOR_FAIL_IF_CALLED_ON_MAIN_THREAD);
    }

    /** Constructor with a custom timeout to wait for the outcome. */
    protected SyncCallback(long timeoutMs) {
        this(timeoutMs, DEFAULT_BEHAVIOR_FOR_FAIL_IF_CALLED_ON_MAIN_THREAD);
    }

    /** Constructor with custom settings. */
    protected SyncCallback(long timeoutMs, boolean failIfCalledOnMainThread) {
        this(
                new SyncCallbackSettings.Builder()
                        .setMaxTimeoutMs(timeoutMs)
                        .setFailIfCalledOnMainThread(failIfCalledOnMainThread)
                        .build());
    }

    protected SyncCallback(SyncCallbackSettings settings) {
        super(settings);
    }

    @VisibleForTesting
    boolean isFailIfCalledOnMainThread() {
        return getSettings().isFailIfCalledOnMainThread();
    }

    /** Deprecated! */
    public final void assertReceived() throws InterruptedException {
        assertCalled();
    }

    /** Deprecated! */
    public @Nullable T getResultReceived() {
        return getResult();
    }

    /** Deprecated! */
    public final void injectError(E error) {
        injectFailure(error);
    }

    /** Deprecated! */
    public final <S extends E> S assertErrorReceived(Class<S> expectedClass)
            throws InterruptedException {
        return assertFailureReceived(Objects.requireNonNull(expectedClass));
    }

    /** Deprecated! */
    public final E assertErrorReceived() throws InterruptedException {
        return assertFailureReceived();
    }

    /** Deprecated! */
    public final @Nullable E getErrorReceived() {
        return getFailure();
    }

    /** Deprecated! */
    public final boolean isReceived() {
        return isCalled();
    }

    protected final long getMaxTimeoutMs() {
        return getSettings().getMaxTimeoutMs();
    }
}
