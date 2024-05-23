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

import androidx.annotation.Nullable;

import com.google.common.base.Optional;

import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link SyncCallback} use to return an object (result).
 *
 * @param <T> type of the result.
 */
public class ResultSyncCallback<T> extends AbstractTestSyncCallback {

    private final AtomicReference<Optional<T>> mResult = new AtomicReference<>();

    public ResultSyncCallback() {
        super(new SyncCallbackSettings.Builder().build());
    }

    public ResultSyncCallback(SyncCallbackSettings settings) {
        super(settings);
    }

    /**
     * Sets the result.
     *
     * @throws IllegalStateException if it was already called.
     */
    public final void injectResult(@Nullable T result) {
        logV("Injecting %s (mResult=%s)", result, mResult);
        Optional<T> newResult = Optional.fromNullable(result);
        if (!mResult.compareAndSet(null, newResult)) {
            throw new IllegalStateException(
                    "injectResult already called (with " + getResult() + ")");
        }
        super.setCalled();
    }

    /**
     * Asserts that {@link #injectResult(Object)} was called, waiting up to {@link
     * #getMaxTimeoutMs()} milliseconds before failing (if not called).
     *
     * @return the result
     */
    public final @Nullable T assertResultReceived() throws InterruptedException {
        assertCalled();
        return getResult();
    }

    /**
     * Gets the result returned by {@link #injectResult(Object)} (or {@code null} if it was not
     * called yet).
     */
    public final @Nullable T getResult() {
        Optional<T> result = mResult.get();
        return result == null ? null : result.orNull();
    }

    @Override
    public final void setCalled() {
        throw new UnsupportedOperationException("should call injectResult() instead");
    }
}
