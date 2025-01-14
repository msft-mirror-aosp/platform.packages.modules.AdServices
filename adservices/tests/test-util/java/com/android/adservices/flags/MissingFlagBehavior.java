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
package com.android.adservices.flags;

/**
 * Defines the behavior of flag getters mode (like {@code getFlag(name, defaultValue)}) when the
 * test did not explicitly set the value of the flag.
 *
 * <p>This method is useful when converting tests to use {@code AdServicesFakeFlagsSetterRule}
 * instead of {@code AdServicesMockFlagsSetterRule}, although it's better to explicitly set the all
 * the flags (you can check which flags are returning the default value by looking at {@code
 * logcat}, it would have entries like {@code W FakeFlags:
 * getFlag(protected_signals_periodic_encoding_job_flex_ms, 300000): returning 300000 for missing
 * flag}.
 */
public enum MissingFlagBehavior {
    /**
     * Returns the value explicitly set in the getter method.
     *
     * <p>For example, for a {@code getFlags("myApi", true)} method, it would return {@code true} if
     * the value of {@code "myApiEnabled"} was not explicitly set.
     */
    USES_EXPLICIT_DEFAULT,
    /**
     * Returns the Java language default for the flag type.
     *
     * <p>For example, for a {@code getFlags("myApi", true)} method, it would return {@code false}
     * if the value of {@code "myApiEnabled"} was not explicitly set.
     */
    USES_JAVA_LANGUAGE_DEFAULT,

    /** Throws an {@link IllegalStateException} when the value of the flag is not explicitly set. */
    THROWS_EXCEPTION
}
