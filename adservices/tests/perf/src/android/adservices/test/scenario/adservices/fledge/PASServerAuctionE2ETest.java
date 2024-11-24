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

import java.util.Map;
import java.util.concurrent.TimeUnit;

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
            new StringOption("sfe-address").setRequired(true).setDefault("");

    @ClassRule
    public static StringOption encryptionKeyFetchUri =
            new StringOption("encryption-key-fetch-uri").setRequired(true).setDefault("");

    @ClassRule
    public static StringOption buyerDomain =
            new StringOption("buyer-domain").setRequired(true).setDefault("");

    @ClassRule
    public static StringOption sellerDomain =
            new StringOption("seller-domain").setRequired(true).setDefault("");

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
                            getEncryptionKeyFetchUri());

    /** Perform the class-wide required setup. */
    @BeforeClass
    public static void setupBeforeClass() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.WRITE_DEVICE_CONFIG,
                        Manifest.permission.WRITE_ALLOWLISTED_DEVICE_CONFIG);
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
     * Runs the basic simulation test to ensure that B&A can parse and return a response with the
     * enrolled PTB seller, buyer and a coordinator key that's older than 45 days
     */
    @Test
    @Ignore("346898992")
    public void runAdSelection_twoSignals_success() throws Exception {
        String clean = "e2e_clear";
        String bid1 = "e2e_shirts_bid_1";
        String bid2 = "e2e_shoes_bid_2";
        boolean isUpdateSignalSuccess;
        isUpdateSignalSuccess =
                ProtectedAppSignalsTestFixture.updateSignals(getBuyerDomain() + clean);
        isUpdateSignalSuccess =
                isUpdateSignalSuccess
                        && ProtectedAppSignalsTestFixture.updateSignals(getBuyerDomain() + bid1);
        isUpdateSignalSuccess =
                isUpdateSignalSuccess
                        && ProtectedAppSignalsTestFixture.updateSignals(getBuyerDomain() + bid2);
        Assert.assertTrue(isUpdateSignalSuccess);

        boolean isBackgroundJobSuccess = BackgroundJobFixture.runJob(BACKGROUND_JOB_ID);
        Assert.assertTrue(isBackgroundJobSuccess);

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

        String renderUriString = adSelectionOutcome.getRenderUri().toString();
        Assert.assertTrue(renderUriString.contains(getBuyerDomain()));
    }
}
