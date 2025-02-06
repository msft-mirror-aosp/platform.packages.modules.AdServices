/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.adservices.shared.testing.flags;

import com.android.adservices.shared.flags.FlagsBackend;

import com.google.common.annotations.VisibleForTesting;

/** Extension of {@link FlagsBackend} that provides setters. */
public interface TestableFlagsBackend extends FlagsBackend {

    @VisibleForTesting
    String UNSUPPORTED_TEMPLATE = "getFlag(%s) should not have been called. Reason %s";

    /** Sets the flag to the given value, or removes it if the value is {@code null}. */
    void setFlag(String name, String value);

    /**
     * Configures the flag to throw an {@link UnsupportedOperationException} with the given {@code
     * reason} when the flag with the given {@code name} is called.
     */
    void onGetFlagThrows(String name, String reason);

    /** Sets the flag to the given value. */
    default void setFlag(String name, boolean value) {
        setFlag(name, Boolean.toString(value));
    }

    /** Sets the flag to the given value. */
    default void setFlag(String name, int value) {
        setFlag(name, Integer.toString(value));
    }

    /** Sets the flag to the given value. */
    default void setFlag(String name, long value) {
        setFlag(name, Long.toString(value));
    }

    /** Sets the flag to the given value. */
    default void setFlag(String name, float value) {
        setFlag(name, Float.toString(value));
    }
}
