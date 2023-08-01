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

import static android.provider.DeviceConfig.NAMESPACE_ADSERVICES;

import static com.android.compatibility.common.util.ShellIdentityUtils.invokeStaticMethodWithShellPermissions;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.SystemProperties;
import android.provider.DeviceConfig;
import android.text.TextUtils;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.function.Supplier;

/** Helper to check if AdServices is supported / enabled in a device. */
public final class AdServicesSupportHelper {

    private static final String TAG = AdServicesSupportHelper.class.getSimpleName();

    private static final String SYSPROP_ADSERVICES_SUPPORTED = "debug.adservices.supported";
    private static final String SYSPROP_SDK_SANDBOX_SUPPORTED =
            "debug.adservices.sdk_sandbox_supported";

    // TODO(b/284971005): reuse from PhFlags ?
    private static final String SYSTEM_PROPERTY_PREFIX = "debug.adservices.";
    private static final String KEY_GLOBAL_KILL_SWITCH = "global_kill_switch";
    private static final boolean GLOBAL_KILL_SWITCH = true;

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    private static boolean isDeviceSupportedByDefault(Context context) {
        PackageManager pm = context.getPackageManager();
        return !pm.hasSystemFeature(PackageManager.FEATURE_RAM_LOW) // Android Go Devices
                && !pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
                && !pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
                && !pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    // TODO(b/284971005): use isDeviceSupportedByDefault() instead (if Go check is ok)
    private static boolean isSdkSandboxSupportedOnDeviceByDefault(Context context) {
        PackageManager pm = context.getPackageManager();
        return !pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
                && !pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
                && !pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    /** Checks whether AdServices is supported by the device / form factor. */
    public static boolean isDeviceSupported() {
        return isDeviceSupported(
                sContext,
                "isDeviceSupported()",
                SYSPROP_ADSERVICES_SUPPORTED,
                () -> isDeviceSupportedByDefault(sContext));
    }

    private static boolean isDeviceSupported(
            Context context,
            String logPrefix,
            String sysProp,
            Supplier<Boolean> supportedByDefault) {
        if (AdservicesTestHelper.isDebuggable()) {
            String overriddenValue = SystemProperties.get(sysProp);
            if (!TextUtils.isEmpty(overriddenValue)) {
                boolean supported = Boolean.valueOf(overriddenValue);
                Log.i(
                        TAG,
                        logPrefix
                                + ": returning "
                                + supported
                                + " as defined by system property "
                                + sysProp
                                + " ("
                                + overriddenValue
                                + ")");
                return supported;
            }
        }

        boolean supported = isDeviceSupportedByDefault(context);
        Log.v(TAG, logPrefix + ": returning hardcoded value (" + supported + ")");
        return supported;
    }

    /** Checks whether SdkSandbox is supported by the device / form factor. */
    public static boolean isSdkSandboxSupportedOnDevice() {
        return isSdkSandboxSupportedOnDevice(sContext);
    }

    /** Checks whether SdkSandbox is supported by the device / form factor. */
    public static boolean isSdkSandboxSupportedOnDevice(Context context) {
        return isDeviceSupported(
                context,
                "isSdkSandboxSupportedOnDevice()",
                SYSPROP_SDK_SANDBOX_SUPPORTED,
                () -> isSdkSandboxSupportedOnDeviceByDefault(context));
    }

    /** Checks whether AdServices is enabled in the device. */
    public static boolean isFeatureSupported() throws Exception {
        // TODO(b/284744130): there's more logic than that, we might need to refactor the PhFlags
        // logic into a separate library used both by the production and test code
        // TODO(b/284744130): once moved to common code, use the adservice_enabled /
        // getAdServicesEnabled()  flag instead (and perhaps even rename this method to
        // isAdServicesEnabled()
        return invokeStaticMethodWithShellPermissions(
                () ->
                        !SystemProperties.getBoolean(
                                getSystemPropertyName(KEY_GLOBAL_KILL_SWITCH),
                                /* defaultValue= */ DeviceConfig.getBoolean(
                                        NAMESPACE_ADSERVICES,
                                        /* flagName= */ KEY_GLOBAL_KILL_SWITCH,
                                        /* defaultValue= */ GLOBAL_KILL_SWITCH)));
    }

    private static String getSystemPropertyName(String key) {
        return SYSTEM_PROPERTY_PREFIX + key;
    }

    private AdServicesSupportHelper() {
        throw new UnsupportedOperationException("Provides only static methods");
    }
}
