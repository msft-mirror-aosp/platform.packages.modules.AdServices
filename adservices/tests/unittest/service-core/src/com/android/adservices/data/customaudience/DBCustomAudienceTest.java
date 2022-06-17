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

import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.customaudience.DBCustomAudienceFixture;

import org.junit.Test;

import java.time.Instant;

public class DBCustomAudienceTest {
    private static final String CALLING_APP_NAME = "not.impled.yet";

    @Test
    public void testFromServiceObject_passThrough() {
        assertEquals(
                DBCustomAudienceFixture.getValidBuilder()
                        .build(),
                DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilder()
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
                () -> DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilder().build(), null,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));
    }

    @Test
    public void testFromServiceObject_nullCurrentTime() {
        assertThrows(NullPointerException.class,
                () -> DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilder().build(), CALLING_APP_NAME,
                        null));
    }

    @Test
    public void testFromServiceObject_noAdsData_lastUpdatedSetToZero() {
        assertEquals(
                DBCustomAudienceFixture.getValidBuilder()
                        .setLastAdsAndBiddingDataUpdatedTime(Instant.EPOCH)
                        .setAds(null)
                        .build(),
                DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilder()
                                .setAds(null)
                                .build(),
                        CALLING_APP_NAME,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));
    }

    @Test
    public void testFromServiceObject_activationTimeBeforeCurrentTime_setToNow() {
        assertEquals(
                DBCustomAudienceFixture.getValidBuilder()
                        .setActivationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build(),
                DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilder()
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
                DBCustomAudienceFixture.getValidBuilder()
                        .setActivationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build(),
                DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilder()
                                .setActivationTime(null)
                                .build(),
                        CALLING_APP_NAME,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));
    }

    @Test
    public void testFromServiceObject_nullExpirationTime() {
        assertEquals(
                DBCustomAudienceFixture.getValidBuilder()
                        .setExpirationTime(
                                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI
                                        .plus(DBCustomAudience.getDefaultExpireIn()))
                        .build(),
                DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilder()
                                .setExpirationTime(null)
                                .build(),
                        CALLING_APP_NAME,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));
    }

    @Test
    public void testFromServiceObject_nullOwner() {
        assertEquals(
                DBCustomAudienceFixture.getValidBuilder()
                        .setOwner(CALLING_APP_NAME)
                        .build(),
                DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilder()
                                .setOwner(null)
                                .build(),
                        CALLING_APP_NAME,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));
    }

    @Test
    public void testFromServiceObject_activationTimeMoreThanMax() {
        assertThrows(IllegalArgumentException.class, () ->
                DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilder()
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
                        CustomAudienceFixture.getValidBuilder()
                                .setExpirationTime(
                                        CustomAudienceFixture.INVALID_BEYOND_MAX_EXPIRATION_TIME)
                                .build(),
                        CALLING_APP_NAME,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));
    }
}
