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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdWithBid;
import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.http.MockWebServerRule;
import android.content.Context;
import android.net.Uri;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBCustomAudience;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

public class AdBidGeneratorImplTest {
    public static final List<Double> BIDS =
            new ArrayList<Double>(Arrays.asList(-10.0, 0.0, 1.0, 5.4));
    public static final List<AdWithBid> AD_WITH_NON_POSITIVE_BIDS =
            new ArrayList<AdWithBid>(
                    Arrays.asList(
                            new AdWithBid(AdDataFixture.VALID_ADS.get(0), BIDS.get(1)),
                            new AdWithBid(AdDataFixture.VALID_ADS.get(1), BIDS.get(0)),
                            new AdWithBid(AdDataFixture.VALID_ADS.get(2), BIDS.get(1)),
                            new AdWithBid(AdDataFixture.VALID_ADS.get(3), BIDS.get(0))));
    public static final List<AdWithBid> AD_WITH_BIDS =
            new ArrayList<AdWithBid>(
                    Arrays.asList(
                            new AdWithBid(AdDataFixture.VALID_ADS.get(0), BIDS.get(1)),
                            new AdWithBid(AdDataFixture.VALID_ADS.get(1), BIDS.get(0)),
                            new AdWithBid(AdDataFixture.VALID_ADS.get(2), BIDS.get(3)),
                            new AdWithBid(AdDataFixture.VALID_ADS.get(3), BIDS.get(2))));
    private static final String EMPTY_BUYER_DECISION_LOGIC_JS = "{}";
    private static final String EMPTY_AD_SELECTION_SIGNALS = "{}";
    private static final String EMPTY_BUYER_SIGNALS = "{}";
    private static final String EMPTY_CONTEXTUAL_SIGNALS = "{}";
    private static final String EMPTY_TRUSTED_BIDDING_SIGNALS = "{}";
    private static final String EMPTY_USER_SIGNALS = "{}";
    private static final ArrayList<AdData> ADS = AdDataFixture.VALID_ADS;
    private static final DBCustomAudience CUSTOM_AUDIENCE_WITHOUT_ADS =
            CustomAudienceFixture.getDBCustomAudienceBuilder().build();
    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();
    private final String mFetchJavaScriptPath = "/fetchJavascript/";
    @Rule
    public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    @Mock
    AdSelectionScriptEngine mAdSelectionScriptEngine;
    Uri mDecisionLogicUri;
    private AdBidGeneratorImpl mAdBidGenerator;
    private AdSelectionHttpClient mAdSelectionHttpClient;
    private String mBuyerDecisionLogicJs;
    private MockWebServer mServer;
    private DBCustomAudience mCustomAudienceWithAds;
    private CustomAudienceSignals mCustomAudienceSignals;
    private CustomAudienceBiddingInfo mCustomAudienceBiddingInfo;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Executor executor = MoreExecutors.directExecutor();
        mAdSelectionHttpClient = new AdSelectionHttpClient(MoreExecutors
                .newDirectExecutorService());
        mAdBidGenerator = new AdBidGeneratorImpl(context,
                executor,
                mAdSelectionScriptEngine,
                mAdSelectionHttpClient);

        mBuyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_url': '"
                        + " buyerReportingUrl "
                        + "' } };\n"
                        + "}";

        mServer = mMockWebServerRule.startMockWebServer(
                List.of(new MockResponse().setBody(mBuyerDecisionLogicJs))
        );

        mDecisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        mCustomAudienceWithAds =
                CustomAudienceFixture.getDBCustomAudienceBuilder()
                        .setAds(
                                AdDataFixture.VALID_ADS.stream()
                                        .map(DBAdData::fromServiceObject)
                                        .collect(Collectors.toList()))
                        .setBiddingLogicUrl(mDecisionLogicUri)
                        .build();

        mCustomAudienceSignals =
                CustomAudienceSignals.buildFromCustomAudience(mCustomAudienceWithAds);

        mCustomAudienceBiddingInfo =
                CustomAudienceBiddingInfo.create(
                        mDecisionLogicUri,
                        mBuyerDecisionLogicJs,
                        mCustomAudienceSignals);
    }

    @Test
    /**
     * TODO(b/231349121): replace mocking script engine result with simple js scripts to test the
     * outcome.
     */
    public void testRunAdBiddingPerCASuccess()
            throws JSONException, ExecutionException, InterruptedException {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        Mockito.when(
                        mAdSelectionScriptEngine.generateBids(
                                mBuyerDecisionLogicJs,
                                ADS,
                                EMPTY_AD_SELECTION_SIGNALS,
                                EMPTY_BUYER_SIGNALS,
                                EMPTY_TRUSTED_BIDDING_SIGNALS,
                                EMPTY_CONTEXTUAL_SIGNALS,
                                EMPTY_USER_SIGNALS,
                                mCustomAudienceSignals))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(AD_WITH_BIDS)));
        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        EMPTY_CONTEXTUAL_SIGNALS);
        AdBiddingOutcome expectedAdBiddingOutcome =
                AdBiddingOutcome.builder()
                        .setAdWithBid(AD_WITH_BIDS.get(2))
                        .setCustomAudienceBiddingInfo(mCustomAudienceBiddingInfo)
                        .build();
        // Then we can test the result by assertion,
        assertEquals(expectedAdBiddingOutcome, result.get());
        Mockito.verify(mAdSelectionScriptEngine)
                .generateBids(
                        mBuyerDecisionLogicJs,
                        ADS,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        EMPTY_TRUSTED_BIDDING_SIGNALS,
                        EMPTY_CONTEXTUAL_SIGNALS,
                        EMPTY_USER_SIGNALS,
                        mCustomAudienceSignals);
        RecordedRequest fetchRequest = mServer.takeRequest();
        assertEquals(mFetchJavaScriptPath, fetchRequest.getPath());
    }

    @Test
    /**
     * TODO(b/231349121): replace mocking script engine result with simple js scripts to test the
     * outcome.
     */
    public void testRunAdBiddingPerCANonPositiveBidResult()
            throws JSONException, ExecutionException, InterruptedException {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        Mockito.when(
                        mAdSelectionScriptEngine.generateBids(
                                mBuyerDecisionLogicJs,
                                ADS,
                                EMPTY_AD_SELECTION_SIGNALS,
                                EMPTY_BUYER_SIGNALS,
                                EMPTY_TRUSTED_BIDDING_SIGNALS,
                                EMPTY_CONTEXTUAL_SIGNALS,
                                EMPTY_USER_SIGNALS,
                                mCustomAudienceSignals))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(AD_WITH_NON_POSITIVE_BIDS)));
        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        EMPTY_CONTEXTUAL_SIGNALS);
        // Then we can test the result by assertion,
        assertNull(result.get());
        Mockito.verify(mAdSelectionScriptEngine)
                .generateBids(
                        mBuyerDecisionLogicJs,
                        ADS,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        EMPTY_TRUSTED_BIDDING_SIGNALS,
                        EMPTY_CONTEXTUAL_SIGNALS,
                        EMPTY_USER_SIGNALS,
                        mCustomAudienceSignals);
        RecordedRequest fetchRequest = mServer.takeRequest();
        assertEquals(mFetchJavaScriptPath, fetchRequest.getPath());
    }

    @Test
    public void testRunBiddingThrowsException() throws JSONException {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        JSONException jsonException = new JSONException("");
        Mockito.when(
                        mAdSelectionScriptEngine.generateBids(
                                mBuyerDecisionLogicJs,
                                ADS,
                                EMPTY_AD_SELECTION_SIGNALS,
                                EMPTY_BUYER_SIGNALS,
                                EMPTY_TRUSTED_BIDDING_SIGNALS,
                                EMPTY_CONTEXTUAL_SIGNALS,
                                EMPTY_USER_SIGNALS,
                                mCustomAudienceSignals))
                .thenThrow(jsonException);
        // When the call to runBidding, and the computation of future is complete.
        FluentFuture<Pair<AdWithBid, String>> result =
                mAdBidGenerator.runBidding(
                        mBuyerDecisionLogicJs,
                        ImmutableList.copyOf(ADS),
                        EMPTY_BUYER_SIGNALS,
                        EMPTY_TRUSTED_BIDDING_SIGNALS,
                        EMPTY_CONTEXTUAL_SIGNALS,
                        mCustomAudienceSignals,
                        EMPTY_USER_SIGNALS,
                        EMPTY_AD_SELECTION_SIGNALS);

        ExecutionException outException =
                assertThrows(ExecutionException.class, () -> result.get());
        assertEquals(outException.getCause(), jsonException);
    }

    @Test
    public void testRunAdBiddingPerCAWithException()
            throws ExecutionException, JSONException, InterruptedException {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        Mockito.when(
                        mAdSelectionScriptEngine.generateBids(
                                mBuyerDecisionLogicJs,
                                ADS,
                                EMPTY_AD_SELECTION_SIGNALS,
                                EMPTY_BUYER_SIGNALS,
                                EMPTY_TRUSTED_BIDDING_SIGNALS,
                                EMPTY_CONTEXTUAL_SIGNALS,
                                EMPTY_USER_SIGNALS,
                                mCustomAudienceSignals))
                .thenThrow(new JSONException(""));
        // When the call to runBidding, and the computation of future is complete.
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        EMPTY_CONTEXTUAL_SIGNALS);
        assertNull(result.get());
        RecordedRequest fetchRequest = mServer.takeRequest();
        assertEquals(mFetchJavaScriptPath, fetchRequest.getPath());
    }

    @Test
    public void testRunAdBiddingPerCANoAds() throws ExecutionException, InterruptedException {
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        CUSTOM_AUDIENCE_WITHOUT_ADS,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        EMPTY_CONTEXTUAL_SIGNALS);
        // The result is an early return with a FluentFuture of Null, after checking the Ads list is
        // empty.
        assertNull(result.get());
    }
}
