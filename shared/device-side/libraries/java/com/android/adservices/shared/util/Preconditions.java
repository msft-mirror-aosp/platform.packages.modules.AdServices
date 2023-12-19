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

package com.android.adservices.shared.util;

import android.annotation.IntRange;
import android.annotation.NonNull;
import android.text.TextUtils;

import com.google.errorprone.annotations.FormatMethod;

import java.util.Collection;

/**
 * Simple static methods to be called at the start of your own methods to verify correct arguments
 * and state.
 *
 * <p>Note: This class is copied from modules-utils-preconditions library because some methods are
 * not available in R or below.
 */
public final class Preconditions {

    /**
     * Ensures that an expression checking an argument is true.
     *
     * @param expression the expression to check
     * @throws IllegalArgumentException if {@code expression} is false
     */
    public static void checkArgument(boolean expression) {
        if (!expression) {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Ensures that an expression checking an argument is true.
     *
     * @param expression the expression to check
     * @param errorMessage the exception message to use if the check fails; will be converted to a
     *     string using {@link String#valueOf(Object)}
     * @throws IllegalArgumentException if {@code expression} is false
     */
    public static void checkArgument(boolean expression, Object errorMessage) {
        if (!expression) {
            throw new IllegalArgumentException(String.valueOf(errorMessage));
        }
    }

    /**
     * Ensures that an expression checking an argument is true.
     *
     * @param expression the expression to check
     * @param messageTemplate a printf-style message template to use if the check fails; will be
     *     converted to a string using {@link String#format(String, Object...)}
     * @param messageArgs arguments for {@code messageTemplate}
     * @throws IllegalArgumentException if {@code expression} is false
     */
    @FormatMethod
    public static void checkArgument(
            boolean expression, @NonNull String messageTemplate, Object... messageArgs) {
        if (!expression) {
            throw new IllegalArgumentException(String.format(messageTemplate, messageArgs));
        }
    }

    /**
     * Ensures the truth of an expression involving the state of the calling instance, but not
     * involving any parameters to the calling method.
     *
     * @param expression a boolean expression
     * @throws IllegalStateException if {@code expression} is false
     */
    public static void checkState(boolean expression) {
        checkState(expression, null);
    }

    /**
     * Ensures the truth of an expression involving the state of the calling instance, but not
     * involving any parameters to the calling method.
     *
     * @param expression a boolean expression
     * @param errorMessage the exception message to use if the check fails; will be converted to a
     *     string using {@link String#valueOf(Object)}
     * @throws IllegalStateException if {@code expression} is false
     */
    public static void checkState(boolean expression, String errorMessage) {
        if (!expression) {
            throw new IllegalStateException(errorMessage);
        }
    }

    /**
     * Ensures the truth of an expression involving the state of the calling instance, but not
     * involving any parameters to the calling method.
     *
     * @param expression a boolean expression
     * @param messageTemplate a printf-style message template to use if the check fails; will be
     *     converted to a string using {@link String#format(String, Object...)}
     * @param messageArgs arguments for {@code messageTemplate}
     * @throws IllegalStateException if {@code expression} is false
     */
    @FormatMethod
    public static void checkState(
            boolean expression, @NonNull String messageTemplate, Object... messageArgs) {
        if (!expression) {
            throw new IllegalStateException(String.format(messageTemplate, messageArgs));
        }
    }

    /**
     * Ensures that an string reference passed as a parameter to the calling method is not empty.
     *
     * @param string an string reference
     * @return the string reference that was validated
     * @throws IllegalArgumentException if {@code string} is empty
     */
    public static @NonNull <T extends CharSequence> T checkStringNotEmpty(T string) {
        if (TextUtils.isEmpty(string)) {
            throw new IllegalArgumentException();
        }
        return string;
    }

    /**
     * Ensures that an string reference passed as a parameter to the calling method is not empty.
     *
     * @param string an string reference
     * @param errorMessage the exception message to use if the check fails; will be converted to a
     *     string using {@link String#valueOf(Object)}
     * @return the string reference that was validated
     * @throws IllegalArgumentException if {@code string} is empty
     */
    public static @NonNull <T extends CharSequence> T checkStringNotEmpty(
            T string, Object errorMessage) {
        if (TextUtils.isEmpty(string)) {
            throw new IllegalArgumentException(String.valueOf(errorMessage));
        }
        return string;
    }

    /**
     * Ensures that an string reference passed as a parameter to the calling method is not empty.
     *
     * @param string an string reference
     * @param messageTemplate a printf-style message template to use if the check fails; will be
     *     converted to a string using {@link String#format(String, Object...)}
     * @param messageArgs arguments for {@code messageTemplate}
     * @return the string reference that was validated
     * @throws IllegalArgumentException if {@code string} is empty
     */
    @FormatMethod
    public static @NonNull <T extends CharSequence> T checkStringNotEmpty(
            T string, @NonNull String messageTemplate, Object... messageArgs) {
        if (TextUtils.isEmpty(string)) {
            throw new IllegalArgumentException(String.format(messageTemplate, messageArgs));
        }
        return string;
    }

    /**
     * Ensures that that the argument numeric value is non-negative (greater than or equal to 0).
     *
     * @param value a numeric int value
     * @param errorMessage the exception message to use if the check fails
     * @return the validated numeric value
     * @throws IllegalArgumentException if {@code value} was negative
     */
    public static @IntRange(from = 0) int checkArgumentNonnegative(int value, String errorMessage) {
        if (value < 0) {
            throw new IllegalArgumentException(errorMessage);
        }

        return value;
    }

    /**
     * Ensures that that the argument numeric value is non-negative (greater than or equal to 0).
     *
     * @param value a numeric int value
     * @return the validated numeric value
     * @throws IllegalArgumentException if {@code value} was negative
     */
    public static @IntRange(from = 0) int checkArgumentNonnegative(int value) {
        if (value < 0) {
            throw new IllegalArgumentException();
        }

        return value;
    }

    /**
     * Ensures that that the argument numeric value is non-negative (greater than or equal to 0).
     *
     * @param value a numeric long value
     * @return the validated numeric value
     * @throws IllegalArgumentException if {@code value} was negative
     */
    public static long checkArgumentNonnegative(long value) {
        if (value < 0) {
            throw new IllegalArgumentException();
        }

        return value;
    }

    /**
     * Ensures that that the argument numeric value is non-negative (greater than or equal to 0).
     *
     * @param value a numeric long value
     * @param errorMessage the exception message to use if the check fails
     * @return the validated numeric value
     * @throws IllegalArgumentException if {@code value} was negative
     */
    public static long checkArgumentNonnegative(long value, String errorMessage) {
        if (value < 0) {
            throw new IllegalArgumentException(errorMessage);
        }

        return value;
    }

    /**
     * Ensures that that the argument numeric value is positive (greater than 0).
     *
     * @param value a numeric int value
     * @param errorMessage the exception message to use if the check fails
     * @return the validated numeric value
     * @throws IllegalArgumentException if {@code value} was not positive
     */
    public static int checkArgumentPositive(int value, String errorMessage) {
        if (value <= 0) {
            throw new IllegalArgumentException(errorMessage);
        }

        return value;
    }

    /**
     * Ensures that the argument floating point value is non-negative (greater than or equal to 0).
     *
     * @param value a floating point value
     * @param errorMessage the exteption message to use if the check fails
     * @return the validated numeric value
     * @throws IllegalArgumentException if {@code value} was negative
     */
    public static float checkArgumentNonNegative(float value, String errorMessage) {
        if (value < 0) {
            throw new IllegalArgumentException(errorMessage);
        }

        return value;
    }

    /**
     * Ensures that the argument floating point value is positive (greater than 0).
     *
     * @param value a floating point value
     * @param errorMessage the exteption message to use if the check fails
     * @return the validated numeric value
     * @throws IllegalArgumentException if {@code value} was not positive
     */
    public static float checkArgumentPositive(float value, String errorMessage) {
        if (value <= 0) {
            throw new IllegalArgumentException(errorMessage);
        }

        return value;
    }

    /**
     * Ensures that the {@link Collection} is not {@code null}, and contains at least one element.
     *
     * @param value a {@link Collection} of boxed elements.
     * @param valueName the name of the argument to use if the check fails.
     * @return the validated {@link Collection}
     * @throws NullPointerException if the {@code value} was {@code null}
     * @throws IllegalArgumentException if the {@code value} was empty
     */
    public static <T> Collection<T> checkCollectionNotEmpty(Collection<T> value, String valueName) {
        if (value == null) {
            throw new NullPointerException(valueName + " must not be null");
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException(valueName + " is empty");
        }
        return value;
    }
}
