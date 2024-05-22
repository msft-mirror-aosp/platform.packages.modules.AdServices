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

import android.os.Handler;
import android.os.Looper;

import com.android.adservices.shared.util.Preconditions;

/**
 * {@link SyncCallback} used to make sure a handler is "idle", i.e., it processes all requests in
 * its queue (posted prior to the callback creation).
 *
 * <p>Typically used on <code>@After</code> methods to wait until for background tasks to be
 * finished (like pending jobs).
 *
 * <p>NOTE: for now it only checks the main thread, but it could be extended to take a {@code
 * Handler} or {@code Looper} in the constructor.
 */
public final class HandlerIdleSyncCallback extends SyncCallback<Object, Void> {

    public HandlerIdleSyncCallback() {
        super(DEFAULT_TIMEOUT_MS, /* failIfCalledOnMainThread= */ false);

        Looper looper = Looper.getMainLooper();
        Preconditions.checkState(looper != null, "No main looper");
        new Handler(looper).post(() -> super.injectResult(null));
    }

    /**
     * Returns whether the handler is idle (i.e., it already process the runnable posted by the
     * callback).
     */
    public boolean isIdle() {
        return isReceived();
    }

    /** Blocks until the handler is idle. */
    public void assertIdle() throws InterruptedException {
        assertReceived();
    }

    // TODO(b/337014024): should not need to override methods below

    @Override
    public void injectResult(Object result) {
        throw new UnsupportedOperationException("result posted on handler by constructor");
    }

    @Override
    public void injectError(Void result) {
        throw new UnsupportedOperationException("result posted on handler by constructor");
    }

    @Override
    public Void assertErrorReceived() throws InterruptedException {
        throw new UnsupportedOperationException("should call assertIdle() instead");
    }

    @Override
    public Void getErrorReceived() {
        throw new UnsupportedOperationException("should call isIdle() instead");
    }

    @Override
    public Object assertResultReceived() throws InterruptedException {
        throw new UnsupportedOperationException("should call assertIdle() instead");
    }

    @Override
    public Object getResultReceived() {
        throw new UnsupportedOperationException("should call isIdle() instead");
    }
}
