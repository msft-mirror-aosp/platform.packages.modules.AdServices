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
import static org.junit.Assert.assertThrows;

import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.adservices.common.CommonFixture;
import android.net.Uri;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public class DBAdSelectionTest {
    private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    private static final Uri BIDDING_LOGIC_URI = Uri.parse("http://www.domain.com/logic");
    private static final Uri RENDER_URI = Uri.parse("http://www.domain.com/advert");
    private static final Instant ACTIVATION_TIME = CLOCK.instant().truncatedTo(ChronoUnit.MILLIS);

    private static final long AD_SELECTION_ID = 1;
    private static final String CONTEXTUAL_SIGNALS = "contextual_signals";

    private static final double BID = 5;

    private static final CustomAudienceSignals CUSTOM_AUDIENCE_SIGNALS =
            CustomAudienceSignalsFixture.aCustomAudienceSignals();

    private static final String CALLER_PACKAGE_NAME = "callerPackageName";

    @Test
    public void testBuildDBAdSelection() {
        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                        .setContextualSignals(CONTEXTUAL_SIGNALS)
                        .setBiddingLogicUri(BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        assertEquals(AD_SELECTION_ID, dbAdSelection.getAdSelectionId());
        assertEquals(CUSTOM_AUDIENCE_SIGNALS, dbAdSelection.getCustomAudienceSignals());
        assertEquals(CONTEXTUAL_SIGNALS, dbAdSelection.getContextualSignals());
        assertEquals(BIDDING_LOGIC_URI, dbAdSelection.getBiddingLogicUri());
        assertEquals(RENDER_URI, dbAdSelection.getWinningAdRenderUri());
        assertEquals(BID, dbAdSelection.getWinningAdBid(), 0);
        assertEquals(ACTIVATION_TIME, dbAdSelection.getCreationTimestamp());
        assertEquals(CALLER_PACKAGE_NAME, dbAdSelection.getCallerPackageName());
    }

    @Test
    public void testFailsToBuildContextualAdWithNonNullBiddingLogicUri() {

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new DBAdSelection.Builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setContextualSignals(CONTEXTUAL_SIGNALS)
                            .setBiddingLogicUri(BIDDING_LOGIC_URI)
                            .setWinningAdRenderUri(RENDER_URI)
                            .setWinningAdBid(BID)
                            .setCreationTimestamp(ACTIVATION_TIME)
                            .setCallerPackageName(CALLER_PACKAGE_NAME)
                            .build();
                });
    }

    @Test
    public void testFailsToBuildContextualAdWithNonNullCustomAudienceSignals() {

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new DBAdSelection.Builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                            .setContextualSignals(CONTEXTUAL_SIGNALS)
                            .setWinningAdRenderUri(RENDER_URI)
                            .setWinningAdBid(BID)
                            .setCreationTimestamp(ACTIVATION_TIME)
                            .setCallerPackageName(CALLER_PACKAGE_NAME)
                            .build();
                });
    }

    @Test
    public void testFailsToBuildDBAdSelectionWithUnsetAdSelectionId() {

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new DBAdSelection.Builder()
                            .setAdSelectionId(0)
                            .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                            .setContextualSignals(CONTEXTUAL_SIGNALS)
                            .setBiddingLogicUri(BIDDING_LOGIC_URI)
                            .setWinningAdRenderUri(RENDER_URI)
                            .setWinningAdBid(BID)
                            .setCreationTimestamp(ACTIVATION_TIME)
                            .setCallerPackageName(CALLER_PACKAGE_NAME)
                            .build();
                });
    }

    @Test
    public void testFailsToBuildDBAdSelectionWithNullCallerPackageName() {

        assertThrows(
                NullPointerException.class,
                () -> {
                    new DBAdSelection.Builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                            .setContextualSignals(CONTEXTUAL_SIGNALS)
                            .setBiddingLogicUri(BIDDING_LOGIC_URI)
                            .setWinningAdRenderUri(RENDER_URI)
                            .setWinningAdBid(BID)
                            .setCreationTimestamp(ACTIVATION_TIME)
                            .build();
                });
    }

    @Test
    public void testEqualDBAdSelectionObjectsHaveSameHashCode() {
        DBAdSelection obj1 =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                        .setContextualSignals(CONTEXTUAL_SIGNALS)
                        .setBiddingLogicUri(BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        DBAdSelection obj2 =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                        .setContextualSignals(CONTEXTUAL_SIGNALS)
                        .setBiddingLogicUri(BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        CommonFixture.assertHaveSameHashCode(obj1, obj2);
    }

    @Test
    public void testNotEqualDBAdSelectionObjectsHaveDifferentHashCode() {
        DBAdSelection obj1 =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                        .setContextualSignals(CONTEXTUAL_SIGNALS)
                        .setBiddingLogicUri(BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        DBAdSelection obj2 =
                new DBAdSelection.Builder()
                        .setAdSelectionId(2)
                        .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                        .setContextualSignals(CONTEXTUAL_SIGNALS)
                        .setBiddingLogicUri(BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        DBAdSelection obj3 =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                        .setContextualSignals(CONTEXTUAL_SIGNALS)
                        .setBiddingLogicUri(BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(10)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        CommonFixture.assertDifferentHashCode(obj1, obj2, obj3);
    }
}
