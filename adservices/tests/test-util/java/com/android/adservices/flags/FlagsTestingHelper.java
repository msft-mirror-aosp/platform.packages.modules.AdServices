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

import com.android.adservices.service.Flags;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import javax.annotation.Nullable;

/** Helper for flags-related stuff. */
public final class FlagsTestingHelper {

    /**
     * Returns the flags as a map of {@code getter()} -> {@code value}.
     *
     * <p>Useful to debug tests that fail when being refactored to use different flags (for example,
     * by calling {@code expect.withMessage("new
     * flags").that(asMap(newFlags)).containsExactlyEntriesIn(asMap(oldFlags))}
     */
    public static Map<String, String> asMap(Flags flags) {
        return asMapWithOptionalPrefix(flags, /* prefix= */ null);
    }

    /**
     * Returns the flags as a map of {@code getter()} -> {@code value}, but only containing getters
     * that match the given {@code prefix}.
     *
     * <p>Useful to debug tests that fail when being refactored to use different flags (for example,
     * by calling {@code expect.withMessage("new flags").that(asMap(newFlags,
     * "getFledge")).containsExactlyEntriesIn(asMap(oldFlags, "getFledge"))}
     */
    public static Map<String, String> asMap(Flags flags, String prefix) {
        Objects.requireNonNull(prefix, "prefix cannot be null");
        return asMapWithOptionalPrefix(flags, prefix);
    }

    private static Map<String, String> asMapWithOptionalPrefix(
            Flags flags, @Nullable String prefix) {
        Objects.requireNonNull(flags, "flags cannot be null");

        // First filter out non-getters...
        HashMap<String, Method> getters = new HashMap<>();
        for (var method : Flags.class.getMethods()) {
            // We could check if starts with is... or get... , but in reality only dump(...) is not
            // a flag, and some take args (like isEnrollmentBlocklisted(enrollmentId))
            if (method.getParameterCount() == 0) {
                String methodName = method.getName();
                if (prefix == null || methodName.startsWith(prefix)) {
                    getters.put(methodName, method);
                }
            }
        }

        // Then get the values
        TreeMap<String, String> map = new TreeMap<>();
        for (var method : getters.values()) {
            String methodName = method.getName() + "()";
            Object value;
            try {
                value = method.invoke(flags);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                // shouldn't happen
                throw new IllegalStateException("invocation error", cause);
            } catch (Exception e) {
                // shouldn't happen
                throw new IllegalStateException("reflection error", e);
            }
            map.put(methodName, (value == null) ? "null" : value.toString());
        }

        return map;
    }

    private FlagsTestingHelper() {
        throw new UnsupportedOperationException("contains only static methods");
    }
}
