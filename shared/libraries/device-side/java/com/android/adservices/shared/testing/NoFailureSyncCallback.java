/*
 * Copyright (C) 2023 The Android Open Source Project
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

/** Custom {@link SyncCallback} implementation that doesn't expect an exception to be thrown. */
public class NoFailureSyncCallback<T> extends SyncCallback<T, Void> {

    /** Default constructor. */
    public NoFailureSyncCallback() {}

    /** Constructor with a custom timeout to wait for the outcome. */
    public NoFailureSyncCallback(long timeoutMs) {
        super(timeoutMs);
    }

    // TODO(b/337014024): should not need to override methods below

    @Override
    public void injectError(Void error) {
        throw new UnsupportedOperationException("this callback only handle result");
    }

    @Override
    public Void assertErrorReceived() throws InterruptedException {
        throw new UnsupportedOperationException("this callback only handle result");
    }

    @Override
    public Void getErrorReceived() {
        throw new UnsupportedOperationException("this callback only handle result");
    }
}
