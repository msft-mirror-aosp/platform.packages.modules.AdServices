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

import static com.android.adservices.mockito.AbstractStaticMocker.TAG;

import android.util.Log;

import com.google.common.collect.ImmutableSet;

/** Helper class use to check if a class is statically spied / mocked. */
public interface StaticClassChecker {

    /** Gets the name of the test being running (for logging purposes). */
    default String getTestName() {
        // TODO(b/285014040): move constant to uber superclass
        return "N/A";
    }

    /**
     * Checks whether the given class is spied or mocked.
     *
     * @return {@code true} by default.
     */
    default boolean isSpiedOrMocked(Class<?> clazz) {
        Log.d(
                TAG,
                "isSpiedOrMocked("
                        + clazz.getSimpleName()
                        + "): always returning true on default StaticClassChecker");
        return true;
    }

    /**
     * Gets the classes that are spied or mocked.
     *
     * @return empty list by default.
     */
    default ImmutableSet<Class<?>> getSpiedOrMockedClasses() {
        Log.d(
                TAG,
                "getSpiedOrMockedClasses(): always returning empty on default"
                        + " StaticClassChecker");
        return ImmutableSet.of();
    }
}
