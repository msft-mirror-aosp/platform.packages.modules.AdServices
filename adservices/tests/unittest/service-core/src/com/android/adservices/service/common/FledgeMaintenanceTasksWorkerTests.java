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

package com.android.adservices.service.common;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.adselection.DBBuyerDecisionLogic;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoSession;

import java.time.Clock;

public class FledgeMaintenanceTasksWorkerTests {
    private static final Flags TEST_FLAGS = FlagsFactory.getFlagsForTest();
    private static final Uri BIDDING_LOGIC_URI = Uri.parse("https://biddinglogic.com");
    private static final Uri EXPIRED_BIDDING_LOGIC_URI =
            Uri.parse("https://expiredbiddinglogic.com");

    private static final long AD_SELECTION_ID_1 = 12345L;
    private static final long AD_SELECTION_ID_2 = 23456L;

    private static final DBAdSelection DB_AD_SELECTION =
            new DBAdSelection.Builder()
                    .setAdSelectionId(AD_SELECTION_ID_1)
                    .setCustomAudienceSignals(CustomAudienceSignalsFixture.aCustomAudienceSignals())
                    .setContextualSignals("contextualSignals")
                    .setBiddingLogicUri(BIDDING_LOGIC_URI)
                    .setWinningAdRenderUri(Uri.parse("https://winningAd.com"))
                    .setWinningAdBid(5)
                    .setCreationTimestamp(Clock.systemUTC().instant())
                    .setCallerPackageName("testPackageName")
                    .build();

    private static final DBBuyerDecisionLogic DB_BUYER_DECISION_LOGIC =
            new DBBuyerDecisionLogic.Builder()
                    .setBuyerDecisionLogicJs("buyerDecisionLogicJS")
                    .setBiddingLogicUri(BIDDING_LOGIC_URI)
                    .build();

    private static final DBAdSelection EXPIRED_DB_AD_SELECTION =
            new DBAdSelection.Builder()
                    .setAdSelectionId(AD_SELECTION_ID_2)
                    .setCustomAudienceSignals(CustomAudienceSignalsFixture.aCustomAudienceSignals())
                    .setContextualSignals("contextualSignals")
                    .setBiddingLogicUri(EXPIRED_BIDDING_LOGIC_URI)
                    .setWinningAdRenderUri(Uri.parse("https://winningAd.com"))
                    .setWinningAdBid(5)
                    .setCreationTimestamp(
                            Clock.systemUTC()
                                    .instant()
                                    .minusSeconds(2 * TEST_FLAGS.getAdSelectionExpirationWindowS()))
                    .setCallerPackageName("testPackageName")
                    .build();

    private static final DBBuyerDecisionLogic EXPIRED_DB_BUYER_DECISION_LOGIC =
            new DBBuyerDecisionLogic.Builder()
                    .setBuyerDecisionLogicJs("buyerDecisionLogicJS")
                    .setBiddingLogicUri(EXPIRED_BIDDING_LOGIC_URI)
                    .build();

    private AdSelectionEntryDao mAdSelectionEntryDao;
    private FledgeMaintenanceTasksWorker mFledgeMaintenanceTasksWorker;

    @Before
    public void setup() {
        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        mFledgeMaintenanceTasksWorker = new FledgeMaintenanceTasksWorker(mAdSelectionEntryDao);
    }

    @Test
    public void testFledgeMaintenanceWorkerDoesNotRemoveValidData() throws Exception {
        // Add valid and expired ad selections
        mAdSelectionEntryDao.persistAdSelection(DB_AD_SELECTION);

        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(DB_AD_SELECTION.getAdSelectionId()));

        // Add valid and expired buyer decision logics
        mAdSelectionEntryDao.persistBuyerDecisionLogic(DB_BUYER_DECISION_LOGIC);

        assertTrue(
                mAdSelectionEntryDao.doesBuyerDecisionLogicExist(
                        DB_BUYER_DECISION_LOGIC.getBiddingLogicUri()));

        // Assert that valid data was not cleared
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(DB_AD_SELECTION.getAdSelectionId()));
        assertTrue(
                mAdSelectionEntryDao.doesBuyerDecisionLogicExist(
                        DB_BUYER_DECISION_LOGIC.getBiddingLogicUri()));
    }

    @Test
    public void testFledgeMaintenanceWorkerRemovesExpiredData() throws Exception {
        MockitoSession mockitoSession =
                ExtendedMockito.mockitoSession().spyStatic(FlagsFactory.class).startMocking();

        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        ExtendedMockito.doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        // Add valid and expired ad selections
        mAdSelectionEntryDao.persistAdSelection(DB_AD_SELECTION);
        mAdSelectionEntryDao.persistAdSelection(EXPIRED_DB_AD_SELECTION);

        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(DB_AD_SELECTION.getAdSelectionId()));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionIdExist(
                        EXPIRED_DB_AD_SELECTION.getAdSelectionId()));

        // Add valid and expired buyer decision logics
        mAdSelectionEntryDao.persistBuyerDecisionLogic(DB_BUYER_DECISION_LOGIC);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(EXPIRED_DB_BUYER_DECISION_LOGIC);

        assertTrue(
                mAdSelectionEntryDao.doesBuyerDecisionLogicExist(
                        DB_BUYER_DECISION_LOGIC.getBiddingLogicUri()));
        assertTrue(
                mAdSelectionEntryDao.doesBuyerDecisionLogicExist(
                        EXPIRED_DB_BUYER_DECISION_LOGIC.getBiddingLogicUri()));

        mFledgeMaintenanceTasksWorker.clearExpiredAdSelectionData();

        // Assert expired data was removed
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionIdExist(
                        EXPIRED_DB_AD_SELECTION.getAdSelectionId()));
        assertFalse(
                mAdSelectionEntryDao.doesBuyerDecisionLogicExist(
                        EXPIRED_DB_BUYER_DECISION_LOGIC.getBiddingLogicUri()));

        // Assert that valid data was not cleared
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(DB_AD_SELECTION.getAdSelectionId()));
        assertTrue(
                mAdSelectionEntryDao.doesBuyerDecisionLogicExist(
                        DB_BUYER_DECISION_LOGIC.getBiddingLogicUri()));

        mockitoSession.finishMocking();
    }
}
