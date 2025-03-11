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

package com.android.adservices.service.devapi;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/** No-op implementation of {@link DevSessionDataStore} for when the feature is disabled. */
public final class DevSessionDataStoreNoOp implements DevSessionDataStore {
    @Override
    public ListenableFuture<DevSession> set(DevSession devSession) {
        return get();
    }

    @Override
    public ListenableFuture<DevSession> get() {
        // Note that IN_PROD needs to be the default behaviour as this is what we would expect when
        // the developer mode feature is fully disabled.
        // If the flag is remotely disabled then this takes effect immediately.
        return Futures.immediateFuture(
                DevSession.builder().setState(DevSessionState.IN_PROD).build());
    }
}
