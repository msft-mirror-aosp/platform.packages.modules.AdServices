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

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** Base implementation for all {@code SyncCallback} classes. */
public abstract class AbstractSyncCallback implements SyncCallback {

    private static final AtomicInteger sIdGenerator = new AtomicInteger();

    protected final SyncCallbackSettings mSettings;

    private final String mId = String.valueOf(sIdGenerator.incrementAndGet());

    private final AtomicInteger mNumberCalls = new AtomicInteger();

    @Nullable private String mCustomizedToString;

    /** Default constructor. */
    public AbstractSyncCallback(SyncCallbackSettings settings) {
        mSettings = Objects.requireNonNull(settings, "settings cannot be null");
    }

    // Hack to avoid assertion failures when checking logged messages, as the number of calls could
    // be changed - should only be called by SyncCallbackTestCase.LogChecker
    @VisibleForTesting
    void setCustomizedToString(String string) {
        mCustomizedToString = Objects.requireNonNull(string);
    }

    /**
     * By default is a no-op, but subclasses could override to add additional info to {@code
     * toString()}.
     */
    protected void customizeToString(StringBuilder string) {
        if (mCustomizedToString != null) {
            string.append(mCustomizedToString);
            return;
        }
        string.append(", ")
                .append(mSettings)
                .append(", numberActualCalls=")
                .append(mNumberCalls.get());
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
        mSettings.getLogger().e("%s: %s", toString(), msg);
    }

    // Note: making msgFmt final to avoid [FormatStringAnnotation] errorprone warning
    /**
     * Convenience method to log a debug message, it includes the summarized {@link #toStringLite()}
     * in the message.
     */
    @FormatMethod
    protected final void logD(@FormatString final String msgFmt, @Nullable Object... msgArgs) {
        String msg = String.format(Locale.ENGLISH, msgFmt, msgArgs);
        mSettings.getLogger().d("%s: %s", toStringLite(), msg);
    }

    // Note: making msgFmt final to avoid [FormatStringAnnotation] errorprone warning
    /**
     * Convenience method to log a verbose message, it includes the whole {@link #toString()} in the
     * message.
     */
    @FormatMethod
    protected final void logV(@FormatString final String msgFmt, @Nullable Object... msgArgs) {
        String msg = String.format(Locale.ENGLISH, msgFmt, msgArgs);
        mSettings.getLogger().v("%s: %s", toString(), msg);
    }

    /**
     * Should be overridden by callbacks that don't support {@link #assertCalled()}.
     *
     * @return name of the alternative method(s)
     */
    @Nullable
    protected String getSetCalledAlternatives() {
        return null;
    }

    @Override
    public final boolean supportsSetCalled() {
        return getSetCalledAlternatives() == null;
    }

    @Override
    public final void setCalled() {
        logD("setCalled() called");
        String alternative = getSetCalledAlternatives();
        if (alternative != null) {
            throw new UnsupportedOperationException("Should call " + alternative + " instead!");
        }
        logV("setCalled() returning");
        internalSetCalled();
    }

    // TODO(b/337014024): make it final somehow?
    // NOTE: not final because test version might disable it
    /**
     * Real implementation of {@code setCalled()}, should be called by subclasses that don't support
     * it.
     */
    protected void internalSetCalled() {
        try {
            mNumberCalls.incrementAndGet();
        } finally {
            mSettings.countDown();
        }
    }

    // TODO(b/337014024): get rid of this?
    /** Called by {@link #assertCalled()} so subclasses can fail it if needed. */
    protected void postAssertCalled() {}


    // NOTE: not final because test version might disable it
    @Override
    public void assertCalled() throws InterruptedException {
        logD("assertCalled() called");
        try {
            mSettings.assertCalled(() -> toString());
        } catch (Exception e) {
            logE("assertCalled() failed: %s", e);
            throw e;
        }
        postAssertCalled();
        logV("assertCalled() returning");
    }


    @Override
    public final boolean isCalled() {
        return mSettings.isCalled();
    }

    @Override
    public int getNumberActualCalls() {
        return mNumberCalls.get();
    }

    @Override
    public final String toString() {
        StringBuilder string =
                new StringBuilder("[")
                        .append(getClass().getSimpleName())
                        .append(": id=")
                        .append(mId);
        customizeToString(string);
        return string.append(']').toString();
    }

    /** Gets a simpler representation of the callback. */
    public final String toStringLite() {
        return '[' + getClass().getSimpleName() + "#" + mId + ']';
    }
}
