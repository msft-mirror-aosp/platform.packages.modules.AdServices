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

import com.android.adservices.shared.meta_testing.FakeRealLogger;

// Not testing a real callback, but callbacks that cannot be constructed with
// setFailIfCalledOnMainThread(true) (like BroadcastReceiverSyncCallback)
public final class MainThreadSyncCallbackTest extends SyncCallbackTestCase<AbstractSyncCallback> {

    @Override
    protected AbstractSyncCallback newCallback(SyncCallbackSettings settings) {
        return new MainThreadSyncCallback(settings);
    }

    @Override
    protected boolean supportsFailIfCalledOnMainThread() {
        return false;
    }

    @Override
    protected String callCallback(AbstractSyncCallback callback) {
        return callback.internalSetCalled("internalSetCalled()");
    }

    @Override
    protected void assertCalled(AbstractSyncCallback callback, long timeoutMs)
            throws InterruptedException {
        callback.internalAssertCalled(timeoutMs);
    }

    private static final class MainThreadSyncCallback extends AbstractSyncCallback {

        @SuppressWarnings("unused") // Called by superclass using reflection
        MainThreadSyncCallback() {
            this(
                    new SyncCallbackSettings.Builder(new FakeRealLogger())
                            .setFailIfCalledOnMainThread(false)
                            .build());
        }

        MainThreadSyncCallback(SyncCallbackSettings settings) {
            super(SyncCallbackSettings.checkCanFailOnMainThread(settings));
        }
    }
}
