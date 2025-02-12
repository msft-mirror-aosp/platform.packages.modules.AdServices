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

package android.adservices.common;

import static com.android.adservices.service.Flags.COMPONENT_AD_RENDER_ID_MAX_LENGTH_BYTES;
import static com.android.adservices.service.Flags.MAX_COMPONENT_ADS_PER_CUSTOM_AUDIENCE;

import android.net.Uri;

import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.customaudience.ComponentAdsListValidator;
import com.android.adservices.service.customaudience.CustomAudienceWithComponentAds;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Utility class supporting ad services API unit tests */
public final class ComponentAdDataFixture {
    public static final ComponentAdsListValidator TEST_COMPONENT_ADS_FILTERER =
            new ComponentAdsListValidator(
                    COMPONENT_AD_RENDER_ID_MAX_LENGTH_BYTES, MAX_COMPONENT_ADS_PER_CUSTOM_AUDIENCE);

    private ComponentAdDataFixture() {}

    /**
     * @return a valid render uri for a specified buyer and sequence number
     */
    public static Uri getValidRenderUriByBuyer(AdTechIdentifier buyer, int sequence) {
        return CommonFixture.getUri(buyer, "/testing/hello" + sequence);
    }

    /**
     * @return a valid list of component ads for a specified buyer.
     */
    public static List<ComponentAdData> getValidComponentAdsByBuyer(AdTechIdentifier buyer) {
        return ImmutableList.of(
                getValidComponentAdDataByBuyer(buyer, 1),
                getValidComponentAdDataByBuyer(buyer, 2),
                getValidComponentAdDataByBuyer(buyer, 3),
                getValidComponentAdDataByBuyer(buyer, 4));
    }

    /**
     * @return a valid list of component ads with the specified ad render ids.
     */
    public static List<ComponentAdData> getValidComponentAdsByBuyerAndRenderId(
            AdTechIdentifier buyer, List<String> adRenderIds) {
        List<ComponentAdData> result = new ArrayList<>();
        for (int i = 0; i < adRenderIds.size(); i++) {
            result.add(getValidComponentAdDataWithAdRenderId(buyer, i, adRenderIds.get(i)));
        }
        return result;
    }

    /**
     * @return a component ad for a specified buyer.
     */
    public static ComponentAdData getValidComponentAdDataByBuyer(
            AdTechIdentifier buyer, int sequenceNumber) {
        return new ComponentAdData(
                getValidRenderUriByBuyer(buyer, sequenceNumber),
                AdDataFixture.VALID_RENDER_ID + sequenceNumber);
    }

    /**
     * @return a component ad with a specified render id.
     */
    public static ComponentAdData getValidComponentAdDataWithAdRenderId(
            AdTechIdentifier buyer, int sequenceNumber, String adRenderId) {
        return new ComponentAdData(getValidRenderUriByBuyer(buyer, sequenceNumber), adRenderId);
    }

    /** Creates a list of {@link CustomAudienceWithComponentAds} with empty component ads. */
    public static List<CustomAudienceWithComponentAds> getCustomAudiencesWithEmptyComponentAds(
            List<DBCustomAudience> dbCustomAudiences) {
        return dbCustomAudiences.stream()
                .map(
                        dbCustomAudience ->
                                CustomAudienceWithComponentAds.create(dbCustomAudience, List.of()))
                .collect(Collectors.toList());
    }
}
