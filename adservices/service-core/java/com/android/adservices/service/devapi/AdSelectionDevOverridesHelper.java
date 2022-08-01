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

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.common.AdSelectionSignals;
import android.annotation.NonNull;

import androidx.annotation.Nullable;

import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelectionOverride;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import java.util.Objects;

/** Helper class to support the runtime retrieval of dev overrides for the AdSelection API. */
public class AdSelectionDevOverridesHelper {
    private static final HashFunction sHashFunction = Hashing.murmur3_128();
    private static final String API_NOT_AUTHORIZED_MSG =
            "This API is not enabled for the given app because either dev options are disabled or"
                    + " the app is not debuggable.";

    private final DevContext mDevContext;
    private final AdSelectionEntryDao mAdSelectionEntryDao;

    /**
     * Creates an instance of {@link AdSelectionDevOverridesHelper} with the given {@link
     * DevContext} and {@link AdSelectionEntryDao}.
     */
    public AdSelectionDevOverridesHelper(
            @NonNull DevContext devContext, @NonNull AdSelectionEntryDao adSelectionEntryDao) {
        Objects.requireNonNull(devContext);
        Objects.requireNonNull(adSelectionEntryDao);

        this.mDevContext = devContext;
        this.mAdSelectionEntryDao = adSelectionEntryDao;
    }

    /**
     * @return a low-collision ID for the given {@link AdSelectionConfig} instance. We are accepting
     *     collision since this is a developer targeted feature and the collision should be low rate
     *     enough not to constitute a serious issue.
     */
    public static String calculateAdSelectionConfigId(
            @NonNull AdSelectionConfig adSelectionConfig) {
        // See go/hashing#java
        Hasher hasher = sHashFunction.newHasher();
        hasher.putUnencodedChars(adSelectionConfig.getSeller())
                .putUnencodedChars(adSelectionConfig.getDecisionLogicUri().toString())
                .putUnencodedChars(adSelectionConfig.getAdSelectionSignals())
                .putUnencodedChars(adSelectionConfig.getSellerSignals());

        adSelectionConfig.getCustomAudienceBuyers().stream().forEach(hasher::putUnencodedChars);
        adSelectionConfig.getContextualAds().stream()
                .forEach(
                        adWithBid -> {
                            hasher.putUnencodedChars(
                                            adWithBid.getAdData().getRenderUri().toString())
                                    .putUnencodedChars(adWithBid.getAdData().getMetadata())
                                    .putDouble(adWithBid.getBid());
                        });
        adSelectionConfig.getPerBuyerSignals().entrySet().stream()
                .forEach(
                        buyerAndSignals -> {
                            hasher.putUnencodedChars(buyerAndSignals.getKey())
                                    .putUnencodedChars(buyerAndSignals.getValue());
                        });
        return hasher.hash().toString();
    }

    /**
     * Looks for an override for the given {@link AdSelectionConfig}. Will return {@code null} if
     * {@link DevContext#getDevOptionsEnabled()} returns false for the {@link DevContext} passed in
     * the constructor or if there is no override created by the app with package name specified in
     * {@link DevContext#getCallingAppPackageName()}.
     */
    @Nullable
    public String getDecisionLogicOverride(@NonNull AdSelectionConfig adSelectionConfig) {
        Objects.requireNonNull(adSelectionConfig);

        if (!mDevContext.getDevOptionsEnabled()) {
            return null;
        }
        return mAdSelectionEntryDao.getDecisionLogicOverride(
                calculateAdSelectionConfigId(adSelectionConfig),
                mDevContext.getCallingAppPackageName());
    }

    /**
     * Looks for an override for the given {@link AdSelectionConfig}. Will return {@code null} if
     * {@link DevContext#getDevOptionsEnabled()} returns false for the {@link DevContext} passed in
     * the constructor or if there is no override created by the app with package name specified in
     * {@link DevContext#getCallingAppPackageName()}.
     */
    @Nullable
    public AdSelectionSignals getTrustedScoringSignalsOverride(
            @NonNull AdSelectionConfig adSelectionConfig) {
        Objects.requireNonNull(adSelectionConfig);

        if (!mDevContext.getDevOptionsEnabled()) {
            return null;
        }
        String overrideSignals =
                mAdSelectionEntryDao.getTrustedScoringSignalsOverride(
                        calculateAdSelectionConfigId(adSelectionConfig),
                        mDevContext.getCallingAppPackageName());
        if (overrideSignals == null) {
            return null;
        }
        return AdSelectionSignals.fromString(overrideSignals);
    }

    /**
     * Adds an override of the {@code decisionLogicJS} along with {@link
     * DevContext#getCallingAppPackageName()} for the given {@link AdSelectionConfig}.
     *
     * @throws SecurityException if{@link DevContext#getDevOptionsEnabled()} returns false for the
     *     {@link DevContext}
     */
    public void addAdSelectionSellerOverride(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull String decisionLogicJS,
            @NonNull AdSelectionSignals trustedScoringSignals) {
        Objects.requireNonNull(adSelectionConfig);
        Objects.requireNonNull(decisionLogicJS);

        if (!mDevContext.getDevOptionsEnabled()) {
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }
        mAdSelectionEntryDao.persistAdSelectionOverride(
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(calculateAdSelectionConfigId(adSelectionConfig))
                        .setAppPackageName(mDevContext.getCallingAppPackageName())
                        .setDecisionLogicJS(decisionLogicJS)
                        .setTrustedScoringSignals(trustedScoringSignals.getStringForm())
                        .build());
    }

    /**
     * Removes an override for the given {@link AdSelectionConfig}.
     *
     * @throws SecurityException if{@link DevContext#getDevOptionsEnabled()} returns false for the
     *     {@link DevContext}
     */
    public void removeAdSelectionSellerOverride(@NonNull AdSelectionConfig adSelectionConfig) {
        Objects.requireNonNull(adSelectionConfig);

        if (!mDevContext.getDevOptionsEnabled()) {
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        String adSelectionConfigId = calculateAdSelectionConfigId(adSelectionConfig);
        String appPackageName = mDevContext.getCallingAppPackageName();

        mAdSelectionEntryDao.removeAdSelectionOverrideByIdAndPackageName(
                adSelectionConfigId, appPackageName);
    }

    /**
     * Removes all ad selection overrides that match {@link DevContext#getCallingAppPackageName()}.
     *
     * @throws SecurityException if{@link DevContext#getDevOptionsEnabled()} returns false for the
     *     {@link DevContext}
     */
    public void removeAllDecisionLogicOverrides() {
        if (!mDevContext.getDevOptionsEnabled()) {
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        mAdSelectionEntryDao.removeAllAdSelectionOverrides(mDevContext.getCallingAppPackageName());
    }
}
