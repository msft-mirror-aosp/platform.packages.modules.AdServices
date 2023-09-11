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

package android.adservices.debuggablects;

import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER;
import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.ReportEventRequest;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.FetchAndJoinCustomAudienceRequest;
import android.adservices.customaudience.JoinCustomAudienceRequest;
import android.adservices.customaudience.TrustedBiddingData;
import android.adservices.utils.MockWebServerRule;
import android.adservices.utils.ScenarioDispatcher;
import android.adservices.utils.Scenarios;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.CompatAdServicesTestUtils;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.compatibility.common.util.ShellUtils;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.mockwebserver.MockWebServer;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AdSelectionTest extends ForegroundDebuggableCtsTest {
    private static final String TAG = "android.adservices.debuggablects";
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final int TIMEOUT = 120;
    private static final int NUM_ADS_PER_AUDIENCE = 4;
    private static final String SHOES_CA = "shoes";
    private static final String SHIRTS_CA = "shirts";
    private static final String PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private AdvertisingCustomAudienceClient mCustomAudienceClient;
    private AdSelectionClient mAdSelectionClient;
    private String mServerBaseAddress;
    private AdTechIdentifier mAdTechIdentifier;
    private MockWebServer mMockWebServer;

    @Rule
    public MockWebServerRule mMockWebServerRule =
            MockWebServerRule.forHttps(
                    CONTEXT, "adservices_untrusted_test_server.p12", "adservices_test");

    // Prefix added to all requests to bust cache.
    private int mCacheBuster;
    private final Random mCacheBusterRandom = new Random();
    private String mPreviousAppAllowList;

    @Before
    public void setUp() throws Exception {
        // Skip the test if it runs on unsupported platforms
        Assume.assumeTrue(AdservicesTestHelper.isDeviceSupported());
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        if (SdkLevel.isAtLeastT()) {
            assertForegroundActivityStarted();
        } else {
            mPreviousAppAllowList =
                    CompatAdServicesTestUtils.getAndOverridePpapiAppAllowList(
                            sContext.getPackageName());
            CompatAdServicesTestUtils.setFlags();
        }

        AdservicesTestHelper.killAdservicesProcess(sContext);
        ExecutorService executor = Executors.newCachedThreadPool();
        mCustomAudienceClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(CONTEXT)
                        .setExecutor(executor)
                        .build();
        mAdSelectionClient =
                new AdSelectionClient.Builder().setContext(CONTEXT).setExecutor(executor).build();
        mCacheBuster = mCacheBusterRandom.nextInt();
    }

    @After
    public void tearDown() throws IOException {
        if (mMockWebServer != null) {
            mMockWebServer.shutdown();
        }
        if (!AdservicesTestHelper.isDeviceSupported()) {
            return;
        }
        if (!SdkLevel.isAtLeastT()) {
            CompatAdServicesTestUtils.setPpapiAppAllowList(mPreviousAppAllowList);
            CompatAdServicesTestUtils.resetFlagsToDefault();
        }
    }

    /**
     * End-to-end test for ad selection.
     *
     * <p>Covers the following Remarketing CUJs:
     *
     * <ul>
     *   <li><b>001</b>: A buyer can provide bidding logic using JS
     *   <li><b>002</b>: A seller can provide scoring logic using JS
     *   <li><b>035</b>: A buyer can provide the trusted signals to be used during ad selection
     * </ul>
     */
    @Test
    public void testAdSelection_withBiddingAndScoringLogic_happyPath() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-default.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();

        try {
            joinCustomAudience(SHIRTS_CA);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            assertThat(result.hasOutcome()).isTrue();
        } finally {
            leaveCustomAudience(SHIRTS_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /**
     * End-to-end test for report impression.
     *
     * <p>Covers the following Remarketing CUJs:
     *
     * <ul>
     *   <li><b>007</b>: As a buyer/seller I will receive a notification of impression for a winning
     *       ad on an URL I can return from a script I can provide
     * </ul>
     */
    @Test
    public void testAdSelection_withReportImpression_happyPath() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-reportimpression.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();

        try {
            joinCustomAudience(SHOES_CA);
            doReportImpression(
                    doSelectAds(adSelectionConfig).getAdSelectionId(), adSelectionConfig);
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /**
     * Test for ad selection with V3 bidding logic.
     *
     * <p>Covers the following Remarketing CUJs:
     *
     * <ul>
     *   <li><b>119</b>: A ad selection can be run with V3 bidding logic without override
     * </ul>
     */
    @Test
    public void testAdSelection_withBiddingLogicV3_happyPath() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-119.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();

        try {
            joinCustomAudience(SHOES_CA);
            overrideBiddingLogicVersionToV3(true);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            assertThat(result.hasOutcome()).isTrue();
            assertThat(result.getRenderUri()).isNotNull();
        } finally {
            overrideBiddingLogicVersionToV3(false);
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /**
     * Test that buyers can specify an adCost in generateBid that is found in the buyer impression
     * reporting URI (Remarketing CUJ 160).
     */
    @Test
    public void testAdSelection_withAdCostInUrl_happyPath() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-160.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();
        long adSelectionId;

        try {
            overrideCpcBillingEnabled(true);
            joinCustomAudience(SHOES_CA);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            adSelectionId = result.getAdSelectionId();
            assertThat(result.hasOutcome()).isTrue();
            assertThat(result.getRenderUri()).isNotNull();
        } finally {
            overrideCpcBillingEnabled(false);
            leaveCustomAudience(SHOES_CA);
        }
        doReportImpression(adSelectionId, adSelectionConfig);

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /**
     * Test that buyers can specify an adCost in generateBid that reported (Remarketing CUJ 161).
     */
    @Test
    public void testAdSelection_withAdCostInUrl_adCostIsReported() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-161.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();
        long adSelectionId;

        try {
            overrideRegisterAdBeaconEnabled(true);
            overrideCpcBillingEnabled(true);
            joinCustomAudience(SHOES_CA);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            adSelectionId = result.getAdSelectionId();
            doReportImpression(adSelectionId, adSelectionConfig);
            doReportEvent(adSelectionId, "click");
        } finally {
            overrideRegisterAdBeaconEnabled(false);
            overrideCpcBillingEnabled(false);
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /**
     * Test that custom audience can be successfully fetched from a server and joined to participate
     * in a successful ad selection (Remarketing CUJ 169).
     */
    @Test
    public void testAdSelection_withFetchCustomAudience_fetchesAndReturnsSuccessfully()
            throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-fetchCA.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();
        String customAudienceName = "hats";

        try {
            CustomAudience customAudience = makeCustomAudience(customAudienceName);
            ShellUtils.runShellCommand(
                    "device_config put adservices fledge_fetch_custom_audience_enabled true");
            mCustomAudienceClient
                    .fetchAndJoinCustomAudience(
                            new FetchAndJoinCustomAudienceRequest.Builder(
                                            Uri.parse(
                                                    getServerBaseAddress()
                                                            + Scenarios.FETCH_CA_PATH))
                                    .setActivationTime(customAudience.getActivationTime())
                                    .setExpirationTime(customAudience.getExpirationTime())
                                    .setName(customAudience.getName())
                                    .setUserBiddingSignals(customAudience.getUserBiddingSignals())
                                    .build())
                    .get(5, TimeUnit.SECONDS);
            Log.d(TAG, "Joined Custom Audience: " + customAudienceName);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            assertThat(result.hasOutcome()).isTrue();
            assertThat(result.getRenderUri()).isNotNull();
        } finally {
            ShellUtils.runShellCommand(
                    "device_config put adservices fledge_fetch_custom_audience_enabled false");
            leaveCustomAudience(customAudienceName);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /** Test that ad selection fails with an expired custom audience. */
    @Test
    public void testAdSelection_withShortlyExpiringCustomAudience_selectAdsThrowsException()
            throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-default.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        CustomAudience template = makeCustomAudience(SHOES_CA);
        CustomAudience customAudience =
                new CustomAudience.Builder()
                        .setName(template.getName())
                        .setBuyer(template.getBuyer())
                        .setBiddingLogicUri(template.getBiddingLogicUri())
                        .setActivationTime(template.getActivationTime())
                        .setExpirationTime(Instant.now().plus(1, ChronoUnit.SECONDS))
                        .setDailyUpdateUri(template.getDailyUpdateUri())
                        .setUserBiddingSignals(template.getUserBiddingSignals())
                        .setTrustedBiddingData(template.getTrustedBiddingData())
                        .setAds(template.getAds())
                        .build();
        AdSelectionConfig config = makeAdSelectionConfig();

        mCustomAudienceClient.joinCustomAudience(customAudience).get(1, TimeUnit.SECONDS);
        Log.d(TAG, "Joined custom audience");
        // Make a call to verify ad selection succeeds before timing out.
        mAdSelectionClient.selectAds(config).get(TIMEOUT, TimeUnit.SECONDS);
        Thread.sleep(4000);

        Exception selectAdsException =
                assertThrows(
                        ExecutionException.class,
                        () -> mAdSelectionClient.selectAds(config).get(TIMEOUT, TimeUnit.SECONDS));
        assertThat(selectAdsException.getCause()).isInstanceOf(IllegalStateException.class);
    }

    /**
     * Test that not providing any ad selection Ids to selectAds with ad selection outcomes should
     * result in failure (Remarketing CUJ 071).
     */
    @Test
    public void testAdSelectionOutcomes_withNoAdSelectionId_throwsException() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-default.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionFromOutcomesConfig config =
                new AdSelectionFromOutcomesConfig.Builder()
                        .setSeller(mAdTechIdentifier)
                        .setAdSelectionIds(List.of())
                        .setSelectionLogicUri(
                                Uri.parse(getServerBaseAddress() + Scenarios.MEDIATION_LOGIC_PATH))
                        .setSelectionSignals(makeAdSelectionSignals())
                        .build();

        try {
            Exception selectAdsException =
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                    mAdSelectionClient
                                            .selectAds(config)
                                            .get(TIMEOUT, TimeUnit.SECONDS));
            assertThat(selectAdsException.getCause()).isInstanceOf(IllegalArgumentException.class);
        } finally {
            leaveCustomAudience(SHIRTS_CA);
        }
    }

    /** Test that buyer and seller receive win and loss debug reports (Remarketing CUJ 164). */
    @Test
    public void testAdSelection_withDebugReporting_happyPath() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-164.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();

        try {
            joinCustomAudience(SHOES_CA);
            setDebugReportingEnabledForTesting(true);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            assertThat(result.hasOutcome()).isTrue();
        } finally {
            setDebugReportingEnabledForTesting(false);
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /**
     * Test that buyer and seller do not receive win and loss debug reports if the feature is
     * disabled (Remarketing CUJ 165).
     */
    @Test
    public void testAdSelection_withDebugReportingDisabled_doesNotSend() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-165.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();

        try {
            joinCustomAudience(SHOES_CA);
            overrideBiddingLogicVersionToV3(true);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            assertThat(result.hasOutcome()).isTrue();
        } finally {
            overrideBiddingLogicVersionToV3(false);
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /**
     * Test that buyer and seller receive win and loss debug reports with reject reason (Remarketing
     * CUJ 170).
     */
    @Test
    public void testAdSelection_withDebugReportingAndRejectReason_happyPath() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-170.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();

        try {
            joinCustomAudience(SHOES_CA);
            joinCustomAudience(SHIRTS_CA);
            setDebugReportingEnabledForTesting(true);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            assertThat(result.hasOutcome()).isTrue();
        } finally {
            setDebugReportingEnabledForTesting(false);
            leaveCustomAudience(SHOES_CA);
            leaveCustomAudience(SHIRTS_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    private AdSelectionOutcome doSelectAds(AdSelectionConfig adSelectionConfig)
            throws ExecutionException, InterruptedException, TimeoutException {
        AdSelectionOutcome result =
                mAdSelectionClient.selectAds(adSelectionConfig).get(TIMEOUT, TimeUnit.SECONDS);
        Log.d(TAG, "Ran ad selection.");
        return result;
    }

    private void doReportEvent(long adSelectionId, String eventName)
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        mAdSelectionClient
                .reportEvent(
                        new ReportEventRequest.Builder(
                                        adSelectionId,
                                        eventName,
                                        new JSONObject().put("key", "value").toString(),
                                        FLAG_REPORTING_DESTINATION_SELLER
                                                | FLAG_REPORTING_DESTINATION_BUYER)
                                .build())
                .get(TIMEOUT, TimeUnit.SECONDS);
        Log.d(TAG, "Ran report ad click for ad selection id: " + adSelectionId);
    }

    private void doReportImpression(long adSelectionId, AdSelectionConfig adSelectionConfig)
            throws ExecutionException, InterruptedException, TimeoutException {
        mAdSelectionClient
                .reportImpression(new ReportImpressionRequest(adSelectionId, adSelectionConfig))
                .get(TIMEOUT, TimeUnit.SECONDS);
        Log.d(TAG, "Ran report impression for ad selection id: " + adSelectionId);
    }

    private void joinCustomAudience(String customAudienceName)
            throws ExecutionException, InterruptedException, TimeoutException {
        JoinCustomAudienceRequest joinCustomAudienceRequest =
                makeJoinCustomAudienceRequest(customAudienceName);
        mCustomAudienceClient
                .joinCustomAudience(joinCustomAudienceRequest.getCustomAudience())
                .get(5, TimeUnit.SECONDS);
        Log.d(TAG, "Joined Custom Audience: " + customAudienceName);
    }

    private void leaveCustomAudience(String customAudienceName)
            throws ExecutionException, InterruptedException, TimeoutException {
        CustomAudience customAudience = makeCustomAudience(customAudienceName);
        mCustomAudienceClient
                .leaveCustomAudience(customAudience.getBuyer(), customAudience.getName())
                .get(TIMEOUT, TimeUnit.SECONDS);
        Log.d(TAG, "Left Custom Audience: " + customAudienceName);
    }

    private String getServerBaseAddress() {
        return String.format(
                "https://%s:%s%s/",
                mMockWebServer.getHostName(), mMockWebServer.getPort(), getCacheBusterPrefix());
    }

    private void overrideCpcBillingEnabled(boolean enabled) {
        ShellUtils.runShellCommand(
                String.format(
                        "device_config put adservices fledge_cpc_billing_enabled %s",
                        enabled ? "true" : "false"));
    }

    private void overrideRegisterAdBeaconEnabled(boolean enabled) {
        ShellUtils.runShellCommand(
                String.format(
                        "device_config put adservices fledge_register_ad_beacon_enabled %s",
                        enabled ? "true" : "false"));
    }

    private void setDebugReportingEnabledForTesting(boolean enabled) {
        overrideBiddingLogicVersionToV3(enabled);
        ShellUtils.runShellCommand(
                String.format(
                        "device_config put adservices fledge_event_level_debug_reporting_enabled"
                                + " %s",
                        enabled ? "true" : "false"));
        ShellUtils.runShellCommand(
                String.format(
                        "device_config put adservices"
                                + " fledge_event_level_debug_report_send_immediately %s",
                        enabled ? "true" : "false"));
    }

    private static void overrideBiddingLogicVersionToV3(boolean useVersion3) {
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_ad_selection_bidding_logic_js_version %s",
                useVersion3 ? "3" : "2");
    }

    private AdSelectionConfig makeAdSelectionConfig() {
        AdSelectionSignals signals = makeAdSelectionSignals();
        Log.d(TAG, "Ad tech: " + mAdTechIdentifier.toString());
        return new AdSelectionConfig.Builder()
                .setSeller(mAdTechIdentifier)
                .setPerBuyerSignals(ImmutableMap.of(mAdTechIdentifier, signals))
                .setCustomAudienceBuyers(ImmutableList.of(mAdTechIdentifier))
                .setAdSelectionSignals(signals)
                .setSellerSignals(signals)
                .setDecisionLogicUri(Uri.parse(mServerBaseAddress + Scenarios.SCORING_LOGIC_PATH))
                .setTrustedScoringSignalsUri(
                        Uri.parse(mServerBaseAddress + Scenarios.SCORING_SIGNALS_PATH))
                .build();
    }

    private void setupDefaultMockWebServer(ScenarioDispatcher dispatcher) throws Exception {
        if (mMockWebServer != null) {
            mMockWebServer.shutdown();
        }
        mMockWebServer = mMockWebServerRule.startMockWebServer(dispatcher);
        mServerBaseAddress = getServerBaseAddress();
        mAdTechIdentifier = AdTechIdentifier.fromString(mMockWebServer.getHostName());
        Log.d(TAG, "Started default MockWebServer.");
    }

    private String getCacheBusterPrefix() {
        return String.format("/%s", mCacheBuster);
    }

    private static AdSelectionSignals makeAdSelectionSignals() {
        return AdSelectionSignals.fromString(
                String.format("{\"valid\": true, \"publisher\": \"%s\"}", PACKAGE_NAME));
    }

    private JoinCustomAudienceRequest makeJoinCustomAudienceRequest(String customAudienceName) {
        return new JoinCustomAudienceRequest.Builder()
                .setCustomAudience(makeCustomAudience(customAudienceName))
                .build();
    }

    private CustomAudience makeCustomAudience(String customAudienceName) {
        Uri trustedBiddingUri = Uri.parse(mServerBaseAddress + Scenarios.BIDDING_SIGNALS_PATH);
        Uri dailyUpdateUri =
                Uri.parse(mServerBaseAddress + Scenarios.getDailyUpdatePath(customAudienceName));
        return new CustomAudience.Builder()
                .setName(customAudienceName)
                .setDailyUpdateUri(dailyUpdateUri)
                .setTrustedBiddingData(
                        new TrustedBiddingData.Builder()
                                .setTrustedBiddingKeys(ImmutableList.of())
                                .setTrustedBiddingUri(trustedBiddingUri)
                                .build())
                .setUserBiddingSignals(AdSelectionSignals.fromString("{}"))
                .setAds(makeAds(customAudienceName))
                .setBiddingLogicUri(
                        Uri.parse(String.format(mServerBaseAddress + Scenarios.BIDDING_LOGIC_PATH)))
                .setBuyer(mAdTechIdentifier)
                .setActivationTime(Instant.now())
                .setExpirationTime(Instant.now().plus(5, ChronoUnit.DAYS))
                .build();
    }

    private ImmutableList<AdData> makeAds(String customAudienceName) {
        ImmutableList.Builder<AdData> ads = new ImmutableList.Builder<>();
        for (int i = 0; i < NUM_ADS_PER_AUDIENCE; i++) {
            ads.add(makeAd(/* adNumber= */ i, customAudienceName));
        }
        return ads.build();
    }

    private AdData makeAd(int adNumber, String customAudienceName) {
        return new AdData.Builder()
                .setMetadata(
                        String.format(
                                Locale.ENGLISH,
                                "{\"bid\": 5, \"ad_number\": %d, \"target\": \"%s\"}",
                                adNumber,
                                PACKAGE_NAME))
                .setRenderUri(
                        Uri.parse(
                                String.format(
                                        "%s/render/%s/%s",
                                        mServerBaseAddress, customAudienceName, adNumber)))
                .build();
    }
}
