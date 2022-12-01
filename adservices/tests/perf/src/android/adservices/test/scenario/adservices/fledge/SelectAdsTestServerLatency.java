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

import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.customaudience.CustomAudience;
import android.adservices.test.scenario.adservices.utils.StaticAdTechServerUtils;
import android.content.Context;
import android.platform.test.scenario.annotation.Scenario;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

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

    private static final String LOG_LABEL_P50_5G = "SELECT_ADS_LATENCY_P50_5G";
    private static final String LOG_LABEL_P90_5G = "SELECT_ADS_LATENCY_P90_5G";

    private static final int NUMBER_OF_CUSTOM_AUDIENCES_MEDIUM = 10;
    private static final int NUMBER_ADS_PER_CA_MEDIUM = 5;
    private static final int API_RESPONSE_TIMEOUT_SECONDS = 100;
    private static final int DELAY_TO_AVOID_THROTTLE = 1001;

    private static final String AD_SELECTION_FAILURE_MESSAGE =
            "Ad selection outcome is not expected";

    private final Context mContext = ApplicationProvider.getApplicationContext();
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
    private final Ticker mTicker =
            new Ticker() {
                public long read() {
                    return android.os.SystemClock.elapsedRealtimeNanos();
                }
            };
    private List<CustomAudience> mCustomAudiences = new ArrayList<>();

    @Rule public SelectAdsFlagRule mSelectAdsFlagRule = new SelectAdsFlagRule();

    @BeforeClass
    public static void setupBeforeClass() {
        StaticAdTechServerUtils.warmupServers();
    }

    @Before
    public void setup() {
        mCustomAudiences.clear();
    }

    @After
    public void tearDown() throws Exception {
        leaveCustomAudiences(mCustomAudiences);
    }

    @Test
    public void selectAds_oneBuyer_realServer() throws Exception {
        StaticAdTechServerUtils staticAdTechServerUtils =
                StaticAdTechServerUtils.withNumberOfBuyers(1);
        List<CustomAudience> customAudiences =
                staticAdTechServerUtils.createAndGetCustomAudiences(
                        NUMBER_OF_CUSTOM_AUDIENCES_MEDIUM, NUMBER_ADS_PER_CA_MEDIUM);
        joinCustomAudiences(customAudiences);

        Stopwatch timer = Stopwatch.createStarted(mTicker);
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(staticAdTechServerUtils.createAndGetAdSelectionConfig())
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        timer.stop();

        // TODO(b/259248789) : Modify SelectAdsLatencyHelper to parse all log queries beginning with
        //  SELECT_ADS_LATENCY and use LOG_LABEL_REAL_SERVER_ONE_BUYER_P50 below instead of
        //  LOG_LABEL_P50_5G
        Log.i(
                TAG,
                "("
                        + generateLogLabel("selectAds_oneBuyer_realServer")
                        + ": "
                        + timer.elapsed(TimeUnit.MILLISECONDS)
                        + " ms)");
        Assert.assertEquals(
                AD_SELECTION_FAILURE_MESSAGE,
                createExpectedWinningUri(
                        /* buyerIndex = */ 0, "GENERIC_CA_1", NUMBER_ADS_PER_CA_MEDIUM),
                outcome.getRenderUri().toString());
    }

    @Test
    public void selectAds_fiveBuyers_realServer() throws Exception {
        StaticAdTechServerUtils staticAdTechServerUtils =
                StaticAdTechServerUtils.withNumberOfBuyers(5);
        mCustomAudiences =
                staticAdTechServerUtils.createAndGetCustomAudiences(
                        NUMBER_OF_CUSTOM_AUDIENCES_MEDIUM, NUMBER_ADS_PER_CA_MEDIUM);
        joinCustomAudiences(mCustomAudiences);

        Stopwatch timer = Stopwatch.createStarted(mTicker);
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(staticAdTechServerUtils.createAndGetAdSelectionConfig())
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        timer.stop();

        // TODO(b/259248789) : Modify SelectAdsLatencyHelper to parse all log queries beginning with
        //  SELECT_ADS_LATENCY and use LOG_LABEL_REAL_SERVER_FIVE_BUYERS_P50 below instead of
        //  LOG_LABEL_P90_5G
        Log.i(
                TAG,
                "("
                        + generateLogLabel("selectAds_fiveBuyers_realServer")
                        + ": "
                        + timer.elapsed(TimeUnit.MILLISECONDS)
                        + " ms)");
        Assert.assertEquals(
                AD_SELECTION_FAILURE_MESSAGE,
                createExpectedWinningUri(
                        /* buyerIndex = */ 4, "GENERIC_CA_1", NUMBER_ADS_PER_CA_MEDIUM),
                outcome.getRenderUri().toString());
    }

    private void joinCustomAudiences(List<CustomAudience> customAudiences) throws Exception {
        for (CustomAudience ca : customAudiences) {
            Thread.sleep(DELAY_TO_AVOID_THROTTLE);
            mCustomAudienceClient
                    .joinCustomAudience(ca)
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void leaveCustomAudiences(List<CustomAudience> customAudiences) throws Exception {
        for (CustomAudience ca : customAudiences) {
            Thread.sleep(DELAY_TO_AVOID_THROTTLE);
            mCustomAudienceClient
                    .leaveCustomAudience(ca.getBuyer(), ca.getName())
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private String createExpectedWinningUri(
            int buyerIndex, String customAudienceName, int adNumber) {
        return StaticAdTechServerUtils.getAdRenderUri(buyerIndex, customAudienceName, adNumber);
    }

    private String generateLogLabel(String testName) {
        return "SELECT_ADS_LATENCY_" + getClass().getSimpleName() + "#" + testName;
    }
}
