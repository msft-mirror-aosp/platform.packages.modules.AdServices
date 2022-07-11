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
}
