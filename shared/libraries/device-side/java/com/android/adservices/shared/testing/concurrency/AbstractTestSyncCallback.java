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

import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import com.android.adservices.shared.testing.AndroidLogger;

/** Base class for device-side sync callbacks for testing. */
public abstract class AbstractTestSyncCallback extends AbstractSidelessTestSyncCallback {

    /** Constant used to help readability. */
    public static final boolean FAIL_IF_CALLED_ON_MAIN_THREAD = true;

    /** Constant used to help readability. */
    public static final boolean DONT_FAIL_IF_CALLED_ON_MAIN_THREAD = false;

    // NOTE: currently there's no usage that takes a custom timeout, but we could add on demand

    private final boolean mFailIfCalledOnMainThread;

    @Nullable private IllegalStateException mInternalFailure;

    private final long mEpoch = SystemClock.elapsedRealtime();

    protected AbstractTestSyncCallback() {
        this(EXPECTS_ONLY_ONE_CALL);
    }

    protected AbstractTestSyncCallback(int expectedNumberOfCalls) {
        this(FAIL_IF_CALLED_ON_MAIN_THREAD, expectedNumberOfCalls);
    }

    protected AbstractTestSyncCallback(
            boolean failIfCalledOnMainThread, int expectedNumberOfCalls) {
        super(AndroidLogger.getInstance(), expectedNumberOfCalls);
        mFailIfCalledOnMainThread = failIfCalledOnMainThread;
    }

    @Override
    protected void customizeToString(StringBuilder string) {
        super.customizeToString(string);

        string.append(", failIfCalledOnMainThread=")
                .append(mFailIfCalledOnMainThread)
                .append(", epoch=")
                .append(mEpoch)
                .append(", internalFailure=")
                .append(mInternalFailure);
    }

    @Override
    public void setCalled() {
        long delta = SystemClock.elapsedRealtime() - mEpoch;
        Thread currentThread = Thread.currentThread();
        logV("setCalled() called in %d ms on %s", delta, currentThread);
        if (mFailIfCalledOnMainThread
                && Looper.getMainLooper() != null
                && Looper.getMainLooper().isCurrentThread()) {
            String errorMsg = "setCalled() called on main thread (" + currentThread + ")";
            logE("%s; assertCalled() will throw an IllegalStateException", errorMsg);
            mInternalFailure = new IllegalStateException(errorMsg);
        }
        super.setCalled();
    }

    @Override
    protected void postAssertCalled() {
        if (mInternalFailure != null) {
            throw mInternalFailure;
        }
    }
}
