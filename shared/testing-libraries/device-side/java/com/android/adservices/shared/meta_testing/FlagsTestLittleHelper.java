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

import android.util.Pair;

import com.android.adservices.shared.testing.common.DumpHelper;
import com.android.adservices.shared.testing.common.DumpHelper.Dumper;

import com.google.common.truth.StandardSubjectBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Helper for tests dealing with flags infra. */
public final class FlagsTestLittleHelper {

    /**
     * Gets all "flag" constants (those starting with {@code KEY_} or {@code FLAG_}.
     *
     * @return list of pairs, whose first value is the name of the constant (like {@code
     *     KEY_MY_API_ENABLED}) and the second value is its value (like {@code my_api_enabled}).
     */
    public static List<Pair<String, String>> getAllFlagNameConstants(Class<?> clazz)
            throws IllegalAccessException {
        List<Pair<String, String>> constants = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers)
                    && Modifier.isFinal(modifiers)
                    && (field.getType().equals(String.class))) {
                String name = field.getName();
                if (name.startsWith("KEY_") || name.startsWith("FLAG_")) {
                    String value = (String) field.get(null);
                    constants.add(new Pair<>(name, value));
                }
            }
        }
        return constants;
    }

    // TODO(b/378913349): we could "merge" the 2 methods below so they user a 3rd, private one, but
    // it's not worth the effort as most likely expectDumpHasAllFlags() will go away

    /** Asserts the content of dump() based on constants used as keys for flags. */
    public static void expectDumpHasAllFlags(
            StandardSubjectBuilder expect,
            Class<?> flagsClass,
            Dumper dumper,
            Function<Pair<String, String>, String> lineMatcher)
            throws Exception {
        String dump = DumpHelper.dump(dumper);

        StringBuilder missingFlags = new StringBuilder();
        int numberMissingFlags = 0;
        for (Pair<String, String> flag : getAllFlagNameConstants(flagsClass)) {
            if (!dump.contains(lineMatcher.apply(flag))) {
                // NOTE: not using expect because the value of dump printed on each failure would be
                // hundreds of lines long
                numberMissingFlags++;
                missingFlags.append('\n').append(flag.first);
            }
        }

        if (numberMissingFlags > 0) {
            expect.withMessage("dump() is missing %s flags: %s", numberMissingFlags, missingFlags)
                    .fail();
        }
    }

    /** Asserts the content of dump() based on getters methods. */
    public static void expectDumpHasAllGetters(
            StandardSubjectBuilder expect, Class<?> gettersClass, Dumper dumper) throws Exception {
        String dump = DumpHelper.dump(dumper);

        Set<String> expectedGetters =
                Arrays.stream(gettersClass.getMethods())
                        .filter(
                                method ->
                                        method.getParameterCount() == 0
                                                && (method.getName().startsWith("is")
                                                        || method.getName().startsWith("get")))
                        .map(method -> method.getName() + "()")
                        .collect(Collectors.toSet());
        expect.withMessage("dump()").that(dump).contains(expectedGetters.size() + " flags:\n");

        String missing =
                expectedGetters.stream()
                        .filter(methodName -> !dump.contains(methodName))
                        .collect(Collectors.joining("\n"));
        if (!missing.isEmpty()) {
            expect.withMessage(
                            "dump() is missing %s getters: %s", missing.split("\n").length, missing)
                    .fail();
        }

        // TODO(b/378913349): ideally it should check that getters are shown on alphabetical order,
        // but it's kind of an an overkill for now (it would be easier to "test" that once this
        // method is moved to DumpHelper, though)
    }

    private FlagsTestLittleHelper() {
        throw new UnsupportedOperationException("provides only static methods");
    }
}
