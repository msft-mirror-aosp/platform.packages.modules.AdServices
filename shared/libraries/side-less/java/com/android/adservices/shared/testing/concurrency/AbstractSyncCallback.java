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

import com.android.adservices.shared.testing.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import com.google.errorprone.annotations.concurrent.GuardedBy;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Base implementation for all {@code SyncCallback} classes. */
public abstract class AbstractSyncCallback implements SyncCallback, FreezableToString {

    private static final AtomicInteger sIdGenerator = new AtomicInteger();

    protected final SyncCallbackSettings mSettings;

    private final String mId = String.valueOf(sIdGenerator.incrementAndGet());

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private int mNumberCalls;

    private final AtomicReference<String> mFrozenToString = new AtomicReference<>();

    // Used to fail assertCalled() if something bad happened before
    @GuardedBy("mLock")
    @Nullable
    private RuntimeException mOnAssertCalledException;

    // The "real" callback - used in cases (mostly loggin) where a callback delegates its methods
    // to another one.
    private final AbstractSyncCallback mRealCallback;

    /** Default constructor. */
    public AbstractSyncCallback(SyncCallbackSettings settings) {
        this(/* realCallback= */ null, settings);
    }

    @VisibleForTesting
    AbstractSyncCallback(
            @Nullable AbstractSyncCallback realCallback, SyncCallbackSettings settings) {
        mRealCallback = realCallback != null ? realCallback : this;
        mSettings = Objects.requireNonNull(settings, "settings cannot be null");
    }

    @Override
    public final void freezeToString() {
        mFrozenToString.set("FROZEN" + toStringLite());
    }

    /**
     * By default is a no-op, but subclasses could override to add additional info to {@code
     * toString()}.
     */
    protected void customizeToString(StringBuilder string) {
        string.append(", ")
                .append(mSettings)
                .append(", numberActualCalls=")
                .append(getNumberActualCalls());
    }

    @Override
    public final String getId() {
        return mId;
    }

    @Override
    public final SyncCallbackSettings getSettings() {
        return mSettings;
    }

    // Note: making msgFmt final to avoid [FormatStringAnnotation] errorprone warning
    /**
     * Convenience method to log an error message, it includes the whole {@link #toString()} in the
     * message.
     */
    @FormatMethod
    protected final void logE(@FormatString final String msgFmt, @Nullable Object... msgArgs) {
        String msg = String.format(Locale.ENGLISH, msgFmt, msgArgs);
        mSettings.getLogger().e("%s: %s", mRealCallback, msg);
    }

    // Note: making msgFmt final to avoid [FormatStringAnnotation] errorprone warning
    /**
     * Convenience method to log a debug message, it includes the summarized {@link #toStringLite()}
     * in the message.
     */
    @FormatMethod
    protected final void logD(@FormatString final String msgFmt, @Nullable Object... msgArgs) {
        String msg = String.format(Locale.ENGLISH, msgFmt, msgArgs);
        mSettings.getLogger().d("%s: %s", mRealCallback.toStringLite(), msg);
    }

    // Note: making msgFmt final to avoid [FormatStringAnnotation] errorprone warning
    /**
     * Convenience method to log a verbose message, it includes the whole {@link #toString()} in the
     * message.
     */
    @FormatMethod
    protected final void logV(@FormatString final String msgFmt, @Nullable Object... msgArgs) {
        String msg = String.format(Locale.ENGLISH, msgFmt, msgArgs);
        mSettings.getLogger().v("%s: %s", mRealCallback.toStringLite(), msg);
    }

    // TODO(b/342448771): make it package protected once classes are moved
    /**
     * Real implementation of {@code setCalled()}, should be called by subclass to "unblock" the
     * callback.
     *
     * @return {@code methodName}
     */
    public final String internalSetCalled(String methodName) {
        logD("%s called on %s", methodName, Thread.currentThread().getName());
        synchronized (mLock) {
            if (mSettings.isFailIfCalledOnMainThread() && mSettings.isMainThread()) {
                String errorMsg =
                        methodName
                                + " called on main thread ("
                                + Thread.currentThread().getName()
                                + ")";
                mOnAssertCalledException = new CalledOnMainThreadException(errorMsg);
            }
            mNumberCalls++;
        }
        mSettings.countDown();
        logV("%s returning", methodName);
        return methodName;
    }

    @Override
    public void assertCalled() throws InterruptedException {
        internalAssertCalled(mSettings.getMaxTimeoutMs());
    }

    // TODO(b/342448771): make it package protected once classes are moved
    /**
     * Real implementation of {@link #assertCalled(timeoutMs)} - subclasses overriding {@link
     * #assertCalled(timeoutMs)} should call it.
     */
    public final void internalAssertCalled(long timeoutMs) throws InterruptedException {
        logD("assertCalled() called on %s", Thread.currentThread().getName());
        try {
            mSettings.assertCalled(timeoutMs, () -> toString());
        } catch (Exception e) {
            logE("assertCalled() failed: %s", e);
            throw e;
        }
        synchronized (mLock) {
            if (mOnAssertCalledException != null) {
                logE("assertCalled() failed: %s", mOnAssertCalledException);
                throw mOnAssertCalledException;
            }
        }
        logV("assertCalled() returning");
    }

    @Override
    public final boolean isCalled() {
        return mSettings.isCalled();
    }

    @Override
    public int getNumberActualCalls() {
        synchronized (mLock) {
            return mNumberCalls;
        }
    }

    @Override
    public final String toString() {
        String frozenToString = mFrozenToString.get();
        if (frozenToString != null) {
            return frozenToString;
        }
        // Should guard access to mOnAssertCalledException, but we don't care
        @SuppressWarnings("GuardedBy")
        StringBuilder string =
                new StringBuilder("[")
                        .append(getClass().getSimpleName())
                        .append(": id=")
                        .append(mId)
                        .append(", onAssertCalledException=")
                        .append(mOnAssertCalledException);
        customizeToString(string);
        return string.append(']').toString();
    }

    /** Gets a simpler representation of the callback. */
    public final String toStringLite() {
        return '[' + getClass().getSimpleName() + "#" + mId + ']';
    }
}
