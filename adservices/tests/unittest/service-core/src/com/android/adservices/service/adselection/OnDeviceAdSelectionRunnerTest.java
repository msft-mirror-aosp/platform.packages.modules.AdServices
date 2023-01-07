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

import static android.adservices.common.AdServicesStatusUtils.ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_TIMEOUT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNSET;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;

import static com.android.adservices.data.adselection.AdSelectionDatabase.DATABASE_NAME;
import static com.android.adservices.service.adselection.AdSelectionRunner.AD_SELECTION_THROTTLED;
import static com.android.adservices.service.adselection.AdSelectionRunner.AD_SELECTION_TIMED_OUT;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_AD_SELECTION_FAILURE;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_NO_CA_AVAILABLE;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_NO_VALID_BIDS_FOR_SCORING;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_NO_WINNING_AD_FOUND;
import static com.android.adservices.service.adselection.AdSelectionRunner.JS_SANDBOX_IS_NOT_AVAILABLE;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_SELECT_ADS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.BIDDING_STAGE_END_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.BIDDING_STAGE_START_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.DB_AD_SELECTION_FILE_SIZE;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.GET_BUYERS_CUSTOM_AUDIENCE_LATENCY_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.IS_RMKT_ADS_WON;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.IS_RMKT_ADS_WON_UNSET;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.PERSIST_AD_SELECTION_END_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.PERSIST_AD_SELECTION_START_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.RUN_AD_BIDDING_END_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.RUN_AD_BIDDING_LATENCY_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.RUN_AD_BIDDING_START_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.RUN_AD_SELECTION_INTERNAL_FINAL_LATENCY_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.RUN_AD_SELECTION_OVERALL_LATENCY_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.START_ELAPSED_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.STOP_ELAPSED_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.TOTAL_BIDDING_STAGE_LATENCY_IN_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.sCallerMetadata;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyString;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyZeroInteractions;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;

import android.adservices.adselection.AdBiddingOutcomeFixture;
import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionInput;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.exceptions.AdServicesException;
import android.content.Context;
import android.net.Uri;
import android.os.Process;
import android.os.RemoteException;
import android.webkit.WebView;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.adselection.DBAdSelectionEntry;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AdServicesHttpsClient;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.AdSelectionExecutionLogger;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.RunAdBiddingProcessReportedStats;
import com.android.adservices.service.stats.RunAdSelectionProcessReportedStats;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.Returns;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This test covers strictly the unit of {@link AdSelectionRunner} The dependencies in this test are
 * mocked and provide expected mock responses when invoked with desired input
 */
public class OnDeviceAdSelectionRunnerTest {
    private static final String TAG = OnDeviceAdSelectionRunnerTest.class.getName();

    private static final AdTechIdentifier BUYER_1 = AdSelectionConfigFixture.BUYER_1;
    private static final AdTechIdentifier BUYER_2 = AdSelectionConfigFixture.BUYER_2;
    private static final Long AD_SELECTION_ID = 1234L;
    private static final String ERROR_INVALID_JSON = "Invalid Json Exception";
    private static final int CALLER_UID = Process.myUid();
    private static final String MY_APP_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;

    private static final AdTechIdentifier SELLER_VALID =
            AdTechIdentifier.fromString("developer.android.com");
    private static final Uri DECISION_LOGIC_URI =
            Uri.parse("https://developer.android.com/test/decisions_logic_uris");
    private static final Uri TRUSTED_SIGNALS_URI =
            Uri.parse("https://developer.android.com/test/trusted_signals_uri");
    private static final int PERSIST_AD_SELECTION_LATENCY_MS =
            (int) (PERSIST_AD_SELECTION_END_TIMESTAMP - PERSIST_AD_SELECTION_START_TIMESTAMP);
    private MockitoSession mStaticMockSession = null;
    @Mock private AdsScoreGenerator mMockAdsScoreGenerator;
    @Mock private AdSelectionIdGenerator mMockAdSelectionIdGenerator;
    @Mock private AppImportanceFilter mAppImportanceFilter;
    @Spy private Clock mClock = Clock.systemUTC();
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private Throttler mMockThrottler;
    @Mock private com.android.adservices.service.stats.Clock mAdSelectionExecutionLoggerClock;
    @Mock private File mMockDBAdSelectionFile;

    @Captor
    ArgumentCaptor<RunAdSelectionProcessReportedStats>
            mRunAdSelectionProcessReportedStatsArgumentCaptor;

    @Captor
    ArgumentCaptor<RunAdBiddingProcessReportedStats>
            mRunAdBiddingProcessReportedStatsArgumentCaptor;

    @Mock private PerBuyerBiddingRunner mPerBuyerBiddingRunner;

    private Flags mFlags =
            new Flags() {
                @Override
                public long getAdSelectionOverallTimeoutMs() {
                    return 300;
                }

                @Override
                public boolean getDisableFledgeEnrollmentCheck() {
                    return true;
                }
            };
    @Spy private Context mContext = ApplicationProvider.getApplicationContext();
    private AdServicesHttpsClient mAdServicesHttpsClient;
    private ExecutorService mLightweightExecutorService;
    private ExecutorService mBackgroundExecutorService;
    private ScheduledThreadPoolExecutor mScheduledExecutor;
    private CustomAudienceDao mCustomAudienceDao;
    private AdSelectionEntryDao mAdSelectionEntryDao;
    private Supplier<Throttler> mThrottlerSupplier = () -> mMockThrottler;
    private AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);
    private final FledgeAuthorizationFilter mFledgeAuthorizationFilter =
            new FledgeAuthorizationFilter(
                    mContext.getPackageManager(),
                    new EnrollmentDao(mContext, DbTestUtil.getDbHelperForTest()),
                    mAdServicesLoggerMock);
    private final FledgeAllowListsFilter mFledgeAllowListsFilter =
            new FledgeAllowListsFilter(mFlags, mAdServicesLoggerMock);
    private List<AdTechIdentifier> mCustomAudienceBuyers;
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
    private AdSelectionExecutionLogger mAdSelectionExecutionLogger;


    @Before
    public void setUp() {
        // Test applications don't have the required permissions to read config P/H flags, and
        // injecting mocked flags everywhere is annoying and non-trivial for static methods
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(WebView.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();

        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mScheduledExecutor = AdServicesExecutors.getScheduler();
        mAdServicesHttpsClient =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        CacheProviderFactory.createNoOpCache());
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(mContext, CustomAudienceDatabase.class)
                        .build()
                        .customAudienceDao();

        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(mContext, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        mCustomAudienceBuyers = Arrays.asList(BUYER_1, BUYER_2);
        mAdSelectionConfigBuilder =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setSeller(SELLER_VALID)
                        .setCustomAudienceBuyers(mCustomAudienceBuyers)
                        .setDecisionLogicUri(DECISION_LOGIC_URI)
                        .setTrustedScoringSignalsUri(TRUSTED_SIGNALS_URI);

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
        when(mMockThrottler.tryAcquire(eq(FLEDGE_API_SELECT_ADS), anyString())).thenReturn(true);

        when(mContext.getDatabasePath(DATABASE_NAME)).thenReturn(mMockDBAdSelectionFile);
        when(mMockDBAdSelectionFile.length()).thenReturn(DB_AD_SELECTION_FILE_SIZE);
    }

    private DBCustomAudience createDBCustomAudience(final AdTechIdentifier buyer) {
        return DBCustomAudienceFixture.getValidBuilderByBuyer(buyer)
                .setOwner(buyer.toString() + CustomAudienceFixture.VALID_OWNER)
                .setName(buyer + CustomAudienceFixture.VALID_NAME)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setLastAdsAndBiddingDataUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .build();
    }

    @Test
    public void testRunAdSelectionSuccess() throws AdServicesException {
        when(mClock.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFlags).when(FlagsFactory::getFlags);
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(mAdScoringOutcomeList))));

        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClock.instant()).thenReturn(adSelectionCreationTs);

        DBAdSelectionEntry expectedAdSelectionResult =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2
                                        .getCustomAudienceBiddingInfo()
                                        .getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBuyerDecisionLogicJs(
                                mAdBiddingOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBuyerDecisionLogicJs())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setContextualSignals("{}")
                        .setCreationTimestamp(adSelectionCreationTs)
                        .build();

        mockAdSelectionExecutionLoggerSpyWithSuccessAdSelection();

        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mConsentManagerMock,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerMock,
                        mAppImportanceFilter,
                        mFlags,
                        mThrottlerSupplier,
                        CALLER_UID,
                        mFledgeAuthorizationFilter,
                        mFledgeAllowListsFilter,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunner);
        assertFalse(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertEquals(
                expectedAdSelectionResult,
                mAdSelectionEntryDao.getAdSelectionEntityById(AD_SELECTION_ID));
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionRetriesAdSelectionIdGenerationAfterCollision()
            throws AdServicesException {
        when(mClock.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFlags).when(FlagsFactory::getFlags);
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        long existingAdSelectionId = 2345L;

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(mAdScoringOutcomeList))));

        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClock.instant()).thenReturn(adSelectionCreationTs);

        DBAdSelectionEntry expectedAdSelectionResult =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2
                                        .getCustomAudienceBiddingInfo()
                                        .getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBuyerDecisionLogicJs(
                                mAdBiddingOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBuyerDecisionLogicJs())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setContextualSignals("{}")
                        .setCreationTimestamp(adSelectionCreationTs)
                        .build();

        DBAdSelection existingAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(existingAdSelectionId)
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2
                                        .getCustomAudienceBiddingInfo()
                                        .getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setContextualSignals("{}")
                        .setCreationTimestamp(adSelectionCreationTs)
                        .setCallerPackageName(MY_APP_PACKAGE_NAME)
                        .setBiddingLogicUri(
                                mAdScoringOutcomeForBuyer2
                                        .getCustomAudienceBiddingInfo()
                                        .getBiddingLogicUri())
                        .build();

        // Persist existing ad selection entry with existingAdSelectionId
        mAdSelectionEntryDao.persistAdSelection(existingAdSelection);

        mockAdSelectionExecutionLoggerSpyWithSuccessAdSelection();

        // Mock generator to return a collision on the first generation
        when(mMockAdSelectionIdGenerator.generateId())
                .thenReturn(existingAdSelectionId, existingAdSelectionId, AD_SELECTION_ID);

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mConsentManagerMock,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerMock,
                        mAppImportanceFilter,
                        mFlags,
                        mThrottlerSupplier,
                        CALLER_UID,
                        mFledgeAuthorizationFilter,
                        mFledgeAllowListsFilter,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunner);

        assertFalse(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(existingAdSelectionId));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(existingAdSelectionId));
        assertEquals(
                expectedAdSelectionResult,
                mAdSelectionEntryDao.getAdSelectionEntityById(AD_SELECTION_ID));
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionWithRevokedUserConsentSuccess() throws AdServicesException {
        doReturn(mFlags).when(FlagsFactory::getFlags);
        doReturn(AdServicesApiConsent.REVOKED).when(mConsentManagerMock).getConsent();

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);

        mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionByValidateRequest();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mConsentManagerMock,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerMock,
                        mAppImportanceFilter,
                        mFlags,
                        mThrottlerSupplier,
                        CALLER_UID,
                        mFledgeAuthorizationFilter,
                        mFledgeAllowListsFilter,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunner);

        assertFalse(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunner, never()).runBidding(any(), any(), anyLong(), any());
        verify(mMockAdsScoreGenerator, never()).runAdScoring(any(), any());

        assertTrue(resultsCallback.mIsSuccess);
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionIdExist(
                        resultsCallback.mAdSelectionResponse.getAdSelectionId()));
        assertEquals(Uri.EMPTY, resultsCallback.mAdSelectionResponse.getRenderUri());
        assertFalse(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID));

        verifyLogForFailurePriorPersistAdSelection(STATUS_USER_CONSENT_REVOKED);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionMissingBuyerSignals() throws AdServicesException {
        when(mClock.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFlags).when(FlagsFactory::getFlags);
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        // Creating ad selection config with missing Buyer signals to test the fallback
        AdSelectionConfig adSelectionConfig =
                mAdSelectionConfigBuilder.setPerBuyerSignals(Collections.EMPTY_MAP).build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

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
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBiddingLogicUri(
                                mAdScoringOutcomeForBuyer2
                                        .getCustomAudienceBiddingInfo()
                                        .getBiddingLogicUri())
                        .setContextualSignals("{}")
                        .setCallerPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);

        mockAdSelectionExecutionLoggerSpyWithSuccessAdSelection();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mConsentManagerMock,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerMock,
                        mAppImportanceFilter,
                        mFlags,
                        mThrottlerSupplier,
                        CALLER_UID,
                        mFledgeAuthorizationFilter,
                        mFledgeAllowListsFilter,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunner);

        assertFalse(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedDBAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedDBAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID));

        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionNoCAs() {
        when(mClock.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Do not populate CustomAudience DAO

        mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionByNoCAs();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mConsentManagerMock,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerMock,
                        mAppImportanceFilter,
                        mFlags,
                        mThrottlerSupplier,
                        CALLER_UID,
                        mFledgeAuthorizationFilter,
                        mFledgeAllowListsFilter,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunner);
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(), ERROR_NO_CA_AVAILABLE);

        verifyLogForFailedBiddingStageDuringFetchBuyersCustomAudience(STATUS_INTERNAL_ERROR);
        verifyLogForFailurePriorPersistAdSelection(STATUS_INTERNAL_ERROR);

        // If there are no corresponding CAs we should not even attempt bidding
        verifyZeroInteractions(mPerBuyerBiddingRunner);
        // If there was no bidding then we should not even attempt to run scoring
        verifyZeroInteractions(mMockAdsScoreGenerator);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_INTERNAL_ERROR),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionCallerNotInForeground_fails() {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(
                        CALLER_UID, AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS, null);
        mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionByValidateRequest();
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mConsentManagerMock,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerMock,
                        mAppImportanceFilter,
                        mFlags,
                        mThrottlerSupplier,
                        CALLER_UID,
                        mFledgeAuthorizationFilter,
                        mFledgeAllowListsFilter,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunner);
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE);

        verifyLogForFailurePriorPersistAdSelection(STATUS_BACKGROUND_CALLER);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_BACKGROUND_CALLER),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionCallerNotInForegroundFlagDisabled_doesNotFailValidation() {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        Flags flags =
                new Flags() {
                    @Override
                    public boolean getEnforceForegroundStatusForFledgeRunAdSelection() {
                        return false;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }
                };

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionByNoCAs();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mConsentManagerMock,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerMock,
                        mAppImportanceFilter,
                        flags,
                        mThrottlerSupplier,
                        CALLER_UID,
                        mFledgeAuthorizationFilter,
                        mFledgeAllowListsFilter,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunner);
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verifyZeroInteractions(mAppImportanceFilter);

        // This ad selection fails because there are no CAs but the foreground status validation
        // is not blocking the rest of the process
        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(), ERROR_NO_CA_AVAILABLE);
        verifyLogForFailedBiddingStageDuringFetchBuyersCustomAudience(STATUS_INTERNAL_ERROR);
        verifyLogForFailurePriorPersistAdSelection(STATUS_INTERNAL_ERROR);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_INTERNAL_ERROR),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionPartialBidding() throws AdServicesException {
        when(mClock.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFlags).when(FlagsFactory::getFlags);
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        // In this case assuming bidding fails for one of ads and return partial result
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(null)))
                .when(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        // In this case we are only expected to get score for the first bidding,
        // as second one is null
        List<AdBiddingOutcome> partialBiddingOutcome = Arrays.asList(mAdBiddingOutcomeForBuyer1);
        when(mMockAdsScoreGenerator.runAdScoring(partialBiddingOutcome, adSelectionConfig))
                .thenReturn(
                        (FluentFuture.from(
                                Futures.immediateFuture(mAdScoringOutcomeList.subList(0, 1)))));

        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);

        mockAdSelectionExecutionLoggerSpyWithSuccessAdSelection();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mConsentManagerMock,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerMock,
                        mAppImportanceFilter,
                        mFlags,
                        mThrottlerSupplier,
                        CALLER_UID,
                        mFledgeAuthorizationFilter,
                        mFledgeAllowListsFilter,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunner);

        assertFalse(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

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
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer1
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBiddingLogicUri(
                                mAdScoringOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBiddingLogicUri())
                        .setContextualSignals("{}")
                        .setCallerPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        verify(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mMockAdsScoreGenerator).runAdScoring(partialBiddingOutcome, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedDBAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedDBAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID));
        verifyLogForSuccessfulBiddingProcess(partialBiddingOutcome);
        verifyLogForSuccessfulAdSelectionProcess();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionBiddingFailure() {
        when(mClock.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFlags).when(FlagsFactory::getFlags);
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        // In this case assuming bidding fails and returns null
        doReturn(ImmutableList.of(Futures.immediateFuture(null)))
                .when(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(null)))
                .when(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionByNoBiddingOutcomes();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mConsentManagerMock,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerMock,
                        mAppImportanceFilter,
                        mFlags,
                        mThrottlerSupplier,
                        CALLER_UID,
                        mFledgeAuthorizationFilter,
                        mFledgeAllowListsFilter,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunner);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        // If the result of bidding is empty, then we should not even attempt to run scoring
        verifyZeroInteractions(mMockAdsScoreGenerator);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                ERROR_NO_VALID_BIDS_FOR_SCORING);
        verifyLogForSuccessfulBiddingProcess(Arrays.asList(null, null));
        verifyLogForFailurePriorPersistAdSelection(STATUS_INTERNAL_ERROR);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_INTERNAL_ERROR),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionScoringFailure() throws AdServicesException {
        when(mClock.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFlags).when(FlagsFactory::getFlags);
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        // In this case assuming we get an empty result
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(Collections.EMPTY_LIST))));

        mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionDuringScoring();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mConsentManagerMock,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerMock,
                        mAppImportanceFilter,
                        mFlags,
                        mThrottlerSupplier,
                        CALLER_UID,
                        mFledgeAuthorizationFilter,
                        mFledgeAllowListsFilter,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunner);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(), ERROR_NO_WINNING_AD_FOUND);
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForFailurePriorPersistAdSelection(STATUS_INTERNAL_ERROR);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_INTERNAL_ERROR),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionNegativeScoring() throws AdServicesException {
        when(mClock.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFlags).when(FlagsFactory::getFlags);
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

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

        mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionDuringScoring();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mConsentManagerMock,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerMock,
                        mAppImportanceFilter,
                        mFlags,
                        mThrottlerSupplier,
                        CALLER_UID,
                        mFledgeAuthorizationFilter,
                        mFledgeAllowListsFilter,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunner);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(), ERROR_NO_WINNING_AD_FOUND);
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForFailurePriorPersistAdSelection(STATUS_INTERNAL_ERROR);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_INTERNAL_ERROR),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionPartialNegativeScoring() throws AdServicesException {
        when(mClock.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFlags).when(FlagsFactory::getFlags);
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

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

        mockAdSelectionExecutionLoggerSpyWithSuccessAdSelection();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mConsentManagerMock,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerMock,
                        mAppImportanceFilter,
                        mFlags,
                        mThrottlerSupplier,
                        CALLER_UID,
                        mFledgeAuthorizationFilter,
                        mFledgeAllowListsFilter,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunner);

        assertFalse(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

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
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer1
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBiddingLogicUri(
                                mAdScoringOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBiddingLogicUri())
                        .setContextualSignals("{}")
                        .setCallerPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedDBAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedDBAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID));
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionScoringException() throws AdServicesException {
        when(mClock.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFlags).when(FlagsFactory::getFlags);
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig =
                mAdSelectionConfigBuilder
                        .setAdSelectionSignals(AdSelectionSignals.fromString("{/}"))
                        .build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        // In this case we expect a JSON validation exception
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenThrow(new AdServicesException(ERROR_INVALID_JSON));

        mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionDuringScoring();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mConsentManagerMock,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerMock,
                        mAppImportanceFilter,
                        mFlags,
                        mThrottlerSupplier,
                        CALLER_UID,
                        mFledgeAuthorizationFilter,
                        mFledgeAllowListsFilter,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunner);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(), ERROR_INVALID_JSON);

        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForFailurePriorPersistAdSelection(STATUS_INTERNAL_ERROR);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_INTERNAL_ERROR),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testmMockDBAdSeleciton() throws AdServicesException {
        when(mClock.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFlags).when(FlagsFactory::getFlags);
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        Flags flagsWithSmallerLimits =
                new Flags() {
                    @Override
                    public long getAdSelectionOverallTimeoutMs() {
                        return 100;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }
                };

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        flagsWithSmallerLimits.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        flagsWithSmallerLimits.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(mAdScoringOutcomeList))));

        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClock.instant()).thenReturn(adSelectionCreationTs);

        when(mMockAdSelectionIdGenerator.generateId())
                .thenAnswer(
                        new AnswersWithDelay(
                                2 * mFlags.getAdSelectionOverallTimeoutMs(),
                                new Returns(AD_SELECTION_ID)));
        mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionBeforePersistAdSelection();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mConsentManagerMock,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerMock,
                        mAppImportanceFilter,
                        flagsWithSmallerLimits,
                        mThrottlerSupplier,
                        CALLER_UID,
                        mFledgeAuthorizationFilter,
                        mFledgeAllowListsFilter,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunner);

        assertFalse(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        assertFalse(resultsCallback.mIsSuccess);
        FledgeErrorResponse response = resultsCallback.mFledgeErrorResponse;
        verifyErrorMessageIsCorrect(response.getErrorMessage(), AD_SELECTION_TIMED_OUT);
        Assert.assertEquals(
                "Error response code mismatch",
                AdServicesStatusUtils.STATUS_TIMEOUT,
                response.getStatusCode());

        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForFailureByRunAdSelectionOrchestrationTimesOut(STATUS_TIMEOUT);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_TIMEOUT),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionPerBuyerTimeout() throws AdServicesException {
        Flags flagsWithSmallPerBuyerTimeout =
                new Flags() {
                    @Override
                    public long getAdSelectionOverallTimeoutMs() {
                        return 5000;
                    }

                    @Override
                    public long getAdSelectionBiddingTimeoutPerBuyerMs() {
                        return 100;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }
                };
        when(mClock.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(flagsWithSmallPerBuyerTimeout).when(FlagsFactory::getFlags);
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        Callable<AdBiddingOutcome> delayedBiddingOutcomeForBuyer1 =
                () -> {
                    TimeUnit.MILLISECONDS.sleep(
                            10
                                    * flagsWithSmallPerBuyerTimeout
                                            .getAdSelectionBiddingTimeoutPerBuyerMs());
                    return mAdBiddingOutcomeForBuyer1;
                };

        doReturn(ImmutableList.of())
                .when(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        flagsWithSmallPerBuyerTimeout.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        flagsWithSmallPerBuyerTimeout.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // bidding Outcome List should only have one bidding outcome
        List<AdBiddingOutcome> adBiddingOutcomeList = ImmutableList.of(mAdBiddingOutcomeForBuyer2);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        when(mMockAdsScoreGenerator.runAdScoring(adBiddingOutcomeList, adSelectionConfig))
                .thenReturn(
                        (FluentFuture.from(
                                Futures.immediateFuture(
                                        ImmutableList.of(mAdScoringOutcomeForBuyer2)))));

        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClock.instant()).thenReturn(adSelectionCreationTs);

        DBAdSelectionEntry expectedAdSelectionResult =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2
                                        .getCustomAudienceBiddingInfo()
                                        .getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBuyerDecisionLogicJs(
                                mAdBiddingOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBuyerDecisionLogicJs())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setContextualSignals("{}")
                        .setCreationTimestamp(adSelectionCreationTs)
                        .build();

        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);

        mockAdSelectionExecutionLoggerSpyWithSuccessAdSelection();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mConsentManagerMock,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerMock,
                        mAppImportanceFilter,
                        flagsWithSmallPerBuyerTimeout,
                        mThrottlerSupplier,
                        CALLER_UID,
                        mFledgeAuthorizationFilter,
                        mFledgeAllowListsFilter,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunner);

        assertFalse(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        flagsWithSmallPerBuyerTimeout.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunner)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        flagsWithSmallPerBuyerTimeout.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mMockAdsScoreGenerator).runAdScoring(adBiddingOutcomeList, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertEquals(
                expectedAdSelectionResult,
                mAdSelectionEntryDao.getAdSelectionEntityById(AD_SELECTION_ID));
        verifyLogForSuccessfulBiddingProcess(adBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionThrottledFailure() throws AdServicesException {
        when(mClock.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFlags).when(FlagsFactory::getFlags);
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Throttle Ad Selection request
        when(mMockThrottler.tryAcquire(eq(FLEDGE_API_SELECT_ADS), anyString())).thenReturn(false);

        mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionByValidateRequest();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mConsentManagerMock,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerMock,
                        mAppImportanceFilter,
                        mFlags,
                        mThrottlerSupplier,
                        CALLER_UID,
                        mFledgeAuthorizationFilter,
                        mFledgeAllowListsFilter,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunner);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        assertFalse(resultsCallback.mIsSuccess);
        FledgeErrorResponse response = resultsCallback.mFledgeErrorResponse;
        verifyErrorMessageIsCorrect(response.getErrorMessage(), AD_SELECTION_THROTTLED);
        Assert.assertEquals(
                "Error response code mismatch",
                STATUS_RATE_LIMIT_REACHED,
                response.getStatusCode());
        verifyLogForFailurePriorPersistAdSelection(STATUS_RATE_LIMIT_REACHED);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_RATE_LIMIT_REACHED),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testAdSelectionRunnerInstanceNotCreatedIfJSSandboxNotInWebView() {
        doReturn(null).when(WebView::getCurrentWebViewPackage);

        ThrowingRunnable initializeAdSelectionRunner =
                () ->
                        new OnDeviceAdSelectionRunner(
                                mContext,
                                mCustomAudienceDao,
                                mAdSelectionEntryDao,
                                mAdServicesHttpsClient,
                                mLightweightExecutorService,
                                mBackgroundExecutorService,
                                mScheduledExecutor,
                                mConsentManagerMock,
                                mAdServicesLoggerMock,
                                DevContext.createForDevOptionsDisabled(),
                                mAppImportanceFilter,
                                mFlags,
                                mThrottlerSupplier,
                                CALLER_UID,
                                mFledgeAuthorizationFilter,
                                mFledgeAllowListsFilter,
                                mAdSelectionExecutionLogger);
        Throwable throwable =
                assertThrows(IllegalArgumentException.class, initializeAdSelectionRunner);
        verifyErrorMessageIsCorrect(throwable.getMessage(), JS_SANDBOX_IS_NOT_AVAILABLE);
    }

    private void verifyErrorMessageIsCorrect(
            final String actualErrorMassage, final String expectedErrorReason) {
        Assert.assertTrue(
                String.format(
                        "Actual error [%s] does not begin with [%s]",
                        actualErrorMassage, ERROR_AD_SELECTION_FAILURE),
                actualErrorMassage.startsWith(ERROR_AD_SELECTION_FAILURE));
        Assert.assertTrue(
                String.format(
                        "Actual error [%s] does not contain expected message: [%s]",
                        actualErrorMassage, expectedErrorReason),
                actualErrorMassage.contains(expectedErrorReason));
    }

    private AdSelectionTestCallback invokeRunAdSelection(
            AdSelectionRunner adSelectionRunner,
            AdSelectionConfig adSelectionConfig,
            String callerPackageName) {

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
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        AdSelectionInput input =
                new AdSelectionInput.Builder()
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(callerPackageName)
                        .build();

        adSelectionRunner.runAdSelection(input, adSelectionTestCallback);
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
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
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

    private void mockAdSelectionExecutionLoggerSpyWithSuccessAdSelection() {
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        BIDDING_STAGE_START_TIMESTAMP,
                        GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP,
                        RUN_AD_BIDDING_START_TIMESTAMP,
                        RUN_AD_BIDDING_END_TIMESTAMP,
                        PERSIST_AD_SELECTION_START_TIMESTAMP,
                        PERSIST_AD_SELECTION_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        mAdSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata,
                        mAdSelectionExecutionLoggerClock,
                        mContext,
                        mAdServicesLoggerMock);
    }

    private void mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionByValidateRequest() {
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(START_ELAPSED_TIMESTAMP, STOP_ELAPSED_TIMESTAMP);
        mAdSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata,
                        mAdSelectionExecutionLoggerClock,
                        mContext,
                        mAdServicesLoggerMock);
    }

    private void mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionByNoCAs() {
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        BIDDING_STAGE_START_TIMESTAMP,
                        BIDDING_STAGE_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        mAdSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata,
                        mAdSelectionExecutionLoggerClock,
                        mContext,
                        mAdServicesLoggerMock);
    }

    private void mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionByNoBiddingOutcomes() {
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        BIDDING_STAGE_START_TIMESTAMP,
                        GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP,
                        RUN_AD_BIDDING_START_TIMESTAMP,
                        RUN_AD_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        mAdSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata,
                        mAdSelectionExecutionLoggerClock,
                        mContext,
                        mAdServicesLoggerMock);
    }

    // TODO(b/221861861): add SCORING TIMESTAMP.
    private void mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionDuringScoring() {
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        BIDDING_STAGE_START_TIMESTAMP,
                        GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP,
                        RUN_AD_BIDDING_START_TIMESTAMP,
                        RUN_AD_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        mAdSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata,
                        mAdSelectionExecutionLoggerClock,
                        mContext,
                        mAdServicesLoggerMock);
    }

    private void mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionBeforePersistAdSelection() {
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        BIDDING_STAGE_START_TIMESTAMP,
                        GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP,
                        RUN_AD_BIDDING_START_TIMESTAMP,
                        RUN_AD_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        mAdSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata,
                        mAdSelectionExecutionLoggerClock,
                        mContext,
                        mAdServicesLoggerMock);
    }

    // Verify bidding process.
    private void verifyLogForSuccessfulBiddingProcess(List<AdBiddingOutcome> adBiddingOutcomeList) {
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(
                        mRunAdBiddingProcessReportedStatsArgumentCaptor.capture());
        RunAdBiddingProcessReportedStats runAdBiddingProcessReportedStats =
                mRunAdBiddingProcessReportedStatsArgumentCaptor.getValue();

        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceLatencyInMills())
                .isEqualTo(GET_BUYERS_CUSTOM_AUDIENCE_LATENCY_MS);
        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersRequested())
                .isEqualTo(mCustomAudienceBuyers.size());
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersFetched())
                .isEqualTo(
                        mBuyerCustomAudienceList.stream()
                                .filter(a -> !Objects.isNull(a))
                                .map(a -> a.getBuyer())
                                .collect(Collectors.toSet())
                                .size());
        assertThat(runAdBiddingProcessReportedStats.getNumOfAdsEnteringBidding())
                .isEqualTo(
                        mBuyerCustomAudienceList.stream()
                                .filter(a -> !Objects.isNull(a))
                                .map(a -> a.getAds().size())
                                .reduce(0, (a, b) -> (a + b)));
        int numOfCAsEnteringBidding = mBuyerCustomAudienceList.size();
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasEnteringBidding())
                .isEqualTo(numOfCAsEnteringBidding);
        int numOfCAsPostBidding =
                adBiddingOutcomeList.stream()
                        .filter(a -> !Objects.isNull(a))
                        .map(
                                a ->
                                        a.getCustomAudienceBiddingInfo()
                                                .getCustomAudienceSignals()
                                                .hashCode())
                        .collect(Collectors.toSet())
                        .size();
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasPostBidding())
                .isEqualTo(numOfCAsPostBidding);
        assertThat(runAdBiddingProcessReportedStats.getRatioOfCasSelectingRmktAds())
                .isEqualTo(((float) numOfCAsPostBidding) / numOfCAsEnteringBidding);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingLatencyInMillis())
                .isEqualTo(RUN_AD_BIDDING_LATENCY_MS);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingProcessReportedStats.getTotalAdBiddingStageLatencyInMillis())
                .isEqualTo(TOTAL_BIDDING_STAGE_LATENCY_IN_MS);
    }

    private void verifyLogForFailedBiddingStageDuringFetchBuyersCustomAudience(int resultCode) {
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(
                        mRunAdBiddingProcessReportedStatsArgumentCaptor.capture());
        RunAdBiddingProcessReportedStats runAdBiddingProcessReportedStats =
                mRunAdBiddingProcessReportedStatsArgumentCaptor.getValue();

        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceLatencyInMills())
                .isEqualTo(TOTAL_BIDDING_STAGE_LATENCY_IN_MS);
        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersRequested())
                .isEqualTo(mCustomAudienceBuyers.size());
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersFetched()).isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getNumOfAdsEnteringBidding())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasEnteringBidding())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasPostBidding())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getRatioOfCasSelectingRmktAds())
                .isEqualTo(-1.0f);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingLatencyInMillis())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingResultCode())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getTotalAdBiddingStageLatencyInMillis())
                .isEqualTo(TOTAL_BIDDING_STAGE_LATENCY_IN_MS);
    }

    // Verify Ad selection process.
    private void verifyLogForSuccessfulAdSelectionProcess() {
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        mRunAdSelectionProcessReportedStatsArgumentCaptor.capture());
        RunAdSelectionProcessReportedStats runAdSelectionProcessReportedStats =
                mRunAdSelectionProcessReportedStatsArgumentCaptor.getValue();

        assertThat(runAdSelectionProcessReportedStats.getIsRemarketingAdsWon())
                .isEqualTo(IS_RMKT_ADS_WON);
        assertThat(runAdSelectionProcessReportedStats.getDBAdSelectionSizeInBytes())
                .isEqualTo((int) DB_AD_SELECTION_FILE_SIZE);
        assertThat(runAdSelectionProcessReportedStats.getPersistAdSelectionLatencyInMillis())
                .isEqualTo(PERSIST_AD_SELECTION_LATENCY_MS);
        assertThat(runAdSelectionProcessReportedStats.getPersistAdSelectionResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionLatencyInMillis())
                .isEqualTo(RUN_AD_SELECTION_INTERNAL_FINAL_LATENCY_MS);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionResultCode())
                .isEqualTo(STATUS_SUCCESS);
    }

    private void verifyLogForFailurePriorPersistAdSelection(int resultCode) {
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        mRunAdSelectionProcessReportedStatsArgumentCaptor.capture());
        RunAdSelectionProcessReportedStats runAdSelectionProcessReportedStats =
                mRunAdSelectionProcessReportedStatsArgumentCaptor.getValue();

        assertThat(runAdSelectionProcessReportedStats.getIsRemarketingAdsWon())
                .isEqualTo(IS_RMKT_ADS_WON_UNSET);
        assertThat(runAdSelectionProcessReportedStats.getDBAdSelectionSizeInBytes())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdSelectionProcessReportedStats.getPersistAdSelectionLatencyInMillis())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdSelectionProcessReportedStats.getPersistAdSelectionResultCode())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionLatencyInMillis())
                .isEqualTo(RUN_AD_SELECTION_INTERNAL_FINAL_LATENCY_MS);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionResultCode())
                .isEqualTo(resultCode);
    }

    private void verifyLogForFailureByRunAdSelectionOrchestrationTimesOut(int resultCode) {
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        mRunAdSelectionProcessReportedStatsArgumentCaptor.capture());
        RunAdSelectionProcessReportedStats runAdSelectionProcessReportedStats =
                mRunAdSelectionProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionLatencyInMillis())
                .isEqualTo(RUN_AD_SELECTION_INTERNAL_FINAL_LATENCY_MS);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionResultCode())
                .isEqualTo(resultCode);
    }
}
