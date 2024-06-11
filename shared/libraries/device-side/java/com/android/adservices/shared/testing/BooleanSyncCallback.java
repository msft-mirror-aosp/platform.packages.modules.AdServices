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

import com.android.adservices.shared.testing.concurrency.ResultSyncCallback;
import com.android.adservices.shared.testing.concurrency.SyncCallbackSettings;

/**
 * Custom {@code SyncCallback} implementation that doesn't expect an exception to be thrown and
 * injects a {@code boolean}
 */
public final class BooleanSyncCallback extends ResultSyncCallback<Boolean> {

    public BooleanSyncCallback() {
        super();
    }

    public BooleanSyncCallback(SyncCallbackSettings settings) {
        super(settings);
    }
}
