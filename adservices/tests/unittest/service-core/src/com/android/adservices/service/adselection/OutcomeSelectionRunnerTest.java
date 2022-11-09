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

package com.android.adservices.service.adselection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.AdSelectionOutcomeFixture;
import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.DBAdSelection;

import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class OutcomeSelectionRunnerTest {
    // Time allowed by current test async calls to respond
    private static final int RESPONSE_TIMEOUT_SECONDS = 3;

    private static final Uri BIDDING_LOGIC_URI_1 = Uri.parse("http://www.domain.com/logic/1");
    private static final Uri BIDDING_LOGIC_URI_2 = Uri.parse("http://www.domain.com/logic/2");

    private static final Uri RENDER_URI = Uri.parse("http://www.domain.com/advert/");

    private static final Instant ACTIVATION_TIME = Instant.now();

    private static final long AD_SELECTION_ID_1 = 1;
    private static final long AD_SELECTION_ID_2 = 2;
    private static final long AD_SELECTION_ID_3 = 3;
    private static final String CONTEXTUAL_SIGNALS = "contextual_signals";

    private static final double BID_1 = 10;
    private static final double BID_2 = 20;
    private static final double BID_3 = 30;

    private static final String CALLER_PACKAGE_NAME_1 = "callerPackageName1";
    private static final String CALLER_PACKAGE_NAME_2 = "callerPackageName2";

    public static final CustomAudienceSignals CUSTOM_AUDIENCE_SIGNALS =
            CustomAudienceSignalsFixture.aCustomAudienceSignals();

    public static final DBAdSelection DB_AD_SELECTION_1 =
            new DBAdSelection.Builder()
                    .setAdSelectionId(AD_SELECTION_ID_1)
                    .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                    .setContextualSignals(CONTEXTUAL_SIGNALS)
                    .setBiddingLogicUri(BIDDING_LOGIC_URI_1)
                    .setWinningAdRenderUri(RENDER_URI)
                    .setWinningAdBid(BID_1)
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
                    .setWinningAdBid(BID_2)
                    .setCreationTimestamp(ACTIVATION_TIME)
                    .setCallerPackageName(CALLER_PACKAGE_NAME_2)
                    .build();

    public static final DBAdSelection DB_AD_SELECTION_3 =
            new DBAdSelection.Builder()
                    .setAdSelectionId(AD_SELECTION_ID_3)
                    .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                    .setContextualSignals(CONTEXTUAL_SIGNALS)
                    .setBiddingLogicUri(BIDDING_LOGIC_URI_2)
                    .setWinningAdRenderUri(RENDER_URI)
                    .setWinningAdBid(BID_3)
                    .setCreationTimestamp(ACTIVATION_TIME)
                    .setCallerPackageName(CALLER_PACKAGE_NAME_2)
                    .build();

    private AdSelectionEntryDao mAdSelectionEntryDao;
    private OutcomeSelectionRunner mOutcomeSelectionRunner;

    @Before
    public void setup() {
        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        mOutcomeSelectionRunner =
                new OutcomeSelectionRunner(
                        mAdSelectionEntryDao,
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor());
    }

    @Test
    public void testRetrieveOutcomesAndBidsFromDbSuccess()
            throws ExecutionException, InterruptedException, TimeoutException {
        List<DBAdSelection> adSelectionResults =
                List.of(DB_AD_SELECTION_1, DB_AD_SELECTION_2, DB_AD_SELECTION_3);
        for (DBAdSelection selection : adSelectionResults) {
            mAdSelectionEntryDao.persistAdSelection(selection);
        }

        List<AdSelectionOutcome> adOutcomes =
                List.of(
                        AdSelectionOutcomeFixture.anAdSelectionOutcome(
                                DB_AD_SELECTION_1.getAdSelectionId()),
                        AdSelectionOutcomeFixture.anAdSelectionOutcome(
                                DB_AD_SELECTION_2.getAdSelectionId()),
                        AdSelectionOutcomeFixture.anAdSelectionOutcome(
                                DB_AD_SELECTION_3.getAdSelectionId()));

        Map<Long, Double> adOutcomeBidPair =
                mOutcomeSelectionRunner
                        .retrieveAdSelectionIdToBidMap(adOutcomes)
                        .get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        for (DBAdSelection selection : adSelectionResults) {
            assertTrue(
                    String.format(
                            "Ad selection id %s is missing from db results",
                            selection.getAdSelectionId()),
                    adOutcomeBidPair.containsKey(selection.getAdSelectionId()));
            assertEquals(
                    String.format(
                            "Bid values are not equal for ad selection id %s",
                            selection.getAdSelectionId()),
                    adOutcomeBidPair.get(selection.getAdSelectionId()),
                    selection.getWinningAdBid(),
                    0.0);
        }
    }

    @Test
    public void testIgnoresAdSelectionIdsNotInDbSuccess()
            throws ExecutionException, InterruptedException, TimeoutException {
        List<DBAdSelection> adSelectionResults =
                List.of(DB_AD_SELECTION_1, DB_AD_SELECTION_2, DB_AD_SELECTION_3);

        List<AdSelectionOutcome> adOutcomes =
                List.of(
                        AdSelectionOutcomeFixture.anAdSelectionOutcome(
                                DB_AD_SELECTION_1.getAdSelectionId()),
                        AdSelectionOutcomeFixture.anAdSelectionOutcome(
                                DB_AD_SELECTION_2.getAdSelectionId()),
                        AdSelectionOutcomeFixture.anAdSelectionOutcome(
                                DB_AD_SELECTION_3.getAdSelectionId()));

        Map<Long, Double> adOutcomeBidPair =
                mOutcomeSelectionRunner
                        .retrieveAdSelectionIdToBidMap(adOutcomes)
                        .get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        for (DBAdSelection selection : adSelectionResults) {
            assertFalse(
                    String.format(
                            "Ad selection id %s is missing from db results",
                            selection.getAdSelectionId()),
                    adOutcomeBidPair.containsKey(selection.getAdSelectionId()));
        }
    }
}
