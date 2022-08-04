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

import static com.android.adservices.service.adselection.AdsScoreGeneratorImpl.MISSING_TRUSTED_SCORING_SIGNALS;
import static com.android.adservices.service.adselection.AdsScoreGeneratorImpl.QUERY_PARAM_RENDER_URLS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.adselection.AdBiddingOutcomeFixture;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.http.MockWebServerRule;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelectionOverride;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AdServicesHttpsClient;
import com.android.adservices.service.devapi.AdSelectionDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.json.JSONException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class AdsScoreGeneratorImplTest {

    private static final AdTechIdentifier BUYER_1 = AdSelectionConfigFixture.BUYER_1;
    private static final AdTechIdentifier BUYER_2 = AdSelectionConfigFixture.BUYER_2;
    private final String mFetchJavaScriptPath = "/fetchJavascript/";
    private final String mTrustedScoringSignalsPath = "/getTrustedScoringSignals/";
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    AdSelectionConfig mAdSelectionConfig;

    @Mock private AdSelectionScriptEngine mMockAdSelectionScriptEngine;

    private ListeningExecutorService mListeningExecutorService;
    private ExecutorService mExecutorService;
    private AdServicesHttpsClient mWebClient;
    private String mSellerDecisionLogicJs;

    private AdBiddingOutcome mAdBiddingOutcomeBuyer1;
    private AdBiddingOutcome mAdBiddingOutcomeBuyer2;
    private List<AdBiddingOutcome> mAdBiddingOutcomeList;

    private AdsScoreGenerator mAdsScoreGenerator;
    private DevContext mDevContext;
    private Flags mFlags;

    private AdSelectionEntryDao mAdSelectionEntryDao;

    private AdSelectionSignals mTrustedScoringSignals;
    private String mTrustedScoringParams;
    private List<String> mTrustedScoringSignalsKeys;

    private Dispatcher mDefaultDispatcher;
    private MockWebServerRule.RequestMatcher<String> mRequestMatcherExactMatch;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mDevContext = DevContext.createForDevOptionsDisabled();

        mExecutorService = Executors.newFixedThreadPool(20);
        mListeningExecutorService = MoreExecutors.listeningDecorator(mExecutorService);
        mWebClient = new AdServicesHttpsClient(mExecutorService);

        mAdBiddingOutcomeBuyer1 =
                AdBiddingOutcomeFixture.anAdBiddingOutcomeBuilder(BUYER_1, 1.0).build();
        mAdBiddingOutcomeBuyer2 =
                AdBiddingOutcomeFixture.anAdBiddingOutcomeBuilder(BUYER_2, 2.0).build();
        mAdBiddingOutcomeList = ImmutableList.of(mAdBiddingOutcomeBuyer1, mAdBiddingOutcomeBuyer2);

        mSellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_url, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_url': '"
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
                        QUERY_PARAM_RENDER_URLS,
                        Uri.encode(String.join(",", mTrustedScoringSignalsKeys)));

        mTrustedScoringSignals =
                AdSelectionSignals.fromString(
                        "{\n"
                                + mAdBiddingOutcomeBuyer1
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri()
                                        .getEncodedPath()
                                + ": signalsForUrl1,\n"
                                + mAdBiddingOutcomeBuyer2
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri()
                                        .getEncodedPath()
                                + ": signalsForUrl2,\n"
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
                            return new MockResponse()
                                    .setBody(mTrustedScoringSignals.getStringForm());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mRequestMatcherExactMatch =
                (actualRequest, expectedRequest) -> actualRequest.equals(expectedRequest);

        mFlags = FlagsFactory.getFlagsForTest();

        mAdsScoreGenerator =
                new AdsScoreGeneratorImpl(
                        mMockAdSelectionScriptEngine,
                        mListeningExecutorService,
                        mWebClient,
                        mDevContext,
                        mAdSelectionEntryDao,
                        mFlags);
    }

    @Test
    public void testRunAdScoringSuccess() throws Exception {
        List<Double> scores = ImmutableList.of(1.0, 2.0);
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
                                        .collect(Collectors.toList())))
                .thenReturn(Futures.immediateFuture(scores));

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        List<AdScoringOutcome> scoringOutcome =
                waitForFuture(
                        () -> {
                            return scoringResultFuture;
                        });

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
                                .collect(Collectors.toList()));

        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                ImmutableList.of(
                        mFetchJavaScriptPath, mTrustedScoringSignalsPath + mTrustedScoringParams),
                mRequestMatcherExactMatch);

        assertEquals(1L, scoringOutcome.get(0).getAdWithScore().getScore().longValue());
        assertEquals(2L, scoringOutcome.get(1).getAdWithScore().getScore().longValue());
    }

    @Test
    public void testMissingTrustedSignalsException() throws Exception {
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

        ExecutionException outException =
                assertThrows(ExecutionException.class, scoringResultFuture::get);
        assertEquals(outException.getCause().getMessage(), missingSignalsException.getMessage());
        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                ImmutableList.of(
                        mFetchJavaScriptPath, mTrustedScoringSignalsPath + mTrustedScoringParams),
                mRequestMatcherExactMatch);
    }

    @Test
    public void testRunAdScoringUseDevOverrideForJS() throws Exception {
        List<Double> scores = ImmutableList.of(1.0, 2.0);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);
        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        // Different seller decision logic JS to simulate different different override from server
        String differentSellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_url, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":2}', 'reporting_url': '"
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
                        .setTrustedScoringSignals(mTrustedScoringSignals.getStringForm())
                        .build();
        mAdSelectionEntryDao.persistAdSelectionOverride(adSelectionOverride);

        // Resetting Generator to use new dev context
        mDevContext =
                DevContext.builder()
                        .setDevOptionsEnabled(true)
                        .setCallingAppPackageName(myAppPackageName)
                        .build();

        mAdsScoreGenerator =
                new AdsScoreGeneratorImpl(
                        mMockAdSelectionScriptEngine,
                        mListeningExecutorService,
                        mWebClient,
                        mDevContext,
                        mAdSelectionEntryDao,
                        mFlags);

        Mockito.when(
                        mMockAdSelectionScriptEngine.scoreAds(
                                differentSellerDecisionLogicJs,
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
                                        .collect(Collectors.toList())))
                .thenReturn(Futures.immediateFuture(scores));

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        List<AdScoringOutcome> scoringOutcome = waitForFuture(() -> scoringResultFuture);

        // The server will not be invoked as the web calls should be overridden
        mMockWebServerRule.verifyMockServerRequests(
                server, 0, Collections.emptyList(), mRequestMatcherExactMatch);
        Assert.assertEquals(1L, scoringOutcome.get(0).getAdWithScore().getScore().longValue());
        Assert.assertEquals(2L, scoringOutcome.get(1).getAdWithScore().getScore().longValue());
    }

    @Test
    public void testRunAdScoringJsonException() throws Exception {
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
                                        .collect(Collectors.toList())))
                .thenThrow(new JSONException("Badly formatted JSON"));

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        ExecutionException adServicesException =
                Assert.assertThrows(
                        ExecutionException.class,
                        () -> {
                            waitForFuture(
                                    () -> {
                                        return scoringResultFuture;
                                    });
                        });

        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                ImmutableList.of(
                        mFetchJavaScriptPath, mTrustedScoringSignalsPath + mTrustedScoringParams),
                mRequestMatcherExactMatch);
    }

    @Test
    public void testRunAdScoringTimesOut() throws Exception {
        Flags flagsWithSmallerLimits =
                new Flags() {
                    @Override
                    public long getAdSelectionScoringTimeoutMs() {
                        return 100;
                    }
                };
        mAdsScoreGenerator =
                new AdsScoreGeneratorImpl(
                        mMockAdSelectionScriptEngine,
                        mListeningExecutorService,
                        mWebClient,
                        mDevContext,
                        mAdSelectionEntryDao,
                        flagsWithSmallerLimits);

        List<Double> scores = Arrays.asList(1.0, 2.0);
        mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

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
                                        .collect(Collectors.toList())))
                .thenReturn(getScoresWithDelay(scores));

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        ExecutionException thrown =
                assertThrows(ExecutionException.class, scoringResultFuture::get);
        assertTrue(thrown.getMessage().contains("TimeoutFuture$TimeoutFutureException"));
    }

    private ListenableFuture<List<Double>> getScoresWithDelay(List<Double> scores) {
        return mListeningExecutorService.submit(
                () -> {
                    Thread.sleep(2 * mFlags.getAdSelectionScoringTimeoutMs());
                    return scores;
                });
    }

    private <T> T waitForFuture(
            AdsScoreGeneratorImplTest.ThrowingSupplier<ListenableFuture<T>> function)
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        ListenableFuture<T> futureResult = function.get();
        futureResult.addListener(resultLatch::countDown, mExecutorService);
        resultLatch.await();
        return futureResult.get();
    }

    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
