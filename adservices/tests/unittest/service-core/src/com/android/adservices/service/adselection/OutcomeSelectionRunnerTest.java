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

import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.content.Context;
import android.net.Uri;
import android.os.Process;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdServicesHttpsClient;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class OutcomeSelectionRunnerTest {
    // Time allowed by current test async calls to respond
    private static final int RESPONSE_TIMEOUT_SECONDS = 3;

    public static final DBAdSelection DB_AD_SELECTION_1 =
            new DBAdSelection.Builder()
                    .setAdSelectionId(1)
                    .setCustomAudienceSignals(CustomAudienceSignalsFixture.aCustomAudienceSignals())
                    .setContextualSignals("contextual_signals")
                    .setBiddingLogicUri(Uri.parse("http://www.domain.com/logic/1"))
                    .setWinningAdRenderUri(Uri.parse("http://www.domain.com/advert/"))
                    .setWinningAdBid(10)
                    .setCreationTimestamp(Instant.now())
                    .setCallerPackageName("callerPackageName1")
                    .build();

    public static final DBAdSelection DB_AD_SELECTION_2 =
            new DBAdSelection.Builder()
                    .setAdSelectionId(2)
                    .setCustomAudienceSignals(CustomAudienceSignalsFixture.aCustomAudienceSignals())
                    .setContextualSignals("contextual_signals")
                    .setBiddingLogicUri(Uri.parse("http://www.domain.com/logic/2"))
                    .setWinningAdRenderUri(Uri.parse("http://www.domain.com/advert/"))
                    .setWinningAdBid(20)
                    .setCreationTimestamp(Instant.now())
                    .setCallerPackageName("callerPackageName2")
                    .build();

    public static final DBAdSelection DB_AD_SELECTION_3 =
            new DBAdSelection.Builder()
                    .setAdSelectionId(3)
                    .setCustomAudienceSignals(CustomAudienceSignalsFixture.aCustomAudienceSignals())
                    .setContextualSignals("contextual_signals")
                    .setBiddingLogicUri(Uri.parse("http://www.domain.com/logic/2"))
                    .setWinningAdRenderUri(Uri.parse("http://www.domain.com/advert/"))
                    .setWinningAdBid(30)
                    .setCreationTimestamp(Instant.now())
                    .setCallerPackageName("callerPackageName2")
                    .build();

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private static final int CALLER_UID = Process.myUid();
    private AdSelectionEntryDao mAdSelectionEntryDao;
    private final AdServicesHttpsClient mAdServicesHttpsClient =
            new AdServicesHttpsClient(AdServicesExecutors.getBlockingExecutor());
    private OutcomeSelectionRunner mOutcomeSelectionRunner;
    private final Flags mFlags =
            new Flags() {
                @Override
                public long getAdSelectionSelectingOutcomeTimeoutMs() {
                    return 300;
                }

                @Override
                public boolean getDisableFledgeEnrollmentCheck() {
                    return true;
                }
            };

    private final AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);

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
                        CALLER_UID,
                        mAdSelectionEntryDao,
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        AdServicesExecutors.getScheduler(),
                        mAdServicesHttpsClient,
                        mAdServicesLoggerMock,
                        mContext,
                        mFlags);
    }

    @Test
    public void testRetrieveOutcomesAndBidsFromDbSuccess()
            throws ExecutionException, InterruptedException, TimeoutException {
        List<DBAdSelection> adSelectionResults =
                List.of(DB_AD_SELECTION_1, DB_AD_SELECTION_2, DB_AD_SELECTION_3);
        for (DBAdSelection selection : adSelectionResults) {
            mAdSelectionEntryDao.persistAdSelection(selection);
        }

        List<Long> adOutcomeIds =
                List.of(
                        DB_AD_SELECTION_1.getAdSelectionId(),
                        DB_AD_SELECTION_2.getAdSelectionId(),
                        DB_AD_SELECTION_3.getAdSelectionId());

        List<AdSelectionIdWithBidAndRenderUri> adSelectionIdWithBidAndRenderUriList =
                mOutcomeSelectionRunner
                        .retrieveAdSelectionIdWithBidList(adOutcomeIds)
                        .get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Map<Long, Double> helperMap =
                adSelectionIdWithBidAndRenderUriList.stream()
                        .collect(
                                Collectors.toMap(
                                        AdSelectionIdWithBidAndRenderUri::getAdSelectionId,
                                        AdSelectionIdWithBidAndRenderUri::getBid));

        for (DBAdSelection selection : adSelectionResults) {
            assertTrue(
                    String.format(
                            "Ad selection id %s is missing from db results",
                            selection.getAdSelectionId()),
                    helperMap.containsKey(selection.getAdSelectionId()));
            assertEquals(
                    String.format(
                            "Bid values are not equal for ad selection id %s",
                            selection.getAdSelectionId()),
                    helperMap.get(selection.getAdSelectionId()),
                    selection.getWinningAdBid(),
                    0.0);
        }
    }

    @Test
    public void testIgnoresAdSelectionIdsNotInDbSuccess()
            throws ExecutionException, InterruptedException, TimeoutException {
        List<DBAdSelection> adSelectionResults =
                List.of(DB_AD_SELECTION_1, DB_AD_SELECTION_2, DB_AD_SELECTION_3);

        List<Long> adOutcomeIds =
                List.of(
                        DB_AD_SELECTION_1.getAdSelectionId(),
                        DB_AD_SELECTION_2.getAdSelectionId(),
                        DB_AD_SELECTION_3.getAdSelectionId());

        List<AdSelectionIdWithBidAndRenderUri> adSelectionIdWithBidAndRenderUriList =
                mOutcomeSelectionRunner
                        .retrieveAdSelectionIdWithBidList(adOutcomeIds)
                        .get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Map<Long, Double> helperMap =
                adSelectionIdWithBidAndRenderUriList.stream()
                        .collect(
                                Collectors.toMap(
                                        AdSelectionIdWithBidAndRenderUri::getAdSelectionId,
                                        AdSelectionIdWithBidAndRenderUri::getBid));

        for (DBAdSelection selection : adSelectionResults) {
            assertFalse(
                    String.format(
                            "Ad selection id %s is missing from db results",
                            selection.getAdSelectionId()),
                    helperMap.containsKey(selection.getAdSelectionId()));
        }
    }
}
