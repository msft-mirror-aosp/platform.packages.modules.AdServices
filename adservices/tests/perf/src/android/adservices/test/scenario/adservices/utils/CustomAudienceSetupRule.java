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

import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.TrustedBiddingData;
import android.net.Uri;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CustomAudienceSetupRule implements TestRule {

    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("localhost");
    private static final String AD_URI_PREFIX = "/adverts/123/";
    private static final int DELAY_TO_AVOID_THROTTLE_MS = 1001;
    private static final int API_RESPONSE_TIMEOUT_SECONDS = 100;
    private static final Duration CUSTOM_AUDIENCE_EXPIRE_IN = Duration.ofDays(1);
    private static final Instant VALID_ACTIVATION_TIME = Instant.now();
    private static final Instant VALID_EXPIRATION_TIME =
            VALID_ACTIVATION_TIME.plus(CUSTOM_AUDIENCE_EXPIRE_IN);
    private static final AdSelectionSignals VALID_USER_BIDDING_SIGNALS =
            AdSelectionSignals.fromString("{'valid': 'yep', 'opaque': 'definitely'}");
    private final List<CustomAudience> mCustomAudiences;
    private final AdvertisingCustomAudienceClient mAdvertisingCustomAudienceClient;
    private final android.adservices.test.scenario.adservices.utils.MockWebServerRule
            mMockWebServerRule;

    public CustomAudienceSetupRule(
            AdvertisingCustomAudienceClient advertisingCustomAudienceClient,
            MockWebServerRule mockWebServerRule) {
        mAdvertisingCustomAudienceClient = advertisingCustomAudienceClient;
        mMockWebServerRule = mockWebServerRule;
        mCustomAudiences = new ArrayList<>();
    }

    private static Uri getUri(String name, String path) {
        return Uri.parse("https://" + name + path);
    }

    public void populateCustomAudiences(
            int numberOfCustomAudiences, int numberOfAdsPerCustomAudiences) throws Exception {
        List<Double> bidsForBuyer = new ArrayList<>();
        for (int i = 1; i <= numberOfAdsPerCustomAudiences; i++) {
            bidsForBuyer.add(i + 0.1);
        }
        // Create multiple generic custom audience entries
        for (int i = 1; i <= numberOfCustomAudiences; i++) {
            CustomAudience customAudience =
                    createCustomAudience(BUYER, "GENERIC_CA_" + i, bidsForBuyer);
            mCustomAudiences.add(customAudience);
        }

        for (CustomAudience ca : mCustomAudiences) {
            addDelayToAvoidThrottle();
            mAdvertisingCustomAudienceClient
                    .joinCustomAudience(ca)
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    base.evaluate();
                } finally {
                    if (mCustomAudiences != null) {
                        for (CustomAudience ca : mCustomAudiences) {
                            addDelayToAvoidThrottle(DELAY_TO_AVOID_THROTTLE_MS);
                            mAdvertisingCustomAudienceClient
                                    .leaveCustomAudience(ca.getBuyer(), ca.getName())
                                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                        }
                        mCustomAudiences.clear();
                    }
                }
            }
        };
    }

    private CustomAudience createCustomAudience(
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
            ads.add(
                    new AdData.Builder()
                            .setRenderUri(
                                    getUri(
                                            buyer.toString(),
                                            AD_URI_PREFIX + name + "/ad" + (i + 1)))
                            .setMetadata("{\"result\":" + bids.get(i) + "}")
                            .build());
        }

        return new CustomAudience.Builder()
                .setBuyer(buyer)
                .setName(name)
                .setActivationTime(activationTime)
                .setExpirationTime(expirationTime)
                .setDailyUpdateUri(mMockWebServerRule.uriForPath("/update"))
                .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        getValidTrustedBiddingDataByBuyer(
                                mMockWebServerRule.uriForPath(
                                        MockWebServerDispatcherFactory
                                                .getTrustedBiddingSignalsPath())))
                .setBiddingLogicUri(
                        mMockWebServerRule.uriForPath(
                                MockWebServerDispatcherFactory.getBiddingLogicUriPath()))
                .setAds(ads)
                .build();
    }

    private CustomAudience createCustomAudience(
            final AdTechIdentifier buyer, String name, List<Double> bids) {
        return createCustomAudience(
                buyer, name, bids, VALID_ACTIVATION_TIME, VALID_EXPIRATION_TIME);
    }

    private TrustedBiddingData getValidTrustedBiddingDataByBuyer(Uri validTrustedBiddingUri) {
        return new TrustedBiddingData.Builder()
                .setTrustedBiddingKeys(MockWebServerDispatcherFactory.getValidTrustedBiddingKeys())
                .setTrustedBiddingUri(validTrustedBiddingUri)
                .build();
    }

    private void addDelayToAvoidThrottle() throws InterruptedException {
        addDelayToAvoidThrottle(DELAY_TO_AVOID_THROTTLE_MS);
    }

    private void addDelayToAvoidThrottle(int delayValueMs) throws InterruptedException {
        if (delayValueMs > 0) {
            Thread.sleep(delayValueMs);
        }
    }
}
