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
import static org.mockito.Mockito.verifyZeroInteractions;

import android.adservices.common.CommonFixture;
import android.adservices.common.ComponentAdDataFixture;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.data.customaudience.CustomAudienceDao;

import org.junit.Test;
import org.mockito.Mock;

public class ComponentAdsStrategyTest extends AdServicesMockitoTestCase {
    private final ComponentAdsStrategy mComponentAdsStrategyEnabled =
            ComponentAdsStrategy.createInstance(/* componentAdsEnabled= */ true);
    private final ComponentAdsStrategy mComponentAdsStrategyDisabled =
            ComponentAdsStrategy.createInstance(/* componentAdsEnabled= */ false);

    @Mock private CustomAudienceDao mCustomAudienceDao;

    @Test
    public void testEnabledStrategyPersistComponentAdsInvokesDaoMethod() {
        CustomAudience customAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                        .setComponentAds(
                                ComponentAdDataFixture.getValidComponentAdsByBuyer(
                                        CommonFixture.VALID_BUYER_1))
                        .build();

        mComponentAdsStrategyEnabled.persistComponentAds(
                customAudience, CommonFixture.TEST_PACKAGE_NAME, mCustomAudienceDao);

        verify(mCustomAudienceDao)
                .insertAndOverwriteComponentAds(
                        customAudience.getComponentAds(),
                        CommonFixture.TEST_PACKAGE_NAME,
                        CommonFixture.VALID_BUYER_1,
                        customAudience.getName());
    }

    @Test
    public void testDisabledStrategyPersistComponentAdsDoesNothing() {
        CustomAudience customAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                        .setComponentAds(
                                ComponentAdDataFixture.getValidComponentAdsByBuyer(
                                        CommonFixture.VALID_BUYER_1))
                        .build();

        mComponentAdsStrategyDisabled.persistComponentAds(
                customAudience, CommonFixture.TEST_PACKAGE_NAME, mCustomAudienceDao);

        verifyZeroInteractions(mCustomAudienceDao);
    }
}
