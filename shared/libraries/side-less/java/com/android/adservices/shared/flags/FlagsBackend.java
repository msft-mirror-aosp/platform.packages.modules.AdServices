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
package com.android.adservices.shared.flags;

import java.util.Objects;

/**
 * Abstraction for how {@code Flags} are stored and retrieved.
 *
 * <p>By default all methods uses {@link #getFlag(String)}, but implementations can override them to
 * call backend-specific methods (like those provided by {@code DeviceConfig}.
 */
public interface FlagsBackend {

    /** Gets the canonical value of a flag, or {@code null} when not present. */
    String getFlag(String name);

    /** Gets the value of a {@code boolean} flag, or {@code defaultValue} when not present. */
    default boolean getFlag(String name, boolean defaultValue) {
        String value = getFlagChecked(name);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    /** Gets the value of a {@code String} flag, or {@code defaultValue} when not present. */
    default String getFlag(String name, String defaultValue) {
        String value = getFlagChecked(name);
        return value == null ? defaultValue : value;
    }

    /** Gets the value of a {@code int} flag, or {@code defaultValue} when not present. */
    default int getFlag(String name, int defaultValue) {
        String value = getFlagChecked(name);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    /** Gets the value of a {@code long} flag, or {@code defaultValue} when not present. */
    default long getFlag(String name, long defaultValue) {
        String value = getFlagChecked(name);
        return value == null ? defaultValue : Long.parseLong(value);
    }

    /** Gets the value of a {@code float} flag, or {@code defaultValue} when not present. */
    default float getFlag(String name, float defaultValue) {
        String value = getFlagChecked(name);
        return value == null ? defaultValue : Float.parseFloat(value);
    }

    private String getFlagChecked(String name) {
        Objects.requireNonNull(name, "name cannot be null");
        return getFlag(name);
    }
}
