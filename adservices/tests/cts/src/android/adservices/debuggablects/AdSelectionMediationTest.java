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

package android.adservices.debuggablects;

import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_HTTP_CACHE_ENABLE;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_ON_DEVICE_AUCTION_SHOULD_USE_UNIFIED_TABLES;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.utils.ScenarioDispatcher;
import android.adservices.utils.ScenarioDispatcherFactory;
import android.adservices.utils.Scenarios;
import android.net.Uri;

import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;

import org.junit.Assert;
import org.junit.Test;

import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SetFlagDisabled(KEY_FLEDGE_HTTP_CACHE_ENABLE)
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
public final class AdSelectionMediationTest extends FledgeDebuggableScenarioTest {

    /** Test sellers can orchestrate waterfall mediation. Remarketing CUJ 069. */
    @Test
    public void testSelectAds_withAdSelectionFromOutcomes_happyPath() throws Exception {
        testSelectAds_withAdSelectionFromOutcomes_happyPath_helper(mAdSelectionClient);
    }

    /**
     * Test sellers can orchestrate waterfall mediation using ad selection client created using get
     * method. Remarketing CUJ 069.
     */
    @Test
    public void testSelectAds_withAdSelectionFromOutcomes_happyPath_usingGetMethod()
            throws Exception {
        testSelectAds_withAdSelectionFromOutcomes_happyPath_helper(
                mAdSelectionClientUsingGetMethod);
    }

    public void testSelectAds_withAdSelectionFromOutcomes_happyPath_helper(
            AdSelectionClient adSelectionClient) throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-mediation.json"));

        try {
            joinCustomAudience(SHIRTS_CA);
            URL baseAddress = dispatcher.getBaseAddressWithPrefix();
            AdSelectionOutcome result =
                    doSelectAds(
                            adSelectionClient,
                            makeAdSelectionFromOutcomesConfig(baseAddress)
                                    .setAdSelectionIds(
                                            List.of(
                                                    doSelectAds(makeAdSelectionConfig(baseAddress))
                                                            .getAdSelectionId()))
                                    .build());
            assertThat(result.hasOutcome()).isTrue();
        } finally {
            leaveCustomAudience(SHIRTS_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /**
     * Test ad impressions are reported to winner buyer/seller after waterfall mediation Remarketing
     * CUJ 075.
     */
    @Test
    public void testSelectAds_withImpressionReporting_eventsAreReceived() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-075.json"));
        AdSelectionConfig config = makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(SHIRTS_CA);
            long adSelectionId = doSelectAds(config).getAdSelectionId();
            AdSelectionOutcome result =
                    doSelectAds(
                            makeAdSelectionFromOutcomesConfig(dispatcher.getBaseAddressWithPrefix())
                                    .setAdSelectionIds(List.of(adSelectionId))
                                    .build());
            assertThat(result.hasOutcome()).isTrue();
            doReportImpression(adSelectionId, config);
        } finally {
            leaveCustomAudience(SHIRTS_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /**
     * CUJ 198: Impressions are reported to winner buyer/seller after waterfall mediation while
     * using unified tables.
     */
    @Test
    @SetFlagEnabled(KEY_FLEDGE_ON_DEVICE_AUCTION_SHOULD_USE_UNIFIED_TABLES)
    public void testSelectAdsWithUnifiedTable_withImpressionReporting_eventsAreReceived()
            throws Exception {
        testSelectAds_withImpressionReporting_eventsAreReceived();
    }

    /** Test buyers must be enrolled in order to participate in waterfall mediation. For CUJ 080. */
    @Test
    public void testAdSelectionFromOutcome_buyerMustEnrolledToParticipate() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-mediation.json"));

        try {
            flags.setFlag(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK, true);
            joinCustomAudience(SHIRTS_CA);
            AdSelectionOutcome adSelectionOutcome1 =
                    doSelectAds(makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix()));
            long adSelectionId = adSelectionOutcome1.getAdSelectionId();

            final AdSelectionFromOutcomesConfig fromOutcomesConfigEnrollmentFail =
                    makeAdSelectionFromOutcomesConfig(dispatcher.getBaseAddressWithPrefix())
                            .setSeller(AdTechIdentifier.fromString("fakeadtech.com"))
                            .setAdSelectionIds(List.of(adSelectionId))
                            .build();

            flags.setFlag(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK, false);
            Exception e =
                    Assert.assertThrows(
                            ExecutionException.class,
                            () -> doSelectAds(fromOutcomesConfigEnrollmentFail));
            assertThat(e.getCause() instanceof SecurityException).isTrue();

            AdSelectionFromOutcomesConfig fromOutcomesConfig =
                    makeAdSelectionFromOutcomesConfig(dispatcher.getBaseAddressWithPrefix())
                            .setAdSelectionIds(List.of(adSelectionId))
                            .build();
            flags.setFlag(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK, true);
            AdSelectionOutcome result = doSelectAds(fromOutcomesConfig);
            assertThat(result.hasOutcome()).isTrue();
        } finally {
            leaveCustomAudience(SHIRTS_CA);
            flags.setFlag(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK, true);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    private AdSelectionOutcome doSelectAds(AdSelectionFromOutcomesConfig config)
            throws ExecutionException, InterruptedException, TimeoutException {
        return doSelectAds(mAdSelectionClient, config);
    }

    private AdSelectionOutcome doSelectAds(
            AdSelectionClient adSelectionClient, AdSelectionFromOutcomesConfig config)
            throws ExecutionException, InterruptedException, TimeoutException {
        return adSelectionClient.selectAds(config).get(TIMEOUT, TimeUnit.SECONDS);
    }

    private AdSelectionFromOutcomesConfig.Builder makeAdSelectionFromOutcomesConfig(
            URL serverBaseAddressWithPrefix) {
        return new AdSelectionFromOutcomesConfig.Builder()
                .setSelectionSignals(AdSelectionSignals.fromString("{\"bidFloor\": 2.0}"))
                .setSelectionLogicUri(
                        Uri.parse(
                                serverBaseAddressWithPrefix.toString()
                                        + Scenarios.MEDIATION_LOGIC_PATH))
                .setSeller(AdTechIdentifier.fromString(serverBaseAddressWithPrefix.getHost()))
                .setAdSelectionIds(List.of());
    }
}
