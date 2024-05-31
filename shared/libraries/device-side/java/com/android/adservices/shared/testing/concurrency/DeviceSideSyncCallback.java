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

import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.Nullable;


import java.util.Objects;

/** Base class for device-side sync callbacks for testing. */
public abstract class DeviceSideSyncCallback extends AbstractSyncCallback
        implements IBinderSyncCallback {

    @Nullable private RuntimeException mInternalFailure;

    // TODO(b/337014024): must abstract SystemClock and IBinder so it can be moved to sideless
    private final long mEpoch = SystemClock.elapsedRealtime();

    protected DeviceSideSyncCallback(SyncCallbackSettings settings) {
        super(settings);
    }

    @Override
    protected void customizeToString(StringBuilder string) {
        super.customizeToString(string);

        string.append(", epoch=")
                .append(mEpoch)
                .append(", internalFailure=")
                .append(mInternalFailure);
    }

    /**
     * Sets an internal failure to be thrown by {@link #postAssertCalled()}.
     *
     * <p>This method should be used to "delay" an exception that could otherwise be thrown in a
     * background thread.
     */
    protected void setInternalFailure(RuntimeException failure) {
        mInternalFailure = Objects.requireNonNull(failure, "failure cannot be null");
    }

    @Override
    public void internalSetCalled() {
        long delta = SystemClock.elapsedRealtime() - mEpoch;
        Thread currentThread = Thread.currentThread();
        if (false) {
            // TODO(b/337014024): log call below was removed because it would break the log
            // assertions on SimpleSyncCallbackTest#testAssertCalled
            // We could try to fix the test, but it would be better to move these extra info (epoch
            // and thread name) to the superclass, but only if they're really useful (as that info
            // is kind of already provided by logcat itself)
            logV("setCalled() called in %d ms on %s", delta, currentThread);
        }
        if (mSettings.isFailIfCalledOnMainThread() && mSettings.isMainThread()) {
            String errorMsg = "setCalled() called on main thread (" + currentThread + ")";
            logE("%s; assertCalled() will throw an IllegalStateException", errorMsg);
            mInternalFailure = new CalledOnMainThreadException(errorMsg);
        }
        super.internalSetCalled();
    }

    @Override
    protected void postAssertCalled() {
        if (mInternalFailure != null) {
            throw mInternalFailure;
        }
    }

    @Override
    public IBinder asBinder() {
        return null;
    }
}
