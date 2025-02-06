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
package com.android.adservices.service;

import com.android.adservices.flags.FakeFlags;

/** Provides a {@link Flags} singleton that overrides some common values that are used in tests. */
public final class FakeFlagsFactory {

    // Use the Flags that has constant values.
    private static final Flags sSingleton =
            FakeFlags.createFakeFlagsForFakeFlagsFactoryPurposesOnly();

    /** Gets the singleton. */
    public static Flags getFlagsForTest() {
        return sSingleton;
    }

    private FakeFlagsFactory() {
        throw new UnsupportedOperationException("provides only static methods");
    }
}
