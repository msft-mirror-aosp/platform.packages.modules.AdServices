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

package android.adservices.common;

import android.annotation.SystemApi;

/** Permissions used by the AdServices APIs. */
public class AdServicesPermissions {
    private AdServicesPermissions() {}

    /** This permission needs to be declared by the caller of Topics APIs. */
    public static final String ACCESS_ADSERVICES_TOPICS =
            "android.permission.ACCESS_ADSERVICES_TOPICS";

    /** This permission needs to be declared by the caller of Attribution APIs. */
    public static final String ACCESS_ADSERVICES_ATTRIBUTION =
            "android.permission.ACCESS_ADSERVICES_ATTRIBUTION";

    /** This permission needs to be declared by the caller of Custom Audiences APIs. */
    public static final String ACCESS_ADSERVICES_CUSTOM_AUDIENCE =
            "android.permission.ACCESS_ADSERVICES_CUSTOM_AUDIENCE";

    /** This permission needs to be declared by the caller of Advertising ID APIs. */
    public static final String ACCESS_ADSERVICES_ADID = "android.permission.ACCESS_ADSERVICES_ADID";

    /**
     * This permission needs to be declared by the Consent Service to access AdServices.
     *
     * @hide
     */
    @SystemApi
    public static final String ACCESS_ADSERVICES_CONSENT =
            "android.permission.ACCESS_ADSERVICES_CONSENT";

    /**
     * This permission needs to be declared by the AdServices to access API for AdID.
     *
     * @hide
     */
    @SystemApi
    public static final String ADSERVICES_ACCESS_AD_ID =
            "android.permission.ADSERVICES_ACCESS_AD_ID";

    /**
     * This permission needs to be declared by the AdServices to access API for AppSetId.
     *
     * @hide
     */
    @SystemApi
    public static final String ADSERVICES_ACCESS_APP_SET_ID =
            "android.permission.ADSERVICES_ACCESS_APP_SET_ID";

    /**
     * This permission needs to be declared by the AdServices to access Consent Service.
     *
     * @hide
     */
    @SystemApi
    public static final String ADSERVICES_ACCESS_CONSENT_SERVICE =
            "android.permission.ADSERVICES_ACCESS_CONSENT_SERVICE";
}
