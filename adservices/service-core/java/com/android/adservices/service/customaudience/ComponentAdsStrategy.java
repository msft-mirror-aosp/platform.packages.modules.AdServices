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

    /**
     * Returns an implementation for the {@link ComponentAdsStrategy} depending on whether the
     * component ads feature is enabled.
     */
    static ComponentAdsStrategy createInstance(boolean componentAdsEnabled) {
        if (componentAdsEnabled) {
            return new ComponentAdsStrategyEnabled();
        } else {
            return new ComponentAdsStrategy() {
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
            };
        }
    }
}
