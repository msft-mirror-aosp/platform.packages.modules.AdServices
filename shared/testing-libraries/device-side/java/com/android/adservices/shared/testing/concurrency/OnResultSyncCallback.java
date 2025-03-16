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
 * {@link ResultSyncCallback} for implementation of binder stub interfaces that have a {@code
 * onResult()} method.
 *
 * @param <T> type of the result.
 */
public abstract class OnResultSyncCallback<T> extends ResultSyncCallback<T>
        implements IOnResultSyncCallback<T> {

    protected OnResultSyncCallback() {
        super();
    }

    protected OnResultSyncCallback(SyncCallbackSettings settings) {
        super(settings);
    }

    @Override
    public final void onResult(T result) {
        injectResult(result);
    }
}
