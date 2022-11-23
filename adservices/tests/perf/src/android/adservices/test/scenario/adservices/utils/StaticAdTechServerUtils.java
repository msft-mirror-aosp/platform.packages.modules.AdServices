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

package android.adservices.test.scenario.adservices.utils;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.TrustedBiddingData;
import android.net.Uri;

import com.google.common.collect.ImmutableList;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StaticAdTechServerUtils {
    // All Server Endpoints
    private static final String SERVER_BASE_DOMAIN =
            "performance-fledge-static-5jyy5ulagq-uc.a.run.app";
    private static final String SERVER_BASE_ADDRESS =
            String.format("https://%s", SERVER_BASE_DOMAIN);
    private static final String DECISION_LOGIC_URI =
            String.format("%s/seller/decision/simple_logic", SERVER_BASE_ADDRESS);
    private static final String TRUSTED_SCORING_SIGNALS_URI =
            String.format("%s/trusted/scoringsignals/simple", SERVER_BASE_ADDRESS);
    private static final String BIDDING_LOGIC_URI =
            String.format("%s/buyer/bidding/simple_logic", SERVER_BASE_ADDRESS);
    private static final String TRUSTED_BIDDING_SIGNALS_URI =
            String.format("%s/trusted/biddingsignals/simple", SERVER_BASE_ADDRESS);

    // All details needed to create AdSelectionConfig
    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString(SERVER_BASE_DOMAIN);
    private static final List<AdTechIdentifier> CUSTOM_AUDIENCE_BUYERS =
            Collections.singletonList(BUYER);
    private static final AdSelectionSignals AD_SELECTION_SIGNALS =
            AdSelectionSignals.fromString("{\"ad_selection_signals\":1}");
    private static final AdSelectionSignals SELLER_SIGNALS =
            AdSelectionSignals.fromString("{\"test_seller_signals\":1}");
    private static final Map<AdTechIdentifier, AdSelectionSignals> PER_BUYER_SIGNALS =
            Map.of(BUYER, AdSelectionSignals.fromString("{\"buyer_signals\":1}"));
    private static final AdTechIdentifier SELLER = AdTechIdentifier.fromString(SERVER_BASE_DOMAIN);

    // All details needed to create custom audiences
    private static final AdSelectionSignals VALID_USER_BIDDING_SIGNALS =
            AdSelectionSignals.fromString("{'valid': 'yep', 'opaque': 'definitely'}");
    private static final String AD_URI_PATH_FORMAT = "/render/%s/%s";
    private static final String DAILY_UPDATE_PATH_FORMAT = "/dailyupdate/%s";
    private static final int DELAY_TO_AVOID_THROTTLE_MS = 1001;
    private static final int API_RESPONSE_TIMEOUT_SECONDS = 100;
    private static final Duration CUSTOM_AUDIENCE_EXPIRE_IN = Duration.ofDays(1);
    private static final Instant VALID_ACTIVATION_TIME = Instant.now();
    private static final Instant VALID_EXPIRATION_TIME =
            VALID_ACTIVATION_TIME.plus(CUSTOM_AUDIENCE_EXPIRE_IN);
    private static final ArrayList<String> VALID_TRUSTED_BIDDING_KEYS =
            new ArrayList<>(Arrays.asList("example", "valid", "list", "of", "keys"));

    public static AdSelectionConfig createAdSelectionConfig() {
        return new AdSelectionConfig.Builder()
                .setSeller(SELLER)
                .setDecisionLogicUri(Uri.parse(DECISION_LOGIC_URI))
                // TODO(b/244530379) Make compatible with multiple buyers
                .setCustomAudienceBuyers(CUSTOM_AUDIENCE_BUYERS)
                .setAdSelectionSignals(AD_SELECTION_SIGNALS)
                .setSellerSignals(SELLER_SIGNALS)
                .setPerBuyerSignals(PER_BUYER_SIGNALS)
                .setTrustedScoringSignalsUri(Uri.parse(TRUSTED_SCORING_SIGNALS_URI))
                .build();
    }

    public static List<CustomAudience> getCustomAudiences(
            int numberOfCustomAudiences, int numberOfAdsPerCustomAudiences) {
        List<CustomAudience> customAudiences = new ArrayList<>();
        List<Double> bidsForBuyer = new ArrayList<>();

        for (int i = 1; i <= numberOfAdsPerCustomAudiences; i++) {
            bidsForBuyer.add(i + 0.1);
        }
        // Create multiple generic custom audience entries
        for (int i = 1; i <= numberOfCustomAudiences; i++) {
            CustomAudience customAudience =
                    createCustomAudience(BUYER, "GENERIC_CA_" + i, bidsForBuyer);
            customAudiences.add(customAudience);
        }
        return customAudiences;
    }

    public static String getAdRenderUri(String ca, int adId) {
        String adPath = String.format(AD_URI_PATH_FORMAT, ca, adId);
        String adRenderUri = SERVER_BASE_ADDRESS + adPath;
        return adRenderUri;
    }

    private static CustomAudience createCustomAudience(
            final AdTechIdentifier buyer, String name, List<Double> bids) {
        return createCustomAudience(
                buyer, name, bids, VALID_ACTIVATION_TIME, VALID_EXPIRATION_TIME);
    }

    private static CustomAudience createCustomAudience(
            final AdTechIdentifier buyer,
            String name,
            List<Double> bids,
            Instant activationTime,
            Instant expirationTime) {
        // Generate ads for with bids provided
        List<AdData> ads = new ArrayList<>();

        // Create ads with the custom audience name and bid number as the ad URI
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            String adRenderUri = getAdRenderUri(name, i + 1);

            ads.add(
                    new AdData.Builder()
                            .setRenderUri(Uri.parse(adRenderUri))
                            .setMetadata("{\"bid\":" + bids.get(i) + "}")
                            .build());
        }

        String dailyUpdatePath = String.format(DAILY_UPDATE_PATH_FORMAT, name);
        return new CustomAudience.Builder()
                .setBuyer(buyer)
                .setName(name)
                .setActivationTime(activationTime)
                .setExpirationTime(expirationTime)
                .setDailyUpdateUri(Uri.parse(SERVER_BASE_ADDRESS + dailyUpdatePath))
                .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        getValidTrustedBiddingDataByBuyer(Uri.parse(TRUSTED_BIDDING_SIGNALS_URI)))
                .setBiddingLogicUri(Uri.parse(BIDDING_LOGIC_URI))
                .setAds(ads)
                .build();
    }

    private static TrustedBiddingData getValidTrustedBiddingDataByBuyer(
            Uri validTrustedBiddingUri) {
        return new TrustedBiddingData.Builder()
                .setTrustedBiddingKeys(getValidTrustedBiddingKeys())
                .setTrustedBiddingUri(validTrustedBiddingUri)
                .build();
    }

    private static ImmutableList<String> getValidTrustedBiddingKeys() {
        return ImmutableList.copyOf(VALID_TRUSTED_BIDDING_KEYS);
    }
}
