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

package com.android.adservices.shared.meta_testing;

import com.android.adservices.shared.testing.DynamicLogger;
import com.android.adservices.shared.testing.Logger;

import java.util.Objects;

/**
 * Helper class used to wrap an object
 *
 * @param <T> class being wrapped
 */
public abstract class Wrapper<T> {

    protected final Logger mLog;

    private T mWrapped;

    /** Default constructor, sets logger. */
    public Wrapper() {
        this(/* checkLogger= */ false, /* logger= */ null);
    }

    /** Constructor with a custom logger. */
    public Wrapper(Logger logger) {
        this(/* checkLogger= */ true, logger);
    }

    private Wrapper(boolean checkLogger, Logger logger) {
        if (logger != null) {
            mLog = logger;
        } else if (!checkLogger) {
            mLog = new Logger(DynamicLogger.getInstance(), getClass());
        } else {
            throw new NullPointerException("logger cannot be null");
        }
    }

    /** Sets the wrapped object. */
    public final void setWrapped(T wrapped) {
        mWrapped = Objects.requireNonNull(wrapped, "wrapped object cannot be null");
    }

    /** Gets the wrapped object. */
    public final T getWrapped() {
        if (mWrapped == null) {
            throw new IllegalStateException("setWrapped() not called");
        }
        return mWrapped;
    }

    @Override
    public String toString() {
        return mLog.getTag() + "[wrapped=" + mWrapped + "]";
    }
}
