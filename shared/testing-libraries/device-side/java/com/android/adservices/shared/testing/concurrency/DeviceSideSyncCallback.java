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

import com.google.common.annotations.VisibleForTesting;

// TODO(b/342448771): it might be better to remove this class, or split it in 2 (for result and
// resultless); either way, we should then move most of the custom callbacks to the side-less
// project - the only device-side dependent stuff on most of them here is the asBinder()

/** Base class for device-side sync callbacks for testing. */
public abstract class DeviceSideSyncCallback extends AbstractSyncCallback
        implements IBinderSyncCallback {

    protected DeviceSideSyncCallback(SyncCallbackSettings settings) {
        super(settings);
    }

    @VisibleForTesting
    DeviceSideSyncCallback(AbstractSyncCallback realCallback, SyncCallbackSettings settings) {
        super(realCallback, settings);
    }

    @Override
    public IBinder asBinder() {
        return null;
    }
}
