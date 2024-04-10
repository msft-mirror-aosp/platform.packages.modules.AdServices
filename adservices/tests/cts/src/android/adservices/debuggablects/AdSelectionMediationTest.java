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

import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_HTTP_CACHE_ENABLE;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.utils.FledgeScenarioTest;
import android.adservices.utils.ScenarioDispatcher;
import android.adservices.utils.Scenarios;
import android.net.Uri;

import com.android.adservices.service.PhFlagsFixture;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SetFlagDisabled(KEY_FLEDGE_HTTP_CACHE_ENABLE)
public class AdSelectionMediationTest extends FledgeScenarioTest {

    /** Test sellers can orchestrate waterfall mediation. Remarketing CUJ 069. */
    @Test
    public void testSelectAds_withAdSelectionFromOutcomes_happyPath() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-mediation.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);

        try {
            joinCustomAudience(SHIRTS_CA);
            AdSelectionOutcome result =
                    doSelectAds(
                            makeAdSelectionFromOutcomesConfig()
                                    .setAdSelectionIds(
                                            List.of(
                                                    doSelectAds(makeAdSelectionConfig())
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
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-075.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig config = makeAdSelectionConfig();

        try {
            joinCustomAudience(SHIRTS_CA);
            long adSelectionId = doSelectAds(config).getAdSelectionId();
            AdSelectionOutcome result =
                    doSelectAds(
                            makeAdSelectionFromOutcomesConfig()
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
    public void testSelectAdsWithUnifiedTable_withImpressionReporting_eventsAreReceived()
            throws Exception {
        overrideShouldUseUnifiedTable(true);
        try {
            testSelectAds_withImpressionReporting_eventsAreReceived();
        } finally {
            overrideShouldUseUnifiedTable(false);
        }
    }

    /** Test buyers must be enrolled in order to participate in waterfall mediation. For CUJ 080. */
    @Test
    public void testAdSelectionFromOutcome_buyerMustEnrolledToParticipate() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-mediation.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);

        try {
            PhFlagsFixture.overrideFledgeEnrollmentCheck(false);
            joinCustomAudience(SHIRTS_CA);
            AdSelectionOutcome adSelectionOutcome1 = doSelectAds(makeAdSelectionConfig());
            long adSelectionId = adSelectionOutcome1.getAdSelectionId();

            final AdSelectionFromOutcomesConfig fromOutcomesConfigEnrollmentFail =
                    makeAdSelectionFromOutcomesConfig()
                            .setSeller(AdTechIdentifier.fromString("fakeadtech.com"))
                            .setAdSelectionIds(List.of(adSelectionId))
                            .build();

            PhFlagsFixture.overrideFledgeEnrollmentCheck(true);
            Exception e =
                    Assert.assertThrows(
                            ExecutionException.class,
                            () -> doSelectAds(fromOutcomesConfigEnrollmentFail));
            assertThat(e.getCause() instanceof SecurityException).isTrue();

            AdSelectionFromOutcomesConfig fromOutcomesConfig =
                    makeAdSelectionFromOutcomesConfig()
                            .setAdSelectionIds(List.of(adSelectionId))
                            .build();
            PhFlagsFixture.overrideFledgeEnrollmentCheck(false);
            AdSelectionOutcome result = doSelectAds(fromOutcomesConfig);
            assertThat(result.hasOutcome()).isTrue();
        } finally {
            leaveCustomAudience(SHIRTS_CA);
            PhFlagsFixture.overrideFledgeEnrollmentCheck(false);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    private AdSelectionOutcome doSelectAds(AdSelectionFromOutcomesConfig config)
            throws ExecutionException, InterruptedException, TimeoutException {
        return mAdSelectionClient.selectAds(config).get(TIMEOUT, TimeUnit.SECONDS);
    }

    private AdSelectionFromOutcomesConfig.Builder makeAdSelectionFromOutcomesConfig() {
        return new AdSelectionFromOutcomesConfig.Builder()
                .setSelectionSignals(AdSelectionSignals.fromString("{\"bidFloor\": 2.0}"))
                .setSelectionLogicUri(
                        Uri.parse(getServerBaseAddress() + Scenarios.MEDIATION_LOGIC_PATH))
                .setSeller(mAdTechIdentifier)
                .setAdSelectionIds(List.of());
    }
}
