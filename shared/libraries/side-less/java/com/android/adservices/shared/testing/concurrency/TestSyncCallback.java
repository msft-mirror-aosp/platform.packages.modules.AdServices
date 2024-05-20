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

import com.android.adservices.shared.concurrency.SyncCallback;
import com.android.adservices.shared.testing.Identifiable;

/** Base interface for all testing-related sync callbacks. */
public interface TestSyncCallback extends SyncCallback, Identifiable {

    /**
     * Asserts the callback was called or throw if it times out - the timeout value is defined by
     * the constructor and can be obtained through {@link #getMaxTimeoutMs()}.
     */
    void assertCalled() throws InterruptedException;

    /** Returns the maximum timeout before {@link #assertCalled()} would throw. */
    long getMaxTimeoutMs();
}
