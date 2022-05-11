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

import android.adservices.adselection.AdBiddingOutcomeFixture;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.exceptions.AdServicesException;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.json.JSONException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class AdsScoreGeneratorImplTest {

    // TODO(b/230436736): Invoke the server to get decision Logic JS
    public static final String SELLER_SCORING_LOGIC_JS = "";
    private static final String BUYER_1 = AdSelectionConfigFixture.BUYER_1;
    private static final String BUYER_2 = AdSelectionConfigFixture.BUYER_2;
    @Mock
    //TODO(b/231349121): replace mocking script engine result with simple js scripts
    AdSelectionScriptEngine mMockAdSelectionScriptEngine;

    ListeningExecutorService mListeningExecutorService;
    ExecutorService mExecutorService;

    AdSelectionConfig mAdSelectionConfig;

    private AdBiddingOutcome mAdBiddingOutcomeBuyer1;
    private AdBiddingOutcome mAdBiddingOutcomeBuyer2;
    private List<AdBiddingOutcome> mAdBiddingOutcomeList;

    private AdsScoreGenerator mAdsScoreGenerator;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mExecutorService = Executors.newFixedThreadPool(20);
        mListeningExecutorService = MoreExecutors.listeningDecorator(mExecutorService);

        mAdSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfigBuilder().build();

        mAdBiddingOutcomeBuyer1 = AdBiddingOutcomeFixture
                .anAdBiddingOutcomeBuilder(BUYER_1, 1.0).build();
        mAdBiddingOutcomeBuyer2 = AdBiddingOutcomeFixture
                .anAdBiddingOutcomeBuilder(BUYER_2, 2.0).build();
        mAdBiddingOutcomeList =
                Arrays.asList(mAdBiddingOutcomeBuyer1, mAdBiddingOutcomeBuyer2);

        mAdsScoreGenerator = new AdsScoreGeneratorImpl(mMockAdSelectionScriptEngine,
                mListeningExecutorService);
    }

    @Test
    public void testRunAdScoringSuccess() throws Exception {
        List<Double> scores = Arrays.asList(1.0, 2.0);

        Mockito.when(mMockAdSelectionScriptEngine.scoreAds("",
                        mAdBiddingOutcomeList.stream().map(a -> a.getAdWithBid())
                                .collect(Collectors.toList()),
                        mAdSelectionConfig,
                        mAdSelectionConfig.getSellerSignals(),
                        "{}",
                        mAdSelectionConfig.getSeller(),
                        mAdBiddingOutcomeList.get(0)
                                .getCustomAudienceBiddingInfo().getCustomAudienceSignals()))
                .thenReturn(Futures.immediateFuture(scores));

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        List<AdScoringOutcome> scoringOutcome = waitForFuture(
                () -> {
                    return scoringResultFuture;
                });

        Assert.assertEquals(1L,
                scoringOutcome.get(0).getAdWithScore().getScore().longValue());
        Assert.assertEquals(2L,
                scoringOutcome.get(1).getAdWithScore().getScore().longValue());
    }

    @Test
    public void testRunAdScoringJsonException() throws Exception {

        Mockito.when(mMockAdSelectionScriptEngine.scoreAds("",
                        mAdBiddingOutcomeList.stream().map(a -> a.getAdWithBid())
                                .collect(Collectors.toList()),
                        mAdSelectionConfig,
                        mAdSelectionConfig.getSellerSignals(),
                        "{}",
                        mAdSelectionConfig.getSeller(),
                        mAdBiddingOutcomeList.get(0)
                                .getCustomAudienceBiddingInfo().getCustomAudienceSignals()))
                .thenThrow(new JSONException("Badly formatted JSON"));

        AdServicesException adServicesException =
                Assert.assertThrows(AdServicesException.class,
                        () -> {
                            mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList,
                                    mAdSelectionConfig);
                        });

        Assert.assertEquals("Invalid results obtained from Ad Scoring",
                adServicesException.getMessage());
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
