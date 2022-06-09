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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION;
import static com.android.adservices.stats.FledgeApiCallStatsMatcher.aCallStatForFledgeApiWithStatus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.adselection.AdBiddingOutcomeFixture;
import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.TrustedBiddingDataFixture;
import android.adservices.exceptions.AdServicesException;
import android.content.Context;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.adselection.DBAdSelectionEntry;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * This test covers strictly the unit of {@link AdSelectionRunner} The dependencies in this test are
 * mocked and provide expected mock responses when invoked with desired input
 */
public class AdSelectionRunnerTest {
    private static final String TAG = AdSelectionRunnerTest.class.getName();

    private static final String FAILURE_RESPONSE = "Encountered failure during Ad Selection";

    private static final String BUYER_1 = AdSelectionConfigFixture.BUYER_1;
    private static final String BUYER_2 = AdSelectionConfigFixture.BUYER_2;
    private static final Long AD_SELECTION_ID = 1234L;
    private static final Instant MOCKED_AD_SELECTION_CREATION_TS = Instant.ofEpochMilli(12345L);

    @Mock private AdsScoreGenerator mMockAdsScoreGenerator;
    @Mock private AdBidGenerator mMockAdBidGenerator;
    @Mock private AdSelectionIdGenerator mMockAdSelectionIdGenerator;
    @Mock private Clock mClock;

    private Context mContext;
    private ExecutorService mExecutorService;
    private CustomAudienceDao mCustomAudienceDao;
    private AdSelectionEntryDao mAdSelectionEntryDao;
    @Spy private AdServicesLogger mAdServicesLoggerSpy = AdServicesLoggerImpl.getInstance();

    private AdSelectionConfig.Builder mAdSelectionConfigBuilder;

    private DBCustomAudience mDBCustomAudienceForBuyer1;
    private DBCustomAudience mDBCustomAudienceForBuyer2;
    private List<DBCustomAudience> mBuyerCustomAudienceList;

    private AdBiddingOutcome mAdBiddingOutcomeForBuyer1;
    private AdBiddingOutcome mAdBiddingOutcomeForBuyer2;
    private List<AdBiddingOutcome> mAdBiddingOutcomeList;

    private AdScoringOutcome mAdScoringOutcomeForBuyer1;
    private AdScoringOutcome mAdScoringOutcomeForBuyer2;
    private List<AdScoringOutcome> mAdScoringOutcomeList;

    private AdSelectionRunner mAdSelectionRunner;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = ApplicationProvider.getApplicationContext();
        mExecutorService = Executors.newFixedThreadPool(20);
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(mContext, CustomAudienceDatabase.class)
                        .build()
                        .customAudienceDao();

        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(mContext, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        mAdSelectionConfigBuilder =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(Arrays.asList(BUYER_1, BUYER_2));

        mDBCustomAudienceForBuyer1 = createDBCustomAudience(BUYER_1);
        mDBCustomAudienceForBuyer2 = createDBCustomAudience(BUYER_2);
        mBuyerCustomAudienceList =
                Arrays.asList(mDBCustomAudienceForBuyer1, mDBCustomAudienceForBuyer2);

        mAdBiddingOutcomeForBuyer1 =
                AdBiddingOutcomeFixture.anAdBiddingOutcomeBuilder(BUYER_1, 1.0).build();
        mAdBiddingOutcomeForBuyer2 =
                AdBiddingOutcomeFixture.anAdBiddingOutcomeBuilder(BUYER_2, 2.0).build();
        mAdBiddingOutcomeList =
                Arrays.asList(mAdBiddingOutcomeForBuyer1, mAdBiddingOutcomeForBuyer2);

        mAdScoringOutcomeForBuyer1 =
                AdScoringOutcomeFixture.anAdScoringBuilder(BUYER_1, 2.0).build();
        mAdScoringOutcomeForBuyer2 =
                AdScoringOutcomeFixture.anAdScoringBuilder(BUYER_2, 3.0).build();
        mAdScoringOutcomeList =
                Arrays.asList(mAdScoringOutcomeForBuyer1, mAdScoringOutcomeForBuyer2);

        when(mClock.instant()).thenReturn(MOCKED_AD_SELECTION_CREATION_TS);
    }

    private DBCustomAudience createDBCustomAudience(final String buyer) {
        return new DBCustomAudience.Builder()
                .setOwner(buyer + CustomAudienceFixture.VALID_OWNER)
                .setBuyer(buyer)
                .setName(buyer + CustomAudienceFixture.VALID_NAME)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setLastAdsAndBiddingDataUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setDailyUpdateUrl(CustomAudienceFixture.VALID_DAILY_UPDATE_URL)
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        new DBTrustedBiddingData.Builder()
                                .setUrl(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_URL)
                                .setKeys(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_KEYS)
                                .build())
                .setBiddingLogicUrl(CustomAudienceFixture.VALID_BIDDING_LOGIC_URL)
                .setAds(
                        AdDataFixture.VALID_ADS.stream()
                                .map(DBAdData::fromServiceObject)
                                .collect(Collectors.toList()))
                .build();
    }

    @Test
    public void testRunAdSelectionSuccess() throws AdServicesException {

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverrideCustomAudience(mDBCustomAudienceForBuyer1);
        mCustomAudienceDao.insertOrOverrideCustomAudience(mDBCustomAudienceForBuyer2);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        when(mMockAdBidGenerator.runAdBiddingPerCA(
                        mDBCustomAudienceForBuyer1,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig
                                .getPerBuyerSignals()
                                .get(mDBCustomAudienceForBuyer1.getBuyer()),
                        "{}",
                        adSelectionConfig))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)));
        when(mMockAdBidGenerator.runAdBiddingPerCA(
                        mDBCustomAudienceForBuyer2,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig
                                .getPerBuyerSignals()
                                .get(mDBCustomAudienceForBuyer2.getBuyer()),
                        "{}",
                        adSelectionConfig))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)));

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(mAdScoringOutcomeList))));

        DBAdSelectionEntry expectedAdSelectionResult =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2
                                        .getCustomAudienceBiddingInfo()
                                        .getCustomAudienceSignals())
                        .setWinningAdRenderUrl(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUrl())
                        .setBuyerDecisionLogicJs(
                                mAdBiddingOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBuyerDecisionLogicJs())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setContextualSignals("{}")
                        .setCreationTimestamp(MOCKED_AD_SELECTION_CREATION_TS)
                        .build();

        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);

        mAdSelectionRunner =
                new AdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mExecutorService,
                        mMockAdsScoreGenerator,
                        mMockAdBidGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerSpy);

        assertFalse(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedAdSelectionResult.getWinningAdRenderUrl(),
                resultsCallback.mAdSelectionResponse.getRenderUrl());
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertEquals(
                expectedAdSelectionResult,
                mAdSelectionEntryDao.getAdSelectionEntityById(AD_SELECTION_ID));

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION,
                        AdServicesStatusUtils.STATUS_SUCCESS);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION,
                                AdServicesStatusUtils.STATUS_SUCCESS));
    }

    @Test
    public void testRunAdSelectionMissingBuyerSignals() throws AdServicesException {

        // Creating ad selection config with missing Buyer signals to test the fallback
        AdSelectionConfig adSelectionConfig =
                mAdSelectionConfigBuilder.setPerBuyerSignals(Collections.EMPTY_MAP).build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverrideCustomAudience(mDBCustomAudienceForBuyer1);
        mCustomAudienceDao.insertOrOverrideCustomAudience(mDBCustomAudienceForBuyer2);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        when(mMockAdBidGenerator.runAdBiddingPerCA(
                        mDBCustomAudienceForBuyer1,
                        adSelectionConfig.getAdSelectionSignals(),
                        "{}",
                        "{}",
                        adSelectionConfig))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)));
        when(mMockAdBidGenerator.runAdBiddingPerCA(
                        mDBCustomAudienceForBuyer2,
                        adSelectionConfig.getAdSelectionSignals(),
                        "{}",
                        "{}",
                        adSelectionConfig))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)));

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(mAdScoringOutcomeList))));

        DBAdSelection expectedDBAdSelectionResult =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCreationTimestamp(Calendar.getInstance().toInstant())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2
                                        .getCustomAudienceBiddingInfo()
                                        .getCustomAudienceSignals())
                        .setWinningAdRenderUrl(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUrl())
                        .setBiddingLogicUrl(
                                mAdScoringOutcomeForBuyer2
                                        .getCustomAudienceBiddingInfo()
                                        .getBiddingLogicUrl())
                        .setContextualSignals("{}")
                        .build();

        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);

        mAdSelectionRunner =
                new AdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mExecutorService,
                        mMockAdsScoreGenerator,
                        mMockAdBidGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerSpy);

        assertFalse(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedDBAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedDBAdSelectionResult.getWinningAdRenderUrl(),
                resultsCallback.mAdSelectionResponse.getRenderUrl());
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID));

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION,
                        AdServicesStatusUtils.STATUS_SUCCESS);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION,
                                AdServicesStatusUtils.STATUS_SUCCESS));
    }

    @Test
    public void testRunAdSelectionNoCAs() {

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Do not populate CustomAudience DAO

        // If there are no corresponding CAs we should not even attempt bidding
        verifyZeroInteractions(mMockAdBidGenerator);
        // If there was no bidding then we should not even attempt to run scoring
        verifyZeroInteractions(mMockAdsScoreGenerator);

        mAdSelectionRunner =
                new AdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mExecutorService,
                        mMockAdsScoreGenerator,
                        mMockAdBidGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerSpy);
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig);

        assertFalse(resultsCallback.mIsSuccess);
        assertEquals(FAILURE_RESPONSE, resultsCallback.mFledgeErrorResponse.getErrorMessage());

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION,
                        AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION,
                                AdServicesStatusUtils.STATUS_INTERNAL_ERROR));
    }

    @Test
    public void testRunAdSelectionPartialBidding() throws AdServicesException {

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverrideCustomAudience(mDBCustomAudienceForBuyer1);
        mCustomAudienceDao.insertOrOverrideCustomAudience(mDBCustomAudienceForBuyer2);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        // In this case assuming bidding fails for one of ads and return partial result
        when(mMockAdBidGenerator.runAdBiddingPerCA(
                        mDBCustomAudienceForBuyer1,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig
                                .getPerBuyerSignals()
                                .get(mDBCustomAudienceForBuyer1.getBuyer()),
                        "{}",
                        adSelectionConfig))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)));
        when(mMockAdBidGenerator.runAdBiddingPerCA(
                        mDBCustomAudienceForBuyer2,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig
                                .getPerBuyerSignals()
                                .get(mDBCustomAudienceForBuyer2.getBuyer()),
                        "{}",
                        adSelectionConfig))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(null)));

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        // In this case we are only expected to get score for the first bidding,
        // as second one is null
        List<AdBiddingOutcome> partialBiddingOutcome = Arrays.asList(mAdBiddingOutcomeForBuyer1);
        when(mMockAdsScoreGenerator.runAdScoring(partialBiddingOutcome, adSelectionConfig))
                .thenReturn(
                        (FluentFuture.from(
                                Futures.immediateFuture(mAdScoringOutcomeList.subList(0, 1)))));

        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);

        mAdSelectionRunner =
                new AdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mExecutorService,
                        mMockAdsScoreGenerator,
                        mMockAdBidGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerSpy);

        assertFalse(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig);

        DBAdSelection expectedDBAdSelectionResult =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCreationTimestamp(Calendar.getInstance().toInstant())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer1.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getCustomAudienceSignals())
                        .setWinningAdRenderUrl(
                                mAdScoringOutcomeForBuyer1
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUrl())
                        .setBiddingLogicUrl(
                                mAdScoringOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBiddingLogicUrl())
                        .setContextualSignals("{}")
                        .build();

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedDBAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedDBAdSelectionResult.getWinningAdRenderUrl(),
                resultsCallback.mAdSelectionResponse.getRenderUrl());
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID));

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION,
                        AdServicesStatusUtils.STATUS_SUCCESS);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION,
                                AdServicesStatusUtils.STATUS_SUCCESS));
    }

    @Test
    public void testRunAdSelectionBiddingFailure() {

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverrideCustomAudience(mDBCustomAudienceForBuyer1);
        mCustomAudienceDao.insertOrOverrideCustomAudience(mDBCustomAudienceForBuyer2);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        // In this case assuming bidding fails and returns null
        when(mMockAdBidGenerator.runAdBiddingPerCA(
                        mDBCustomAudienceForBuyer1,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig
                                .getPerBuyerSignals()
                                .get(mDBCustomAudienceForBuyer1.getBuyer()),
                        "{}",
                        adSelectionConfig))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(null)));
        when(mMockAdBidGenerator.runAdBiddingPerCA(
                        mDBCustomAudienceForBuyer2,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig
                                .getPerBuyerSignals()
                                .get(mDBCustomAudienceForBuyer2.getBuyer()),
                        "{}",
                        adSelectionConfig))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(null)));

        // If the result of bidding is empty, then we should not even attempt to run scoring
        verifyZeroInteractions(mMockAdsScoreGenerator);

        mAdSelectionRunner =
                new AdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mExecutorService,
                        mMockAdsScoreGenerator,
                        mMockAdBidGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerSpy);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig);

        assertFalse(resultsCallback.mIsSuccess);
        assertEquals(FAILURE_RESPONSE, resultsCallback.mFledgeErrorResponse.getErrorMessage());

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION,
                        AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION,
                                AdServicesStatusUtils.STATUS_INTERNAL_ERROR));
    }

    @Test
    public void testRunAdSelectionScoringFailure() throws AdServicesException {

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverrideCustomAudience(mDBCustomAudienceForBuyer1);
        mCustomAudienceDao.insertOrOverrideCustomAudience(mDBCustomAudienceForBuyer2);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        when(mMockAdBidGenerator.runAdBiddingPerCA(
                        mDBCustomAudienceForBuyer1,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig
                                .getPerBuyerSignals()
                                .get(mDBCustomAudienceForBuyer1.getBuyer()),
                        "{}",
                        adSelectionConfig))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)));
        when(mMockAdBidGenerator.runAdBiddingPerCA(
                        mDBCustomAudienceForBuyer2,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig
                                .getPerBuyerSignals()
                                .get(mDBCustomAudienceForBuyer2.getBuyer()),
                        "{}",
                        adSelectionConfig))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)));

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        // In this case assuming we get an empty result
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(Collections.EMPTY_LIST))));

        mAdSelectionRunner =
                new AdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mExecutorService,
                        mMockAdsScoreGenerator,
                        mMockAdBidGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerSpy);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig);

        assertFalse(resultsCallback.mIsSuccess);
        assertEquals(FAILURE_RESPONSE, resultsCallback.mFledgeErrorResponse.getErrorMessage());

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION,
                        AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION,
                                AdServicesStatusUtils.STATUS_INTERNAL_ERROR));
    }

    @Test
    public void testRunAdSelectionNegativeScoring() throws AdServicesException {

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverrideCustomAudience(mDBCustomAudienceForBuyer1);
        mCustomAudienceDao.insertOrOverrideCustomAudience(mDBCustomAudienceForBuyer2);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        when(mMockAdBidGenerator.runAdBiddingPerCA(
                        mDBCustomAudienceForBuyer1,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig
                                .getPerBuyerSignals()
                                .get(mDBCustomAudienceForBuyer1.getBuyer()),
                        "{}",
                        adSelectionConfig))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)));
        when(mMockAdBidGenerator.runAdBiddingPerCA(
                        mDBCustomAudienceForBuyer2,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig
                                .getPerBuyerSignals()
                                .get(mDBCustomAudienceForBuyer2.getBuyer()),
                        "{}",
                        adSelectionConfig))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)));

        AdScoringOutcome adScoringNegativeOutcomeForBuyer1 =
                AdScoringOutcomeFixture.anAdScoringBuilder(BUYER_1, -2.0).build();
        AdScoringOutcome adScoringNegativeOutcomeForBuyer2 =
                AdScoringOutcomeFixture.anAdScoringBuilder(BUYER_2, -3.0).build();
        List<AdScoringOutcome> negativeScoreOutcome =
                Arrays.asList(adScoringNegativeOutcomeForBuyer1, adScoringNegativeOutcomeForBuyer2);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        // In this case assuming we get a result with negative scores
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(negativeScoreOutcome))));

        mAdSelectionRunner =
                new AdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mExecutorService,
                        mMockAdsScoreGenerator,
                        mMockAdBidGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerSpy);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig);

        assertFalse(resultsCallback.mIsSuccess);
        assertEquals(FAILURE_RESPONSE, resultsCallback.mFledgeErrorResponse.getErrorMessage());

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION,
                        AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION,
                                AdServicesStatusUtils.STATUS_INTERNAL_ERROR));
    }

    @Test
    public void testRunAdSelectionPartialNegativeScoring() throws AdServicesException {

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverrideCustomAudience(mDBCustomAudienceForBuyer1);
        mCustomAudienceDao.insertOrOverrideCustomAudience(mDBCustomAudienceForBuyer2);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        when(mMockAdBidGenerator.runAdBiddingPerCA(
                        mDBCustomAudienceForBuyer1,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig
                                .getPerBuyerSignals()
                                .get(mDBCustomAudienceForBuyer1.getBuyer()),
                        "{}",
                        adSelectionConfig))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)));
        when(mMockAdBidGenerator.runAdBiddingPerCA(
                        mDBCustomAudienceForBuyer2,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig
                                .getPerBuyerSignals()
                                .get(mDBCustomAudienceForBuyer2.getBuyer()),
                        "{}",
                        adSelectionConfig))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)));

        AdScoringOutcome adScoringNegativeOutcomeForBuyer1 =
                AdScoringOutcomeFixture.anAdScoringBuilder(BUYER_1, 2.0).build();
        AdScoringOutcome adScoringNegativeOutcomeForBuyer2 =
                AdScoringOutcomeFixture.anAdScoringBuilder(BUYER_2, -3.0).build();
        List<AdScoringOutcome> negativeScoreOutcome =
                Arrays.asList(adScoringNegativeOutcomeForBuyer1, adScoringNegativeOutcomeForBuyer2);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        // In this case assuming we get a result with partially negative scores
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(negativeScoreOutcome))));

        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);

        mAdSelectionRunner =
                new AdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mExecutorService,
                        mMockAdsScoreGenerator,
                        mMockAdBidGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerSpy);

        assertFalse(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig);

        DBAdSelection expectedDBAdSelectionResult =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCreationTimestamp(Calendar.getInstance().toInstant())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer1.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getCustomAudienceSignals())
                        .setWinningAdRenderUrl(
                                mAdScoringOutcomeForBuyer1
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUrl())
                        .setBiddingLogicUrl(
                                mAdScoringOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBiddingLogicUrl())
                        .setContextualSignals("{}")
                        .build();

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedDBAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedDBAdSelectionResult.getWinningAdRenderUrl(),
                resultsCallback.mAdSelectionResponse.getRenderUrl());
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID));

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION,
                        AdServicesStatusUtils.STATUS_SUCCESS);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION,
                                AdServicesStatusUtils.STATUS_SUCCESS));
    }

    @Test
    public void testRunAdSelectionScoringException() throws AdServicesException {

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig =
                mAdSelectionConfigBuilder.setAdSelectionSignals("{/}").build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverrideCustomAudience(mDBCustomAudienceForBuyer1);
        mCustomAudienceDao.insertOrOverrideCustomAudience(mDBCustomAudienceForBuyer2);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        when(mMockAdBidGenerator.runAdBiddingPerCA(
                        mDBCustomAudienceForBuyer1,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig
                                .getPerBuyerSignals()
                                .get(mDBCustomAudienceForBuyer1.getBuyer()),
                        "{}",
                        adSelectionConfig))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)));
        when(mMockAdBidGenerator.runAdBiddingPerCA(
                        mDBCustomAudienceForBuyer2,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig
                                .getPerBuyerSignals()
                                .get(mDBCustomAudienceForBuyer2.getBuyer()),
                        "{}",
                        adSelectionConfig))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)));

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        // In this case we expect a JSON validation exception
        when(mMockAdsScoreGenerator.runAdScoring(Collections.EMPTY_LIST, adSelectionConfig))
                .thenThrow(new AdServicesException("Invalid Json Exception"));

        mAdSelectionRunner =
                new AdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mExecutorService,
                        mMockAdsScoreGenerator,
                        mMockAdBidGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerSpy);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig);

        assertFalse(resultsCallback.mIsSuccess);
        assertEquals(FAILURE_RESPONSE, resultsCallback.mFledgeErrorResponse.getErrorMessage());

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION,
                        AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION,
                                AdServicesStatusUtils.STATUS_INTERNAL_ERROR));
    }

    private AdSelectionTestCallback invokeRunAdSelection(
            AdSelectionRunner adSelectionRunner, AdSelectionConfig adSelectionConfig) {

        // Counted down in 1) callback and 2) logApiCall
        CountDownLatch countDownLatch = new CountDownLatch(2);
        AdSelectionTestCallback adSelectionTestCallback =
                new AdSelectionTestCallback(countDownLatch);

        // Wait for the logging call, which happens after the callback
        Answer<Void> countDownAnswer =
                unused -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer).when(mAdServicesLoggerSpy).logApiCallStats(any());

        adSelectionRunner.runAdSelection(adSelectionConfig, adSelectionTestCallback);
        try {
            adSelectionTestCallback.mCountDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return adSelectionTestCallback;
    }

    @After
    public void tearDown() {
        mAdSelectionEntryDao.removeAdSelectionEntriesByIds(Arrays.asList(AD_SELECTION_ID));
        mExecutorService.shutdown();
    }

    static class AdSelectionTestCallback extends AdSelectionCallback.Stub {

        final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        AdSelectionResponse mAdSelectionResponse;
        FledgeErrorResponse mFledgeErrorResponse;

        AdSelectionTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
            mAdSelectionResponse = null;
            mFledgeErrorResponse = null;
        }

        @Override
        public void onSuccess(AdSelectionResponse adSelectionResponse) throws RemoteException {
            mIsSuccess = true;
            mAdSelectionResponse = adSelectionResponse;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mIsSuccess = false;
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }
}
