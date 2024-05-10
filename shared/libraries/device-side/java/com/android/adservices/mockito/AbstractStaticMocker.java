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
package com.android.adservices.mockito;

import android.util.Log;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.Objects;

/** Base implementation for static mockers. */
public abstract class AbstractStaticMocker {

    static final String TAG = "StaticMocker";

    protected final String mTag = getClass().getSimpleName();

    private final StaticClassChecker mStaticClassChecker;

    protected AbstractStaticMocker(StaticClassChecker staticClassChecker) {
        mStaticClassChecker = Objects.requireNonNull(staticClassChecker);
    }

    @FormatMethod
    protected final void logV(@FormatString String fmt, Object... args) {
        Log.v(
                TAG,
                "on "
                        + mStaticClassChecker.getTestName()
                        + " by "
                        + mTag
                        + ": "
                        + String.format(fmt, args));
    }

    protected final void assertSpiedOrMocked(Class<?> clazz) {
        if (!mStaticClassChecker.isSpiedOrMocked(clazz)) {
            throw new IllegalStateException(
                    "Test doesn't static spy or mock "
                            + clazz
                            + ", only: "
                            + mStaticClassChecker.getSpiedOrMockedClasses());
        }
    }
}