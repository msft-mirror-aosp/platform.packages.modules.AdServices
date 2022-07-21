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

import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.customaudience.DBTrustedBiddingDataFixture;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.customaudience.CustomAudienceUpdatableData;
import com.android.adservices.service.customaudience.CustomAudienceUpdatableDataFixture;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DBCustomAudienceTest {
    private static final String CALLING_APP_NAME = "not.impled.yet";
    private static final Flags FLAGS = FlagsFactory.getFlagsForTest();
    private static final Duration DEFAULT_EXPIRE_IN =
            Duration.ofMillis(FLAGS.getFledgeCustomAudienceDefaultExpireInMs());

    @Test
    public void testFromServiceObject_passThrough() {
        assertEquals(
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER).build(),
                DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER)
                                .build(),
                        CALLING_APP_NAME,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        DEFAULT_EXPIRE_IN));
    }

    @Test
    public void testFromServiceObject_nullCustomAudience() {
        assertThrows(
                NullPointerException.class,
                () ->
                        DBCustomAudience.fromServiceObject(
                                null,
                                CALLING_APP_NAME,
                                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                                DEFAULT_EXPIRE_IN));
    }

    @Test
    public void testFromServiceObject_nullCallingAppName() {
        assertThrows(
                NullPointerException.class,
                () ->
                        DBCustomAudience.fromServiceObject(
                                CustomAudienceFixture.getValidBuilderForBuyer(
                                                CommonFixture.VALID_BUYER)
                                        .build(),
                                null,
                                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                                DEFAULT_EXPIRE_IN));
    }

    @Test
    public void testFromServiceObject_nullCurrentTime() {
        assertThrows(
                NullPointerException.class,
                () ->
                        DBCustomAudience.fromServiceObject(
                                CustomAudienceFixture.getValidBuilderForBuyer(
                                                CommonFixture.VALID_BUYER)
                                        .build(),
                                CALLING_APP_NAME,
                                null,
                                DEFAULT_EXPIRE_IN));
    }

    @Test
    public void testFromServiceObject_nullDefaultExpireIn() {
        assertThrows(
                NullPointerException.class,
                () ->
                        DBCustomAudience.fromServiceObject(
                                CustomAudienceFixture.getValidBuilderForBuyer(
                                                CommonFixture.VALID_BUYER)
                                        .build(),
                                CALLING_APP_NAME,
                                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                                null));
    }

    @Test
    public void testFromServiceObject_noAdsData_lastUpdatedSetToZero() {
        assertEquals(
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER)
                        .setLastAdsAndBiddingDataUpdatedTime(Instant.EPOCH)
                        .setAds(null)
                        .build(),
                DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER)
                                .setAds(null)
                                .build(),
                        CALLING_APP_NAME,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        DEFAULT_EXPIRE_IN));
    }

    @Test
    public void testFromServiceObject_activationTimeBeforeCurrentTime_setToNow() {
        assertEquals(
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER)
                        .setActivationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build(),
                DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER)
                                .setActivationTime(
                                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.minusSeconds(
                                                200))
                                .build(),
                        CALLING_APP_NAME,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        DEFAULT_EXPIRE_IN));
    }

    @Test
    public void testFromServiceObject_nullActivationTime() {
        assertEquals(
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER)
                        .setActivationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build(),
                DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER)
                                .setActivationTime(null)
                                .build(),
                        CALLING_APP_NAME,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        DEFAULT_EXPIRE_IN));
    }

    @Test
    public void testFromServiceObject_nullExpirationTime() {
        assertEquals(
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER)
                        .setExpirationTime(
                                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.plus(DEFAULT_EXPIRE_IN))
                        .build(),
                DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER)
                                .setExpirationTime(null)
                                .build(),
                        CALLING_APP_NAME,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        DEFAULT_EXPIRE_IN));
    }

    @Test
    public void testFromServiceObject_nullOwner() {
        assertEquals(
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER)
                        .setOwner(CALLING_APP_NAME)
                        .build(),
                DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER)
                                .setOwner(null)
                                .build(),
                        CALLING_APP_NAME,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        DEFAULT_EXPIRE_IN));
    }

    @Test
    public void testCopyWithNullUpdatableDataThrowsException() {
        DBCustomAudience customAudience =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER).build();

        assertThrows(NullPointerException.class, () -> customAudience.copyWithUpdatableData(null));
    }

    @Test
    public void testCopyWithUnsuccessfulUpdatableDataDoesNotChange() {
        Instant originalUpdateTime = CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI;
        DBCustomAudience originalCustomAudience =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER)
                        .setLastAdsAndBiddingDataUpdatedTime(originalUpdateTime)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .setTrustedBiddingData(
                                DBTrustedBiddingDataFixture.getValidBuilderByBuyer(
                                                CommonFixture.VALID_BUYER)
                                        .build())
                        .setAds(
                                DBAdDataFixture.getValidDbAdDataListByBuyer(
                                        CommonFixture.VALID_BUYER))
                        .build();

        Instant attemptedUpdateTime = originalUpdateTime.plusSeconds(10);
        CustomAudienceUpdatableData updatableData =
                CustomAudienceUpdatableDataFixture.getValidBuilderEmptyFailedResponse()
                        .setAttemptedUpdateTime(attemptedUpdateTime)
                        .build();

        DBCustomAudience updatedCustomAudience =
                originalCustomAudience.copyWithUpdatableData(updatableData);

        assertEquals(originalCustomAudience, updatedCustomAudience);
    }

    @Test
    public void testCopyWithSuccessfulEmptyUpdatableDataOnlyUpdatesTime() {
        Instant originalUpdateTime = CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI;
        DBCustomAudience originalCustomAudience =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER)
                        .setLastAdsAndBiddingDataUpdatedTime(originalUpdateTime)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .setTrustedBiddingData(
                                DBTrustedBiddingDataFixture.getValidBuilderByBuyer(
                                                CommonFixture.VALID_BUYER)
                                        .build())
                        .setAds(
                                DBAdDataFixture.getValidDbAdDataListByBuyer(
                                        CommonFixture.VALID_BUYER))
                        .build();

        Instant attemptedUpdateTime = originalUpdateTime.plusSeconds(10);
        CustomAudienceUpdatableData updatableData =
                CustomAudienceUpdatableDataFixture.getValidBuilderEmptySuccessfulResponse()
                        .setAttemptedUpdateTime(attemptedUpdateTime)
                        .build();

        DBCustomAudience expectedCustomAudience =
                new DBCustomAudience.Builder(originalCustomAudience)
                        .setLastAdsAndBiddingDataUpdatedTime(attemptedUpdateTime)
                        .build();

        DBCustomAudience updatedCustomAudience =
                originalCustomAudience.copyWithUpdatableData(updatableData);

        assertEquals(expectedCustomAudience, updatedCustomAudience);
    }

    @Test
    public void testCopyWithSuccessfulFullUpdatableDataUpdatesAll() {
        Instant originalUpdateTime = CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI;
        DBCustomAudience originalCustomAudience =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER)
                        .setLastAdsAndBiddingDataUpdatedTime(originalUpdateTime)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .setTrustedBiddingData(
                                DBTrustedBiddingDataFixture.getValidBuilderByBuyer(
                                                CommonFixture.VALID_BUYER)
                                        .build())
                        .setAds(
                                DBAdDataFixture.getValidDbAdDataListByBuyer(
                                        CommonFixture.VALID_BUYER))
                        .build();

        Instant attemptedUpdateTime = originalUpdateTime.plusSeconds(10);
        String updatedUserBiddingSignals = "{'new':1}";
        DBTrustedBiddingData updatedTrustedBiddingData =
                DBTrustedBiddingDataFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER)
                        .setKeys(Arrays.asList("new", "updated"))
                        .build();
        List<DBAdData> updatedAds = Collections.emptyList();
        CustomAudienceUpdatableData updatableData =
                CustomAudienceUpdatableDataFixture.getValidBuilderEmptySuccessfulResponse()
                        .setAttemptedUpdateTime(attemptedUpdateTime)
                        .setUserBiddingSignals(updatedUserBiddingSignals)
                        .setTrustedBiddingData(updatedTrustedBiddingData)
                        .setAds(updatedAds)
                        .build();

        DBCustomAudience expectedCustomAudience =
                new DBCustomAudience.Builder(originalCustomAudience)
                        .setLastAdsAndBiddingDataUpdatedTime(attemptedUpdateTime)
                        .setUserBiddingSignals(updatedUserBiddingSignals)
                        .setTrustedBiddingData(updatedTrustedBiddingData)
                        .setAds(updatedAds)
                        .build();

        DBCustomAudience updatedCustomAudience =
                originalCustomAudience.copyWithUpdatableData(updatableData);

        assertEquals(expectedCustomAudience, updatedCustomAudience);
    }
}
