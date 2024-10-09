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

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Custom exception to indicate a {@code SyncCallback} timed out. */
public final class SyncCallbackTimeoutException extends IllegalStateException {

    // TODO(b/341797803): @VisibleForTesting
    static final String MSG_TEMPLATE = "%s: not called in %d %s";

    private final String mWhat;
    private final long mTimeout;
    private final TimeUnit mUnit;

    /**
     * Default constructor.
     *
     * @param what technically the {@code SyncCallback}, but could be any callback that timed out.
     * @param timeout timeout duration
     * @param unit timeout unit
     */
    public SyncCallbackTimeoutException(String what, long timeout, TimeUnit unit) {
        super(
                String.format(
                        Locale.ENGLISH,
                        MSG_TEMPLATE,
                        Objects.requireNonNull(what),
                        timeout,
                        Objects.requireNonNull(unit)));
        mWhat = what;
        mTimeout = timeout;
        mUnit = unit;
    }

    /** Gets the description of the callback that timed out. */
    public String getWhat() {
        return mWhat;
    }

    /** Gets the timeout duration. */
    public long getTimeout() {
        return mTimeout;
    }

    /** Gets the timeout unit. */
    public TimeUnit getUnit() {
        return mUnit;
    }
}
