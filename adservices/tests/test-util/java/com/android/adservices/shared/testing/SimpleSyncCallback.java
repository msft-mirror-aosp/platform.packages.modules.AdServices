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

/**
 * Custom {@link SyncCallback} implementation that doesn't expect any result or error, just that
 * something is called - you should use {@link #setCalled()} to indicate it was called, and {@link
 * #assertCalled()} to block until it was called.
 */
public final class SimpleSyncCallback extends NoFailureSyncCallback<Object> {

    private static final Object RESULT = new Object();

    /** Default constructor. */
    public SimpleSyncCallback() {}

    /** Constructor with a custom timeout to wait for the outcome. */
    public SimpleSyncCallback(long timeoutMs) {
        super(timeoutMs);
    }

    /** Notifies the callback was called. */
    public void setCalled() {
        super.injectResult(RESULT);
    }

    /** Blocks until callback is called or fails if it isn't. */
    public void assertCalled() throws InterruptedException {
        assertReceived();
    }

    /** Returns whether the callback was called yet (without blocking if it wasn't). */
    public boolean isCalled() {
        return isReceived();
    }

    // TODO(b/337014024): should not need to override methods below

    @Override
    public void injectResult(Object result) {
        throw new UnsupportedOperationException("should call setCalled() instead");
    }

    @Override
    public Object assertResultReceived() throws InterruptedException {
        throw new UnsupportedOperationException("should call assertCalled() instead");
    }

    @Override
    public Object getResultReceived() {
        throw new UnsupportedOperationException("should call isCalled() instead");
    }
}
