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

    @Nullable private IllegalStateException mInternalFailure;

    private final long mEpoch = SystemClock.elapsedRealtime();

    protected AbstractTestSyncCallback(SyncCallbackSettings settings) {
        super(AndroidLogger.getInstance(), settings);
    }

    @Override
    protected void customizeToString(StringBuilder string) {
        super.customizeToString(string);

        string.append(", epoch=")
                .append(mEpoch)
                .append(", internalFailure=")
                .append(mInternalFailure);
    }

    @Override
    public void setCalled() {
        long delta = SystemClock.elapsedRealtime() - mEpoch;
        Thread currentThread = Thread.currentThread();
        logV("setCalled() called in %d ms on %s", delta, currentThread);
        if (mSettings.isFailIfCalledOnMainThread()
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
