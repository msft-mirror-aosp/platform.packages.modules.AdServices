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

package com.android.tests.providers.sdkfledge;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.AddAdSelectionOverrideRequest;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.clients.adselection.TestAdSelectionClient;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.clients.customaudience.TestAdvertisingCustomAudienceClient;
import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.AddCustomAudienceOverrideRequest;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.TrustedBiddingData;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SdkFledge extends SandboxedSdkProvider {
    private static final String TAG = "SdkFledge";
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private static final AdTechIdentifier SELLER = AdTechIdentifier.fromString("store.google.com");

    private static final AdTechIdentifier BUYER_1 =
            AdTechIdentifier.fromString("developer.android.com");
    private static final AdTechIdentifier BUYER_2 = AdTechIdentifier.fromString("google.com");

    private static final String AD_URL_PREFIX = "/adverts/123/";

    private static final String SELLER_DECISION_LOGIC_URI_PATH = "/ssp/decision/logic/";
    private static final String BUYER_BIDDING_LOGIC_URI_PATH = "/buyer/bidding/logic/";
    private static final String SELLER_TRUSTED_SIGNAL_URI_PATH = "/kv/seller/signals/";

    private static final String SELLER_REPORTING_PATH = "/reporting/seller";
    private static final String BUYER_REPORTING_PATH = "/reporting/buyer";

    private static final AdSelectionSignals TRUSTED_SCORING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"render_url_1\": \"signals_for_1\",\n"
                            + "\t\"render_url_2\": \"signals_for_2\"\n"
                            + "}");

    private static final AdSelectionSignals TRUSTED_BIDDING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"example\": \"example\",\n"
                            + "\t\"valid\": \"Also valid\",\n"
                            + "\t\"list\": \"list\",\n"
                            + "\t\"of\": \"of\",\n"
                            + "\t\"keys\": \"trusted bidding signal Values\"\n"
                            + "}");

    private static final AdSelectionConfig AD_SELECTION_CONFIG =
            anAdSelectionConfigBuilder()
                    .setCustomAudienceBuyers(Arrays.asList(BUYER_1, BUYER_2))
                    .setDecisionLogicUri(
                            Uri.parse(
                                    String.format(
                                            "https://%s%s",
                                            SELLER, SELLER_DECISION_LOGIC_URI_PATH)))
                    .setTrustedScoringSignalsUri(
                            Uri.parse(
                                    String.format(
                                            "https://%s%s",
                                            SELLER, SELLER_TRUSTED_SIGNAL_URI_PATH)))
                    .build();
    private static final String HTTPS_SCHEME = "https";

    private AdSelectionClient mAdSelectionClient;
    private TestAdSelectionClient mTestAdSelectionClient;
    private AdvertisingCustomAudienceClient mCustomAudienceClient;
    private TestAdvertisingCustomAudienceClient mTestCustomAudienceClient;

    private Executor mExecutor;
    private OnLoadSdkCallback mCallback;

    @Override
    public void onLoadSdk(Bundle params, Executor executor, OnLoadSdkCallback callback) {
        mExecutor = executor;
        mCallback = callback;
        executeTest();
        callback.onLoadSdkFinished(null);
    }

    @Override
    public View getView(
            @NonNull Context windowContext, @NonNull Bundle params, int width, int height) {
        return null;
    }

    @Override
    public void onDataReceived(Bundle data, DataReceivedCallback callback) {}

    private void executeTest() {
        try {
            setup();
        } catch (Exception e) {
            String errorMessage =
                    String.format("Error setting up the test: message is %s", e.getMessage());
            Log.e(TAG, errorMessage);
            mExecutor.execute(() -> mCallback.onLoadSdkError(errorMessage));
            return;
        }
        String decisionLogicJs =
                "function scoreAd(ad, bid, auction_config, seller_signals,"
                        + " trusted_scoring_signals, contextual_signal, user_signal,"
                        + " custom_audience_signal) { \n"
                        + "  return {'status': 0, 'score': bid };\n"
                        + "}\n"
                        + "function reportResult(ad_selection_config, render_url, bid,"
                        + " contextual_signals) { \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_url': '"
                        + SELLER_REPORTING_PATH
                        + "' } };\n"
                        + "}";

        String biddingLogicJs =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals, contextual_signals, user_signals,"
                        + " custom_audience_signals) { \n"
                        + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}\n"
                        + "function reportWin(ad_selection_signals, per_buyer_signals,"
                        + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_url': '"
                        + BUYER_REPORTING_PATH
                        + "' } };\n"
                        + "}";

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_2, bidsForBuyer2);

        try {
            mCustomAudienceClient.joinCustomAudience(customAudience1).get(10, TimeUnit.SECONDS);
            mCustomAudienceClient.joinCustomAudience(customAudience2).get(10, TimeUnit.SECONDS);

        } catch (Exception e) {
            String errorMessage =
                    String.format("Error setting up the test: message is %s", e.getMessage());
            Log.e(TAG, errorMessage);
            mExecutor.execute(() -> mCallback.onLoadSdkError(errorMessage));
            return;
        }

        try {
            AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                    new AddAdSelectionOverrideRequest(
                            AD_SELECTION_CONFIG, decisionLogicJs, TRUSTED_SCORING_SIGNALS);
            mTestAdSelectionClient
                    .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                    .get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            String errorMessage =
                    String.format(
                            "Error adding ad selection override: message is %s", e.getMessage());
            Log.e(TAG, errorMessage);
            mExecutor.execute(() -> mCallback.onLoadSdkError(errorMessage));
            return;
        }

        try {
            AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest =
                    new AddCustomAudienceOverrideRequest.Builder()
                            .setOwnerPackageName(customAudience2.getOwnerPackageName())
                            .setBuyer(customAudience2.getBuyer())
                            .setName(customAudience2.getName())
                            .setBiddingLogicJs(biddingLogicJs)
                            .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                            .build();

            mTestCustomAudienceClient
                    .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest)
                    .get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            String errorMessage =
                    String.format(
                            "Error adding custom audience override: message is %s", e.getMessage());
            Log.e(TAG, errorMessage);
            mExecutor.execute(() -> mCallback.onLoadSdkError(errorMessage));
            return;
        }

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        long adSelectionId = -1;
        try {
            // Running ad selection and asserting that the outcome is returned in < 10 seconds
            AdSelectionOutcome outcome =
                    mAdSelectionClient.selectAds(AD_SELECTION_CONFIG).get(10, TimeUnit.SECONDS);

            adSelectionId = outcome.getAdSelectionId();

            if (!outcome.getRenderUri()
                    .equals(getUri(BUYER_2.toString(), AD_URL_PREFIX + "/ad3"))) {
                String errorMessage = String.format("Ad selection failed to select the correct ad");
                Log.e(TAG, errorMessage);
                mExecutor.execute(() -> mCallback.onLoadSdkError(errorMessage));
            }
        } catch (Exception e) {
            String errorMessage =
                    String.format(
                            "Error encountered during ad selection: message is %s", e.getMessage());
            Log.e(TAG, errorMessage);
            mExecutor.execute(() -> mCallback.onLoadSdkError(errorMessage));
            return;
        }

        try {
            ReportImpressionRequest reportImpressionRequest =
                    new ReportImpressionRequest(adSelectionId, AD_SELECTION_CONFIG);

            // Performing reporting, and asserting that no exception is thrown
            mAdSelectionClient.reportImpression(reportImpressionRequest).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            String errorMessage =
                    String.format(
                            "Error encountered during reporting: message is %s", e.getMessage());
            Log.e(TAG, errorMessage);
            mExecutor.execute(() -> mCallback.onLoadSdkError(errorMessage));
            return;
        }

        // If we got this far, that means the test succeeded
        mExecutor.execute(() -> mCallback.onLoadSdkFinished(null));
    }

    private void setup() {
        mAdSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(getContext())
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        mTestAdSelectionClient =
                new TestAdSelectionClient.Builder()
                        .setContext(getContext())
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        mCustomAudienceClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(getContext())
                        .setExecutor(MoreExecutors.directExecutor())
                        .build();
        mTestCustomAudienceClient =
                new TestAdvertisingCustomAudienceClient.Builder()
                        .setContext(getContext())
                        .setExecutor(MoreExecutors.directExecutor())
                        .build();
    }

    /**
     * @param buyer The name of the buyer for this Custom Audience
     * @param bids these bids, are added to its metadata. Our JS logic then picks this value and
     *     creates ad with the provided value as bid
     * @return a real Custom Audience object that can be persisted and used in bidding and scoring
     */
    private CustomAudience createCustomAudience(final AdTechIdentifier buyer, List<Double> bids) {

        // Generate ads for with bids provided
        List<AdData> ads = new ArrayList<>();

        // Create ads with the buyer name and bid number as the ad URL
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            ads.add(
                    new AdData.Builder()
                            .setRenderUri(getUri(buyer.toString(), AD_URL_PREFIX + "/ad" + (i + 1)))
                            .setMetadata("{\"result\":" + bids.get(i) + "}")
                            .build());
        }

        return new CustomAudience.Builder()
                .setOwnerPackageName("com.android.tests.sandbox.fledge")
                .setBuyer(buyer)
                .setName(buyer + "testCustomAudienceName")
                .setActivationTime(Instant.now().truncatedTo(ChronoUnit.MILLIS))
                .setExpirationTime(Instant.now().plus(Duration.ofDays(40)))
                .setDailyUpdateUrl(getUri(buyer.toString(), "/update"))
                .setUserBiddingSignals(
                        AdSelectionSignals.fromString("{'valid': 'yep', 'opaque': 'definitely'}"))
                .setTrustedBiddingData(
                        new TrustedBiddingData.Builder()
                                .setTrustedBiddingKeys(
                                        Arrays.asList("example", "valid", "list", "of", "keys"))
                                .setTrustedBiddingUrl(getUri(buyer.toString(), "/trusted/bidding"))
                                .build())
                .setBiddingLogicUrl(getUri(buyer.toString(), BUYER_BIDDING_LOGIC_URI_PATH))
                .setAds(ads)
                .build();
    }

    public static AdSelectionConfig.Builder anAdSelectionConfigBuilder() {
        return new AdSelectionConfig.Builder()
                .setSeller(SELLER)
                .setDecisionLogicUri(getUri(SELLER.toString(), "/update"))
                .setCustomAudienceBuyers(Arrays.asList(BUYER_1, BUYER_2))
                .setAdSelectionSignals(AdSelectionSignals.EMPTY)
                .setSellerSignals(AdSelectionSignals.fromString("{\"test_seller_signals\":1}"))
                .setPerBuyerSignals(
                        Map.of(
                                BUYER_1,
                                AdSelectionSignals.fromString("{\"buyer_signals\":1}"),
                                BUYER_2,
                                AdSelectionSignals.fromString("{\"buyer_signals\":2}")))
                .setTrustedScoringSignalsUri(getUri(SELLER.toString(), "/trusted/scoring"));
    }

    private static Uri getUri(String host, String path) {
        return Uri.parse(HTTPS_SCHEME + "://" + host + path);
    }
}
