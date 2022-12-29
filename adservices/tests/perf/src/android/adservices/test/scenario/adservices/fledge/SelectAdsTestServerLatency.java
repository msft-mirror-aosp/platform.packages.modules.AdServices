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

package android.adservices.test.scenario.adservices.fledge;

import android.Manifest;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.TrustedBiddingData;
import android.adservices.test.scenario.adservices.utils.StaticAdTechServerUtils;
import android.content.Context;
import android.net.Uri;
import android.platform.test.scenario.annotation.Scenario;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellUtils;

import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Scenario
@RunWith(JUnit4.class)
public class SelectAdsTestServerLatency {

    private static final String TAG = "SelectAds";

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private static final int API_RESPONSE_TIMEOUT_SECONDS = 100;
    private static final int DELAY_TO_AVOID_THROTTLE = 1001;
    // The number of ms to sleep after killing the adservices process so it has time to recover
    public static final long SLEEP_MS_AFTER_KILL = 2000L;
    // Command to kill the adservices process
    public static final String KILL_ADSERVICES_CMD =
            "su 0 killall -9 com.google.android.adservices.api";
    // Command prevent activity manager from backing off on restarting the adservices process
    public static final String DISABLE_ADSERVICES_BACKOFF_CMD =
            "am service-restart-backoff disable com.google.android.adservices.api";

    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final AdSelectionClient AD_SELECTION_CLIENT =
            new AdSelectionClient.Builder()
                    .setContext(CONTEXT)
                    .setExecutor(CALLBACK_EXECUTOR)
                    .build();
    private static final AdvertisingCustomAudienceClient CUSTOM_AUDIENCE_CLIENT =
            new AdvertisingCustomAudienceClient.Builder()
                    .setContext(CONTEXT)
                    .setExecutor(CALLBACK_EXECUTOR)
                    .build();
    private final Ticker mTicker =
            new Ticker() {
                public long read() {
                    return android.os.SystemClock.elapsedRealtimeNanos();
                }
            };
    private static List<CustomAudience> sCustomAudiences;

    @BeforeClass
    public static void setupBeforeClass() {
        StaticAdTechServerUtils.warmupServers();
        sCustomAudiences = new ArrayList<>();
        // Disable backoff since we will be killing the process between tests
        ShellUtils.runShellCommand(DISABLE_ADSERVICES_BACKOFF_CMD);
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.WRITE_DEVICE_CONFIG);

        ShellUtils.runShellCommand(
                "device_config put adservices fledge_ad_selection_bidding_timeout_per_ca_ms "
                        + "120000");
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_ad_selection_scoring_timeout_ms 120000");
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_ad_selection_overall_timeout_ms 120000");
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_ad_selection_bidding_timeout_per_buyer_ms "
                        + "120000");
        ShellUtils.runShellCommand("setprop debug.adservices.disable_fledge_enrollment_check true");
        ShellUtils.runShellCommand("device_config put adservices global_kill_switch false");
        ShellUtils.runShellCommand(
                "device_config put adservices adservice_system_service_enabled true");
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        for (CustomAudience ca : sCustomAudiences) {
            Thread.sleep(DELAY_TO_AVOID_THROTTLE);
            CUSTOM_AUDIENCE_CLIENT
                    .leaveCustomAudience(ca.getBuyer(), ca.getName())
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Before
    public void setup() throws Exception {
        ShellUtils.runShellCommand(KILL_ADSERVICES_CMD);
        Thread.sleep(SLEEP_MS_AFTER_KILL);
    }

    @After
    public void tearDown() throws Exception {
        List<CustomAudience> removedCAs = new ArrayList<>();
        try {
            for (CustomAudience ca : sCustomAudiences) {
                Thread.sleep(DELAY_TO_AVOID_THROTTLE);
                CUSTOM_AUDIENCE_CLIENT
                        .leaveCustomAudience(ca.getBuyer(), ca.getName())
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                removedCAs.add(ca);
            }
        } finally {
            sCustomAudiences.removeAll(removedCAs);
        }
    }


    @Test
    public void selectAds_oneBuyerLargeCAs() throws Exception {
        // 1 Seller, 1 Buyer, 71 Custom Audiences
        sCustomAudiences.addAll(readCustomAudiences("CustomAudiencesOneBuyerLargeCAs.json"));
        joinCustomAudiences(sCustomAudiences);
        AdSelectionConfig config = readAdSelectionConfig("AdSelectionConfigOneBuyerLargeCAs.json");
        Stopwatch timer = Stopwatch.createStarted(mTicker);

        AdSelectionOutcome outcome =
                AD_SELECTION_CLIENT
                        .selectAds(config)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        timer.stop();

        Log.i(
                TAG,
                "("
                        + generateLogLabel("selectAds_oneBuyerLargeCAs")
                        + ": "
                        + timer.elapsed(TimeUnit.MILLISECONDS)
                        + " ms)");
        Assert.assertFalse(outcome.getRenderUri().toString().isEmpty());
    }

    @Test
    public void selectAds_fiveBuyersLargeCAs() throws Exception {
        // 1 Seller, 5 Buyer, each buyer has 71 Custom Audiences
        sCustomAudiences.addAll(readCustomAudiences("CustomAudiencesFiveBuyersLargeCAs.json"));
        joinCustomAudiences(sCustomAudiences);
        AdSelectionConfig config =
                readAdSelectionConfig("AdSelectionConfigFiveBuyersLargeCAs.json");
        Stopwatch timer = Stopwatch.createStarted(mTicker);

        AdSelectionOutcome outcome =
                AD_SELECTION_CLIENT
                        .selectAds(config)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        timer.stop();

        Log.i(
                TAG,
                "("
                        + generateLogLabel("selectAds_fiveBuyersLargeCAs")
                        + ": "
                        + timer.elapsed(TimeUnit.MILLISECONDS)
                        + " ms)");
        Assert.assertFalse(outcome.getRenderUri().toString().isEmpty());
    }

    private ImmutableList<CustomAudience> readCustomAudiences(String fileName) throws Exception {
        ImmutableList.Builder<CustomAudience> customAudienceBuilder = ImmutableList.builder();
        InputStream is = ApplicationProvider.getApplicationContext().getAssets().open(fileName);
        JSONArray customAudiencesJson = new JSONArray(new String(is.readAllBytes()));
        is.close();

        for (int i = 0; i < customAudiencesJson.length(); i++) {
            JSONObject caJson = customAudiencesJson.getJSONObject(i);
            JSONObject trustedBiddingDataJson = caJson.getJSONObject("trustedBiddingData");
            JSONArray trustedBiddingKeysJson =
                    trustedBiddingDataJson.getJSONArray("trustedBiddingKeys");
            JSONArray adsJson = caJson.getJSONArray("ads");

            ImmutableList.Builder<String> biddingKeys = ImmutableList.builder();
            for (int index = 0; index < trustedBiddingKeysJson.length(); index++) {
                biddingKeys.add(trustedBiddingKeysJson.getString(index));
            }

            ImmutableList.Builder<AdData> adDatas = ImmutableList.builder();
            for (int index = 0; index < adsJson.length(); index++) {
                JSONObject adJson = adsJson.getJSONObject(index);
                adDatas.add(
                        new AdData.Builder()
                                .setRenderUri(Uri.parse(adJson.getString("render_uri")))
                                .setMetadata(adJson.getString("metadata"))
                                .build());
            }

            customAudienceBuilder.add(
                    new CustomAudience.Builder()
                            .setBuyer(AdTechIdentifier.fromString(caJson.getString("buyer")))
                            .setName(caJson.getString("name"))
                            .setActivationTime(Instant.now())
                            .setExpirationTime(Instant.now().plus(90000, ChronoUnit.SECONDS))
                            .setDailyUpdateUri(Uri.parse(caJson.getString("dailyUpdateUri")))
                            .setUserBiddingSignals(
                                    AdSelectionSignals.fromString(
                                            caJson.getString("userBiddingSignals")))
                            .setTrustedBiddingData(
                                    new TrustedBiddingData.Builder()
                                            .setTrustedBiddingKeys(biddingKeys.build())
                                            .setTrustedBiddingUri(
                                                    Uri.parse(
                                                            trustedBiddingDataJson.getString(
                                                                    "trustedBiddingUri")))
                                            .build())
                            .setBiddingLogicUri(Uri.parse(caJson.getString("biddingLogicUri")))
                            .setAds(adDatas.build())
                            .build());
        }
        return customAudienceBuilder.build();
    }

    private AdSelectionConfig readAdSelectionConfig(String fileName) throws Exception {
        InputStream is = ApplicationProvider.getApplicationContext().getAssets().open(fileName);
        JSONObject adSelectionConfigJson = new JSONObject(new String(is.readAllBytes()));
        JSONArray buyersJson = adSelectionConfigJson.getJSONArray("custom_audience_buyers");
        JSONObject perBuyerSignalsJson = adSelectionConfigJson.getJSONObject("per_buyer_signals");
        is.close();

        ImmutableList.Builder<AdTechIdentifier> buyersBuilder = ImmutableList.builder();
        ImmutableMap.Builder<AdTechIdentifier, AdSelectionSignals> perBuyerSignals =
                ImmutableMap.builder();
        for (int i = 0; i < buyersJson.length(); i++) {
            AdTechIdentifier buyer = AdTechIdentifier.fromString(buyersJson.getString(i));
            buyersBuilder.add(buyer);
            perBuyerSignals.put(
                    buyer,
                    AdSelectionSignals.fromString(perBuyerSignalsJson.getString(buyer.toString())));
        }

        return new AdSelectionConfig.Builder()
                .setSeller(AdTechIdentifier.fromString(adSelectionConfigJson.getString("seller")))
                .setDecisionLogicUri(
                        Uri.parse(adSelectionConfigJson.getString("decision_logic_uri")))
                .setAdSelectionSignals(
                        AdSelectionSignals.fromString(
                                adSelectionConfigJson.getString("auction_signals")))
                .setSellerSignals(
                        AdSelectionSignals.fromString(
                                adSelectionConfigJson.getString("seller_signals")))
                .setTrustedScoringSignalsUri(
                        Uri.parse(adSelectionConfigJson.getString("trusted_scoring_signal_uri")))
                .setPerBuyerSignals(perBuyerSignals.build())
                .setCustomAudienceBuyers(buyersBuilder.build())
                .build();
    }

    private void joinCustomAudiences(List<CustomAudience> customAudiences) throws Exception {
        for (CustomAudience ca : customAudiences) {
            Thread.sleep(DELAY_TO_AVOID_THROTTLE);
            CUSTOM_AUDIENCE_CLIENT
                    .joinCustomAudience(ca)
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private String generateLogLabel(String testName) {
        return "SELECT_ADS_LATENCY_" + getClass().getSimpleName() + "#" + testName;
    }
}
