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

package com.android.adservices.data.adselection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.content.Context;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

public class AdSelectionEntryDaoTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneOffset.UTC);

    private static final String BUYER_DECISION_LOGIC_JS_1 =
            "function test() { return \"hello world 1\"; }";
    private static final String BUYER_DECISION_LOGIC_JS_2 =
            "function test() { return \"hello world 2\"; }";

    private static final Uri BIDDING_LOGIC_URI_1 = Uri.parse("http://www.domain.com/logic/1");
    private static final Uri BIDDING_LOGIC_URI_2 = Uri.parse("http://www.domain.com/logic/2");

    private static final Uri RENDER_URI = Uri.parse("http://www.domain.com/advert/");

    private static final Instant ACTIVATION_TIME = CLOCK.instant().truncatedTo(ChronoUnit.MILLIS);

    private static final long AD_SELECTION_ID_1 = 1;
    private static final long AD_SELECTION_ID_2 = 2;
    private static final long AD_SELECTION_ID_3 = 3;
    private static final long AD_SELECTION_ID_4 = 4;
    private static final String CONTEXTUAL_SIGNALS = "contextual_signals";

    private static final double BID = 5;

    private static final String CALLER_PACKAGE_NAME_1 = "callerPackageName1";
    private static final String CALLER_PACKAGE_NAME_2 = "callerPackageName2";

    private static final DBBuyerDecisionLogic DB_BUYER_DECISION_LOGIC_1 =
            new DBBuyerDecisionLogic.Builder()
                    .setBiddingLogicUri(BIDDING_LOGIC_URI_1)
                    .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS_1)
                    .build();

    private static final DBBuyerDecisionLogic DB_BUYER_DECISION_LOGIC_2 =
            new DBBuyerDecisionLogic.Builder()
                    .setBiddingLogicUri(BIDDING_LOGIC_URI_2)
                    .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS_2)
                    .build();

    public static final CustomAudienceSignals CUSTOM_AUDIENCE_SIGNALS =
            CustomAudienceSignalsFixture.aCustomAudienceSignals();

    public static final DBAdSelection DB_AD_SELECTION_1 =
            new DBAdSelection.Builder()
                    .setAdSelectionId(AD_SELECTION_ID_1)
                    .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                    .setContextualSignals(CONTEXTUAL_SIGNALS)
                    .setBiddingLogicUri(BIDDING_LOGIC_URI_1)
                    .setWinningAdRenderUri(RENDER_URI)
                    .setWinningAdBid(BID)
                    .setCreationTimestamp(ACTIVATION_TIME)
                    .setCallerPackageName(CALLER_PACKAGE_NAME_1)
                    .build();

    public static final DBAdSelection DB_AD_SELECTION_2 =
            new DBAdSelection.Builder()
                    .setAdSelectionId(AD_SELECTION_ID_2)
                    .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                    .setContextualSignals(CONTEXTUAL_SIGNALS)
                    .setBiddingLogicUri(BIDDING_LOGIC_URI_2)
                    .setWinningAdRenderUri(RENDER_URI)
                    .setWinningAdBid(BID)
                    .setCreationTimestamp(ACTIVATION_TIME)
                    .setCallerPackageName(CALLER_PACKAGE_NAME_2)
                    .build();

    public static final DBAdSelection DB_AD_CONTEXTUAL_AD_SELECTION =
            new DBAdSelection.Builder()
                    .setAdSelectionId(AD_SELECTION_ID_3)
                    .setContextualSignals(CONTEXTUAL_SIGNALS)
                    .setWinningAdRenderUri(RENDER_URI)
                    .setWinningAdBid(BID)
                    .setCreationTimestamp(ACTIVATION_TIME)
                    .setCallerPackageName(CALLER_PACKAGE_NAME_1)
                    .build();

    private static final String AD_SELECTION_CONFIG_ID_1 = "1";
    private static final String DECISION_LOGIC_JS_1 =
            "function test() { return \"hello world_1\"; }";
    private static final String TRUSTED_SCORING_SIGNALS_1 =
            "{\n"
                    + "\t\"render_uri_1\": \"signals_for_1_1\",\n"
                    + "\t\"render_uri_2\": \"signals_for_1_2\"\n"
                    + "}";
    public static final DBAdSelectionOverride DB_AD_SELECTION_OVERRIDE_1 =
            DBAdSelectionOverride.builder()
                    .setAdSelectionConfigId(AD_SELECTION_CONFIG_ID_1)
                    .setAppPackageName(CALLER_PACKAGE_NAME_1)
                    .setDecisionLogicJS(DECISION_LOGIC_JS_1)
                    .setTrustedScoringSignals(TRUSTED_SCORING_SIGNALS_1)
                    .build();

    private static final String AD_SELECTION_CONFIG_ID_2 = "2";
    private static final String DECISION_LOGIC_JS_2 =
            "function test() { return \"hello world_2\"; }";
    private static final String TRUSTED_SCORING_SIGNALS_2 =
            "{\n"
                    + "\t\"render_uri_1\": \"signals_for_2_1\",\n"
                    + "\t\"render_uri_2\": \"signals_for_2_2\"\n"
                    + "}";
    public static final DBAdSelectionOverride DB_AD_SELECTION_OVERRIDE_2 =
            DBAdSelectionOverride.builder()
                    .setAdSelectionConfigId(AD_SELECTION_CONFIG_ID_2)
                    .setAppPackageName(CALLER_PACKAGE_NAME_2)
                    .setDecisionLogicJS(DECISION_LOGIC_JS_2)
                    .setTrustedScoringSignals(TRUSTED_SCORING_SIGNALS_2)
                    .build();

    private static final String DECISION_LOGIC_JS_3 =
            "function test() { return \"hello world_3\"; }";
    private static final String TRUSTED_SCORING_SIGNALS_3 =
            "{\n"
                    + "\t\"render_uri_1\": \"signals_for_3_1\",\n"
                    + "\t\"render_uri_2\": \"signals_for_3_2\"\n"
                    + "}";
    public static final DBAdSelectionOverride DB_AD_SELECTION_OVERRIDE_3 =
            DBAdSelectionOverride.builder()
                    .setAdSelectionConfigId(AD_SELECTION_CONFIG_ID_1)
                    .setAppPackageName(CALLER_PACKAGE_NAME_1)
                    .setDecisionLogicJS(DECISION_LOGIC_JS_3)
                    .setTrustedScoringSignals(TRUSTED_SCORING_SIGNALS_3)
                    .build();

    private static final String AD_SELECTION_CONFIG_ID_4 = "4";
    private static final String DECISION_LOGIC_JS_4 =
            "function test() { return \"hello world_4\"; }";
    private static final String TRUSTED_SCORING_SIGNALS_4 =
            "{\n"
                    + "\t\"render_uri_1\": \"signals_for_4_1\",\n"
                    + "\t\"render_uri_2\": \"signals_for_4_2\"\n"
                    + "}";
    public static final DBAdSelectionOverride DB_AD_SELECTION_OVERRIDE_4 =
            DBAdSelectionOverride.builder()
                    .setAdSelectionConfigId(AD_SELECTION_CONFIG_ID_4)
                    .setAppPackageName(CALLER_PACKAGE_NAME_1)
                    .setDecisionLogicJS(DECISION_LOGIC_JS_4)
                    .setTrustedScoringSignals(TRUSTED_SCORING_SIGNALS_4)
                    .build();

    // Event registering constants
    private static final int BUYER_DESTINATION = DBRegisteredAdEvent.DESTINATION_SELLER;
    private static final int SELLER_DESTINATION = DBRegisteredAdEvent.DESTINATION_BUYER;

    private static final String CLICK_EVENT = "click";
    private static final String HOVER_EVENT = "hover";

    private static final String SELLER_BASE_URI = "https://www.seller.com/";
    private static final String BUYER_BASE_URI = "https://www.buyer.com/";
    private static final String DIFFERENT_BASE_URI = "https://www.different.com/";

    private static final Uri SELLER_CLICK_URI = Uri.parse(SELLER_BASE_URI + CLICK_EVENT);
    private static final Uri SELLER_HOVER_URI = Uri.parse(SELLER_BASE_URI + HOVER_EVENT);
    private static final Uri BUYER_CLICK_URI = Uri.parse(BUYER_BASE_URI + CLICK_EVENT);

    private static final DBRegisteredAdEvent DB_REGISTERED_EVENT_SELLER_CLICK_1 =
            DBRegisteredAdEvent.builder()
                    .setAdSelectionId(AD_SELECTION_ID_1)
                    .setEventType(CLICK_EVENT)
                    .setDestination(SELLER_DESTINATION)
                    .setEventUri(SELLER_CLICK_URI)
                    .build();
    private static final DBRegisteredAdEvent DB_REGISTERED_EVENT_SELLER_HOVER_1 =
            DBRegisteredAdEvent.builder()
                    .setAdSelectionId(AD_SELECTION_ID_1)
                    .setEventType(HOVER_EVENT)
                    .setDestination(SELLER_DESTINATION)
                    .setEventUri(SELLER_HOVER_URI)
                    .build();
    private static final DBRegisteredAdEvent DB_REGISTERED_EVENT_SELLER_1_DIFFERENT_URI =
            DBRegisteredAdEvent.builder()
                    .setAdSelectionId(AD_SELECTION_ID_1)
                    .setEventType(CLICK_EVENT)
                    .setDestination(SELLER_DESTINATION)
                    .setEventUri(Uri.parse(DIFFERENT_BASE_URI + CLICK_EVENT))
                    .build();
    private static final DBRegisteredAdEvent DB_REGISTERED_EVENT_SELLER_CLICK_2 =
            DBRegisteredAdEvent.builder()
                    .setAdSelectionId(AD_SELECTION_ID_2)
                    .setEventType(CLICK_EVENT)
                    .setDestination(SELLER_DESTINATION)
                    .setEventUri(SELLER_CLICK_URI)
                    .build();
    private static final DBRegisteredAdEvent DB_REGISTERED_EVENT_SELLER_HOVER_2 =
            DBRegisteredAdEvent.builder()
                    .setAdSelectionId(AD_SELECTION_ID_2)
                    .setEventType(HOVER_EVENT)
                    .setDestination(SELLER_DESTINATION)
                    .setEventUri(SELLER_HOVER_URI)
                    .build();
    private static final DBRegisteredAdEvent DB_REGISTERED_EVENT_SELLER_CLICK_3 =
            DBRegisteredAdEvent.builder()
                    .setAdSelectionId(AD_SELECTION_ID_3)
                    .setEventType(CLICK_EVENT)
                    .setDestination(SELLER_DESTINATION)
                    .setEventUri(SELLER_CLICK_URI)
                    .build();
    private static final DBRegisteredAdEvent DB_REGISTERED_EVENT_SELLER_HOVER_3 =
            DBRegisteredAdEvent.builder()
                    .setAdSelectionId(AD_SELECTION_ID_3)
                    .setEventType(HOVER_EVENT)
                    .setDestination(SELLER_DESTINATION)
                    .setEventUri(SELLER_HOVER_URI)
                    .build();

    private static final DBRegisteredAdEvent DB_REGISTERED_EVENT_BUYER_1 =
            DBRegisteredAdEvent.builder()
                    .setAdSelectionId(AD_SELECTION_ID_1)
                    .setEventType(CLICK_EVENT)
                    .setDestination(BUYER_DESTINATION)
                    .setEventUri(BUYER_CLICK_URI)
                    .build();

    private AdSelectionEntryDao mAdSelectionEntryDao;

    @Before
    public void setup() {
        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();
    }

    @Test
    public void testReturnsTrueIfAdSelectionConfigIdExists() {
        mAdSelectionEntryDao.persistAdSelectionOverride(DB_AD_SELECTION_OVERRIDE_1);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_1));

        mAdSelectionEntryDao.persistAdSelectionOverride(DB_AD_SELECTION_OVERRIDE_2);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_1));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AD_SELECTION_CONFIG_ID_2, CALLER_PACKAGE_NAME_2));
    }

    @Test
    public void testReturnsFalseIfAdSelectionConfigIdExistsDifferentPackageName() {
        mAdSelectionEntryDao.persistAdSelectionOverride(DB_AD_SELECTION_OVERRIDE_1);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_1));
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_2));
    }

    @Test
    public void testDeletesByAdSelectionConfigId() {
        mAdSelectionEntryDao.persistAdSelectionOverride(DB_AD_SELECTION_OVERRIDE_1);
        mAdSelectionEntryDao.persistAdSelectionOverride(DB_AD_SELECTION_OVERRIDE_2);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_1));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AD_SELECTION_CONFIG_ID_2, CALLER_PACKAGE_NAME_2));

        mAdSelectionEntryDao.removeAdSelectionOverrideByIdAndPackageName(
                AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_1);

        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_1));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AD_SELECTION_CONFIG_ID_2, CALLER_PACKAGE_NAME_2));
    }

    @Test
    public void testDoesNotDeleteWithIncorrectPackageName() {
        mAdSelectionEntryDao.persistAdSelectionOverride(DB_AD_SELECTION_OVERRIDE_1);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_1));

        mAdSelectionEntryDao.removeAdSelectionOverrideByIdAndPackageName(
                AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_2);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_1));
    }

    @Test
    public void testDeletesAllAdSelectionOverrides() {
        mAdSelectionEntryDao.persistAdSelectionOverride(DB_AD_SELECTION_OVERRIDE_1);
        mAdSelectionEntryDao.persistAdSelectionOverride(DB_AD_SELECTION_OVERRIDE_2);
        mAdSelectionEntryDao.persistAdSelectionOverride(DB_AD_SELECTION_OVERRIDE_4);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_1));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AD_SELECTION_CONFIG_ID_2, CALLER_PACKAGE_NAME_2));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AD_SELECTION_CONFIG_ID_4, CALLER_PACKAGE_NAME_1));

        mAdSelectionEntryDao.removeAllAdSelectionOverrides(CALLER_PACKAGE_NAME_1);

        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_1));
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AD_SELECTION_CONFIG_ID_4, CALLER_PACKAGE_NAME_1));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AD_SELECTION_CONFIG_ID_2, CALLER_PACKAGE_NAME_2));
    }

    @Test
    public void testGetAdSelectionOverrideExists() {
        mAdSelectionEntryDao.persistAdSelectionOverride(DB_AD_SELECTION_OVERRIDE_1);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_1));

        String decisionLogicJS =
                mAdSelectionEntryDao.getDecisionLogicOverride(
                        AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_1);

        String trustedScoringSignals =
                mAdSelectionEntryDao.getTrustedScoringSignalsOverride(
                        AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_1);

        assertEquals(DECISION_LOGIC_JS_1, decisionLogicJS);
        assertEquals(TRUSTED_SCORING_SIGNALS_1, trustedScoringSignals);
    }

    @Test
    public void testGetAdSelectionOverrideExistsIgnoresOverridesByDifferentApp() {
        mAdSelectionEntryDao.persistAdSelectionOverride(DB_AD_SELECTION_OVERRIDE_1);

        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_2));
    }

    @Test
    public void testCorrectlyOverridesAdSelectionOverride() {
        mAdSelectionEntryDao.persistAdSelectionOverride(DB_AD_SELECTION_OVERRIDE_1);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_1));

        String decisionLogicJS_1 =
                mAdSelectionEntryDao.getDecisionLogicOverride(
                        AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_1);
        String trustedScoringSignals_1 =
                mAdSelectionEntryDao.getTrustedScoringSignalsOverride(
                        AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_1);

        assertEquals(DECISION_LOGIC_JS_1, decisionLogicJS_1);
        assertEquals(TRUSTED_SCORING_SIGNALS_1, trustedScoringSignals_1);

        // Persisting with same AdSelectionConfigId but different decisionLogicJS
        mAdSelectionEntryDao.persistAdSelectionOverride(DB_AD_SELECTION_OVERRIDE_3);

        String decisionLogicJS_3 =
                mAdSelectionEntryDao.getDecisionLogicOverride(
                        AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_1);
        String trustedScoringSignals_3 =
                mAdSelectionEntryDao.getTrustedScoringSignalsOverride(
                        AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_1);

        assertEquals(DECISION_LOGIC_JS_3, decisionLogicJS_3);
        assertEquals(TRUSTED_SCORING_SIGNALS_3, trustedScoringSignals_3);
    }

    @Test
    public void testCorrectlyGetsBothAdSelectionOverrides() {
        mAdSelectionEntryDao.persistAdSelectionOverride(DB_AD_SELECTION_OVERRIDE_1);
        mAdSelectionEntryDao.persistAdSelectionOverride(DB_AD_SELECTION_OVERRIDE_2);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_1));

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AD_SELECTION_CONFIG_ID_2, CALLER_PACKAGE_NAME_2));

        String decisionLogicJS_1 =
                mAdSelectionEntryDao.getDecisionLogicOverride(
                        AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_1);
        String trustedScoringSignals_1 =
                mAdSelectionEntryDao.getTrustedScoringSignalsOverride(
                        AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_1);

        assertEquals(DECISION_LOGIC_JS_1, decisionLogicJS_1);
        assertEquals(TRUSTED_SCORING_SIGNALS_1, trustedScoringSignals_1);

        String decisionLogicJS_2 =
                mAdSelectionEntryDao.getDecisionLogicOverride(
                        AD_SELECTION_CONFIG_ID_2, CALLER_PACKAGE_NAME_2);
        String trustedScoringSignals_2 =
                mAdSelectionEntryDao.getTrustedScoringSignalsOverride(
                        AD_SELECTION_CONFIG_ID_2, CALLER_PACKAGE_NAME_2);

        assertEquals(DECISION_LOGIC_JS_2, decisionLogicJS_2);
        assertEquals(TRUSTED_SCORING_SIGNALS_2, trustedScoringSignals_2);
    }

    @Test
    public void testAdSelectionOverridesDoneByOtherAppsAreIgnored() {
        mAdSelectionEntryDao.persistAdSelectionOverride(DB_AD_SELECTION_OVERRIDE_1);
        assertNull(
                mAdSelectionEntryDao.getDecisionLogicOverride(
                        AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_2));
        assertNull(
                mAdSelectionEntryDao.getTrustedScoringSignalsOverride(
                        AD_SELECTION_CONFIG_ID_1, CALLER_PACKAGE_NAME_2));
    }

    @Test(expected = NullPointerException.class)
    public void testPersistNullAdSelectionOverride() {
        mAdSelectionEntryDao.persistAdSelectionOverride(null);
    }

    @Test
    public void testReturnsTrueIfAdSelectionIdExists() {
        mAdSelectionEntryDao.persistAdSelection(DB_AD_SELECTION_1);

        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID_1));

        mAdSelectionEntryDao.persistAdSelection(DB_AD_SELECTION_2);

        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID_1));
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID_2));
    }

    @Test
    public void testDeletesByAdSelectionId() {
        mAdSelectionEntryDao.persistAdSelection(DB_AD_SELECTION_1);
        mAdSelectionEntryDao.persistAdSelection(DB_AD_SELECTION_2);

        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID_1));
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID_2));

        mAdSelectionEntryDao.removeAdSelectionEntriesByIds(List.of(AD_SELECTION_ID_1));

        assertFalse(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID_1));
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID_2));
    }

    @Test
    public void testDeletesByAdSelectionIdNotExist() {
        mAdSelectionEntryDao.persistAdSelection(DB_AD_SELECTION_1);

        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID_1));
        assertFalse(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID_2));

        mAdSelectionEntryDao.removeAdSelectionEntriesByIds(List.of(AD_SELECTION_ID_2));

        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID_1));
    }

    @Test
    public void testDeletesByMultipleAdSelectionIds() {
        mAdSelectionEntryDao.persistAdSelection(DB_AD_SELECTION_1);
        mAdSelectionEntryDao.persistAdSelection(DB_AD_SELECTION_2);

        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID_1));
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID_2));

        mAdSelectionEntryDao.removeAdSelectionEntriesByIds(
                List.of(AD_SELECTION_ID_1, AD_SELECTION_ID_2));

        assertFalse(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID_1));
        assertFalse(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID_2));
    }

    @Test(expected = NullPointerException.class)
    public void testPersistNullAdSelectionEntry() {
        mAdSelectionEntryDao.persistAdSelection(null);
    }

    @Test
    public void testReturnsFalseIfAdSelectionIdDoesNotExist() {
        mAdSelectionEntryDao.persistAdSelection(DB_AD_SELECTION_1);

        assertFalse(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID_2));
    }

    @Test
    public void testGetsAdSelectionEntryExistsContextualAd() {
        mAdSelectionEntryDao.persistAdSelection(DB_AD_CONTEXTUAL_AD_SELECTION);

        DBAdSelectionEntry adSelectionEntry =
                mAdSelectionEntryDao.getAdSelectionEntityById(AD_SELECTION_ID_3);
        DBAdSelectionEntry expected = toAdSelectionEntry(DB_AD_CONTEXTUAL_AD_SELECTION);

        assertEquals(adSelectionEntry, expected);
    }

    @Test
    public void testGetsAdSelectionEntryExistsAndDifferentBuyerDecisionLogicExists() {
        mAdSelectionEntryDao.persistAdSelection(DB_AD_CONTEXTUAL_AD_SELECTION);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(DB_BUYER_DECISION_LOGIC_2);

        DBAdSelectionEntry adSelectionEntry =
                mAdSelectionEntryDao.getAdSelectionEntityById(AD_SELECTION_ID_3);
        DBAdSelectionEntry expected = toAdSelectionEntry(DB_AD_CONTEXTUAL_AD_SELECTION);

        assertEquals(adSelectionEntry, expected);
    }

    @Test
    public void testGetsAdSelectionEntryExistsAndBuyerDecisionLogicExists() {
        mAdSelectionEntryDao.persistAdSelection(DB_AD_SELECTION_1);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(DB_BUYER_DECISION_LOGIC_1);

        DBAdSelectionEntry adSelectionEntry =
                mAdSelectionEntryDao.getAdSelectionEntityById(AD_SELECTION_ID_1);
        DBAdSelectionEntry expected =
                toAdSelectionEntry(DB_AD_SELECTION_1, DB_BUYER_DECISION_LOGIC_1);

        assertEquals(adSelectionEntry, expected);
    }

    @Test
    public void testGetsAdSelectionEntryExistsAndMultipleBuyerDecisionLogicExist() {
        mAdSelectionEntryDao.persistAdSelection(DB_AD_SELECTION_1);
        mAdSelectionEntryDao.persistAdSelection(DB_AD_SELECTION_2);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(DB_BUYER_DECISION_LOGIC_1);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(DB_BUYER_DECISION_LOGIC_2);

        DBAdSelectionEntry adSelectionEntry1 =
                mAdSelectionEntryDao.getAdSelectionEntityById(AD_SELECTION_ID_1);
        DBAdSelectionEntry expected1 =
                toAdSelectionEntry(DB_AD_SELECTION_1, DB_BUYER_DECISION_LOGIC_1);
        assertEquals(adSelectionEntry1, expected1);

        DBAdSelectionEntry adSelectionEntry2 =
                mAdSelectionEntryDao.getAdSelectionEntityById(AD_SELECTION_ID_2);
        DBAdSelectionEntry expected2 =
                toAdSelectionEntry(DB_AD_SELECTION_2, DB_BUYER_DECISION_LOGIC_2);
        assertEquals(adSelectionEntry2, expected2);
    }

    @Test
    public void testJoinsWithCorrectBuyerDecisionLogic() {
        mAdSelectionEntryDao.persistAdSelection(DB_AD_SELECTION_1);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(DB_BUYER_DECISION_LOGIC_1);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(DB_BUYER_DECISION_LOGIC_2);

        DBAdSelectionEntry adSelectionEntry =
                mAdSelectionEntryDao.getAdSelectionEntityById(AD_SELECTION_ID_1);
        DBAdSelectionEntry expected =
                toAdSelectionEntry(DB_AD_SELECTION_1, DB_BUYER_DECISION_LOGIC_1);

        assertEquals(adSelectionEntry, expected);
    }

    /**
     * Tests that if two decision logic inserts are made with the same URI, the second overwrites
     * the first.
     */
    @Test
    public void testOverwriteDecisionLogic() {
        DBBuyerDecisionLogic firstEntry = DB_BUYER_DECISION_LOGIC_1;
        DBBuyerDecisionLogic secondEntry =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(DB_BUYER_DECISION_LOGIC_1.getBiddingLogicUri())
                        .setBuyerDecisionLogicJs(
                                DB_BUYER_DECISION_LOGIC_2.getBuyerDecisionLogicJs())
                        .build();
        mAdSelectionEntryDao.persistBuyerDecisionLogic(firstEntry);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(secondEntry);
        mAdSelectionEntryDao.persistAdSelection(DB_AD_SELECTION_1);

        DBAdSelectionEntry adSelectionEntry =
                mAdSelectionEntryDao.getAdSelectionEntityById(AD_SELECTION_ID_1);
        DBAdSelectionEntry expected =
                toAdSelectionEntry(DB_AD_SELECTION_1, secondEntry);

        assertEquals(adSelectionEntry, expected);
    }

    @Test
    public void testRemoveExpiredAdSelection() {
        DBAdSelection expiredDBAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID_4)
                        .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                        .setContextualSignals(CONTEXTUAL_SIGNALS)
                        .setBiddingLogicUri(BIDDING_LOGIC_URI_1)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME.minusSeconds(10))
                        .setCallerPackageName(CALLER_PACKAGE_NAME_1)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(DB_AD_SELECTION_1);
        mAdSelectionEntryDao.persistAdSelection(expiredDBAdSelection);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionIdExist(DB_AD_SELECTION_1.getAdSelectionId()));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionIdExist(
                        expiredDBAdSelection.getAdSelectionId()));

        mAdSelectionEntryDao.removeExpiredAdSelection(ACTIVATION_TIME.minusSeconds(5));

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionIdExist(DB_AD_SELECTION_1.getAdSelectionId()));
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionIdExist(
                        expiredDBAdSelection.getAdSelectionId()));
    }

    @Test
    public void testDoesBuyerDecisionLogicExist() {
        assertFalse(
                mAdSelectionEntryDao.doesBuyerDecisionLogicExist(
                        DB_BUYER_DECISION_LOGIC_1.getBiddingLogicUri()));

        mAdSelectionEntryDao.persistBuyerDecisionLogic(DB_BUYER_DECISION_LOGIC_1);

        assertTrue(
                mAdSelectionEntryDao.doesBuyerDecisionLogicExist(
                        DB_BUYER_DECISION_LOGIC_1.getBiddingLogicUri()));
    }

    @Test
    public void testGetAdSelectionEntitiesFilteredByCallerPackageName() {
        mAdSelectionEntryDao.persistAdSelection(DB_AD_SELECTION_1);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(DB_BUYER_DECISION_LOGIC_1);
        mAdSelectionEntryDao.persistAdSelection(DB_AD_SELECTION_2); // different caller package name
        mAdSelectionEntryDao.persistBuyerDecisionLogic(DB_BUYER_DECISION_LOGIC_2);

        List<DBAdSelectionEntry> adSelectionEntries =
                mAdSelectionEntryDao.getAdSelectionEntities(
                        Collections.singletonList(AD_SELECTION_ID_1), CALLER_PACKAGE_NAME_1);
        DBAdSelectionEntry expected =
                toAdSelectionEntry(DB_AD_SELECTION_1, DB_BUYER_DECISION_LOGIC_1);

        assertEquals(1, adSelectionEntries.size());
        assertEquals(adSelectionEntries.get(0), expected);
    }

    @Test
    public void testRemoveExpiredBuyerDecisionLogic() {
        assertFalse(
                mAdSelectionEntryDao.doesBuyerDecisionLogicExist(
                        DB_BUYER_DECISION_LOGIC_1.getBiddingLogicUri()));
        assertFalse(
                mAdSelectionEntryDao.doesBuyerDecisionLogicExist(
                        DB_BUYER_DECISION_LOGIC_2.getBiddingLogicUri()));

        mAdSelectionEntryDao.persistBuyerDecisionLogic(DB_BUYER_DECISION_LOGIC_1);
        mAdSelectionEntryDao.persistAdSelection(DB_AD_SELECTION_1);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(DB_BUYER_DECISION_LOGIC_2);

        assertTrue(
                mAdSelectionEntryDao.doesBuyerDecisionLogicExist(
                        DB_BUYER_DECISION_LOGIC_1.getBiddingLogicUri()));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionIdExist(DB_AD_SELECTION_1.getAdSelectionId()));
        assertTrue(
                mAdSelectionEntryDao.doesBuyerDecisionLogicExist(
                        DB_BUYER_DECISION_LOGIC_2.getBiddingLogicUri()));

        mAdSelectionEntryDao.removeExpiredBuyerDecisionLogic();

        assertTrue(
                mAdSelectionEntryDao.doesBuyerDecisionLogicExist(
                        DB_BUYER_DECISION_LOGIC_1.getBiddingLogicUri()));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionIdExist(DB_AD_SELECTION_1.getAdSelectionId()));

        // DB_BUYER_DECISION_LOGIC_2 will be removed because there is no instance of
        // DB_BUYER_DECISION_LOGIC_2.getBiddingLogicUri() in the DBAdSelection table
        assertFalse(
                mAdSelectionEntryDao.doesBuyerDecisionLogicExist(
                        DB_BUYER_DECISION_LOGIC_2.getBiddingLogicUri()));
    }

    @Test
    public void testReturnsTrueIfRegisteredEventExists() {
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdEventExist(
                        AD_SELECTION_ID_1, CLICK_EVENT, SELLER_DESTINATION));
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdEventExist(
                        AD_SELECTION_ID_1, CLICK_EVENT, BUYER_DESTINATION));

        mAdSelectionEntryDao.persistDBRegisteredAdEvents(
                ImmutableList.of(DB_REGISTERED_EVENT_SELLER_CLICK_1));

        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdEventExist(
                        AD_SELECTION_ID_1, CLICK_EVENT, SELLER_DESTINATION));
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdEventExist(
                        AD_SELECTION_ID_1, CLICK_EVENT, BUYER_DESTINATION));
    }

    @Test
    public void testGetsCorrectEventUri() {
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdEventExist(
                        AD_SELECTION_ID_1, CLICK_EVENT, SELLER_DESTINATION));
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdEventExist(
                        AD_SELECTION_ID_1, CLICK_EVENT, BUYER_DESTINATION));

        mAdSelectionEntryDao.persistDBRegisteredAdEvents(
                ImmutableList.of(DB_REGISTERED_EVENT_SELLER_CLICK_1, DB_REGISTERED_EVENT_BUYER_1));

        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdEventExist(
                        AD_SELECTION_ID_1, CLICK_EVENT, SELLER_DESTINATION));
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdEventExist(
                        AD_SELECTION_ID_1, CLICK_EVENT, BUYER_DESTINATION));

        // Asserts seller uri is returned
        assertEquals(
                SELLER_CLICK_URI,
                mAdSelectionEntryDao.getRegisteredAdEventUri(
                        AD_SELECTION_ID_1, CLICK_EVENT, SELLER_DESTINATION));

        // Asserts buyer uri is returned
        assertEquals(
                BUYER_CLICK_URI,
                mAdSelectionEntryDao.getRegisteredAdEventUri(
                        AD_SELECTION_ID_1, CLICK_EVENT, BUYER_DESTINATION));
    }

    @Test
    public void testUpdatesEventUriIfPrimaryKeySame() {
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdEventExist(
                        AD_SELECTION_ID_1, CLICK_EVENT, SELLER_DESTINATION));

        mAdSelectionEntryDao.persistDBRegisteredAdEvents(
                ImmutableList.of(DB_REGISTERED_EVENT_SELLER_CLICK_1));

        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdEventExist(
                        AD_SELECTION_ID_1, CLICK_EVENT, SELLER_DESTINATION));

        // Asserts seller uri is returned
        assertEquals(
                SELLER_CLICK_URI,
                mAdSelectionEntryDao.getRegisteredAdEventUri(
                        AD_SELECTION_ID_1, CLICK_EVENT, SELLER_DESTINATION));

        // Overwrite primary key with another uri
        mAdSelectionEntryDao.persistDBRegisteredAdEvents(
                ImmutableList.of(DB_REGISTERED_EVENT_SELLER_1_DIFFERENT_URI));

        // Asserts different uri is returned
        assertEquals(
                Uri.parse(DIFFERENT_BASE_URI + CLICK_EVENT),
                mAdSelectionEntryDao.getRegisteredAdEventUri(
                        AD_SELECTION_ID_1, CLICK_EVENT, SELLER_DESTINATION));
    }

    @Test
    public void testClearsExpiredRegisteredEventsData() {
        mAdSelectionEntryDao.persistAdSelection(DB_AD_SELECTION_1);

        // Added registered event data with same adSelectionId as DB_AD_SELECTION_1
        mAdSelectionEntryDao.persistDBRegisteredAdEvents(
                ImmutableList.of(
                        DB_REGISTERED_EVENT_SELLER_CLICK_1, DB_REGISTERED_EVENT_SELLER_HOVER_1));

        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdEventExist(
                        AD_SELECTION_ID_1, CLICK_EVENT, SELLER_DESTINATION));
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdEventExist(
                        AD_SELECTION_ID_1, HOVER_EVENT, SELLER_DESTINATION));

        // Simulating stale registered event data by inserting data with different adSelectionIds
        mAdSelectionEntryDao.persistDBRegisteredAdEvents(
                ImmutableList.of(
                        DB_REGISTERED_EVENT_SELLER_CLICK_2,
                        DB_REGISTERED_EVENT_SELLER_HOVER_2,
                        DB_REGISTERED_EVENT_SELLER_CLICK_3,
                        DB_REGISTERED_EVENT_SELLER_HOVER_3));

        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdEventExist(
                        AD_SELECTION_ID_2, CLICK_EVENT, SELLER_DESTINATION));
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdEventExist(
                        AD_SELECTION_ID_2, HOVER_EVENT, SELLER_DESTINATION));

        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdEventExist(
                        AD_SELECTION_ID_3, CLICK_EVENT, SELLER_DESTINATION));
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdEventExist(
                        AD_SELECTION_ID_3, HOVER_EVENT, SELLER_DESTINATION));

        mAdSelectionEntryDao.removeExpiredRegisteredAdEvents();

        // Assert that stale registered event data was cleared
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdEventExist(
                        AD_SELECTION_ID_2, CLICK_EVENT, SELLER_DESTINATION));
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdEventExist(
                        AD_SELECTION_ID_2, HOVER_EVENT, SELLER_DESTINATION));

        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdEventExist(
                        AD_SELECTION_ID_3, CLICK_EVENT, SELLER_DESTINATION));
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdEventExist(
                        AD_SELECTION_ID_3, HOVER_EVENT, SELLER_DESTINATION));

        // Assert that non-stale data was not cleared
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdEventExist(
                        AD_SELECTION_ID_1, CLICK_EVENT, SELLER_DESTINATION));
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdEventExist(
                        AD_SELECTION_ID_1, HOVER_EVENT, SELLER_DESTINATION));
    }

    /**
     * Creates expected DBAdSelectionEntry to be used for testing from DBAdSelection and
     * DBBuyerDecisionLogic. Remarketing Case
     */
    private DBAdSelectionEntry toAdSelectionEntry(
            DBAdSelection adSelection, DBBuyerDecisionLogic buyerDecisionLogic) {
        return new DBAdSelectionEntry.Builder()
                .setAdSelectionId(adSelection.getAdSelectionId())
                .setCustomAudienceSignals(adSelection.getCustomAudienceSignals())
                .setContextualSignals(adSelection.getContextualSignals())
                .setWinningAdRenderUri(adSelection.getWinningAdRenderUri())
                .setWinningAdBid(adSelection.getWinningAdBid())
                .setCreationTimestamp(adSelection.getCreationTimestamp())
                .setBuyerDecisionLogicJs(buyerDecisionLogic.getBuyerDecisionLogicJs())
                .build();
    }

    /**
     * Creates expected DBAdSelectionEntry to be used for testing from DBAdSelection. Contextual
     * Case
     */
    private DBAdSelectionEntry toAdSelectionEntry(DBAdSelection adSelection) {
        return new DBAdSelectionEntry.Builder()
                .setAdSelectionId(adSelection.getAdSelectionId())
                .setCustomAudienceSignals(adSelection.getCustomAudienceSignals())
                .setContextualSignals(adSelection.getContextualSignals())
                .setWinningAdRenderUri(adSelection.getWinningAdRenderUri())
                .setWinningAdBid(adSelection.getWinningAdBid())
                .setCreationTimestamp(adSelection.getCreationTimestamp())
                .build();
    }
}
