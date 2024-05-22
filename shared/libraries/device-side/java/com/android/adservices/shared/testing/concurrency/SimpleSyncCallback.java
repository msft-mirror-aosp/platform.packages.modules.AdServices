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

/** Simplest implementation of a {@link SyncCallback} for tests. */
public final class SimpleSyncCallback extends AbstractTestSyncCallback {

    /** Default constructor (for single call). */
    public SimpleSyncCallback() {
        this(/* expectedNumberOfCalls= */ 1);
    }

    /** Constructor for multiple calls. */
    public SimpleSyncCallback(int expectedNumberOfCalls) {
        super(expectedNumberOfCalls);
    }
}
