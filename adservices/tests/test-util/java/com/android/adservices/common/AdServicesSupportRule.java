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

import static com.android.adservices.common.AbstractSupportedFeatureRule.Mode.SUPPORTED_BY_DEFAULT;

import android.app.Instrumentation;
import android.content.pm.PackageManager;
import android.os.SystemProperties;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.AssumptionViolatedException;

// TODO(b/284971005): improve javadoc / add examples
/**
 * Rule used to properly check a test behavior depending on whether the device supports AdService.
 */
public final class AdServicesSupportRule extends AbstractSupportedFeatureRule {

    private static final String TAG = AdServicesSupportRule.class.getSimpleName();

    // TODO(b/284971005): add unit test to make sure it's false
    /**
     * When set to {@code true}, it checks whether the device is supported by reading the {@value
     * #DEBUG_PROP_IS_SUPPORTED} system property.
     *
     * <p>Should <b>NEVER</b> be merged as {@code true} - it's only meant to be used locally to
     * develop / debug the rule itself (not real tests).
     */
    private static final boolean ALLOW_OVERRIDE_BY_SYS_PROP = false;

    private static final String DEBUG_PROP_IS_SUPPORTED =
            "debug.AbstractSupportedFeatureRule.supported";

    public AdServicesSupportRule() {
        this(SUPPORTED_BY_DEFAULT);
    }

    public AdServicesSupportRule(Mode mode) {
        super(mode);
    }

    @Override
    boolean isFeatureSupported() {
        boolean isSupported;
        if (ALLOW_OVERRIDE_BY_SYS_PROP) {
            logI("isFeatureSupported(): checking value from property %s", DEBUG_PROP_IS_SUPPORTED);
            isSupported = SystemProperties.getBoolean(DEBUG_PROP_IS_SUPPORTED, true);
        } else {
            isSupported = isDeviceSupported();
        }
        logV("isFeatureSupported(): %b", isSupported);
        return isSupported;
    }

    @Override
    protected void throwFeatureNotSupportedAVE() {
        throw new AssumptionViolatedException("Device doesn't support AdServices");
    }

    @Override
    protected void throwFeatureSupportedAVE() {
        throw new AssumptionViolatedException("Device supports AdServices");
    }

    @Override
    protected void logI(String msgFmt, Object... msgArgs) {
        Log.i(TAG, String.format(msgFmt, msgArgs));
    }

    @Override
    protected void logD(String msgFmt, Object... msgArgs) {
        Log.d(TAG, String.format(msgFmt, msgArgs));
    }

    @Override
    protected void logV(String msgFmt, Object... msgArgs) {
        Log.v(TAG, String.format(msgFmt, msgArgs));
    }

    // TODO(b/284971005): currently it's package-protected and static because it's used by
    // AdservicesTestHelper.isDeviceSupported(). Once that method is gone, inline the logic into
    // isFeatureSupported().
    /** Checks whether AdServices is supported in the device. */
    static boolean isDeviceSupported() {
        // TODO(b/284744130): read from flags
        Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        PackageManager pm = inst.getContext().getPackageManager();
        return !pm.hasSystemFeature(PackageManager.FEATURE_RAM_LOW) // Android Go Devices
                && !pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
                && !pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
                && !pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }
}
