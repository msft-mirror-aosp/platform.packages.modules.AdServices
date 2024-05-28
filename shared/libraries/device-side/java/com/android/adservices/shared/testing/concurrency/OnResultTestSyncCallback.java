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

/**
 * Abstraction for {@code OnResultSyncCallback} and {@code FailableOnResultSyncCallback}.
 *
 * <p>This is needed because the latter is a "special" case of the former as it can receive either a
 * result OR a failure.
 *
 * @param <T> type of the result.
 */
public interface OnResultTestSyncCallback<T> extends ResultTestSyncCallback<T> {

    /** Injects the result. */
    void onResult(T result);
}
