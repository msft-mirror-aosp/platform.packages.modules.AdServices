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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.AdServicesDatabase;
import com.android.adservices.data.common.DBAdData;

import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

public class CustomAudienceDaoTest {

    private static final Uri DAILY_UPDATE_URL_1 = Uri.parse("https://www.example.com/d1");
    private static final String USER_BIDDING_SIGNALS_1 = "ExampleBiddingSignal1";
    private static final Uri AD_DATA_RENDER_URL_1 = Uri.parse("https://www.example.com/a1");
    private static final String AD_DATA_METADATA_1 = "meta1";
    private static final DBAdData ADS_1 =
            new DBAdData.Builder()
                    .setRenderUrl(AD_DATA_RENDER_URL_1)
                    .setMetadata(AD_DATA_METADATA_1)
                    .build();
    private static final Uri DAILY_UPDATE_URL_2 = Uri.parse("https://www.example.com/d2");
    private static final String USER_BIDDING_SIGNALS_2 = "ExampleBiddingSignal2";
    private static final Uri AD_DATA_RENDER_URL_2 = Uri.parse("https://www.example.com/a2");
    private static final String AD_DATA_METADATA_2 = "meta2";
    private static final DBAdData ADS_2 =
            new DBAdData.Builder()
                    .setRenderUrl(AD_DATA_RENDER_URL_2)
                    .setMetadata(AD_DATA_METADATA_2)
                    .build();
    private static final Uri TRUSTED_BIDDING_DATA_URL_2 = Uri.parse("https://www.example.com/t1");
    private static final List<String> TRUSTED_BIDDING_DATA_KEYS_2 =
            Collections.singletonList("key2");
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    private static final Instant CREATION_TIME_1 = CLOCK.instant().truncatedTo(ChronoUnit.MILLIS);
    private static final Instant ACTIVATION_TIME_1 =
            CLOCK.instant().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MILLIS);
    private static final Instant EXPIRATION_TIME_1 =
            CLOCK.instant().plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.MILLIS);
    private static final Instant LAST_UPDATED_TIME_1 =
            CLOCK.instant().truncatedTo(ChronoUnit.MILLIS);
    private static final Instant CREATION_TIME_2 =
            CLOCK.instant().plus(Duration.ofMinutes(1)).truncatedTo(ChronoUnit.MILLIS);
    private static final Instant ACTIVATION_TIME_2 =
            CLOCK.instant()
                    .plus(Duration.ofDays(1))
                    .plus(Duration.ofMinutes(1))
                    .truncatedTo(ChronoUnit.MILLIS);
    private static final Instant EXPIRATION_TIME_2 =
            CLOCK.instant()
                    .plus(Duration.ofDays(3))
                    .plus(Duration.ofMinutes(1))
                    .truncatedTo(ChronoUnit.MILLIS);
    private static final Instant LAST_UPDATED_TIME_2 =
            CLOCK.instant().plus(Duration.ofMinutes(1)).truncatedTo(ChronoUnit.MILLIS);
    private static final String OWNER_1 = "owner1";
    private static final String OWNER_2 = "owner2";
    private static final String BUYER_1 = "buyer1";
    private static final String BUYER_2 = "buyer2";
    private static final String NAME_1 = "name1";
    private static final String NAME_2 = "name2";
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
                    .setDailyUpdateUrl(DAILY_UPDATE_URL_1)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_1)
                    .setAds(List.of(ADS_1))
                    .setTrustedBiddingData(null)
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
                    .setDailyUpdateUrl(DAILY_UPDATE_URL_2)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_1)
                    .setAds(List.of(ADS_1))
                    .setTrustedBiddingData(null)
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
                    .setDailyUpdateUrl(DAILY_UPDATE_URL_2)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_2)
                    .setAds(List.of(ADS_2))
                    .setTrustedBiddingData(TRUSTED_BIDDING_DATA_2)
                    .build();

    private static final String APP_PACKAGE_NAME_1 = "appPackageName1";
    private static final String DECISION_LOGIC_JS_1 =
            "function test() { return \"hello world_1\"; }";
    private static final String TRUSTED_BIDDING_OVERRIDE_DATA_1 = "{\"trusted_bidding_data\":1}";
    public static final DBCustomAudienceOverride DB_CUSTOM_AUDIENCE_OVERRIDE_1 =
            DBCustomAudienceOverride.builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setAppPackageName(APP_PACKAGE_NAME_1)
                    .setDecisionLogicJS(DECISION_LOGIC_JS_1)
                    .setTrustedBiddingData(TRUSTED_BIDDING_OVERRIDE_DATA_1)
                    .build();

    private static final String APP_PACKAGE_NAME_2 = "appPackageName2";
    private static final String DECISION_LOGIC_JS_2 =
            "function test() { return \"hello world_2\"; }";
    private static final String TRUSTED_BIDDING_OVERRIDE_DATA_2 = "{\"trusted_bidding_data\":2}";
    public static final DBCustomAudienceOverride DB_CUSTOM_AUDIENCE_OVERRIDE_2 =
            DBCustomAudienceOverride.builder()
                    .setOwner(OWNER_2)
                    .setBuyer(BUYER_2)
                    .setName(NAME_2)
                    .setAppPackageName(APP_PACKAGE_NAME_2)
                    .setDecisionLogicJS(DECISION_LOGIC_JS_2)
                    .setTrustedBiddingData(TRUSTED_BIDDING_OVERRIDE_DATA_2)
                    .build();

    private static final String DECISION_LOGIC_JS_3 =
            "function test() { return \"hello world_3\"; }";
    private static final String TRUSTED_BIDDING_OVERRIDE_DATA_3 = "{\"trusted_bidding_data\":3}";
    public static final DBCustomAudienceOverride DB_CUSTOM_AUDIENCE_OVERRIDE_3 =
            DBCustomAudienceOverride.builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setAppPackageName(APP_PACKAGE_NAME_1)
                    .setDecisionLogicJS(DECISION_LOGIC_JS_3)
                    .setTrustedBiddingData(TRUSTED_BIDDING_OVERRIDE_DATA_3)
                    .build();

    private CustomAudienceDao mCustomAudienceDao;

    @Before
    public void setup() {
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, AdServicesDatabase.class)
                        .build()
                        .customAudienceDao();
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

        mCustomAudienceDao.removeCustomAudienceOverrideByPrimaryKey(OWNER_1, BUYER_1, NAME_1);

        assertFalse(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));
        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_2, BUYER_2, NAME_2));
    }

    @Test
    public void testDeletesAllCustomAudienceOverrides() {
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_1);
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_2);

        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));
        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_2, BUYER_2, NAME_2));

        mCustomAudienceDao.removeAllCustomAudienceOverrides();

        assertFalse(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));
        assertFalse(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_2, BUYER_2, NAME_2));
    }

    @Test
    public void testGetCustomAudienceOverrideExists() {
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_1);

        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));

        String decisionLogicJS =
                mCustomAudienceDao.getDecisionLogicUrlOverride(OWNER_1, BUYER_1, NAME_1);

        assertEquals(DECISION_LOGIC_JS_1, decisionLogicJS);
    }

    @Test
    public void testCorrectlyOverridesCustomAudienceOverride() {
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_1);

        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));

        String decisionLogicJS_1 =
                mCustomAudienceDao.getDecisionLogicUrlOverride(OWNER_1, BUYER_1, NAME_1);

        String trustedBiddingData_1 =
                mCustomAudienceDao.getTrustedBiddingDataOverride(OWNER_1, BUYER_1, NAME_1);

        assertEquals(DECISION_LOGIC_JS_1, decisionLogicJS_1);
        assertEquals(TRUSTED_BIDDING_OVERRIDE_DATA_1, trustedBiddingData_1);

        // Persisting with same primary key
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_3);

        String decisionLogicJS_3 =
                mCustomAudienceDao.getDecisionLogicUrlOverride(OWNER_1, BUYER_1, NAME_1);

        String trustedBiddingData_3 =
                mCustomAudienceDao.getTrustedBiddingDataOverride(OWNER_1, BUYER_1, NAME_1);

        assertEquals(DECISION_LOGIC_JS_3, decisionLogicJS_3);
        assertEquals(TRUSTED_BIDDING_OVERRIDE_DATA_3, trustedBiddingData_3);
    }

    @Test
    public void testCorrectlyGetsBothCustomAudienceOverrides() {
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_1);
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_2);

        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));
        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_2, BUYER_2, NAME_2));

        String decisionLogicJS_1 =
                mCustomAudienceDao.getDecisionLogicUrlOverride(OWNER_1, BUYER_1, NAME_1);

        String trustedBiddingData_1 =
                mCustomAudienceDao.getTrustedBiddingDataOverride(OWNER_1, BUYER_1, NAME_1);

        assertEquals(DECISION_LOGIC_JS_1, decisionLogicJS_1);
        assertEquals(TRUSTED_BIDDING_OVERRIDE_DATA_1, trustedBiddingData_1);

        String decisionLogicJS_2 =
                mCustomAudienceDao.getDecisionLogicUrlOverride(OWNER_2, BUYER_2, NAME_2);

        String trustedBiddingData_2 =
                mCustomAudienceDao.getTrustedBiddingDataOverride(OWNER_2, BUYER_2, NAME_2);

        assertEquals(DECISION_LOGIC_JS_2, decisionLogicJS_2);
        assertEquals(TRUSTED_BIDDING_OVERRIDE_DATA_2, trustedBiddingData_2);
    }

    @Test(expected = NullPointerException.class)
    public void testPersistCustomAudienveOverride() {
        mCustomAudienceDao.persistCustomAudienceOverride(null);
    }

    @Test
    public void getByPrimaryKey_keyExistOrNotExist() {
        mCustomAudienceDao.insertOrOverrideCustomAudience(CUSTOM_AUDIENCE_1);
        assertNull(mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_2, BUYER_2, NAME_2));
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        mCustomAudienceDao.insertOrOverrideCustomAudience(CUSTOM_AUDIENCE_2);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_2, BUYER_2, NAME_2));
    }

    @Test
    public void deleteByPrimaryKey_keyExist() {
        mCustomAudienceDao.insertOrOverrideCustomAudience(CUSTOM_AUDIENCE_1);
        mCustomAudienceDao.insertOrOverrideCustomAudience(CUSTOM_AUDIENCE_2);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_2, BUYER_2, NAME_2));
        mCustomAudienceDao.deleteCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1);
        assertNull(mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_2, BUYER_2, NAME_2));
    }

    @Test
    public void deleteByPrimaryKey_keyNotExist() {
        mCustomAudienceDao.insertOrOverrideCustomAudience(CUSTOM_AUDIENCE_1);
        mCustomAudienceDao.insertOrOverrideCustomAudience(CUSTOM_AUDIENCE_2);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_2, BUYER_2, NAME_2));
        mCustomAudienceDao.deleteCustomAudienceByPrimaryKey(OWNER_1, BUYER_2, NAME_1);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_2, BUYER_2, NAME_2));
    }

    @Test(expected = NullPointerException.class)
    public void testCreateOrUpdate_nullCustomAudience() {
        mCustomAudienceDao.insertOrOverrideCustomAudience(null);
    }

    @Test
    public void testCreateOrUpdate_UpdateExist() {
        mCustomAudienceDao.insertOrOverrideCustomAudience(CUSTOM_AUDIENCE_1);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        mCustomAudienceDao.insertOrOverrideCustomAudience(CUSTOM_AUDIENCE_1_1);
        assertEquals(
                CUSTOM_AUDIENCE_1_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
    }
}
