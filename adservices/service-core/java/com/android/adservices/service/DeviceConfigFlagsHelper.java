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

import static com.android.adservices.service.CommonFlagsConstants.NAMESPACE_ADSERVICES;

import android.provider.DeviceConfig;

// NOTE: not package-protected because it's used by {@code MddFlags}. It could also be moved to
// shared code (and take the namespace in the constructor), but it's too simple for that.
/**
 * Helper class that abstract calls to DeviceConfig for adservices namespace.
 *
 * @hide
 */
@SuppressWarnings("AvoidDeviceConfigUsage") // Helper / infra class
public final class DeviceConfigFlagsHelper {

    /** Gets the value of a boolean flag. */
    public static boolean getDeviceConfigFlag(String name, boolean defaultValue) {
        return DeviceConfig.getBoolean(NAMESPACE_ADSERVICES, name, defaultValue);
    }

    /** Gets the value of a string flag. */
    public static String getDeviceConfigFlag(String name, String defaultValue) {
        return DeviceConfig.getString(NAMESPACE_ADSERVICES, name, defaultValue);
    }

    /** Gets the value of a int flag. */
    public static int getDeviceConfigFlag(String name, int defaultValue) {
        return DeviceConfig.getInt(NAMESPACE_ADSERVICES, name, defaultValue);
    }

    /** Gets the value of a long flag. */
    public static long getDeviceConfigFlag(String name, long defaultValue) {
        return DeviceConfig.getLong(NAMESPACE_ADSERVICES, name, defaultValue);
    }

    /** Gets the value of a float flag. */
    public static float getDeviceConfigFlag(String name, float defaultValue) {
        return DeviceConfig.getFloat(NAMESPACE_ADSERVICES, name, defaultValue);
    }

    private DeviceConfigFlagsHelper() {
        throw new UnsupportedOperationException("provides only static methods");
    }
}
