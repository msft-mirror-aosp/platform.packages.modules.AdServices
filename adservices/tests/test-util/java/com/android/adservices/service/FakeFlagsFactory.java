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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Provides {@link Flags} implementations that override some common values that are used in tests.
 */
public final class FakeFlagsFactory {

    // TODO(b/332723427): once this class is gone, make it standalone here or move to the same
    // package as the other similar annotations (if we want it to be supported by the CTS rule)
    /** Used by {@code FlagSetter} rules to provide common values that are used in tests. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    public @interface SetFakeFlagsFactoryFlags {}

    /**
     * @deprecated TODO(b/332723427): each API should use its own fake factory.
     */
    @Deprecated
    public static Flags getFlagsForTest() {
        // Use the Flags that has constant values.
        return FakeFlags.createFakeFlagsForFakeFlagsFactoryPurposesOnly();
    }

    private FakeFlagsFactory() {
        throw new UnsupportedOperationException("provides only static methods");
    }
}
