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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;

import android.os.SystemProperties;
import android.provider.DeviceConfig;
import android.util.Log;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

final class DeviceConfigAndSystemPropertiesExpectations {

    private static final String TAG =
            DeviceConfigAndSystemPropertiesExpectations.class.getSimpleName();

    /**
     * Mocks a call to {@code DeviceConfig.getBoolean()} using the AdServices namespace and
     * returning {@code value}.
     */
    public static void mockGetAdServicesFlag(String name, boolean value) {
        logV("mockGetAdServicesFlag(name=%s, value=%s)", name, value);
        doReturn(value)
                .when(
                        () ->
                                DeviceConfig.getBoolean(
                                        eq(DeviceConfig.NAMESPACE_ADSERVICES),
                                        eq(name),
                                        /* defaultValue= */ anyBoolean()));
    }

    /**
     * Mocks a call to {@code DeviceConfig.getString()} using the AdServices namespace and returning
     * {@code value}.
     */
    public static void mockGetAdServicesFlag(String name, String value) {
        logV("mockGetAdServicesFlag(name=%s, value=%s)", name, value);
        doReturn(value)
                .when(
                        () ->
                                DeviceConfig.getString(
                                        eq(DeviceConfig.NAMESPACE_ADSERVICES),
                                        eq(name),
                                        /* defaultValue= */ any()));
    }

    /**
     * Mocks a call to {@code DeviceConfig.getInt()} using the AdServices namespace and returning
     * {@code value}.
     */
    public static void mockGetAdServicesFlag(String name, int value) {
        logV("mockGetAdServicesFlag(name=%s, value=%s)", name, value);
        doReturn(value)
                .when(
                        () ->
                                DeviceConfig.getInt(
                                        eq(DeviceConfig.NAMESPACE_ADSERVICES),
                                        eq(name),
                                        /* defaultValue= */ anyInt()));
    }

    /**
     * Mocks a call to {@code DeviceConfig.getLong()} using the AdServices namespace and returning
     * {@code value}.
     */
    public static void mockGetAdServicesFlag(String name, long value) {
        logV("mockGetAdServicesFlag(name=%s, value=%s)", name, value);
        doReturn(value)
                .when(
                        () ->
                                DeviceConfig.getLong(
                                        eq(DeviceConfig.NAMESPACE_ADSERVICES),
                                        eq(name),
                                        /* defaultValue= */ anyLong()));
    }

    /**
     * Mocks a call to {@code DeviceConfig.getFloat()} using the AdServices namespace and returning
     * {@code value}.
     */
    public static void mockGetAdServicesFlag(String name, float value) {
        logV("mockGetAdServicesFlag(name=%s, value=%s)", name, value);
        doReturn(value)
                .when(
                        () ->
                                DeviceConfig.getFloat(
                                        eq(DeviceConfig.NAMESPACE_ADSERVICES),
                                        eq(name),
                                        /* defaultValue= */ anyFloat()));
    }

    /**
     * Verifies no call to {@link SystemProperties#getLong(String, boolean)} with the given {@code
     * key} was made.
     */
    public static void verifyGetBooleanSystemPropertyNotCalled(String key) {
        logV("verifyGetBooleanSystemPropertyNotCalled(key=%s)", key);
        verify(() -> SystemProperties.getBoolean(eq(key), anyBoolean()), never());
    }

    /**
     * Verifies no call to {@link DeviceConfig#getBoolean(String, String, boolean)} with the given
     * {@code namespace} and {@code name} was made.
     */
    public static void verifyGetBooleanDeviceConfigFlagNotCalled(String namespace, String name) {
        logV("verifyGetBooleanDeviceConfigFlagNotCalled(namespace=%s, name=%s)", namespace, name);
        verify(() -> DeviceConfig.getBoolean(eq(namespace), eq(name), anyBoolean()), never());
    }

    @FormatMethod
    private static void logV(@FormatString String fmt, Object... args) {
        Log.v(TAG, String.format(fmt, args));
    }

    private DeviceConfigAndSystemPropertiesExpectations() {
        throw new UnsupportedOperationException("Provides only static methods");
    }
}
