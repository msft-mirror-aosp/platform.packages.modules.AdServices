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

package com.android.adservices.service.consent;

import android.annotation.NonNull;
import android.content.Context;
import android.telephony.TelephonyManager;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Provides information about the region of the device which is used. Currently it's used only to
 * determine whether the device should be treated as EU or non-EU.
 */
public class DeviceRegionProvider {
    private static final String FEATURE_TELEPHONY = "android.hardware.telephony";
    private static final Set<String> EU_ALPHA2_CODES =
            Set.of(
                    "AT", // Austria
                    "BE", // Belgium
                    "BG", // Bulgaria
                    "HR", // Croatia
                    "CY", // Republic of Cyprus
                    "CZ", // Czech Republic
                    "DK", // Denmark
                    "EE", // Estonia
                    "FI", // Finland
                    "FR", // France
                    "DE", // Germany
                    "GR", // Greece
                    "HU", // Hungary
                    "IE", // Ireland
                    "IT", // Italy
                    "LV", // Latvia
                    "LT", // Lithuania
                    "LU", // Luxembourg
                    "MT", // Malta
                    "NL", // Netherlands
                    "PL", // Poland
                    "PT", // Portugal
                    "RO", // Romania
                    "SK", // Slovakia
                    "SI", // Slovenia
                    "ES", // Spain
                    "SE", // Sweden
                    "IS", // Iceland
                    "LI", // Liechtenstein
                    "NO" // Norway
                    );

    /**
     * @return true if the device should be treated as EU device, otherwise false.
     * @param context {@link Context} of the caller.
     */
    public static boolean isEuDevice(@NonNull Context context) {
        Objects.requireNonNull(context);
        if (context.getPackageManager().hasSystemFeature(FEATURE_TELEPHONY)) {
            TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
            if (telephonyManager == null) return false;

            String simCountryIso = telephonyManager.getSimCountryIso();

            if (simCountryIso.isEmpty()
                    || !EU_ALPHA2_CODES.contains(simCountryIso.toUpperCase(Locale.ENGLISH))) {
                return false;
            }

            return true;
        }
        // if there telephony feature, we fallback to non-EU device
        return false;
    }
}
