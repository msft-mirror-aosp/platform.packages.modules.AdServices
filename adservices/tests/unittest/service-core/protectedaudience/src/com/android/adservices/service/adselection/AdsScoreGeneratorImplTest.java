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

import static android.adservices.adselection.CustomAudienceBiddingInfoFixture.DATA_VERSION_1;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNSET;

import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS;
import static com.android.adservices.data.adselection.CustomAudienceSignals.CONTEXTUAL_CA_NAME;
import static com.android.adservices.service.adselection.AdsScoreGeneratorImpl.MISSING_TRUSTED_SCORING_SIGNALS;
import static com.android.adservices.service.adselection.AdsScoreGeneratorImpl.QUERY_PARAM_RENDER_URIS;
import static com.android.adservices.service.adselection.AdsScoreGeneratorImpl.SCORES_COUNT_LESS_THAN_EXPECTED;
import static com.android.adservices.service.adselection.AdsScoreGeneratorImpl.SCORING_TIMED_OUT;
import static com.android.adservices.service.adselection.DataVersionFetcher.DATA_VERSION_HEADER_KEY;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.SCRIPT_JAVASCRIPT;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.GET_AD_SCORES_END_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.GET_AD_SCORES_LATENCY_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.GET_AD_SCORES_START_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.GET_AD_SELECTION_LOGIC_END_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.GET_AD_SELECTION_LOGIC_LATENCY_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.GET_AD_SELECTION_LOGIC_START_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.GET_TRUSTED_SCORING_SIGNALS_LATENCY_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.RUN_AD_SCORING_END_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.RUN_AD_SCORING_LATENCY_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.RUN_AD_SCORING_START_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.SCORE_ADS_END_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.SCORE_ADS_LATENCY_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.SCORE_ADS_START_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.START_ELAPSED_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.sCallerMetadata;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.adselection.AdBiddingOutcomeFixture;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdWithBid;
import android.adservices.adselection.SignedContextualAds;
import android.adservices.adselection.SignedContextualAdsFixture;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.http.MockWebServerRule;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.DBAdSelectionOverride;
import com.android.adservices.data.adselection.DBBuyerDecisionOverride;
import com.android.adservices.service.Flags;
import com.android.adservices.service.adselection.debug.DebugReporting;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.AdSelectionDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.AdSelectionExecutionLogger;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerUtil;
import com.android.adservices.service.stats.RunAdScoringProcessReportedStats;
import com.android.adservices.shared.util.Clock;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.json.JSONException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;

public final class AdsScoreGeneratorImplTest extends AdServicesMockitoTestCase {

    private static final AdTechIdentifier BUYER_1 = AdSelectionConfigFixture.BUYER_1;
    private static final AdTechIdentifier BUYER_2 = AdSelectionConfigFixture.BUYER_2;
    private final String mFetchJavaScriptPath = "/fetchJavascript/";
    private final String mTrustedScoringSignalsPath = "/getTrustedScoringSignals/";
    private final String mTrustedScoringSignalsPathWithDataVersionHeader =
            "/getTrustedScoringSignalsWithDataVersionHeader/";
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    private AdSelectionConfig mAdSelectionConfig;

    @Mock private AdSelectionScriptEngine mMockAdSelectionScriptEngine;
    @Mock private AdServicesHttpsClient mMockHttpsClient;

    private ListeningExecutorService mLightweightExecutorService;
    private ListeningExecutorService mBackgroundExecutorService;
    private ListeningExecutorService mBlockingExecutorService;
    private ScheduledThreadPoolExecutor mSchedulingExecutor;
    private AdServicesHttpsClient mWebClient;
    private String mSellerDecisionLogicJs;

    private AdBiddingOutcome mAdBiddingOutcomeBuyer1;
    private AdBiddingOutcome mAdBiddingOutcomeBuyer2;
    private List<AdBiddingOutcome> mAdBiddingOutcomeList;

    private AdsScoreGenerator mAdsScoreGenerator;
    private DevContext mDevContext;
    private Flags mFakeFlags;

    private AdSelectionEntryDao mAdSelectionEntryDao;

    private AdSelectionSignals mTrustedScoringSignals;
    private String mTrustedScoringParams;
    private List<String> mTrustedScoringSignalsKeys;

    private Dispatcher mDefaultDispatcher;
    private MockWebServerRule.RequestMatcher<String> mRequestMatcherExactMatch;
    private AdSelectionExecutionLogger mAdSelectionExecutionLogger;
    private static final boolean DATA_VERSION_HEADER_STATUS_DISABLED = false;
    @Mock private Clock mAdSelectionExecutionLoggerClock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;

    @Mock private DebugReporting mDebugReporting;

    @Captor
    private ArgumentCaptor<RunAdScoringProcessReportedStats>
            mRunAdScoringProcessReportedStatsArgumentCaptor;

    @Before
    public void setUp() throws Exception {
        mFakeFlags = new AdsScoreGeneratorImplTestFlags(false);
        mDevContext = DevContext.createForDevOptionsDisabled();
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mBlockingExecutorService = AdServicesExecutors.getBlockingExecutor();
        mSchedulingExecutor = AdServicesExecutors.getScheduler();
        mWebClient =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        CacheProviderFactory.createNoOpCache());

        mAdBiddingOutcomeBuyer1 =
                AdBiddingOutcomeFixture.anAdBiddingOutcomeBuilder(BUYER_1, 1.0).build();
        mAdBiddingOutcomeBuyer2 =
                AdBiddingOutcomeFixture.anAdBiddingOutcomeBuilder(BUYER_2, 2.0).build();
        mAdBiddingOutcomeList = ImmutableList.of(mAdBiddingOutcomeBuyer1, mAdBiddingOutcomeBuyer2);

        mSellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + " /reporting/seller "
                        + "' } };\n"
                        + "}";

        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        mTrustedScoringSignalsKeys =
                ImmutableList.of(
                        mAdBiddingOutcomeBuyer1
                                .getAdWithBid()
                                .getAdData()
                                .getRenderUri()
                                .getEncodedPath(),
                        mAdBiddingOutcomeBuyer2
                                .getAdWithBid()
                                .getAdData()
                                .getRenderUri()
                                .getEncodedPath());

        mTrustedScoringParams =
                String.format(
                        "?%s=%s",
                        QUERY_PARAM_RENDER_URIS,
                        Uri.encode(String.join(",", mTrustedScoringSignalsKeys)));

        mTrustedScoringSignals =
                AdSelectionSignals.fromString(
                        "{\n"
                                + mAdBiddingOutcomeBuyer1
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri()
                                        .getEncodedPath()
                                + ": signalsForUri1,\n"
                                + mAdBiddingOutcomeBuyer2
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri()
                                        .getEncodedPath()
                                + ": signalsForUri2,\n"
                                + "}");

        mDefaultDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (mFetchJavaScriptPath.equals(request.getPath())) {
                            return new MockResponse().setBody(mSellerDecisionLogicJs);
                        } else if (mTrustedScoringSignalsPath
                                .concat(mTrustedScoringParams)
                                .equals(request.getPath())) {
                            return new MockResponse().setBody(mTrustedScoringSignals.toString());
                        } else if (mTrustedScoringSignalsPathWithDataVersionHeader
                                .concat(mTrustedScoringParams)
                                .equals(request.getPath()))
                            return new MockResponse()
                                    .setBody(mTrustedScoringSignals.toString())
                                    .addHeader(DATA_VERSION_HEADER_KEY, DATA_VERSION_1);
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mRequestMatcherExactMatch =
                (actualRequest, expectedRequest) -> actualRequest.equals(expectedRequest);

        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(START_ELAPSED_TIMESTAMP);
        mAdSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata,
                        mAdSelectionExecutionLoggerClock,
                        ApplicationProvider.getApplicationContext(),
                        mAdServicesLoggerMock,
                        mFakeFlags);
        when(mDebugReporting.isEnabled()).thenReturn(false);
        mAdsScoreGenerator = initAdScoreGenerator(mFakeFlags, DATA_VERSION_HEADER_STATUS_DISABLED);
    }

    @Test
    public void testRunAdScoringSuccess() throws Exception {
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        SCORE_ADS_START_TIMESTAMP,
                        SCORE_ADS_END_TIMESTAMP,
                        GET_AD_SCORES_END_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdScoringProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdScoringProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());

        List<Double> scores = ImmutableList.of(1.0, 2.0);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setDecisionLogicUri(decisionLogicUri)
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(mTrustedScoringSignalsPath))
                        .build();

        Answer<ListenableFuture<List<ScoreAdResult>>> loggerAnswer =
                unused -> {
                    mAdSelectionExecutionLogger.startScoreAds();
                    mAdSelectionExecutionLogger
                            .setScoreAdSellerAdditionalSignalsContainedDataVersion(
                                    DATA_VERSION_HEADER_STATUS_DISABLED);
                    mAdSelectionExecutionLogger.endScoreAds();
                    return Futures.immediateFuture(
                            scores.stream()
                                    .map(score -> ScoreAdResult.builder().setAdScore(score).build())
                                    .collect(Collectors.toList()));
                };
        Mockito.when(
                        mMockAdSelectionScriptEngine.scoreAds(
                                mSellerDecisionLogicJs,
                                mAdBiddingOutcomeList.stream()
                                        .map(a -> a.getAdWithBid())
                                        .collect(Collectors.toList()),
                                mAdSelectionConfig,
                                mAdSelectionConfig.getSellerSignals(),
                                mTrustedScoringSignals,
                                AdSelectionSignals.EMPTY,
                                mAdBiddingOutcomeList.stream()
                                        .map(
                                                a ->
                                                        a.getCustomAudienceBiddingInfo()
                                                                .getCustomAudienceSignals())
                                        .collect(Collectors.toList()),
                                mAdSelectionExecutionLogger))
                .thenAnswer(loggerAnswer);

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        List<AdScoringOutcome> scoringOutcome = waitForFuture(() -> scoringResultFuture);

        Mockito.verify(mMockAdSelectionScriptEngine)
                .scoreAds(
                        mSellerDecisionLogicJs,
                        mAdBiddingOutcomeList.stream()
                                .map(a -> a.getAdWithBid())
                                .collect(Collectors.toList()),
                        mAdSelectionConfig,
                        mAdSelectionConfig.getSellerSignals(),
                        mTrustedScoringSignals,
                        AdSelectionSignals.EMPTY,
                        mAdBiddingOutcomeList.stream()
                                .map(
                                        a ->
                                                a.getCustomAudienceBiddingInfo()
                                                        .getCustomAudienceSignals())
                                .collect(Collectors.toList()),
                        mAdSelectionExecutionLogger);

        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                ImmutableList.of(
                        mFetchJavaScriptPath, mTrustedScoringSignalsPath + mTrustedScoringParams),
                mRequestMatcherExactMatch);
        runAdScoringProcessLoggerLatch.await();
        assertEquals(1L, scoringOutcome.get(0).getAdWithScore().getScore().longValue());
        assertEquals(2L, scoringOutcome.get(1).getAdWithScore().getScore().longValue());
        verifySuccessAdScoringLogging(
                mSellerDecisionLogicJs,
                mTrustedScoringSignals,
                mAdBiddingOutcomeList,
                DATA_VERSION_HEADER_STATUS_DISABLED);
    }

    @Test
    public void testRunAdScoringSuccessWithDataVersionHeaderEnabled() throws Exception {
        // Re init generator
        boolean dataVersionHeaderEnabled = true;
        mAdsScoreGenerator = initAdScoreGenerator(mFakeFlags, dataVersionHeaderEnabled);
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        SCORE_ADS_START_TIMESTAMP,
                        SCORE_ADS_END_TIMESTAMP,
                        GET_AD_SCORES_END_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdScoringProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdScoringProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());

        List<Double> scores = ImmutableList.of(1.0, 2.0);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        // Set trusted scoring uri to return data version header
        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setDecisionLogicUri(decisionLogicUri)
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(
                                        mTrustedScoringSignalsPathWithDataVersionHeader))
                        .build();

        AdSelectionSignals expectedSellerContextualSignals =
                SellerContextualSignals.builder()
                        .setDataVersion(DATA_VERSION_1)
                        .build()
                        .toAdSelectionSignals();

        Answer<ListenableFuture<List<ScoreAdResult>>> loggerAnswer =
                unused -> {
                    mAdSelectionExecutionLogger.startScoreAds();
                    mAdSelectionExecutionLogger
                            .setScoreAdSellerAdditionalSignalsContainedDataVersion(
                                    dataVersionHeaderEnabled);
                    mAdSelectionExecutionLogger.endScoreAds();
                    return Futures.immediateFuture(
                            scores.stream()
                                    .map(score -> ScoreAdResult.builder().setAdScore(score).build())
                                    .collect(Collectors.toList()));
                };
        Mockito.when(
                        mMockAdSelectionScriptEngine.scoreAds(
                                mSellerDecisionLogicJs,
                                mAdBiddingOutcomeList.stream()
                                        .map(a -> a.getAdWithBid())
                                        .collect(Collectors.toList()),
                                mAdSelectionConfig,
                                mAdSelectionConfig.getSellerSignals(),
                                mTrustedScoringSignals,
                                expectedSellerContextualSignals,
                                mAdBiddingOutcomeList.stream()
                                        .map(
                                                a ->
                                                        a.getCustomAudienceBiddingInfo()
                                                                .getCustomAudienceSignals())
                                        .collect(Collectors.toList()),
                                mAdSelectionExecutionLogger))
                .thenAnswer(loggerAnswer);

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        List<AdScoringOutcome> scoringOutcome = waitForFuture(() -> scoringResultFuture);

        Mockito.verify(mMockAdSelectionScriptEngine)
                .scoreAds(
                        mSellerDecisionLogicJs,
                        mAdBiddingOutcomeList.stream()
                                .map(a -> a.getAdWithBid())
                                .collect(Collectors.toList()),
                        mAdSelectionConfig,
                        mAdSelectionConfig.getSellerSignals(),
                        mTrustedScoringSignals,
                        expectedSellerContextualSignals,
                        mAdBiddingOutcomeList.stream()
                                .map(
                                        a ->
                                                a.getCustomAudienceBiddingInfo()
                                                        .getCustomAudienceSignals())
                                .collect(Collectors.toList()),
                        mAdSelectionExecutionLogger);

        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                ImmutableList.of(
                        mFetchJavaScriptPath,
                        mTrustedScoringSignalsPathWithDataVersionHeader + mTrustedScoringParams),
                mRequestMatcherExactMatch);
        runAdScoringProcessLoggerLatch.await();
        assertEquals(1L, scoringOutcome.get(0).getAdWithScore().getScore().longValue());
        assertEquals(2L, scoringOutcome.get(1).getAdWithScore().getScore().longValue());

        // Assert seller contextual signals were propagated through
        assertEquals(
                expectedSellerContextualSignals,
                scoringOutcome.get(0).getSellerContextualSignals().toAdSelectionSignals());
        assertEquals(
                expectedSellerContextualSignals,
                scoringOutcome.get(1).getSellerContextualSignals().toAdSelectionSignals());
        verifySuccessAdScoringLogging(
                mSellerDecisionLogicJs,
                mTrustedScoringSignals,
                mAdBiddingOutcomeList,
                dataVersionHeaderEnabled);
    }

    @Test
    public void testRunAdScoringSuccessWithDataVersionHeaderDisabled() throws Exception {
        // Re init generator
        boolean dataVersionHeaderEnabled = false;
        mAdsScoreGenerator = initAdScoreGenerator(mFakeFlags, dataVersionHeaderEnabled);
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        SCORE_ADS_START_TIMESTAMP,
                        SCORE_ADS_END_TIMESTAMP,
                        GET_AD_SCORES_END_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdScoringProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdScoringProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());

        List<Double> scores = ImmutableList.of(1.0, 2.0);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        // Set trusted scoring uri to return data version header
        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setDecisionLogicUri(decisionLogicUri)
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(
                                        mTrustedScoringSignalsPathWithDataVersionHeader))
                        .build();

        Answer<ListenableFuture<List<ScoreAdResult>>> loggerAnswer =
                unused -> {
                    mAdSelectionExecutionLogger.startScoreAds();
                    mAdSelectionExecutionLogger
                            .setScoreAdSellerAdditionalSignalsContainedDataVersion(
                                    dataVersionHeaderEnabled);
                    mAdSelectionExecutionLogger.endScoreAds();
                    return Futures.immediateFuture(
                            scores.stream()
                                    .map(score -> ScoreAdResult.builder().setAdScore(score).build())
                                    .collect(Collectors.toList()));
                };
        Mockito.when(
                        mMockAdSelectionScriptEngine.scoreAds(
                                mSellerDecisionLogicJs,
                                mAdBiddingOutcomeList.stream()
                                        .map(a -> a.getAdWithBid())
                                        .collect(Collectors.toList()),
                                mAdSelectionConfig,
                                mAdSelectionConfig.getSellerSignals(),
                                mTrustedScoringSignals,
                                AdSelectionSignals.EMPTY,
                                mAdBiddingOutcomeList.stream()
                                        .map(
                                                a ->
                                                        a.getCustomAudienceBiddingInfo()
                                                                .getCustomAudienceSignals())
                                        .collect(Collectors.toList()),
                                mAdSelectionExecutionLogger))
                .thenAnswer(loggerAnswer);

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        List<AdScoringOutcome> scoringOutcome = waitForFuture(() -> scoringResultFuture);

        Mockito.verify(mMockAdSelectionScriptEngine)
                .scoreAds(
                        mSellerDecisionLogicJs,
                        mAdBiddingOutcomeList.stream()
                                .map(a -> a.getAdWithBid())
                                .collect(Collectors.toList()),
                        mAdSelectionConfig,
                        mAdSelectionConfig.getSellerSignals(),
                        mTrustedScoringSignals,
                        AdSelectionSignals.EMPTY,
                        mAdBiddingOutcomeList.stream()
                                .map(
                                        a ->
                                                a.getCustomAudienceBiddingInfo()
                                                        .getCustomAudienceSignals())
                                .collect(Collectors.toList()),
                        mAdSelectionExecutionLogger);

        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                ImmutableList.of(
                        mFetchJavaScriptPath,
                        mTrustedScoringSignalsPathWithDataVersionHeader + mTrustedScoringParams),
                mRequestMatcherExactMatch);
        runAdScoringProcessLoggerLatch.await();
        assertEquals(1L, scoringOutcome.get(0).getAdWithScore().getScore().longValue());
        assertEquals(2L, scoringOutcome.get(1).getAdWithScore().getScore().longValue());

        // Assert seller contextual signals were propagated through
        assertEquals(
                AdSelectionSignals.EMPTY,
                scoringOutcome.get(0).getSellerContextualSignals().toAdSelectionSignals());
        assertEquals(
                AdSelectionSignals.EMPTY,
                scoringOutcome.get(1).getSellerContextualSignals().toAdSelectionSignals());
        verifySuccessAdScoringLogging(
                mSellerDecisionLogicJs,
                mTrustedScoringSignals,
                mAdBiddingOutcomeList,
                dataVersionHeaderEnabled);
    }

    @Test
    public void testRunAdScoringSuccess_withDebugReportingEnabled() throws Exception {
        Uri winUri = Uri.parse("http://example.com/reportWin");
        Uri lossUri = Uri.parse("http://example.com/reportLoss");
        String sellerRejectReason = "invalid-bid";
        when(mDebugReporting.isEnabled()).thenReturn(true);
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        SCORE_ADS_START_TIMESTAMP,
                        SCORE_ADS_END_TIMESTAMP,
                        GET_AD_SCORES_END_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdScoringProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                unusedInvocation -> {
                    runAdScoringProcessLoggerLatch.countDown();
                    return null;
                })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());

        List<Double> scores = ImmutableList.of(1.0, 2.0);

        String decisionLogicUriString = "https://example.com" + mFetchJavaScriptPath;
        String trustedScoringSignalsUriString = "https://example.com" + mTrustedScoringSignalsPath;
        Uri decisionLogicUri = Uri.parse(decisionLogicUriString);

        // Running mock web server was causing flakiness/instability in this test. Hence, we are
        // mocking the http response in here
        when(mMockHttpsClient.fetchPayloadWithLogging(
                        argThat(PathMatcher.matchesPath(mFetchJavaScriptPath)), any()))
                .thenReturn(httpResponseWithBody(mSellerDecisionLogicJs));

        when(mMockHttpsClient.fetchPayload(
                        argThat(PathMatcher.matchesPath(mTrustedScoringSignalsPath)), any()))
                .thenReturn(httpResponseWithBody(mTrustedScoringSignals.toString()));

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setDecisionLogicUri(decisionLogicUri)
                        .setTrustedScoringSignalsUri(Uri.parse(trustedScoringSignalsUriString))
                        .build();

        Answer<ListenableFuture<List<ScoreAdResult>>> loggerAnswer =
                unused -> {
                    mAdSelectionExecutionLogger.startScoreAds();
                    mAdSelectionExecutionLogger
                            .setScoreAdSellerAdditionalSignalsContainedDataVersion(
                                    DATA_VERSION_HEADER_STATUS_DISABLED);
                    mAdSelectionExecutionLogger.endScoreAds();
                    return Futures.immediateFuture(
                            scores.stream()
                                    .map(
                                            score ->
                                                    ScoreAdResult.builder()
                                                            .setAdScore(score)
                                                            .setWinDebugReportUri(winUri)
                                                            .setLossDebugReportUri(lossUri)
                                                            .setSellerRejectReason(
                                                                    sellerRejectReason)
                                                            .build())
                                    .collect(Collectors.toList()));
                };
        Mockito.when(
                        mMockAdSelectionScriptEngine.scoreAds(
                                mSellerDecisionLogicJs,
                                mAdBiddingOutcomeList.stream()
                                        .map(a -> a.getAdWithBid())
                                        .collect(Collectors.toList()),
                                mAdSelectionConfig,
                                mAdSelectionConfig.getSellerSignals(),
                                mTrustedScoringSignals,
                                AdSelectionSignals.EMPTY,
                                mAdBiddingOutcomeList.stream()
                                        .map(
                                                a ->
                                                        a.getCustomAudienceBiddingInfo()
                                                                .getCustomAudienceSignals())
                                        .collect(Collectors.toList()),
                                mAdSelectionExecutionLogger))
                .thenAnswer(loggerAnswer);

        AdsScoreGenerator adsScoreGenerator =
                initAdScoreGeneratorWithMockHttpClient(
                        mFakeFlags, DATA_VERSION_HEADER_STATUS_DISABLED);

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                adsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        List<AdScoringOutcome> scoringOutcome = waitForFuture(() -> scoringResultFuture);

        Mockito.verify(mMockAdSelectionScriptEngine)
                .scoreAds(
                        mSellerDecisionLogicJs,
                        mAdBiddingOutcomeList.stream()
                                .map(a -> a.getAdWithBid())
                                .collect(Collectors.toList()),
                        mAdSelectionConfig,
                        mAdSelectionConfig.getSellerSignals(),
                        mTrustedScoringSignals,
                        AdSelectionSignals.EMPTY,
                        mAdBiddingOutcomeList.stream()
                                .map(
                                        a ->
                                                a.getCustomAudienceBiddingInfo()
                                                        .getCustomAudienceSignals())
                                .collect(Collectors.toList()),
                        mAdSelectionExecutionLogger);

        runAdScoringProcessLoggerLatch.await();

        ArgumentCaptor<AdServicesHttpClientRequest> requestArgumentCaptor =
                ArgumentCaptor.forClass(AdServicesHttpClientRequest.class);
        verify(mMockHttpsClient).fetchPayloadWithLogging(requestArgumentCaptor.capture(), any());
        assertEquals(mFetchJavaScriptPath, requestArgumentCaptor.getValue().getUri().getPath());

        ArgumentCaptor<Uri> uriArgumentCaptor = ArgumentCaptor.forClass(Uri.class);
        ArgumentCaptor<DevContext> devContextArgumentCaptor =
                ArgumentCaptor.forClass(DevContext.class);

        verify(mMockHttpsClient)
                .fetchPayload(uriArgumentCaptor.capture(), devContextArgumentCaptor.capture());
        assertEquals(mTrustedScoringSignalsPath, uriArgumentCaptor.getValue().getPath());

        assertEquals(winUri, scoringOutcome.get(0).getDebugReport().getWinDebugReportUri());
        assertEquals(lossUri, scoringOutcome.get(0).getDebugReport().getLossDebugReportUri());
        assertEquals(
                sellerRejectReason, scoringOutcome.get(0).getDebugReport().getSellerRejectReason());
        assertEquals(winUri, scoringOutcome.get(1).getDebugReport().getWinDebugReportUri());
        assertEquals(lossUri, scoringOutcome.get(1).getDebugReport().getLossDebugReportUri());
        assertEquals(
                sellerRejectReason, scoringOutcome.get(1).getDebugReport().getSellerRejectReason());
        verifySuccessAdScoringLogging(
                mSellerDecisionLogicJs,
                mTrustedScoringSignals,
                mAdBiddingOutcomeList,
                DATA_VERSION_HEADER_STATUS_DISABLED);
    }

    @Test
    public void testRunAdScoringContextual_Success() throws Exception {
        mFakeFlags = new AdsScoreGeneratorImplTestFlags(true);
        boolean dataVersionHeaderEnabled = false;
        mAdsScoreGenerator = initAdScoreGenerator(mFakeFlags, dataVersionHeaderEnabled);
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        SCORE_ADS_START_TIMESTAMP,
                        SCORE_ADS_END_TIMESTAMP,
                        GET_AD_SCORES_END_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdScoringProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdScoringProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());

        List<Double> scores = ImmutableList.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        Map<AdTechIdentifier, SignedContextualAds> contextualAdsMap = createContextualAds();
        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigWithSignedContextualAdsBuilder()
                        .setDecisionLogicUri(decisionLogicUri)
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(mTrustedScoringSignalsPath))
                        .setPerBuyerSignedContextualAds(contextualAdsMap)
                        .build();

        List<AdWithBid> adsWithBid =
                mAdBiddingOutcomeList.stream()
                        .map(a -> a.getAdWithBid())
                        .collect(Collectors.toList());
        List<SignedContextualAds> signedContextualAds =
                mAdSelectionConfig.getPerBuyerSignedContextualAds().values().stream()
                        .collect(Collectors.toList());
        List<AdWithBid> contextualBidAds = new ArrayList<>();
        for (SignedContextualAds ctx : signedContextualAds) {
            contextualBidAds.addAll(ctx.getAdsWithBid());
        }

        adsWithBid.addAll(contextualBidAds);
        Answer<ListenableFuture<List<ScoreAdResult>>> loggerAnswer =
                unused -> {
                    mAdSelectionExecutionLogger.startScoreAds();
                    mAdSelectionExecutionLogger
                            .setScoreAdSellerAdditionalSignalsContainedDataVersion(
                                    dataVersionHeaderEnabled);
                    mAdSelectionExecutionLogger.endScoreAds();
                    return Futures.immediateFuture(
                            scores.stream()
                                    .map(score -> ScoreAdResult.builder().setAdScore(score).build())
                                    .collect(Collectors.toList()));
                };
        Mockito.when(
                        mMockAdSelectionScriptEngine.scoreAds(
                                mSellerDecisionLogicJs,
                                adsWithBid,
                                mAdSelectionConfig,
                                mAdSelectionConfig.getSellerSignals(),
                                mTrustedScoringSignals,
                                AdSelectionSignals.EMPTY,
                                mAdBiddingOutcomeList.stream()
                                        .map(
                                                a ->
                                                        a.getCustomAudienceBiddingInfo()
                                                                .getCustomAudienceSignals())
                                        .collect(Collectors.toList()),
                                mAdSelectionExecutionLogger))
                .thenAnswer(loggerAnswer);

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        List<AdScoringOutcome> scoringOutcome = waitForFuture(() -> scoringResultFuture);

        Mockito.verify(mMockAdSelectionScriptEngine)
                .scoreAds(
                        mSellerDecisionLogicJs,
                        adsWithBid,
                        mAdSelectionConfig,
                        mAdSelectionConfig.getSellerSignals(),
                        mTrustedScoringSignals,
                        AdSelectionSignals.EMPTY,
                        mAdBiddingOutcomeList.stream()
                                .map(
                                        a ->
                                                a.getCustomAudienceBiddingInfo()
                                                        .getCustomAudienceSignals())
                                .collect(Collectors.toList()),
                        mAdSelectionExecutionLogger);

        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                ImmutableList.of(
                        mFetchJavaScriptPath, mTrustedScoringSignalsPath + mTrustedScoringParams),
                mRequestMatcherExactMatch);
        runAdScoringProcessLoggerLatch.await();
        assertEquals(1L, scoringOutcome.get(0).getAdWithScore().getScore().longValue());
        assertEquals(2L, scoringOutcome.get(1).getAdWithScore().getScore().longValue());
        assertEquals(5L, scoringOutcome.get(4).getAdWithScore().getScore().longValue());
        assertEquals(300, scoringOutcome.get(4).getAdWithScore().getAdWithBid().getBid(), 0);
        assertEquals(500, scoringOutcome.get(6).getAdWithScore().getAdWithBid().getBid(), 0);

        verifySuccessAdScoringLogging(
                mSellerDecisionLogicJs,
                mTrustedScoringSignals,
                mAdBiddingOutcomeList,
                DATA_VERSION_HEADER_STATUS_DISABLED);
    }


    @Test
    public void testRunAdScoringContextual_withDebugReportingEnabled_Success() throws Exception {
        mFakeFlags = new AdsScoreGeneratorImplTestFlags(true);
        boolean dataVersionHeaderEnabled = false;
        mAdsScoreGenerator = initAdScoreGenerator(mFakeFlags, dataVersionHeaderEnabled);
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        SCORE_ADS_START_TIMESTAMP,
                        SCORE_ADS_END_TIMESTAMP,
                        GET_AD_SCORES_END_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdScoringProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                unusedInvocation -> {
                    runAdScoringProcessLoggerLatch.countDown();
                    return null;
                })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        when(mDebugReporting.isEnabled()).thenReturn(true);

        List<Double> scores = ImmutableList.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        Map<AdTechIdentifier, SignedContextualAds> contextualAdsMap = createContextualAds();
        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigWithSignedContextualAdsBuilder()
                        .setDecisionLogicUri(decisionLogicUri)
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(mTrustedScoringSignalsPath))
                        .setPerBuyerSignedContextualAds(contextualAdsMap)
                        .build();

        List<AdWithBid> adsWithBid =
                mAdBiddingOutcomeList.stream()
                        .map(a -> a.getAdWithBid())
                        .collect(Collectors.toList());
        List<SignedContextualAds> signedContextualAds =
                mAdSelectionConfig.getPerBuyerSignedContextualAds().values().stream()
                        .collect(Collectors.toList());
        List<AdWithBid> contextualBidAds = new ArrayList<>();
        for (SignedContextualAds ctx : signedContextualAds) {
            contextualBidAds.addAll(ctx.getAdsWithBid());
        }

        adsWithBid.addAll(contextualBidAds);
        Answer<ListenableFuture<List<ScoreAdResult>>> loggerAnswer =
                unused -> {
                    mAdSelectionExecutionLogger.startScoreAds();
                    mAdSelectionExecutionLogger
                            .setScoreAdSellerAdditionalSignalsContainedDataVersion(
                                    dataVersionHeaderEnabled);
                    mAdSelectionExecutionLogger.endScoreAds();
                    return Futures.immediateFuture(
                            scores.stream()
                                    .map(
                                            score ->
                                                    ScoreAdResult.builder()
                                                            .setAdScore(score)
                                                            .setWinDebugReportUri(
                                                                    Uri.parse(
                                                                            "http://example.com/1"))
                                                            .setLossDebugReportUri(
                                                                    Uri.parse(
                                                                            "http://example.com/2"))
                                                            .setSellerRejectReason("invalid-bid")
                                                            .build())
                                    .collect(Collectors.toList()));
                };
        Mockito.when(
                        mMockAdSelectionScriptEngine.scoreAds(
                                mSellerDecisionLogicJs,
                                adsWithBid,
                                mAdSelectionConfig,
                                mAdSelectionConfig.getSellerSignals(),
                                mTrustedScoringSignals,
                                AdSelectionSignals.EMPTY,
                                mAdBiddingOutcomeList.stream()
                                        .map(
                                                a ->
                                                        a.getCustomAudienceBiddingInfo()
                                                                .getCustomAudienceSignals())
                                        .collect(Collectors.toList()),
                                mAdSelectionExecutionLogger))
                .thenAnswer(loggerAnswer);

        AdsScoreGenerator adsScoreGenerator =
                initAdScoreGenerator(mFakeFlags, dataVersionHeaderEnabled);

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                adsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        List<AdScoringOutcome> scoringOutcome = waitForFuture(() -> scoringResultFuture);

        Mockito.verify(mMockAdSelectionScriptEngine)
                .scoreAds(
                        mSellerDecisionLogicJs,
                        adsWithBid,
                        mAdSelectionConfig,
                        mAdSelectionConfig.getSellerSignals(),
                        mTrustedScoringSignals,
                        AdSelectionSignals.EMPTY,
                        mAdBiddingOutcomeList.stream()
                                .map(
                                        a ->
                                                a.getCustomAudienceBiddingInfo()
                                                        .getCustomAudienceSignals())
                                .collect(Collectors.toList()),
                        mAdSelectionExecutionLogger);

        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                ImmutableList.of(
                        mFetchJavaScriptPath, mTrustedScoringSignalsPath + mTrustedScoringParams),
                mRequestMatcherExactMatch);
        runAdScoringProcessLoggerLatch.await();
        assertEquals(
                Uri.parse("http://example.com/1"),
                scoringOutcome.get(0).getDebugReport().getWinDebugReportUri());
        assertEquals(
                Uri.parse("http://example.com/2"),
                scoringOutcome.get(0).getDebugReport().getLossDebugReportUri());
        assertEquals("invalid-bid", scoringOutcome.get(0).getDebugReport().getSellerRejectReason());
        assertEquals(
                Uri.parse("http://example.com/1"),
                scoringOutcome.get(1).getDebugReport().getWinDebugReportUri());
        assertEquals(
                Uri.parse("http://example.com/2"),
                scoringOutcome.get(1).getDebugReport().getLossDebugReportUri());
        assertEquals("invalid-bid", scoringOutcome.get(1).getDebugReport().getSellerRejectReason());

        verifySuccessAdScoringLogging(
                mSellerDecisionLogicJs,
                mTrustedScoringSignals,
                mAdBiddingOutcomeList,
                dataVersionHeaderEnabled);
    }

    @Test
    public void testRunAdScoringContextual_UseOverride_Success() throws Exception {
        mFakeFlags = new AdsScoreGeneratorImplTestFlags(true);
        boolean dataVersionHeaderEnabled = false;
        mAdsScoreGenerator = initAdScoreGenerator(mFakeFlags, dataVersionHeaderEnabled);
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        SCORE_ADS_START_TIMESTAMP,
                        SCORE_ADS_END_TIMESTAMP,
                        GET_AD_SCORES_END_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdScoringProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdScoringProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());

        List<Double> scores = ImmutableList.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        Map<AdTechIdentifier, SignedContextualAds> contextualAdsMap = createContextualAds();
        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigWithSignedContextualAdsBuilder()
                        .setDecisionLogicUri(decisionLogicUri)
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(mTrustedScoringSignalsPath))
                        .setPerBuyerSignedContextualAds(contextualAdsMap)
                        .build();

        List<AdWithBid> adsWithBid =
                mAdBiddingOutcomeList.stream()
                        .map(a -> a.getAdWithBid())
                        .collect(Collectors.toList());
        List<SignedContextualAds> signedContextualAds =
                mAdSelectionConfig.getPerBuyerSignedContextualAds().values().stream()
                        .collect(Collectors.toList());
        List<AdWithBid> contextualBidAds = new ArrayList<>();
        for (SignedContextualAds ctx : signedContextualAds) {
            contextualBidAds.addAll(ctx.getAdsWithBid());
        }

        final String fakeDecisionLogicForBuyer = "\"reportWin() { completely fake }\"";
        // Create an override for buyers decision logic only for Buyer 2
        String myAppPackageName = "com.google.ppapi.test";
        DBAdSelectionOverride adSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(
                                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                        mAdSelectionConfig))
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(mSellerDecisionLogicJs)
                        .setTrustedScoringSignals(mTrustedScoringSignals.toString())
                        .build();
        mAdSelectionEntryDao.persistAdSelectionOverride(adSelectionOverride);
        DBBuyerDecisionOverride buyerDecisionOverride =
                DBBuyerDecisionOverride.builder()
                        .setAdSelectionConfigId(
                                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                        mAdSelectionConfig))
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogic(fakeDecisionLogicForBuyer)
                        .setBuyer(BUYER_2)
                        .build();
        mAdSelectionEntryDao.persistPerBuyerDecisionLogicOverride(
                ImmutableList.of(buyerDecisionOverride));
        mDevContext = DevContext.builder(myAppPackageName).setDeviceDevOptionsEnabled(true).build();

        adsWithBid.addAll(contextualBidAds);
        Answer<ListenableFuture<List<ScoreAdResult>>> loggerAnswer =
                unused -> {
                    mAdSelectionExecutionLogger.startScoreAds();
                    mAdSelectionExecutionLogger
                            .setScoreAdSellerAdditionalSignalsContainedDataVersion(
                                    dataVersionHeaderEnabled);
                    mAdSelectionExecutionLogger.endScoreAds();
                    return Futures.immediateFuture(
                            scores.stream()
                                    .map(score -> ScoreAdResult.builder().setAdScore(score).build())
                                    .collect(Collectors.toList()));
                };
        Mockito.when(
                        mMockAdSelectionScriptEngine.scoreAds(
                                mSellerDecisionLogicJs,
                                adsWithBid,
                                mAdSelectionConfig,
                                mAdSelectionConfig.getSellerSignals(),
                                mTrustedScoringSignals,
                                AdSelectionSignals.EMPTY,
                                mAdBiddingOutcomeList.stream()
                                        .map(
                                                a ->
                                                        a.getCustomAudienceBiddingInfo()
                                                                .getCustomAudienceSignals())
                                        .collect(Collectors.toList()),
                                mAdSelectionExecutionLogger))
                .thenAnswer(loggerAnswer);

        mAdsScoreGenerator = initAdScoreGenerator(mFakeFlags, dataVersionHeaderEnabled);
        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        List<AdScoringOutcome> scoringOutcome = waitForFuture(() -> scoringResultFuture);

        Mockito.verify(mMockAdSelectionScriptEngine)
                .scoreAds(
                        mSellerDecisionLogicJs,
                        adsWithBid,
                        mAdSelectionConfig,
                        mAdSelectionConfig.getSellerSignals(),
                        mTrustedScoringSignals,
                        AdSelectionSignals.EMPTY,
                        mAdBiddingOutcomeList.stream()
                                .map(
                                        a ->
                                                a.getCustomAudienceBiddingInfo()
                                                        .getCustomAudienceSignals())
                                .collect(Collectors.toList()),
                        mAdSelectionExecutionLogger);

        // No calls should have been made to the server, as overrides are set
        mMockWebServerRule.verifyMockServerRequests(
                server, 0, ImmutableList.of(), mRequestMatcherExactMatch);
        runAdScoringProcessLoggerLatch.await();
        assertEquals(1L, scoringOutcome.get(0).getAdWithScore().getScore().longValue());
        assertEquals(2L, scoringOutcome.get(1).getAdWithScore().getScore().longValue());
        assertEquals(5L, scoringOutcome.get(4).getAdWithScore().getScore().longValue());
        assertEquals(300, scoringOutcome.get(4).getAdWithScore().getAdWithBid().getBid(), 0);
        assertEquals(500, scoringOutcome.get(6).getAdWithScore().getAdWithBid().getBid(), 0);
        validateCustomAudienceSignals(scoringOutcome.get(6).getCustomAudienceSignals(), BUYER_2);

        // Only buyer2 decision logic should have been populated from overrides
        assertFalse(
                "Buyer 1 should not have gotten decision logic",
                scoringOutcome.get(4).isBiddingLogicJsDownloaded());
        assertTrue(
                "Buyer 2 ctx ads should have gotten decision logic from overrides",
                scoringOutcome.get(5).isBiddingLogicJsDownloaded()
                        && scoringOutcome.get(6).isBiddingLogicJsDownloaded());
        assertEquals(fakeDecisionLogicForBuyer, scoringOutcome.get(6).getBiddingLogicJs());

        verifySuccessAdScoringLogging(
                mSellerDecisionLogicJs,
                mTrustedScoringSignals,
                mAdBiddingOutcomeList,
                dataVersionHeaderEnabled);
    }

    @Test
    public void testRunAdScoringContextualScoresMismatch_Failure() throws Exception {
        mFakeFlags = new AdsScoreGeneratorImplTestFlags(true);
        boolean dataVersionHeaderEnabled = false;
        mAdsScoreGenerator = initAdScoreGenerator(mFakeFlags, dataVersionHeaderEnabled);
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        SCORE_ADS_START_TIMESTAMP,
                        SCORE_ADS_END_TIMESTAMP,
                        GET_AD_SCORES_END_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdScoringProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdScoringProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());

        // Ads expected to be scored are 7, but scoring is wired to return only 6 scores
        List<Double> scores = ImmutableList.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        Map<AdTechIdentifier, SignedContextualAds> contextualAdsMap = createContextualAds();
        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigWithSignedContextualAdsBuilder()
                        .setDecisionLogicUri(decisionLogicUri)
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(mTrustedScoringSignalsPath))
                        .setPerBuyerSignedContextualAds(contextualAdsMap)
                        .build();

        List<AdWithBid> adsWithBid =
                mAdBiddingOutcomeList.stream()
                        .map(a -> a.getAdWithBid())
                        .collect(Collectors.toList());
        List<SignedContextualAds> signedContextualAds =
                mAdSelectionConfig.getPerBuyerSignedContextualAds().values().stream()
                        .collect(Collectors.toList());
        List<AdWithBid> contextualBidAds = new ArrayList<>();
        for (SignedContextualAds ctx : signedContextualAds) {
            contextualBidAds.addAll(ctx.getAdsWithBid());
        }

        adsWithBid.addAll(contextualBidAds);
        Answer<ListenableFuture<List<ScoreAdResult>>> loggerAnswer =
                unused -> {
                    mAdSelectionExecutionLogger.startScoreAds();
                    mAdSelectionExecutionLogger
                            .setScoreAdSellerAdditionalSignalsContainedDataVersion(
                                    dataVersionHeaderEnabled);
                    mAdSelectionExecutionLogger.endScoreAds();
                    return Futures.immediateFuture(
                            scores.stream()
                                    .map(score -> ScoreAdResult.builder().setAdScore(score).build())
                                    .collect(Collectors.toList()));
                };
        Mockito.when(
                        mMockAdSelectionScriptEngine.scoreAds(
                                mSellerDecisionLogicJs,
                                adsWithBid,
                                mAdSelectionConfig,
                                mAdSelectionConfig.getSellerSignals(),
                                mTrustedScoringSignals,
                                AdSelectionSignals.EMPTY,
                                mAdBiddingOutcomeList.stream()
                                        .map(
                                                a ->
                                                        a.getCustomAudienceBiddingInfo()
                                                                .getCustomAudienceSignals())
                                        .collect(Collectors.toList()),
                                mAdSelectionExecutionLogger))
                .thenAnswer(loggerAnswer);

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        IllegalStateException missingScoresException =
                new IllegalStateException(SCORES_COUNT_LESS_THAN_EXPECTED);

        ExecutionException outException =
                assertThrows(ExecutionException.class, scoringResultFuture::get);
        assertEquals(outException.getCause().getMessage(), missingScoresException.getMessage());
    }

    @Test
    public void testMissingTrustedSignalsException() throws Exception {
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdScoringProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdScoringProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());

        // Missing server connection for trusted signals
        Dispatcher missingSignalsDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (mFetchJavaScriptPath.equals(request.getPath())) {
                            return new MockResponse().setBody(mSellerDecisionLogicJs);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };
        MockWebServer server = mMockWebServerRule.startMockWebServer(missingSignalsDispatcher);

        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        IllegalStateException missingSignalsException =
                new IllegalStateException(MISSING_TRUSTED_SCORING_SIGNALS);

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setDecisionLogicUri(decisionLogicUri)
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(mTrustedScoringSignalsPath))
                        .build();

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);
        runAdScoringProcessLoggerLatch.await();
        ExecutionException outException =
                assertThrows(ExecutionException.class, scoringResultFuture::get);
        assertEquals(outException.getCause().getMessage(), missingSignalsException.getMessage());
        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                ImmutableList.of(
                        mFetchJavaScriptPath, mTrustedScoringSignalsPath + mTrustedScoringParams),
                mRequestMatcherExactMatch);
        verifyFailedAdScoringLoggingMissingTrustedScoringSignals(
                mSellerDecisionLogicJs,
                mAdBiddingOutcomeList,
                AdServicesLoggerUtil.getResultCodeFromException(
                        missingSignalsException.getCause()));
    }

    @Test
    public void testRunAdScoringUseDevOverrideForJS() throws Exception {
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        SCORE_ADS_START_TIMESTAMP,
                        SCORE_ADS_END_TIMESTAMP,
                        GET_AD_SCORES_END_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdScoringProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdScoringProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());

        List<Double> scores = ImmutableList.of(1.0, 2.0);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);
        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        // Different seller decision logic JS to simulate different different override from server
        String differentSellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":2}', 'reporting_uri': '"
                        + " /reporting/seller "
                        + "' } };\n"
                        + "}";

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setDecisionLogicUri(decisionLogicUri)
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(mTrustedScoringSignalsPath))
                        .build();

        // Set dev override for this AdSelection
        String myAppPackageName = "com.google.ppapi.test";
        DBAdSelectionOverride adSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(
                                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                        mAdSelectionConfig))
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(differentSellerDecisionLogicJs)
                        .setTrustedScoringSignals(mTrustedScoringSignals.toString())
                        .build();
        mAdSelectionEntryDao.persistAdSelectionOverride(adSelectionOverride);

        // Resetting Generator to use new dev context
        mDevContext = DevContext.builder(myAppPackageName).setDeviceDevOptionsEnabled(true).build();

        boolean dataVersionHeaderEnabled = false;
        mAdsScoreGenerator = initAdScoreGenerator(mFakeFlags, dataVersionHeaderEnabled);
        Answer<ListenableFuture<List<ScoreAdResult>>> loggerAnswer =
                unused -> {
                    mAdSelectionExecutionLogger.startScoreAds();
                    mAdSelectionExecutionLogger
                            .setScoreAdSellerAdditionalSignalsContainedDataVersion(
                                    dataVersionHeaderEnabled);
                    mAdSelectionExecutionLogger.endScoreAds();
                    return Futures.immediateFuture(
                            scores.stream()
                                    .map(score -> ScoreAdResult.builder().setAdScore(score).build())
                                    .collect(Collectors.toList()));
                };
        Mockito.when(
                        mMockAdSelectionScriptEngine.scoreAds(
                                differentSellerDecisionLogicJs,
                                mAdBiddingOutcomeList.stream()
                                        .map(AdBiddingOutcome::getAdWithBid)
                                        .collect(Collectors.toList()),
                                mAdSelectionConfig,
                                mAdSelectionConfig.getSellerSignals(),
                                mTrustedScoringSignals,
                                AdSelectionSignals.EMPTY,
                                mAdBiddingOutcomeList.stream()
                                        .map(
                                                a ->
                                                        a.getCustomAudienceBiddingInfo()
                                                                .getCustomAudienceSignals())
                                        .collect(Collectors.toList()),
                                mAdSelectionExecutionLogger))
                .thenAnswer(loggerAnswer);

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        List<AdScoringOutcome> scoringOutcome = waitForFuture(() -> scoringResultFuture);
        runAdScoringProcessLoggerLatch.await();
        // The server will not be invoked as the web calls should be overridden
        mMockWebServerRule.verifyMockServerRequests(
                server, 0, Collections.emptyList(), mRequestMatcherExactMatch);
        Assert.assertEquals(1L, scoringOutcome.get(0).getAdWithScore().getScore().longValue());
        Assert.assertEquals(2L, scoringOutcome.get(1).getAdWithScore().getScore().longValue());
        verifySuccessAdScoringLogging(
                differentSellerDecisionLogicJs,
                mTrustedScoringSignals,
                mAdBiddingOutcomeList,
                dataVersionHeaderEnabled);
    }

    @Test
    public void testRunAdScoringJsonException() throws Exception {
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdScoringProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdScoringProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());

        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);
        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setDecisionLogicUri(decisionLogicUri)
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(mTrustedScoringSignalsPath))
                        .build();

        Mockito.when(
                        mMockAdSelectionScriptEngine.scoreAds(
                                mSellerDecisionLogicJs,
                                mAdBiddingOutcomeList.stream()
                                        .map(a -> a.getAdWithBid())
                                        .collect(Collectors.toList()),
                                mAdSelectionConfig,
                                mAdSelectionConfig.getSellerSignals(),
                                mTrustedScoringSignals,
                                AdSelectionSignals.EMPTY,
                                mAdBiddingOutcomeList.stream()
                                        .map(
                                                a ->
                                                        a.getCustomAudienceBiddingInfo()
                                                                .getCustomAudienceSignals())
                                        .collect(Collectors.toList()),
                                mAdSelectionExecutionLogger))
                .thenThrow(new JSONException("Badly formatted JSON"));

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);
        runAdScoringProcessLoggerLatch.await();
        ExecutionException adServicesException =
                Assert.assertThrows(
                        ExecutionException.class,
                        () -> {
                            waitForFuture(() -> scoringResultFuture);
                        });
        Truth.assertThat(adServicesException.getMessage()).contains("Badly formatted JSON");
        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                ImmutableList.of(
                        mFetchJavaScriptPath, mTrustedScoringSignalsPath + mTrustedScoringParams),
                mRequestMatcherExactMatch);
        verifyFailedAdScoringLoggingJSONExceptionWithScoreAds(
                mSellerDecisionLogicJs,
                mTrustedScoringSignals,
                mAdBiddingOutcomeList,
                AdServicesLoggerUtil.getResultCodeFromException(adServicesException.getCause()));
    }

    @Test
    public void testRunAdScoringTimesOut() throws Exception {
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdScoringProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdScoringProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());

        Flags flagsWithSmallerLimits =
                new Flags() {
                    @Override
                    public long getAdSelectionScoringTimeoutMs() {
                        return 100;
                    }
                };
        mAdsScoreGenerator = initAdScoreGeneratorWithMockHttpClient(flagsWithSmallerLimits, false);

        List<Double> scores = Arrays.asList(1.0, 2.0);

        String decisionLogicUriString = "https://example.com" + mFetchJavaScriptPath;
        String trustedScoringSignalsUriString = "https://example.com" + mTrustedScoringSignalsPath;
        Uri decisionLogicUri = Uri.parse(decisionLogicUriString);

        // Running mock web server was causing flakiness/instability in this test. Hence, we are
        // mocking the http response in here
        when(mMockHttpsClient.fetchPayloadWithLogging(
                        argThat(PathMatcher.matchesPath(mFetchJavaScriptPath)), any()))
                .thenReturn(httpResponseWithBody(mSellerDecisionLogicJs));

        when(mMockHttpsClient.fetchPayload(
                        argThat(PathMatcher.matchesPath(mTrustedScoringSignalsPath)), any()))
                .thenReturn(httpResponseWithBody(mTrustedScoringSignals.toString()));

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setDecisionLogicUri(decisionLogicUri)
                        .setTrustedScoringSignalsUri(Uri.parse(trustedScoringSignalsUriString))
                        .build();

        Mockito.when(
                        mMockAdSelectionScriptEngine.scoreAds(
                                mSellerDecisionLogicJs,
                                mAdBiddingOutcomeList.stream()
                                        .map(a -> a.getAdWithBid())
                                        .collect(Collectors.toList()),
                                mAdSelectionConfig,
                                mAdSelectionConfig.getSellerSignals(),
                                mTrustedScoringSignals,
                                AdSelectionSignals.EMPTY,
                                mAdBiddingOutcomeList.stream()
                                        .map(
                                                a ->
                                                        a.getCustomAudienceBiddingInfo()
                                                                .getCustomAudienceSignals())
                                        .collect(Collectors.toList()),
                                mAdSelectionExecutionLogger))
                .thenAnswer((invocation) -> getScoresWithDelay(scores, flagsWithSmallerLimits));

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);
        runAdScoringProcessLoggerLatch.await();
        ExecutionException thrown =
                assertThrows(ExecutionException.class, scoringResultFuture::get);
        assertTrue(thrown.getMessage().contains(SCORING_TIMED_OUT));
        verifyFailedAdScoringLoggingTimeout(
                mAdBiddingOutcomeList,
                AdServicesLoggerUtil.getResultCodeFromException(thrown.getCause()));
    }

    private AdsScoreGenerator initAdScoreGenerator(Flags flags, boolean dataVersionHeaderEnabled) {
        return new AdsScoreGeneratorImpl(
                mMockAdSelectionScriptEngine,
                mLightweightExecutorService,
                mBackgroundExecutorService,
                mSchedulingExecutor,
                mWebClient,
                mDevContext,
                mAdSelectionEntryDao,
                flags,
                mAdSelectionExecutionLogger,
                mDebugReporting,
                dataVersionHeaderEnabled);
    }

    private AdsScoreGenerator initAdScoreGeneratorWithMockHttpClient(
            Flags flags, boolean dataVersionHeaderEnabled) {
        return new AdsScoreGeneratorImpl(
                mMockAdSelectionScriptEngine,
                mLightweightExecutorService,
                mBackgroundExecutorService,
                mSchedulingExecutor,
                mMockHttpsClient,
                mDevContext,
                mAdSelectionEntryDao,
                flags,
                mAdSelectionExecutionLogger,
                mDebugReporting,
                dataVersionHeaderEnabled);
    }

    private void verifyFailedAdScoringLoggingTimeout(
            List<AdBiddingOutcome> adBiddingOutcomeList, int resultCode) {
        int numOfCAsEnteringScoring =
                adBiddingOutcomeList.stream()
                        .filter(Objects::nonNull)
                        .map(a -> a.getCustomAudienceBiddingInfo())
                        .filter(Objects::nonNull)
                        .map(a -> a.getCustomAudienceSignals().hashCode())
                        .collect(Collectors.toSet())
                        .size();
        int numOfAdsEnteringScoring =
                adBiddingOutcomeList.stream()
                        .filter(Objects::nonNull)
                        .map(AdBiddingOutcome::getAdWithBid)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())
                        .size();
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(
                        mRunAdScoringProcessReportedStatsArgumentCaptor.capture());
        RunAdScoringProcessReportedStats runAdScoringProcessReportedStats =
                mRunAdScoringProcessReportedStatsArgumentCaptor.getValue();
        // Timeout exception could be thrown at any stage of the RunAdScoring process, so we only
        // verify partial logging of the start and the end stage of RunAdScoring.
        assertThat(runAdScoringProcessReportedStats.getNumOfCasEnteringScoring())
                .isEqualTo(numOfCAsEnteringScoring);
        assertThat(runAdScoringProcessReportedStats.getNumOfRemarketingAdsEnteringScoring())
                .isEqualTo(numOfAdsEnteringScoring);
        assertThat(runAdScoringProcessReportedStats.getNumOfContextualAdsEnteringScoring())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdScoringProcessReportedStats.getRunAdScoringResultCode())
                .isEqualTo(resultCode);
    }

    private void verifyFailedAdScoringLoggingJSONExceptionWithScoreAds(
            String sellerDecisionLogicJs,
            AdSelectionSignals trustedScoringSignals,
            List<AdBiddingOutcome> adBiddingOutcomeList,
            int resultCode) {
        int fetchedAdSelectionLogicScriptSizeInBytes =
                sellerDecisionLogicJs.getBytes(StandardCharsets.UTF_8).length;
        int fetchedTrustedScoringSignalsDataSizeInBytes =
                trustedScoringSignals.toString().getBytes(StandardCharsets.UTF_8).length;
        int numOfCAsEnteringScoring =
                adBiddingOutcomeList.stream()
                        .filter(Objects::nonNull)
                        .map(a -> a.getCustomAudienceBiddingInfo())
                        .filter(Objects::nonNull)
                        .map(a -> a.getCustomAudienceSignals().hashCode())
                        .collect(Collectors.toSet())
                        .size();
        int numOfAdsEnteringScoring =
                adBiddingOutcomeList.stream()
                        .filter(Objects::nonNull)
                        .map(AdBiddingOutcome::getAdWithBid)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())
                        .size();
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(
                        mRunAdScoringProcessReportedStatsArgumentCaptor.capture());
        RunAdScoringProcessReportedStats runAdScoringProcessReportedStats =
                mRunAdScoringProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicLatencyInMillis())
                .isEqualTo(GET_AD_SELECTION_LOGIC_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicScriptType())
                .isEqualTo(SCRIPT_JAVASCRIPT);
        assertThat(runAdScoringProcessReportedStats.getFetchedAdSelectionLogicScriptSizeInBytes())
                .isEqualTo(fetchedAdSelectionLogicScriptSizeInBytes);
        assertThat(runAdScoringProcessReportedStats.getGetTrustedScoringSignalsLatencyInMillis())
                .isEqualTo(GET_TRUSTED_SCORING_SIGNALS_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getGetTrustedScoringSignalsResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(
                        runAdScoringProcessReportedStats
                                .getFetchedTrustedScoringSignalsDataSizeInBytes())
                .isEqualTo(fetchedTrustedScoringSignalsDataSizeInBytes);
        assertThat(runAdScoringProcessReportedStats.getScoreAdsLatencyInMillis())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdScoringProcessReportedStats.getGetAdScoresLatencyInMillis())
                .isEqualTo((int) (RUN_AD_SCORING_END_TIMESTAMP - GET_AD_SCORES_START_TIMESTAMP));
        assertThat(runAdScoringProcessReportedStats.getGetAdScoresResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdScoringProcessReportedStats.getNumOfCasEnteringScoring())
                .isEqualTo(numOfCAsEnteringScoring);
        assertThat(runAdScoringProcessReportedStats.getNumOfRemarketingAdsEnteringScoring())
                .isEqualTo(numOfAdsEnteringScoring);
        assertThat(runAdScoringProcessReportedStats.getNumOfContextualAdsEnteringScoring())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdScoringProcessReportedStats.getRunAdScoringLatencyInMillis())
                .isEqualTo(RUN_AD_SCORING_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getRunAdScoringResultCode())
                .isEqualTo(resultCode);
    }

    private void verifyFailedAdScoringLoggingMissingTrustedScoringSignals(
            String sellerDecisionLogicJs,
            List<AdBiddingOutcome> adBiddingOutcomeList,
            int resultCode) {
        int fetchedAdSelectionLogicScriptSizeInBytes =
                sellerDecisionLogicJs.getBytes(StandardCharsets.UTF_8).length;
        int numOfCAsEnteringScoring =
                adBiddingOutcomeList.stream()
                        .filter(Objects::nonNull)
                        .map(a -> a.getCustomAudienceBiddingInfo())
                        .filter(Objects::nonNull)
                        .map(a -> a.getCustomAudienceSignals().hashCode())
                        .collect(Collectors.toSet())
                        .size();
        int numOfAdsEnteringScoring =
                adBiddingOutcomeList.stream()
                        .filter(Objects::nonNull)
                        .map(AdBiddingOutcome::getAdWithBid)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())
                        .size();
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(
                        mRunAdScoringProcessReportedStatsArgumentCaptor.capture());
        RunAdScoringProcessReportedStats runAdScoringProcessReportedStats =
                mRunAdScoringProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicLatencyInMillis())
                .isEqualTo(GET_AD_SELECTION_LOGIC_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicScriptType())
                .isEqualTo(SCRIPT_JAVASCRIPT);
        assertThat(runAdScoringProcessReportedStats.getFetchedAdSelectionLogicScriptSizeInBytes())
                .isEqualTo(fetchedAdSelectionLogicScriptSizeInBytes);
        assertThat(runAdScoringProcessReportedStats.getGetTrustedScoringSignalsLatencyInMillis())
                .isEqualTo(
                        (int)
                                (RUN_AD_SCORING_END_TIMESTAMP
                                        - GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP));
        assertThat(runAdScoringProcessReportedStats.getGetTrustedScoringSignalsResultCode())
                .isEqualTo(resultCode);
        assertThat(
                        runAdScoringProcessReportedStats
                                .getFetchedTrustedScoringSignalsDataSizeInBytes())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdScoringProcessReportedStats.getScoreAdsLatencyInMillis())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdScoringProcessReportedStats.getGetAdScoresLatencyInMillis())
                .isEqualTo((int) (RUN_AD_SCORING_END_TIMESTAMP - GET_AD_SCORES_START_TIMESTAMP));
        assertThat(runAdScoringProcessReportedStats.getGetAdScoresResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdScoringProcessReportedStats.getNumOfCasEnteringScoring())
                .isEqualTo(numOfCAsEnteringScoring);
        assertThat(runAdScoringProcessReportedStats.getNumOfRemarketingAdsEnteringScoring())
                .isEqualTo(numOfAdsEnteringScoring);
        assertThat(runAdScoringProcessReportedStats.getNumOfContextualAdsEnteringScoring())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdScoringProcessReportedStats.getRunAdScoringLatencyInMillis())
                .isEqualTo(RUN_AD_SCORING_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getRunAdScoringResultCode())
                .isEqualTo(resultCode);
    }

    private void verifySuccessAdScoringLogging(
            String sellerDecisionLogicJs,
            AdSelectionSignals trustedScoringSignals,
            List<AdBiddingOutcome> adBiddingOutcomeList,
            boolean dataVersionEnabled) {
        int fetchedAdSelectionLogicScriptSizeInBytes =
                sellerDecisionLogicJs.getBytes(StandardCharsets.UTF_8).length;
        int fetchedTrustedScoringSignalsDataSizeInBytes =
                trustedScoringSignals.toString().getBytes(StandardCharsets.UTF_8).length;
        int numOfCAsEnteringScoring =
                adBiddingOutcomeList.stream()
                        .filter(Objects::nonNull)
                        .map(a -> a.getCustomAudienceBiddingInfo())
                        .filter(Objects::nonNull)
                        .map(a -> a.getCustomAudienceSignals().hashCode())
                        .collect(Collectors.toSet())
                        .size();
        int numOfAdsEnteringScoring =
                adBiddingOutcomeList.stream()
                        .filter(Objects::nonNull)
                        .map(AdBiddingOutcome::getAdWithBid)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())
                        .size();
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(
                        mRunAdScoringProcessReportedStatsArgumentCaptor.capture());
        RunAdScoringProcessReportedStats runAdScoringProcessReportedStats =
                mRunAdScoringProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicLatencyInMillis())
                .isEqualTo(GET_AD_SELECTION_LOGIC_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicScriptType())
                .isEqualTo(SCRIPT_JAVASCRIPT);
        assertThat(runAdScoringProcessReportedStats.getFetchedAdSelectionLogicScriptSizeInBytes())
                .isEqualTo(fetchedAdSelectionLogicScriptSizeInBytes);
        assertThat(runAdScoringProcessReportedStats.getGetTrustedScoringSignalsLatencyInMillis())
                .isEqualTo(GET_TRUSTED_SCORING_SIGNALS_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getGetTrustedScoringSignalsResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(
                        runAdScoringProcessReportedStats
                                .getFetchedTrustedScoringSignalsDataSizeInBytes())
                .isEqualTo(fetchedTrustedScoringSignalsDataSizeInBytes);
        assertThat(runAdScoringProcessReportedStats.getScoreAdsLatencyInMillis())
                .isEqualTo(SCORE_ADS_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getGetAdScoresLatencyInMillis())
                .isEqualTo(GET_AD_SCORES_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getGetAdScoresResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdScoringProcessReportedStats.getNumOfCasEnteringScoring())
                .isEqualTo(numOfCAsEnteringScoring);
        assertThat(runAdScoringProcessReportedStats.getNumOfRemarketingAdsEnteringScoring())
                .isEqualTo(numOfAdsEnteringScoring);
        assertThat(runAdScoringProcessReportedStats.getNumOfContextualAdsEnteringScoring())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdScoringProcessReportedStats.getRunAdScoringLatencyInMillis())
                .isEqualTo(RUN_AD_SCORING_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getRunAdScoringResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(
                        runAdScoringProcessReportedStats
                                .getScoreAdSellerAdditionalSignalsContainedDataVersion())
                .isEqualTo(dataVersionEnabled);
    }

    private ListenableFuture<List<Double>> getScoresWithDelay(
            List<Double> scores, @NonNull Flags flags) {
        return mBlockingExecutorService.submit(
                () -> {
                    Thread.sleep(2 * flags.getAdSelectionScoringTimeoutMs());
                    return scores;
                });
    }

    private void validateCustomAudienceSignals(
            CustomAudienceSignals signals, AdTechIdentifier buyer) {
        assertEquals(CONTEXTUAL_CA_NAME, signals.getName());
        assertEquals(buyer.toString(), signals.getOwner());
        assertEquals(buyer, signals.getBuyer());
        assertEquals(AdSelectionSignals.EMPTY, signals.getUserBiddingSignals());
    }

    private Map<AdTechIdentifier, SignedContextualAds> createContextualAds() {
        Map<AdTechIdentifier, SignedContextualAds> buyerContextualAds = new HashMap<>();

        AdTechIdentifier buyer1 = BUYER_1;
        SignedContextualAds contextualAds1 =
                SignedContextualAdsFixture.aContextualAdsWithEmptySignatureBuilder(
                                buyer1, ImmutableList.of(100.0, 200.0, 300.0))
                        .build();

        AdTechIdentifier buyer2 = CommonFixture.VALID_BUYER_2;
        SignedContextualAds contextualAds2 =
                SignedContextualAdsFixture.aContextualAdsWithEmptySignatureBuilder(
                                buyer2, ImmutableList.of(400.0, 500.0))
                        .build();

        buyerContextualAds.put(buyer1, contextualAds1);
        buyerContextualAds.put(buyer2, contextualAds2);

        return buyerContextualAds;
    }

    private static class PathMatcher<T> implements ArgumentMatcher<T> {
        @NonNull private final String mPathToMatch;

        private PathMatcher(@NonNull String path) {
            Objects.requireNonNull(path);
            mPathToMatch = path;
        }

        public static <T> PathMatcher<T> matchesPath(@NonNull String path) {
            return new PathMatcher<>(path);
        }

        @Override
        public boolean matches(@Nullable T argument) {
            if (argument == null) {
                return false;
            }

            if (argument instanceof AdServicesHttpClientRequest) {
                return ((AdServicesHttpClientRequest) argument)
                        .getUri()
                        .getPath()
                        .equals(mPathToMatch);
            }

            if (argument instanceof Uri) {
                return ((Uri) argument).getPath().equals(mPathToMatch);
            }

            throw new IllegalArgumentException("Unsupported parameter to matcher");
        }
    }

    private static ListenableFuture<AdServicesHttpClientResponse> httpResponseWithBody(
            String body) {
        return Futures.immediateFuture(
                AdServicesHttpClientResponse.builder().setResponseBody(body).build());
    }

    private <T> T waitForFuture(
            AdsScoreGeneratorImplTest.ThrowingSupplier<ListenableFuture<T>> function)
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        ListenableFuture<T> futureResult = function.get();
        futureResult.addListener(resultLatch::countDown, mLightweightExecutorService);
        resultLatch.await();
        return futureResult.get();
    }

    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static class AdsScoreGeneratorImplTestFlags implements Flags {

        private final boolean mContextualAdsEnabled;

        AdsScoreGeneratorImplTestFlags(boolean contextualAdsEnabled) {
            mContextualAdsEnabled = contextualAdsEnabled;
        }

        @Override
        public boolean getFledgeAdSelectionContextualAdsEnabled() {
            return mContextualAdsEnabled;
        }

        @Override
        public long getAdSelectionScoringTimeoutMs() {
            return EXTENDED_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS;
        }

        @Override
        public long getAdSelectionOverallTimeoutMs() {
            return EXTENDED_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS;
        }

        @Override
        public boolean getFledgeDataVersionHeaderMetricsEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeJsScriptResultCodeMetricsEnabled() {
            return true;
        }
    }
}
