/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.adservices.utils;

import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER;
import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;

import static com.android.adservices.service.FlagsConstants.KEY_AD_SERVICES_RETRY_STRATEGY_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS;

import android.Manifest;
import android.adservices.adselection.AdSelectionConfig;
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
import android.adservices.customaudience.ScheduleCustomAudienceUpdateRequest;
import android.adservices.customaudience.TrustedBiddingData;
import android.net.Uri;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.annotations.EnableAllApis;
import com.android.adservices.common.annotations.SetCompatModeFlags;
import com.android.adservices.common.annotations.SetPpapiAppAllowList;
import com.android.adservices.shared.testing.SupportedByConditionRule;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import java.net.URL;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Abstract class for FLEDGE scenario tests using local servers. */
@EnableAllApis
@SetCompatModeFlags
@SetFlagEnabled(KEY_AD_SERVICES_RETRY_STRATEGY_ENABLED)
@SetIntegerFlag(name = KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS, value = 5_000)
@SetIntegerFlag(name = KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS, value = 10_000)
@SetIntegerFlag(name = KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS, value = 5_000)
@SetPpapiAppAllowList
public abstract class FledgeScenarioTest extends AdServicesCtsTestCase {
    protected static final int TIMEOUT = 120;
    protected static final int TIMEOUT_TES_SECONDS = 10;
    protected static final String SHOES_CA = "shoes";
    protected static final String SHIRTS_CA = "shirts";
    protected static final String HATS_CA = "hats";
    protected static final long AD_ID_FETCHER_TIMEOUT = 1000;
    private static final int NUM_ADS_PER_AUDIENCE = 4;
    private static final String PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;

    protected AdvertisingCustomAudienceClient mCustomAudienceClient;
    protected AdvertisingCustomAudienceClient mCustomAudienceClientUsingGetMethod;
    protected AdSelectionClient mAdSelectionClient;
    protected AdSelectionClient mAdSelectionClientUsingGetMethod;

    private AdTechIdentifier mBuyer;
    private AdTechIdentifier mSeller;
    private String mServerBaseAddress;

    @Rule(order = 1)
    public final SupportedByConditionRule devOptionsEnabled =
            DevContextUtils.createDevOptionsAvailableRule(mContext, LOGCAT_TAG_FLEDGE);

    @Rule(order = 2)
    public final SupportedByConditionRule webViewSupportsJSSandbox =
            CtsWebViewSupportUtil.createJSSandboxAvailableRule(mContext);

    @Rule(order = 3)
    public MockWebServerRule mMockWebServerRule =
            MockWebServerRule.forHttps(
                    mContext, "adservices_untrusted_test_server.p12", "adservices_test");

    protected static AdSelectionSignals makeAdSelectionSignals() {
        return AdSelectionSignals.fromString(
                String.format("{\"valid\": true, \"publisher\": \"%s\"}", PACKAGE_NAME));
    }

    @Before
    public void setUp() throws Exception {
        String[] deviceConfigPermissions;
        if (SdkLevel.isAtLeastU()) {
            deviceConfigPermissions =
                    new String[] {
                        Manifest.permission.WRITE_DEVICE_CONFIG,
                        Manifest.permission.WRITE_ALLOWLISTED_DEVICE_CONFIG
                    };
        } else {
            deviceConfigPermissions = new String[] {Manifest.permission.WRITE_DEVICE_CONFIG};
        }
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(deviceConfigPermissions);

        AdservicesTestHelper.killAdservicesProcess(mContext);
        ExecutorService executor = Executors.newCachedThreadPool();
        mCustomAudienceClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(mContext)
                        .setExecutor(executor)
                        .build();
        mCustomAudienceClientUsingGetMethod =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(mContext)
                        .setExecutor(executor)
                        .setUseGetMethodToCreateManagerInstance(true)
                        .build();
        mAdSelectionClient =
                new AdSelectionClient.Builder().setContext(mContext).setExecutor(executor).build();
        mAdSelectionClientUsingGetMethod =
                new AdSelectionClient.Builder()
                        .setContext(mContext)
                        .setExecutor(executor)
                        .setUseGetMethodToCreateManagerInstance(true)
                        .build();
    }

    @After
    public void tearDown() throws Exception {
        try {
            leaveCustomAudience(SHOES_CA);
            leaveCustomAudience(SHIRTS_CA);
            leaveCustomAudience(HATS_CA);
        } catch (Exception e) {
            // No-op catch here, these are only for cleaning up
            Log.w(LOGCAT_TAG_FLEDGE, "Failed while cleaning up custom audiences", e);
        }
    }

    protected AdSelectionOutcome doSelectAds(AdSelectionConfig adSelectionConfig)
            throws ExecutionException, InterruptedException, TimeoutException {
        return doSelectAds(mAdSelectionClient, adSelectionConfig);
    }

    protected AdSelectionOutcome doSelectAds(
            AdSelectionClient adSelectionClient, AdSelectionConfig adSelectionConfig)
            throws ExecutionException, InterruptedException, TimeoutException {
        AdSelectionOutcome result =
                adSelectionClient.selectAds(adSelectionConfig).get(TIMEOUT, TimeUnit.SECONDS);
        Log.d(LOGCAT_TAG_FLEDGE, "Ran ad selection.");
        return result;
    }

    protected void doReportEvent(long adSelectionId, String eventName)
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        doReportEvent(mAdSelectionClient, adSelectionId, eventName);
    }

    protected void doReportEvent(
            AdSelectionClient adSelectionClient, long adSelectionId, String eventName)
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        adSelectionClient
                .reportEvent(
                        new ReportEventRequest.Builder(
                                        adSelectionId,
                                        eventName,
                                        new JSONObject().put("key", "value").toString(),
                                        FLAG_REPORTING_DESTINATION_SELLER
                                                | FLAG_REPORTING_DESTINATION_BUYER)
                                .build())
                .get(TIMEOUT, TimeUnit.SECONDS);
        Log.d(LOGCAT_TAG_FLEDGE, "Ran report ad click for ad selection id: " + adSelectionId);
    }

    protected void doReportImpression(long adSelectionId, AdSelectionConfig adSelectionConfig)
            throws ExecutionException, InterruptedException, TimeoutException {
        doReportImpression(mAdSelectionClient, adSelectionId, adSelectionConfig);
    }

    protected void doReportImpression(
            AdSelectionClient adSelectionClient,
            long adSelectionId,
            AdSelectionConfig adSelectionConfig)
            throws ExecutionException, InterruptedException, TimeoutException {
        adSelectionClient
                .reportImpression(new ReportImpressionRequest(adSelectionId, adSelectionConfig))
                .get(TIMEOUT, TimeUnit.SECONDS);
        Log.d(LOGCAT_TAG_FLEDGE, "Ran report impression for ad selection id: " + adSelectionId);
    }

    protected void joinCustomAudience(String customAudienceName)
            throws ExecutionException, InterruptedException, TimeoutException {
        joinCustomAudience(mCustomAudienceClient, customAudienceName);
    }

    protected void joinCustomAudience(
            AdvertisingCustomAudienceClient client, String customAudienceName)
            throws ExecutionException, InterruptedException, TimeoutException {
        JoinCustomAudienceRequest joinCustomAudienceRequest =
                makeJoinCustomAudienceRequest(customAudienceName);
        client.joinCustomAudience(joinCustomAudienceRequest.getCustomAudience())
                .get(TIMEOUT_TES_SECONDS, TimeUnit.SECONDS);
        Log.d(LOGCAT_TAG_FLEDGE, "Joined Custom Audience: " + customAudienceName);
    }

    protected void joinCustomAudience(CustomAudience customAudience)
            throws ExecutionException, InterruptedException, TimeoutException {
        mCustomAudienceClient
                .joinCustomAudience(customAudience)
                .get(TIMEOUT_TES_SECONDS, TimeUnit.SECONDS);
        Log.d(LOGCAT_TAG_FLEDGE, "Joined Custom Audience: " + customAudience.getName());
    }

    protected void leaveCustomAudience(String customAudienceName)
            throws ExecutionException, InterruptedException, TimeoutException {
        leaveCustomAudience(mCustomAudienceClient, customAudienceName);
    }

    protected void leaveCustomAudience(
            AdvertisingCustomAudienceClient client, String customAudienceName)
            throws ExecutionException, InterruptedException, TimeoutException {
        CustomAudience customAudience = makeCustomAudience(customAudienceName).build();
        client.leaveCustomAudience(customAudience.getBuyer(), customAudience.getName())
                .get(TIMEOUT_TES_SECONDS, TimeUnit.SECONDS);
        Log.d(LOGCAT_TAG_FLEDGE, "Left Custom Audience: " + customAudienceName);
    }

    protected void doScheduleCustomAudienceUpdate(ScheduleCustomAudienceUpdateRequest request)
            throws ExecutionException, InterruptedException, TimeoutException {
        doScheduleCustomAudienceUpdate(mCustomAudienceClient, request);
    }

    protected void doScheduleCustomAudienceUpdate(
            AdvertisingCustomAudienceClient client, ScheduleCustomAudienceUpdateRequest request)
            throws ExecutionException, InterruptedException, TimeoutException {
        client.scheduleCustomAudienceUpdate(request).get(TIMEOUT, TimeUnit.SECONDS);
        Log.d(LOGCAT_TAG_FLEDGE, "Scheduled Custom Audience Update: " + request);
    }

    protected String getServerBaseAddress() {
        return mServerBaseAddress;
    }

    protected AdSelectionConfig makeAdSelectionConfig(URL serverBaseAddressWithPrefix) {
        AdSelectionSignals signals = FledgeScenarioTest.makeAdSelectionSignals();
        Log.d(LOGCAT_TAG_FLEDGE, "Ad tech buyer: " + mBuyer);
        Log.d(LOGCAT_TAG_FLEDGE, "Ad tech seller: " + mSeller);
        return new AdSelectionConfig.Builder()
                .setSeller(mSeller)
                .setPerBuyerSignals(ImmutableMap.of(mBuyer, signals))
                .setCustomAudienceBuyers(ImmutableList.of(mBuyer))
                .setAdSelectionSignals(signals)
                .setSellerSignals(signals)
                .setDecisionLogicUri(
                        Uri.parse(serverBaseAddressWithPrefix + Scenarios.SCORING_LOGIC_PATH))
                .setTrustedScoringSignalsUri(
                        Uri.parse(serverBaseAddressWithPrefix + Scenarios.SCORING_SIGNALS_PATH))
                .build();
    }

    protected ScenarioDispatcher setupDispatcher(
            ScenarioDispatcherFactory scenarioDispatcherFactory) throws Exception {
        ScenarioDispatcher scenarioDispatcher =
                mMockWebServerRule.startMockWebServer(scenarioDispatcherFactory);
        mServerBaseAddress = scenarioDispatcher.getBaseAddressWithPrefix().toString();
        mBuyer =
                AdTechIdentifier.fromString(
                        scenarioDispatcher.getBaseAddressWithPrefix().getHost());
        mSeller =
                AdTechIdentifier.fromString(
                        scenarioDispatcher.getBaseAddressWithPrefix().getHost());
        Log.d(LOGCAT_TAG_FLEDGE, "Started default MockWebServer.");
        return scenarioDispatcher;
    }


    private JoinCustomAudienceRequest makeJoinCustomAudienceRequest(String customAudienceName) {
        return new JoinCustomAudienceRequest.Builder()
                .setCustomAudience(makeCustomAudience(customAudienceName).build())
                .build();
    }

    protected CustomAudience.Builder makeCustomAudience(String customAudienceName) {
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
                .setBuyer(mBuyer)
                .setActivationTime(Instant.now())
                .setExpirationTime(Instant.now().plus(5, ChronoUnit.DAYS));
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

    protected FetchAndJoinCustomAudienceRequest.Builder makeFetchAndJoinCustomAudienceRequest() {
        return new FetchAndJoinCustomAudienceRequest.Builder(
                        Uri.parse(mServerBaseAddress + Scenarios.FETCH_CA_PATH))
                .setName(HATS_CA);
    }

    protected void doFetchAndJoinCustomAudience(FetchAndJoinCustomAudienceRequest request)
            throws Exception {
        doFetchAndJoinCustomAudience(mCustomAudienceClient, request);
    }

    protected void doFetchAndJoinCustomAudience(
            AdvertisingCustomAudienceClient client, FetchAndJoinCustomAudienceRequest request)
            throws Exception {
        client.fetchAndJoinCustomAudience(request).get(TIMEOUT_TES_SECONDS, TimeUnit.SECONDS);
    }
}
