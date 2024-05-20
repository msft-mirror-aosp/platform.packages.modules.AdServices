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
package com.android.adservices.shared.concurrency;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Base implementation for {@link SyncCallback}. */
public abstract class AbstractSyncCallback implements SyncCallback {

    private static final AtomicInteger sIdGenerator = new AtomicInteger();

    private final String mId = getClass().getSimpleName() + '#' + sIdGenerator.incrementAndGet();
    private final int mNumberExpectedCalls;
    private final CountDownLatch mLatch;

    /** Default constructor (for callbacks that should be called just once). */
    public AbstractSyncCallback() {
        this(1);
    }

    /** Constructor for callbacks that should be called multiple times. */
    public AbstractSyncCallback(int numberOfExpectedCalls) {
        mNumberExpectedCalls = numberOfExpectedCalls;
        mLatch = new CountDownLatch(mNumberExpectedCalls);
    }

    /**
     * By default is a no-op, but subclasses could override to add additional info to {@code
     * toString()}.
     */
    protected void customizeToString(StringBuilder string) {}

    /** Gets a unique id identifying the callback - used for logging / debugging purposes. */
    protected final String getId() {
        return mId;
    }

    // TODO(b/341797803): add @Nullable on msgArgs
    /**
     * Convenience method to log a debug message.
     *
     * <p>By default it's a no-op, but subclasses should implement it including the {@link #getId()
     * id} in the message.
     */
    @FormatMethod
    protected void logD(@FormatString String msgFmt, Object... msgArgs) {
        // TODO(b/280460130): use side-less Logger so it's not empty
    }

    // TODO(b/341797803): add @Nullable on msgArgs
    /**
     * Convenience method to log a verbose message.
     *
     * <p>By default it's a no-op, but subclasses should implement it including all info (provided
     * by {@link #toString()}) in the message.
     */
    @FormatMethod
    protected void logV(@FormatString String msgFmt, Object... msgArgs) {
        // TODO(b/280460130): use side-less Logger so it's not empty
    }

    @Override
    public final void setCalled() {
        logD("setCalled() called");
        try {
            mLatch.countDown();
        } finally {
            logV("setCalled() returning");
        }
    }

    @Override
    public final void waitCalled() throws InterruptedException {
        logD("waitCalled() called");
        try {
            mLatch.await();
        } finally {
            logV("waitCalled() returning");
        }
    }

    @Override
    public final void waitCalled(long timeout, TimeUnit unit) throws InterruptedException {
        logD("waitCalled(%d, %s) called", timeout, unit);
        try {
            if (!mLatch.await(timeout, unit)) {
                throw new SyncCallbackTimeoutException(toString(), timeout, unit);
            }
        } finally {
            logV("waitCalled(%d, %s) returning", timeout, unit);
        }
    }

    @Override
    public final boolean isCalled() {
        return mLatch.getCount() == 0;
    }

    @Override
    public final String toString() {
        StringBuilder string =
                new StringBuilder()
                        .append('[')
                        .append(mId)
                        .append(": numberExpectedCalls=")
                        .append(mNumberExpectedCalls)
                        .append(", missingCalls=")
                        .append(mLatch.getCount());
        customizeToString(string);
        return string.append(']').toString();
    }
}
