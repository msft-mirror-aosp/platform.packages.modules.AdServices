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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AddAdSelectionOverrideRequest;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.clients.customaudience.TestAdvertisingCustomAudienceClient;
import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.AddCustomAudienceOverrideRequest;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.TrustedBiddingDataFixture;
import android.content.Context;
import android.net.Uri;
import android.os.Process;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FledgeCtsTest {
    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private static final AdTechIdentifier BUYER_1 = AdSelectionConfigFixture.BUYER_1;
    private static final AdTechIdentifier BUYER_2 = AdSelectionConfigFixture.BUYER_2;

    private static final String SELLER_DECISION_LOGIC_URI = "/ssp/decision/logic/";
    private static final String BUYER_BIDDING_LOGIC_URI_PREFIX = "/buyer/bidding/logic/";
    private static final Uri TRUSTED_SCORING_SIGNALS_URI = Uri.parse("ssp/trusted/signals");
    private static final AdSelectionSignals TRUSTED_SCORING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"render_url_1\": \"signals_for_1\",\n"
                            + "\t\"render_url_2\": \"signals_for_2\"\n"
                            + "}");

    private static final AdTechIdentifier SELLER =
            AdTechIdentifier.fromString("developer.android.com");
    private static final String SELLER_REPORTING_PATH = "/reporting/seller";
    private static final String BUYER_REPORTING_PATH = "/reporting/buyer";

    private static final AdSelectionConfig AD_SELECTION_CONFIG =
            AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                    .setCustomAudienceBuyers(Arrays.asList(BUYER_1, BUYER_2))
                    .setSeller(SELLER)
                    .setDecisionLogicUri(
                            Uri.parse("https://" + SELLER + "/" + SELLER_DECISION_LOGIC_URI))
                    .setTrustedScoringSignalsUri(
                            Uri.parse("https://" + SELLER + "/" + TRUSTED_SCORING_SIGNALS_URI))
                    .build();

    private AdSelectionClient mAdSelectionClient;
    private AdvertisingCustomAudienceClient mCustomAudienceClient;
    private TestAdvertisingCustomAudienceClient mTestCustomAudienceClient;
    private DevContext mDevContext;
    private boolean mIsDebugMode;

    @Before
    public void setup() {
        mAdSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        mCustomAudienceClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(MoreExecutors.directExecutor())
                        .build();
        mTestCustomAudienceClient =
                new TestAdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(MoreExecutors.directExecutor())
                        .build();
        mDevContext = DevContextFilter.create(sContext).createDevContext(Process.myUid());
        mIsDebugMode = mDevContext.getDevOptionsEnabled();
    }

    @Test
    public void testFledgeFlowFailsInNonDebuggableState() throws Exception {
        Assume.assumeFalse(mIsDebugMode);

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

        CustomAudience customAudience1 =
                createCustomAudience(
                        BUYER_1,
                        bidsForBuyer1);

        CustomAudience customAudience2 =
                createCustomAudience(
                        BUYER_2,
                        bidsForBuyer2);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        mCustomAudienceClient.joinCustomAudience(customAudience1).get(10, TimeUnit.SECONDS);
        mCustomAudienceClient.joinCustomAudience(customAudience2).get(10, TimeUnit.SECONDS);

        // Adding AdSelection override, asserting a failure since app is not debuggable.
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, decisionLogicJs, TRUSTED_SCORING_SIGNALS);

        ListenableFuture<Void> adSelectionOverrideResult =
                mAdSelectionClient.overrideAdSelectionConfigRemoteInfo(
                        addAdSelectionOverrideRequest);

        Exception adSelectionOverrideException =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            adSelectionOverrideResult.get(10, TimeUnit.SECONDS);
                        });
        assertThat(adSelectionOverrideException.getCause()).isInstanceOf(SecurityException.class);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setOwnerPackageName(customAudience2.getOwnerPackageName())
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(biddingLogicJs)
                        .setTrustedBiddingSignals(AdSelectionSignals.EMPTY)
                        .build();

        // Adding Custom audience override, asserting a failure since app is not debuggable.
        ListenableFuture<Void> customAudienceOverrideResult =
                mTestCustomAudienceClient.overrideCustomAudienceRemoteInfo(
                        addCustomAudienceOverrideRequest);

        Exception customAudienceOverrideException =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            customAudienceOverrideResult.get(10, TimeUnit.SECONDS);
                        });
        assertThat(customAudienceOverrideException.getCause())
                .isInstanceOf(SecurityException.class);

        // Running ad selection and asserting that a failure is returned since the fetch calls
        // should fail.
        Exception selectAdsException =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            mAdSelectionClient
                                    .selectAds(AD_SELECTION_CONFIG)
                                    .get(30, TimeUnit.SECONDS);
                        });
        assertThat(selectAdsException.getCause().getCause())
                .isInstanceOf(IllegalStateException.class);
        // Cannot perform reporting since no ad selection id is returned.
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
                            .setRenderUri(CommonFixture.getUri(buyer, "/ad" + (i + 1)))
                            .setMetadata("{\"result\":" + bids.get(i) + "}")
                            .build());
        }

        return new CustomAudience.Builder()
                .setOwnerPackageName(CustomAudienceFixture.VALID_OWNER)
                .setBuyer(buyer)
                .setName(buyer + CustomAudienceFixture.VALID_NAME)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setDailyUpdateUrl(CustomAudienceFixture.getValidDailyUpdateUriByBuyer(buyer))
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(buyer))
                .setBiddingLogicUrl(
                        CommonFixture.getUri(
                                buyer, BUYER_BIDDING_LOGIC_URI_PREFIX + buyer.toString()))
                .setAds(ads)
                .build();
    }
}
