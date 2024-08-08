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
package com.android.adservices.shared.testing.mockito;

import com.android.adservices.shared.testing.DynamicLogger;
import com.android.adservices.shared.testing.Logger.LogLevel;

import org.mockito.internal.util.MockUtil;
import org.mockito.invocation.InvocationOnMock;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

// TODO(b/306522832): move to side-less side
/** Helper for {@code Mockito}-related functionalities. */
public final class MockitoHelper {

    private static final String TAG = MockitoHelper.class.getSimpleName();

    /**
     * Gets a user-friendly, one-line representation of the invocation.
     *
     * <p>Useful when logging the invocation while setting an {@code Answer}, as {@code
     * InvocationOnMock.toString()} returns multiple lines.
     */
    public static String toString(InvocationOnMock invocation) {
        Objects.requireNonNull(invocation, "invocation cannot be null");
        try {
            // Note: must use String::valueOf (instead of Object::toString) so it handles null
            return invocation.getMethod().getName()
                    + '('
                    + String.join(
                            ", ",
                            Arrays.stream(invocation.getArguments())
                                    .map(String::valueOf)
                                    .collect(Collectors.toList()))
                    + ')';
        } catch (Exception e) {
            // Don't need to keep a reference to a logger, as it should only happen on
            // MockitoHelperTest
            DynamicLogger.getInstance()
                    .log(
                            LogLevel.WARNING,
                            TAG,
                            e,
                            "toString(%s) failed (usually happens when it's a @Mock)",
                            invocation);
            return invocation.toString();
        }
    }

    /** Checks if the given object is a Mockito mock. */
    public static boolean isMock(Object object) {
        return MockUtil.isMock(object);
    }

    /** Checks if the given object is a Mockito spy. */
    public static boolean isSpy(Object object) {
        return MockUtil.isSpy(object);
    }

    /**
     * Gets the object being spied by {@code spy}.
     *
     * @throws IllegalArgumentException if {@code spy} is not a Mockito spy.
     */
    public static Object getSpiedInstance(Object object) {
        if (!isSpy(object)) {
            throw new IllegalArgumentException("not a spy: " + object);
        }
        return MockUtil.getMockSettings(object).getSpiedInstance();
    }

    private MockitoHelper() {
        throw new UnsupportedOperationException("provides only static methods");
    }
}
