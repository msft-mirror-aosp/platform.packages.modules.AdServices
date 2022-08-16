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
package com.android.adservices;

import android.adservices.adid.AdIdProviderService;
import android.adservices.appsetid.AppSetIdProviderService;

/**
 * Common constants for AdServices
 *
 * @hide
 */
public class AdServicesCommon {
    private AdServicesCommon() {}

    /** Intent action to discover the Topics service in the APK. */
    public static final String ACTION_TOPICS_SERVICE = "android.adservices.TOPICS_SERVICE";

    /** Intent action to discover the Custom Audience service in the APK. */
    public static final String ACTION_CUSTOM_AUDIENCE_SERVICE =
            "android.adservices.customaudience.CUSTOM_AUDIENCE_SERVICE";

    /** Intent action to discover the AdSelection service in the APK. */
    public static final String ACTION_AD_SELECTION_SERVICE =
            "android.adservices.adselection.AD_SELECTION_SERVICE";

    /** Intent action to discover the Measurement service in the APK. */
    public static final String ACTION_MEASUREMENT_SERVICE =
            "android.adservices.MEASUREMENT_SERVICE";

    /** Intent action to discover the AdId service in the APK. */
    public static final String ACTION_ADID_SERVICE = "android.adservices.ADID_SERVICE";

    /** Intent action to discover the AdId Provider service. */
    public static final String ACTION_ADID_PROVIDER_SERVICE = AdIdProviderService.SERVICE_INTERFACE;

    /** Intent action to discover the AppSetId service in the APK. */
    public static final String ACTION_APPSETID_SERVICE = "android.adservices.APPSETID_SERVICE";

    /** Intent action to discover the AppSetId Provider service. */
    public static final String ACTION_APPSETID_PROVIDER_SERVICE =
            AppSetIdProviderService.SERVICE_INTERFACE;

    /** Intent action to discover the AdServicesCommon service in the APK. */
    public static final String ACTION_AD_SERVICES_COMMON_SERVICE =
            "android.adservices.AD_SERVICES_COMMON_SERVICE";
}
