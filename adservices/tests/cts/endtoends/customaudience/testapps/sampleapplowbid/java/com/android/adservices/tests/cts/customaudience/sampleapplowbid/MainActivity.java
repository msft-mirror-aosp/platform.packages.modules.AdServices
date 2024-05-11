/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.tests.cts.customaudience.sampleapplowbid;

import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.common.AdData;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.TrustedBiddingData;
import android.adservices.customaudience.TrustedBiddingDataFixture;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class MainActivity extends AppCompatActivity {
    private static final String TAG = "adservices";
    private static final int API_RESPONSE_TIMEOUT_SECONDS = 100;
    private static final String AD_URI_PREFIX = "/adverts/123/";
    private static final String BUYER_BIDDING_LOGIC_URI_PATH = "/buyer/bidding/logic/";
    private static final String BUYER_TRUSTED_SIGNAL_URI_PATH = "/trusted/bidding/";
    private static final String CUSTOM_AUDIENCE_SEQ = "customAudienceLowBid";
    private static final List<Double> BIDS = ImmutableList.of(10d);

    private static final String TEST_PACKAGE_NAME =
            "com.android.adservices.tests.cts.customaudience";
    // This test app will send this broadcast to the CTS test suite when custom audience is joined.
    private static final String JOINED_CUSTOM_AUDIENCE_LOW_BID_BROADCAST =
            TEST_PACKAGE_NAME + ".JOINED_CUSTOM_AUDIENCE_LOW_BID";

    // Intent keys to pass data between test and buyer apps.
    private static final String SERVER_BASE_ADDRESS_INTENT_KEY =
            TEST_PACKAGE_NAME + ".serverBaseAddress";
    private static final String LOCALHOST_BUYER_DOMAIN_INTENT_KEY =
            TEST_PACKAGE_NAME + ".localhostBuyerDomain";
    private static final String AD_RENDER_URIS_INTENT_KEY = TEST_PACKAGE_NAME + ".adRenderUris";

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private final Random mRenderUriRandom = new Random();

    private Uri mLocalhostBuyerDomain;
    private String mServerBaseAddress;
    private Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getApplicationContext();

        Intent intent = getIntent();
        mLocalhostBuyerDomain =
                intent.getParcelableExtra(LOCALHOST_BUYER_DOMAIN_INTENT_KEY, Uri.class);
        mServerBaseAddress = intent.getStringExtra(SERVER_BASE_ADDRESS_INTENT_KEY);

        try {
            CustomAudience customAudience = createCustomAudience();
            joinCustomAudience(customAudience);
            sendJoinedCustomAudienceBroadcast(customAudience);
        } catch (Exception e) {
            Log.e(TAG, "Failed to join custom audience: " + e);
        }

        finish();
    }

    private CustomAudience createCustomAudience() {
        // Create ads for bids
        List<AdData> ads = new ArrayList<>();

        // Create ads with the buyer name, bid number, and random ID as the ad URI.
        // (Random ID ensures distinct URIs on subsequent invocations.)
        // Add the bid value to the metadata.
        for (int i = 0; i < BIDS.size(); i++) {
            AdData.Builder builder =
                    new AdData.Builder()
                            .setRenderUri(
                                    CommonFixture.getUri(
                                            mLocalhostBuyerDomain.getAuthority(),
                                            AD_URI_PREFIX
                                                    + CUSTOM_AUDIENCE_SEQ
                                                    + "/ad"
                                                    + (i + 1)
                                                    + "/id"
                                                    + mRenderUriRandom.nextInt()))
                            .setMetadata("{\"result\":" + BIDS.get(i) + "}");
            ads.add(builder.build());
        }

        return new CustomAudience.Builder()
                .setBuyer(
                        AdTechIdentifier.fromString(
                                Objects.requireNonNull(mLocalhostBuyerDomain.getHost())))
                .setName(
                        mLocalhostBuyerDomain.getHost()
                                + CUSTOM_AUDIENCE_SEQ
                                + CustomAudienceFixture.VALID_NAME)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setDailyUpdateUri(
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                AdTechIdentifier.fromString(
                                        Objects.requireNonNull(
                                                mLocalhostBuyerDomain.getAuthority()))))
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        new TrustedBiddingData.Builder()
                                .setTrustedBiddingUri(
                                        Uri.parse(
                                                mServerBaseAddress + BUYER_TRUSTED_SIGNAL_URI_PATH))
                                .setTrustedBiddingKeys(
                                        TrustedBiddingDataFixture.getValidTrustedBiddingKeys())
                                .build())
                .setBiddingLogicUri(
                        Uri.parse(
                                mServerBaseAddress
                                        + BUYER_BIDDING_LOGIC_URI_PATH
                                        + CUSTOM_AUDIENCE_SEQ))
                .setAds(ads)
                .build();
    }

    private void joinCustomAudience(CustomAudience customAudience) throws Exception {
        AdvertisingCustomAudienceClient advertisingCustomAudienceClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(mContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        advertisingCustomAudienceClient
                .joinCustomAudience(customAudience)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void sendJoinedCustomAudienceBroadcast(CustomAudience customAudience) {
        ArrayList<Uri> adRenderUris =
                customAudience.getAds().stream()
                        .map(AdData::getRenderUri)
                        .collect(Collectors.toCollection(ArrayList::new));
        Intent sendJoinedCustomAudienceBroadcast =
                new Intent()
                        .setAction(JOINED_CUSTOM_AUDIENCE_LOW_BID_BROADCAST)
                        .putParcelableArrayListExtra(AD_RENDER_URIS_INTENT_KEY, adRenderUris);
        mContext.sendBroadcast(sendJoinedCustomAudienceBroadcast);
    }
}
