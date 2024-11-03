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

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.GetAdSelectionDataOutcome;
import android.adservices.adselection.GetAdSelectionDataRequest;
import android.adservices.adselection.PersistAdSelectionResultRequest;
import android.adservices.common.AdTechIdentifier;
import android.adservices.test.scenario.adservices.fledge.utils.BackgroundJobFixture;
import android.adservices.test.scenario.adservices.fledge.utils.FakeAdExchangeServer;
import android.adservices.test.scenario.adservices.fledge.utils.ProtectedAppSignalsTestFixture;
import android.adservices.test.scenario.adservices.fledge.utils.SelectAdResponse;
import android.adservices.test.scenario.adservices.utils.SelectAdsFlagRule;
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

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Scenario
@RunWith(JUnit4.class)
public class PASServerAuctionE2ETest extends ServerAuctionE2ETestBase {
    private static final String TAG = PASServerAuctionE2ETest.class.getName();
    private static final boolean SERVER_RESPONSE_LOGGING_ENABLED = true;
    private static final int BACKGROUND_JOB_ID = 29;
    private static final String SINGLE_BUYER_SERVER_REQUEST = "PASBuyerRequest.json";
    private static final String CUSTOM_AUDIENCE_ONE_CA_ONE_AD = "PtbCustomAudienceOneCaOneAd.json";
    private static final Map<String, String> HTTP_HEADERS =
            Map.of(
                    "X-Accept-Language", "en-US",
                    "X-BnA-Client-IP", "192.168.0.1",
                    "X-User-Agent", "Test-User-Agent");

    @ClassRule
    public static StringOption sfeAddress =
            new StringOption("sfe-address")
                    .setRequired(true)
                    .setDefault("https://seller1-lj2.sfe.ppapi.gcp.pstest.dev/v1/selectAd");

    @ClassRule
    public static StringOption encryptionKeyFetchUri =
            new StringOption("encryption-key-fetch-uri")
                    .setRequired(true)
                    .setDefault("https://storage.googleapis.com/wasm-explorer/PAS/publicKeys.json");

    @ClassRule
    public static StringOption buyerDomain =
            new StringOption("buyer-domain")
                    .setRequired(true)
                    .setDefault("https://cb-test-buyer-5jyy5ulagq-uc.a.run.app/");

    @ClassRule
    public static StringOption sellerDomain =
            new StringOption("seller-domain")
                    .setRequired(true)
                    .setDefault("cb-test-seller-5jyy5ulagq-uc.a.run.app");

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

    @Rule
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.newInstance()
                    .setFlag(
                            FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_AUCTION_KEY_FETCH_URI,
                            getEncryptionKeyFetchUri())
                    .setFlag(FlagsConstants.KEY_PROTECTED_SIGNALS_PERIODIC_ENCODING_ENABLED, true)
                    .setFlag(
                            FlagsConstants.KEY_PROTECTED_SIGNALS_ENCODER_REFRESH_WINDOW_SECONDS, 1);

    /** Perform the class-wide required setup. */
    @BeforeClass
    public static void setupBeforeClass() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.WRITE_DEVICE_CONFIG);
    }

    /**
     * Warm up servers to reduce flakiness.
     *
     * <p>B&A servers often send responses if contacted after a while. Warming up with a couple of
     * calls should greatly reduce this flakiness.
     */
    @Before
    public void warmup() throws Exception {
        makeWarmUpNetworkCall(getEncryptionKeyFetchUri());
        warmupBiddingAuctionServer(
                CUSTOM_AUDIENCE_ONE_CA_ONE_AD,
                getSellerDomain(),
                SINGLE_BUYER_SERVER_REQUEST,
                getSfeAddress(),
                SERVER_RESPONSE_LOGGING_ENABLED);
        Thread.sleep(2000L);
    }

    private String getSfeAddress() {
        return sfeAddress.get();
    }

    private String getEncryptionKeyFetchUri() {
        return encryptionKeyFetchUri.get();
    }

    private String getBuyerDomain() {
        return buyerDomain.get();
    }

    private String getSellerDomain() {
        return sellerDomain.get();
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    /**
     * Static server: https://critique.corp.google.com/cl/681636876 Auction Server:
     * https://team-review.git.corp.google.com/c/android-privacy-sandbox-remarketing/fledge/servers/bidding-auction-server/+/2348882
     */
    @Test
    public void runAdSelection_twoSignals_runEviction_success() throws Exception {
        String clean1 = "cb/pas/clear/1/800/1";
        String clean2 = "cb/pas/clear/800/1600/1";
        String clean3 = "cb/pas/clear/1600/2400/1";
        String bid1encoder1 = "cb/pas/add/1/1";
        String bid2encoder1 = "cb/pas/add/2/1";
        String bid2encoder2 = "cb/pas/add/2/2";

        updateSignalsAndAssertSuccess(clean1);
        updateSignalsAndAssertSuccess(clean2);
        updateSignalsAndAssertSuccess(clean3);
        updateSignalsAndAssertSuccess(bid1encoder1);
        runBackgroundJobAndAssertSuccess();

        assertThat(runServerAdSelection()).isEqualTo(getBuyerDomain() + "pas/ad1");

        updateSignalsAndAssertSuccess(bid2encoder1);
        runBackgroundJobAndAssertSuccess();
        assertThat(runServerAdSelection()).isEqualTo(getBuyerDomain() + "pas/ad2");

        updateSignalsAndAssertSuccess(bid2encoder2);
        Thread.sleep(5000);
        runBackgroundJobAndAssertSuccess();
        // Since the encoder updates after the encoding job. We need run twice.
        Thread.sleep(5000);
        runBackgroundJobAndAssertSuccess();
        assertThat(runServerAdSelection()).isEqualTo(getBuyerDomain() + "pas/ad1");

        // Flush the signals until eviction occur.
        updateSignalsAndAssertSuccess("cb/pas/meaningless/2/300");
        updateSignalsAndAssertSuccess("cb/pas/meaningless/301/600");
        updateSignalsAndAssertSuccess("cb/pas/meaningless/601/900");
        updateSignalsAndAssertSuccess("cb/pas/meaningless/901/1200");
        updateSignalsAndAssertSuccess("cb/pas/meaningless/1201/1500");
        updateSignalsAndAssertSuccess("cb/pas/meaningless/1501/1800");
        updateSignalsAndAssertSuccess("cb/pas/meaningless/1801/2100");

        Thread.sleep(5000);
        runBackgroundJobAndAssertSuccess();
        // Since the encoder updates after the encoding job. We need run twice.
        Thread.sleep(5000);
        runBackgroundJobAndAssertSuccess();
        // The server should not return a result.
        assertThat(runServerAdSelection()).isEqualTo("");
    }

    private void updateSignalsAndAssertSuccess(String path) {
        assertThat(ProtectedAppSignalsTestFixture.updateSignals(getBuyerDomain() + path)).isTrue();
    }

    private void runBackgroundJobAndAssertSuccess() throws InterruptedException {
        boolean isBackgroundJobSuccess = BackgroundJobFixture.runJob(BACKGROUND_JOB_ID);
        assertThat(isBackgroundJobSuccess).isTrue();
    }

    private String runServerAdSelection()
            throws ExecutionException, InterruptedException, TimeoutException, IOException {

        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(AdTechIdentifier.fromString(getSellerDomain()))
                        .build();
        GetAdSelectionDataOutcome outcome =
                AD_SELECTION_CLIENT
                        .getAdSelectionData(request)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Log.i(TAG, "asdf: seller domain: " + getSellerDomain());
        Log.i(TAG, "GetAdSelectionData finished: " + outcome.getAdSelectionDataId());

        SelectAdResponse selectAdResponse =
                FakeAdExchangeServer.runServerAuction(
                        SINGLE_BUYER_SERVER_REQUEST,
                        outcome.getAdSelectionData(),
                        getSfeAddress(),
                        SERVER_RESPONSE_LOGGING_ENABLED,
                        HTTP_HEADERS);
        Log.i(TAG, "SelectAdResponse received: " + selectAdResponse);

        PersistAdSelectionResultRequest persistAdSelectionResultRequest =
                new PersistAdSelectionResultRequest.Builder()
                        .setAdSelectionDataId(outcome.getAdSelectionDataId())
                        .setSeller(AdTechIdentifier.fromString(getSellerDomain()))
                        .setAdSelectionResult(
                                BaseEncoding.base64()
                                        .decode(selectAdResponse.auctionResultCiphertext))
                        .build();

        AdSelectionOutcome adSelectionOutcome =
                AD_SELECTION_CLIENT
                        .persistAdSelectionResult(persistAdSelectionResultRequest)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        return adSelectionOutcome.getRenderUri().toString();
    }
}
