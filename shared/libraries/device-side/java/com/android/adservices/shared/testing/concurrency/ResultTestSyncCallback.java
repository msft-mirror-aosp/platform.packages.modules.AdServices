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

import android.os.IBinder;

import androidx.annotation.Nullable;

/**
 * Abstraction for {@code ResultSyncCallback} and {@code FailableResultSyncCallback}.
 *
 * <p>This is needed because the latter is a "special" case of the former as it can receive either a
 * result OR a failure.
 */
interface ResultTestSyncCallback<T> extends TestSyncCallback {

    /** Sets the result. */
    void injectResult(@Nullable T result);

    /**
     * Asserts that {@link #injectResult(Object)} was called, waiting up to {@link
     * #getMaxTimeoutMs()} milliseconds before failing (if not called).
     *
     * @return the result
     */
    @Nullable
    T assertResultReceived() throws InterruptedException;

    /**
     * Gets the result returned by {@link #injectResult(Object)} (or {@code null} if it was not
     * called yet).
     */
    @Nullable
    T getResult();

    /**
     * Convenience method for callbacks used to implement binder stubs.
     *
     * @return {@code null} by default, but subclasses can extend.
     */
    @Nullable
    IBinder asBinder();
}
