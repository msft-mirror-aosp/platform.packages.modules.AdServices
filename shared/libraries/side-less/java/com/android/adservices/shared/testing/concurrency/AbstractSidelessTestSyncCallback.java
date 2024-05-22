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

import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.Logger.RealLogger;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.concurrent.TimeUnit;

/** Base class for all test-related, side-agnostic sync callback implementations . */
public abstract class AbstractSidelessTestSyncCallback extends AbstractSyncCallback
        implements TestSyncCallback {

    private final Logger mLogger;

    /** Default constructor. */
    protected AbstractSidelessTestSyncCallback(
            RealLogger realLogger, SyncCallbackSettings settings) {
        super(settings);
        mLogger = new Logger(realLogger, LOG_TAG);
    }

    @Override
    public SyncCallbackSettings getSettings() {
        return mSettings;
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
        super.waitCalled(mSettings.getMaxTimeoutMs(), TimeUnit.MILLISECONDS);
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
