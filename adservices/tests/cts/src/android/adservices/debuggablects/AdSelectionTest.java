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

import static com.google.common.truth.Truth.assertThat;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
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

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AdSelectionTest extends ForegroundDebuggableCtsTest {
    private static final String TAG = "android.adservices.debuggablects";
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final int TIMEOUT = 120;
    private static final int NUM_ADS_PER_AUDIENCE = 4;
    private static final String CUSTOM_AUDIENCE = CustomAudienceFixture.VALID_NAME;
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
        JoinCustomAudienceRequest joinCustomAudienceRequest = makeJoinCustomAudienceRequest();
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();

        try {
            mCustomAudienceClient
                    .joinCustomAudience(joinCustomAudienceRequest.getCustomAudience())
                    .get(5, TimeUnit.SECONDS);
            Log.d(TAG, "Joined Custom Audience.");
            AdSelectionOutcome result =
                    mAdSelectionClient.selectAds(adSelectionConfig).get(TIMEOUT, TimeUnit.SECONDS);
            Log.d(TAG, "Ran ad selection.");
            assertThat(result.hasOutcome()).isTrue();
        } finally {
            mCustomAudienceClient
                    .leaveCustomAudience(
                            joinCustomAudienceRequest.getCustomAudience().getBuyer(),
                            joinCustomAudienceRequest.getCustomAudience().getName())
                    .get(TIMEOUT, TimeUnit.SECONDS);
            Log.d(TAG, "Left Custom Audience.");
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
        JoinCustomAudienceRequest joinCustomAudienceRequest = makeJoinCustomAudienceRequest();
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();
        long adSelectionId;

        try {
            mCustomAudienceClient
                    .joinCustomAudience(joinCustomAudienceRequest.getCustomAudience())
                    .get(5, TimeUnit.SECONDS);
            AdSelectionOutcome result =
                    mAdSelectionClient.selectAds(adSelectionConfig).get(TIMEOUT, TimeUnit.SECONDS);
            adSelectionId = result.getAdSelectionId();
            assertThat(result.hasOutcome()).isTrue();
            assertThat(result.getRenderUri()).isNotNull();
        } finally {
            mCustomAudienceClient
                    .leaveCustomAudience(
                            joinCustomAudienceRequest.getCustomAudience().getBuyer(),
                            joinCustomAudienceRequest.getCustomAudience().getName())
                    .get(TIMEOUT, TimeUnit.SECONDS);
        }
        mAdSelectionClient
                .reportImpression(new ReportImpressionRequest(adSelectionId, adSelectionConfig))
                .get(TIMEOUT, TimeUnit.SECONDS);
        Log.d(TAG, "Ran report impression.");

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
        JoinCustomAudienceRequest joinCustomAudienceRequest = makeJoinCustomAudienceRequest();
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();

        try {
            overrideBiddingLogicVersionToV3(true);
            mCustomAudienceClient
                    .joinCustomAudience(joinCustomAudienceRequest.getCustomAudience())
                    .get(5, TimeUnit.SECONDS);
            Log.d(TAG, "Joined Custom Audience.");
            AdSelectionOutcome result =
                    mAdSelectionClient.selectAds(adSelectionConfig).get(TIMEOUT, TimeUnit.SECONDS);
            Log.d(TAG, "Ran ad selection.");
            assertThat(result.hasOutcome()).isTrue();
            assertThat(result.getRenderUri()).isNotNull();
        } finally {
            overrideBiddingLogicVersionToV3(false);
            mCustomAudienceClient
                    .leaveCustomAudience(
                            joinCustomAudienceRequest.getCustomAudience().getBuyer(),
                            joinCustomAudienceRequest.getCustomAudience().getName())
                    .get(TIMEOUT, TimeUnit.SECONDS);
            Log.d(TAG, "Left Custom Audience.");
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    private String getServerBaseAddress() {
        return String.format(
                "https://%s:%s%s/",
                mMockWebServer.getHostName(), mMockWebServer.getPort(), getCacheBusterPrefix());
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

    private JoinCustomAudienceRequest makeJoinCustomAudienceRequest() {
        Uri trustedBiddingUri = Uri.parse(mServerBaseAddress + Scenarios.BIDDING_SIGNALS_PATH);
        Uri dailyUpdateUri =
                Uri.parse(mServerBaseAddress + Scenarios.getDailyUpdatePath(CUSTOM_AUDIENCE));
        return new JoinCustomAudienceRequest.Builder()
                .setCustomAudience(
                        new CustomAudience.Builder()
                                .setName(AdSelectionTest.CUSTOM_AUDIENCE)
                                .setDailyUpdateUri(dailyUpdateUri)
                                .setTrustedBiddingData(
                                        new TrustedBiddingData.Builder()
                                                .setTrustedBiddingKeys(ImmutableList.of())
                                                .setTrustedBiddingUri(trustedBiddingUri)
                                                .build())
                                .setUserBiddingSignals(AdSelectionSignals.fromString("{}"))
                                .setAds(makeAds())
                                .setBiddingLogicUri(
                                        Uri.parse(
                                                String.format(
                                                        mServerBaseAddress
                                                                + Scenarios.BIDDING_LOGIC_PATH)))
                                .setBuyer(mAdTechIdentifier)
                                .setActivationTime(Instant.now())
                                .setExpirationTime(Instant.now().plus(5, ChronoUnit.DAYS))
                                .build())
                .build();
    }

    private ImmutableList<AdData> makeAds() {
        ImmutableList.Builder<AdData> ads = new ImmutableList.Builder<>();
        for (int i = 0; i < NUM_ADS_PER_AUDIENCE; i++) {
            ads.add(makeAd(/* adNumber= */ i));
        }
        return ads.build();
    }

    private AdData makeAd(int adNumber) {
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
                                        mServerBaseAddress,
                                        AdSelectionTest.CUSTOM_AUDIENCE,
                                        adNumber)))
                .build();
    }
}
