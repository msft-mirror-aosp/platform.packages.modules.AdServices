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

import static android.adservices.common.CommonFixture.VALID_BUYER_1;
import static android.adservices.common.CommonFixture.VALID_BUYER_2;

import static com.android.adservices.service.Flags.COMPONENT_AD_RENDER_ID_MAX_LENGTH_BYTES;
import static com.android.adservices.service.Flags.MAX_COMPONENT_ADS_PER_CUSTOM_AUDIENCE;

import android.adservices.common.AdDataFixture;
import android.adservices.common.CommonFixture;
import android.adservices.common.ComponentAdData;
import android.adservices.common.ComponentAdDataFixture;
import android.net.Uri;

import com.android.adservices.common.AdServicesMockitoTestCase;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class ComponentAdsListValidatorTest extends AdServicesMockitoTestCase {

    private ComponentAdsListValidator mComponentAdsListValidator;

    @Before
    public void setup() {
        mComponentAdsListValidator =
                new ComponentAdsListValidator(
                        COMPONENT_AD_RENDER_ID_MAX_LENGTH_BYTES,
                        MAX_COMPONENT_ADS_PER_CUSTOM_AUDIENCE);
    }

    @Test
    public void testFilterComponentAdsAllAdsValid() {
        List<ComponentAdData> componentAdDataList =
                ComponentAdDataFixture.getValidComponentAdsByBuyer(VALID_BUYER_1);

        expect.that(
                        mComponentAdsListValidator.extractValidComponentAds(
                                VALID_BUYER_1, componentAdDataList))
                .containsExactlyElementsIn(componentAdDataList)
                .inOrder();
    }

    @Test
    public void testFilterComponentAds_incorrectBuyer_filteredOut() throws Exception {
        List<ComponentAdData> componentAds = new ArrayList<>();

        ComponentAdData validComponentAd =
                ComponentAdDataFixture.getValidComponentAdDataByBuyer(VALID_BUYER_1, 0);

        componentAds.add(validComponentAd);
        componentAds.add(ComponentAdDataFixture.getValidComponentAdDataByBuyer(VALID_BUYER_2, 1));

        List<ComponentAdData> filteredAds =
                mComponentAdsListValidator.extractValidComponentAds(VALID_BUYER_1, componentAds);

        expect.that(filteredAds).hasSize(1);
        expect.that(filteredAds).containsExactlyElementsIn(List.of(validComponentAd));
    }

    @Test
    public void testFilterComponentAds_inValidUri_filteredOut() throws Exception {
        List<ComponentAdData> componentAds = new ArrayList<>();

        ComponentAdData validComponentAd =
                ComponentAdDataFixture.getValidComponentAdDataByBuyer(VALID_BUYER_1, 0);

        componentAds.add(validComponentAd);
        componentAds.add(
                new ComponentAdData(
                        Uri.parse("I'm an invalid uri"), AdDataFixture.VALID_RENDER_ID));

        List<ComponentAdData> filteredAds =
                mComponentAdsListValidator.extractValidComponentAds(VALID_BUYER_1, componentAds);

        expect.that(filteredAds).hasSize(1);
        expect.that(filteredAds).containsExactlyElementsIn(List.of(validComponentAd));
    }

    @Test
    public void testFilterComponentAds_tooLongRenderId_filteredOut() throws Exception {
        List<ComponentAdData> componentAds = new ArrayList<>();

        ComponentAdData validComponentAd =
                ComponentAdDataFixture.getValidComponentAdDataByBuyer(
                        CommonFixture.VALID_BUYER_1, 0);

        componentAds.add(validComponentAd);
        componentAds.add(
                ComponentAdDataFixture.getValidComponentAdDataWithAdRenderId(
                        CommonFixture.VALID_BUYER_1, 1, "I am extremely loooooong"));

        List<ComponentAdData> filteredAds =
                mComponentAdsListValidator.extractValidComponentAds(VALID_BUYER_1, componentAds);

        expect.that(filteredAds).hasSize(1);
        expect.that(filteredAds).containsExactlyElementsIn(List.of(validComponentAd));
    }

    @Test
    public void testFilterComponentAds_ReturnsEmptyListWithTooManyComponentAds() throws Exception {
        ComponentAdsListValidator componentAdsListValidator =
                new ComponentAdsListValidator(
                        COMPONENT_AD_RENDER_ID_MAX_LENGTH_BYTES, /* maxComponentAds= */ 1);

        ComponentAdData componentAdData1 =
                ComponentAdDataFixture.getValidComponentAdDataByBuyer(
                        CommonFixture.VALID_BUYER_1, 0);
        ComponentAdData componentAdData2 =
                ComponentAdDataFixture.getValidComponentAdDataByBuyer(
                        CommonFixture.VALID_BUYER_1, 1);

        List<ComponentAdData> filteredAds =
                componentAdsListValidator.extractValidComponentAds(
                        VALID_BUYER_1, List.of(componentAdData1, componentAdData2));

        expect.that(filteredAds).isEmpty();
    }
}
