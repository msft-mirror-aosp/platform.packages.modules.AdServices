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

package com.android.adservices.service.shell;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.customaudience.DBTrustedBiddingDataFixture;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public class CustomAudienceHelperTest {

    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("example.com");
    private static final DBCustomAudience.Builder CUSTOM_AUDIENCE_BUILDER =
            DBCustomAudienceFixture.getValidBuilderByBuyer(BUYER);

    @Test
    public void testFromJson_happyPath() throws JSONException {
        JSONObject jsonObject =
                new JSONObject()
                        .put(CustomAudienceHelper.OWNER, CustomAudienceFixture.VALID_OWNER)
                        .put(CustomAudienceHelper.BUYER, BUYER)
                        .put(CustomAudienceHelper.NAME, CustomAudienceFixture.VALID_NAME)
                        .put(
                                CustomAudienceHelper.EXPIRATION_TIME,
                                CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .put(
                                CustomAudienceHelper.CREATION_TIME,
                                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .put(
                                CustomAudienceHelper.ACTIVATION_TIME,
                                CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .put(
                                CustomAudienceHelper.UPDATED_TIME,
                                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .put(
                                CustomAudienceHelper.USER_BIDDING_SIGNALS,
                                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .put(
                                CustomAudienceHelper.BIDDING_LOGIC_URI,
                                CustomAudienceFixture.getValidBiddingLogicUriByBuyer(BUYER))
                        .put(
                                CustomAudienceHelper.TRUSTED_BIDDING_DATA,
                                getFakeTrustedBiddingDataJson())
                        .put(CustomAudienceHelper.ADS, getFakeAdsJson())
                        .put(CustomAudienceHelper.IS_DEBUGGABLE, false);

        DBCustomAudience customAudience = CustomAudienceHelper.fromJson(jsonObject);

        assertThat(customAudience.getOwner()).isEqualTo(CustomAudienceFixture.VALID_OWNER);
        assertThat(customAudience.getBuyer()).isEqualTo(BUYER);
        assertThat(customAudience.getName()).isEqualTo(CustomAudienceFixture.VALID_NAME);
        assertThat(customAudience.getActivationTime()).isNotNull();
        assertThat(customAudience.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_EXPIRATION_TIME);
        assertThat(customAudience.getCreationTime())
                .isEqualTo(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI);
        assertThat(customAudience.getLastAdsAndBiddingDataUpdatedTime())
                .isEqualTo(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI);
        assertThat(customAudience.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
        assertThat(customAudience.getTrustedBiddingData())
                .isEqualTo(DBTrustedBiddingDataFixture.getValidBuilderByBuyer(BUYER).build());
        assertThat(customAudience.getBiddingLogicUri())
                .isEqualTo(CustomAudienceFixture.getValidBiddingLogicUriByBuyer(BUYER));
        assertThat(customAudience.getAds())
                .isEqualTo(DBAdDataFixture.getValidDbAdDataListByBuyer(BUYER));
    }

    @Test
    public void testToAndFromJson_happyPath_success() throws JSONException {
        DBCustomAudience customAudience = CUSTOM_AUDIENCE_BUILDER.build();

        JSONObject jsonObject = CustomAudienceHelper.toJson(customAudience);

        assertThat(customAudience).isEqualTo(CustomAudienceHelper.fromJson(jsonObject));
    }

    @Test
    public void testToJson_withNullTrustedBiddingData_fieldNotPresent() throws JSONException {
        DBCustomAudience customAudience =
                CUSTOM_AUDIENCE_BUILDER.setTrustedBiddingData(null).build();

        JSONObject jsonObject = CustomAudienceHelper.toJson(customAudience);

        assertThat(jsonObject.isNull(CustomAudienceHelper.TRUSTED_BIDDING_DATA)).isTrue();
    }

    @Test
    public void testToJson_withNullUserBiddingSignals_fieldNotPresent() throws JSONException {
        DBCustomAudience customAudience =
                CUSTOM_AUDIENCE_BUILDER.setUserBiddingSignals(null).build();

        JSONObject jsonObject = CustomAudienceHelper.toJson(customAudience);

        assertThat(jsonObject.isNull(CustomAudienceHelper.USER_BIDDING_SIGNALS)).isTrue();
    }

    @Test
    public void testToJson_withNullAds_arrayIsEmpty() throws JSONException {
        DBCustomAudience customAudience = CUSTOM_AUDIENCE_BUILDER.setAds(List.of()).build();

        JSONObject jsonObject = CustomAudienceHelper.toJson(customAudience);

        assertThat(jsonObject.getJSONArray(CustomAudienceHelper.ADS).length()).isEqualTo(0);
    }

    private static JSONArray getFakeAdsJson() throws JSONException {
        JSONArray ads = new JSONArray();
        for (DBAdData adData : DBAdDataFixture.getValidDbAdDataListByBuyer(BUYER)) {
            JSONObject jsonObject =
                    new JSONObject()
                            .put(CustomAudienceHelper.AD_AD_RENDER_URI, adData.getRenderUri())
                            .put(CustomAudienceHelper.AD_AD_RENDER_ID, adData.getAdRenderId())
                            .put(CustomAudienceHelper.AD_METADATA, adData.getMetadata());
            if (adData.getAdFilters() != null) {
                jsonObject.put(
                        CustomAudienceHelper.ADS_AD_FILTERS,
                        adData.getAdFilters().toJson().toString());
            }

            JSONArray adCounterKeys = new JSONArray();
            for (Integer i : adData.getAdCounterKeys()) {
                adCounterKeys.put(i);
            }
            jsonObject.put(CustomAudienceHelper.ADS_AD_COUNTER_KEYS, adCounterKeys);
            ads.put(jsonObject);
        }
        return ads;
    }

    private static JSONObject getFakeTrustedBiddingDataJson() throws JSONException {
        DBTrustedBiddingData trustedBiddingData =
                DBTrustedBiddingDataFixture.getValidBuilderByBuyer(BUYER).build();
        JSONObject jsonTrustedBiddingData =
                new JSONObject()
                        .put(
                                CustomAudienceHelper.TRUSTED_BIDDING_DATA_URI,
                                trustedBiddingData.getUri().toString());
        JSONArray trustedBiddingKeys = new JSONArray();
        trustedBiddingData.getKeys().forEach(trustedBiddingKeys::put);
        jsonTrustedBiddingData.put(CustomAudienceHelper.ADS_KEYS, trustedBiddingKeys);
        return jsonTrustedBiddingData;
    }
}
