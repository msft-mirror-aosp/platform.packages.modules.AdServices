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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.common.DBAdData;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.customaudience.BackgroundFetchRunner;
import com.android.adservices.service.customaudience.CustomAudienceUpdatableData;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoSession;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CustomAudienceDaoTest {
    private static final Flags TEST_FLAGS = FlagsFactory.getFlagsForTest();

    private static final Uri DAILY_UPDATE_URL_1 = Uri.parse("https://www.example.com/d1");
    private static final String USER_BIDDING_SIGNALS_1 = "ExampleBiddingSignal1";
    private static final Uri AD_DATA_RENDER_URL_1 = Uri.parse("https://www.example.com/a1");
    private static final String AD_DATA_METADATA_1 = "meta1";
    private static final DBAdData ADS_1 =
            new DBAdData.Builder()
                    .setRenderUri(AD_DATA_RENDER_URL_1)
                    .setMetadata(AD_DATA_METADATA_1)
                    .build();
    private static final Uri DAILY_UPDATE_URL_2 = Uri.parse("https://www.example.com/d2");
    private static final String USER_BIDDING_SIGNALS_2 = "ExampleBiddingSignal2";
    private static final Uri AD_DATA_RENDER_URL_2 = Uri.parse("https://www.example.com/a2");
    private static final String AD_DATA_METADATA_2 = "meta2";
    private static final DBAdData ADS_2 =
            new DBAdData.Builder()
                    .setRenderUri(AD_DATA_RENDER_URL_2)
                    .setMetadata(AD_DATA_METADATA_2)
                    .build();
    private static final Uri TRUSTED_BIDDING_DATA_URL_2 = Uri.parse("https://www.example.com/t1");
    private static final List<String> TRUSTED_BIDDING_DATA_KEYS_2 =
            Collections.singletonList("key2");
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    private static final Instant CURRENT_TIME = CLOCK.instant();
    private static final Instant CREATION_TIME_1 = CURRENT_TIME.truncatedTo(ChronoUnit.MILLIS);
    private static final Instant ACTIVATION_TIME_1 =
            CURRENT_TIME.plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MILLIS);
    private static final Instant EXPIRATION_TIME_1 =
            CURRENT_TIME.plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.MILLIS);
    private static final Instant LAST_UPDATED_TIME_1 = CURRENT_TIME.truncatedTo(ChronoUnit.MILLIS);
    private static final Instant LAST_UPDATED_TIME_36_HRS =
            CURRENT_TIME.minus(Duration.ofHours(36)).truncatedTo(ChronoUnit.MILLIS);
    private static final Instant LAST_UPDATED_TIME_72_HRS =
            CURRENT_TIME.minus(Duration.ofHours(72)).truncatedTo(ChronoUnit.MILLIS);
    private static final Instant CREATION_TIME_2 =
            CURRENT_TIME.plus(Duration.ofMinutes(1)).truncatedTo(ChronoUnit.MILLIS);
    private static final Instant ACTIVATION_TIME_2 =
            CURRENT_TIME
                    .plus(Duration.ofDays(1))
                    .plus(Duration.ofMinutes(1))
                    .truncatedTo(ChronoUnit.MILLIS);
    private static final Instant EXPIRATION_TIME_2 =
            CURRENT_TIME
                    .plus(Duration.ofDays(3))
                    .plus(Duration.ofMinutes(1))
                    .truncatedTo(ChronoUnit.MILLIS);
    private static final Instant LAST_UPDATED_TIME_2 =
            CURRENT_TIME.plus(Duration.ofMinutes(1)).truncatedTo(ChronoUnit.MILLIS);
    private static final Instant ACTIVATION_TIME_MINUS_ONE_HOUR =
            CURRENT_TIME.minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MILLIS);
    private static final Instant CREATION_TIME_MINUS_THREE_DAYS =
            CURRENT_TIME.minus(Duration.ofDays(3)).truncatedTo(ChronoUnit.MILLIS);
    private static final Instant EXPIRATION_TIME_MINUS_ONE_DAY =
            CURRENT_TIME.minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MILLIS);
    private static final String OWNER_1 = "owner1";
    private static final String OWNER_2 = "owner2";
    private static final String OWNER_3 = "owner3";
    private static final String BUYER_1 = "buyer1";
    private static final String BUYER_2 = "buyer2";
    private static final String BUYER_3 = "buyer3";
    private static final String NAME_1 = "name1";
    private static final String NAME_2 = "name2";
    private static final String NAME_3 = "name3";
    private static final Uri BIDDING_LOGIC_URL_1 = Uri.parse("https://www.example.com/e1");
    private static final Uri BIDDING_LOGIC_URL_2 = Uri.parse("https://www.example.com/e2");
    private static final DBTrustedBiddingData TRUSTED_BIDDING_DATA_2 =
            new DBTrustedBiddingData.Builder()
                    .setUrl(TRUSTED_BIDDING_DATA_URL_2)
                    .setKeys(TRUSTED_BIDDING_DATA_KEYS_2)
                    .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_1 =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setActivationTime(ACTIVATION_TIME_1)
                    .setCreationTime(CREATION_TIME_1)
                    .setExpirationTime(EXPIRATION_TIME_1)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_1)
                    .setBiddingLogicUrl(BIDDING_LOGIC_URL_1)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_1)
                    .setAds(List.of(ADS_1))
                    .setTrustedBiddingData(null)
                    .build();

    private static final DBCustomAudienceBackgroundFetchData CUSTOM_AUDIENCE_BGF_DATA_1 =
            DBCustomAudienceBackgroundFetchData.builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setDailyUpdateUrl(DAILY_UPDATE_URL_1)
                    .setEligibleUpdateTime(
                            DBCustomAudienceBackgroundFetchData
                                    .computeNextEligibleUpdateTimeAfterSuccessfulUpdate(
                                            CREATION_TIME_1, TEST_FLAGS))
                    .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_1_1 =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setActivationTime(ACTIVATION_TIME_1)
                    .setCreationTime(CREATION_TIME_2)
                    .setExpirationTime(EXPIRATION_TIME_2)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_2)
                    .setBiddingLogicUrl(BIDDING_LOGIC_URL_2)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_1)
                    .setAds(List.of(ADS_1))
                    .setTrustedBiddingData(null)
                    .build();

    private static final DBCustomAudienceBackgroundFetchData CUSTOM_AUDIENCE_BGF_DATA_1_1 =
            DBCustomAudienceBackgroundFetchData.builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setDailyUpdateUrl(DAILY_UPDATE_URL_1)
                    .setEligibleUpdateTime(
                            DBCustomAudienceBackgroundFetchData
                                    .computeNextEligibleUpdateTimeAfterSuccessfulUpdate(
                                            CREATION_TIME_2, TEST_FLAGS))
                    .build();

    private static final DBCustomAudienceBackgroundFetchData CUSTOM_AUDIENCE_BGF_DATA_1_UPDATED =
            DBCustomAudienceBackgroundFetchData.builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setDailyUpdateUrl(DAILY_UPDATE_URL_1)
                    .setEligibleUpdateTime(
                            DBCustomAudienceBackgroundFetchData
                                    .computeNextEligibleUpdateTimeAfterSuccessfulUpdate(
                                            LAST_UPDATED_TIME_2, TEST_FLAGS))
                    .build();

    private static final CustomAudienceUpdatableData CUSTOM_AUDIENCE_UPDATABLE_DATA =
            CustomAudienceUpdatableData.builder()
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_2)
                    .setTrustedBiddingData(TRUSTED_BIDDING_DATA_2)
                    .setAds(List.of(ADS_2))
                    .setAttemptedUpdateTime(LAST_UPDATED_TIME_2)
                    .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                    .setContainsSuccessfulUpdate(true)
                    .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_1_UPDATED_FROM_UPDATABLE_DATA =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setActivationTime(ACTIVATION_TIME_1)
                    .setCreationTime(CREATION_TIME_1)
                    .setExpirationTime(EXPIRATION_TIME_1)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_2)
                    .setBiddingLogicUrl(BIDDING_LOGIC_URL_1)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_2)
                    .setAds(List.of(ADS_2))
                    .setTrustedBiddingData(TRUSTED_BIDDING_DATA_2)
                    .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_2 =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_2)
                    .setBuyer(BUYER_2)
                    .setName(NAME_2)
                    .setActivationTime(ACTIVATION_TIME_2)
                    .setCreationTime(CREATION_TIME_2)
                    .setExpirationTime(EXPIRATION_TIME_2)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_2)
                    .setBiddingLogicUrl(BIDDING_LOGIC_URL_2)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_2)
                    .setAds(List.of(ADS_2))
                    .setTrustedBiddingData(TRUSTED_BIDDING_DATA_2)
                    .build();

    private static final DBCustomAudienceBackgroundFetchData CUSTOM_AUDIENCE_BGF_DATA_2 =
            DBCustomAudienceBackgroundFetchData.builder()
                    .setOwner(OWNER_2)
                    .setBuyer(BUYER_2)
                    .setName(NAME_2)
                    .setDailyUpdateUrl(DAILY_UPDATE_URL_2)
                    .setEligibleUpdateTime(
                            DBCustomAudienceBackgroundFetchData
                                    .computeNextEligibleUpdateTimeAfterSuccessfulUpdate(
                                            CREATION_TIME_2, TEST_FLAGS))
                    .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_INACTIVE =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setActivationTime(ACTIVATION_TIME_1)
                    .setCreationTime(CREATION_TIME_1)
                    .setExpirationTime(EXPIRATION_TIME_1)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_1)
                    .setBiddingLogicUrl(BIDDING_LOGIC_URL_1)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_1)
                    .setAds(List.of(ADS_1))
                    .setTrustedBiddingData(TRUSTED_BIDDING_DATA_2)
                    .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_ACTIVE =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setActivationTime(ACTIVATION_TIME_MINUS_ONE_HOUR)
                    .setCreationTime(CREATION_TIME_MINUS_THREE_DAYS)
                    .setExpirationTime(EXPIRATION_TIME_1)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_1)
                    .setBiddingLogicUrl(BIDDING_LOGIC_URL_1)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_1)
                    .setAds(List.of(ADS_1))
                    .setTrustedBiddingData(TRUSTED_BIDDING_DATA_2)
                    .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_EXPIRED =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_2)
                    .setBuyer(BUYER_2)
                    .setName(NAME_2)
                    .setActivationTime(ACTIVATION_TIME_MINUS_ONE_HOUR)
                    .setCreationTime(CREATION_TIME_MINUS_THREE_DAYS)
                    .setExpirationTime(EXPIRATION_TIME_MINUS_ONE_DAY)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_2)
                    .setBiddingLogicUrl(BIDDING_LOGIC_URL_2)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_2)
                    .setAds(List.of(ADS_2))
                    .setTrustedBiddingData(TRUSTED_BIDDING_DATA_2)
                    .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_UPDATED =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setActivationTime(ACTIVATION_TIME_MINUS_ONE_HOUR)
                    .setCreationTime(CREATION_TIME_MINUS_THREE_DAYS)
                    .setExpirationTime(EXPIRATION_TIME_1)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_36_HRS)
                    .setBiddingLogicUrl(BIDDING_LOGIC_URL_1)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_1)
                    .setAds(List.of(ADS_1))
                    .setTrustedBiddingData(TRUSTED_BIDDING_DATA_2)
                    .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_OUTDATED =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_2)
                    .setBuyer(BUYER_2)
                    .setName(NAME_2)
                    .setActivationTime(ACTIVATION_TIME_MINUS_ONE_HOUR)
                    .setCreationTime(CREATION_TIME_MINUS_THREE_DAYS)
                    .setExpirationTime(EXPIRATION_TIME_1)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_72_HRS)
                    .setBiddingLogicUrl(BIDDING_LOGIC_URL_1)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_1)
                    .setAds(List.of(ADS_1))
                    .setTrustedBiddingData(TRUSTED_BIDDING_DATA_2)
                    .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_NO_USER_BIDDING_SIGNALS =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setActivationTime(ACTIVATION_TIME_MINUS_ONE_HOUR)
                    .setCreationTime(CREATION_TIME_MINUS_THREE_DAYS)
                    .setExpirationTime(EXPIRATION_TIME_1)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_1)
                    .setBiddingLogicUrl(BIDDING_LOGIC_URL_1)
                    .setUserBiddingSignals(null)
                    .setAds(List.of(ADS_1))
                    .setTrustedBiddingData(TRUSTED_BIDDING_DATA_2)
                    .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_NO_TRUSTED_BIDDING_DATA_URL =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_2)
                    .setBuyer(BUYER_2)
                    .setName(NAME_2)
                    .setActivationTime(ACTIVATION_TIME_MINUS_ONE_HOUR)
                    .setCreationTime(CREATION_TIME_MINUS_THREE_DAYS)
                    .setExpirationTime(EXPIRATION_TIME_1)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_1)
                    .setBiddingLogicUrl(BIDDING_LOGIC_URL_1)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_1)
                    .setAds(List.of(ADS_1))
                    .setTrustedBiddingData(null)
                    .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_NO_ADS =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_3)
                    .setBuyer(BUYER_3)
                    .setName(NAME_3)
                    .setActivationTime(ACTIVATION_TIME_MINUS_ONE_HOUR)
                    .setCreationTime(CREATION_TIME_MINUS_THREE_DAYS)
                    .setExpirationTime(EXPIRATION_TIME_1)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_1)
                    .setBiddingLogicUrl(BIDDING_LOGIC_URL_1)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_1)
                    .setAds(null)
                    .setTrustedBiddingData(TRUSTED_BIDDING_DATA_2)
                    .build();

    private static final String APP_PACKAGE_NAME_1 = "appPackageName1";
    private static final String BIDDING_LOGIC_JS_1 =
            "function test() { return \"hello world_1\"; }";
    private static final String TRUSTED_BIDDING_OVERRIDE_DATA_1 = "{\"trusted_bidding_data\":1}";
    public static final DBCustomAudienceOverride DB_CUSTOM_AUDIENCE_OVERRIDE_1 =
            DBCustomAudienceOverride.builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setAppPackageName(APP_PACKAGE_NAME_1)
                    .setBiddingLogicJS(BIDDING_LOGIC_JS_1)
                    .setTrustedBiddingData(TRUSTED_BIDDING_OVERRIDE_DATA_1)
                    .build();

    private static final String APP_PACKAGE_NAME_2 = "appPackageName2";
    private static final String BIDDING_LOGIC_JS_2 =
            "function test() { return \"hello world_2\"; }";
    private static final String TRUSTED_BIDDING_OVERRIDE_DATA_2 = "{\"trusted_bidding_data\":2}";
    public static final DBCustomAudienceOverride DB_CUSTOM_AUDIENCE_OVERRIDE_2 =
            DBCustomAudienceOverride.builder()
                    .setOwner(OWNER_2)
                    .setBuyer(BUYER_2)
                    .setName(NAME_2)
                    .setAppPackageName(APP_PACKAGE_NAME_2)
                    .setBiddingLogicJS(BIDDING_LOGIC_JS_2)
                    .setTrustedBiddingData(TRUSTED_BIDDING_OVERRIDE_DATA_2)
                    .build();

    private static final String BIDDING_LOGIC_JS_3 =
            "function test() { return \"hello world_3\"; }";
    private static final String TRUSTED_BIDDING_OVERRIDE_DATA_3 = "{\"trusted_bidding_data\":3}";
    public static final DBCustomAudienceOverride DB_CUSTOM_AUDIENCE_OVERRIDE_3 =
            DBCustomAudienceOverride.builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setAppPackageName(APP_PACKAGE_NAME_1)
                    .setBiddingLogicJS(BIDDING_LOGIC_JS_3)
                    .setTrustedBiddingData(TRUSTED_BIDDING_OVERRIDE_DATA_3)
                    .build();

    private static final String OWNER_4 = "owner4";
    private static final String BUYER_4 = "buyer4";
    private static final String NAME_4 = "name4";

    public static final DBCustomAudienceOverride DB_CUSTOM_AUDIENCE_OVERRIDE_4 =
            DBCustomAudienceOverride.builder()
                    .setOwner(OWNER_4)
                    .setBuyer(BUYER_4)
                    .setName(NAME_4)
                    .setAppPackageName(APP_PACKAGE_NAME_1)
                    .setBiddingLogicJS(BIDDING_LOGIC_JS_1)
                    .setTrustedBiddingData(TRUSTED_BIDDING_OVERRIDE_DATA_1)
                    .build();

    private MockitoSession mStaticMockSession = null;
    private CustomAudienceDao mCustomAudienceDao;

    @Before
    public void setup() {
        // Test applications don't have the required permissions to read config P/H flags, and
        // injecting mocked flags everywhere is annoying and non-trivial for static methods
        mStaticMockSession =
                ExtendedMockito.mockitoSession().spyStatic(FlagsFactory.class).startMocking();

        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, CustomAudienceDatabase.class)
                        .build()
                        .customAudienceDao();
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testReturnsTrueIfCustomAudienceOverrideExists() {
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_1);

        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));

        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_2);

        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));
        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_2, BUYER_2, NAME_2));
    }

    @Test
    public void testDeletesCustomAudienceOverridesByPrimaryKey() {
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_1);
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_2);

        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));
        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_2, BUYER_2, NAME_2));

        mCustomAudienceDao.removeCustomAudienceOverrideByPrimaryKeyAndPackageName(
                OWNER_1, BUYER_1, NAME_1, APP_PACKAGE_NAME_1);

        assertFalse(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));
        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_2, BUYER_2, NAME_2));
    }

    @Test
    public void testDoesNotDeleteCustomAudienceOverrideWithIncorrectPackageName() {
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_1);

        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));

        mCustomAudienceDao.removeCustomAudienceOverrideByPrimaryKeyAndPackageName(
                OWNER_1, BUYER_1, NAME_1, APP_PACKAGE_NAME_2);

        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));
    }

    @Test
    public void testDeletesAllCustomAudienceOverridesThatMatchPackageName() {
        // Adding with same package name
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_1);
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_4);

        // Adding with different package name
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_2);

        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));
        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_4, BUYER_4, NAME_4));
        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_2, BUYER_2, NAME_2));

        mCustomAudienceDao.removeAllCustomAudienceOverrides(APP_PACKAGE_NAME_1);

        assertFalse(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));
        assertFalse(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_4, BUYER_4, NAME_4));
        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_2, BUYER_2, NAME_2));
    }

    @Test
    public void testGetCustomAudienceOverrideExists() {
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_1);

        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));

        String biddingLogicJS =
                mCustomAudienceDao.getBiddingLogicUrlOverride(
                        OWNER_1, BUYER_1, NAME_1, APP_PACKAGE_NAME_1);

        assertEquals(BIDDING_LOGIC_JS_1, biddingLogicJS);
    }

    @Test
    public void testCorrectlyOverridesCustomAudienceOverride() {
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_1);

        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));

        String biddingLogicJs1 =
                mCustomAudienceDao.getBiddingLogicUrlOverride(
                        OWNER_1, BUYER_1, NAME_1, APP_PACKAGE_NAME_1);

        String trustedBiddingData_1 =
                mCustomAudienceDao.getTrustedBiddingDataOverride(OWNER_1, BUYER_1, NAME_1);

        assertEquals(BIDDING_LOGIC_JS_1, biddingLogicJs1);
        assertEquals(TRUSTED_BIDDING_OVERRIDE_DATA_1, trustedBiddingData_1);

        // Persisting with same primary key
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_3);

        String biddingLogicJs3 =
                mCustomAudienceDao.getBiddingLogicUrlOverride(
                        OWNER_1, BUYER_1, NAME_1, APP_PACKAGE_NAME_1);

        String trustedBiddingData_3 =
                mCustomAudienceDao.getTrustedBiddingDataOverride(OWNER_1, BUYER_1, NAME_1);

        assertEquals(BIDDING_LOGIC_JS_3, biddingLogicJs3);
        assertEquals(TRUSTED_BIDDING_OVERRIDE_DATA_3, trustedBiddingData_3);
    }

    @Test
    public void testCorrectlyGetsBothCustomAudienceOverrides() {
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_1);
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_2);

        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));
        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_2, BUYER_2, NAME_2));

        String biddingLogicJs1 =
                mCustomAudienceDao.getBiddingLogicUrlOverride(
                        OWNER_1, BUYER_1, NAME_1, APP_PACKAGE_NAME_1);

        String trustedBiddingData_1 =
                mCustomAudienceDao.getTrustedBiddingDataOverride(OWNER_1, BUYER_1, NAME_1);

        assertEquals(BIDDING_LOGIC_JS_1, biddingLogicJs1);
        assertEquals(TRUSTED_BIDDING_OVERRIDE_DATA_1, trustedBiddingData_1);

        String biddingLogicJs2 =
                mCustomAudienceDao.getBiddingLogicUrlOverride(
                        OWNER_2, BUYER_2, NAME_2, APP_PACKAGE_NAME_2);

        String trustedBiddingData_2 =
                mCustomAudienceDao.getTrustedBiddingDataOverride(OWNER_2, BUYER_2, NAME_2);

        assertEquals(BIDDING_LOGIC_JS_2, biddingLogicJs2);
        assertEquals(TRUSTED_BIDDING_OVERRIDE_DATA_2, trustedBiddingData_2);
    }

    @Test(expected = NullPointerException.class)
    public void testPersistCustomAudienceOverride() {
        mCustomAudienceDao.persistCustomAudienceOverride(null);
    }

    @Test
    public void getByPrimaryKey_keyExistOrNotExist() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        mCustomAudienceDao.insertOrOverwriteCustomAudience(CUSTOM_AUDIENCE_1, DAILY_UPDATE_URL_1);
        assertNull(mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_2, BUYER_2, NAME_2));
        assertNull(
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_2, BUYER_2, NAME_2));
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));

        mCustomAudienceDao.insertOrOverwriteCustomAudience(CUSTOM_AUDIENCE_2, DAILY_UPDATE_URL_2);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_2, BUYER_2, NAME_2));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_2,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_2, BUYER_2, NAME_2));
    }

    @Test
    public void deleteByPrimaryKey_keyExist() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        mCustomAudienceDao.insertOrOverwriteCustomAudience(CUSTOM_AUDIENCE_1, DAILY_UPDATE_URL_1);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(CUSTOM_AUDIENCE_2, DAILY_UPDATE_URL_2);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_2, BUYER_2, NAME_2));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_2,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_2, BUYER_2, NAME_2));

        mCustomAudienceDao.deleteAllCustomAudienceDataByPrimaryKey(OWNER_1, BUYER_1, NAME_1);
        assertNull(mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertNull(
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_2, BUYER_2, NAME_2));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_2,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_2, BUYER_2, NAME_2));
    }

    @Test
    public void deleteByPrimaryKey_keyNotExist() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        mCustomAudienceDao.insertOrOverwriteCustomAudience(CUSTOM_AUDIENCE_1, DAILY_UPDATE_URL_1);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(CUSTOM_AUDIENCE_2, DAILY_UPDATE_URL_2);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_2, BUYER_2, NAME_2));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_2,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_2, BUYER_2, NAME_2));

        mCustomAudienceDao.deleteAllCustomAudienceDataByPrimaryKey(OWNER_1, BUYER_2, NAME_1);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_2, BUYER_2, NAME_2));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_2,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_2, BUYER_2, NAME_2));
    }

    @Test(expected = NullPointerException.class)
    public void testCreateOrUpdate_nullCustomAudience() {
        mCustomAudienceDao.persistCustomAudience(null);
    }

    @Test(expected = NullPointerException.class)
    public void testCreateOrUpdate_nullCustomAudienceBackgroundFetchData() {
        mCustomAudienceDao.persistCustomAudienceBackgroundFetchData(null);
    }

    @Test
    public void testCreateOrUpdate_UpdateExist() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        mCustomAudienceDao.insertOrOverwriteCustomAudience(CUSTOM_AUDIENCE_1, DAILY_UPDATE_URL_1);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));

        mCustomAudienceDao.insertOrOverwriteCustomAudience(CUSTOM_AUDIENCE_1_1, DAILY_UPDATE_URL_1);
        assertEquals(
                CUSTOM_AUDIENCE_1_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));
    }

    @Test
    public void testCreateOrUpdate_UpdateExistingBackgroundFetchData() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        mCustomAudienceDao.insertOrOverwriteCustomAudience(CUSTOM_AUDIENCE_1, DAILY_UPDATE_URL_1);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));

        mCustomAudienceDao.persistCustomAudienceBackgroundFetchData(
                CUSTOM_AUDIENCE_BGF_DATA_1_UPDATED);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1_UPDATED,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));
    }

    @Test
    public void testCreateOrUpdate_UpdateExistingCustomAudienceAndBackgroundFetchData() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        mCustomAudienceDao.insertOrOverwriteCustomAudience(CUSTOM_AUDIENCE_1, DAILY_UPDATE_URL_1);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));

        mCustomAudienceDao.updateCustomAudienceAndBackgroundFetchData(
                CUSTOM_AUDIENCE_BGF_DATA_1_1, CUSTOM_AUDIENCE_UPDATABLE_DATA);
        assertEquals(
                CUSTOM_AUDIENCE_1_UPDATED_FROM_UPDATABLE_DATA,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1_UPDATED,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));
    }

    @Test
    public void testUpdateMissingCustomAudienceAndBackgroundFetchData() {
        mCustomAudienceDao.persistCustomAudienceBackgroundFetchData(CUSTOM_AUDIENCE_BGF_DATA_1);
        assertNull(mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));

        // If a custom audience does not exist when we try to update the CA with its background
        // fetch data, we assume it was cleaned up while the CA was being updated.  In this case, we
        // should not persist the CA again, and the operation is aborted.
        mCustomAudienceDao.updateCustomAudienceAndBackgroundFetchData(
                CUSTOM_AUDIENCE_BGF_DATA_1_1, CUSTOM_AUDIENCE_UPDATABLE_DATA);
        assertNull(mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));
    }

    @Test
    public void testGetActiveCustomAudienceByBuyersInactiveCAs() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        List<String> buyers = Arrays.asList(BUYER_1);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_INACTIVE, DAILY_UPDATE_URL_1);
        assertEquals(
                CUSTOM_AUDIENCE_INACTIVE,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));

        assertTrue(
                mCustomAudienceDao.getActiveCustomAudienceByBuyers(buyers, CURRENT_TIME).isEmpty());
    }

    @Test
    public void testGetActiveCustomAudienceByBuyersActivatedCAs() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        List<String> buyers = Arrays.asList(BUYER_1, BUYER_2);
        List<DBCustomAudience> expectedCAs = Arrays.asList(CUSTOM_AUDIENCE_ACTIVE);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_ACTIVE, DAILY_UPDATE_URL_1);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_EXPIRED, DAILY_UPDATE_URL_2);
        List<DBCustomAudience> result =
                mCustomAudienceDao.getActiveCustomAudienceByBuyers(buyers, CURRENT_TIME);
        assertThat(result).containsExactlyElementsIn(expectedCAs);
    }

    @Test
    public void testGetActiveCustomAudienceByBuyersUpdatedCAs() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        List<String> buyers = Arrays.asList(BUYER_1, BUYER_2);
        List<DBCustomAudience> expectedCAs = Arrays.asList(CUSTOM_AUDIENCE_UPDATED);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_UPDATED, DAILY_UPDATE_URL_1);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_OUTDATED, DAILY_UPDATE_URL_2);
        List<DBCustomAudience> result =
                mCustomAudienceDao.getActiveCustomAudienceByBuyers(buyers, CURRENT_TIME);
        assertThat(result).containsExactlyElementsIn(expectedCAs);
    }

    @Test
    public void testGetActiveCustomAudienceByBuyersInvalidCAs() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        List<String> buyers = Arrays.asList(BUYER_1, BUYER_2, BUYER_3);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_NO_TRUSTED_BIDDING_DATA_URL, DAILY_UPDATE_URL_1);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_NO_USER_BIDDING_SIGNALS, DAILY_UPDATE_URL_1);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_NO_ADS, DAILY_UPDATE_URL_1);
        List<DBCustomAudience> result =
                mCustomAudienceDao.getActiveCustomAudienceByBuyers(buyers, CURRENT_TIME);
        assertTrue(result.isEmpty());
    }
}
