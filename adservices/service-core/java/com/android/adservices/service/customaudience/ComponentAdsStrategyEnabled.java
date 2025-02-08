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

import android.adservices.common.ComponentAdData;
import android.net.Uri;

import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.stats.BuyerInputGeneratorIntermediateStats;
import com.android.adservices.service.stats.pas.PersistAdSelectionResultCalledStats;

import java.util.List;

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
}
