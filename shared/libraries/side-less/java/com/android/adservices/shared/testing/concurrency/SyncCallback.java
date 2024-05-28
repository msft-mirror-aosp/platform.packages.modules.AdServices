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
import com.android.adservices.shared.testing.Nullable;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

/** Base interface for all testing-related sync callbacks. */
public interface SyncCallback extends Identifiable {

    /**
     * Asserts the callback was called or throw if it times out - the timeout value is defined by
     * the constructor and can be obtained through {@link #getSettings()}.
     */
    void assertCalled() throws InterruptedException;

    /** Returns whether the callback was called (at least) the expected number of times. */
    boolean isCalled();

    /** Gets the callback settings. */
    SyncCallbackSettings getSettings();

    /**
     * Convenience method to log a debug message.
     *
     * <p>By default it's a no-op, but subclasses should implement it including all info (provided
     * by {@link #toString()}) in the message.
     */
    @FormatMethod
    void logE(@FormatString String msgFmt, @Nullable Object... msgArgs);

    /**
     * Convenience method to log a debug message.
     *
     * <p>By default it's a no-op, but subclasses should implement it including the {@link #getId()
     * id} in the message.
     */
    @FormatMethod
    void logD(@FormatString String msgFmt, @Nullable Object... msgArgs);

    /**
     * Convenience method to log a verbose message.
     *
     * <p>By default it's a no-op, but subclasses should implement it including all info (provided
     * by {@link #toString()}) in the message.
     */
    @FormatMethod
    void logV(@FormatString String msgFmt, @Nullable Object... msgArgs);
}
