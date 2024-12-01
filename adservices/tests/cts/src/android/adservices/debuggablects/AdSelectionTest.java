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

import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_AD_ID_FETCHER_TIMEOUT_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_BIDDING_LOGIC_JS_VERSION;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_CPC_BILLING_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORT_SEND_IMMEDIATELY;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_HTTP_CACHE_ENABLE;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_ON_DEVICE_AUCTION_SHOULD_USE_UNIFIED_TABLES;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.adservices.adid.AdId;
import android.adservices.adid.AdIdManager;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.adservices.utils.ScenarioDispatcher;
import android.adservices.utils.ScenarioDispatcherFactory;
import android.adservices.utils.Scenarios;
import android.net.Uri;
import android.util.Log;

import com.android.adservices.common.AdServicesOutcomeReceiverForTests;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;
import com.android.adservices.shared.testing.annotations.SetLongFlag;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SetFlagDisabled(KEY_FLEDGE_HTTP_CACHE_ENABLE)
@SetFlagDisabled(KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED)
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
public final class AdSelectionTest extends FledgeDebuggableScenarioTest {

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
        testAdSelectionHelper(mCustomAudienceClient, mAdSelectionClient);
    }

    @Test
    public void testAdSelection_withBiddingAndScoringLogic_happyPath_usingGetMethod()
            throws Exception {
        testAdSelectionHelper(
                mCustomAudienceClientUsingGetMethod, mAdSelectionClientUsingGetMethod);
    }

    private void testAdSelectionHelper(
            AdvertisingCustomAudienceClient customAudienceClient,
            AdSelectionClient adSelectionClient)
            throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-default.json"));
        AdSelectionConfig adSelectionConfig =
                makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(customAudienceClient, SHIRTS_CA);
            AdSelectionOutcome result = doSelectAds(adSelectionClient, adSelectionConfig);
            assertThat(result.hasOutcome()).isTrue();
        } finally {
            leaveCustomAudience(customAudienceClient, SHIRTS_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /**
     * {@link AdSelectionTest#testAdSelection_withBiddingAndScoringLogic_happyPath} with flag {@link
     * KEY_FLEDGE_ON_DEVICE_AUCTION_SHOULD_USE_UNIFIED_TABLES} turned on.
     */
    @Test
    @SetFlagEnabled(KEY_FLEDGE_ON_DEVICE_AUCTION_SHOULD_USE_UNIFIED_TABLES)
    @SetFlagEnabled(KEY_FLEDGE_CPC_BILLING_ENABLED)
    public void testAdSelection_withUnifiedTable_withBiddingAndScoringLogic_happyPath()
            throws Exception {
        testAdSelection_withAdCostInUrl_happyPath();
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
    @SetIntegerFlag(name = KEY_FLEDGE_AD_SELECTION_BIDDING_LOGIC_JS_VERSION, value = 3)
    public void testAdSelection_withBiddingLogicV3_happyPath() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-119.json"));
        AdSelectionConfig adSelectionConfig =
                makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(SHOES_CA);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            assertThat(result.hasOutcome()).isTrue();
            assertThat(result.getRenderUri()).isNotNull();
        } finally {
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
    @SetFlagEnabled(KEY_FLEDGE_CPC_BILLING_ENABLED)
    public void testAdSelection_withAdCostInUrl_happyPath() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-160.json"));
        AdSelectionConfig adSelectionConfig =
                makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());
        long adSelectionId;

        try {
            joinCustomAudience(SHOES_CA);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            adSelectionId = result.getAdSelectionId();
            assertThat(result.hasOutcome()).isTrue();
            assertThat(result.getRenderUri()).isNotNull();
        } finally {
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
    @SetFlagEnabled(KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED)
    @SetFlagEnabled(KEY_FLEDGE_CPC_BILLING_ENABLED)
    public void testAdSelection_withAdCostInUrl_adCostIsReported() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-161.json"));
        AdSelectionConfig adSelectionConfig =
                makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());
        long adSelectionId;

        try {
            joinCustomAudience(SHOES_CA);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            adSelectionId = result.getAdSelectionId();
            doReportImpression(adSelectionId, adSelectionConfig);
            doReportEvent(adSelectionId, "click");
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /** Test that ad selection fails with an expired custom audience. */
    @Test
    public void testAdSelection_withShortlyExpiringCustomAudience_selectAdsThrowsException()
            throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-default.json"));
        AdSelectionConfig config = makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());
        CustomAudience customAudience =
                makeCustomAudience(SHOES_CA)
                        .setExpirationTime(Instant.now().plus(5, ChronoUnit.SECONDS))
                        .build();

        joinCustomAudience(customAudience);
        Log.d(LOGCAT_TAG_FLEDGE, "Joined custom audience");
        // Make a call to verify ad selection succeeds before timing out.
        mAdSelectionClient.selectAds(config).get(TIMEOUT, TimeUnit.SECONDS);
        Thread.sleep(7000);

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
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-default.json"));
        AdSelectionFromOutcomesConfig config =
                new AdSelectionFromOutcomesConfig.Builder()
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        dispatcher.getBaseAddressWithPrefix().getHost()))
                        .setAdSelectionIds(List.of())
                        .setSelectionLogicUri(
                                Uri.parse(
                                        dispatcher.getBaseAddressWithPrefix()
                                                + Scenarios.MEDIATION_LOGIC_PATH))
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
    @SetIntegerFlag(name = KEY_FLEDGE_AD_SELECTION_BIDDING_LOGIC_JS_VERSION, value = 3)
    @SetLongFlag(name = KEY_AD_ID_FETCHER_TIMEOUT_MS, value = AD_ID_FETCHER_TIMEOUT)
    @SetFlagEnabled(KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_ENABLED)
    @SetFlagEnabled(KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORT_SEND_IMMEDIATELY)
    public void testAdSelection_withDebugReporting_happyPath() throws Exception {
        assumeTrue(isAdIdSupported());
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-164.json"));
        AdSelectionConfig adSelectionConfig =
                makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(SHOES_CA);
            joinCustomAudience(SHIRTS_CA);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            assertThat(result.hasOutcome()).isTrue();
        } finally {
            leaveCustomAudience(SHOES_CA);
            leaveCustomAudience(SHIRTS_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /** Test that buyer and seller receive win and loss debug reports for bids = 0.0. */
    @Test
    @SetIntegerFlag(name = KEY_FLEDGE_AD_SELECTION_BIDDING_LOGIC_JS_VERSION, value = 3)
    @SetLongFlag(name = KEY_AD_ID_FETCHER_TIMEOUT_MS, value = AD_ID_FETCHER_TIMEOUT)
    @SetFlagEnabled(KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_ENABLED)
    @SetFlagEnabled(KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORT_SEND_IMMEDIATELY)
    public void testAdSelection_withDebugReportingIsSentForZeroBid() throws Exception {
        assumeTrue(isAdIdSupported());
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-debug-reporting-zero-bid.json"));
        AdSelectionConfig adSelectionConfig =
                makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(SHOES_CA);
            joinCustomAudience(SHIRTS_CA);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            assertThat(result.hasOutcome()).isTrue();
        } finally {
            leaveCustomAudience(SHOES_CA);
            leaveCustomAudience(SHIRTS_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /**
     * Test that buyer and seller do not receive win and loss debug reports if the feature is
     * disabled (Remarketing CUJ 165).
     */
    @Test
    @SetIntegerFlag(name = KEY_FLEDGE_AD_SELECTION_BIDDING_LOGIC_JS_VERSION, value = 3)
    public void testAdSelection_withDebugReportingDisabled_doesNotSend() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-165.json"));
        AdSelectionConfig adSelectionConfig =
                makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(SHOES_CA);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            assertThat(result.hasOutcome()).isTrue();
        } finally {
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
    @SetIntegerFlag(name = KEY_FLEDGE_AD_SELECTION_BIDDING_LOGIC_JS_VERSION, value = 3)
    @SetLongFlag(name = KEY_AD_ID_FETCHER_TIMEOUT_MS, value = AD_ID_FETCHER_TIMEOUT)
    @SetFlagEnabled(KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_ENABLED)
    @SetFlagEnabled(KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORT_SEND_IMMEDIATELY)
    public void testAdSelection_withDebugReportingAndRejectReason_happyPath() throws Exception {
        assumeTrue(isAdIdSupported());
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-170.json"));
        AdSelectionConfig adSelectionConfig =
                makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(SHOES_CA);
            joinCustomAudience(SHIRTS_CA);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            assertThat(result.hasOutcome()).isTrue();
        } finally {
            leaveCustomAudience(SHOES_CA);
            leaveCustomAudience(SHIRTS_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    @Test
    public void testAdSelection_withHighLatencyBackend_doesNotWinAuction() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-053.json"));
        AdSelectionConfig config = makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(SHIRTS_CA);
            Exception selectAdsException =
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                    mAdSelectionClient
                                            .selectAds(config)
                                            .get(TIMEOUT, TimeUnit.SECONDS));
            assertThat(selectAdsException.getCause()).isInstanceOf(TimeoutException.class);
        } finally {
            leaveCustomAudience(SHIRTS_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    @Test
    public void testAdSelection_withInvalidScoringUrl_doesNotWinAuction() throws Exception {
        // ScenarioDispatcher returns 404 for all paths which are not setup from the json file and
        // we didn't configure a scoring logic url.
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-invalid-scoring-logic-url.json"));
        AdSelectionConfig config = makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(SHIRTS_CA);
            Exception selectAdsException =
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                    mAdSelectionClient
                                            .selectAds(config)
                                            .get(TIMEOUT, TimeUnit.SECONDS));
            assertThat(
                            selectAdsException.getCause() instanceof TimeoutException
                                    || selectAdsException.getCause()
                                            instanceof IllegalStateException)
                    .isTrue();
        } finally {
            leaveCustomAudience(SHIRTS_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    private boolean isAdIdSupported() {
        AdIdManager adIdManager;
        AdServicesOutcomeReceiverForTests<AdId> callback =
                new AdServicesOutcomeReceiverForTests<>();
        try {
            adIdManager = AdIdManager.get(sContext);
            adIdManager.getAdId(MoreExecutors.directExecutor(), callback);
        } catch (IllegalStateException e) {
            Log.d(
                    LOGCAT_TAG_FLEDGE,
                    "isAdIdAvailable(): IllegalStateException detected in AdId manager.");
            return false;
        }

        boolean isAdIdAvailable;
        try {
            AdId result = callback.assertSuccess();
            isAdIdAvailable =
                    !Objects.isNull(result)
                            && !result.isLimitAdTrackingEnabled()
                            && !result.getAdId().equals(AdId.ZERO_OUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.d(
                    LOGCAT_TAG_FLEDGE,
                    "isAdIdSupported(): failed to get AdId due to InterruptedException.");
            isAdIdAvailable = false;
        }

        Log.d(LOGCAT_TAG_FLEDGE, String.format("isAdIdSupported(): %b", isAdIdAvailable));
        return isAdIdAvailable;
    }
}
