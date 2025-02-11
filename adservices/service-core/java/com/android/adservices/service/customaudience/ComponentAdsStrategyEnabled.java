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
import com.android.adservices.data.customaudience.DBComponentAdData;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.stats.BuyerInputGeneratorIntermediateStats;
import com.android.adservices.service.stats.pas.PersistAdSelectionResultCalledStats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ComponentAdsStrategyEnabled implements ComponentAdsStrategy {

    @Override
    public void persistCustomAudiencesWithComponentAds(
            CustomAudienceDao customAudienceDao,
            DBCustomAudience customAudience,
            Uri dailyUpdateUri,
            boolean debuggable,
            List<ComponentAdData> componentAdDataList) {
        customAudienceDao.insertOrOverwriteCustomAudience(
                customAudience, dailyUpdateUri, debuggable, componentAdDataList);
    }

    @Override
    public void incrementNumCustomAudiencesWithComponentAds(
            BuyerInputGeneratorIntermediateStats stats) {
        stats.incrementNumCustomAudiencesWithComponentAds();
    }

    @Override
    public void setNumComponentAdsInPersistAdSelectionResultWinnerType(
            PersistAdSelectionResultCalledStats.Builder builder, int numComponentAds) {
        builder.setNumComponentAds(numComponentAds);
    }

    @Override
    public int getNumCustomAudiencesWithComponentAds(BuyerInputGeneratorIntermediateStats stats) {
        return stats.getNumCustomAudiencesWithComponentAds();
    }

    /**
     * Retrieves custom audiences and their associated component ad render IDs.
     *
     * <p>This method optimizes database I/O by fetching component ads for all unique buyers in a
     * single database query, rather than making individual queries for each {@link
     * DBCustomAudience}.
     *
     * <p>The method performs the following steps:
     *
     * <ol>
     *   <li>Extracts the set of unique {@link AdTechIdentifier} buyers from the input list.
     *   <li>Queries the DAO to retrieve all component ads associated with all extracted buyers in a
     *       single database call.
     *   <li>Creates a map (owner_buyerIdentifier_name -> List of {@link DBComponentAdData}).
     *   <li>Iterates through the input {@link DBCustomAudience} list:
     *       <ul>
     *         <li>Constructs the key (owner_buyerIdentifier_name).
     *         <li>Retrieves matching component ads from the map (or an empty list if no match).
     *         <li>Extracts the render IDs.
     *         <li>Creates a {@link CustomAudienceWithComponentAds} object.
     *         <li>Adds the combined object to the result list.
     *       </ul>
     *   <li>Returns the list of {@link CustomAudienceWithComponentAds} objects.
     * </ol>
     *
     * @param customAudienceDao The DAO used to retrieve component ad data.
     * @param dbCustomAudiences The list of {@link DBCustomAudience} objects.
     * @return A list of {@link CustomAudienceWithComponentAds}, in the same order as the input.
     */
    @Override
    public List<CustomAudienceWithComponentAds> getCustomAudiencesWithComponentAds(
            CustomAudienceDao customAudienceDao, List<DBCustomAudience> dbCustomAudiences) {
        Set<AdTechIdentifier> buyers =
                dbCustomAudiences.stream()
                        .map(DBCustomAudience::getBuyer)
                        .collect(Collectors.toSet());

        List<DBComponentAdData> componentAdDataList =
                customAudienceDao.getComponentAdsByBuyers(buyers);

        // Add component ads to a hash map keyed on owner_buyer_name
        Map<String, List<DBComponentAdData>> componentAdDataMap = new HashMap<>();
        for (DBComponentAdData componentAd : componentAdDataList) {
            String key =
                    componentAd.getOwner()
                            + "_"
                            + componentAd.getBuyer()
                            + "_"
                            + componentAd.getName();
            componentAdDataMap.computeIfAbsent(key, k -> new ArrayList<>()).add(componentAd);
        }

        List<CustomAudienceWithComponentAds> result = new ArrayList<>();
        for (DBCustomAudience customAudience : dbCustomAudiences) {
            String owner = customAudience.getOwner();
            AdTechIdentifier buyer = customAudience.getBuyer();
            String name = customAudience.getName();

            String key = owner + "_" + buyer + "_" + name;
            List<String> adRenderIds = new ArrayList<>();

            // Look up matching component ads and extract adRenderIds
            List<DBComponentAdData> matchingComponentAds =
                    componentAdDataMap.getOrDefault(key, List.of());
            for (DBComponentAdData componentAdData : matchingComponentAds) {
                adRenderIds.add(componentAdData.getRenderId());
            }

            result.add(CustomAudienceWithComponentAds.create(customAudience, adRenderIds));
        }
        return result;
    }
}
