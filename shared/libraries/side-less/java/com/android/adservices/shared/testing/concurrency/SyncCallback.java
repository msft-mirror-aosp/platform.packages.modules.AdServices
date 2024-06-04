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

import com.android.adservices.shared.testing.Identifiable;

// TODO(b/337014024): explain that subclasse must provide a "setCalled" method
/** Base interface for all testing-related sync callbacks. */
public interface SyncCallback extends Identifiable {

    /** Tag used on {@code logcat} calls. */
    String LOG_TAG = "SyncCallback";

    /**
     * Asserts the callback was called or throw if it times out - the timeout value is defined by
     * the constructor and can be obtained through {@link #getSettings()}.
     */
    void assertCalled() throws InterruptedException;

    /** Returns whether the callback was called (at least) the expected number of times. */
    boolean isCalled();

    /** Gets the total number of calls so far. */
    int getNumberActualCalls();

    /** Gets the callback settings. */
    SyncCallbackSettings getSettings();
}
