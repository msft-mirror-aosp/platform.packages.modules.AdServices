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

package android.adservices.test.scenario.adservices.fledge;


import android.Manifest;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.GetAdSelectionDataOutcome;
import android.adservices.adselection.GetAdSelectionDataRequest;
import android.adservices.adselection.PersistAdSelectionResultRequest;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.adservices.test.scenario.adservices.fledge.utils.CustomAudienceTestFixture;
import android.adservices.test.scenario.adservices.fledge.utils.FakeAdExchangeServer;
import android.adservices.test.scenario.adservices.fledge.utils.SelectAdResponse;
import android.adservices.test.scenario.adservices.utils.SelectAdsFlagRule;
import android.platform.test.option.StringOption;
import android.platform.test.rule.CleanPackageRule;
import android.platform.test.rule.KillAppsRule;
import android.platform.test.scenario.annotation.Scenario;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.service.FlagsConstants;

import com.google.common.io.BaseEncoding;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Scenario
@RunWith(JUnit4.class)
public class PtbM11RampServerAuctionSimulationTest extends ServerAuctionE2ETestBase {
    private static final String TAG = "PtbM11SimulationTest";

    private static final String CONTEXTUAL_SIGNALS_ONE_BUYER = "PtbContextualSignals.json";
    private static final String CUSTOM_AUDIENCE_ONE_CA_ONE_AD = "PtbCustomAudienceOneCaOneAd.json";

    @ClassRule
    public static StringOption serverUrlOption =
            new StringOption("server-url").setRequired(true).setDefault("");

    @ClassRule
    public static StringOption coordinatorUrlOption =
            new StringOption("coordinator-url").setRequired(true).setDefault("");

    @ClassRule
    public static StringOption sellerOption =
            new StringOption("seller").setRequired(true).setDefault("");

    @ClassRule
    public static StringOption winnerDomainOption =
            new StringOption("winner-domain").setRequired(true).setDefault("");

    private static final boolean SERVER_RESPONSE_LOGGING_ENABLED = true;

    private String getCoordinator() {
        return coordinatorUrlOption.get();
    }

    private String getServer() {
        return serverUrlOption.get();
    }

    private String getSeller() {
        return sellerOption.get();
    }

    private String getAdWinnerDomain() {
        return winnerDomainOption.get();
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Rule
    public RuleChain rules =
            RuleChain.outerRule(
                            new KillAppsRule(
                                    AdservicesTestHelper.getAdServicesPackageName(CONTEXT)))
                    .around(
                            // CleanPackageRule should not execute after each test method because
                            // there's a chance it interferes with ShowmapSnapshotListener snapshot
                            // at the end of the test, impacting collection of memory metrics for
                            // AdServices process.
                            new CleanPackageRule(
                                    AdservicesTestHelper.getAdServicesPackageName(CONTEXT),
                                    /* clearOnStarting= */ true,
                                    /* clearOnFinished= */ false))
                    .around(new SelectAdsFlagRule());

    /** Perform the class-wide required setup. */
    @BeforeClass
    public static void setupBeforeClass() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.WRITE_DEVICE_CONFIG,
                        Manifest.permission.WRITE_ALLOWLISTED_DEVICE_CONFIG);
    }

    @Rule
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.newInstance()
                    .setAllLogcatTags()
                    .setFlag(
                            FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_URI,
                            getCoordinator());

    /**
     * Warm up servers to reduce flakiness.
     *
     * <p>B&A servers often send responses if contacted after a while. Warming up with a couple of
     * calls should greatly reduce this flakiness.
     */
    @Before
    public void warmup() throws Exception {

        makeWarmUpNetworkCall(getCoordinator());

        byte[] getAdSelectionData =
                warmupBiddingAuctionServer(
                        CUSTOM_AUDIENCE_ONE_CA_ONE_AD,
                        getSeller(),
                        CONTEXTUAL_SIGNALS_ONE_BUYER,
                        getServer(),
                        SERVER_RESPONSE_LOGGING_ENABLED);
        Thread.sleep(2000L);

        runServerAuction(
                CONTEXTUAL_SIGNALS_ONE_BUYER,
                getAdSelectionData,
                getServer(),
                SERVER_RESPONSE_LOGGING_ENABLED);
        Thread.sleep(2000L);
    }

    /**
     * Runs the basic simulation test to ensure that B&A can parse and return a response with the
     * enrolled PTB seller, buyer and a coordinator key that's older than 45 days
     */
    @Test
    public void runAdSelection_ptb_basicSimulation_test() throws Exception {
        List<CustomAudience> customAudiences =
                CustomAudienceTestFixture.readCustomAudiences(CUSTOM_AUDIENCE_ONE_CA_ONE_AD);

        CustomAudienceTestFixture.joinCustomAudiences(customAudiences);

        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(AdTechIdentifier.fromString(getSeller()))
                        .build();

        GetAdSelectionDataOutcome outcome =
                retryOnCondition(
                        () ->
                                AD_SELECTION_CLIENT
                                        .getAdSelectionData(request)
                                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                        matchOnTimeoutExecutionException(),
                        /* maxRetries= */ 3,
                        /* retryIntervalMillis= */ 2000L,
                        "getAdSelectionData");

        SelectAdResponse selectAdResponse =
                FakeAdExchangeServer.runServerAuction(
                        CONTEXTUAL_SIGNALS_ONE_BUYER,
                        outcome.getAdSelectionData(),
                        getServer(),
                        SERVER_RESPONSE_LOGGING_ENABLED);

        PersistAdSelectionResultRequest persistAdSelectionResultRequest =
                new PersistAdSelectionResultRequest.Builder()
                        .setAdSelectionId(outcome.getAdSelectionId())
                        .setSeller(AdTechIdentifier.fromString(getSeller()))
                        .setAdSelectionResult(
                                BaseEncoding.base64()
                                        .decode(selectAdResponse.auctionResultCiphertext))
                        .build();

        AdSelectionOutcome adSelectionOutcome =
                AD_SELECTION_CLIENT
                        .persistAdSelectionResult(persistAdSelectionResultRequest)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        CustomAudienceTestFixture.leaveCustomAudience(customAudiences);

        Assert.assertTrue(
                adSelectionOutcome.getRenderUri().toString().contains(getAdWinnerDomain()));
    }
}
