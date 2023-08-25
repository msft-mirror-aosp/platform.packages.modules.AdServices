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
import android.content.Context;
import android.net.Uri;
import android.util.ArrayMap;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.CompatAdServicesTestUtils;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
        setupDefaultMockWebServer();
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

    @Test
    public void testAdSelection_withBiddingAndScoringLogic_happyPath() throws Exception {
        JoinCustomAudienceRequest joinCustomAudienceRequest = makeJoinCustomAudienceRequest();

        try {
            mCustomAudienceClient
                    .joinCustomAudience(joinCustomAudienceRequest.getCustomAudience())
                    .get(5, TimeUnit.SECONDS);
            Log.d(TAG, "Joined Custom Audience.");
            AdSelectionOutcome result =
                    mAdSelectionClient
                            .selectAds(makeAdSelectionConfig())
                            .get(TIMEOUT, TimeUnit.SECONDS);
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

        List<String> urls = getUrlsCalled(mMockWebServer, 4);
        assertThat(urls).contains(getCacheBusterPrefix() + "/buyer/bidding/simple_logic");
        assertThat(urls).contains(getCacheBusterPrefix() + "/seller/decision/simple_logic");
    }

    private String getServerBaseDomain() {
        return mMockWebServer.getHostName();
    }

    private String getServerBaseAddress() {
        return String.format(
                "https://%s:%s", mMockWebServer.getHostName(), mMockWebServer.getPort());
    }

    private AdSelectionConfig makeAdSelectionConfig() {
        AdSelectionSignals signals = makeAdSelectionSignals();
        Uri decisionLogicUri =
                Uri.parse(String.format("%s/seller/decision/simple_logic", mServerBaseAddress));
        Log.d(TAG, "Decision logic URI: " + decisionLogicUri.toString());
        Log.d(TAG, "Ad tech: " + mAdTechIdentifier.toString());
        return new AdSelectionConfig.Builder()
                .setSeller(mAdTechIdentifier)
                .setPerBuyerSignals(ImmutableMap.of(mAdTechIdentifier, signals))
                .setCustomAudienceBuyers(ImmutableList.of(mAdTechIdentifier))
                .setAdSelectionSignals(signals)
                .setSellerSignals(signals)
                .setDecisionLogicUri(decisionLogicUri)
                .setTrustedScoringSignalsUri(
                        Uri.parse(
                                String.format(
                                        "%s/seller/scoringsignals/simple", mServerBaseAddress)))
                .build();
    }

    private static String loadResource(String fileName) {
        String lines = "";
        try {
            InputStream is = ApplicationProvider.getApplicationContext().getAssets().open(fileName);
            byte[] bytes = is.readAllBytes();
            is.close();
            lines = new String(bytes);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return lines;
    }

    private void setupDefaultMockWebServer() throws Exception {
        if (mMockWebServer != null) {
            mMockWebServer.shutdown();
        }
        Map<String, String> pathToFileMap = new ArrayMap<>();
        pathToFileMap.put("/buyer/bidding/simple_logic", "BiddingLogic.js");
        pathToFileMap.put("/seller/decision/simple_logic", "ScoringLogic.js");
        pathToFileMap.put("/seller/scoringsignals/simple", "ScoringSignals.json");
        pathToFileMap.put("/buyer/biddingsignals/simple", "BiddingSignals.json");
        mMockWebServer = makeMockWebServer(pathToFileMap);
        mCacheBuster = mCacheBusterRandom.nextInt();
        mServerBaseAddress = getServerBaseAddress() + getCacheBusterPrefix();
        mAdTechIdentifier = AdTechIdentifier.fromString(getServerBaseDomain());
        Log.d(TAG, "Started default MockWebServer.");
    }

    private String getCacheBusterPrefix() {
        return String.format("/%s", mCacheBuster);
    }

    private MockWebServer makeMockWebServer(Map<String, String> pathToFileMap) throws Exception {
        return mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        Log.d(TAG, String.format("Serving: %s", request.getPath()));
                        for (Map.Entry<String, String> pathToFile : pathToFileMap.entrySet()) {
                            String path = pathToFile.getKey();
                            String filePath = pathToFile.getValue();
                            if (request.getPath().contains(path)) {
                                return new MockResponse()
                                        .setBody(
                                                Objects.requireNonNull(loadResource(filePath))
                                                        .getBytes(StandardCharsets.UTF_8));
                            }
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                });
    }

    private static List<String> getUrlsCalled(MockWebServer mockWebServer, int expectedRequests)
            throws InterruptedException {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        RecordedRequest request = null;
        for (int i = 0; i < expectedRequests; i++) {
            request = mockWebServer.takeRequest();
            if (request == null) {
                break;
            }
            builder.add(request.getPath());
        }
        return builder.build();
    }

    private static AdSelectionSignals makeAdSelectionSignals() {
        return AdSelectionSignals.fromString(
                String.format("{\"valid\": true, \"publisher\": \"%s\"}", PACKAGE_NAME));
    }

    private JoinCustomAudienceRequest makeJoinCustomAudienceRequest() {
        return new JoinCustomAudienceRequest.Builder()
                .setCustomAudience(
                        new CustomAudience.Builder()
                                .setName(AdSelectionTest.CUSTOM_AUDIENCE)
                                .setDailyUpdateUri(
                                        Uri.parse(
                                                String.format(
                                                        "%s/buyer/dailyupdate/%s",
                                                        mServerBaseAddress,
                                                        AdSelectionTest.CUSTOM_AUDIENCE)))
                                .setTrustedBiddingData(
                                        new TrustedBiddingData.Builder()
                                                .setTrustedBiddingKeys(ImmutableList.of())
                                                .setTrustedBiddingUri(
                                                        Uri.parse(
                                                                String.format(
                                                                        "%s/buyer/biddingsignals"
                                                                                + "/simple",
                                                                        mServerBaseAddress)))
                                                .build())
                                .setUserBiddingSignals(AdSelectionSignals.fromString("{}"))
                                .setAds(makeAds())
                                .setBiddingLogicUri(
                                        Uri.parse(
                                                String.format(
                                                        "%s/buyer/bidding/simple_logic",
                                                        mServerBaseAddress)))
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
