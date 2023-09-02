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
package com.android.adservices.common;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

/** Helper to check if AdServices is supported / enabled in a device. */
public final class AdServicesHostSideSupportHelper {

    private static final String FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";
    private static final String FEATURE_LEANBACK = "android.software.leanback";
    private static final String FEATURE_WATCH = "android.hardware.type.watch";

    // Copied from RoSystemProperties
    private static final String SYSTEM_PROPERTY_CONFIG_LOW_RAM = "ro.config.low_ram";

    // TODO(b/295321663): 3 constants below should be static imported from AdServicesCommonConstants
    private static final String SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX = "debug.adservices.";
    private static final String SYSTEM_PROPERTY_FOR_DEBUGGING_SUPPORTED_ON_DEVICE =
            SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX + "supported";
    private static final String SYSTEM_PROPERTY_FOR_DEBUGGING_FEATURE_RAM_LOW =
            SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX + "low_ram_device";

    private static final Logger sLogger =
            new Logger(ConsoleLogger.getInstance(), AdServicesHostSideSupportHelper.class);

    public static boolean isDebuggable(ITestDevice device) throws DeviceNotAvailableException {
        return "1".equals(device.getProperty("ro.debuggable"));
    }

    public static boolean isDeviceSupported(ITestDevice device) throws DeviceNotAvailableException {
        if (isDebuggable(device)) {
            String overriddenValue =
                    device.getProperty(SYSTEM_PROPERTY_FOR_DEBUGGING_SUPPORTED_ON_DEVICE);
            if (overriddenValue != null && !overriddenValue.isEmpty()) {
                boolean supported = Boolean.valueOf(overriddenValue);
                sLogger.i(
                        "isDeviceSupported(): returning %b as defined by system property %s (%s)",
                        supported,
                        SYSTEM_PROPERTY_FOR_DEBUGGING_SUPPORTED_ON_DEVICE,
                        overriddenValue);
                return supported;
            }
        }

        boolean supported = isDeviceSupportedByDefault(device);
        sLogger.v("isDeviceSupported(): returning hardcoded value (%b)", supported);
        return supported;
    }

    //  TODO(b/284971005): create another object to have this logic (which would take an interface
    // that gets system properties) so it can be used by device and host sides
    private static boolean isDeviceSupportedByDefault(ITestDevice device)
            throws DeviceNotAvailableException {
        return isPhone(device) && !isLowRamDevice(device);
    }

    private static boolean isPhone(ITestDevice device) throws DeviceNotAvailableException {
        boolean isIt =
                !device.hasFeature(FEATURE_WATCH)
                        && !device.hasFeature(FEATURE_AUTOMOTIVE)
                        && !device.hasFeature(FEATURE_LEANBACK);
        // TODO(b/284744130): need to figure out how to filter out tablets
        sLogger.v("isPhone(): returning %b", isIt);
        return isIt;
    }

    // TODO(b/297408848): rename to isAdservicesLiteDevice() or something like that
    /** Checks whether the device has low ram. */
    public static boolean isLowRamDevice(ITestDevice device) throws DeviceNotAvailableException {
        if (isDebuggable(device)) {
            String overriddenValue =
                    device.getProperty(SYSTEM_PROPERTY_FOR_DEBUGGING_FEATURE_RAM_LOW);
            if (overriddenValue != null && !overriddenValue.isEmpty()) {
                boolean isLowRamDevice = Boolean.valueOf(overriddenValue);
                sLogger.i(
                        "isLowRamDevice(): returning %b as defined by system property %s (%s)",
                        isLowRamDevice,
                        SYSTEM_PROPERTY_FOR_DEBUGGING_FEATURE_RAM_LOW,
                        overriddenValue);
                return isLowRamDevice;
            }
        }

        boolean isLowRamDevice = "true".equals(device.getProperty(SYSTEM_PROPERTY_CONFIG_LOW_RAM));
        boolean isPhone = isPhone(device);
        boolean isIt = isPhone && isLowRamDevice;
        sLogger.v(
                "isLowRamDevice(): returning non-simulated value %b when isPhone=%b and"
                        + " isLowRamDevice=%b",
                isIt, isPhone, isLowRamDevice);
        return isIt;
    }

    private AdServicesHostSideSupportHelper() {
        throw new UnsupportedOperationException("Provides only static methods");
    }
}
