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

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.customaudience.DBCustomAudience;

import org.junit.Test;

import java.util.List;

public class CustomAudienceWithComponentAdsTest extends AdServicesUnitTestCase {
    @Test
    public void testCreate_validInput() {
        DBCustomAudience dbCustomAudience =
                DBCustomAudienceFixture.VALID_DB_CUSTOM_AUDIENCE_NO_FILTERS;
        List<String> componentAdRenderIds = List.of("renderId1", "renderId2");

        CustomAudienceWithComponentAds customAudienceWithComponentAds =
                CustomAudienceWithComponentAds.create(dbCustomAudience, componentAdRenderIds);

        expect.that(customAudienceWithComponentAds.getDBCustomAudience())
                .isEqualTo(DBCustomAudienceFixture.VALID_DB_CUSTOM_AUDIENCE_NO_FILTERS);

        expect.that(customAudienceWithComponentAds.getComponentAdRenderIds())
                .containsExactlyElementsIn(componentAdRenderIds);
    }
}
