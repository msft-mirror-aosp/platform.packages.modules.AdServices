/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.ComponentAdData;
import android.net.Uri;

import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.stats.BuyerInputGeneratorIntermediateStats;
import com.android.adservices.service.stats.pas.PersistAdSelectionResultCalledStats;

import java.util.List;

/** Interface reading/writing custom audiences with component ads. */
public interface ComponentAdsStrategy {

    /** Persists a custom audience with component ads. */
    void persistCustomAudiencesWithComponentAds(
            CustomAudienceDao customAudienceDao,
            DBCustomAudience customAudience,
            Uri dailyUpdateUri,
            boolean debuggable,
            List<ComponentAdData> componentAdDataList);

    /** Returns a list of valid component ads. */
    List<ComponentAdData> extractValidComponentAds(
            AdTechIdentifier buyer, List<ComponentAdData> componentAds);

    /** Returns a list of custom audiences with component ads attached. */
    List<CustomAudienceWithComponentAds> getCustomAudiencesWithComponentAds(
            CustomAudienceDao customAudienceDao, List<DBCustomAudience> dbCustomAudiences);

    /** Increments the number of custom audiences for this buyer sending component ads. */
    void incrementNumCustomAudiencesWithComponentAds(BuyerInputGeneratorIntermediateStats stats);

    /** Sets number of component ads with persistAdSelectionResult winner type metric. */
    void setNumComponentAdsInPersistAdSelectionResultWinnerType(
            PersistAdSelectionResultCalledStats.Builder builder, int numComponentAds);

    /** Returns the number of custom audiences for this buyer sending component ads. */
    int getNumCustomAudiencesWithComponentAds(BuyerInputGeneratorIntermediateStats stats);

    /**
     * Returns an implementation for the {@link ComponentAdsStrategy} depending on whether the
     * component ads feature is enabled.
     */
    static ComponentAdsStrategy createInstance(
            boolean componentAdsEnabled, ComponentAdsListValidator componentAdsListValidator) {
        if (componentAdsEnabled) {
            return new ComponentAdsStrategyEnabled(componentAdsListValidator);
        } else {
            return new ComponentAdsStrategyDisabled();
        }
    }
}
