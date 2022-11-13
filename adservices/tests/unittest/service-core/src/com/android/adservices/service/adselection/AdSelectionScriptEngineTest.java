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


import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdWithBid;
import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.service.adselection.AdSelectionScriptEngine.AuctionScriptResult;
import com.android.adservices.service.exception.JSExecutionException;
import com.android.adservices.service.js.IsolateSettings;
import com.android.adservices.service.js.JSScriptArgument;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

@SmallTest
public class AdSelectionScriptEngineTest {
    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String TAG = "AdSelectionScriptEngineTest";
    private static final Instant NOW = Instant.now();
    private static final CustomAudienceSignals CUSTOM_AUDIENCE_SIGNALS_1 =
            new CustomAudienceSignals(
                    CustomAudienceFixture.VALID_OWNER,
                    CommonFixture.VALID_BUYER_1,
                    "name",
                    NOW,
                    NOW.plus(Duration.ofDays(1)),
                    AdSelectionSignals.EMPTY);
    private static final CustomAudienceSignals CUSTOM_AUDIENCE_SIGNALS_2 =
            new CustomAudienceSignals(
                    CustomAudienceFixture.VALID_OWNER,
                    CommonFixture.VALID_BUYER_1,
                    "name",
                    NOW,
                    NOW.plus(Duration.ofDays(1)),
                    AdSelectionSignals.EMPTY);
    private static final List<CustomAudienceSignals> CUSTOM_AUDIENCE_SIGNALS_LIST =
            ImmutableList.of(CUSTOM_AUDIENCE_SIGNALS_1, CUSTOM_AUDIENCE_SIGNALS_2);
    private static final long AD_SELECTION_ID_1 = 12345L;
    private static final double AD_BID_1 = 10.0;
    private static final long AD_SELECTION_ID_2 = 123456L;
    private static final double AD_BID_2 = 11.0;
    private static final long AD_SELECTION_ID_3 = 1234567L;
    private static final double AD_BID_3 = 12.0;
    private static final AdSelectionIdWithBid AD_SELECTION_ID_WITH_BID_1 =
            AdSelectionIdWithBid.builder()
                    .setAdSelectionId(AD_SELECTION_ID_1)
                    .setBid(AD_BID_1)
                    .build();
    private static final AdSelectionIdWithBid AD_SELECTION_ID_WITH_BID_2 =
            AdSelectionIdWithBid.builder()
                    .setAdSelectionId(AD_SELECTION_ID_1)
                    .setBid(AD_BID_1)
                    .build();
    private static final AdSelectionIdWithBid AD_SELECTION_ID_WITH_BID_3 =
            AdSelectionIdWithBid.builder()
                    .setAdSelectionId(AD_SELECTION_ID_1)
                    .setBid(AD_BID_1)
                    .build();
    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(1);
    IsolateSettings mIsolateSettings = IsolateSettings.forMaxHeapSizeEnforcementDisabled();
    private final AdSelectionScriptEngine mAdSelectionScriptEngine =
            new AdSelectionScriptEngine(
                    sContext,
                    () -> mIsolateSettings.getEnforceMaxHeapSizeFeature(),
                    () -> mIsolateSettings.getMaxHeapSizeBytes());

    @Test
    public void testAuctionScriptIsInvalidIfRequiredFunctionDoesNotExist() throws Exception {
        assertFalse(
                callJsValidation(
                        "function helloAdvert(ad) { return {'status': 0, 'greeting': 'hello ' +"
                                + " ad.render_uri }; }",
                        ImmutableList.of("helloAdvertWrongName")));
    }

    @Test
    public void testAuctionScriptIsInvalidIfAnyRequiredFunctionDoesNotExist() throws Exception {
        assertFalse(
                callJsValidation(
                        "function helloAdvert(ad) { return {'status': 0, 'greeting': 'hello ' +"
                                + " ad.render_uri }; }",
                        ImmutableList.of("helloAdvert", "helloAdvertWrongName")));
    }

    @Test
    public void testCanCallScript() throws Exception {
        AdData advert = new AdData(Uri.parse("http://www.domain.com/adverts/123"), "{}");
        final AuctionScriptResult result =
                callAuctionEngine(
                        "function helloAdvert(ad) { return {'status': 0, 'greeting': 'hello ' +"
                                + " ad.render_uri }; }",
                        "helloAdvert(ad)",
                        advert,
                        ImmutableList.of());
        assertThat(result.status).isEqualTo(0);
        assertThat(((JSONObject) result.results.get(0)).getString("greeting"))
                .isEqualTo("hello http://www.domain.com/adverts/123");
    }

    @Test
    public void testThrowsJSExecutionExceptionIfTheFunctionIsNotFound() throws Exception {
        AdData advert = new AdData(Uri.parse("http://www.domain.com/adverts/123"), "{}");
        Exception exception =
                Assert.assertThrows(
                        ExecutionException.class,
                        () ->
                                callAuctionEngine(
                                        "function helloAdvert(ad) { return {'status': 0,"
                                                + " 'greeting': 'hello ' + ad.render_uri }; }",
                                        "helloAdvertWrongName",
                                        advert,
                                        ImmutableList.of()));

        assertThat(exception.getCause()).isInstanceOf(JSExecutionException.class);
    }

    @Test
    public void testFailsIfScriptIsNotReturningJson() throws Exception {
        AdData advert = new AdData(Uri.parse("http://www.domain.com/adverts/123"), "{}");
        final AuctionScriptResult result =
                callAuctionEngine(
                        "function helloAdvert(ad) { return 'hello ' + ad.render_uri; }",
                        "helloAdvert(ad)",
                        advert,
                        ImmutableList.of());
        assertThat(result.status).isEqualTo(-1);
    }

    @Test
    public void testCallsFailAtFirstNonzeroStatus() throws Exception {
        AdData processedSuccessfully =
                new AdData(Uri.parse("http://www.domain.com/adverts/123"), "{ \"result\": 0}");
        AdData failToProcess =
                new AdData(Uri.parse("http://www.domain.com/adverts/456"), "{ \"result\": 1}");
        AdData willNotBeProcessed =
                new AdData(Uri.parse("http://www.domain.com/adverts/789"), "{ \"result\": 0}");
        final AuctionScriptResult result =
                callAuctionEngine(
                        "function injectFailure(ad) { return {'status': ad.metadata.result,"
                                + " 'value': ad.render_uri }; }",
                        "injectFailure(ad)",
                        ImmutableList.of(processedSuccessfully, failToProcess, willNotBeProcessed),
                        ImmutableList.of());
        assertThat(result.status).isEqualTo(1);
        // Only processed result is returned
        assertThat(result.results.length()).isEqualTo(1);
        assertThat(((JSONObject) result.results.get(0)).getString("value"))
                .isEqualTo("http://www.domain.com/adverts/123");
    }

    @Test
    public void testGenerateBidSuccessfulCase() throws Exception {
        final AdData ad1 =
                new AdData(Uri.parse("http://www.domain.com/adverts/123"), "{\"result\":1.1}");
        final AdData ad2 =
                new AdData(Uri.parse("http://www.domain.com/adverts/456"), "{\"result\":2.1}");
        List<AdData> ads = ImmutableList.of(ad1, ad2);
        final List<AdWithBid> result =
                generateBids(
                        "function generateBid(ad, auction_signals, per_buyer_signals,"
                                + " trusted_bidding_signals, contextual_signals,"
                                + " custom_audience_signals) { \n"
                                + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                                + "}",
                        ads,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        CUSTOM_AUDIENCE_SIGNALS_1);
        assertThat(result).containsExactly(new AdWithBid(ad1, 1.1), new AdWithBid(ad2, 2.1));
    }

    @Test
    public void testGenerateBidReturnEmptyListInCaseNonSuccessStatus() throws Exception {
        final AdData ad1 =
                new AdData(Uri.parse("http://www.domain.com/adverts/123"), "{\"result\":1.1}");
        final AdData ad2 =
                new AdData(Uri.parse("http://www.domain.com/adverts/456"), "{\"result\":2.1}");
        List<AdData> ads = ImmutableList.of(ad1, ad2);
        final List<AdWithBid> result =
                generateBids(
                        "function generateBid(ad, auction_signals, per_buyer_signals,"
                                + " trusted_bidding_signals, contextual_signals,"
                                + " custom_audience_signals) { \n"
                                + "  return {'status': 1, 'ad': ad, 'bid': ad.metadata.result };\n"
                                + "}",
                        ads,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        CUSTOM_AUDIENCE_SIGNALS_1);
        assertThat(result).isEmpty();
    }

    @Test
    public void testGenerateBidReturnEmptyListInCaseOfMalformedResponseForAnyAd() throws Exception {
        final AdData ad1 =
                new AdData(Uri.parse("http://www.domain.com/adverts/123"), "{\"result\":1.1}");
        final AdData ad2 =
                new AdData(Uri.parse("http://www.domain.com/adverts/456"), "{\"result\":2.1}");
        List<AdData> ads = ImmutableList.of(ad1, ad2);
        final List<AdWithBid> result =
                generateBids(
                        // The response for the second add doesn't include the bid so we cannot
                        // parse and AdWithBid
                        "function generateBid(ad, auction_signals, per_buyer_signals,"
                                + " trusted_bidding_signals, contextual_signals,"
                                + " custom_audience_signals) { \n"
                                + " if (ad.metadata.result > 2) return {'status': 0, 'ad': ad };\n"
                                + " else return {'status': 0, 'ad': ad, 'bid': 10 };\n"
                                + "}",
                        ads,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        CUSTOM_AUDIENCE_SIGNALS_1);
        assertThat(result).isEmpty();
    }

    @Test
    public void testScoreAdsSuccessfulCase() throws Exception {
        final AdData ad1 =
                new AdData(Uri.parse("http://www.domain.com/adverts/123"), "{\"result\":1.1}");
        final AdData ad2 =
                new AdData(Uri.parse("http://www.domain.com/adverts/456"), "{\"result\":2.1}");
        List<AdWithBid> adWithBids =
                ImmutableList.of(new AdWithBid(ad1, 100), new AdWithBid(ad2, 200));
        final List<Double> result =
                scoreAds(
                        "function scoreAd(ad, bid, auction_config, seller_signals, "
                                + "trusted_scoring_signals, contextual_signal, user_signal, "
                                + "custom_audience_signal) { \n"
                                + "  return {'status': 0, 'score': bid };\n"
                                + "}",
                        adWithBids,
                        anAdSelectionConfig(),
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        CUSTOM_AUDIENCE_SIGNALS_LIST);
        assertThat(result).containsExactly(100.0, 200.0);
    }

    @Test
    public void testScoreAdsReturnEmptyListInCaseOfNonSuccessStatus() throws Exception {
        final AdData ad1 =
                new AdData(Uri.parse("http://www.domain.com/adverts/123"), "{\"result\":1.1}");
        final AdData ad2 =
                new AdData(Uri.parse("http://www.domain.com/adverts/456"), "{\"result\":2.1}");
        List<AdWithBid> adWithBids =
                ImmutableList.of(new AdWithBid(ad1, 100), new AdWithBid(ad2, 200));
        final List<Double> result =
                scoreAds(
                        "function scoreAd(ad, bid, auction_config, seller_signals, "
                                + "trusted_scoring_signals, contextual_signal, user_signal, "
                                + "custom_audience_signal) { \n"
                                + "  return {'status': 1, 'score': bid };\n"
                                + "}",
                        adWithBids,
                        anAdSelectionConfig(),
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        CUSTOM_AUDIENCE_SIGNALS_LIST);
        assertThat(result).isEmpty();
    }

    @Test
    public void testSelectOutcomeWaterfallMediationLogicReturnAdJsSuccess() throws Exception {
        final Long result =
                selectOutcome(
                        "function selectOutcome(outcomes, selection_signals) {\n"
                                + "    if (outcomes.length != 1 || selection_signals.bid_floor =="
                                + " undefined) return null;\n"
                                + "\n"
                                + "    const outcome_1p = outcomes[0];\n"
                                + "    return {'status': 0, 'result': (outcome_1p.bid >"
                                + " selection_signals.bid_floor) ? outcome_1p : null};\n"
                                + "}",
                        Collections.singletonList(AD_SELECTION_ID_WITH_BID_1),
                        AdSelectionSignals.fromString("{bid_floor: 9}"));
        assertThat(result).isEqualTo(AD_SELECTION_ID_WITH_BID_1.getAdSelectionId());
    }

    @Test
    public void testSelectOutcomeWaterfallMediationLogicReturnNullJsSuccess() throws Exception {
        final Long result =
                selectOutcome(
                        "function selectOutcome(outcomes, selection_signals) {\n"
                                + "    if (outcomes.length != 1 || selection_signals.bid_floor =="
                                + " undefined) return null;\n"
                                + "\n"
                                + "    const outcome_1p = outcomes[0];\n"
                                + "    return {'status': 0, 'result': (outcome_1p.bid >"
                                + " selection_signals.bid_floor) ? outcome_1p : null};\n"
                                + "}",
                        Collections.singletonList(AD_SELECTION_ID_WITH_BID_1),
                        AdSelectionSignals.fromString("{bid_floor: 11}"));
        assertThat(result).isNull();
    }

    @Test
    public void testSelectOutcomeOpenBiddingMediationLogicJsSuccess() throws Exception {
        final Long result =
                selectOutcome(
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
                                + "}",
                        List.of(
                                AD_SELECTION_ID_WITH_BID_1,
                                AD_SELECTION_ID_WITH_BID_2,
                                AD_SELECTION_ID_WITH_BID_3),
                        AdSelectionSignals.EMPTY);
        assertThat(result).isEqualTo(AD_SELECTION_ID_WITH_BID_3.getAdSelectionId());
    }

    @Test
    public void testSelectOutcomeReturningMultipleIdsFailure() {
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                selectOutcome(
                                        "function selectOutcome(outcomes, selection_signals) {\n"
                                                + "    return {'status': 0, 'result': outcomes};\n"
                                                + "}",
                                        List.of(
                                                AD_SELECTION_ID_WITH_BID_1,
                                                AD_SELECTION_ID_WITH_BID_2,
                                                AD_SELECTION_ID_WITH_BID_3),
                                        AdSelectionSignals.EMPTY));
        Assert.assertTrue(exception.getCause() instanceof IllegalStateException);
    }

    @Test
    public void testCanRunScriptWithStringInterpolationTokenInIt() throws Exception {
        AdData advert = new AdData(Uri.parse("http://www.domain.com/adverts/123"), "{}");
        final AuctionScriptResult result =
                callAuctionEngine(
                        "function helloAdvert(ad) { return {'status': 0, 'greeting': '%shello ' +"
                                + " ad.render_uri }; }",
                        "helloAdvert(ad)", advert, ImmutableList.of());
        assertThat(result.status).isEqualTo(0);
        assertThat(((JSONObject) result.results.get(0)).getString("greeting"))
                .isEqualTo("%shello http://www.domain.com/adverts/123");
    }

    private AdSelectionConfig anAdSelectionConfig() {
        return new AdSelectionConfig.Builder()
                .setSeller(AdTechIdentifier.fromString("www.mydomain.com"))
                .setPerBuyerSignals(ImmutableMap.of())
                .setDecisionLogicUri(Uri.parse("http://www.mydomain.com/updateAds"))
                .setSellerSignals(AdSelectionSignals.EMPTY)
                .setCustomAudienceBuyers(
                        ImmutableList.of(AdTechIdentifier.fromString("www.buyer.com")))
                .setAdSelectionSignals(AdSelectionSignals.EMPTY)
                .setTrustedScoringSignalsUri(Uri.parse("https://kvtrusted.com/scoring_signals"))
                .build();
    }

    private AuctionScriptResult callAuctionEngine(
            String jsScript,
            String auctionFunctionName,
            AdData advert,
            List<JSScriptArgument> otherArgs)
            throws Exception {
        return callAuctionEngine(
                jsScript, auctionFunctionName, ImmutableList.of(advert), otherArgs);
    }

    private List<AdWithBid> generateBids(
            String jsScript,
            List<AdData> ads,
            AdSelectionSignals auctionSignals,
            AdSelectionSignals perBuyerSignals,
            AdSelectionSignals trustedBiddingSignals,
            AdSelectionSignals contextualSignals,
            CustomAudienceSignals customAudienceSignals)
            throws Exception {
        return waitForFuture(
                () -> {
                    Log.i(TAG, "Calling generateBids");
                    return mAdSelectionScriptEngine.generateBids(
                            jsScript,
                            ads,
                            auctionSignals,
                            perBuyerSignals,
                            trustedBiddingSignals,
                            contextualSignals,
                            customAudienceSignals);
                });
    }

    private List<Double> scoreAds(
            String jsScript,
            List<AdWithBid> adsWithBids,
            AdSelectionConfig adSelectionConfig,
            AdSelectionSignals sellerSignals,
            AdSelectionSignals trustedScoringSignals,
            AdSelectionSignals contextualSignals,
            List<CustomAudienceSignals> customAudienceSignals)
            throws Exception {
        return waitForFuture(
                () -> {
                    Log.i(TAG, "Calling scoreAds");
                    return mAdSelectionScriptEngine.scoreAds(
                            jsScript,
                            adsWithBids,
                            adSelectionConfig,
                            sellerSignals,
                            trustedScoringSignals,
                            contextualSignals,
                            customAudienceSignals);
                });
    }

    private Long selectOutcome(
            String jsScript,
            List<AdSelectionIdWithBid> adSelectionIdWithBids,
            AdSelectionSignals selectionSignals)
            throws Exception {
        return waitForFuture(
                () -> {
                    Log.i(TAG, "Calling selectOutcome");
                    return mAdSelectionScriptEngine.selectOutcome(
                            jsScript, adSelectionIdWithBids, selectionSignals);
                });
    }

    private AuctionScriptResult callAuctionEngine(
            String jsScript,
            String auctionFunctionCall,
            List<AdData> adData,
            List<JSScriptArgument> otherArgs)
            throws Exception {
        ImmutableList.Builder<JSScriptArgument> adDataArgs = new ImmutableList.Builder<>();
        for (AdData ad : adData) {
            adDataArgs.add(AdDataArgument.asScriptArgument("ignored", ad));
        }
        return waitForFuture(
                () -> {
                    Log.i(TAG, "Calling Auction Script Engine");
                    return mAdSelectionScriptEngine.runAuctionScriptIterative(
                            jsScript,
                            adDataArgs.build(),
                            otherArgs,
                            ignoredArgs -> auctionFunctionCall);
                });
    }

    private boolean callJsValidation(String jsScript, List<String> functionNames) throws Exception {
        return waitForFuture(
                () -> {
                    Log.i(TAG, "Calling Auction Script Engine");
                    return mAdSelectionScriptEngine.validateAuctionScript(jsScript, functionNames);
                });
    }

    private <T> T waitForFuture(ThrowingSupplier<ListenableFuture<T>> function) throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        AtomicReference<ListenableFuture<T>> futureResult = new AtomicReference<>();
        futureResult.set(function.get());
        futureResult.get().addListener(resultLatch::countDown, mExecutorService);
        resultLatch.await();
        return futureResult.get().get();
    }

    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
