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

package com.android.adservices.service.common;

import android.annotation.NonNull;

/** The object representing the AdServices manifest config. */
public class AppManifestConfig {
    private final AppManifestAttributionConfig mAttributionConfig;
    private final AppManifestCustomAudiencesConfig mCustomAudiencesConfig;
    private final AppManifestTopicsConfig mTopicsConfig;

    /**
     * AdServices manifest config must contain configs for Attribution, Custom Audiences and Topics.
     *
     * @param attributionConfig the config for Attribution.
     * @param customAudiencesConfig the config for Custom Audiences.
     * @param topicsConfig the config for Topics.
     */
    public AppManifestConfig(
            @NonNull AppManifestAttributionConfig attributionConfig,
            @NonNull AppManifestCustomAudiencesConfig customAudiencesConfig,
            @NonNull AppManifestTopicsConfig topicsConfig) {
        mAttributionConfig = attributionConfig;
        mCustomAudiencesConfig = customAudiencesConfig;
        mTopicsConfig = topicsConfig;
    }

    /** Getter for AttributionConfig. */
    @NonNull
    public AppManifestAttributionConfig getAttributionConfig() {
        return mAttributionConfig;
    }

    /**
     * Returns if sdk is permitted to access Attribution API for config represented by this object.
     */
    public boolean isAllowedAttributionAccess(@NonNull String sdk) {
        return mAttributionConfig.getAllowAllToAccess()
                || mAttributionConfig.getAllowAdPartnersToAccess().contains(sdk);
    }

    /** Getter for CustomAudiencesConfig. */
    @NonNull
    public AppManifestCustomAudiencesConfig getCustomAudiencesConfig() {
        return mCustomAudiencesConfig;
    }

    /**
     * Returns if sdk is permitted to access Custom Audiences API for config represented by this
     * object.
     */
    @NonNull
    public boolean isAllowedCustomAudiencesAccess(@NonNull String sdk) {
        return mCustomAudiencesConfig.getAllowAllToAccess()
                || mCustomAudiencesConfig.getAllowAdPartnersToAccess().contains(sdk);
    }

    /** Getter for TopicsConfig. */
    @NonNull
    public AppManifestTopicsConfig getTopicsConfig() {
        return mTopicsConfig;
    }

    /** Returns if sdk is permitted to access Topics API for config represented by this object. */
    public boolean isAllowedTopicsAccess(@NonNull String sdk) {
        return mTopicsConfig.getAllowAllToAccess()
                || mTopicsConfig.getAllowAdPartnersToAccess().contains(sdk);
    }
}
