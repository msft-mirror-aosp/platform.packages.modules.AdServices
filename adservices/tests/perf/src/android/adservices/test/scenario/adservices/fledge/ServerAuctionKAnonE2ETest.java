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
import android.net.Uri;
import android.platform.test.option.StringOption;
import android.platform.test.rule.CleanPackageRule;
import android.platform.test.rule.KillAppsRule;
import android.platform.test.scenario.annotation.Scenario;
import android.util.Log;

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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Scenario
@RunWith(JUnit4.class)
public class ServerAuctionKAnonE2ETest extends ServerAuctionE2ETestBase {
    private static final String CONTEXTUAL_SIGNALS_ONE_BUYER = "ContextualSignalsOneBuyer.json";

    private static final String CONTEXTUAL_SIGNALS_TWO_BUYERS = "ContextualSignalsTwoBuyers.json";
    private static final String CUSTOM_AUDIENCE_ONE_BUYER_ONE_CA_ONE_AD =
            "CustomAudienceOneBuyerOneCaOneAd.json";

    private static final String CUSTOM_AUDIENCE_TWO_BUYERS_MULTIPLE_CA =
            "CustomAudienceServerAuctionTwoBuyersMultipleCa.json";
    private static final String SELLER = "ba-seller-5jyy5ulagq-uc.a.run.app";
    private static final boolean SERVER_RESPONSE_LOGGING_ENABLED = true;
    private static final DateTimeFormatter LOG_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private static final String SUCCESS_LOG =
            "Response code is 200. Updating message status to JOINED in database";
    private static final String TAG = "ServerAuctionKAnonE2ETest";

    private static final int EXPONENTIAL_BACKOFF_BASE_SECONDS = 4;

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

    @ClassRule(order = 1)
    public static StringOption joinUrlOption =
            new StringOption("join-url").setRequired(true).setDefault("");

    @ClassRule(order = 2)
    public static StringOption fetchParamsUrlOption =
            new StringOption("fetch-params-url").setRequired(true).setDefault("");

    @ClassRule(order = 3)
    public static StringOption registerClientUrlOption =
            new StringOption("register-client-url").setRequired(true).setDefault("");

    @ClassRule(order = 4)
    public static StringOption keyFetchUrlOption =
            new StringOption("key-fetch-url").setRequired(true).setDefault("");

    @ClassRule(order = 5)
    public static StringOption getTokensUrlOption =
            new StringOption("get-tokens-url").setRequired(true).setDefault("");

    @ClassRule(order = 6)
    public static StringOption joinAuthorityOption =
            new StringOption("join-authority").setRequired(true).setDefault("");

    @ClassRule(order = 7)
    public static StringOption getChallengeUrlOption =
            new StringOption("get-challenge-url").setRequired(true).setDefault("");

    @ClassRule(order = 8)
    public static StringOption serverUrlOption =
            new StringOption("server-url").setRequired(true).setDefault("");

    @ClassRule(order = 9)
    public static StringOption coordinatorUrlOption =
            new StringOption("coordinator-url").setRequired(true).setDefault("");

    @Rule(order = 10)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forKAnonEnabledTests()
                    .setFlag(FlagsConstants.KEY_FLEDGE_KANON_JOIN_URL, joinUrlOption.get())
                    .setFlag(
                            FlagsConstants.KEY_KANON_FETCH_PARAMETERS_URL,
                            fetchParamsUrlOption.get())
                    .setFlag(
                            FlagsConstants.KEY_FLEDGE_KANON_REGISTER_CLIENT_PARAMETERS_URL,
                            registerClientUrlOption.get())
                    .setFlag(
                            FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_JOIN_KEY_FETCH_URI,
                            keyFetchUrlOption.get())
                    .setFlag(
                            FlagsConstants.KEY_FLEDGE_KANON_GET_TOKENS_URL,
                            getTokensUrlOption.get())
                    .setFlag(
                            FlagsConstants.KEY_FLEDGE_KANON_JOIN_URL_AUTHORIY,
                            joinAuthorityOption.get())
                    .setFlag(FlagsConstants.KEY_ANON_GET_CHALLENGE_URl, getChallengeUrlOption.get())
                    .setFlag(
                            FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_URI,
                            getCoordinator());

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

    /**
     * Warm up servers to reduce flakiness.
     *
     * <p>B&A servers often send responses if contacted after a while. Warming up with a couple of
     * calls should greatly reduce this flakiness.
     */
    private void warmupClientAndServer() throws Exception {
        // warm up the b&a encryption key fetch URL
        makeWarmUpNetworkCall(getCoordinator());

        makeWarmUpNetworkCall(keyFetchUrlOption.get());

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

    @Before
    public void warmup() throws Exception {
        warmupClientAndServer();
    }

    @Test
    public void runServerAuction_verifyKAnonSetJoined() throws Exception {
        List<CustomAudience> customAudiences =
                CustomAudienceTestFixture.readCustomAudiences(
                        CUSTOM_AUDIENCE_ONE_BUYER_ONE_CA_ONE_AD);

        CustomAudienceTestFixture.joinCustomAudiences(customAudiences);

        Instant startTime = Clock.system(ZoneId.systemDefault()).instant();
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
        Assert.assertNotEquals(adSelectionOutcome.getRenderUri(), Uri.EMPTY);

        Thread.sleep(10000L);
        Assert.assertTrue(doLogsContainSuccessStatement(startTime, /* retries= */ 3));
    }

    /** Return AdServices(EpochManager) logs that will be used to build the test metrics. */
    public InputStream getMetricsEvents(Instant startTime) throws IOException {
        ProcessBuilder pb =
                new ProcessBuilder(
                        Arrays.asList(
                                "logcat",
                                "-s",
                                "adservices.kanon:V",
                                "-t",
                                LOG_TIME_FORMATTER.format(startTime),
                                "|",
                                "grep",
                                "kanon"));
        return pb.start().getInputStream();
    }

    private boolean doLogsContainSuccessStatement(Instant startTime, int retries) throws Exception {
        long waitTimeMs = EXPONENTIAL_BACKOFF_BASE_SECONDS * 1000;
        for (int i = 0; i < retries; i++) {
            Log.d(TAG, "Reading logs. Attempt: " + (i + 1));
            try (InputStream inputStream = getMetricsEvents(startTime);
                    BufferedReader bufferedReader =
                            new BufferedReader(new InputStreamReader(inputStream))) {
                boolean successFound =
                        bufferedReader.lines().anyMatch(line -> line.contains(SUCCESS_LOG));

                if (successFound) {
                    return true;
                }
            }

            waitTimeMs *= EXPONENTIAL_BACKOFF_BASE_SECONDS;
            Thread.sleep(waitTimeMs);
        }
        return false;
    }
}
