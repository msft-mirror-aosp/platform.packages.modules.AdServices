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

import com.android.adservices.shared.concurrency.AbstractSyncCallback;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.Logger.RealLogger;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.concurrent.TimeUnit;

/** Base class for all test-related, side-agnostic sync callback implementations . */
public abstract class AbstractSidelessTestSyncCallback extends AbstractSyncCallback
        implements TestSyncCallback {

    /** Constant used to help readability. */
    public static final int EXPECTS_ONLY_ONE_CALL = 1;

    /** Timeout set by default constructor */
    public static final long DEFAULT_TIMEOUT_MS = 5_000;

    private final long mTimeoutMs;

    private final Logger mLogger;

    /** Default constructor (uses {@link #DEFAULT_TIMEOUT_MS}) that expects a single call. */
    protected AbstractSidelessTestSyncCallback(RealLogger realLogger) {
        this(realLogger, DEFAULT_TIMEOUT_MS);
    }

    /** Custom with custom number of expected calls (using {@link #DEFAULT_TIMEOUT_MS}). */
    protected AbstractSidelessTestSyncCallback(RealLogger realLogger, int expectedNumberOfCalls) {
        this(realLogger, expectedNumberOfCalls, DEFAULT_TIMEOUT_MS);
    }

    /** Constructor with custom timeout (for a single call). */
    protected AbstractSidelessTestSyncCallback(RealLogger realLogger, long timeoutMs) {
        this(realLogger, /* expectedNumberOfCalls= */ 1, timeoutMs);
    }

    /** Constructor with custom timeout and number of expected calls. */
    protected AbstractSidelessTestSyncCallback(
            RealLogger realLogger, int expectedNumberOfCalls, long timeoutMs) {
        super(expectedNumberOfCalls);
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        mTimeoutMs = timeoutMs;
        mLogger = new Logger(realLogger, LOG_TAG);
    }

    @Override
    public long getMaxTimeoutMs() {
        return mTimeoutMs;
    }

    @Override
    public final void waitCalled() throws InterruptedException {
        throwWaitCalledNotSupported();
    }

    @Override
    public final void waitCalled(long timeout, TimeUnit unit) throws InterruptedException {
        throwWaitCalledNotSupported();
    }

    /** Called by {@link #assertCalled()} so subclasses can fail it if needed. */
    protected void postAssertCalled() {}

    @Override
    public final void assertCalled() throws InterruptedException {
        super.waitCalled(mTimeoutMs, TimeUnit.MILLISECONDS);
        postAssertCalled();
    }

    @FormatMethod
    @Override
    public final void logE(@FormatString String msgFmt, Object... msgArgs) {
        mLogger.e("%s: %s", toString(), String.format(msgFmt, msgArgs));
    }

    @FormatMethod
    @Override
    public final void logD(@FormatString String msgFmt, Object... msgArgs) {
        mLogger.d("[%s]: %s", getId(), String.format(msgFmt, msgArgs));
    }

    @FormatMethod
    @Override
    public final void logV(@FormatString String msgFmt, Object... msgArgs) {
        mLogger.v("%s: %s", toString(), String.format(msgFmt, msgArgs));
    }

    private void throwWaitCalledNotSupported() {
        throw new UnsupportedOperationException("should call assertCalled() instead");
    }
}
