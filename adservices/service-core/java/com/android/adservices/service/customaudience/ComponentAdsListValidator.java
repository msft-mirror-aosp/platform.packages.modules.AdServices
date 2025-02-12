/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.adservices.service.customaudience;

import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.RENDER_URI_KEY;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.ComponentAdData;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.common.AdRenderIdValidator;
import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.ValidatorUtil;

import java.util.ArrayList;
import java.util.List;

/** Class that filters out invalid component ads. */
public class ComponentAdsListValidator {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private final AdRenderIdValidator mComponentAdRenderIdValidator;
    private final int mMaxNumComponentAds;

    public ComponentAdsListValidator(
            int componentAdRenderIdMaxLengthBytes, int maxNumComponentAds) {
        mComponentAdRenderIdValidator =
                AdRenderIdValidator.createEnabledInstance(componentAdRenderIdMaxLengthBytes);
        mMaxNumComponentAds = maxNumComponentAds;
    }

    /**
     * Filters a list of {@link ComponentAdData} objects, validating their render URIs and render
     * IDs.
     *
     * <p>This method performs the following checks:
     *
     * <ol>
     *   <li>Checks if the number of component ads exceeds a predefined maximum ({@code
     *       mMaxNumComponentAds}). If it does, an empty list is returned.
     *   <li>Validates the render URI of each component ad using an {@link AdTechUriValidator}.
     *   <li>Validates the ad render ID of each component ad using an {@link AdRenderIdValidator}.
     * </ol>
     *
     * <p>Only component ads that pass *both* URI and ID validation are included in the returned
     * list. If any validation fails (due to an invalid URI or an invalid render ID), that specific
     * component ad is skipped. The order of valid component ads is preserved.
     */
    public List<ComponentAdData> extractValidComponentAds(
            AdTechIdentifier buyer, List<ComponentAdData> componentAds) {
        List<ComponentAdData> validatedComponentAds = new ArrayList<>();
        if (componentAds.size() > mMaxNumComponentAds) {
            sLogger.v(
                    "Size of component ads list exceeds the maximum allowed. Empty list returned as"
                            + " a result.");
            return validatedComponentAds;
        }

        AdTechUriValidator uriValidator =
                new AdTechUriValidator(
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        buyer.toString(),
                        this.getClass().getSimpleName(),
                        RENDER_URI_KEY);

        for (ComponentAdData componentAd : componentAds) {
            try {
                uriValidator.validate(componentAd.getRenderUri());
                mComponentAdRenderIdValidator.validate(componentAd.getAdRenderId());
                validatedComponentAds.add(componentAd);
            } catch (IllegalArgumentException exception) {
                sLogger.d(
                        "Component ad %s failed validation with exception %s, skipping",
                        componentAd, exception.toString());
            }
        }
        return validatedComponentAds;
    }
}
