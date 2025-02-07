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

import static org.mockito.Mockito.verify;

import android.adservices.common.CommonFixture;
import android.adservices.common.ComponentAdData;
import android.adservices.common.ComponentAdDataFixture;
import android.net.Uri;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;

import org.junit.Test;
import org.mockito.Mock;

import java.util.List;

public class ComponentAdsStrategyTest extends AdServicesMockitoTestCase {
    private final ComponentAdsStrategy mComponentAdsStrategyEnabled =
            ComponentAdsStrategy.createInstance(/* componentAdsEnabled= */ true);
    private final ComponentAdsStrategy mComponentAdsStrategyDisabled =
            ComponentAdsStrategy.createInstance(/* componentAdsEnabled= */ false);
    private static final List<ComponentAdData> COMPONENT_AD_DATA_LIST =
            ComponentAdDataFixture.getValidComponentAdsByBuyer(CommonFixture.VALID_BUYER_1);

    @Mock private CustomAudienceDao mCustomAudienceDaoMock;

    @Test
    public void testEnabledStrategyPersistCustomAudiencesWithComponentAdsAddsComponentAds() {
        DBCustomAudience customAudience =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1).build();
        Uri dailyUpdateUri = Uri.parse("https://example.com");

        boolean debuggable = true;

        mComponentAdsStrategyEnabled.persistCustomAudiencesWithComponentAds(
                mCustomAudienceDaoMock,
                customAudience,
                dailyUpdateUri,
                debuggable,
                COMPONENT_AD_DATA_LIST);

        verify(mCustomAudienceDaoMock)
                .insertOrOverwriteCustomAudience(
                        customAudience, dailyUpdateUri, debuggable, COMPONENT_AD_DATA_LIST);
    }

    @Test
    public void testDisabledStrategyPersistCustomAudiencesWithComponentAdsOnlyAddsCA() {
        DBCustomAudience customAudience =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1).build();
        Uri dailyUpdateUri = Uri.parse("https://example.com");

        boolean debuggable = true;

        mComponentAdsStrategyDisabled.persistCustomAudiencesWithComponentAds(
                mCustomAudienceDaoMock,
                customAudience,
                dailyUpdateUri,
                debuggable,
                COMPONENT_AD_DATA_LIST);

        verify(mCustomAudienceDaoMock)
                .insertOrOverwriteCustomAudience(
                        customAudience, dailyUpdateUri, debuggable, List.of());
    }
}
