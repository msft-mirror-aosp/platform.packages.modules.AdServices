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
package com.android.adservices.shared.testing.junit;

import org.junit.FixMethodOrder;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Copied from {@code org.junit.internal.MethodSorter}, but renamed{@code getDeclaredMethods()} to
 * {@code getSortedMethods()}, which takes an array of methods (as {@code
 * Class.getDeclaredMethods()} can throw {@link NoSuchMethodException}).
 */
public final class MethodSorter {
    /** DEFAULT sort order */
    public static final Comparator<Method> DEFAULT =
            new Comparator<Method>() {
                public int compare(Method m1, Method m2) {
                    int i1 = m1.getName().hashCode();
                    int i2 = m2.getName().hashCode();
                    if (i1 != i2) {
                        return i1 < i2 ? -1 : 1;
                    }
                    return NAME_ASCENDING.compare(m1, m2);
                }
            };

    /**
     * Method name ascending lexicographic sort order, with {@link Method#toString()} as a
     * tiebreaker
     */
    public static final Comparator<Method> NAME_ASCENDING =
            new Comparator<Method>() {
                public int compare(Method m1, Method m2) {
                    final int comparison = m1.getName().compareTo(m2.getName());
                    if (comparison != 0) {
                        return comparison;
                    }
                    return m1.toString().compareTo(m2.toString());
                }
            };

    /**
     * Gets declared methods of a class in a predictable order,
     * unless @FixMethodOrder(MethodSorters.JVM) is specified.
     *
     * <p>Using the JVM order is unwise since the Java platform does not specify any particular
     * order, and in fact JDK 7 returns a more or less random order; well-written test code would
     * not assume any order, but some does, and a predictable failure is better than a random
     * failure on certain platforms. By default, uses an unspecified but deterministic order.
     *
     * @param clazz a class
     * @return same as {@link Class#getDeclaredMethods} but sorted
     * @see <a href="http://bugs.sun.com/view_bug.do?bug_id=7023180">JDK (non-)bug #7023180</a>
     */
    public static Method[] getSortedMethods(Class<?> clazz, Method[] methods) {
        Comparator<Method> comparator = getSorter(clazz.getAnnotation(FixMethodOrder.class));

        if (comparator != null) {
            Arrays.sort(methods, comparator);
        }

        return methods;
    }

    private MethodSorter() {}

    private static Comparator<Method> getSorter(FixMethodOrder fixMethodOrder) {
        if (fixMethodOrder == null) {
            return DEFAULT;
        }

        return fixMethodOrder.value().getComparator();
    }
}
