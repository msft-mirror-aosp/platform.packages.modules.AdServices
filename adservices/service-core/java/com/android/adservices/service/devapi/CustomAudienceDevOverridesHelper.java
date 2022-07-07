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

package com.android.adservices.service.devapi;

import android.annotation.NonNull;

import androidx.annotation.Nullable;

import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudienceOverride;

import java.util.Objects;

/**
 * Helper class to support the persistence and retrieval of dev overrides for the Custom Audience
 * API.
 */
public class CustomAudienceDevOverridesHelper {
    private static final String API_NOT_AUTHORIZED_MSG =
            "This API is not enabled for the given app because either dev options are disabled or"
                    + " the app is not debuggable.";

    private final DevContext mDevContext;
    private final CustomAudienceDao mCustomAudienceDao;

    /**
     * Creates an instance of {@link CustomAudienceDevOverridesHelper} with the given {@link
     * DevContext} and {@link CustomAudienceDao}.
     */
    public CustomAudienceDevOverridesHelper(
            @NonNull DevContext devContext, @NonNull CustomAudienceDao customAudienceDao) {
        Objects.requireNonNull(devContext);
        Objects.requireNonNull(customAudienceDao);

        this.mDevContext = devContext;
        this.mCustomAudienceDao = customAudienceDao;
    }

    /**
     * Looks for an bidding logic override for the given combination of {@code owner}, {@code
     * buyer}, and {@code name}. Will return {@code null} if {@link
     * DevContext#getDevOptionsEnabled()} returns false for the {@link DevContext} passed in the
     * constructor or if there is no override created by the app with package name specified in
     * {@link DevContext#getCallingAppPackageName()}.
     */
    @Nullable
    public String getBiddingLogicOverride(
            @NonNull String owner, @NonNull String buyer, @NonNull String name) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(buyer);
        Objects.requireNonNull(name);

        String appPackageName = mDevContext.getCallingAppPackageName();

        return mCustomAudienceDao.getBiddingLogicUrlOverride(owner, buyer, name, appPackageName);
    }

    /**
     * Adds an override of the {@code biddingLogicJS} and {@code trustedBiddingData} along with
     * {@link DevContext#getCallingAppPackageName()} for the given combination of {@code owner},
     * {@code buyer}, and {@code name}.
     *
     * @throws SecurityException if {@link DevContext#getDevOptionsEnabled()} returns false for the
     *     {@link DevContext}
     */
    public void addOverride(
            @NonNull String owner,
            @NonNull String buyer,
            @NonNull String name,
            @NonNull String biddingLogicJS,
            @NonNull String trustedBiddingData) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(buyer);
        Objects.requireNonNull(name);
        Objects.requireNonNull(biddingLogicJS);
        Objects.requireNonNull(trustedBiddingData);

        if (!mDevContext.getDevOptionsEnabled()) {
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        String appPackageName = mDevContext.getCallingAppPackageName();

        if (Objects.equals(owner, appPackageName)) {
            mCustomAudienceDao.persistCustomAudienceOverride(
                    DBCustomAudienceOverride.builder()
                            .setOwner(owner)
                            .setBuyer(buyer)
                            .setName(name)
                            .setBiddingLogicJS(biddingLogicJS)
                            .setTrustedBiddingData(trustedBiddingData)
                            .setAppPackageName(appPackageName)
                            .build());
        }
    }

    /**
     * Removes an override for the given combination of {@code owner}, {@code buyer}, and {@code
     * name}.
     *
     * @throws SecurityException if{@link DevContext#getDevOptionsEnabled()} returns false for the
     *     {@link DevContext}
     */
    public void removeOverride(@NonNull String owner, @NonNull String buyer, @NonNull String name) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(buyer);
        Objects.requireNonNull(name);

        if (!mDevContext.getDevOptionsEnabled()) {
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        String appPackageName = mDevContext.getCallingAppPackageName();

        mCustomAudienceDao.removeCustomAudienceOverrideByPrimaryKeyAndPackageName(
                owner, buyer, name, appPackageName);
    }

    /**
     * Removes all custom audience overrides that match {@link
     * DevContext#getCallingAppPackageName()}.
     *
     * @throws SecurityException if{@link DevContext#getDevOptionsEnabled()} returns false for the
     *     {@link DevContext}
     */
    public void removeAllOverrides() {
        if (!mDevContext.getDevOptionsEnabled()) {
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        mCustomAudienceDao.removeAllCustomAudienceOverrides(mDevContext.getCallingAppPackageName());
    }
}
