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

package android.adservices.debuggablects;

import static android.adservices.common.CommonFixture.INVALID_EMPTY_BUYER;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.AddAdSelectionOverrideRequest;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.adselection.SetAppInstallAdvertisersRequest;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.clients.adselection.TestAdSelectionClient;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.clients.customaudience.TestAdvertisingCustomAudienceClient;
import android.adservices.common.AdData;
import android.adservices.common.AdFilters;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.AppInstallFilters;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.AddCustomAudienceOverrideRequest;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.TrustedBiddingDataFixture;
import android.net.Uri;
import android.os.Process;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.service.PhFlagsFixture;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FledgeCtsDebuggableTest extends ForegroundDebuggableCtsTest {
    public static final String TAG = "adservices";
    // Time allowed by current test setup for APIs to respond
    private static final int API_RESPONSE_TIMEOUT_SECONDS = 120;

    // This is used to check actual API timeout conditions; note that the default overall timeout
    // for ad selection is 10 seconds
    private static final int API_RESPONSE_LONGER_TIMEOUT_SECONDS = 120;

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private static final AdTechIdentifier BUYER_1 = AdSelectionConfigFixture.BUYER_1;
    private static final AdTechIdentifier BUYER_2 = AdSelectionConfigFixture.BUYER_2;

    private static final String AD_URI_PREFIX = "/adverts/123/";

    private static final String SELLER_DECISION_LOGIC_URI_PATH = "/ssp/decision/logic/";
    private static final String BUYER_BIDDING_LOGIC_URI_PATH = "/buyer/bidding/logic/";
    private static final String SELLER_TRUSTED_SIGNAL_URI_PATH = "/kv/seller/signals/";

    private static final String SELLER_REPORTING_PATH = "/reporting/seller";
    private static final String BUYER_REPORTING_PATH = "/reporting/buyer";

    private static final String SELLER_REPORTING_URI =
            String.format("https://%s%s", AdSelectionConfigFixture.SELLER, SELLER_REPORTING_PATH);

    private static final String DEFAULT_DECISION_LOGIC_JS =
            "function scoreAd(ad, bid, auction_config, seller_signals,"
                    + " trusted_scoring_signals, contextual_signal, user_signal,"
                    + " custom_audience_signal) { \n"
                    + "  return {'status': 0, 'score': bid };\n"
                    + "}\n"
                    + "function reportResult(ad_selection_config, render_uri, bid,"
                    + " contextual_signals) { \n"
                    + " return {'status': 0, 'results': {'signals_for_buyer':"
                    + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                    + SELLER_REPORTING_URI
                    + "' } };\n"
                    + "}";
    private static final AdSelectionSignals TRUSTED_SCORING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"render_uri_1\": \"signals_for_1\",\n"
                            + "\t\"render_uri_2\": \"signals_for_2\"\n"
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
    private static final String APP_NOT_DEBUGGABLE = "App is not debuggable!";
    private static final String DEVELOPER_OPTIONS_OFF = "Developer options are off!";
    private static final AdSelectionConfig AD_SELECTION_CONFIG =
            AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                    .setCustomAudienceBuyers(Arrays.asList(BUYER_1, BUYER_2))
                    .setDecisionLogicUri(
                            Uri.parse(
                                    String.format(
                                            "https://%s%s",
                                            AdSelectionConfigFixture.SELLER,
                                            SELLER_DECISION_LOGIC_URI_PATH)))
                    .setTrustedScoringSignalsUri(
                            Uri.parse(
                                    String.format(
                                            "https://%s%s",
                                            AdSelectionConfigFixture.SELLER,
                                            SELLER_TRUSTED_SIGNAL_URI_PATH)))
                    .build();

    private static final String BUYER_2_REPORTING_URI =
            String.format("https://%s%s", AdSelectionConfigFixture.BUYER_2, BUYER_REPORTING_PATH);

    private static final String BUYER_2_BIDDING_LOGIC_JS =
            "function generateBid(ad, auction_signals, per_buyer_signals,"
                    + " trusted_bidding_signals, contextual_signals,"
                    + " custom_audience_signals) { \n"
                    + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                    + "}\n"
                    + "function reportWin(ad_selection_signals, per_buyer_signals,"
                    + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                    + " return {'status': 0, 'results': {'reporting_uri': '"
                    + BUYER_2_REPORTING_URI
                    + "' } };\n"
                    + "}";

    private static final String BUYER_2_BIDDING_LOGIC_JS_V3 =
            "function generateBid(customAudience, auction_signals, per_buyer_signals,\n"
                    + "    trusted_bidding_signals, contextual_signals) {\n"
                    + "    const ads = customAudience.ads;\n"
                    + "    let result = null;\n"
                    + "    for (const ad of ads) {\n"
                    + "        if (!result || ad.metadata.result > result.metadata.result) {\n"
                    + "            result = ad;\n"
                    + "        }\n"
                    + "    }\n"
                    + "    return { 'status': 0, 'ad': result, 'bid': result.metadata.result, "
                    + "'render': result.render_uri };\n"
                    + "}\n"
                    + "function reportWin(ad_selection_signals, per_buyer_signals,"
                    + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                    + " return {'status': 0, 'results': {'reporting_uri': '"
                    + BUYER_2_REPORTING_URI
                    + "' } };\n"
                    + "}";

    private static final String BUYER_1_REPORTING_URI =
            String.format("https://%s%s", AdSelectionConfigFixture.BUYER_1, BUYER_REPORTING_PATH);

    private static final String BUYER_1_BIDDING_LOGIC_JS =
            "function generateBid(ad, auction_signals, per_buyer_signals,"
                    + " trusted_bidding_signals, contextual_signals,"
                    + " custom_audience_signals) { \n"
                    + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                    + "}\n"
                    + "function reportWin(ad_selection_signals, per_buyer_signals,"
                    + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                    + " return {'status': 0, 'results': {'reporting_uri': '"
                    + BUYER_1_REPORTING_URI
                    + "' } };\n"
                    + "}";

    private static final String BUYER_1_BIDDING_LOGIC_JS_V3 =
            "function generateBid(customAudience, auction_signals, per_buyer_signals,\n"
                    + "    trusted_bidding_signals, contextual_signals) {\n"
                    + "    const ads = customAudience.ads;\n"
                    + "    let result = null;\n"
                    + "    for (const ad of ads) {\n"
                    + "        if (!result || ad.metadata.result > result.metadata.result) {\n"
                    + "            result = ad;\n"
                    + "        }\n"
                    + "    }\n"
                    + "    return { 'status': 0, 'ad': result, 'bid': result.metadata.result, "
                    + "'render': result.render_uri };\n"
                    + "}\n"
                    + "function reportWin(ad_selection_signals, per_buyer_signals,"
                    + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                    + " return {'status': 0, 'results': {'reporting_uri': '"
                    + BUYER_1_REPORTING_URI
                    + "' } };\n"
                    + "}";
    private AdSelectionClient mAdSelectionClient;
    private TestAdSelectionClient mTestAdSelectionClient;
    private AdvertisingCustomAudienceClient mCustomAudienceClient;
    private TestAdvertisingCustomAudienceClient mTestCustomAudienceClient;
    private DevContext mDevContext;

    private boolean mHasAccessToDevOverrides;

    private String mAccessStatus;

    @Before
    public void setup() throws InterruptedException {
        assertForegroundActivityStarted();

        mAdSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        mTestAdSelectionClient =
                new TestAdSelectionClient.Builder()
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
        DevContextFilter devContextFilter = DevContextFilter.create(sContext);
        mDevContext = DevContextFilter.create(sContext).createDevContext(Process.myUid());
        boolean isDebuggable =
                devContextFilter.isDebuggable(mDevContext.getCallingAppPackageName());
        boolean isDeveloperMode = devContextFilter.isDeveloperMode();
        mHasAccessToDevOverrides = mDevContext.getDevOptionsEnabled();
        mAccessStatus =
                String.format("Debuggable: %b\n", isDebuggable)
                        + String.format("Developer options on: %b", isDeveloperMode);

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.WRITE_DEVICE_CONFIG);

        // Enable CTS to be run with versions of WebView < M105
        PhFlagsFixture.overrideEnforceIsolateMaxHeapSize(false);
        PhFlagsFixture.overrideIsolateMaxHeapSizeBytes(0);

        // Clear the buyer list with an empty call to setAppInstallAdvertisers
        mAdSelectionClient.setAppInstallAdvertisers(
                new SetAppInstallAdvertisersRequest(Collections.EMPTY_SET));

        // TODO(b/266725238): Remove/modify once the API rate limit has been adjusted for FLEDGE
        Thread.sleep(PhFlagsFixture.DEFAULT_API_RATE_LIMIT_SLEEP_MS);
    }

    @After
    public void tearDown() throws Exception {
        mTestAdSelectionClient.resetAllAdSelectionConfigRemoteOverrides();
        mTestCustomAudienceClient.resetAllCustomAudienceOverrides();
        // Clear the buyer list with an empty call to setAppInstallAdvertisers
        mAdSelectionClient.setAppInstallAdvertisers(
                new SetAppInstallAdvertisersRequest(Collections.EMPTY_SET));
    }

    @Test
    public void testFledgeAuctionSelectionFlow_overall_Success() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_2, bidsForBuyer2);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        mCustomAudienceClient
                .joinCustomAudience(customAudience1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // TODO(b/266725238): Remove/modify once the API rate limit has been adjusted for FLEDGE
        Thread.sleep(PhFlagsFixture.DEFAULT_API_RATE_LIMIT_SLEEP_MS);

        mCustomAudienceClient
                .joinCustomAudience(customAudience2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS_V3)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_2_BIDDING_LOGIC_JS_V3)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 2 is rendered, since it had the highest bid and score
        Assert.assertEquals(
                CommonFixture.getUri(BUYER_2, AD_URI_PREFIX + "/ad3"), outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testFledgeFlow_manuallyUpdateCustomAudience_Success() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer = ImmutableList.of(1.1, 2.2);
        List<Double> updatedBidsForBuyer = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience = createCustomAudience(BUYER_1, bidsForBuyer);
        CustomAudience customAudienceUpdate = createCustomAudience(BUYER_1, updatedBidsForBuyer);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception.
        mCustomAudienceClient
                .joinCustomAudience(customAudience)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // TODO(b/266725238): Remove/modify once the API rate limit has been adjusted for FLEDGE
        Thread.sleep(PhFlagsFixture.DEFAULT_API_RATE_LIMIT_SLEEP_MS);

        mCustomAudienceClient
                .joinCustomAudience(customAudienceUpdate)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception.
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience.getBuyer())
                        .setName(customAudience.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS_V3)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception.
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 1 is rendered, since it had the highest bid and score
        // This verifies that the custom audience was updated, since it originally only had two ads
        Assert.assertEquals(
                CommonFixture.getUri(BUYER_1, AD_URI_PREFIX + "/ad3"), outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testAdSelection_etldViolation_failure() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_2, bidsForBuyer2);

        AdSelectionConfig adSelectionConfigWithEtldViolations =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(Arrays.asList(BUYER_1, BUYER_2))
                        .setDecisionLogicUri(
                                Uri.parse(
                                        String.format(
                                                "https://%s%s",
                                                AdSelectionConfigFixture.SELLER + "etld_noise",
                                                SELLER_DECISION_LOGIC_URI_PATH)))
                        .setTrustedScoringSignalsUri(
                                Uri.parse(
                                        String.format(
                                                "https://%s%s",
                                                AdSelectionConfigFixture.SELLER + "etld_noise",
                                                SELLER_TRUSTED_SIGNAL_URI_PATH)))
                        .build();

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        mCustomAudienceClient
                .joinCustomAudience(customAudience1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // TODO(b/266725238): Remove/modify once the API rate limit has been adjusted for FLEDGE
        Thread.sleep(PhFlagsFixture.DEFAULT_API_RATE_LIMIT_SLEEP_MS);

        mCustomAudienceClient
                .joinCustomAudience(customAudience2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        adSelectionConfigWithEtldViolations,
                        DEFAULT_DECISION_LOGIC_JS,
                        TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_2_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Running ad selection and asserting that exception is thrown when decision and signals
        // URIs are not etld+1 compliant
        Exception selectAdsException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mAdSelectionClient
                                        .selectAds(adSelectionConfigWithEtldViolations)
                                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertThat(selectAdsException.getCause()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testReportImpression_etldViolation_failure() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_2, bidsForBuyer2);

        AdSelectionConfig adSelectionConfigWithEtldViolations =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(Arrays.asList(BUYER_1, BUYER_2))
                        .setDecisionLogicUri(
                                Uri.parse(
                                        String.format(
                                                "https://%s%s",
                                                AdSelectionConfigFixture.SELLER + "etld_noise",
                                                SELLER_DECISION_LOGIC_URI_PATH)))
                        .setTrustedScoringSignalsUri(
                                Uri.parse(
                                        String.format(
                                                "https://%s%s",
                                                AdSelectionConfigFixture.SELLER + "etld_noise",
                                                SELLER_TRUSTED_SIGNAL_URI_PATH)))
                        .build();

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        mCustomAudienceClient
                .joinCustomAudience(customAudience1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // TODO(b/266725238): Remove/modify once the API rate limit has been adjusted for FLEDGE
        Thread.sleep(PhFlagsFixture.DEFAULT_API_RATE_LIMIT_SLEEP_MS);

        mCustomAudienceClient
                .joinCustomAudience(customAudience2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS_V3)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_2_BIDDING_LOGIC_JS_V3)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 2 is rendered, since it had the highest bid and score
        Assert.assertEquals(
                CommonFixture.getUri(BUYER_2, AD_URI_PREFIX + "/ad3"), outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(
                        outcome.getAdSelectionId(), adSelectionConfigWithEtldViolations);

        // Running report Impression and asserting that exception is thrown when decision and
        // signals URIs are not etld+1 compliant
        Exception selectAdsException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mAdSelectionClient
                                        .reportImpression(reportImpressionRequest)
                                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertThat(selectAdsException.getCause()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testAdSelection_skipAdsMalformedBiddingLogic_success() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_2, bidsForBuyer2);

        String malformedBiddingLogic = " This is an invalid javascript";

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        mCustomAudienceClient
                .joinCustomAudience(customAudience1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // TODO(b/266725238): Remove/modify once the API rate limit has been adjusted for FLEDGE
        Thread.sleep(PhFlagsFixture.DEFAULT_API_RATE_LIMIT_SLEEP_MS);

        mCustomAudienceClient
                .joinCustomAudience(customAudience2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS_V3)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(malformedBiddingLogic)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 2 is skipped despite having the highest bid, since it has
        // malformed bidding logic
        // The winner should come from buyer1 with the highest bid i.e. ad2
        Assert.assertEquals(
                CommonFixture.getUri(BUYER_1, AD_URI_PREFIX + "/ad2"), outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testAdSelection_malformedScoringLogic_failure() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_2, bidsForBuyer2);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        mCustomAudienceClient
                .joinCustomAudience(customAudience1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // TODO(b/266725238): Remove/modify once the API rate limit has been adjusted for FLEDGE
        Thread.sleep(PhFlagsFixture.DEFAULT_API_RATE_LIMIT_SLEEP_MS);

        mCustomAudienceClient
                .joinCustomAudience(customAudience2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        String malformedScoringLogic = " This is an invalid javascript";

        // Adding malformed scoring logic AdSelection override, no result to do assertion on.
        // Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, malformedScoringLogic, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_2_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Ad Selection will fail due to scoring logic malformed
        Exception selectAdsException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mAdSelectionClient
                                        .selectAds(AD_SELECTION_CONFIG)
                                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertThat(selectAdsException.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testAdSelection_skipAdsFailedGettingBiddingLogic_success() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_2, bidsForBuyer2);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        mCustomAudienceClient
                .joinCustomAudience(customAudience1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // TODO(b/266725238): Remove/modify once the API rate limit has been adjusted for FLEDGE
        Thread.sleep(PhFlagsFixture.DEFAULT_API_RATE_LIMIT_SLEEP_MS);

        mCustomAudienceClient
                .joinCustomAudience(customAudience2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS_V3)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        // We do not provide override for CA 2, that should lead to failure to get biddingLogic

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 2 is skipped despite having the highest bid, since it has
        // missing bidding logic
        // The winner should come from buyer1 with the highest bid i.e. ad2
        Assert.assertEquals(
                CommonFixture.getUri(BUYER_1, AD_URI_PREFIX + "/ad2"), outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testAdSelection_errorGettingScoringLogic_failure() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_2, bidsForBuyer2);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        mCustomAudienceClient
                .joinCustomAudience(customAudience1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // TODO(b/266725238): Remove/modify once the API rate limit has been adjusted for FLEDGE
        Thread.sleep(PhFlagsFixture.DEFAULT_API_RATE_LIMIT_SLEEP_MS);

        mCustomAudienceClient
                .joinCustomAudience(customAudience2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Skip adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_2_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Ad Selection will fail due to scoring logic not found, because the URI that is used to
        // fetch scoring logic does not exist
        Exception selectAdsException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mAdSelectionClient
                                        .selectAds(AD_SELECTION_CONFIG)
                                        .get(
                                                API_RESPONSE_LONGER_TIMEOUT_SECONDS,
                                                TimeUnit.SECONDS));
        // Sometimes a 400 status code is returned (ISE) instead of the network fetch timing out
        assertThat(
                        selectAdsException.getCause() instanceof TimeoutException
                                || selectAdsException.getCause() instanceof IllegalStateException)
                .isTrue();
    }

    @Test
    public void testAdSelectionFlow_skipNonActivatedCA_Success() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        // CA 2 activated long in the future
        CustomAudience customAudience2 =
                createCustomAudience(
                        BUYER_2,
                        bidsForBuyer2,
                        CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME,
                        CustomAudienceFixture.VALID_DELAYED_EXPIRATION_TIME);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        mCustomAudienceClient
                .joinCustomAudience(customAudience1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // TODO(b/266725238): Remove/modify once the API rate limit has been adjusted for FLEDGE
        Thread.sleep(PhFlagsFixture.DEFAULT_API_RATE_LIMIT_SLEEP_MS);

        mCustomAudienceClient
                .joinCustomAudience(customAudience2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS_V3)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_2_BIDDING_LOGIC_JS_V3)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 2 is skipped despite having the highest bid, since it is
        // not activated yet
        // The winner should come from buyer1 with the highest bid i.e. ad2
        Assert.assertEquals(
                CommonFixture.getUri(BUYER_1, AD_URI_PREFIX + "/ad2"), outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testAdSelectionFlow_skipExpiredCA_Success() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        int caTimeToExpireSeconds = 2;
        // Since we cannot create CA which is already expired, we create one which expires in few
        // seconds
        // We will then wait till this CA expires before we run Ad Selection
        CustomAudience customAudience2 =
                createCustomAudience(
                        BUYER_2,
                        bidsForBuyer2,
                        CustomAudienceFixture.VALID_ACTIVATION_TIME,
                        Instant.now().plusSeconds(caTimeToExpireSeconds));

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        mCustomAudienceClient
                .joinCustomAudience(customAudience1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // TODO(b/266725238): Remove/modify once the API rate limit has been adjusted for FLEDGE
        Thread.sleep(PhFlagsFixture.DEFAULT_API_RATE_LIMIT_SLEEP_MS);

        mCustomAudienceClient
                .joinCustomAudience(customAudience2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS_V3)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_2_BIDDING_LOGIC_JS_V3)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Wait to ensure that CA2 gets expired
        Thread.sleep(caTimeToExpireSeconds * 2 * 1000);

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 2 is skipped despite having the highest bid, since it is
        // expired
        // The winner should come from buyer1 with the highest bid i.e. ad2
        Assert.assertEquals(
                CommonFixture.getUri(BUYER_1, AD_URI_PREFIX + "/ad2"), outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testAdSelectionFlow_skipCAsThatTimeoutDuringBidding_Success() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);
        CustomAudience customAudience2 = this.createCustomAudience(BUYER_2, bidsForBuyer2);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        mCustomAudienceClient
                .joinCustomAudience(customAudience1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // TODO(b/266725238): Remove/modify once the API rate limit has been adjusted for FLEDGE
        Thread.sleep(PhFlagsFixture.DEFAULT_API_RATE_LIMIT_SLEEP_MS);

        mCustomAudienceClient
                .joinCustomAudience(customAudience2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        String jsWaitMoreThanAllowedForBiddingPerCa = insertJsWait(5000);
        String readBidFromAdMetadataWithDelayJs =
                "function generateBid(customAudience, auction_signals, per_buyer_signals,\n"
                        + "    trusted_bidding_signals, contextual_signals) {\n"
                        + "    const ads = customAudience.ads;\n"
                        + "    let result = null;\n"
                        + "    for (const ad of ads) {\n"
                        + "        if (!result || ad.metadata.result > result.metadata.result) {\n"
                        + "            result = ad;\n"
                        + "        }\n"
                        + "    }\n"
                        + jsWaitMoreThanAllowedForBiddingPerCa
                        + "    return { 'status': 0, 'ad': result, 'bid': result.metadata.result, "
                        + "'render': result.render_uri };\n"
                        + "}\n";

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS_V3)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(readBidFromAdMetadataWithDelayJs)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_LONGER_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 2 is skipped despite having the highest bid, since it
        // timed out
        // The winner should come from buyer1 with the highest bid i.e. ad2
        Assert.assertEquals(
                CommonFixture.getUri(BUYER_1, AD_URI_PREFIX + "/ad2"), outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testAdSelection_overallTimeout_Failure() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_2, bidsForBuyer2);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        mCustomAudienceClient
                .joinCustomAudience(customAudience1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // TODO(b/266725238): Remove/modify once the API rate limit has been adjusted for FLEDGE
        Thread.sleep(PhFlagsFixture.DEFAULT_API_RATE_LIMIT_SLEEP_MS);

        mCustomAudienceClient
                .joinCustomAudience(customAudience2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        String jsWaitMoreThanAllowedForScoring = insertJsWait(10000);
        String useBidAsScoringWithDelayJs =
                "function scoreAd(ad, bid, auction_config, seller_signals, "
                        + "trusted_scoring_signals, contextual_signal, user_signal, "
                        + "custom_audience_signal) { \n"
                        + jsWaitMoreThanAllowedForScoring
                        + "  return {'status': 0, 'score': bid };\n"
                        + "}";

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, useBidAsScoringWithDelayJs, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_2_BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        Exception selectAdsException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mAdSelectionClient
                                        .selectAds(AD_SELECTION_CONFIG)
                                        .get(
                                                API_RESPONSE_LONGER_TIMEOUT_SECONDS,
                                                TimeUnit.SECONDS));
        assertThat(selectAdsException.getCause()).isInstanceOf(TimeoutException.class);
    }

    @Ignore
    @Test
    public void testFledgeAuctionAppFilteringFlow_overall_Success() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);
        PhFlagsFixture.overrideFledgeAdSelectionFilteringEnabled(true);

        // Allow BUYER_2 to filter on the test package
        SetAppInstallAdvertisersRequest request =
                new SetAppInstallAdvertisersRequest(new HashSet<>(Arrays.asList(BUYER_2)));
        ListenableFuture<Void> appInstallFuture =
                mAdSelectionClient.setAppInstallAdvertisers(request);
        assertNull(appInstallFuture.get());

        // Run the auction with the ads that should be filtered
        String packageName = sContext.getPackageName();
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        List<AdData> adsForBuyer2 = new ArrayList<>();
        // Create ads with the buyer name and bid number as the ad URI
        // Add the bid value to the metadata and add filters to the adss
        for (int i = 0; i < bidsForBuyer2.size(); i++) {
            adsForBuyer2.add(
                    new AdData.Builder()
                            .setRenderUri(
                                    CommonFixture.getUri(BUYER_2, AD_URI_PREFIX + "/ad" + (i + 1)))
                            .setMetadata("{\"result\":" + bidsForBuyer2.get(i) + "}")
                            .setAdFilters(
                                    new AdFilters.Builder()
                                            .setAppInstallFilters(
                                                    new AppInstallFilters.Builder()
                                                            .setPackageNames(
                                                                    new HashSet<>(
                                                                            Arrays.asList(
                                                                                    packageName)))
                                                            .build())
                                            .build())
                            .build());
        }

        CustomAudience customAudience2 =
                new CustomAudience.Builder()
                        .setBuyer(BUYER_2)
                        .setName(BUYER_2 + CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setDailyUpdateUri(
                                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2))
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .setTrustedBiddingData(
                                TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                        BUYER_2))
                        .setBiddingLogicUri(
                                CommonFixture.getUri(BUYER_2, BUYER_BIDDING_LOGIC_URI_PATH))
                        .setAds(adsForBuyer2)
                        .build();

        // TODO(b/266725238): Remove/modify once the API rate limit has been adjusted for FLEDGE
        Thread.sleep(PhFlagsFixture.DEFAULT_API_RATE_LIMIT_SLEEP_MS);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        mCustomAudienceClient
                .joinCustomAudience(customAudience1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // TODO(b/266725238): Remove/modify once the API rate limit has been adjusted for FLEDGE
        Thread.sleep(PhFlagsFixture.DEFAULT_API_RATE_LIMIT_SLEEP_MS);

        mCustomAudienceClient
                .joinCustomAudience(customAudience2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS_V3)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS_V3)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 1 is rendered, since had the highest unfiltered score
        Assert.assertEquals(
                CommonFixture.getUri(BUYER_1, AD_URI_PREFIX + "/ad2"), outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Ignore
    @Test
    public void testFledgeAuctionAppFilteringFlow_overall_AppInstallFailure() throws Exception {
        /**
         * In this test, we give bad input to setAppInstallAdvertisers and ensure that it gives an
         * error, and does not filter based on the good input
         */
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);
        PhFlagsFixture.overrideFledgeAdSelectionFilteringEnabled(true);

        // Allow BUYER_2 to filter on the test package
        SetAppInstallAdvertisersRequest request =
                new SetAppInstallAdvertisersRequest(
                        new HashSet<>(Arrays.asList(BUYER_2, INVALID_EMPTY_BUYER)));
        mAdSelectionClient.setAppInstallAdvertisers(request);
        ListenableFuture<Void> appInstallFuture =
                mAdSelectionClient.setAppInstallAdvertisers(request);
        assertThrows(ExecutionException.class, appInstallFuture::get);

        // Run the auction with the ads that should be filtered
        String packageName = sContext.getPackageName();
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        List<AdData> adsForBuyer2 = new ArrayList<>();
        // Create ads with the buyer name and bid number as the ad URI
        // Add the bid value to the metadata and add filters to the adss
        for (int i = 0; i < bidsForBuyer2.size(); i++) {
            adsForBuyer2.add(
                    new AdData.Builder()
                            .setRenderUri(
                                    CommonFixture.getUri(BUYER_2, AD_URI_PREFIX + "/ad" + (i + 1)))
                            .setMetadata("{\"result\":" + bidsForBuyer2.get(i) + "}")
                            .setAdFilters(
                                    new AdFilters.Builder()
                                            .setAppInstallFilters(
                                                    new AppInstallFilters.Builder()
                                                            .setPackageNames(
                                                                    new HashSet<>(
                                                                            Arrays.asList(
                                                                                    packageName)))
                                                            .build())
                                            .build())
                            .build());
        }

        CustomAudience customAudience2 =
                new CustomAudience.Builder()
                        .setBuyer(BUYER_2)
                        .setName(BUYER_2 + CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setDailyUpdateUri(
                                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2))
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .setTrustedBiddingData(
                                TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                        BUYER_2))
                        .setBiddingLogicUri(
                                CommonFixture.getUri(BUYER_2, BUYER_BIDDING_LOGIC_URI_PATH))
                        .setAds(adsForBuyer2)
                        .build();

        // TODO(b/266725238): Remove/modify once the API rate limit has been adjusted for FLEDGE
        Thread.sleep(PhFlagsFixture.DEFAULT_API_RATE_LIMIT_SLEEP_MS);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        mCustomAudienceClient
                .joinCustomAudience(customAudience1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // TODO(b/266725238): Remove/modify once the API rate limit has been adjusted for FLEDGE
        Thread.sleep(PhFlagsFixture.DEFAULT_API_RATE_LIMIT_SLEEP_MS);

        mCustomAudienceClient
                .joinCustomAudience(customAudience2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Adding AdSelection override, no result to do assertion on. Failures will generate an
        // exception."
        AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DEFAULT_DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS_V3)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience2.getBuyer())
                        .setName(customAudience2.getName())
                        .setBiddingLogicJs(BUYER_1_BIDDING_LOGIC_JS_V3)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override, no result to do assertion on. Failures will generate an
        // exception."
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(AD_SELECTION_CONFIG)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 1 is rendered, since had the highest unfiltered score
        Assert.assertEquals(
                CommonFixture.getUri(BUYER_2, AD_URI_PREFIX + "/ad3"), outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), AD_SELECTION_CONFIG);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private String insertJsWait(long waitTime) {
        return "    const wait = (ms) => {\n"
                + "       var start = new Date().getTime();\n"
                + "       var end = start;\n"
                + "       while(end < start + ms) {\n"
                + "         end = new Date().getTime();\n"
                + "      }\n"
                + "    }\n"
                + String.format("    wait(\"%d\");\n", waitTime);
    }

    /**
     * @param buyer The name of the buyer for this Custom Audience
     * @param bids these bids, are added to its metadata. Our JS logic then picks this value and
     *     creates ad with the provided value as bid
     * @return a real Custom Audience object that can be persisted and used in bidding and scoring
     */
    private CustomAudience createCustomAudience(final AdTechIdentifier buyer, List<Double> bids) {
        return createCustomAudience(
                buyer,
                bids,
                CustomAudienceFixture.VALID_ACTIVATION_TIME,
                CustomAudienceFixture.VALID_EXPIRATION_TIME);
    }

    private CustomAudience createCustomAudience(
            final AdTechIdentifier buyer,
            List<Double> bids,
            Instant activationTime,
            Instant expirationTime) {
        // Generate ads for with bids provided
        List<AdData> ads = new ArrayList<>();

        // Create ads with the buyer name and bid number as the ad URI
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            ads.add(
                    new AdData.Builder()
                            .setRenderUri(
                                    CommonFixture.getUri(buyer, AD_URI_PREFIX + "/ad" + (i + 1)))
                            .setMetadata("{\"result\":" + bids.get(i) + "}")
                            .build());
        }

        return new CustomAudience.Builder()
                .setBuyer(buyer)
                .setName(buyer + CustomAudienceFixture.VALID_NAME)
                .setActivationTime(activationTime)
                .setExpirationTime(expirationTime)
                .setDailyUpdateUri(CustomAudienceFixture.getValidDailyUpdateUriByBuyer(buyer))
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(buyer))
                .setBiddingLogicUri(CommonFixture.getUri(buyer, BUYER_BIDDING_LOGIC_URI_PATH))
                .setAds(ads)
                .build();
    }

    private AdSelectionOutcome runAdSelectionAsPreSteps(
            double bid, AdTechIdentifier buyer, String buyerDecisionLogic) throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        List<Double> bidsForBuyer = ImmutableList.of(bid);
        CustomAudience customAudience1 = createCustomAudience(buyer, bidsForBuyer);

        // Joining custom audiences
        mCustomAudienceClient
                .joinCustomAudience(customAudience1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(customAudience1.getBuyer())
                        .setName(customAudience1.getName())
                        .setBiddingLogicJs(buyerDecisionLogic)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        // Adding Custom audience override
        mTestCustomAudienceClient
                .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        AdSelectionConfig adSelectionConfigBuyer1 =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setDecisionLogicUri(
                                Uri.parse(
                                        String.format(
                                                "https://%s%s",
                                                AdSelectionConfigFixture.SELLER,
                                                SELLER_DECISION_LOGIC_URI_PATH)))
                        .setTrustedScoringSignalsUri(
                                Uri.parse(
                                        String.format(
                                                "https://%s%s",
                                                AdSelectionConfigFixture.SELLER,
                                                SELLER_TRUSTED_SIGNAL_URI_PATH)))
                        .setCustomAudienceBuyers(Collections.singletonList(buyer))
                        .build();

        // Adding AdSelection override
        mTestAdSelectionClient
                .overrideAdSelectionConfigRemoteInfo(
                        new AddAdSelectionOverrideRequest(
                                adSelectionConfigBuyer1,
                                DEFAULT_DECISION_LOGIC_JS,
                                TRUSTED_SCORING_SIGNALS))
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        return mAdSelectionClient
                .selectAds(adSelectionConfigBuyer1)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
