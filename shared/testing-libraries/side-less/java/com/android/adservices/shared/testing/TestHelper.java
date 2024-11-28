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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Provides helpers for generic test-related tasks. */
public final class TestHelper {
    private static final boolean VERBOSE = false; // Should NEVER be merged as true

    private static final Logger sLogger = new Logger(DynamicLogger.getInstance(), TestHelper.class);

    /**
     * Gets the given annotation from the test, its class, its ancestors, or interfaces each of
     * these classes implements.
     *
     * @param test test itself
     * @param annotationClass type of annotation to look for
     * @return the annotation defined "closest" to the test (closest would be the one declared in
     *     the test method), or {@code null} if the annotation is not present anywhere.
     */
    @Nullable
    public static <T extends Annotation> T getAnnotationFromAnywhere(
            Description test, Class<T> annotationClass) {
        Objects.requireNonNull(test, "test (Description) cannot be null");
        Objects.requireNonNull(annotationClass, "annotationClass cannot be null");

        Function<Annotation, Boolean> filter = a -> annotationClass.isInstance(a);

        // Try from test method first...
        List<Annotation> annotations = new ArrayList<>();
        addAnnotationsFromTestMethodOnly(annotations, test, filter);
        if (!annotations.isEmpty()) {
            return annotationClass.cast(annotations.get(0));
        }
        // ...then from class hierarchy
        addAnnotationsFromTestTypesOnly(annotations, test.getTestClass(), filter);
        return annotations.isEmpty() ? null : annotationClass.cast(annotations.get(0));
    }

    /**
     * Gets the given annotation from the test class, its ancestors, or any of the implemented
     * interface(s).
     *
     * <p>This method is similar to {@link #getAnnotationFromAnywhere(Description, Class)}, but it
     * doesn't look for the annotation in the test method itself, so it's more suitable to be used
     * by class rules.
     *
     * @param testClass class of the test
     * @param annotationClass type of annotation to look for
     * @return the annotation defined "closest" to the test (closest would be the one declared in
     *     the test class), or {@code null} if the annotation is not present anywhere.
     */
    @Nullable
    public static <T extends Annotation> T getAnnotationFromTypesOnly(
            Class<?> testClass, Class<T> annotationClass) {
        Objects.requireNonNull(testClass, "test class cannot be null");
        Objects.requireNonNull(annotationClass, "annotationClass cannot be null");

        Function<Annotation, Boolean> filter = a -> annotationClass.isInstance(a);
        List<Annotation> annotations = new ArrayList<>();
        addAnnotationsFromTestTypesOnly(annotations, testClass, filter);
        T annotation = annotations.isEmpty() ? null : annotationClass.cast(annotations.get(0));

        if (VERBOSE) {
            sLogger.v(
                    "getAnnotation(%s, %s): returning %s", testClass, annotationClass, annotation);
        }
        return annotation;
    }

    /**
     * Gets the given annotations from the test, its class, its ancestors, or interfaces each of
     * these classes implements.
     *
     * @param test test itself
     * @param filter object used to defined which annotations to look for (should return {@code
     *     true} to include an annotation in the result).
     * @return list of annotations, with the "closest" annotations coming first (closest would be
     *     the one declared in the test method), or empty if the annotation is not present anywhere.
     */
    public static List<Annotation> getAnnotationsFromEverywhere(
            Description test, Function<Annotation, Boolean> filter) {
        Objects.requireNonNull(test, "test (Description) cannot be null");
        Objects.requireNonNull(filter, "filter cannot be null");

        List<Annotation> annotations = new ArrayList<>();
        addAnnotationsFromTestMethodOnly(annotations, test, filter);
        addAnnotationsFromTestTypesOnly(annotations, test.getTestClass(), filter);

        if (VERBOSE) {
            sLogger.v("getAnnotations(%s): returning %s", test, annotations);
        }
        return annotations;
    }

    private static void addAnnotationsFromTestMethodOnly(
            List<Annotation> annotations, Description test, Function<Annotation, Boolean> filter) {
        for (var annotation : test.getAnnotations()) {
            if (filter.apply(annotation)) {
                if (VERBOSE) {
                    sLogger.v(
                            "getAnnotations(%s): adding annotation (%s) from test itself (%s)",
                            test, annotation, getTestName(test));
                }
                annotations.add(annotation);
            }
        }
    }

    private static void addAnnotationsFromTestTypesOnly(
            List<Annotation> annotations,
            Class<?> testClass,
            Function<Annotation, Boolean> filter) {
        while (testClass != null) {
            for (var annotation : testClass.getAnnotations()) {
                if (filter.apply(annotation)) {
                    if (VERBOSE) {
                        sLogger.v(
                                "getAnnotations(): adding annotation (%s) from class (%s)",
                                annotation, testClass.getSimpleName());
                    }
                    annotations.add(annotation);
                }
            }
            for (Class<?> classInterface : testClass.getInterfaces()) {
                for (var annotation : classInterface.getAnnotations()) {
                    if (filter.apply(annotation)) {
                        if (VERBOSE) {
                            sLogger.v(
                                    "getAnnotations(): adding annotation (%s) from interface (%s)",
                                    annotation, classInterface.getSimpleName());
                        }
                        annotations.add(annotation);
                    }
                }
            }
            testClass = testClass.getSuperclass();
        }
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
