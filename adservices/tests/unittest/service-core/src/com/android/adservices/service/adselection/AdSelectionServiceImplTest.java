/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.adselection;

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.adservices.adselection.ReportImpressionCallback;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.common.FledgeErrorResponse;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.adselection.DBBuyerDecisionLogic;

import com.google.common.collect.ImmutableList;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URL;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdSelectionServiceImplTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(20);

    private final String mSellerReportingPath = "/reporting/seller";
    private final String mBuyerReportingPath = "/reporting/buyer";
    private final String mFetchJavaScriptPath = "/fetchJavascript/";

    private static final Uri RENDER_URL = Uri.parse("http://www.domain.com/advert/");

    private static final Instant ACTIVATION_TIME = CLOCK.instant().truncatedTo(ChronoUnit.MILLIS);

    private static final Uri BUYER_BIDDING_LOGIC_URL = Uri.parse("http://www.seller.com");

    private static final long AD_SELECTION_ID = 1;
    private static final long INCORRECT_AD_SELECTION_ID = 2;

    private final String mContextualSignals = "{\"contextual_signals\":1}";

    private static final double BID = 5.0;

    private final AdSelectionHttpClient mClient = new AdSelectionHttpClient(mExecutorService);

    @Test
    public void testReportImpressionSuccess() throws Exception {
        int port = getAvailablePort();

        String sellerReportingUrl = getStringUrl(mSellerReportingPath, port);
        String buyerReportingUrl = getStringUrl(mBuyerReportingPath, port);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_url, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_url': '"
                        + sellerReportingUrl
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_url': '"
                        + buyerReportingUrl
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                setupServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()),
                        port);

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUrl(BUYER_BIDDING_LOGIC_URL)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignals();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(customAudienceSignals)
                        .setContextualSignals(mContextualSignals)
                        .setBiddingLogicUrl(BUYER_BIDDING_LOGIC_URL)
                        .setWinningAdRenderUrl(RENDER_URL)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .build();

        AdSelectionEntryDao adSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        adSelectionEntryDao.persistAdSelection(dbAdSelection);
        adSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        URL sellerFetchUrl = server.getUrl(mFetchJavaScriptPath);
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfig(Uri.parse(sellerFetchUrl.toString()));

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(adSelectionEntryDao, mClient, mExecutorService, CONTEXT);

        ReportImpressionRequest request =
                new ReportImpressionRequest.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .build();

        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, request);

        assertTrue(callback.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPath, fetchRequest.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);
    }

    @Test
    public void testReportImpressionFailsWithInvalidAdSelectionId() throws Exception {
        int port = getAvailablePort();

        String sellerReportingUrl = getStringUrl(mSellerReportingPath, port);
        String buyerReportingUrl = getStringUrl(mBuyerReportingPath, port);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_url, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_url': '"
                        + sellerReportingUrl
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_url': '"
                        + buyerReportingUrl
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                setupServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()),
                        port);

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUrl(BUYER_BIDDING_LOGIC_URL)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignals();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(customAudienceSignals)
                        .setContextualSignals(mContextualSignals)
                        .setBiddingLogicUrl(BUYER_BIDDING_LOGIC_URL)
                        .setWinningAdRenderUrl(RENDER_URL)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .build();

        AdSelectionEntryDao adSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        adSelectionEntryDao.persistAdSelection(dbAdSelection);
        adSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        URL sellerFetchUrl = server.getUrl(mFetchJavaScriptPath);
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfig(Uri.parse(sellerFetchUrl.toString()));

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(adSelectionEntryDao, mClient, mExecutorService, CONTEXT);

        ReportImpressionRequest request =
                new ReportImpressionRequest.Builder()
                        .setAdSelectionId(INCORRECT_AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .build();

        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, request);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INVALID_ARGUMENT);
    }

    @Test
    public void testReportImpressionBadSellerJavascriptFailsWithInternalError() throws Exception {
        int port = getAvailablePort();

        String sellerReportingUrl = getStringUrl(mSellerReportingPath, port);
        String buyerReportingUrl = getStringUrl(mBuyerReportingPath, port);

        String invalidSellerDecisionLogicJsMissingCurlyBracket =
                "function reportResult(ad_selection_config, render_url, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': 'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_url': '"
                        + sellerReportingUrl
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_url': '"
                        + buyerReportingUrl
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                setupServer(
                        List.of(
                                new MockResponse()
                                        .setBody(invalidSellerDecisionLogicJsMissingCurlyBracket),
                                new MockResponse(),
                                new MockResponse()),
                        port);

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUrl(BUYER_BIDDING_LOGIC_URL)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignals();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(customAudienceSignals)
                        .setContextualSignals(mContextualSignals)
                        .setBiddingLogicUrl(BUYER_BIDDING_LOGIC_URL)
                        .setWinningAdRenderUrl(RENDER_URL)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .build();

        AdSelectionEntryDao adSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        adSelectionEntryDao.persistAdSelection(dbAdSelection);
        adSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        URL sellerFetchUrl = server.getUrl(mFetchJavaScriptPath);
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfig(Uri.parse(sellerFetchUrl.toString()));

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(adSelectionEntryDao, mClient, mExecutorService, CONTEXT);

        ReportImpressionRequest request =
                new ReportImpressionRequest.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .build();

        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, request);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INTERNAL_ERROR);
    }

    @Test
    public void testReportImpressionBadBuyerJavascriptFailsWithInternalError() throws Exception {
        int port = getAvailablePort();

        String sellerReportingUrl = getStringUrl(mSellerReportingPath, port);
        String buyerReportingUrl = getStringUrl(mBuyerReportingPath, port);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_url, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_url': '"
                        + sellerReportingUrl
                        + "' } };\n"
                        + "}";

        String inValidBuyerDecisionLogicJsMissingCurlyBracket =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': 'reporting_url': '"
                        + buyerReportingUrl
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                setupServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()),
                        port);

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUrl(BUYER_BIDDING_LOGIC_URL)
                        .setBuyerDecisionLogicJs(inValidBuyerDecisionLogicJsMissingCurlyBracket)
                        .build();

        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignals();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(customAudienceSignals)
                        .setContextualSignals(mContextualSignals)
                        .setBiddingLogicUrl(BUYER_BIDDING_LOGIC_URL)
                        .setWinningAdRenderUrl(RENDER_URL)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .build();

        AdSelectionEntryDao adSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        adSelectionEntryDao.persistAdSelection(dbAdSelection);
        adSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        URL sellerFetchUrl = server.getUrl(mFetchJavaScriptPath);
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfig(Uri.parse(sellerFetchUrl.toString()));

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(adSelectionEntryDao, mClient, mExecutorService, CONTEXT);

        ReportImpressionRequest request =
                new ReportImpressionRequest.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .build();

        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, request);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INTERNAL_ERROR);
    }

    @Test
    public void testReportImpressionContextualAdSuccess() throws Exception {
        int port = getAvailablePort();

        String sellerReportingUrl = getStringUrl(mSellerReportingPath, port);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_url, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_url': '"
                        + sellerReportingUrl
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                setupServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse()),
                        port);

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setContextualSignals(mContextualSignals)
                        .setWinningAdRenderUrl(RENDER_URL)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .build();

        AdSelectionEntryDao adSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        adSelectionEntryDao.persistAdSelection(dbAdSelection);

        URL sellerFetchUrl = server.getUrl(mFetchJavaScriptPath);
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfig(Uri.parse(sellerFetchUrl.toString()));

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(adSelectionEntryDao, mClient, mExecutorService, CONTEXT);

        ReportImpressionRequest request =
                new ReportImpressionRequest.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .build();

        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, request);

        assertTrue(callback.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPath, fetchRequest.getPath());

        RecordedRequest reportRequest = server.takeRequest();
        assertEquals(mSellerReportingPath, reportRequest.getPath());
    }

    private ReportImpressionTestCallback callReportImpression(
            AdSelectionServiceImpl adSelectionService, ReportImpressionRequest requestParams)
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        ReportImpressionTestCallback callback = new ReportImpressionTestCallback(resultLatch);

        adSelectionService.reportImpression(requestParams, callback);
        resultLatch.await();
        return callback;
    }

    private MockWebServer setupServer(List<MockResponse> responses, int port) throws Exception {
        MockWebServer server = new MockWebServer();
        for (MockResponse response : responses) {
            server.enqueue(response);
        }
        server.play(port);
        return server;
    }

    private String getStringUrl(String path, int port) {
        return "http://localhost:" + port + path;
    }

    private int getAvailablePort() throws IOException {
        ServerSocket serverSocket = new ServerSocket(0);
        serverSocket.setReuseAddress(true);
        int port = serverSocket.getLocalPort();
        serverSocket.close();
        return port;
    }

    public static class ReportImpressionTestCallback extends ReportImpressionCallback.Stub {
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;
        private final CountDownLatch mCountDownLatch;

        public ReportImpressionTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }
}
