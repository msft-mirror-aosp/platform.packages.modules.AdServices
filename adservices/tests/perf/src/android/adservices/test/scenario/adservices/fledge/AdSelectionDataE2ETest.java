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

package android.adservices.test.scenario.adservices.fledge;


import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

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
import android.provider.DeviceConfig;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdservicesTestHelper;

import com.google.common.io.BaseEncoding;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Scenario
@RunWith(JUnit4.class)
public class AdSelectionDataE2ETest extends ServerAuctionE2ETestBase {
    private static final String TAG = "AdSelectionDataE2ETest";

    private static final String CONTEXTUAL_SIGNALS_ONE_BUYER = "ContextualSignalsOneBuyer.json";
    private static final String CONTEXTUAL_SIGNALS_CONTEXTUAL_WINNER =
            "ContextualSignalsContextualWinner.json";

    @ClassRule
    public static StringOption serverUrlOption =
            new StringOption("server-url").setRequired(true).setDefault("");

    @ClassRule
    public static StringOption coordinatorUrlOption =
            new StringOption("coordinator-url").setRequired(true).setDefault("");

    private static final String CONTEXTUAL_SIGNALS_FIVE_BUYERS = "ContextualSignalsFiveBuyers.json";
    private static final String CONTEXTUAL_SIGNALS_TWO_BUYERS = "ContextualSignalsTwoBuyers.json";
    private static final String CUSTOM_AUDIENCE_ONE_BUYER_ONE_CA_ONE_AD =
            "CustomAudienceOneBuyerOneCaOneAd.json";
    private static final String CUSTOM_AUDIENCE_FIVE_BUYERS_MULTIPLE_CA =
            "CustomAudienceServerAuctionFiveBuyersMultipleCa.json";

    private static final String CUSTOM_AUDIENCE_TWO_BUYERS_MULTIPLE_CA =
            "CustomAudienceServerAuctionTwoBuyersMultipleCa.json";
    private static final String CUSTOM_AUDIENCE_NO_AD_RENDER_ID = "CustomAudienceNoAdRenderId.json";
    private static final String SELLER = "ba-seller-5jyy5ulagq-uc.a.run.app";

    private static final boolean SERVER_RESPONSE_LOGGING_ENABLED = true;

    private static final String AD_WINNER_DOMAIN = "https://ba-buyer-5jyy5ulagq-uc.a.run.app/";

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
                                    /* clearOnStarting = */ true,
                                    /* clearOnFinished = */ false))
                    .around(new SelectAdsFlagRule());

    @BeforeClass
    public static void setupBeforeClass() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.WRITE_DEVICE_CONFIG,
                        Manifest.permission.WRITE_ALLOWLISTED_DEVICE_CONFIG);
    }

    private String getCoordinator() {
        return coordinatorUrlOption.get();
    }

    private String getServer() {
        return serverUrlOption.get();
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    /**
     * Warm up servers to reduce flakiness.
     *
     * <p>B&A servers often send responses if contacted after a while. Warming up with a couple of
     * calls should greatly reduce this flakiness.
     */
    @Before
    public void warmup() throws Exception {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "fledge_auction_server_auction_key_fetch_uri",
                getCoordinator(),
                false);

        makeWarmUpNetworkCall(getCoordinator());

        // The first warm up call brings ups the sfe
        byte[] getAdSelectionData =
                warmupBiddingAuctionServer(
                        CUSTOM_AUDIENCE_TWO_BUYERS_MULTIPLE_CA,
                        SELLER,
                        CONTEXTUAL_SIGNALS_TWO_BUYERS,
                        getServer(),
                        SERVER_RESPONSE_LOGGING_ENABLED);

        // Wait for a couple of seconds before test execution
        Thread.sleep(2000L);

        // The second warm up call will bring up both the BFEs
        runServerAuction(
                CONTEXTUAL_SIGNALS_TWO_BUYERS,
                getAdSelectionData,
                getServer(),
                SERVER_RESPONSE_LOGGING_ENABLED);

        // Wait for a couple of seconds before test execution
        Thread.sleep(2000L);
    }

    @Test
    public void runAdSelection_oneBuyerOneCaOneAd_dummyData_remarketingWinner() throws Exception {
        List<CustomAudience> customAudiences =
                CustomAudienceTestFixture.readCustomAudiences(
                        CUSTOM_AUDIENCE_ONE_BUYER_ONE_CA_ONE_AD);

        CustomAudienceTestFixture.joinCustomAudiences(customAudiences);

        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(AdTechIdentifier.fromString(SELLER))
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
                        .setSeller(AdTechIdentifier.fromString(SELLER))
                        .setAdSelectionResult(
                                BaseEncoding.base64()
                                        .decode(selectAdResponse.auctionResultCiphertext))
                        .build();

        AdSelectionOutcome adSelectionOutcome =
                AD_SELECTION_CLIENT
                        .persistAdSelectionResult(persistAdSelectionResultRequest)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        CustomAudienceTestFixture.leaveCustomAudience(customAudiences);

        Assert.assertEquals(
                AD_WINNER_DOMAIN + getWinningAdRenderIdForDummyScripts(customAudiences),
                adSelectionOutcome.getRenderUri().toString());
    }

    @Test
    public void runAdSelection_twoBuyersMultipleCa_dummyData_remarketingWinner() throws Exception {
        List<CustomAudience> customAudiences =
                CustomAudienceTestFixture.readCustomAudiences(
                        CUSTOM_AUDIENCE_TWO_BUYERS_MULTIPLE_CA);

        CustomAudienceTestFixture.joinCustomAudiences(customAudiences);

        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(AdTechIdentifier.fromString(SELLER))
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
                        CONTEXTUAL_SIGNALS_TWO_BUYERS,
                        outcome.getAdSelectionData(),
                        getServer(),
                        SERVER_RESPONSE_LOGGING_ENABLED);

        PersistAdSelectionResultRequest persistAdSelectionResultRequest =
                new PersistAdSelectionResultRequest.Builder()
                        .setAdSelectionId(outcome.getAdSelectionId())
                        .setSeller(AdTechIdentifier.fromString(SELLER))
                        .setAdSelectionResult(
                                BaseEncoding.base64()
                                        .decode(selectAdResponse.auctionResultCiphertext))
                        .build();

        AdSelectionOutcome adSelectionOutcome =
                AD_SELECTION_CLIENT
                        .persistAdSelectionResult(persistAdSelectionResultRequest)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        CustomAudienceTestFixture.leaveCustomAudience(customAudiences);

        Assert.assertEquals(
                AD_WINNER_DOMAIN + getWinningAdRenderIdForDummyScripts(customAudiences),
                adSelectionOutcome.getRenderUri().toString());
    }

    @Test
    @Ignore("b/322323696")
    public void runAdSelection_fiveBuyersMultipleCa_dummyData_remarketingWinner() throws Exception {
        List<CustomAudience> customAudiences =
                CustomAudienceTestFixture.readCustomAudiences(
                        CUSTOM_AUDIENCE_FIVE_BUYERS_MULTIPLE_CA);

        CustomAudienceTestFixture.joinCustomAudiences(customAudiences);

        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(AdTechIdentifier.fromString(SELLER))
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
                        CONTEXTUAL_SIGNALS_FIVE_BUYERS,
                        outcome.getAdSelectionData(),
                        getServer(),
                        SERVER_RESPONSE_LOGGING_ENABLED);

        PersistAdSelectionResultRequest persistAdSelectionResultRequest =
                new PersistAdSelectionResultRequest.Builder()
                        .setAdSelectionId(outcome.getAdSelectionId())
                        .setSeller(AdTechIdentifier.fromString(SELLER))
                        .setAdSelectionResult(
                                BaseEncoding.base64()
                                        .decode(selectAdResponse.auctionResultCiphertext))
                        .build();

        AdSelectionOutcome adSelectionOutcome =
                AD_SELECTION_CLIENT
                        .persistAdSelectionResult(persistAdSelectionResultRequest)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        CustomAudienceTestFixture.leaveCustomAudience(customAudiences);

        Assert.assertEquals(
                AD_WINNER_DOMAIN + getWinningAdRenderIdForDummyScripts(customAudiences),
                adSelectionOutcome.getRenderUri().toString());
    }

    @Test
    public void runAdSelection_noAdRenderId_baReturnsError_ppapiThrowsError() throws Exception {
        List<CustomAudience> customAudiences =
                CustomAudienceTestFixture.readCustomAudiences(CUSTOM_AUDIENCE_NO_AD_RENDER_ID);
        CustomAudienceTestFixture.joinCustomAudiences(customAudiences);

        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(AdTechIdentifier.fromString(SELLER))
                        .setSeller(AdTechIdentifier.fromString(SELLER))
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
                        .setSeller(AdTechIdentifier.fromString(SELLER))
                        .setAdSelectionResult(
                                BaseEncoding.base64()
                                        .decode(selectAdResponse.auctionResultCiphertext))
                        .build();

        // AuctionConfig returns an Error saying no buyer inputs found
        // PPAPI reads that and throws IllegalArgumentException
        // PPAPI returns an IllegalArgumentException but it seems to be getting wrapped in
        // ExecutionException somewhere in the test framework.
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                AD_SELECTION_CLIENT
                                        .persistAdSelectionResult(persistAdSelectionResultRequest)
                                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS));

        assertTrue(exception.getMessage().contains("IllegalArgumentException"));
    }

    @Test
    public void runAdSelection_baReturnsIsChaffTrue_noAdReturnedByPpapi() throws Exception {
        List<CustomAudience> customAudiences =
                CustomAudienceTestFixture.readCustomAudiences(
                        CUSTOM_AUDIENCE_ONE_BUYER_ONE_CA_ONE_AD);
        CustomAudienceTestFixture.joinCustomAudiences(customAudiences);

        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(AdTechIdentifier.fromString(SELLER))
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
                        CONTEXTUAL_SIGNALS_CONTEXTUAL_WINNER,
                        outcome.getAdSelectionData(),
                        getServer(),
                        SERVER_RESPONSE_LOGGING_ENABLED);

        PersistAdSelectionResultRequest persistAdSelectionResultRequest =
                new PersistAdSelectionResultRequest.Builder()
                        .setAdSelectionId(outcome.getAdSelectionId())
                        .setSeller(AdTechIdentifier.fromString(SELLER))
                        .setAdSelectionResult(
                                BaseEncoding.base64()
                                        .decode(selectAdResponse.auctionResultCiphertext))
                        .build();

        AdSelectionOutcome adSelectionOutcome =
                AD_SELECTION_CLIENT
                        .persistAdSelectionResult(persistAdSelectionResultRequest)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        CustomAudienceTestFixture.leaveCustomAudience(customAudiences);

        Assert.assertTrue(adSelectionOutcome.getRenderUri().toString().isEmpty());

    }

    @Test
    public void runAdSelection_oneBuyerOneCaOneAd_dummyData_remarketingWinner_withMediaTypeChanged()
            throws Exception {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "fledge_auction_server_media_type_change_enabled",
                "true",
                false);

        List<CustomAudience> customAudiences =
                CustomAudienceTestFixture.readCustomAudiences(
                        CUSTOM_AUDIENCE_ONE_BUYER_ONE_CA_ONE_AD);

        CustomAudienceTestFixture.joinCustomAudiences(customAudiences);

        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(AdTechIdentifier.fromString(SELLER))
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
                        .setSeller(AdTechIdentifier.fromString(SELLER))
                        .setAdSelectionResult(
                                BaseEncoding.base64()
                                        .decode(selectAdResponse.auctionResultCiphertext))
                        .build();

        AdSelectionOutcome adSelectionOutcome =
                AD_SELECTION_CLIENT
                        .persistAdSelectionResult(persistAdSelectionResultRequest)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        CustomAudienceTestFixture.leaveCustomAudience(customAudiences);

        Assert.assertEquals(
                AD_WINNER_DOMAIN + getWinningAdRenderIdForDummyScripts(customAudiences),
                adSelectionOutcome.getRenderUri().toString());
    }

    private String getWinningAdRenderIdForDummyScripts(List<CustomAudience> customAudiences) {
        for (CustomAudience ca : customAudiences) {
            // Logic obtained from our custom dummy bidding script where we bid the highest
            // for the first ad in the CA winningCA
            if (ca.getName().equals("winningCA")) {
                return ca.getAds().get(0).getAdRenderId();
            }
        }
        return "";
    }
}

