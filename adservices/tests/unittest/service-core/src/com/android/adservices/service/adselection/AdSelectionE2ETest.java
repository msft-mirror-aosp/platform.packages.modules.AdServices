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

import static android.adservices.common.AdServicesStatusUtils.RATE_LIMIT_REACHED_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_TIMEOUT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;

import static com.android.adservices.data.adselection.AdSelectionDatabase.DATABASE_NAME;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_AD_SELECTION_FAILURE;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_NO_BUYERS_AVAILABLE;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_NO_CA_AVAILABLE;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_NO_VALID_BIDS_FOR_SCORING;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_NO_WINNING_AD_FOUND;
import static com.android.adservices.service.adselection.AdsScoreGeneratorImpl.MISSING_SCORING_LOGIC;
import static com.android.adservices.service.adselection.AdsScoreGeneratorImpl.MISSING_TRUSTED_SCORING_SIGNALS;
import static com.android.adservices.service.adselection.AdsScoreGeneratorImpl.SCORING_TIMED_OUT;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.DB_AD_SELECTION_FILE_SIZE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.isA;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalMatchers.geq;
import static org.mockito.Mockito.timeout;

import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionInput;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallerMetadata;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.TrustedBiddingDataFixture;
import android.adservices.http.MockWebServerRule;
import android.content.Context;
import android.net.Uri;
import android.os.LimitExceededException;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.LogUtil;
import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelectionOverride;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBCustomAudienceOverride;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.FledgeServiceFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.cache.FledgeHttpCache;
import com.android.adservices.service.common.cache.HttpCache;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.AdSelectionDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.RunAdBiddingProcessReportedStats;
import com.android.adservices.service.stats.RunAdScoringProcessReportedStats;
import com.android.adservices.service.stats.RunAdSelectionProcessReportedStats;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * This test the actual flow of Ad Selection internal flow without any mocking. The dependencies in
 * this test are invoked and used in real time.
 */
public class AdSelectionE2ETest {
    private static final int CALLER_UID = Process.myUid();
    private static final String ERROR_SCORE_AD_LOGIC_MISSING = "scoreAd is not defined";

    private static final AdTechIdentifier BUYER_1 = AdSelectionConfigFixture.BUYER_1;
    private static final AdTechIdentifier BUYER_2 = AdSelectionConfigFixture.BUYER_2;
    private static final AdTechIdentifier BUYER_3 = AdSelectionConfigFixture.BUYER_3;
    private static final String BUYER = "buyer";
    private static final String AD_URI_PREFIX = "http://www.domain.com/adverts/123/";
    private static final String DEFAULT_CUSTOM_AUDIENCE_NAME_SUFFIX = "";

    private static final String BUYER_BIDDING_LOGIC_URI_PATH = "/buyer/bidding/logic/";
    private static final String BUYER_TRUSTED_SIGNAL_URI_PATH = "/kv/buyer/signals/";
    private static final String BUYER_TRUSTED_SIGNAL_PARAMS =
            "?keys=example%2Cvalid%2Clist%2Cof%2Ckeys";

    private static final String SELLER_DECISION_LOGIC_URI_PATH = "/ssp/decision/logic/";
    private static final String SELLER_TRUSTED_SIGNAL_URI_PATH = "/kv/seller/signals/";
    private static final String SELLER_TRUSTED_SIGNAL_PARAMS = "?renderuris=";

    private static final String READ_BID_FROM_AD_METADATA_JS =
            "function generateBid(ad, auction_signals, per_buyer_signals,"
                    + " trusted_bidding_signals, contextual_signals,"
                    + " custom_audience_signals) { \n"
                    + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                    + "}";

    private static final String USE_BID_AS_SCORE_JS =
            "function scoreAd(ad, bid, auction_config, seller_signals, "
                    + "trusted_scoring_signals, contextual_signal, user_signal, "
                    + "custom_audience_signal) { \n"
                    + "  return {'status': 0, 'score': bid };\n"
                    + "}";

    private static final String SELECTION_PICK_HIGHEST_LOGIC_JS_PATH =
            "/selectionPickHighestLogicJS/";
    private static final String SELECTION_PICK_NONE_LOGIC_JS_PATH = "/selectionPickNoneLogicJS/";
    private static final String SELECTION_WATERFALL_LOGIC_JS_PATH = "/selectionWaterfallLogicJS/";
    private static final String SELECTION_PICK_HIGHEST_LOGIC_JS =
            "function selectOutcome(outcomes, selection_signals) {\n"
                    + "    let max_bid = 0;\n"
                    + "    let winner_outcome = null;\n"
                    + "    for (let outcome of outcomes) {\n"
                    + "        if (outcome.bid > max_bid) {\n"
                    + "            max_bid = outcome.bid;\n"
                    + "            winner_outcome = outcome;\n"
                    + "        }\n"
                    + "    }\n"
                    + "    return {'status': 0, 'result': winner_outcome};\n"
                    + "}";
    private static final String SELECTION_PICK_NONE_LOGIC_JS =
            "function selectOutcome(outcomes, selection_signals) {\n"
                    + "    return {'status': 0, 'result': null};\n"
                    + "}";
    private static final String SELECTION_WATERFALL_LOGIC_JS =
            "function selectOutcome(outcomes, selection_signals) {\n"
                    + "    if (outcomes.length != 1 || selection_signals.bid_floor =="
                    + " undefined) return null;\n"
                    + "\n"
                    + "    const outcome_1p = outcomes[0];\n"
                    + "    return {'status': 0, 'result': (outcome_1p.bid >"
                    + " selection_signals.bid_floor) ? outcome_1p : null};\n"
                    + "}";

    private static final AdTechIdentifier TRUSTED_BIDDING_SIGNALS =
            AdTechIdentifier.fromString(
                    "{\n"
                            + "\t\"example\": \"example\",\n"
                            + "\t\"valid\": \"Also valid\",\n"
                            + "\t\"list\": \"list\",\n"
                            + "\t\"of\": \"of\",\n"
                            + "\t\"keys\": \"trusted bidding signal Values\"\n"
                            + "}");

    private static final Map<String, String> TRUSTED_BIDDING_SIGNALS_SERVER_DATA =
            new ImmutableMap.Builder<String, String>()
                    .put("example", "example")
                    .put("valid", "Also valid")
                    .put("list", "list")
                    .put("of", "of")
                    .put("keys", "trusted bidding signal Values")
                    .build();

    private static final AdSelectionSignals TRUSTED_SCORING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"render_uri_1\": \"signals_for_1\",\n"
                            + "\t\"render_uri_2\": \"signals_for_2\"\n"
                            + "}");

    private static final AdTechIdentifier SELLER_VALID =
            AdTechIdentifier.fromString("developer.android.com");
    private static final Uri DECISION_LOGIC_URI_INCONSISTENT =
            Uri.parse("https://developer%$android.com/test/decisions_logic_uris");
    private static final long BINDER_ELAPSED_TIME_MS = 100L;
    private static final String CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final String MY_APP_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private final AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);
    private final Flags mFlags = new AdSelectionE2ETestFlags();

    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    // Mocking DevContextFilter to test behavior with and without override api authorization
    @Mock DevContextFilter mDevContextFilter;
    @Mock CallerMetadata mMockCallerMetadata;
    @Mock FledgeHttpCache.HttpCacheObserver mCacheObserver;

    @Spy private Context mContext = ApplicationProvider.getApplicationContext();
    @Mock private File mMockDBAdSelectionFile;

    FledgeAuthorizationFilter mFledgeAuthorizationFilter =
            new FledgeAuthorizationFilter(
                    mContext.getPackageManager(),
                    new EnrollmentDao(mContext, DbTestUtil.getDbHelperForTest()),
                    mAdServicesLoggerMock);

    private MockitoSession mStaticMockSession = null;
    private ExecutorService mLightweightExecutorService;
    private ExecutorService mBackgroundExecutorService;
    private ScheduledThreadPoolExecutor mScheduledExecutor;
    private CustomAudienceDao mCustomAudienceDao;
    @Spy private AdSelectionEntryDao mAdSelectionEntryDaoSpy;
    private AdServicesHttpsClient mAdServicesHttpsClient;
    private AdSelectionConfig mAdSelectionConfig;
    private AdSelectionServiceImpl mAdSelectionService;
    private Dispatcher mDispatcher;
    private AdTechIdentifier mSeller;

    @Mock private FledgeServiceFilter mFledgeServiceFilter;

    @Before
    public void setUp() throws Exception {
        // Every test in this class requires that the JS Sandbox be available. The JS Sandbox
        // availability depends on an external component (the system webview) being higher than a
        // certain minimum version. Marking that as an assumption that the test is making.
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        mAdSelectionEntryDaoSpy =
                Room.inMemoryDatabaseBuilder(mContext, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        // Test applications don't have the required permissions to read config P/H flags, and
        // injecting mocked flags everywhere is annoying and non-trivial for static methods
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .initMocks(this)
                        .strictness(Strictness.LENIENT)
                        .startMocking();

        // Initialize dependencies for the AdSelectionService
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mScheduledExecutor = AdServicesExecutors.getScheduler();
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(mContext, CustomAudienceDatabase.class)
                        .build()
                        .customAudienceDao();
        mAdServicesHttpsClient =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        CacheProviderFactory.createNoOpCache());

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());
        when(mMockCallerMetadata.getBinderElapsedTimestamp())
                .thenReturn(SystemClock.elapsedRealtime() - BINDER_ELAPSED_TIME_MS);
        // Create an instance of AdSelection Service with real dependencies
        mAdSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDaoSpy,
                        mCustomAudienceDao,
                        mAdServicesHttpsClient,
                        mDevContextFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mContext,
                        mAdServicesLoggerMock,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilter,
                        mFledgeServiceFilter);

        // Create a dispatcher that helps map a request -> response in mockWebServer
        mDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                        .equals(request.getPath())
                                || (BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                        .equals(request.getPath())) {
                            return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        } else if (request.getPath().equals(SELECTION_PICK_HIGHEST_LOGIC_JS_PATH)) {
                            return new MockResponse().setBody(SELECTION_PICK_HIGHEST_LOGIC_JS);
                        } else if (request.getPath().equals(SELECTION_PICK_NONE_LOGIC_JS_PATH)) {
                            return new MockResponse().setBody(SELECTION_PICK_NONE_LOGIC_JS);
                        } else if (request.getPath().equals(SELECTION_WATERFALL_LOGIC_JS_PATH)) {
                            return new MockResponse().setBody(SELECTION_WATERFALL_LOGIC_JS);
                        } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        }
                        LogUtil.w("Unexpected call to MockWebServer " + request.getPath());
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mSeller =
                AdTechIdentifier.fromString(
                        mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH).getHost());

        // Create an Ad Selection Config with the buyers and decision logic URI
        // the URI points to a JS with score generation logic
        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(ImmutableList.of(BUYER_1, BUYER_2, BUYER_3))
                        .setSeller(mSeller)
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();
        when(mContext.getDatabasePath(DATABASE_NAME)).thenReturn(mMockDBAdSelectionFile);
        when(mMockDBAdSelectionFile.length()).thenReturn(DB_AD_SELECTION_FILE_SIZE);
        doNothing()
                .when(mFledgeServiceFilter)
                .filterRequest(
                        mSeller,
                        CALLER_PACKAGE_NAME,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS);
    }

    @After
    public void tearDown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testRunAdSelectionSuccess() throws Exception {
        doReturn(new AdSelectionE2ETestFlags()).when(FlagsFactory::getFlags);
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionWithRevokedUserConsentSuccess() throws Exception {
        doReturn(new AdSelectionE2ETestFlags()).when(FlagsFactory::getFlags);

        doThrow(new ConsentManager.RevokedConsentException())
                .when(mFledgeServiceFilter)
                .filterRequest(
                        mSeller,
                        CALLER_PACKAGE_NAME,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS);

        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                Uri.EMPTY.toString(),
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionMultipleCAsSuccess() throws Exception {
        doReturn(new AdSelectionE2ETestFlags()).when(FlagsFactory::getFlags);
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        List<AdTechIdentifier> participatingBuyers = new ArrayList<>();
        participatingBuyers.add(BUYER_1);
        participatingBuyers.add(BUYER_2);

        for (int i = 3; i <= 50; i++) {
            AdTechIdentifier buyerX = AdTechIdentifier.fromString(BUYER + i + ".com");
            DBCustomAudience dBCustomAudienceForBuyerX =
                    createDBCustomAudience(
                            buyerX,
                            mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                            bidsForBuyer1);
            mCustomAudienceDao.insertOrOverwriteCustomAudience(
                    dBCustomAudienceForBuyerX,
                    CustomAudienceFixture.getValidDailyUpdateUriByBuyer(buyerX));

            participatingBuyers.add(buyerX);
        }

        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(participatingBuyers)
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, adSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionMultipleCAsNoCachingSuccess() throws Exception {
        doReturn(new AdSelectionE2ETestFlags()).when(FlagsFactory::getFlags);

        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        List<AdTechIdentifier> participatingBuyers = new ArrayList<>();
        participatingBuyers.add(BUYER_1);
        participatingBuyers.add(BUYER_2);

        int extraCustomAudienceCount = 100;
        for (int i = 1; i <= extraCustomAudienceCount; i++) {
            DBCustomAudience dBCustomAudienceX =
                    createDBCustomAudience(
                            BUYER_1,
                            DEFAULT_CUSTOM_AUDIENCE_NAME_SUFFIX + i,
                            mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                            bidsForBuyer1,
                            CustomAudienceFixture.VALID_ACTIVATION_TIME,
                            CustomAudienceFixture.VALID_EXPIRATION_TIME,
                            CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI);
            mCustomAudienceDao.insertOrOverwriteCustomAudience(
                    dBCustomAudienceX,
                    CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        }

        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(participatingBuyers)
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        // Creating client which does not have caching
        AdServicesHttpsClient httpClientWithNoCaching =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        CacheProviderFactory.createNoOpCache());

        AdSelectionServiceImpl adSelectionServiceNoCache =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDaoSpy,
                        mCustomAudienceDao,
                        httpClientWithNoCaching,
                        mDevContextFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mContext,
                        mAdServicesLoggerMock,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilter,
                        mFledgeServiceFilter);

        AdSelectionTestCallback resultsCallbackNoCache =
                invokeSelectAds(adSelectionServiceNoCache, adSelectionConfig, CALLER_PACKAGE_NAME);

        assertCallbackIsSuccessful(resultsCallbackNoCache);
        long resultSelectionId = resultsCallbackNoCache.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallbackNoCache.mAdSelectionResponse.getRenderUri().toString());

        // 1 call per CA bidJs + 2 calls per buyer signals + 2 calls per seller scoreAdJS, signals
        int expectedNetworkCalls = (102 * 1) + 2 + 2;
        int serverCallsCountNoCaching = server.getRequestCount();
        Assert.assertEquals(
                "Server calls mismatch", expectedNetworkCalls, serverCallsCountNoCaching);
    }

    @Test
    public void testRunAdSelectionMultipleCAsJSCachedSuccess() throws Exception {
        doReturn(new AdSelectionE2ETestFlags()).when(FlagsFactory::getFlags);

        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        List<AdTechIdentifier> participatingBuyers = new ArrayList<>();
        participatingBuyers.add(BUYER_1);
        participatingBuyers.add(BUYER_2);

        int extraCustomAudienceCount = 100;
        for (int i = 1; i <= extraCustomAudienceCount; i++) {
            DBCustomAudience dBCustomAudienceX =
                    createDBCustomAudience(
                            BUYER_1,
                            DEFAULT_CUSTOM_AUDIENCE_NAME_SUFFIX + i,
                            // All these CAs use the same uri, therefore JS should be cached
                            mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                            bidsForBuyer1,
                            CustomAudienceFixture.VALID_ACTIVATION_TIME,
                            CustomAudienceFixture.VALID_EXPIRATION_TIME,
                            CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI);
            mCustomAudienceDao.insertOrOverwriteCustomAudience(
                    dBCustomAudienceX,
                    CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        }

        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(participatingBuyers)
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        HttpCache cache = CacheProviderFactory.create(mContext, mFlags);
        cache.addObserver(mCacheObserver);

        // Creating client which has caching
        AdServicesHttpsClient httpClientWithCaching =
                new AdServicesHttpsClient(AdServicesExecutors.getBlockingExecutor(), cache);

        AdSelectionServiceImpl adSelectionServiceWithCache =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDaoSpy,
                        mCustomAudienceDao,
                        httpClientWithCaching,
                        mDevContextFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mContext,
                        mAdServicesLoggerMock,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilter,
                        mFledgeServiceFilter);

        // We call selectAds again to verify that scoring logic was also cached
        AdSelectionTestCallback resultsCallbackWithCaching =
                invokeSelectAds(
                        adSelectionServiceWithCache, adSelectionConfig, CALLER_PACKAGE_NAME);
        assertCallbackIsSuccessful(resultsCallbackWithCaching);

        // 1 call per CA bidJs + 2 calls per buyer signals + 2 calls per seller scoreAdJS, signals
        int expectedNetworkCallsNoCaching = (102 * 1) + 2 + 2;

        int serverCallsCountWithCaching = server.getRequestCount();
        assertTrue("Some requests should have been cached", cache.getCachedEntriesCount() > 0);
        assertTrue(
                "Network calls with caching should have been lesser",
                serverCallsCountWithCaching < expectedNetworkCallsNoCaching);

        // Cache cleanup should have been eventually invoked
        verify(mCacheObserver, timeout(3000)).update(HttpCache.CacheEventType.CLEANUP);
        cache.delete();
    }

    @Test
    public void testRunAdSelectionSucceedsWithOverride() throws Exception {
        doReturn(new AdSelectionE2ETestFlags()).when(FlagsFactory::getFlags);
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        mMockWebServerRule.startMockWebServer(mDispatcher);
        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Set dev override for  ad selection
        DBAdSelectionOverride adSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(
                                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                        mAdSelectionConfig))
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .setDecisionLogicJS(USE_BID_AS_SCORE_JS)
                        .setTrustedScoringSignals(TRUSTED_SCORING_SIGNALS.toString())
                        .build();
        mAdSelectionEntryDaoSpy.persistAdSelectionOverride(adSelectionOverride);

        // Set dev override for custom audience
        DBCustomAudienceOverride dbCustomAudienceOverride =
                DBCustomAudienceOverride.builder()
                        .setOwner(dBCustomAudienceForBuyer2.getOwner())
                        .setBuyer(dBCustomAudienceForBuyer2.getBuyer())
                        .setName(dBCustomAudienceForBuyer2.getName())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .setBiddingLogicJS(READ_BID_FROM_AD_METADATA_JS)
                        .setTrustedBiddingData(
                                new JSONObject(TRUSTED_BIDDING_SIGNALS_SERVER_DATA).toString())
                        .build();
        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride);

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(MY_APP_PACKAGE_NAME)
                                .build());

        // Creating new instance of service with new DevContextFilter
        mAdSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDaoSpy,
                        mCustomAudienceDao,
                        mAdServicesHttpsClient,
                        mDevContextFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mContext,
                        mAdServicesLoggerMock,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilter,
                        mFledgeServiceFilter);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionActiveCAs() throws Exception {
        doReturn(new AdSelectionE2ETestFlags()).when(FlagsFactory::getFlags);
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());
        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(0.9, 0.45);
        List<Double> bidsForBuyer2 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer3 = ImmutableList.of(10.0, 100.0);
        DBCustomAudience dbCustomAudienceActive =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceInactive =
                createDBCustomAudience(
                        BUYER_2,
                        DEFAULT_CUSTOM_AUDIENCE_NAME_SUFFIX,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        CustomAudienceFixture.INVALID_DELAYED_ACTIVATION_TIME,
                        CustomAudienceFixture.VALID_EXPIRATION_TIME,
                        CustomAudienceFixture.VALID_LAST_UPDATE_TIME_24_HRS_BEFORE);
        DBCustomAudience dBCustomAudienceExpired =
                createDBCustomAudience(
                        BUYER_3,
                        DEFAULT_CUSTOM_AUDIENCE_NAME_SUFFIX,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_3),
                        bidsForBuyer3,
                        CustomAudienceFixture.VALID_ACTIVATION_TIME,
                        CustomAudienceFixture.INVALID_NOW_EXPIRATION_TIME,
                        CustomAudienceFixture.VALID_LAST_UPDATE_TIME_24_HRS_BEFORE);
        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dbCustomAudienceActive,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceInactive,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceExpired,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_3));
        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_1 + "/ad1",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionNoCAsActive() throws Exception {
        doReturn(new AdSelectionE2ETestFlags()).when(FlagsFactory::getFlags);
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(2);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());
        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);
        List<Double> bidsForBuyer3 = ImmutableList.of(4.3, 6.0, 10.0);

        DBCustomAudience dBCustomAudienceInactive =
                createDBCustomAudience(
                        BUYER_1,
                        DEFAULT_CUSTOM_AUDIENCE_NAME_SUFFIX,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        CustomAudienceFixture.INVALID_DELAYED_ACTIVATION_TIME,
                        CustomAudienceFixture.VALID_EXPIRATION_TIME,
                        CustomAudienceFixture.VALID_LAST_UPDATE_TIME_24_HRS_BEFORE);
        DBCustomAudience dBCustomAudienceExpired =
                createDBCustomAudience(
                        BUYER_2,
                        DEFAULT_CUSTOM_AUDIENCE_NAME_SUFFIX,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        CustomAudienceFixture.VALID_ACTIVATION_TIME,
                        CustomAudienceFixture.INVALID_NOW_EXPIRATION_TIME,
                        CustomAudienceFixture.VALID_LAST_UPDATE_TIME_24_HRS_BEFORE);

        DBCustomAudience dBCustomAudienceOutdated =
                createDBCustomAudience(
                        BUYER_3,
                        DEFAULT_CUSTOM_AUDIENCE_NAME_SUFFIX,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_3),
                        bidsForBuyer3,
                        CustomAudienceFixture.VALID_ACTIVATION_TIME,
                        CustomAudienceFixture.VALID_EXPIRATION_TIME,
                        CustomAudienceFixture.INVALID_LAST_UPDATE_TIME_72_DAYS_BEFORE);
        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceInactive,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceExpired,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceOutdated,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_3));

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        assertCallbackFailed(resultsCallback);
        assertEquals(resultsCallback.mFledgeErrorResponse.getStatusCode(), STATUS_INTERNAL_ERROR);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                String.format(
                        Locale.ENGLISH,
                        AdSelectionRunner.AD_SELECTION_ERROR_PATTERN,
                        AdSelectionRunner.ERROR_AD_SELECTION_FAILURE,
                        AdSelectionRunner.ERROR_NO_CA_AVAILABLE));
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock, never()).logRunAdScoringProcessReportedStats(any());
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_INTERNAL_ERROR),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionNoCAsFailure() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(2);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());
        // Do not populate CustomAudience DAO
        mMockWebServerRule.startMockWebServer(mDispatcher);
        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        assertCallbackFailed(resultsCallback);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(), ERROR_NO_CA_AVAILABLE);
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock, never()).logRunAdScoringProcessReportedStats(any());
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_INTERNAL_ERROR),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionNoBuyersFailure() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());
        // Do not populate buyers in AdSelectionConfig
        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .setCustomAudienceBuyers(Collections.emptyList())
                        .build();

        mMockWebServerRule.startMockWebServer(mDispatcher);
        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        assertCallbackFailed(resultsCallback);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(), ERROR_NO_BUYERS_AVAILABLE);
        verify(mAdServicesLoggerMock, never()).logRunAdScoringProcessReportedStats(any());
        verify(mAdServicesLoggerMock, never()).logRunAdBiddingProcessReportedStats(any());
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_INVALID_ARGUMENT),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionPartialAdsExcludedBidding() throws Exception {
        doReturn(new AdSelectionE2ETestFlags()).when(FlagsFactory::getFlags);
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());
        mMockWebServerRule.startMockWebServer(mDispatcher);
        // Setting bids which are partially non-positive
        List<Double> bidsForBuyer1 = ImmutableList.of(-1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(-4.5, -6.7, -10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_1 + "/ad2",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    private void assertCallbackIsSuccessful(AdSelectionTestCallback resultsCallback) {
        assertTrue(
                resultsCallback.mFledgeErrorResponse != null
                        ? String.format(
                                Locale.ENGLISH,
                                "Expected callback to succeed but it failed with status %d and"
                                        + " message '%s'",
                                resultsCallback.mFledgeErrorResponse.getStatusCode(),
                                resultsCallback.mFledgeErrorResponse.getErrorMessage())
                        : "Expected callback to succeed but it failed with no details",
                resultsCallback.mIsSuccess);
    }

    private void assertCallbackFailed(AdSelectionTestCallback resultsCallback) {
        assertFalse("Expected callback to fail but succeeded", resultsCallback.mIsSuccess);
    }

    @Test
    public void testRunAdSelectionMissingBiddingLogicFailure() throws Exception {
        doReturn(new AdSelectionE2ETestFlags()).when(FlagsFactory::getFlags);
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(2);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());
        // Setting bids->scores
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        // In this case the Buyers have no bidding logic response in dispatcher
        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                        .equals(request.getPath())
                                || (BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                        .equals(request.getPath())) {
                            return new MockResponse().setBody("");
                        } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        assertCallbackFailed(resultsCallback);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                ERROR_NO_VALID_BIDS_FOR_SCORING);
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock, never()).logRunAdScoringProcessReportedStats(any());
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_INTERNAL_ERROR),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionMissingScoringLogicFailure() throws Exception {
        doReturn(new AdSelectionE2ETestFlags()).when(FlagsFactory::getFlags);
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());
        // Setting bids->scores
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        // In this case the Seller has no scoring logic in dispatcher
        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody("");
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                        .equals(request.getPath())
                                || (BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                        .equals(request.getPath())) {
                            return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        assertCallbackFailed(resultsCallback);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                ERROR_SCORE_AD_LOGIC_MISSING);
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_INTERNAL_ERROR),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionErrorFetchingScoringLogicFailure() throws Exception {
        doReturn(new AdSelectionE2ETestFlags()).when(FlagsFactory::getFlags);
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());
        // Setting bids->scores
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        // In this case the web server returns failure for scoring
        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setResponseCode(404);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                        .equals(request.getPath())
                                || (BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                        .equals(request.getPath())) {
                            return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        assertCallbackFailed(resultsCallback);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(), MISSING_SCORING_LOGIC);
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_INTERNAL_ERROR),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionPartialMissingBiddingLogic() throws Exception {
        doReturn(new AdSelectionE2ETestFlags()).when(FlagsFactory::getFlags);
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());
        // Setting bids->scores
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        // In this case the Buyer 2 has no bidding logic response in dispatcher
        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                .equals(request.getPath())) {
                            return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                .equals(request.getPath())) {
                            return new MockResponse().setBody("");
                        } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_1 + "/ad2",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionPartialNonPositiveScoring() throws Exception {
        doReturn(new AdSelectionE2ETestFlags()).when(FlagsFactory::getFlags);
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());
        // Setting bids, in this case the odd bids will be made negative by scoring logic
        List<Double> bidsForBuyer1 = ImmutableList.of(1.0, 2.0);
        List<Double> bidsForBuyer2 = ImmutableList.of(3.0, 5.0, 7.0);

        // This scoring logic assigns negative score to odd bids
        String makeOddBidsNegativeScoreJs =
                "function scoreAd(ad, bid, auction_config, seller_signals, "
                        + "trusted_scoring_signals, contextual_signal, user_signal, "
                        + "custom_audience_signal) { \n"
                        + "  return {'status': 0, 'score': (bid % 2 == 0) ? bid : -bid };\n"
                        + "}";

        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(makeOddBidsNegativeScoreJs);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                        .equals(request.getPath())
                                || (BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                        .equals(request.getPath())) {
                            return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_1 + "/ad2",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionNonPositiveScoringFailure() throws Exception {
        doReturn(new AdSelectionE2ETestFlags()).when(FlagsFactory::getFlags);
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());
        // Setting bids, in this case the odd bids will be made negative by scoring logic
        List<Double> bidsForBuyer1 = ImmutableList.of(1.0, 9.0);
        List<Double> bidsForBuyer2 = ImmutableList.of(3.0, 5.0, 7.0);

        // This scoring logic assigns negative score to odd bids
        String makeOddBidsNegativeScoreJs =
                "function scoreAd(ad, bid, auction_config, seller_signals, "
                        + "trusted_scoring_signals, contextual_signal, user_signal, "
                        + "custom_audience_signal) { \n"
                        + "  return {'status': 0, 'score': (bid % 2 == 0) ? bid : -bid };\n"
                        + "}";

        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(makeOddBidsNegativeScoreJs);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                        .equals(request.getPath())
                                || (BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                        .equals(request.getPath())) {
                            return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        assertCallbackFailed(resultsCallback);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(), ERROR_NO_WINNING_AD_FOUND);
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_INTERNAL_ERROR),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionBiddingTimesOutForCA() throws Exception {
        doReturn(new AdSelectionE2ETestFlags()).when(FlagsFactory::getFlags);
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());
        Flags flagsWithSmallerLimits =
                new Flags() {
                    @Override
                    public long getAdSelectionBiddingTimeoutPerCaMs() {
                        return 1500;
                    }

                    @Override
                    public boolean getEnforceIsolateMaxHeapSize() {
                        return false;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }

                    @Override
                    public float getSdkRequestPermitsPerSecond() {
                        // Unlimited rate for unit tests to avoid flake in tests due to rate
                        // limiting
                        return -1;
                    }
                };

        // Create an instance of AdSelection Service with real dependencies
        mAdSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDaoSpy,
                        mCustomAudienceDao,
                        mAdServicesHttpsClient,
                        mDevContextFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mContext,
                        mAdServicesLoggerMock,
                        flagsWithSmallerLimits,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilter,
                        mFledgeServiceFilter);

        String jsWaitMoreThanAllowedForBiddingPerCa =
                insertJsWait(2 * mFlags.getAdSelectionBiddingTimeoutPerCaMs());
        String readBidFromAdMetadataWithDelayJs =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals, contextual_signals,"
                        + " custom_audience_signals) { \n"
                        + jsWaitMoreThanAllowedForBiddingPerCa
                        + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}";

        // In this case the one buyer's logic takes more than the bidding time limit
        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                .equals(request.getPath())) {
                            return new MockResponse().setBody(readBidFromAdMetadataWithDelayJs);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                .equals(request.getPath())) {
                            return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);
        // buyer1/ad3 is clear winner but will time out
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2, 15.0);
        // due to timeout buyer2/ad3 will win
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionImposesPerBuyerBiddingTimeout() throws Exception {
        doReturn(new AdSelectionE2ETestFlags()).when(FlagsFactory::getFlags);
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(6);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(2);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());
        Long lenientPerBuyerTimeOutLimit = 50000L;
        Long tightPerBuyerTimeOutLimit = 2000L;
        int largeCACountForBuyer = 300;

        Flags flagsWithLenientBuyerBiddingLimits =
                new Flags() {
                    @Override
                    public long getAdSelectionBiddingTimeoutPerBuyerMs() {
                        return lenientPerBuyerTimeOutLimit;
                    }

                    @Override
                    public boolean getEnforceIsolateMaxHeapSize() {
                        return false;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }

                    @Override
                    public float getSdkRequestPermitsPerSecond() {
                        // Unlimited rate for unit tests to avoid flake in tests due to rate
                        // limiting
                        return -1;
                    }

                    @Override
                    public long getAdSelectionOverallTimeoutMs() {
                        return lenientPerBuyerTimeOutLimit * 3;
                    }
                };

        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        List<AdTechIdentifier> participatingBuyers = new ArrayList<>();
        participatingBuyers.add(BUYER_1);
        participatingBuyers.add(BUYER_2);
        participatingBuyers.add(BUYER_3);

        for (int i = 1; i <= largeCACountForBuyer; i++) {
            DBCustomAudience dBCustomAudienceX =
                    createDBCustomAudience(
                            BUYER_3,
                            "-" + i,
                            mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                            bidsForBuyer1,
                            CustomAudienceFixture.VALID_ACTIVATION_TIME,
                            CustomAudienceFixture.VALID_EXPIRATION_TIME,
                            CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI);
            mCustomAudienceDao.insertOrOverwriteCustomAudience(
                    dBCustomAudienceX,
                    CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_3));
        }

        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(participatingBuyers)
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        // Create an instance of AdSelection Service with lenient dependencies
        mAdSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDaoSpy,
                        mCustomAudienceDao,
                        mAdServicesHttpsClient,
                        mDevContextFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mContext,
                        mAdServicesLoggerMock,
                        flagsWithLenientBuyerBiddingLimits,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilter,
                        mFledgeServiceFilter);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, adSelectionConfig, CALLER_PACKAGE_NAME);

        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());

        int networkRequestCountWithLenientTimeout = server.getRequestCount();

        // Now we run the same Ad selection with tight per buyer timeout limits
        Flags flagsWithTightBuyerBiddingLimits =
                new Flags() {
                    @Override
                    public long getAdSelectionBiddingTimeoutPerBuyerMs() {
                        return tightPerBuyerTimeOutLimit;
                    }

                    @Override
                    public boolean getEnforceIsolateMaxHeapSize() {
                        return false;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }

                    @Override
                    public float getSdkRequestPermitsPerSecond() {
                        // Unlimited rate for unit tests to avoid flake in tests due to rate
                        // limiting
                        return -1;
                    }

                    @Override
                    public long getAdSelectionOverallTimeoutMs() {
                        return lenientPerBuyerTimeOutLimit * 3;
                    }

                    @Override
                    public int getAdSelectionMaxConcurrentBiddingCount() {
                        return 1;
                    }
                };

        // Create an instance of AdSelection Service with tight dependencies
        mAdSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDaoSpy,
                        mCustomAudienceDao,
                        mAdServicesHttpsClient,
                        mDevContextFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mContext,
                        mAdServicesLoggerMock,
                        flagsWithTightBuyerBiddingLimits,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilter,
                        mFledgeServiceFilter);

        resultsCallback =
                invokeSelectAds(mAdSelectionService, adSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());

        int networkRequestCountWithTightTimeout =
                server.getRequestCount() - networkRequestCountWithLenientTimeout;

        LogUtil.v(
                String.format(
                        "Network calls with buyer timeout :%d, network calls with"
                                + " lenient timeout :%d",
                        networkRequestCountWithTightTimeout,
                        networkRequestCountWithLenientTimeout));
        assertTrue(
                String.format(
                        "Network calls with buyer timeout :%d, are not less than network calls with"
                                + " lenient timeout :%d",
                        networkRequestCountWithTightTimeout, networkRequestCountWithLenientTimeout),
                networkRequestCountWithTightTimeout < networkRequestCountWithLenientTimeout);
        verify(mAdServicesLoggerMock, times(2))
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock, times(2))
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock, times(2))
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock, times(2))
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionScoringTimesOut() throws Exception {
        doReturn(new AdSelectionE2ETestFlags()).when(FlagsFactory::getFlags);
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());
        Flags flagsWithSmallerLimits =
                new Flags() {
                    @Override
                    public long getAdSelectionScoringTimeoutMs() {
                        return 1500;
                    }

                    @Override
                    public boolean getEnforceIsolateMaxHeapSize() {
                        return false;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }

                    @Override
                    public float getSdkRequestPermitsPerSecond() {
                        // Unlimited rate for unit tests to avoid flake in tests due to rate
                        // limiting
                        return -1;
                    }
                };

        // Create an instance of AdSelection Service with real dependencies
        mAdSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDaoSpy,
                        mCustomAudienceDao,
                        mAdServicesHttpsClient,
                        mDevContextFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mContext,
                        mAdServicesLoggerMock,
                        flagsWithSmallerLimits,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilter,
                        mFledgeServiceFilter);

        String jsWaitMoreThanAllowedForScoring =
                insertJsWait(2 * mFlags.getAdSelectionScoringTimeoutMs());
        String useBidAsScoringWithDelayJs =
                "function scoreAd(ad, bid, auction_config, seller_signals, "
                        + "trusted_scoring_signals, contextual_signal, user_signal, "
                        + "custom_audience_signal) { \n"
                        + jsWaitMoreThanAllowedForScoring
                        + "  return {'status': 0, 'score': bid };\n"
                        + "}";

        // In this case the one buyer's logic takes more than the bidding time limit
        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(useBidAsScoringWithDelayJs);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                        .equals(request.getPath())
                                || (BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                        .equals(request.getPath())) {
                            return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2, 15.0);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        Assert.assertFalse(resultsCallback.mIsSuccess);

        FledgeErrorResponse response = resultsCallback.mFledgeErrorResponse;
        verifyErrorMessageIsCorrect(response.getErrorMessage(), SCORING_TIMED_OUT);
        Assert.assertEquals(
                "Error response code mismatch",
                AdServicesStatusUtils.STATUS_TIMEOUT,
                response.getStatusCode());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_TIMEOUT),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testAdSelectionConfigInvalidInput() throws Exception {
        doThrow(new IllegalArgumentException())
                .when(mFledgeServiceFilter)
                .filterRequest(
                        mSeller,
                        CALLER_PACKAGE_NAME,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS);

        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        AdSelectionConfig invalidAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setSeller(SELLER_VALID)
                        .setDecisionLogicUri(DECISION_LOGIC_URI_INCONSISTENT)
                        .build();

        Mockito.lenient()
                .when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, invalidAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        assertFalse(resultsCallback.mIsSuccess);

        FledgeErrorResponse response = resultsCallback.mFledgeErrorResponse;
        assertEquals(
                "Error response code mismatch", STATUS_INVALID_ARGUMENT, response.getStatusCode());
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_INVALID_ARGUMENT),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionMissingBiddingSignalsFailure() throws Exception {
        doReturn(new AdSelectionE2ETestFlags()).when(FlagsFactory::getFlags);
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(2);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());
        // Create a dispatcher without buyer trusted Signal end point
        Dispatcher missingBiddingSignalsDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                        .equals(request.getPath())
                                || (BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                        .equals(request.getPath())) {
                            return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(missingBiddingSignalsDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        assertCallbackFailed(resultsCallback);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                ERROR_NO_VALID_BIDS_FOR_SCORING);
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock, never()).logRunAdScoringProcessReportedStats(any());
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_INTERNAL_ERROR),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionMissingScoringSignalsFailure() throws Exception {
        doReturn(new AdSelectionE2ETestFlags()).when(FlagsFactory::getFlags);
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());
        // Create a dispatcher without buyer trusted Signal end point
        Dispatcher missingScoringSignalsDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                        .equals(request.getPath())
                                || (BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                        .equals(request.getPath())) {
                            return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(missingScoringSignalsDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        assertCallbackFailed(resultsCallback);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                MISSING_TRUSTED_SCORING_SIGNALS);
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_INTERNAL_ERROR),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionMissingPartialBiddingSignalsSuccess() throws Exception {
        doReturn(new AdSelectionE2ETestFlags()).when(FlagsFactory::getFlags);
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());
        // Create a dispatcher with valid end points
        Dispatcher missingBiddingSignalsDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {

                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                        .equals(request.getPath())
                                || (BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                        .equals(request.getPath())) {
                            return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(missingBiddingSignalsDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        // Invalid trusted bidding signal path for buyer 2
                        mMockWebServerRule.uriForPath(""),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        // Given buyer 2 will be excluded from bidding for missing signals, Buyer 1 : Ad 2 will win
        assertEquals(
                AD_URI_PREFIX + BUYER_1 + "/ad2",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionFailsWithInvalidPackageName() throws Exception {
        doReturn(new AdSelectionE2ETestFlags()).when(FlagsFactory::getFlags);

        String invalidPackageName = CALLER_PACKAGE_NAME + "invalidPackageName";

        doThrow(new FledgeAuthorizationFilter.CallerMismatchException())
                .when(mFledgeServiceFilter)
                .filterRequest(
                        mSeller,
                        invalidPackageName,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS);

        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, invalidPackageName);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        Assert.assertFalse(resultsCallback.mIsSuccess);

        FledgeErrorResponse response = resultsCallback.mFledgeErrorResponse;
        assertEquals("Error response code mismatch", STATUS_UNAUTHORIZED, response.getStatusCode());

        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                String.format(
                        AdSelectionRunner.AD_SELECTION_ERROR_PATTERN,
                        AdSelectionRunner.ERROR_AD_SELECTION_FAILURE,
                        AdServicesStatusUtils
                                .SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE));
        // TODO(b/242139312): Remove atLeastOnce once this the double logging is addressed and
        //  update third argument value.
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock, Mockito.atLeastOnce())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_UNAUTHORIZED),
                        geq(0));
    }

    @Test
    public void testRunAdSelectionFailsWhenAppCannotUsePPApi() throws Exception {
        doReturn(new AdSelectionE2ETestFlags()).when(FlagsFactory::getFlags);
        doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mFledgeServiceFilter)
                .filterRequest(
                        mSeller,
                        CALLER_PACKAGE_NAME,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS);

        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        Assert.assertFalse(resultsCallback.mIsSuccess);

        FledgeErrorResponse response = resultsCallback.mFledgeErrorResponse;
        assertEquals(
                "Error response code mismatch",
                STATUS_CALLER_NOT_ALLOWED,
                response.getStatusCode());

        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                String.format(
                        AdSelectionRunner.AD_SELECTION_ERROR_PATTERN,
                        AdSelectionRunner.ERROR_AD_SELECTION_FAILURE,
                        AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        // TODO(b/242139312): Remove atLeastOnce once this the double logging is addressed and
        //   update third argument value.
        verify(mAdServicesLoggerMock, Mockito.atLeastOnce())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_CALLER_NOT_ALLOWED),
                        geq(0));
    }

    @Test
    public void testRunAdSelectionFailsWhenAdTechFailsEnrollmentCheck() throws Exception {
        Flags flagsWithEnrollmentCheckEnabled =
                new Flags() {
                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return false;
                    }
                };

        doReturn(flagsWithEnrollmentCheckEnabled).when(FlagsFactory::getFlags);

        doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mFledgeServiceFilter)
                .filterRequest(
                        mSeller,
                        CALLER_PACKAGE_NAME,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS);

        // Create an instance of AdSelection Service with real dependencies
        mAdSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDaoSpy,
                        mCustomAudienceDao,
                        mAdServicesHttpsClient,
                        mDevContextFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mContext,
                        mAdServicesLoggerMock,
                        flagsWithEnrollmentCheckEnabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilter,
                        mFledgeServiceFilter);

        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        Assert.assertFalse(resultsCallback.mIsSuccess);

        FledgeErrorResponse response = resultsCallback.mFledgeErrorResponse;
        assertEquals(
                "Error response code mismatch",
                STATUS_CALLER_NOT_ALLOWED,
                response.getStatusCode());

        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                String.format(
                        AdSelectionRunner.AD_SELECTION_ERROR_PATTERN,
                        AdSelectionRunner.ERROR_AD_SELECTION_FAILURE,
                        AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        // TODO(b/242139312): Remove atLeastOnce once this the double logging is addressed and
        //  update third argument value.
        verify(mAdServicesLoggerMock, Mockito.atLeastOnce())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_CALLER_NOT_ALLOWED),
                        geq(0));
    }

    @Test
    public void testRunAdSelectionThrottledSubsequentCallFailure() throws Exception {
        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        class FlagsWithThrottling implements Flags {
            @Override
            public boolean getEnforceIsolateMaxHeapSize() {
                return false;
            }

            @Override
            public boolean getEnforceForegroundStatusForFledgeRunAdSelection() {
                return true;
            }

            @Override
            public boolean getEnforceForegroundStatusForFledgeReportImpression() {
                return true;
            }

            @Override
            public boolean getEnforceForegroundStatusForFledgeOverrides() {
                return true;
            }

            @Override
            public boolean getDisableFledgeEnrollmentCheck() {
                return true;
            }

            // Testing the default throttling limit
            @Override
            public float getSdkRequestPermitsPerSecond() {
                return 1;
            }
        }

        Throttler.destroyExistingThrottler();
        Flags throttlingFlags = new FlagsWithThrottling();
        AdSelectionServiceImpl adSelectionServiceWithThrottling =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDaoSpy,
                        mCustomAudienceDao,
                        mAdServicesHttpsClient,
                        mDevContextFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mContext,
                        mAdServicesLoggerMock,
                        throttlingFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilter,
                        mFledgeServiceFilter);

        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        // First call to Ad Selection should succeed
        AdSelectionTestCallback resultsCallbackFirstCall =
                invokeSelectAds(
                        adSelectionServiceWithThrottling, mAdSelectionConfig, CALLER_PACKAGE_NAME);

        doThrow(new LimitExceededException(RATE_LIMIT_REACHED_ERROR_MESSAGE))
                .when(mFledgeServiceFilter)
                .filterRequest(
                        mSeller,
                        CALLER_PACKAGE_NAME,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS);

        // Immediately made subsequent call should fail
        AdSelectionTestCallback resultsCallbackSecondCall =
                invokeSelectAds(
                        adSelectionServiceWithThrottling, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        assertCallbackIsSuccessful(resultsCallbackFirstCall);
        long resultSelectionId = resultsCallbackFirstCall.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallbackFirstCall.mAdSelectionResponse.getRenderUri().toString());

        assertCallbackFailed(resultsCallbackSecondCall);

        FledgeErrorResponse response = resultsCallbackSecondCall.mFledgeErrorResponse;
        assertEquals(
                "Error response code mismatch",
                AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED,
                response.getStatusCode());

        verifyErrorMessageIsCorrect(
                resultsCallbackSecondCall.mFledgeErrorResponse.getErrorMessage(),
                String.format(
                        AdSelectionRunner.AD_SELECTION_ERROR_PATTERN,
                        AdSelectionRunner.ERROR_AD_SELECTION_FAILURE,
                        RATE_LIMIT_REACHED_ERROR_MESSAGE));
        resetThrottlerToNoRateLimits();
    }

    /**
     * Given Throttler is singleton, & shared across tests, this method should be invoked after
     * tests that impose restrictive rate limits.
     */
    private void resetThrottlerToNoRateLimits() {
        Throttler.destroyExistingThrottler();
        final float noRateLimit = -1;
        Flags mockNoRateLimitFlags = mock(Flags.class);
        doReturn(noRateLimit).when(mockNoRateLimitFlags).getSdkRequestPermitsPerSecond();
        Throttler.getInstance(mockNoRateLimitFlags);
    }

    @Test
    public void testRunAdSelectionSucceedsWhenAdTechPassesEnrollmentCheck() throws Exception {
        Flags flagsWithEnrollmentCheckEnabled =
                new AdSelectionE2ETestFlags() {
                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return false;
                    }
                };

        doReturn(flagsWithEnrollmentCheckEnabled).when(FlagsFactory::getFlags);

        // Create an instance of AdSelection Service with real dependencies
        mAdSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDaoSpy,
                        mCustomAudienceDao,
                        mAdServicesHttpsClient,
                        mDevContextFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mContext,
                        mAdServicesLoggerMock,
                        flagsWithEnrollmentCheckEnabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilter,
                        mFledgeServiceFilter);

        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1));
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2));

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        loggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    /**
     * @param buyer The name of the buyer for this Custom Audience
     * @param biddingUri path from where the bidding logic for this CA can be fetched from
     * @param bids these bids, are added to its metadata. Our JS logic then picks this value and
     *     creates ad with the provided value as bid
     * @param activationTime is the activation time of the Custom Audience
     * @param expirationTime is the expiration time of the Custom Audience
     * @param lastUpdateTime is the last time of the Custom Audience ads and bidding data got
     *     updated
     * @return a real Custom Audience object that can be persisted and used in bidding and scoring
     */
    private DBCustomAudience createDBCustomAudience(
            final AdTechIdentifier buyer,
            final String nameSuffix,
            final Uri biddingUri,
            List<Double> bids,
            Instant activationTime,
            Instant expirationTime,
            Instant lastUpdateTime) {

        // Generate ads for with bids provided
        List<DBAdData> ads = new ArrayList<>();

        // Create ads with the buyer name and bid number as the ad URI
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            ads.add(
                    new DBAdData(
                            Uri.parse(AD_URI_PREFIX + buyer + "/ad" + (i + 1)),
                            "{\"result\":" + bids.get(i) + "}"));
        }

        return new DBCustomAudience.Builder()
                .setOwner(buyer + CustomAudienceFixture.VALID_OWNER)
                .setBuyer(buyer)
                .setName(buyer.toString() + CustomAudienceFixture.VALID_NAME + nameSuffix)
                .setActivationTime(activationTime)
                .setExpirationTime(expirationTime)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setLastAdsAndBiddingDataUpdatedTime(lastUpdateTime)
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        new DBTrustedBiddingData.Builder()
                                .setUri(
                                        mMockWebServerRule.uriForPath(
                                                BUYER_TRUSTED_SIGNAL_URI_PATH))
                                .setKeys(TrustedBiddingDataFixture.getValidTrustedBiddingKeys())
                                .build())
                .setBiddingLogicUri(biddingUri)
                .setAds(ads)
                .build();
    }

    /**
     * @param buyer The name of the buyer for this Custom Audience
     * @param biddingUri path from where the bidding logic for this CA can be fetched from
     * @param bids these bids, are added to its metadata. Our JS logic then picks this value and
     *     creates ad with the provided value as bid
     * @return a real Custom Audience object that can be persisted and used in bidding and scoring
     */
    private DBCustomAudience createDBCustomAudience(
            final AdTechIdentifier buyer, final Uri biddingUri, List<Double> bids) {
        return createDBCustomAudience(
                buyer,
                DEFAULT_CUSTOM_AUDIENCE_NAME_SUFFIX,
                biddingUri,
                bids,
                CustomAudienceFixture.VALID_ACTIVATION_TIME,
                CustomAudienceFixture.VALID_EXPIRATION_TIME,
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI);
    }

    private void verifyErrorMessageIsCorrect(
            final String actualErrorMassage, final String expectedErrorReason) {
        assertTrue(
                String.format(
                        Locale.ENGLISH,
                        "Actual error [%s] does not begin with [%s]",
                        actualErrorMassage,
                        ERROR_AD_SELECTION_FAILURE),
                actualErrorMassage.startsWith(ERROR_AD_SELECTION_FAILURE));
        assertTrue(
                String.format(
                        Locale.ENGLISH,
                        "Actual error [%s] does not contain expected message: [%s]",
                        actualErrorMassage,
                        expectedErrorReason),
                actualErrorMassage.contains(expectedErrorReason));
    }

    private AdSelectionTestCallback invokeSelectAds(
            AdSelectionServiceImpl adSelectionService,
            AdSelectionConfig adSelectionConfig,
            String callerPackageName)
            throws InterruptedException {

        CountDownLatch countdownLatch = new CountDownLatch(1);
        AdSelectionTestCallback adSelectionTestCallback =
                new AdSelectionTestCallback(countdownLatch);

        AdSelectionInput input =
                new AdSelectionInput.Builder()
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(callerPackageName)
                        .build();

        adSelectionService.selectAds(input, mMockCallerMetadata, adSelectionTestCallback);
        adSelectionTestCallback.mCountDownLatch.await();
        return adSelectionTestCallback;
    }

    private String insertJsWait(long waitTime) {
        return "    const wait = (ms) => {\n"
                + "       var start = new Date().getTime();\n"
                + "       var end = start;\n"
                + "       while(end < start + ms) {\n"
                + "         end = new Date().getTime();\n"
                + "      }\n"
                + "    }\n"
                + String.format(Locale.ENGLISH, "    wait(\"%d\");\n", waitTime);
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

    private static class AdSelectionE2ETestFlags implements Flags {
        @Override
        public boolean getEnforceIsolateMaxHeapSize() {
            return false;
        }

        @Override
        public boolean getEnforceForegroundStatusForFledgeRunAdSelection() {
            return true;
        }

        @Override
        public boolean getEnforceForegroundStatusForFledgeReportImpression() {
            return true;
        }

        @Override
        public boolean getEnforceForegroundStatusForFledgeOverrides() {
            return true;
        }

        @Override
        public boolean getDisableFledgeEnrollmentCheck() {
            return true;
        }

        @Override
        public float getSdkRequestPermitsPerSecond() {
            // Unlimited rate for unit tests to avoid flake in tests due to rate
            // limiting
            return -1;
        }
    }
}
