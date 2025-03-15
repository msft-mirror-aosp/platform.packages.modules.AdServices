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

package com.android.adservices.debuggableendtoends;

import static android.adservices.adselection.AdSelectionConfigFixture.SELLER;
import static android.adservices.adselection.AuctionEncryptionKeyFixture.getDeterministicAuctionResponseBody;

import static com.android.adservices.service.CommonDebugFlagsConstants.KEY_ADSERVICES_SHELL_COMMAND_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_DEVELOPER_SESSION_FEATURE_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_PROTECTED_APP_SIGNALS_CLI_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.GetAdSelectionDataOutcome;
import android.adservices.adselection.GetAdSelectionDataRequest;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.clients.signals.ProtectedSignalsClient;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.http.MockWebServerRule;
import android.adservices.signals.UpdateSignalsRequest;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.AdServicesEndToEndTestCase;
import com.android.adservices.LoggerFactory;
import com.android.adservices.common.AdServicesShellCommandHelper;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.service.adselection.ServerAuctionTestHelper;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;

import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Tests to ensure correct behaviour from a debuggable app during a dev session. */
@EnableDebugFlag(KEY_ADSERVICES_SHELL_COMMAND_ENABLED)
@EnableDebugFlag(KEY_DEVELOPER_SESSION_FEATURE_ENABLED)
public class DevSessionEncryptionDebuggableE2ETest extends AdServicesEndToEndTestCase {

    @Rule
    public MockWebServerRule mMockWebServerRule =
            MockWebServerRule.forHttps(
                    ApplicationProvider.getApplicationContext(),
                    "adservices_test_server.p12",
                    "adservices_test");

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("localhost");
    private static final Uri COORDINATOR_ORIGIN_URI =
            Uri.parse("https://publickeyservice.pa.gcp.privacysandboxservices.com");
    private static final Uri INVALID_COORDINATOR_ORIGIN_URI = Uri.parse("https://example.com");

    private static final String SIGNALS_UPDATE_PATH = "/signals";
    private static final String SIGNALS_ENCODING_SCRIPT_PATH = "/script";
    private static final String KEY_FETCH_PATH = "/keys";
    private static final String SIGNALS_ENCODING_SCRIPT =
            "function encodeSignals(signals, maxSize) {\n"
                    + "   return {'status' : 0, 'results' : new Uint8Array([signals.length])};\n"
                    + "}";
    private static final String SIGNALS_ENCODING_SCRIPT_TEMPLATE = "<encode-signals-script>";
    private static final String SIGNALS_UPDATE_JSON =
            "{\n"
                    + "  \"put\": {\n"
                    + "    \"AAAAAQ==\": \"AAAAZQ==\",\n"
                    + "    \"AAAAAg==\": \"AAAAZg==\"\n"
                    + "  },\n"
                    + "  \"update_encoder\": {\n"
                    + "    \"action\": \"REGISTER\",\n"
                    + "    \"endpoint\": \""
                    + SIGNALS_ENCODING_SCRIPT_TEMPLATE
                    + "\"\n"
                    + "  }\n"
                    + "}";

    private final AdvertisingCustomAudienceClient mCustomAudienceClient =
            new AdvertisingCustomAudienceClient.Builder()
                    .setContext(mContext)
                    .setExecutor(CALLBACK_EXECUTOR)
                    .build();
    private final ProtectedSignalsClient mProtectedSignalsClient =
            new ProtectedSignalsClient.Builder()
                    .setContext(mContext)
                    .setExecutor(CALLBACK_EXECUTOR)
                    .build();
    private final AdSelectionClient mAdSelectionClient =
            new AdSelectionClient.Builder()
                    .setContext(mContext)
                    .setExecutor(CALLBACK_EXECUTOR)
                    .build();

    private final AdServicesShellCommandHelper mAdServicesShellCommandHelper =
            new AdServicesShellCommandHelper();
    private final ServerAuctionTestHelper mServerAuctionTestHelper =
            ServerAuctionTestHelper.getDefaultInstance(AdServicesLoggerImpl.getInstance());

    @Before
    public void setup() throws Exception {
        AdservicesTestHelper.killAdservicesProcess(mContext);
    }

    @After
    public void tearDown() throws Exception {
        runDevSessionCommand(/* state= */ false, /* enableServerTestKeys= */ false);
    }

    @Test
    public void testGetAdSelectionData_devSessionTestKey_success() throws Exception {
        runDevSessionCommandAndAssert(/* state= */ true, /* enableServerTestKeys= */ true);

        String keyFetchBody =
                getDeterministicAuctionResponseBody(mServerAuctionTestHelper.mAuctionKey);

        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                return new MockResponse().setBody(keyFetchBody);
                            }
                        });

        // Join a custom audience
        CustomAudience customAudience =
                CustomAudienceFixture.getValidBuilderForBuyerFiltersWithAdRenderId(BUYER).build();
        mCustomAudienceClient.joinCustomAudience(customAudience).get();

        // Call getAdSelectionData
        Uri keyFetchUri = mMockWebServerRule.uriForPath(KEY_FETCH_PATH);
        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setCoordinatorOriginUri(keyFetchUri)
                        .setSeller(SELLER)
                        .build();

        GetAdSelectionDataOutcome getAdSelectionDataOutcome =
                mAdSelectionClient.getAdSelectionData(request).get();

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getPath()).isEqualTo(KEY_FETCH_PATH);

        byte[] encryptedAuctionResult = getAdSelectionDataOutcome.getAdSelectionData();

        // Decrypt the auction result using the server auction test key. The decryption would be
        // successful only if getAdSelectionData used the test key for encryption.
        BiddingAuctionServers.ProtectedAuctionInput protectedAuctionInput =
                mServerAuctionTestHelper.decryptAdSelectionData(encryptedAuctionResult);
        Map<String, BiddingAuctionServers.BuyerInput> buyerInputs =
                mServerAuctionTestHelper.getDecompressedBuyerInputs(protectedAuctionInput);

        assertThat(buyerInputs).hasSize(1);
        assertThat(buyerInputs).containsKey(BUYER.toString());

        BiddingAuctionServers.BuyerInput buyerInput = buyerInputs.get(BUYER.toString());

        List<BiddingAuctionServers.BuyerInput.CustomAudience> customAudienceList =
                buyerInput.getCustomAudiencesList();

        assertThat(customAudienceList).hasSize(1);
        assertThat(customAudienceList.getFirst().getName()).isEqualTo(customAudience.getName());
    }

    @SetFlagEnabled(KEY_PROTECTED_SIGNALS_ENABLED)
    @EnableDebugFlag(KEY_PROTECTED_APP_SIGNALS_CLI_ENABLED)
    @RequiresSdkLevelAtLeastT(reason = "Protected App Signals is available on T+")
    @Test
    public void testGetAdSelectionData_devSessionTestKey_withSignals_success() throws Exception {
        runDevSessionCommandAndAssert(/* state= */ true, /* enableServerTestKeys= */ true);

        String keyFetchBody =
                getDeterministicAuctionResponseBody(mServerAuctionTestHelper.mAuctionKey);

        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                String path = request.getPath();
                                sLogger.d("TEST dispatch path: %s", path);
                                return switch (path) {
                                    case KEY_FETCH_PATH -> new MockResponse().setBody(keyFetchBody);
                                    case SIGNALS_ENCODING_SCRIPT_PATH ->
                                            new MockResponse().setBody(SIGNALS_ENCODING_SCRIPT);
                                    case SIGNALS_UPDATE_PATH -> {
                                        String body =
                                                SIGNALS_UPDATE_JSON.replace(
                                                        SIGNALS_ENCODING_SCRIPT_TEMPLATE,
                                                        mMockWebServerRule
                                                                .uriForPath(
                                                                        SIGNALS_ENCODING_SCRIPT_PATH)
                                                                .toString());
                                        yield new MockResponse().setBody(body);
                                    }
                                    default ->
                                            throw new IllegalStateException(
                                                    "Unexpected value: " + path);
                                };
                            }
                        });

        // Join a custom audience
        CustomAudience customAudience =
                CustomAudienceFixture.getValidBuilderForBuyerFiltersWithAdRenderId(BUYER).build();
        mCustomAudienceClient.joinCustomAudience(customAudience).get();

        // Update signals
        Uri updateSignalsUri = mMockWebServerRule.uriForPath(SIGNALS_UPDATE_PATH);
        UpdateSignalsRequest updateSignalsRequest =
                new UpdateSignalsRequest.Builder(updateSignalsUri).build();
        mProtectedSignalsClient.updateSignals(updateSignalsRequest).get();

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getPath()).isEqualTo(SIGNALS_UPDATE_PATH);

        // Encode signals
        assertThat(
                        mAdServicesShellCommandHelper.runCommand(
                                "app-signals trigger-encoding --buyer %s", BUYER))
                .isNotNull();

        recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getPath()).isEqualTo(SIGNALS_ENCODING_SCRIPT_PATH);

        // Call getAdSelectionData
        Uri keyFetchUri = mMockWebServerRule.uriForPath(KEY_FETCH_PATH);

        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setCoordinatorOriginUri(keyFetchUri)
                        .setSeller(SELLER)
                        .build();

        GetAdSelectionDataOutcome getAdSelectionDataOutcome =
                mAdSelectionClient.getAdSelectionData(request).get();

        recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getPath()).isEqualTo(KEY_FETCH_PATH);

        byte[] encryptedAuctionResult = getAdSelectionDataOutcome.getAdSelectionData();

        // Decrypt the auction result using the server auction test key. The decryption would be
        // successful only if getAdSelectionData used the test key for encryption.
        BiddingAuctionServers.ProtectedAuctionInput protectedAuctionInput =
                mServerAuctionTestHelper.decryptAdSelectionData(encryptedAuctionResult);
        Map<String, BiddingAuctionServers.BuyerInput> buyerInputs =
                mServerAuctionTestHelper.getDecompressedBuyerInputs(protectedAuctionInput);

        assertThat(buyerInputs).hasSize(1);
        assertThat(buyerInputs).containsKey(BUYER.toString());

        BiddingAuctionServers.BuyerInput buyerInput = buyerInputs.get(BUYER.toString());

        List<BiddingAuctionServers.BuyerInput.CustomAudience> customAudienceList =
                buyerInput.getCustomAudiencesList();

        assertThat(customAudienceList).hasSize(1);
        assertThat(customAudienceList.getFirst().getName()).isEqualTo(customAudience.getName());

        assertThat(buyerInput.hasProtectedAppSignals()).isTrue();
        assertThat(buyerInput.getProtectedAppSignals().getAppInstallSignals()).hasSize(1);
        assertThat(buyerInput.getProtectedAppSignals().getEncodingVersion()).isEqualTo(0);
    }

    @Test
    public void testGetAdSelectionData_devSessionProdKey_success() throws Exception {
        runDevSessionCommandAndAssert(/* state= */ true, /* enableServerTestKeys= */ false);

        // Join a custom audience
        CustomAudience customAudience =
                CustomAudienceFixture.getValidBuilderForBuyerFiltersWithAdRenderId(BUYER).build();
        mCustomAudienceClient.joinCustomAudience(customAudience).get();

        // Call getAdSelectionData
        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setCoordinatorOriginUri(COORDINATOR_ORIGIN_URI)
                        .setSeller(SELLER)
                        .build();

        GetAdSelectionDataOutcome getAdSelectionDataOutcome =
                mAdSelectionClient.getAdSelectionData(request).get();

        byte[] encryptedAuctionResult = getAdSelectionDataOutcome.getAdSelectionData();
        assertThat(encryptedAuctionResult).isNotNull();
    }

    @Test
    public void testGetAdSelectionData_devSessionProdKey_invalidInputCoordinator_failure()
            throws Exception {
        runDevSessionCommandAndAssert(/* state= */ true, /* enableServerTestKeys= */ false);

        // Join a custom audience
        CustomAudience customAudience =
                CustomAudienceFixture.getValidBuilderForBuyerFiltersWithAdRenderId(BUYER).build();
        mCustomAudienceClient.joinCustomAudience(customAudience).get();

        // Call getAdSelectionData
        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setCoordinatorOriginUri(INVALID_COORDINATOR_ORIGIN_URI)
                        .setSeller(SELLER)
                        .build();

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> mAdSelectionClient.getAdSelectionData(request).get());
        assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class);
    }

    private void runDevSessionCommandAndAssert(boolean state, boolean enableServerTestKeys) {
        assertThat(runDevSessionCommand(state, enableServerTestKeys)).isNotEmpty();
    }

    private String runDevSessionCommand(boolean state, boolean enableServerTestKeys) {
        sLogger.v("Starting setDevSession(%b, %b)", state, enableServerTestKeys);
        String result =
                mAdServicesShellCommandHelper.runCommand(
                        "adservices-api dev-session %s --erase-db %s",
                        state ? "start" : "end",
                        enableServerTestKeys ? "--enable-server-auction-test-keys" : "");
        sLogger.v("Completed setDevSession(%b, %b)", state, enableServerTestKeys);
        return result;
    }
}
