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

import java.util.List;

/**
 * Abstraction for {@code ResultSyncCallback} and {@code FailableResultSyncCallback}.
 *
 * <p>This is needed because the latter is a "special" case of the former as it can receive either a
 * result OR a failure.
 */
interface IResultSyncCallback<R> extends SyncCallback, IBinderSyncCallback {

    /** Sets the result. */
    void injectResult(@Nullable R result);

    /**
     * Asserts that {@link #injectResult(Object)} was called, waiting up to {@link
     * #getMaxTimeoutMs()} milliseconds before failing (if not called).
     *
     * @return the result
     */
    @Nullable
    R assertResultReceived() throws InterruptedException;

    /**
     * Gets the result returned by {@link #injectResult(Object)}.
     *
     * <p>NOTE: it returns the result of the first call, which is sufficient for most use cases - if
     * you're expecting multiple calls, you can get the further ones using {@link #getResults()}.
     */
    @Nullable
    R getResult();

    // NOTE: cannot use Guava's ImmutableList because it doesn't support null elements
    /**
     * Gets the result of all calls to {@link #injectResult(Object)}, in order.
     *
     * @return immutable list with all results
     */
    List<R> getResults();
}
