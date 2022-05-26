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
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionOverrideCallback;
import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.adservices.adselection.ReportImpressionCallback;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.http.MockWebServerRule;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.adselection.DBAdSelectionOverride;
import com.android.adservices.data.adselection.DBBuyerDecisionLogic;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.service.devapi.AdSelectionDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;

import com.google.common.collect.ImmutableList;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

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
    private static final Uri RENDER_URL = Uri.parse("http://www.domain.com/advert/");
    private static final Instant ACTIVATION_TIME = CLOCK.instant().truncatedTo(ChronoUnit.MILLIS);
    private static final Uri BUYER_BIDDING_LOGIC_URL = Uri.parse("http://www.seller.com");
    private static final long AD_SELECTION_ID = 1;
    private static final long INCORRECT_AD_SELECTION_ID = 2;
    private static final double BID = 5.0;
    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(20);
    private final String mSellerReportingPath = "/reporting/seller";
    private final String mBuyerReportingPath = "/reporting/buyer";
    private final String mFetchJavaScriptPath = "/fetchJavascript/";
    private final String mContextualSignals = "{\"contextual_signals\":1}";
    private final AdSelectionHttpClient mClient = new AdSelectionHttpClient(mExecutorService);

    private CustomAudienceDao mCustomAudienceDao;
    private AdSelectionEntryDao mAdSelectionEntryDao;

    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();

    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();

    // This object access some system APIs
    @Mock
    DevContextFilter mDevContextFilter;

    @Before
    public void setUp() {
        mCustomAudienceDao = Room.inMemoryDatabaseBuilder(CONTEXT, CustomAudienceDatabase.class)
                .build()
                .customAudienceDao();

        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();
    }

    @Test
    public void testReportImpressionSuccess() throws Exception {
        Uri sellerReportingUrl = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUrl = mMockWebServerRule.uriForPath(mBuyerReportingPath);

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
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

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

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        URL sellerFetchUrl = server.getUrl(mFetchJavaScriptPath);
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfig(Uri.parse(sellerFetchUrl.toString()));

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService = new AdSelectionServiceImpl(
                mAdSelectionEntryDao,
                mCustomAudienceDao,
                mClient,
                mDevContextFilter,
                mExecutorService,
                CONTEXT);

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
        Uri sellerReportingUrl = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUrl = mMockWebServerRule.uriForPath(mBuyerReportingPath);

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
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

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

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        URL sellerFetchUrl = server.getUrl(mFetchJavaScriptPath);
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfig(Uri.parse(sellerFetchUrl.toString()));

        AdSelectionServiceImpl adSelectionService = new AdSelectionServiceImpl(
                mAdSelectionEntryDao,
                mCustomAudienceDao,
                mClient,
                mDevContextFilter,
                mExecutorService,
                CONTEXT);

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
        Uri sellerReportingUrl = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUrl = mMockWebServerRule.uriForPath(mBuyerReportingPath);

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
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(invalidSellerDecisionLogicJsMissingCurlyBracket),
                                new MockResponse(),
                                new MockResponse()));

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

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        URL sellerFetchUrl = server.getUrl(mFetchJavaScriptPath);
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfig(Uri.parse(sellerFetchUrl.toString()));

        AdSelectionServiceImpl adSelectionService = new AdSelectionServiceImpl(
                mAdSelectionEntryDao,
                mCustomAudienceDao,
                mClient,
                mDevContextFilter,
                mExecutorService,
                CONTEXT);

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
        Uri sellerReportingUrl = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUrl = mMockWebServerRule.uriForPath(mBuyerReportingPath);

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
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

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

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        URL sellerFetchUrl = server.getUrl(mFetchJavaScriptPath);
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfig(Uri.parse(sellerFetchUrl.toString()));

        AdSelectionServiceImpl adSelectionService = new AdSelectionServiceImpl(
                mAdSelectionEntryDao,
                mCustomAudienceDao,
                mClient,
                mDevContextFilter,
                mExecutorService,
                CONTEXT);

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
        Uri sellerReportingUrl = mMockWebServerRule.uriForPath(mSellerReportingPath);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_url, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_url': '"
                        + sellerReportingUrl
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse()));

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setContextualSignals(mContextualSignals)
                        .setWinningAdRenderUrl(RENDER_URL)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        URL sellerFetchUrl = server.getUrl(mFetchJavaScriptPath);
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfig(Uri.parse(sellerFetchUrl.toString()));

        AdSelectionServiceImpl adSelectionService = new AdSelectionServiceImpl(
                mAdSelectionEntryDao,
                mCustomAudienceDao,
                mClient,
                mDevContextFilter,
                mExecutorService,
                CONTEXT);

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

    @Test
    public void testReportImpressionUseDevOverrideForSellerJS() throws Exception {
        Uri sellerReportingUrl = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUrl = mMockWebServerRule.uriForPath(mBuyerReportingPath);

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
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                // There is no to fetch JS
                                new MockResponse(), new MockResponse()));

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

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        URL sellerFetchUrl = server.getUrl(mFetchJavaScriptPath);
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfig(Uri.parse(sellerFetchUrl.toString()));

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        // Set dev override for this AdSelection
        String myAppPackageName = "com.google.ppapi.test";
        DBAdSelectionOverride adSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(
                                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                        adSelectionConfig))
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(sellerDecisionLogicJs)
                        .build();
        mAdSelectionEntryDao.persistAdSelectionOverride(adSelectionOverride);

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(myAppPackageName)
                                .build());

        AdSelectionServiceImpl adSelectionService = new AdSelectionServiceImpl(
                mAdSelectionEntryDao,
                mCustomAudienceDao,
                mClient,
                mDevContextFilter,
                mExecutorService,
                CONTEXT);

        ReportImpressionRequest request =
                new ReportImpressionRequest.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, request);

        assertTrue(callback.mIsSuccess);
        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);
    }

    @Test
    public void testOverrideAdSelectionConfigRemoteInfoSuccess() throws Exception {
        String myAppPackageName = "com.google.ppapi.test";

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(myAppPackageName)
                                .build());

        AdSelectionServiceImpl adSelectionService = new AdSelectionServiceImpl(
                mAdSelectionEntryDao,
                mCustomAudienceDao,
                mClient,
                mDevContextFilter,
                mExecutorService,
                CONTEXT);

        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        String decisionLogicJs = "function test() { return \"hello world\"; }";

        AdSelectionOverrideTestCallback callback =
                callAddOverride(adSelectionService, adSelectionConfig, decisionLogicJs);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                adSelectionConfig),
                        myAppPackageName));
    }

    @Test
    public void testOverrideAdSelectionConfigRemoteInfoFailsWithDevOptionsDisabled()
            throws Exception {
        String myAppPackageName = "com.google.ppapi.test";

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService = new AdSelectionServiceImpl(
                mAdSelectionEntryDao,
                mCustomAudienceDao,
                mClient,
                mDevContextFilter,
                mExecutorService,
                CONTEXT);

        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        String decisionLogicJs = "function test() { return \"hello world\"; }";

        AdSelectionOverrideTestCallback callback =
                callAddOverride(adSelectionService, adSelectionConfig, decisionLogicJs);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_UNAUTHORIZED);
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                adSelectionConfig),
                        myAppPackageName));
    }

    @Test
    public void testRemoveAdSelectionConfigRemoteInfoOverrideSuccess() throws Exception {
        String myAppPackageName = "com.google.ppapi.test";

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(myAppPackageName)
                                .build());

        AdSelectionServiceImpl adSelectionService = new AdSelectionServiceImpl(
                mAdSelectionEntryDao,
                mCustomAudienceDao,
                mClient,
                mDevContextFilter,
                mExecutorService,
                CONTEXT);

        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        String decisionLogicJs = "function test() { return \"hello world\"; }";

        String adSelectionConfigId =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig);

        DBAdSelectionOverride dbAdSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId)
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(decisionLogicJs)
                        .build();

        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId, myAppPackageName));

        AdSelectionOverrideTestCallback callback =
                callRemoveOverride(adSelectionService, adSelectionConfig);

        assertTrue(callback.mIsSuccess);
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId, myAppPackageName));
    }

    @Test
    public void testRemoveAdSelectionConfigRemoteInfoOverrideFailsWithDevOptionsDisabled()
            throws Exception {
        String myAppPackageName = "com.google.ppapi.test";

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService = new AdSelectionServiceImpl(
                mAdSelectionEntryDao,
                mCustomAudienceDao,
                mClient,
                mDevContextFilter,
                mExecutorService,
                CONTEXT);

        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        String decisionLogicJs = "function test() { return \"hello world\"; }";

        String adSelectionConfigId =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig);

        DBAdSelectionOverride dbAdSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId)
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(decisionLogicJs)
                        .build();

        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId, myAppPackageName));

        AdSelectionOverrideTestCallback callback =
                callRemoveOverride(adSelectionService, adSelectionConfig);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_UNAUTHORIZED);
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId, myAppPackageName));
    }

    @Test
    public void testRemoveAdSelectionConfigRemoteInfoOverrideDoesNotDeleteWithIncorrectPackageName()
            throws Exception {
        String myAppPackageName = "com.google.ppapi.test";
        String incorrectPackageName = "com.google.ppapi.test.incorrect";

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(incorrectPackageName)
                                .build());

        AdSelectionServiceImpl adSelectionService = new AdSelectionServiceImpl(
                mAdSelectionEntryDao,
                mCustomAudienceDao,
                mClient,
                mDevContextFilter,
                mExecutorService,
                CONTEXT);

        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        String decisionLogicJs = "function test() { return \"hello world\"; }";

        String adSelectionConfigId =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig);

        DBAdSelectionOverride dbAdSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId)
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(decisionLogicJs)
                        .build();

        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId, myAppPackageName));

        AdSelectionOverrideTestCallback callback =
                callRemoveOverride(adSelectionService, adSelectionConfig);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId, myAppPackageName));
    }

    @Test
    public void testResetAllAdSelectionConfigRemoteOverridesDoesNotDeleteWithIncorrectPackageName()
            throws Exception {
        String myAppPackageName = "com.google.ppapi.test";
        String incorrectPackageName = "com.google.ppapi.test.incorrect";

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(incorrectPackageName)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mExecutorService,
                        CONTEXT);

        AdSelectionConfig adSelectionConfig1 = AdSelectionConfigFixture.anAdSelectionConfig();
        AdSelectionConfig adSelectionConfig2 =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder().setSeller("seller_2").build();
        AdSelectionConfig adSelectionConfig3 =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder().setSeller("seller_3").build();

        String decisionLogicJs = "function test() { return \"hello world\"; }";

        String adSelectionConfigId1 =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig1);
        String adSelectionConfigId2 =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig2);
        String adSelectionConfigId3 =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig3);

        DBAdSelectionOverride dbAdSelectionOverride1 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId1)
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(decisionLogicJs)
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride2 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId2)
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(decisionLogicJs)
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride3 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId3)
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(decisionLogicJs)
                        .build();

        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride1);
        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride2);
        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride3);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId1, myAppPackageName));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId2, myAppPackageName));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId3, myAppPackageName));

        AdSelectionOverrideTestCallback callback = callResetALLOverrides(adSelectionService);

        assertTrue(callback.mIsSuccess);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId1, myAppPackageName));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId2, myAppPackageName));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId3, myAppPackageName));
    }

    @Test
    public void testResetAllAdSelectionConfigRemoteOverridesSuccess() throws Exception {
        String myAppPackageName = "com.google.ppapi.test";

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(myAppPackageName)
                                .build());

        AdSelectionServiceImpl adSelectionService = new AdSelectionServiceImpl(
                mAdSelectionEntryDao,
                mCustomAudienceDao,
                mClient,
                mDevContextFilter,
                mExecutorService,
                CONTEXT);

        AdSelectionConfig adSelectionConfig1 = AdSelectionConfigFixture.anAdSelectionConfig();
        AdSelectionConfig adSelectionConfig2 =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder().setSeller("seller_2").build();
        AdSelectionConfig adSelectionConfig3 =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder().setSeller("seller_3").build();

        String decisionLogicJs = "function test() { return \"hello world\"; }";

        String adSelectionConfigId1 =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig1);
        String adSelectionConfigId2 =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig2);
        String adSelectionConfigId3 =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig3);

        DBAdSelectionOverride dbAdSelectionOverride1 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId1)
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(decisionLogicJs)
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride2 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId2)
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(decisionLogicJs)
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride3 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId3)
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(decisionLogicJs)
                        .build();

        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride1);
        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride2);
        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride3);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId1, myAppPackageName));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId2, myAppPackageName));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId3, myAppPackageName));

        AdSelectionOverrideTestCallback callback = callResetALLOverrides(adSelectionService);

        assertTrue(callback.mIsSuccess);

        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId1, myAppPackageName));
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId2, myAppPackageName));
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId3, myAppPackageName));
    }

    @Test
    public void testResetAllAdSelectionConfigRemoteOverridesFailsWithDevOptionsDisabled()
            throws Exception {
        String myAppPackageName = "com.google.ppapi.test";

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService = new AdSelectionServiceImpl(
                mAdSelectionEntryDao,
                mCustomAudienceDao,
                mClient,
                mDevContextFilter,
                mExecutorService,
                CONTEXT);

        AdSelectionConfig adSelectionConfig1 = AdSelectionConfigFixture.anAdSelectionConfig();
        AdSelectionConfig adSelectionConfig2 =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder().setSeller("seller_2").build();
        AdSelectionConfig adSelectionConfig3 =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder().setSeller("seller_3").build();

        String decisionLogicJs = "function test() { return \"hello world\"; }";

        String adSelectionConfigId1 =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig1);
        String adSelectionConfigId2 =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig2);
        String adSelectionConfigId3 =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig3);

        DBAdSelectionOverride dbAdSelectionOverride1 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId1)
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(decisionLogicJs)
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride2 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId2)
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(decisionLogicJs)
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride3 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId3)
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(decisionLogicJs)
                        .build();

        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride1);
        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride2);
        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride3);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId1, myAppPackageName));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId2, myAppPackageName));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId3, myAppPackageName));

        AdSelectionOverrideTestCallback callback = callResetALLOverrides(adSelectionService);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_UNAUTHORIZED);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId1, myAppPackageName));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId2, myAppPackageName));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId3, myAppPackageName));
    }

    private AdSelectionOverrideTestCallback callAddOverride(
            AdSelectionServiceImpl adSelectionService,
            AdSelectionConfig adSelectionConfig,
            String decisionLogicJS)
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        adSelectionService.overrideAdSelectionConfigRemoteInfo(
                adSelectionConfig, decisionLogicJS, callback);
        resultLatch.await();
        return callback;
    }

    private AdSelectionOverrideTestCallback callRemoveOverride(
            AdSelectionServiceImpl adSelectionService, AdSelectionConfig adSelectionConfig)
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        adSelectionService.removeAdSelectionConfigRemoteInfoOverride(adSelectionConfig, callback);
        resultLatch.await();
        return callback;
    }

    private AdSelectionOverrideTestCallback callResetALLOverrides(
            AdSelectionServiceImpl adSelectionService) throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        adSelectionService.resetAllAdSelectionConfigRemoteOverrides(callback);
        resultLatch.await();
        return callback;
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

    public static class AdSelectionOverrideTestCallback extends AdSelectionOverrideCallback.Stub {
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;
        private final CountDownLatch mCountDownLatch;

        public AdSelectionOverrideTestCallback(CountDownLatch countDownLatch) {
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
