/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.data.customaudience;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.AdDataFixture;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.TrustedBiddingDataFixture;

import com.android.adservices.data.common.DBAdData;

import org.junit.Test;

import java.time.Instant;
import java.util.stream.Collectors;

public class DBCustomAudienceTest {
    private static final String CALLING_APP_NAME = "not.impled.yet";

    @Test
    public void testFromServiceObject_passThrough() {
        assertEquals(
                getDBSchemaBuilderWithDefaultValidValue()
                        .build(),
                DBCustomAudience.fromServiceObject(
                        getBuilderWithDefaultValidValue()
                                .build(),
                        CALLING_APP_NAME,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));
    }

    @Test
    public void testFromServiceObject_nullCustomAudience() {
        assertThrows(NullPointerException.class,
                () -> DBCustomAudience.fromServiceObject(null, CALLING_APP_NAME,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));
    }

    @Test
    public void testFromServiceObject_nullCallingAppName() {
        assertThrows(NullPointerException.class,
                () -> DBCustomAudience.fromServiceObject(getBuilderWithDefaultValidValue().build(),
                        null, CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));
    }

    @Test
    public void testFromServiceObject_nullCurrentTime() {
        assertThrows(NullPointerException.class,
                () -> DBCustomAudience.fromServiceObject(getBuilderWithDefaultValidValue().build(),
                        CALLING_APP_NAME, null));
    }

    @Test
    public void testFromServiceObject_noAdsData_lastUpdatedSetToZero() {
        assertEquals(
                getDBSchemaBuilderWithDefaultValidValue()
                        .setLastAdsAndBiddingDataUpdatedTime(Instant.EPOCH)
                        .setAds(null)
                        .build(),
                DBCustomAudience.fromServiceObject(
                        getBuilderWithDefaultValidValue()
                                .setAds(null)
                                .build(),
                        CALLING_APP_NAME,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));
    }

    @Test
    public void testFromServiceObject_activationTimeBeforeCurrentTime_setToNow() {
        assertEquals(
                getDBSchemaBuilderWithDefaultValidValue()
                        .setActivationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build(),
                DBCustomAudience.fromServiceObject(
                        getBuilderWithDefaultValidValue()
                                .setActivationTime(
                                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.minusSeconds(
                                                200))
                                .build(),
                        CALLING_APP_NAME,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));
    }

    @Test
    public void testFromServiceObject_nullActivationTime() {
        assertEquals(
                getDBSchemaBuilderWithDefaultValidValue()
                        .setActivationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build(),
                DBCustomAudience.fromServiceObject(
                        getBuilderWithDefaultValidValue()
                                .setActivationTime(null)
                                .build(),
                        CALLING_APP_NAME,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));
    }

    @Test
    public void testFromServiceObject_nullExpirationTime() {
        assertEquals(
                getDBSchemaBuilderWithDefaultValidValue()
                        .setExpirationTime(
                                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI
                                        .plus(DBCustomAudience.getDefaultExpireIn()))
                        .build(),
                DBCustomAudience.fromServiceObject(
                        getBuilderWithDefaultValidValue()
                                .setExpirationTime(null)
                                .build(),
                        CALLING_APP_NAME,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));
    }

    @Test
    public void testFromServiceObject_nullOwner() {
        assertEquals(
                getDBSchemaBuilderWithDefaultValidValue()
                        .setOwner(CALLING_APP_NAME)
                        .build(),
                DBCustomAudience.fromServiceObject(
                        getBuilderWithDefaultValidValue()
                                .setOwner(null)
                                .build(),
                        CALLING_APP_NAME,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));
    }

    @Test
    public void testFromServiceObject_activationTimeMoreThanMax() {
        assertThrows(IllegalArgumentException.class, () ->
                DBCustomAudience.fromServiceObject(
                        getBuilderWithDefaultValidValue()
                                .setActivationTime(
                                        CustomAudienceFixture.INVALID_DELAYED_ACTIVATION_TIME)
                                .build(),
                        CALLING_APP_NAME,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));
    }

    @Test
    public void testFromServiceObject_expirationTimeMoreThanMax() {
        assertThrows(IllegalArgumentException.class, () ->
                DBCustomAudience.fromServiceObject(
                        getBuilderWithDefaultValidValue()
                                .setExpirationTime(
                                        CustomAudienceFixture.INVALID_BEYOND_MAX_EXPIRATION_TIME)
                                .build(),
                        CALLING_APP_NAME,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));
    }

    private static CustomAudience.Builder getBuilderWithDefaultValidValue() {
        return new CustomAudience.Builder()
                .setOwner(CustomAudienceFixture.VALID_OWNER)
                .setBuyer(CustomAudienceFixture.VALID_BUYER)
                .setName(CustomAudienceFixture.VALID_NAME)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setDailyUpdateUrl(CustomAudienceFixture.VALID_DAILY_UPDATE_URL)
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_DATA)
                .setBiddingLogicUrl(CustomAudienceFixture.VALID_BIDDING_LOGIC_URL)
                .setAds(AdDataFixture.VALID_ADS);
    }

    private static DBCustomAudience.Builder getDBSchemaBuilderWithDefaultValidValue() {
        return new DBCustomAudience.Builder()
                .setOwner(CustomAudienceFixture.VALID_OWNER)
                .setBuyer(CustomAudienceFixture.VALID_BUYER)
                .setName(CustomAudienceFixture.VALID_NAME)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setLastAdsAndBiddingDataUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setDailyUpdateUrl(CustomAudienceFixture.VALID_DAILY_UPDATE_URL)
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(new DBTrustedBiddingData.Builder()
                        .setUrl(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_URL)
                        .setKeys(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_KEYS)
                        .build())
                .setBiddingLogicUrl(CustomAudienceFixture.VALID_BIDDING_LOGIC_URL)
                .setAds(AdDataFixture.VALID_ADS.stream()
                        .map(DBAdData::fromServiceObject)
                        .collect(Collectors.toList()));
    }
}
