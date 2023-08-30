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
import android.annotation.Nullable;

import com.android.adservices.LogUtil;

/** The object representing the AdServices manifest config. */
public class AppManifestConfig {
    @NonNull private final AppManifestIncludesSdkLibraryConfig mIncludesSdkLibraryConfig;
    @Nullable private final AppManifestAttributionConfig mAttributionConfig;
    @Nullable private final AppManifestCustomAudiencesConfig mCustomAudiencesConfig;
    @Nullable private final AppManifestTopicsConfig mTopicsConfig;
    @Nullable private final AppManifestAdIdConfig mAdIdConfig;
    @Nullable private final AppManifestAppSetIdConfig mAppSetIdConfig;

    /**
     * AdServices manifest config must contain configs for Attribution, Custom Audiences, AdId,
     * AppSetId and Topics.
     *
     * <p>If any tags (except for the {@code <includes-sdk-library>} tag) are not found in the ad
     * services config, these configs will be {@code null}.
     *
     * @param includesSdkLibraryConfig the list of Sdk Libraries included in the app.
     * @param attributionConfig the config for Attribution.
     * @param customAudiencesConfig the config for Custom Audiences.
     * @param topicsConfig the config for Topics.
     * @param adIdConfig the config for adId.
     * @param appSetIdConfig the config for appSetId.
     */
    public AppManifestConfig(
            @NonNull AppManifestIncludesSdkLibraryConfig includesSdkLibraryConfig,
            @Nullable AppManifestAttributionConfig attributionConfig,
            @Nullable AppManifestCustomAudiencesConfig customAudiencesConfig,
            @Nullable AppManifestTopicsConfig topicsConfig,
            @Nullable AppManifestAdIdConfig adIdConfig,
            @Nullable AppManifestAppSetIdConfig appSetIdConfig) {
        mIncludesSdkLibraryConfig = includesSdkLibraryConfig;
        mAttributionConfig = attributionConfig;
        mCustomAudiencesConfig = customAudiencesConfig;
        mTopicsConfig = topicsConfig;
        mAdIdConfig = adIdConfig;
        mAppSetIdConfig = appSetIdConfig;
    }

    /** Getter for IncludesSdkLibraryConfig. */
    @NonNull
    public AppManifestIncludesSdkLibraryConfig getIncludesSdkLibraryConfig() {
        return mIncludesSdkLibraryConfig;
    }

    /**
     * Getter for AttributionConfig.
     *
     * <p>If the tag is not found in the app manifest config, this config is {@code null}.
     */
    @Nullable
    public AppManifestAttributionConfig getAttributionConfig() {
        if (mAttributionConfig == null) {
            LogUtil.v("App manifest config attribution tag not found");
        }

        return mAttributionConfig;
    }

    /**
     * Returns if the ad partner is permitted to access Attribution API for config represented by
     * this object.
     *
     * <p>If the tag is not found in the app manifest config, returns {@code false}.
     */
    public boolean isAllowedAttributionAccess(@NonNull String enrollmentId) {
        if (mAttributionConfig == null) {
            LogUtil.v("App manifest config attribution tag not found");
            return false;
        }

        return mAttributionConfig.getAllowAllToAccess()
                || mAttributionConfig.getAllowAdPartnersToAccess().contains(enrollmentId);
    }

    /**
     * Getter for CustomAudiencesConfig.
     *
     * <p>If the tag is not found in the app manifest config, this config is {@code null}.
     */
    @Nullable
    public AppManifestCustomAudiencesConfig getCustomAudiencesConfig() {
        if (mCustomAudiencesConfig == null) {
            LogUtil.v("App manifest config custom-audiences tag not found");
        }

        return mCustomAudiencesConfig;
    }

    /**
     * Returns {@code true} if an ad tech with the given enrollment ID is permitted to access Custom
     * Audience API for config represented by this object.
     *
     * <p>If the tag is not found in the app manifest config, returns {@code false}.
     */
    public boolean isAllowedCustomAudiencesAccess(@NonNull String enrollmentId) {
        if (mCustomAudiencesConfig == null) {
            LogUtil.v("App manifest config custom-audiences tag not found");
            return false;
        }

        return mCustomAudiencesConfig.getAllowAllToAccess()
                || mCustomAudiencesConfig.getAllowAdPartnersToAccess().contains(enrollmentId);
    }

    /**
     * Getter for TopicsConfig.
     *
     * <p>If the tag is not found in the app manifest config, this config is {@code null}.
     */
    @Nullable
    public AppManifestTopicsConfig getTopicsConfig() {
        if (mTopicsConfig == null) {
            LogUtil.v("App manifest config topics tag not found");
        }

        return mTopicsConfig;
    }

    /**
     * Returns if the ad partner is permitted to access Topics API for config represented by this
     * object.
     *
     * <p>If the tag is not found in the app manifest config, returns {@code false}.
     */
    public boolean isAllowedTopicsAccess(@NonNull String enrollmentId) {
        if (mTopicsConfig == null) {
            LogUtil.v("App manifest config topics tag not found");
            return false;
        }

        return mTopicsConfig.getAllowAllToAccess()
                || mTopicsConfig.getAllowAdPartnersToAccess().contains(enrollmentId);
    }

    /**
     * Getter for AdIdConfig.
     *
     * <p>If the tag is not found in the app manifest config, this config is {@code null}.
     */
    @Nullable
    public AppManifestAdIdConfig getAdIdConfig() {
        if (mAdIdConfig == null) {
            LogUtil.v("App manifest config adid tag not found");
        }

        return mAdIdConfig;
    }

    /**
     * Returns if sdk is permitted to access AdId API for config represented by this object.
     *
     * <p>If the tag is not found in the app manifest config, returns {@code false}.
     */
    public boolean isAllowedAdIdAccess(@NonNull String sdk) {
        if (mAdIdConfig == null) {
            LogUtil.v("App manifest config adid tag not found");
            return false;
        }

        return mAdIdConfig.getAllowAllToAccess()
                || mAdIdConfig.getAllowAdPartnersToAccess().contains(sdk);
    }

    /**
     * Getter for AppSetIdConfig.
     *
     * <p>If the tag is not found in the app manifest config, this config is {@code null}.
     */
    @Nullable
    public AppManifestAppSetIdConfig getAppSetIdConfig() {
        if (mAdIdConfig == null) {
            LogUtil.v("App manifest config appsetid tag not found");
        }

        return mAppSetIdConfig;
    }

    /**
     * Returns if sdk is permitted to access AppSetId API for config represented by this object.
     *
     * <p>If the tag is not found in the app manifest config, returns {@code false}.
     */
    public boolean isAllowedAppSetIdAccess(@NonNull String sdk) {
        if (mAdIdConfig == null) {
            LogUtil.v("App manifest config appsetid tag not found");
            return false;
        }

        return mAppSetIdConfig.getAllowAllToAccess()
                || mAppSetIdConfig.getAllowAdPartnersToAccess().contains(sdk);
    }
}
