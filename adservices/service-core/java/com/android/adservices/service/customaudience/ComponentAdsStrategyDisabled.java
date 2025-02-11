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

import static com.android.adservices.service.stats.AdServicesLoggerUtil.FIELD_UNSET;

import android.adservices.common.ComponentAdData;
import android.net.Uri;

import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.stats.BuyerInputGeneratorIntermediateStats;
import com.android.adservices.service.stats.pas.PersistAdSelectionResultCalledStats;

import java.util.List;
import java.util.stream.Collectors;

public class ComponentAdsStrategyDisabled implements ComponentAdsStrategy {

    @Override
    public void persistCustomAudiencesWithComponentAds(
            CustomAudienceDao customAudienceDao,
            DBCustomAudience customAudience,
            Uri dailyUpdateUri,
            boolean debuggable,
            List<ComponentAdData> componentAdDataList) {
        customAudienceDao.insertOrOverwriteCustomAudience(
                customAudience, dailyUpdateUri, debuggable, List.of());
    }

    @Override
    public void incrementNumCustomAudiencesWithComponentAds(
            BuyerInputGeneratorIntermediateStats stats) {
        // Do nothing.
    }

    @Override
    public void setNumComponentAdsInPersistAdSelectionResultWinnerType(
            PersistAdSelectionResultCalledStats.Builder builder, int numComponentAds) {
        // Sets numComponentAds to FIELD_UNSET when component ads disabled.
        builder.setNumComponentAds(FIELD_UNSET);
    }

    @Override
    public int getNumCustomAudiencesWithComponentAds(BuyerInputGeneratorIntermediateStats stats) {
        return FIELD_UNSET;
    }

    @Override
    public List<CustomAudienceWithComponentAds> getCustomAudiencesWithComponentAds(
            CustomAudienceDao customAudienceDao, List<DBCustomAudience> dbCustomAudiences) {
        return dbCustomAudiences.stream()
                .map(
                        dbCustomAudience ->
                                CustomAudienceWithComponentAds.create(dbCustomAudience, List.of()))
                .collect(Collectors.toList());
    }
}
