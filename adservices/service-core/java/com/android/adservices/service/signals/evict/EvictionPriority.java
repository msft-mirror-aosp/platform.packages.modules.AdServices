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

package com.android.adservices.service.signals.evict;

/**
 * Represents the order in which a protected signal will be evicted relative to its ad tech's other
 * signals.
 */
public enum EvictionPriority {
    EVICT_SOONER(-1),
    DEFAULT(0),
    EVICT_LATER(1);

    private final int mValue;

    EvictionPriority(int value) {
        mValue = value;
    }

    public int getValue() {
        return mValue;
    }

    /**
     * Gets the enumerated eviction priority for the given integer value.
     *
     * @param value The integer value.
     * @return The corresponding EvictionPriority enum.
     */
    public static EvictionPriority valueOf(int value) {
        for (EvictionPriority e : EvictionPriority.values()) {
            if (e.getValue() == value) {
                return e;
            }
        }
        throw new IllegalArgumentException("No EvictionPriority exists with value: " + value);
    }
}
