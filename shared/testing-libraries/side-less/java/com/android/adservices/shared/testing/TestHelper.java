/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.adservices.shared.testing;

import org.junit.runner.Description;

import java.lang.annotation.Annotation;
import java.util.Objects;

/** Provides helpers for generic test-related tasks. */
public final class TestHelper {
    private static final boolean VERBOSE = false; // Should NEVER be merged as true

    private static final Logger sLogger = new Logger(DynamicLogger.getInstance(), TestHelper.class);

    // TODO(b/315339283): use in other places
    /** Gets the given annotation from the test, its class, or its ancestors. */
    @Nullable
    public static <T extends Annotation> T getAnnotation(
            Description test, Class<T> annotationClass) {
        Objects.requireNonNull(test, "test (Description) cannot be null");
        Objects.requireNonNull(annotationClass, "annotationClass cannot be null");

        T annotation = test.getAnnotation(annotationClass);
        if (annotation != null) {
            if (VERBOSE) {
                sLogger.v(
                        "getAnnotation(%s, %s): returning annotation (%s) from test itself (%s)",
                        test, annotationClass, annotation, getTestName(test));
            }
            return annotation;
        }
        Class<?> testClass = test.getTestClass();
        annotation = getAnnotationInternal(testClass, annotationClass);
        if (VERBOSE) {
            sLogger.v("getAnnotation(%s, %s): returning %s", test, annotationClass, annotation);
        }
        return annotation;
    }

    /**
     * Gets the given annotation from the test class, its ancestors, or any of the implemented
     * interface(s).
     */
    // TODO(b/315339283): use in other places
    @Nullable
    public static <T extends Annotation> T getAnnotation(
            Class<?> testClass, Class<T> annotationClass) {
        Objects.requireNonNull(testClass, "test class cannot be null");
        Objects.requireNonNull(annotationClass, "annotationClass cannot be null");
        T annotation = getAnnotationInternal(testClass, annotationClass);
        if (VERBOSE) {
            sLogger.v(
                    "getAnnotation(%s, %s): returning %s", testClass, annotationClass, annotation);
        }
        return annotation;
    }

    @Nullable
    private static <T extends Annotation> T getAnnotationInternal(
            Class<?> testClass, Class<T> annotationClass) {
        T annotation = null;
        while (testClass != null) {
            annotation = testClass.getAnnotation(annotationClass);
            if (annotation != null) {
                if (VERBOSE) {
                    sLogger.v(
                            "getAnnotationInternal(%s): returning annotation (%s) from (%s)",
                            annotationClass.getSimpleName(), annotation, testClass);
                }
                return annotation;
            }

            for (Class<?> classInterface : testClass.getInterfaces()) {
                annotation = classInterface.getAnnotation(annotationClass);
                if (annotation != null) {
                    if (VERBOSE) {
                        sLogger.v(
                                "getAnnotationInternal(%s): returning annotation (%s) from "
                                        + "interface (%s)",
                                annotationClass.getSimpleName(), annotation, classInterface);
                    }
                    return annotation;
                }
            }

            if (VERBOSE) {
                sLogger.v(
                        "getAnnotationInternal(%s): not found on class %s or any implemented "
                                + "interfaces, will try superclass (%s)",
                        annotationClass.getSimpleName(), testClass, testClass.getSuperclass());
            }
            testClass = testClass.getSuperclass();
        }
        return null;
    }

    // TODO(b/315339283): use in other places
    /** Gets a user-friendly name for the test. */
    public static String getTestName(Description test) {
        StringBuilder testName = new StringBuilder(test.getTestClass().getSimpleName());
        String methodName = test.getMethodName();
        if (methodName != null) {
            testName.append('#').append(methodName).append("()");
        }
        return testName.toString();
    }

    /**
     * Helper method to throw exception if description is not a test node.
     *
     * <p>Use this to throw exception for rules that can only be used on individual tests, not
     * as @ClassRule or in a suite.
     */
    public static void throwIfNotTest(Description description) {
        if (!description.isTest()) {
            throw new IllegalStateException(
                    "This rule can only be applied to individual tests, it cannot be used as"
                            + " @ClassRule or in a test suite");
        }
    }

    /**
     * Helper method to throw exception if description is a test node.
     *
     * <p>Use this to throw exception for rules that CANNOT be used on individual tests, only
     * as @ClassRule or in a suite.
     */
    public static void throwIfTest(Description description) {
        if (description.isTest() && !description.isSuite()) {
            throw new IllegalStateException(
                    "This rule can only be used as a @ClassRule or in a test suit, it cannot be"
                            + " applied to individual tests as a @Rule");
        }
    }

    private TestHelper() {
        throw new UnsupportedOperationException("Provides only static methods");
    }
}
