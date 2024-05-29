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

import static com.android.adservices.shared.testing.concurrency.SyncCallback.LOG_TAG;

import com.android.adservices.shared.testing.Identifiable;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.Logger.RealLogger;
import com.android.adservices.shared.testing.Nullable;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Defines the settings and some other internal state of a {@code SyncCallback}.
 *
 * <p><b>Note: </b>the internal state includes the current invocations, so normally each callback
 * should have its own settings (for example, it should not be used as a static variable with some
 * default settings), although it could be shared when a test need to block after distinct callbacks
 * are called.
 */
public final class SyncCallbackSettings implements Identifiable {

    /** Timeout set by default constructor */
    public static final long DEFAULT_TIMEOUT_MS = 5_000;

    private static final AtomicInteger sIdGenerator = new AtomicInteger();

    private final String mId = String.valueOf(sIdGenerator.incrementAndGet());
    private final int mExpectedNumberCalls;
    private final CountDownLatch mLatch;
    private final long mMaxTimeoutMs;
    private final boolean mFailIfCalledOnMainThread;
    private final Supplier<Boolean> mIsMainThreadSupplier;
    private final Logger mLogger;

    private SyncCallbackSettings(Builder builder) {
        mExpectedNumberCalls = builder.mExpectedNumberCalls;
        mLatch = new CountDownLatch(mExpectedNumberCalls);
        mMaxTimeoutMs = builder.mMaxTimeoutMs;
        mFailIfCalledOnMainThread = builder.mFailIfCalledOnMainThread;
        mIsMainThreadSupplier = builder.mIsMainThreadSupplier;
        mLogger = new Logger(builder.mRealLogger, LOG_TAG);
    }

    /** Gets the expected number of calls this callback should receive before it's done. */
    public int getExpectedNumberCalls() {
        return mExpectedNumberCalls;
    }

    /** Gets the maximum time the callback could wait before failing. */
    public long getMaxTimeoutMs() {
        return mMaxTimeoutMs;
    }

    /** Checks whether the callback should fail if called from the main thread. */
    public boolean isFailIfCalledOnMainThread() {
        return mFailIfCalledOnMainThread;
    }

    // TODO(b/337014024): add unit test to check this logic (currently it's indirectly checked by
    // AbstractTestSyncCallbackTest, but it will be refactored)
    public boolean isMainThread() {
        return mIsMainThreadSupplier.get();
    }

    @Override
    public String getId() {
        return mId;
    }

    @Override
    public String toString() {
        return "settingsId="
                + mId
                + ", expectedNumberCalls="
                + mExpectedNumberCalls
                + ", maxTimeoutMs="
                + mMaxTimeoutMs
                + ", failIfCalledOnMainThread="
                + mFailIfCalledOnMainThread
                + ", missingCalls="
                + mLatch.getCount();
    }

    Logger getLogger() {
        return mLogger;
    }

    // NOTE: log of methods below are indirectly unit tested by the callback test - testing it again
    // on SyncCallbackSettings would be an overkill (they're not public anyways), so
    // SyncCallbackSettings just asserts that they're properly logged.

    void countDown() {
        logD("countDown() called");
        try {
            mLatch.countDown();
        } finally {
            logV("leaving countDown()");
        }
    }

    /**
     * @deprecated - TODO(b/337014024): merge with other)
     */
    @Deprecated
    void await() throws InterruptedException {
        mLatch.await();
    }

    boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        logD("await() called");
        try {
            return mLatch.await(timeout, unit);
        } finally {
            logV("leaving await()");
        }
    }

    boolean isCalled() {
        return mLatch.getCount() == 0;
    }

    @FormatMethod
    private void logD(@FormatString String msgFmt, @Nullable Object... msgArgs) {
        String realMessage = String.format(msgFmt, msgArgs);
        mLogger.d("[settingsId#%s]: %s", getId(), realMessage);
    }

    @FormatMethod
    private void logV(@FormatString String msgFmt, @Nullable Object... msgArgs) {
        String realMessage = String.format(msgFmt, msgArgs);
        mLogger.v("[SyncCallbackSettings: %s]: %s", toString(), realMessage);
    }

    /** Bob the Builder! */
    public static final class Builder {

        private int mExpectedNumberCalls = 1;
        private long mMaxTimeoutMs = DEFAULT_TIMEOUT_MS;
        private boolean mFailIfCalledOnMainThread = true;
        private final Supplier<Boolean> mIsMainThreadSupplier;
        private final RealLogger mRealLogger;

        // Package protected so it's only called by SyncCallbackFactory and unit tests
        Builder(RealLogger realLogger, Supplier<Boolean> isMainThreadSupplier) {
            mRealLogger = Objects.requireNonNull(realLogger, "realLogger cannot be null");
            mIsMainThreadSupplier =
                    Objects.requireNonNull(
                            isMainThreadSupplier, "isMainThreadSupplier cannot be null");
        }

        /** See {@link SyncCallbackSettings#getExpectedNumberCalls()}. */
        public Builder setExpectedNumberCalls(int value) {
            assertIsPositive(value);
            mExpectedNumberCalls = value;
            return this;
        }

        /** See {@link SyncCallbackSettings#getMaxTimeoutMs(long)}. */
        public Builder setMaxTimeoutMs(long value) {
            assertIsPositive(value);
            mMaxTimeoutMs = value;
            return this;
        }

        /** See {@link SyncCallbackSettings#isFailIfCalledOnMainThread()}. */
        public Builder setFailIfCalledOnMainThread(boolean value) {
            mFailIfCalledOnMainThread = value;
            return this;
        }

        /** Can we build it? Yes we can! */
        public SyncCallbackSettings build() {
            return new SyncCallbackSettings(this);
        }
    }

    private static void assertIsPositive(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("must be a positive value");
        }
    }
}
