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
package com.android.adservices.shared.system;

import android.annotation.Nullable;
import android.content.Context;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.Locale;
import java.util.Objects;

/**
 * Entry point to get the global {@link Context} on system-server classes.
 *
 * <p>The goal of this class is to make it easier to get a context in situations like static methods
 * or singletons, although it's not meant as a crutch to keep using them.
 *
 * <p>This class is <b>NOT</b> thread safe - it assumes {@link #set(Context)} will be called just
 * once by a class that's initialized at boot time (typically a {@link
 * com.android.server.SystemService} subclass).
 */
public final class SystemContextSingleton {

    private static final String TAG = "SystemCtxSingleton";

    @VisibleForTesting
    public static final String ERROR_MESSAGE_SET_NOT_CALLED = "set() not called yet";

    @SuppressWarnings("StaticFieldLeak")
    private static Context sContext;

    /**
     * Gets the application context.
     *
     * @throws IllegalStateException if not {@link #set(Context) set} yet.
     */
    public static Context get() {
        if (sContext == null) {
            throw new IllegalStateException(ERROR_MESSAGE_SET_NOT_CALLED);
        }
        return sContext;
    }

    /**
     * Sets the singleton context.
     *
     * @throws IllegalStateException if the singleton was already {@link #set(Context) set} with a
     *     different context.
     */
    @SuppressWarnings("AvoidStaticContext")
    public static Context set(Context context) {
        Objects.requireNonNull(context, "context cannot be null");

        // Set if it's not set yet
        if (sContext == null) {
            sContext = context;
            logI("Set singleton context as %s", context);
            return sContext;
        }

        // Otherwise, check it's the same.
        if (sContext != context) {
            // TODO(b/309169907): log to CEL
            throw new IllegalStateException(
                    "Trying to set context as "
                            + context
                            + ", but it was already set as "
                            + sContext);
        }
        return sContext;
    }

    // TODO(b/285300419): make it package protected so it's only accessed by rule - would need to
    // move the rule to this package, which currently would be a pain (as all testing artifacts
    // are under c.a.a.shared.testing packages)
    /** Gets the global context, returning {@code null} if it's not set yet. */
    @VisibleForTesting
    public static Context getForTests() {
        logI("getForTests(): returning %s", sContext);
        return sContext;
    }

    // TODO(b/285300419): make it package protected so it's only accessed by rule - would need to
    // move the rule to this package, which currently would be a pain (as all testing artifacts
    // are under c.a.a.shared.testing packages)
    /**
     * Sets the context singleton as the given {@code context}, without doing any check.
     *
     * @return the previous context
     */
    @VisibleForTesting
    @Nullable
    @SuppressWarnings("AvoidStaticContext")
    public static Context setForTests(Context context) {
        Context previousContext = sContext;
        logI("setForTests(): from %s to %s.", previousContext, context);
        sContext = context;
        return previousContext;
    }

    // NOTE: ideally should use logI(), but it's not available for mainline
    @FormatMethod
    private static void logI(@FormatString String fmt, @Nullable Object... args) {
        String msg = String.format(Locale.ENGLISH, fmt, args);
        Slog.i(TAG, msg);
    }

    private SystemContextSingleton() {
        throw new UnsupportedOperationException("provides only static methods");
    }
}
