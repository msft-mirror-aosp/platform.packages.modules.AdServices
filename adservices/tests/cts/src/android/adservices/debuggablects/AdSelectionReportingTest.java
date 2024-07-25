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

import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_DATA_VERSION_HEADER_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_HTTP_CACHE_ENABLE;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_ON_DEVICE_AUCTION_SHOULD_USE_UNIFIED_TABLES;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.common.AdTechIdentifier;
import android.adservices.utils.ScenarioDispatcher;
import android.adservices.utils.ScenarioDispatcherFactory;

import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;

import org.junit.Test;

import java.util.concurrent.ExecutionException;

/** End-to-end test for report impression. */
@SetFlagEnabled(KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED)
@SetFlagDisabled(KEY_FLEDGE_HTTP_CACHE_ENABLE)
@SetFlagDisabled(KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED)
public final class AdSelectionReportingTest extends FledgeDebuggableScenarioTest {

    @Test
    public void testReportImpression_defaultAdSelection_happyPath() throws Exception {
        testReportImpression_defaultAdSelection_helper(mAdSelectionClient);
    }

    @Test
    public void testReportImpression_defaultAdSelection_happyPath_usingGetMethod()
            throws Exception {
        testReportImpression_defaultAdSelection_helper(mAdSelectionClientUsingGetMethod);
    }

    private void testReportImpression_defaultAdSelection_helper(AdSelectionClient adSelectionClient)
            throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-reportimpression.json"));
        AdSelectionConfig adSelectionConfig =
                makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(SHOES_CA);
            doReportImpression(
                    adSelectionClient,
                    doSelectAds(adSelectionConfig).getAdSelectionId(),
                    adSelectionConfig);
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    @Test
    public void testReportImpression_buyerRequestFails_sellerRequestSucceeds() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-008.json"));
        AdSelectionConfig adSelectionConfig =
                makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(SHOES_CA);
            doReportImpression(
                    doSelectAds(adSelectionConfig).getAdSelectionId(), adSelectionConfig);
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
        assertThat(dispatcher.getCalledPaths())
                .containsNoneIn(dispatcher.getVerifyNotCalledPaths());
    }

    @Test
    public void testReportImpression_buyerLogicTimesOut_reportingFails() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-060.json"));
        AdSelectionConfig config = makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(SHOES_CA);
            AdSelectionOutcome adSelectionOutcome = doSelectAds(config);
            Exception exception =
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                    doReportImpression(
                                            adSelectionOutcome.getAdSelectionId(), config));
            assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
        assertThat(dispatcher.getCalledPaths())
                .containsNoneIn(dispatcher.getVerifyNotCalledPaths());
    }

    @Test
    public void testReportImpression_withMismatchedAdTechUri_sellerRequestFails() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-068.json"));
        AdSelectionConfig config =
                makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix())
                        .cloneToBuilder()
                        .setSeller(AdTechIdentifier.fromString("localhost:12345"))
                        .build();

        try {
            joinCustomAudience(SHOES_CA);
            Exception selectAdsException =
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                    doReportImpression(
                                            doSelectAds(config).getAdSelectionId(), config));
            assertThat(selectAdsException.getCause()).isInstanceOf(IllegalArgumentException.class);
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsNoneIn(dispatcher.getVerifyNotCalledPaths());
    }

    @Test
    public void testReportEvent_registerBuyerAndSellerBeacons_happyPath() throws Exception {
        testReportEvent_registerBuyerAndSellerBeacons_happyPath_helper(mAdSelectionClient);
    }

    @Test
    public void testReportEvent_registerBuyerAndSellerBeacons_happyPath_usingGetMethod()
            throws Exception {
        testReportEvent_registerBuyerAndSellerBeacons_happyPath_helper(
                mAdSelectionClientUsingGetMethod);
    }

    public void testReportEvent_registerBuyerAndSellerBeacons_happyPath_helper(
            AdSelectionClient adSelectionClient) throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-beacon.json"));
        AdSelectionConfig config = makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(SHOES_CA);
            long adSelectionId = doSelectAds(adSelectionClient, config).getAdSelectionId();
            doReportImpression(adSelectionClient, adSelectionId, config);
            doReportEvent(adSelectionClient, adSelectionId, "click");
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    @Test
    public void testReportEvent_failToRegisterBuyerBeacon_sellerBeaconSucceeds() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-beacon-buyer-failure.json"));
        AdSelectionConfig config = makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(SHOES_CA);
            long adSelectionId = doSelectAds(config).getAdSelectionId();
            doReportImpression(adSelectionId, config);
            doReportEvent(adSelectionId, "click");
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
        assertThat(dispatcher.getCalledPaths())
                .containsNoneIn(dispatcher.getVerifyNotCalledPaths());
    }

    @Test
    public void testReportEvent_failToRegisterSellerBeacon_buyerBeaconSucceeds() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-beacon-seller-failure.json"));
        AdSelectionConfig config = makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(SHOES_CA);
            long adSelectionId = doSelectAds(config).getAdSelectionId();
            doReportImpression(adSelectionId, config);
            doReportEvent(adSelectionId, "click");
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
        assertThat(dispatcher.getCalledPaths())
                .containsNoneIn(dispatcher.getVerifyNotCalledPaths());
    }

    @Test
    public void testReportEvent_withMismatchedSellerAdTech_buyerStillCalled() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-beacon-seller-failure.json"));
        AdSelectionConfig config = makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(SHOES_CA);
            long adSelectionId = doSelectAds(config).getAdSelectionId();
            doReportImpression(adSelectionId, config);
            doReportEvent(adSelectionId, "click");
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
        assertThat(dispatcher.getCalledPaths())
                .containsNoneIn(dispatcher.getVerifyNotCalledPaths());
    }

    @Test
    public void testReportEvent_withMismatchedBuyerAdTech_sellerStillCalled() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-beacon-buyer-failure.json"));
        AdSelectionConfig config = makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(SHOES_CA);
            long adSelectionId = doSelectAds(config).getAdSelectionId();
            doReportImpression(adSelectionId, config);
            doReportEvent(adSelectionId, "click");
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
        assertThat(dispatcher.getCalledPaths())
                .containsNoneIn(dispatcher.getVerifyNotCalledPaths());
    }

    @Test
    public void testReportEvent_withBuyerBeacon_onlyReportsForViewInteraction() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-101.json"));
        AdSelectionConfig config = makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(SHOES_CA);
            long adSelectionId = doSelectAds(config).getAdSelectionId();
            doReportImpression(adSelectionId, config);
            doReportEvent(adSelectionId, "click");
            doReportEvent(adSelectionId, "view");
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
        assertThat(dispatcher.getCalledPaths())
                .containsNoneIn(dispatcher.getVerifyNotCalledPaths());
    }

    @Test
    public void testReportImpression_biddingLogicDownloadTimesOut_throwsException()
            throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-061.json"));
        AdSelectionConfig config = makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(SHOES_CA);
            Exception exception =
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                    doReportImpression(
                                            doSelectAds(config).getAdSelectionId(), config));
            assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
        } finally {
            leaveCustomAudience(SHOES_CA);
        }
    }

    @Test
    @SetFlagEnabled(KEY_FLEDGE_DATA_VERSION_HEADER_ENABLED)
    public void testAdSelection_withDataVersionHeader() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-data-version-header.json"));
        AdSelectionConfig config = makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(SHOES_CA);
            doReportImpression(doSelectAds(config).getAdSelectionId(), config);
            assertThat(dispatcher.getCalledPaths())
                    .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
        } finally {
            leaveCustomAudience(SHOES_CA);
        }
    }

    @Test
    @SetFlagEnabled(KEY_FLEDGE_DATA_VERSION_HEADER_ENABLED)
    public void testAdSelection_withDataVersionHeader_skipsBuyerExceeds8Bits() throws Exception {
        String filePath = "scenarios/remarketing-cuj-data-version-header-buyer-exceeds-8-bits.json";
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(filePath));
        AdSelectionConfig config = makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(SHOES_CA);
            doReportImpression(doSelectAds(config).getAdSelectionId(), config);
            assertThat(dispatcher.getCalledPaths())
                    .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
            assertThat(dispatcher.getCalledPaths())
                    .containsNoneIn(dispatcher.getVerifyNotCalledPaths());
        } finally {
            leaveCustomAudience(SHOES_CA);
        }
    }

    @Test
    @SetFlagEnabled(KEY_FLEDGE_DATA_VERSION_HEADER_ENABLED)
    public void testAdSelection_withDataVersionHeader_skipsSellerExceeds8Bits() throws Exception {
        String filePath =
                "scenarios/remarketing-cuj-data-version-header-seller-exceeds-8-bits.json";
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(filePath));
        AdSelectionConfig config = makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(SHOES_CA);
            doReportImpression(doSelectAds(config).getAdSelectionId(), config);
            assertThat(dispatcher.getCalledPaths())
                    .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
            assertThat(dispatcher.getCalledPaths())
                    .containsNoneIn(dispatcher.getVerifyNotCalledPaths());
        } finally {
            leaveCustomAudience(SHOES_CA);
        }
    }

    @Test
    @SetFlagEnabled(KEY_FLEDGE_DATA_VERSION_HEADER_ENABLED)
    @SetFlagEnabled(KEY_FLEDGE_ON_DEVICE_AUCTION_SHOULD_USE_UNIFIED_TABLES)
    public void testAdSelection_withDataVersionHeader_unifiedTable() throws Exception {
        testAdSelection_withDataVersionHeader();
    }
}
