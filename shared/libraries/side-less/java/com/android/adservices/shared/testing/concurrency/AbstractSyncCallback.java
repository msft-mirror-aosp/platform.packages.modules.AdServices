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

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// NOTE: this class is basically an abstraction of CountdownLatch and doesn't have any testing
// specific characteristics, so it could be used on production code as well.
/** Base implementation for {@code SyncCallback}. */
public abstract class AbstractSyncCallback {

    private static final AtomicInteger sIdGenerator = new AtomicInteger();

    protected final SyncCallbackSettings mSettings;

    private final String mId = String.valueOf(sIdGenerator.incrementAndGet());

    /** Default constructor. */
    public AbstractSyncCallback(SyncCallbackSettings settings) {
        mSettings = Objects.requireNonNull(settings, "settings cannot be null");
    }

    /**
     * By default is a no-op, but subclasses could override to add additional info to {@code
     * toString()}.
     */
    protected void customizeToString(StringBuilder string) {}

    /** Gets a unique id identifying the callback - used for logging / debugging purposes. */
    public final String getId() {
        return mId;
    }

    // TODO(b/341797803): add @Nullable on msgArgs and @VisibleForTesting(protected)
    /**
     * Convenience method to log a debug message.
     *
     * <p>By default it's a no-op, but subclasses should implement it including all info (provided
     * by {@link #toString()}) in the message.
     */
    @FormatMethod
    public void logE(@FormatString String msgFmt, Object... msgArgs) {
        // TODO(b/280460130): use side-less Logger so it's not empty
    }

    // TODO(b/341797803): add @Nullable on msgArgs and @VisibleForTesting(protected)
    /**
     * Convenience method to log a debug message.
     *
     * <p>By default it's a no-op, but subclasses should implement it including the {@link #getId()
     * id} in the message.
     */
    @FormatMethod
    public void logD(@FormatString String msgFmt, Object... msgArgs) {
        // TODO(b/280460130): use side-less Logger so it's not empty
    }

    // TODO(b/341797803): add @Nullable on msgArgs and @VisibleForTesting(protected)
    /**
     * Convenience method to log a verbose message.
     *
     * <p>By default it's a no-op, but subclasses should implement it including all info (provided
     * by {@link #toString()}) in the message.
     */
    @FormatMethod
    public void logV(@FormatString String msgFmt, Object... msgArgs) {
        // TODO(b/280460130): use side-less Logger so it's not empty
    }

    // NOTE: not final because test version might disable it
    /**
     * Indicates the callback was called, so it unblocks {@link #waitCalled()} / {@link
     * #waitCalled(long, TimeUnit)}.
     */
    public void setCalled() {
        logD("setCalled() called");
        try {
            mSettings.countDown();
        } finally {
            logV("setCalled() returning");
        }
    }

    // NOTE: not final because test version might disable it
    /**
     * Wait (indefinitely) until all calls to {@link #setCalled()} were made.
     *
     * @throws InterruptedException if thread was interrupted while waiting.
     */
    public void waitCalled() throws InterruptedException {
        logD("waitCalled() called");
        try {
            mSettings.await();
        } finally {
            logV("waitCalled() returning");
        }
    }

    // NOTE: not final because test version might set timeout on constructor
    /**
     * Wait (up to given time) until all calls to {@link #setCalled()} were made.
     *
     * @throws InterruptedException if thread was interrupted while waiting.
     * @throws IllegalStateException if not called before it timed out.
     */
    public void waitCalled(long timeout, TimeUnit unit) throws InterruptedException {
        logD("waitCalled(%d, %s) called", timeout, unit);
        try {
            if (!mSettings.await(timeout, unit)) {
                throw new SyncCallbackTimeoutException(toString(), timeout, unit);
            }
        } finally {
            logV("waitCalled(%d, %s) returning", timeout, unit);
        }
    }

    /** Returns whether the callback was called (at least) the expected number of times. */
    public final boolean isCalled() {
        return mSettings.isCalled();
    }

    /**
     * Helper method that fills the {@code string} with the content of {@link #toString()} but
     * without the enclosing {@code [class: ]} part.
     */
    public final StringBuilder appendInfo(StringBuilder string) {
        Objects.requireNonNull(string).append("id=").append(mId).append(", ").append(mSettings);
        customizeToString(string);
        return string;
    }

    @Override
    public final String toString() {
        return appendInfo(new StringBuilder("[").append(getClass().getSimpleName()).append(": "))
                .append(']')
                .toString();
    }
}
