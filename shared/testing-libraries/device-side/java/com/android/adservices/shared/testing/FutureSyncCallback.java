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

package com.android.adservices.shared.testing;

import com.android.adservices.shared.testing.concurrency.FailableResultSyncCallback;
import com.android.adservices.shared.testing.concurrency.SyncCallbackSettings;

import com.google.common.util.concurrent.FutureCallback;

/**
 * SyncCallback based implementation for FutureCallback.
 *
 * @param <T> type of the object received on success.
 */
public class FutureSyncCallback<T> extends FailableResultSyncCallback<T, Throwable>
        implements FutureCallback<T> {

    public FutureSyncCallback() {
        super();
    }

    public FutureSyncCallback(SyncCallbackSettings settings) {
        super(settings);
    }

    @Override
    public void onSuccess(T result) {
        injectResult(result);
    }

    @Override
    public void onFailure(Throwable t) {
        injectFailure(t);
    }
}
