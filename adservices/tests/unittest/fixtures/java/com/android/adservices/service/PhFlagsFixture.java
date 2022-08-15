/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.adservices.service.PhFlags.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDE;
import static com.android.adservices.service.PhFlags.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION;
import static com.android.adservices.service.PhFlags.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION;
import static com.android.adservices.service.PhFlags.KEY_FLEDGE_BACKGROUND_FETCH_ELIGIBLE_UPDATE_BASE_INTERVAL_S;

import static org.junit.Assert.assertEquals;

import android.provider.DeviceConfig;

/**
 * In order to use this test fixture, make sure your test class includes a TestableDeviceConfigRule
 * Rule.
 *
 * <p>{@code @Rule public final TestableDeviceConfig.TestableDeviceConfigRule mDeviceConfigRule =
 * new TestableDeviceConfig.TestableDeviceConfigRule(); }
 */
public class PhFlagsFixture {
    public static void configureFledgeBackgroundFetchEligibleUpdateBaseIntervalS(
            final long phOverridingValue) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_FLEDGE_BACKGROUND_FETCH_ELIGIBLE_UPDATE_BASE_INTERVAL_S,
                Long.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertEquals(
                "Failed to configure P/H flag",
                phOverridingValue,
                phFlags.getFledgeBackgroundFetchEligibleUpdateBaseIntervalS());
    }

    /**
     * Enables test to override the flag enabling the foreground status check for callers of the
     * Fledge Run Ad Selection API.
     */
    public static void overrideForegroundStatusForFledgeRunAdSelection(boolean value) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION,
                Boolean.toString(value),
                false);
    }

    /**
     * Enables test to override the flag enabling the foreground status check for callers of the
     * Fledge Report Impression API.
     */
    public static void overrideForegroundStatusForFledgeReportImpression(boolean value) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION,
                Boolean.toString(value),
                false);
    }

    /**
     * Enables test to override the flag enabling the foreground status check for callers of the
     * Fledge Override API.
     */
    public static void overrideForegroundStatusForFledgeOverrides(boolean value) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDE,
                Boolean.toString(value),
                false);
    }

    /**
     * Enables test to override the flag enabling the foreground status check for callers of the
     * Custom Audience API.
     */
    public static void overrideForegroundStatusForFledgeCustomAudience(boolean value) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                PhFlags.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE,
                Boolean.toString(value),
                false);
    }

    public static void overrideEnforceIsolateMaxHeapSize(boolean value) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                PhFlags.KEY_ENFORCE_ISOLATE_MAX_HEAP_SIZE,
                Boolean.toString(value),
                false);
    }

    public static void overrideIsolateMaxHeapSizeBytes(long value) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                PhFlags.KEY_ISOLATE_MAX_HEAP_SIZE_BYTES,
                Long.toString(value),
                false);
    }
}
