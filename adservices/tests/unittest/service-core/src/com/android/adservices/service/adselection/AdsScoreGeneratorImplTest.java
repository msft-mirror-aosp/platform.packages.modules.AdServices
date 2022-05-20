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

import android.adservices.adselection.AdBiddingOutcomeFixture;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.http.MockWebServerRule;
import android.net.Uri;

import com.android.adservices.MockWebServerRuleFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class AdsScoreGeneratorImplTest {

    private static final String BUYER_1 = AdSelectionConfigFixture.BUYER_1;
    private static final String BUYER_2 = AdSelectionConfigFixture.BUYER_2;
    private final String mFetchJavaScriptPath = "/fetchJavascript/";
    @Rule
    public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    AdSelectionConfig mAdSelectionConfig;
    @Mock
    //TODO(b/231349121): replace mocking script engine result with simple js scripts
    private AdSelectionScriptEngine mMockAdSelectionScriptEngine;
    private ListeningExecutorService mListeningExecutorService;
    private ExecutorService mExecutorService;
    private AdSelectionHttpClient mWebClient;
    private String mSellerDecisionLogicJs;

    private AdBiddingOutcome mAdBiddingOutcomeBuyer1;
    private AdBiddingOutcome mAdBiddingOutcomeBuyer2;
    private List<AdBiddingOutcome> mAdBiddingOutcomeList;

    private AdsScoreGenerator mAdsScoreGenerator;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mExecutorService = Executors.newFixedThreadPool(20);
        mListeningExecutorService = MoreExecutors.listeningDecorator(mExecutorService);
        mWebClient = new AdSelectionHttpClient(mExecutorService);

        mAdBiddingOutcomeBuyer1 = AdBiddingOutcomeFixture
                .anAdBiddingOutcomeBuilder(BUYER_1, 1.0).build();
        mAdBiddingOutcomeBuyer2 = AdBiddingOutcomeFixture
                .anAdBiddingOutcomeBuilder(BUYER_2, 2.0).build();
        mAdBiddingOutcomeList =
                Arrays.asList(mAdBiddingOutcomeBuyer1, mAdBiddingOutcomeBuyer2);

        mSellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_url, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_url': '"
                        + " /reporting/seller "
                        + "' } };\n"
                        + "}";

        mAdsScoreGenerator = new AdsScoreGeneratorImpl(
                mMockAdSelectionScriptEngine,
                mListeningExecutorService,
                mWebClient);
    }

    @Test
    public void testRunAdScoringSuccess() throws Exception {
        List<Double> scores = Arrays.asList(1.0, 2.0);
        MockWebServer server = mMockWebServerRule.startMockWebServer(
                List.of(new MockResponse().setBody(mSellerDecisionLogicJs))
        );

        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        mAdSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                .setDecisionLogicUrl(decisionLogicUri)
                .build();

        Mockito.when(mMockAdSelectionScriptEngine.scoreAds(mSellerDecisionLogicJs,
                        mAdBiddingOutcomeList.stream().map(a -> a.getAdWithBid())
                                .collect(Collectors.toList()),
                        mAdSelectionConfig,
                        mAdSelectionConfig.getSellerSignals(),
                        "{}",
                        "{}",
                        mAdBiddingOutcomeList.get(0)
                                .getCustomAudienceBiddingInfo().getCustomAudienceSignals()))
                .thenReturn(Futures.immediateFuture(scores));

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        List<AdScoringOutcome> scoringOutcome = waitForFuture(
                () -> {
                    return scoringResultFuture;
                });

        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPath, fetchRequest.getPath());

        assertEquals(1L,
                scoringOutcome.get(0).getAdWithScore().getScore().longValue());
        assertEquals(2L,
                scoringOutcome.get(1).getAdWithScore().getScore().longValue());
    }

    @Test
    public void testRunAdScoringJsonException() throws Exception {
        MockWebServer server = mMockWebServerRule.startMockWebServer(
                ImmutableList.of(new MockResponse().setBody(mSellerDecisionLogicJs))
        );

        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        mAdSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                .setDecisionLogicUrl(decisionLogicUri)
                .build();

        Mockito.when(mMockAdSelectionScriptEngine.scoreAds(mSellerDecisionLogicJs,
                        mAdBiddingOutcomeList.stream().map(a -> a.getAdWithBid())
                                .collect(Collectors.toList()),
                        mAdSelectionConfig,
                        mAdSelectionConfig.getSellerSignals(),
                        "{}",
                        "{}",
                        mAdBiddingOutcomeList.get(0)
                                .getCustomAudienceBiddingInfo().getCustomAudienceSignals()))
                .thenThrow(new JSONException("Badly formatted JSON"));

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        ExecutionException adServicesException =
                Assert.assertThrows(ExecutionException.class,
                        () -> {
                            waitForFuture(
                                    () -> {
                                        return scoringResultFuture;
                                    });
                        });

        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPath, fetchRequest.getPath());
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
