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

import static com.android.adservices.shared.testing.TestDeviceHelper.call;

import com.android.adservices.shared.testing.ConsoleLogger;
import com.android.adservices.shared.testing.HostSideSystemPropertiesHelper;
import com.android.adservices.shared.testing.Nullable;
import com.android.compatibility.common.util.PackageUtil;

import com.google.common.collect.ImmutableSet;

import java.util.Arrays;
import java.util.List;

/** Helper to check if AdServices is supported / enabled in a device. */
final class AdServicesHostSideSupportHelper extends AbstractDeviceSupportHelper {

    private static final String ADSERVICES_PACKAGE = "com.google.android.adservices.api";
    private static final String ADSERVICES_PACKAGE_AOSP = "com.android.adservices.api";
    private static final String EXT_ADSERVICES_PACKAGE = "com.google.android.ext.adservices.api";
    private static final String EXT_ADSERVICES_PACKAGE_AOSP = "com.android.ext.adservices.api";

    private static final ImmutableSet<String> ADSERVICES_PACKAGE_NAMES =
            ImmutableSet.of(
                    ADSERVICES_PACKAGE,
                    ADSERVICES_PACKAGE_AOSP,
                    EXT_ADSERVICES_PACKAGE,
                    EXT_ADSERVICES_PACKAGE_AOSP);

    private static final AdServicesHostSideSupportHelper sInstance =
            new AdServicesHostSideSupportHelper();

    public static AdServicesHostSideSupportHelper getInstance() {
        return sInstance;
    }

    @Override
    protected boolean hasPackageManagerFeature(String feature) {
        return call(device -> device.hasFeature(feature));
    }

    @Override
    protected boolean isLowRamDeviceByDefault() {
        return "true".equals(call(device -> device.getProperty("ro.config.low_ram")));
    }

    @Override
    protected boolean isLargeScreenDeviceByDefault() {
        // TODO(b/311328290) implement large screen check in host side
        return false;
    }

    @Override
    protected boolean hasGmsCore() {
        return call(device -> PackageUtil.exists(device, GMS_CORE_PACKAGE));
    }

    @Override
    protected boolean hasPlayStore() {
        return call(device -> PackageUtil.exists(device, PLAY_STORE_PACKAGE));
    }

    @Override
    protected boolean isDebuggable() {
        return "1".equals(call(device -> device.getProperty("ro.debuggable")));
    }

    /**
     * Checks that exactly one package name is present out of provided package names and then
     * returns that.
     *
     * @return the package name if exactly 1 package name present, otherwise {@code null} if 0 or
     *     multiple package names present.
     */
    @Override
    @Nullable
    public String getAdServicesPackageName() {
        List<String> availablePackageNames =
                call(
                        device ->
                                device.getInstalledPackageNames().stream()
                                        .filter(ADSERVICES_PACKAGE_NAMES::contains)
                                        .toList());
        if (availablePackageNames == null || availablePackageNames.isEmpty()) {
            mLog.d("Failed to find the package name");
            return null;
        }
        if (availablePackageNames.size() > 1) {
            mLog.d(
                    "Found multiple package names: %s",
                    Arrays.toString(availablePackageNames.toArray()));
            return null;
        }
        return availablePackageNames.get(0);
    }

    private AdServicesHostSideSupportHelper() {
        super(ConsoleLogger.getInstance(), HostSideSystemPropertiesHelper.getInstance());
    }
}
