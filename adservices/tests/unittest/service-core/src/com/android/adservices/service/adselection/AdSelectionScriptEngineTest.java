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

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdWithBid;
import android.adservices.common.AdData;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.service.adselection.AdSelectionScriptEngine.AuctionScriptResult;
import com.android.adservices.service.js.JSScriptArgument;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.net.MalformedURLException;
import java.time.Duration;
import java.time.Instant;
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
    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(1);
    private final AdSelectionScriptEngine mAdSelectionScriptEngine =
            new AdSelectionScriptEngine(sContext);
    private static final Instant NOW = Instant.now();
    private static final CustomAudienceSignals CUSTOM_AUDIENCE_SIGNALS =
            new CustomAudienceSignals("owner", "buyer", "name",
                    NOW, NOW.plus(Duration.ofDays(1)),
                    "{}");
    @Test
    public void testAuctionScriptIsInvalidIfRequiredFunctionDoesNotExist() throws Exception {
        assertFalse(
                callJsValidation(
                        "function helloAdvert(ad) { return {'status': 0, 'greeting': 'hello ' +"
                                + " ad.render_url }; }",
                        ImmutableList.of("helloAdvertWrongName")));
    }

    @Test
    public void testAuctionScriptIsInvalidIfAnyRequiredFunctionDoesNotExist() throws Exception {
        assertFalse(
                callJsValidation(
                        "function helloAdvert(ad) { return {'status': 0, 'greeting': 'hello ' +"
                                + " ad.render_url }; }",
                        ImmutableList.of("helloAdvert", "helloAdvertWrongName")));
    }

    @Test
    public void testCanCallScript() throws Exception {
        AdData advert = new AdData(Uri.parse("http://www.domain.com/adverts/123"), "{}");
        final AuctionScriptResult result =
                callAuctionEngine(
                        "function helloAdvert(ad) { return {'status': 0, 'greeting': 'hello ' +"
                                + " ad.render_url }; }",
                        "helloAdvert(ad)",
                        advert,
                        ImmutableList.of());
        assertThat(result.status).isEqualTo(0);
        assertThat(((JSONObject) result.results.get(0)).getString("greeting"))
                .isEqualTo("hello http://www.domain.com/adverts/123");
    }

    @Test
    public void testThrowsIllegalArgumentExceptionIfTheFunctionIsNotFound() throws Exception {
        AdData advert = new AdData(Uri.parse("http://www.domain.com/adverts/123"), "{}");
        Exception exception =
                Assert.assertThrows(
                        ExecutionException.class,
                        () ->
                                callAuctionEngine(
                                        "function helloAdvert(ad) { return {'status': 0,"
                                                + " 'greeting': 'hello ' + ad.render_url }; }",
                                        "helloAdvertWrongName",
                                        advert,
                                        ImmutableList.of()));

        assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testFailsIfScriptIsNotReturningJson() throws Exception {
        AdData advert = new AdData(Uri.parse("http://www.domain.com/adverts/123"), "{}");
        final AuctionScriptResult result =
                callAuctionEngine(
                        "function helloAdvert(ad) { return 'hello ' + ad.render_url; }",
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
                                + " 'value': ad.render_url }; }",
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
                                + " trusted_bidding_signals, contextual_signals, user_signals,"
                                + " custom_audience_signals) { \n"
                                + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                                + "}",
                        ads,
                        "{}",
                        "{}",
                        "{}",
                        "{}",
                        "{}",
                        CUSTOM_AUDIENCE_SIGNALS);
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
                                + " trusted_bidding_signals, contextual_signals, user_signals,"
                                + " custom_audience_signals) { \n"
                                + "  return {'status': 1, 'ad': ad, 'bid': ad.metadata.result };\n"
                                + "}",
                        ads,
                        "{}",
                        "{}",
                        "{}",
                        "{}",
                        "{}",
                        CUSTOM_AUDIENCE_SIGNALS);
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
                                + " trusted_bidding_signals, contextual_signals, user_signals,"
                                + " custom_audience_signals) { \n"
                                + " if (ad.metadata.result > 2) return {'status': 0, 'ad': ad };\n"
                                + " else return {'status': 0, 'ad': ad, 'bid': 10 };\n"
                                + "}",
                        ads,
                        "{}",
                        "{}",
                        "{}",
                        "{}",
                        "{}",
                        CUSTOM_AUDIENCE_SIGNALS);
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
                        "{}",
                        "{}",
                        "{}",
                        CUSTOM_AUDIENCE_SIGNALS);
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
                        "{}",
                        "{}",
                        "{}",
                        CUSTOM_AUDIENCE_SIGNALS);
        assertThat(result).isEmpty();
    }

    @Test
    public void testCanRunScriptWithStringInterpolationTokenInIt() throws Exception {
        AdData advert = new AdData(Uri.parse("http://www.domain.com/adverts/123"), "{}");
        final AuctionScriptResult result =
                callAuctionEngine(
                        "function helloAdvert(ad) { return {'status': 0, 'greeting': '%shello ' +"
                                + " ad.render_url }; }",
                        "helloAdvert(ad)", advert, ImmutableList.of());
        assertThat(result.status).isEqualTo(0);
        assertThat(((JSONObject) result.results.get(0)).getString("greeting"))
                .isEqualTo("%shello http://www.domain.com/adverts/123");
    }

    private AdSelectionConfig anAdSelectionConfig() throws MalformedURLException {
        return new AdSelectionConfig.Builder()
                .setSeller("www.mydomain.com")
                .setPerBuyerSignals(ImmutableMap.of())
                .setContextualAds(ImmutableList.of())
                .setDecisionLogicUrl(Uri.parse("http://www.mydomain.com/updateAds"))
                .setSellerSignals("{}")
                .setCustomAudienceBuyers(ImmutableList.of("www.buyer.com"))
                .setAdSelectionSignals("{}")
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
            String auctionSignals,
            String perBuyerSignals,
            String trustedBiddingSignals,
            String contextualSignals,
            String userSignals,
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
                            userSignals,
                            customAudienceSignals);
                });
    }

    private List<Double> scoreAds(
            String jsScript,
            List<AdWithBid> adsWithBids,
            AdSelectionConfig adSelectionConfig,
            String sellerSignals,
            String trustedScoringSignals,
            String contextualSignals,
            CustomAudienceSignals customAudienceSignals)
            throws Exception {
        return waitForFuture(
                () -> {
                    Log.i(TAG, "Calling generateBids");
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
                    return mAdSelectionScriptEngine.runAuctionScript(
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
