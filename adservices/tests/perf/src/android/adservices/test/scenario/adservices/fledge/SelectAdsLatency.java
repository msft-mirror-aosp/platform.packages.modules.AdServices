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

package src.android.adservices.test.scenario.adservices.fledge;

import android.Manifest;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.test.scenario.adservices.utils.CustomAudienceSetupRule;
import android.adservices.test.scenario.adservices.utils.MockWebServerDispatcherFactory;
import android.adservices.test.scenario.adservices.utils.MockWebServerRule;
import android.adservices.test.scenario.adservices.utils.MockWebServerRuleFactory;
import android.content.Context;
import android.net.Uri;
import android.platform.test.scenario.annotation.Scenario;
import android.provider.DeviceConfig;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellUtils;

import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Tests to compute latency runs for on device ad selection. */

// TODO(b/251802548): Consider parameterizing the tests.
@Scenario
@RunWith(JUnit4.class)
public class SelectAdsLatency {
    private static final String TAG = "SelectAds";

    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("localhost");
    private static final List<AdTechIdentifier> CUSTOM_AUDIENCE_BUYERS =
            Collections.singletonList(BUYER);
    private static final AdSelectionSignals AD_SELECTION_SIGNALS =
            AdSelectionSignals.fromString("{\"ad_selection_signals\":1}");
    private static final AdSelectionSignals SELLER_SIGNALS =
            AdSelectionSignals.fromString("{\"test_seller_signals\":1}");
    private static final Map<AdTechIdentifier, AdSelectionSignals> PER_BUYER_SIGNALS =
            Map.of(BUYER, AdSelectionSignals.fromString("{\"buyer_signals\":1}"));
    private static final AdTechIdentifier SELLER = AdTechIdentifier.fromString("localhost");
    private static final int API_RESPONSE_TIMEOUT_SECONDS = 100;
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    // Estimates from
    // https://docs.google.com/spreadsheets/d/1EP_cwBbwYI-NMro0Qq5uif1krwjIQhjK8fjOu15j7hQ/edit?usp=sharing&resourcekey=0-A67kzEnAKKz1k7qpshSedg
    private static final int NUMBER_OF_CUSTOM_AUDIENCES_MEDIUM = 10;
    private static final int NUMBER_OF_CUSTOM_AUDIENCES_LARGE = 50;
    private static final int NUMBER_ADS_PER_CA_MEDIUM = 5;
    private static final int NUMBER_ADS_PER_CA_LARGE = 10;

    private static final String LOG_LABEL_P50_5G = "SELECT_ADS_LATENCY_P50_5G";
    private static final String LOG_LABEL_P50_4GPLUS = "SELECT_ADS_LATENCY_P50_4GPLUS";
    private static final String LOG_LABEL_P50_4G = "SELECT_ADS_LATENCY_P50_4G";
    private static final String LOG_LABEL_P90_5G = "SELECT_ADS_LATENCY_P90_5G";
    private static final String LOG_LABEL_P90_4GPLUS = "SELECT_ADS_LATENCY_P90_4GPLUS";
    private static final String LOG_LABEL_P90_4G = "SELECT_ADS_LATENCY_P90_4G";

    private static final String AD_SELECTION_FAILURE_MESSAGE =
            "Ad selection outcome is not expected";

    protected final Context mContext = ApplicationProvider.getApplicationContext();
    private final AdSelectionClient mAdSelectionClient =
            new AdSelectionClient.Builder()
                    .setContext(mContext)
                    .setExecutor(CALLBACK_EXECUTOR)
                    .build();
    private final AdvertisingCustomAudienceClient mCustomAudienceClient =
            new AdvertisingCustomAudienceClient.Builder()
                    .setContext(mContext)
                    .setExecutor(CALLBACK_EXECUTOR)
                    .build();
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();

    @Rule
    public CustomAudienceSetupRule mCustomAudienceSetupRule =
            new CustomAudienceSetupRule(mCustomAudienceClient, mMockWebServerRule);

    private final Ticker mTicker =
            new Ticker() {
                public long read() {
                    return android.os.SystemClock.elapsedRealtimeNanos();
                }
            };

    @BeforeClass
    public static void setupBeforeClass() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.WRITE_DEVICE_CONFIG);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "fledge_ad_selection_bidding_timeout_per_ca_ms",
                "120000",
                false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "fledge_ad_selection_scoring_timeout_ms",
                "120000",
                false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "fledge_ad_selection_overall_timeout_ms",
                "120000",
                false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "fledge_ad_selection_bidding_timeout_per_buyer_ms",
                "120000",
                false);
        ShellUtils.runShellCommand("su 0 killall -9 com.google.android.adservices.api");
    }

    @Test
    public void selectAds_p50_5G() throws Exception {
        mMockWebServerRule.createMockWebServer();
        mMockWebServerRule.startCreatedMockWebServer(
                MockWebServerDispatcherFactory.create5Gp50LatencyDispatcher(mMockWebServerRule));
        mCustomAudienceSetupRule.populateCustomAudiences(
                NUMBER_OF_CUSTOM_AUDIENCES_MEDIUM, NUMBER_ADS_PER_CA_MEDIUM);
        Stopwatch timer = Stopwatch.createStarted(mTicker);

        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(createAdSelectionConfig())
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        timer.stop();

        // TODO(b/250851601): Currently we use logcat latency collector. Consider replacing this
        // with a perfetto trace collector because it won't be affected by write to logcat latency.
        Log.i(TAG, "(" + LOG_LABEL_P50_5G + ": " + timer.elapsed(TimeUnit.MILLISECONDS) + " ms)");
        Assert.assertEquals(
                AD_SELECTION_FAILURE_MESSAGE,
                createExpectedWinningUri(BUYER, "GENERIC_CA_1", NUMBER_ADS_PER_CA_MEDIUM),
                outcome.getRenderUri());
    }

    @Test
    public void selectAds_p50_4GPlus() throws Exception {
        mMockWebServerRule.createMockWebServer();
        mMockWebServerRule.startCreatedMockWebServer(
                MockWebServerDispatcherFactory.create4GPlusp50LatencyDispatcher(
                        mMockWebServerRule));
        mCustomAudienceSetupRule.populateCustomAudiences(
                NUMBER_OF_CUSTOM_AUDIENCES_MEDIUM, NUMBER_ADS_PER_CA_MEDIUM);

        Stopwatch timer = Stopwatch.createStarted(mTicker);
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(createAdSelectionConfig())
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        timer.stop();

        // TODO(b/250851601): Currently we use logcat latency collector. Consider replacing this
        // with a perfetto trace collector because it won't be affected by write to logcat latency.
        Log.i(
                TAG,
                "(" + LOG_LABEL_P50_4GPLUS + ": " + timer.elapsed(TimeUnit.MILLISECONDS) + " ms)");
        Assert.assertEquals(
                AD_SELECTION_FAILURE_MESSAGE,
                createExpectedWinningUri(BUYER, "GENERIC_CA_1", NUMBER_ADS_PER_CA_MEDIUM),
                outcome.getRenderUri());
    }

    @Test
    public void selectAds_p50_4G() throws Exception {
        mMockWebServerRule.createMockWebServer();
        mMockWebServerRule.startCreatedMockWebServer(
                MockWebServerDispatcherFactory.create4Gp50LatencyDispatcher(mMockWebServerRule));
        mCustomAudienceSetupRule.populateCustomAudiences(
                NUMBER_OF_CUSTOM_AUDIENCES_MEDIUM, NUMBER_ADS_PER_CA_MEDIUM);

        Stopwatch timer = Stopwatch.createStarted(mTicker);
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(createAdSelectionConfig())
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        timer.stop();

        // TODO(b/250851601): Currently we use logcat latency collector. Consider replacing this
        // with a perfetto trace collector because it won't be affected by write to logcat latency.
        Log.i(TAG, "(" + LOG_LABEL_P50_4G + ": " + timer.elapsed(TimeUnit.MILLISECONDS) + " ms)");
        Assert.assertEquals(
                AD_SELECTION_FAILURE_MESSAGE,
                createExpectedWinningUri(BUYER, "GENERIC_CA_1", NUMBER_ADS_PER_CA_MEDIUM),
                outcome.getRenderUri());
    }

    @Test
    public void selectAds_p90_5G() throws Exception {
        mMockWebServerRule.createMockWebServer();
        mMockWebServerRule.startCreatedMockWebServer(
                MockWebServerDispatcherFactory.create5Gp90LatencyDispatcher(mMockWebServerRule));
        mCustomAudienceSetupRule.populateCustomAudiences(
                NUMBER_OF_CUSTOM_AUDIENCES_LARGE, NUMBER_ADS_PER_CA_LARGE);

        Stopwatch timer = Stopwatch.createStarted(mTicker);
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(createAdSelectionConfig())
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        timer.stop();

        // TODO(b/250851601): Currently we use logcat latency collector. Consider replacing this
        // with a perfetto trace collector because it won't be affected by write to logcat latency.
        Log.i(TAG, "(" + LOG_LABEL_P90_5G + ": " + timer.elapsed(TimeUnit.MILLISECONDS) + " ms)");
        Assert.assertEquals(
                AD_SELECTION_FAILURE_MESSAGE,
                createExpectedWinningUri(BUYER, "GENERIC_CA_1", NUMBER_ADS_PER_CA_LARGE),
                outcome.getRenderUri());
    }

    @Test
    public void selectAds_p90_4G() throws Exception {
        mMockWebServerRule.createMockWebServer();
        mMockWebServerRule.startCreatedMockWebServer(
                MockWebServerDispatcherFactory.create4Gp90LatencyDispatcher(mMockWebServerRule));
        mCustomAudienceSetupRule.populateCustomAudiences(
                NUMBER_OF_CUSTOM_AUDIENCES_LARGE, NUMBER_ADS_PER_CA_LARGE);
        Stopwatch timer = Stopwatch.createStarted(mTicker);

        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(createAdSelectionConfig())
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        timer.stop();
        // TODO(b/250851601): Currently we use logcat latency collector. Consider replacing this
        // with a perfetto trace collector because it won't be affected by write to logcat latency.
        Log.i(TAG, "(" + LOG_LABEL_P90_4G + ": " + timer.elapsed(TimeUnit.MILLISECONDS) + " ms)");
        Assert.assertEquals(
                AD_SELECTION_FAILURE_MESSAGE,
                createExpectedWinningUri(BUYER, "GENERIC_CA_1", NUMBER_ADS_PER_CA_LARGE),
                outcome.getRenderUri());
    }

    @Test
    public void selectAds_p90_4GPlus() throws Exception {
        mMockWebServerRule.createMockWebServer();
        mMockWebServerRule.startCreatedMockWebServer(
                MockWebServerDispatcherFactory.create4GPlusp90LatencyDispatcher(
                        mMockWebServerRule));
        mCustomAudienceSetupRule.populateCustomAudiences(
                NUMBER_OF_CUSTOM_AUDIENCES_LARGE, NUMBER_ADS_PER_CA_LARGE);

        Stopwatch timer = Stopwatch.createStarted(mTicker);
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(createAdSelectionConfig())
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        timer.stop();
        // TODO(b/250851601): Currently we use logcat latency collector. Consider replacing this
        // with a perfetto trace collector because it won't be affected by write to logcat latency.
        Log.i(
                TAG,
                "(" + LOG_LABEL_P90_4GPLUS + ": " + timer.elapsed(TimeUnit.MILLISECONDS) + " ms)");
        Assert.assertEquals(
                AD_SELECTION_FAILURE_MESSAGE,
                createExpectedWinningUri(BUYER, "GENERIC_CA_1", NUMBER_ADS_PER_CA_LARGE),
                outcome.getRenderUri());
    }

    private static Uri getUri(String name, String path) {
        return Uri.parse("https://" + name + path);
    }

    private Uri createExpectedWinningUri(
            AdTechIdentifier buyer, String customAudienceName, int adNumber) {
        return getUri(buyer.toString(), "/adverts/123/" + customAudienceName + "/ad" + adNumber);
    }

    private AdSelectionConfig createAdSelectionConfig() {
        return new AdSelectionConfig.Builder()
                .setSeller(SELLER)
                .setDecisionLogicUri(
                        mMockWebServerRule.uriForPath(
                                MockWebServerDispatcherFactory.getDecisionLogicPath()))
                // TODO(b/244530379) Make compatible with multiple buyers
                .setCustomAudienceBuyers(CUSTOM_AUDIENCE_BUYERS)
                .setAdSelectionSignals(AD_SELECTION_SIGNALS)
                .setSellerSignals(SELLER_SIGNALS)
                .setPerBuyerSignals(PER_BUYER_SIGNALS)
                .setTrustedScoringSignalsUri(
                        mMockWebServerRule.uriForPath(
                                MockWebServerDispatcherFactory.getTrustedScoringSignalPath()))
                .build();
    }
}
