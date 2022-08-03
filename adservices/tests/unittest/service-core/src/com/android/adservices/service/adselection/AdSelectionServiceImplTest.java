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
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static com.android.adservices.service.adselection.AdSelectionConfigValidator.DECISION_LOGIC_URI_TYPE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES;
import static com.android.adservices.stats.FledgeApiCallStatsMatcher.aCallStatForFledgeApiWithStatus;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionOverrideCallback;
import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.adservices.adselection.ReportImpressionCallback;
import android.adservices.adselection.ReportImpressionInput;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
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
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AdServicesHttpsClient;
import com.android.adservices.service.common.ValidatorTestUtil;
import com.android.adservices.service.devapi.AdSelectionDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;

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
    private static final Uri BUYER_BIDDING_LOGIC_URI = Uri.parse("http://www.seller.com");
    private static final long AD_SELECTION_ID = 1;
    private static final long INCORRECT_AD_SELECTION_ID = 2;
    private static final double BID = 5.0;
    private static final AdTechIdentifier SELLER_VALID =
            AdTechIdentifier.fromString("developer.android.com");
    private static final Uri DECISION_LOGIC_URI_INCONSISTENT =
            Uri.parse("https://developer%$android.com/test/decisions_logic_urls");
    private static final String DUMMY_DECISION_LOGIC_JS =
            "function test() { return \"hello world\"; }";
    private static final AdSelectionSignals DUMMY_TRUSTED_SCORING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"render_url_1\": \"signals_for_1\",\n"
                            + "\t\"render_url_2\": \"signals_for_2\"\n"
                            + "}");
    // Auto-generated variable names are too long for lint check
    private static final int SHORT_API_NAME_OVERRIDE =
            AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO;
    private static final int SHORT_API_NAME_REMOVE_OVERRIDE =
            AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE;
    private static final int SHORT_API_NAME_RESET_ALL_OVERRIDES =
            AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES;
    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(20);
    private final String mSellerReportingPath = "/reporting/seller";
    private final String mBuyerReportingPath = "/reporting/buyer";
    private final String mFetchJavaScriptPath = "/fetchJavascript/";
    private final String mFetchTrustedScoringSignalsPath = "/fetchTrustedSignals/";
    private final AdTechIdentifier mContextualSignals =
            AdTechIdentifier.fromString("{\"contextual_signals\":1}");
    private final AdServicesHttpsClient mClient = new AdServicesHttpsClient(mExecutorService);
    private final Flags mFlags = FlagsFactory.getFlagsForTest();
    @Spy private final AdServicesLogger mAdServicesLoggerSpy = AdServicesLoggerImpl.getInstance();
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();
    // This object access some system APIs
    @Mock public DevContextFilter mDevContextFilter;
    private CustomAudienceDao mCustomAudienceDao;
    private AdSelectionEntryDao mAdSelectionEntryDao;
    private AdSelectionConfig.Builder mAdSelectionConfigBuilder;

    @Before
    public void setUp() {
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, CustomAudienceDatabase.class)
                        .build()
                        .customAudienceDao();

        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        mAdSelectionConfigBuilder =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(mFetchJavaScriptPath)
                                                .getHost()))
                        .setDecisionLogicUri(mMockWebServerRule.uriForPath(mFetchJavaScriptPath))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(mFetchTrustedScoringSignalsPath));
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
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignals();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(customAudienceSignals)
                        .setContextualSignals(mContextualSignals.getStringForm())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URL)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        URL sellerFetchUrl = server.getUrl(mFetchJavaScriptPath);
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mExecutorService,
                        CONTEXT,
                        mAdServicesLoggerSpy,
                        mFlags);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertTrue(callback.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPath, fetchRequest.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION, STATUS_SUCCESS);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                                STATUS_SUCCESS));
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

        mMockWebServerRule.startMockWebServer(
                List.of(
                        new MockResponse().setBody(sellerDecisionLogicJs),
                        new MockResponse(),
                        new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignals();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(customAudienceSignals)
                        .setContextualSignals(mContextualSignals.getStringForm())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URL)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mExecutorService,
                        CONTEXT,
                        mAdServicesLoggerSpy,
                        mFlags);

        ReportImpressionInput request =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(INCORRECT_AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .build();

        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, request);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INVALID_ARGUMENT);

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                        STATUS_INVALID_ARGUMENT);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                                STATUS_INVALID_ARGUMENT));
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
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignals();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(customAudienceSignals)
                        .setContextualSignals(mContextualSignals.getStringForm())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URL)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mExecutorService,
                        CONTEXT,
                        mAdServicesLoggerSpy,
                        mFlags);

        ReportImpressionInput request =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .build();

        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, request);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INTERNAL_ERROR);

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION, STATUS_INTERNAL_ERROR);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                                STATUS_INTERNAL_ERROR));
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
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(inValidBuyerDecisionLogicJsMissingCurlyBracket)
                        .build();

        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignals();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(customAudienceSignals)
                        .setContextualSignals(mContextualSignals.getStringForm())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URL)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mExecutorService,
                        CONTEXT,
                        mAdServicesLoggerSpy,
                        mFlags);

        ReportImpressionInput request =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .build();

        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, request);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INTERNAL_ERROR);

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION, STATUS_INTERNAL_ERROR);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                                STATUS_INTERNAL_ERROR));
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
                        .setContextualSignals(mContextualSignals.getStringForm())
                        .setWinningAdRenderUri(RENDER_URL)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mExecutorService,
                        CONTEXT,
                        mAdServicesLoggerSpy,
                        mFlags);

        ReportImpressionInput request =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .build();

        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, request);

        assertTrue(callback.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPath, fetchRequest.getPath());

        RecordedRequest reportRequest = server.takeRequest();
        assertEquals(mSellerReportingPath, reportRequest.getPath());

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION, STATUS_SUCCESS);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                                STATUS_SUCCESS));
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
                                // There is no need to fetch JS
                                new MockResponse(), new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignals();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(customAudienceSignals)
                        .setContextualSignals(mContextualSignals.getStringForm())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URL)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

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
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.getStringForm())
                        .build();
        mAdSelectionEntryDao.persistAdSelectionOverride(adSelectionOverride);

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(myAppPackageName)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mExecutorService,
                        CONTEXT,
                        mAdServicesLoggerSpy,
                        mFlags);
        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertTrue(callback.mIsSuccess);
        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION, STATUS_SUCCESS);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                                STATUS_SUCCESS));
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

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mExecutorService,
                        CONTEXT,
                        mAdServicesLoggerSpy,
                        mFlags);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        AdSelectionOverrideTestCallback callback =
                callAddOverride(
                        adSelectionService,
                        adSelectionConfig,
                        DUMMY_DECISION_LOGIC_JS,
                        DUMMY_TRUSTED_SCORING_SIGNALS);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                adSelectionConfig),
                        myAppPackageName));

        verify(mAdServicesLoggerSpy).logFledgeApiCallStats(SHORT_API_NAME_OVERRIDE, STATUS_SUCCESS);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(SHORT_API_NAME_OVERRIDE, STATUS_SUCCESS));
    }

    @Test
    public void testOverrideAdSelectionConfigRemoteInfoFailsWithDevOptionsDisabled()
            throws Exception {
        String myAppPackageName = "com.google.ppapi.test";

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mExecutorService,
                        CONTEXT,
                        mAdServicesLoggerSpy,
                        mFlags);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        assertThrows(
                SecurityException.class,
                () ->
                        callAddOverride(
                                adSelectionService,
                                adSelectionConfig,
                                DUMMY_DECISION_LOGIC_JS,
                                DUMMY_TRUSTED_SCORING_SIGNALS));

        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                adSelectionConfig),
                        myAppPackageName));

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(SHORT_API_NAME_OVERRIDE, STATUS_INTERNAL_ERROR);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                SHORT_API_NAME_OVERRIDE, STATUS_INTERNAL_ERROR));
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

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mExecutorService,
                        CONTEXT,
                        mAdServicesLoggerSpy,
                        mFlags);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        String adSelectionConfigId =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig);

        DBAdSelectionOverride dbAdSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId)
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.getStringForm())
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

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(SHORT_API_NAME_REMOVE_OVERRIDE, STATUS_SUCCESS);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                SHORT_API_NAME_REMOVE_OVERRIDE, STATUS_SUCCESS));
    }

    @Test
    public void testRemoveAdSelectionConfigRemoteInfoOverrideFailsWithDevOptionsDisabled()
            throws Exception {
        String myAppPackageName = "com.google.ppapi.test";

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mExecutorService,
                        CONTEXT,
                        mAdServicesLoggerSpy,
                        mFlags);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        String adSelectionConfigId =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig);

        DBAdSelectionOverride dbAdSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId)
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.getStringForm())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId, myAppPackageName));

        assertThrows(
                SecurityException.class,
                () -> callRemoveOverride(adSelectionService, adSelectionConfig));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId, myAppPackageName));

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(SHORT_API_NAME_REMOVE_OVERRIDE, STATUS_INTERNAL_ERROR);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                SHORT_API_NAME_REMOVE_OVERRIDE, STATUS_INTERNAL_ERROR));
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

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mExecutorService,
                        CONTEXT,
                        mAdServicesLoggerSpy,
                        mFlags);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        String adSelectionConfigId =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig);

        DBAdSelectionOverride dbAdSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId)
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.getStringForm())
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

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(SHORT_API_NAME_REMOVE_OVERRIDE, STATUS_SUCCESS);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                SHORT_API_NAME_REMOVE_OVERRIDE, STATUS_SUCCESS));
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
                        CONTEXT,
                        mAdServicesLoggerSpy,
                        mFlags);

        AdSelectionConfig adSelectionConfig1 = mAdSelectionConfigBuilder.build();
        AdSelectionConfig adSelectionConfig2 =
                mAdSelectionConfigBuilder
                        .setSeller(AdTechIdentifier.fromString("adidas.com"))
                        .setDecisionLogicUri(Uri.parse("https://adidas.com/decisoin_logic_url"))
                        .build();
        AdSelectionConfig adSelectionConfig3 =
                mAdSelectionConfigBuilder
                        .setSeller(AdTechIdentifier.fromString("nike.com"))
                        .setDecisionLogicUri(Uri.parse("https://nike.com/decisoin_logic_url"))
                        .build();

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
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.getStringForm())
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride2 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId2)
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.getStringForm())
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride3 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId3)
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.getStringForm())
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

        AdSelectionOverrideTestCallback callback = callResetAllOverrides(adSelectionService);

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

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(SHORT_API_NAME_RESET_ALL_OVERRIDES, STATUS_SUCCESS);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                SHORT_API_NAME_RESET_ALL_OVERRIDES, STATUS_SUCCESS));
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

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mExecutorService,
                        CONTEXT,
                        mAdServicesLoggerSpy,
                        mFlags);

        AdSelectionConfig adSelectionConfig1 = mAdSelectionConfigBuilder.build();
        AdSelectionConfig adSelectionConfig2 =
                mAdSelectionConfigBuilder
                        .setSeller(AdTechIdentifier.fromString("adidas.com"))
                        .setDecisionLogicUri(Uri.parse("https://adidas.com/decisoin_logic_url"))
                        .build();
        AdSelectionConfig adSelectionConfig3 =
                mAdSelectionConfigBuilder
                        .setSeller(AdTechIdentifier.fromString("nike.com"))
                        .setDecisionLogicUri(Uri.parse("https://nike.com/decisoin_logic_url"))
                        .build();

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
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.getStringForm())
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride2 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId2)
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.getStringForm())
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride3 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId3)
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.getStringForm())
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

        AdSelectionOverrideTestCallback callback = callResetAllOverrides(adSelectionService);

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

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(SHORT_API_NAME_RESET_ALL_OVERRIDES, STATUS_SUCCESS);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                SHORT_API_NAME_RESET_ALL_OVERRIDES, STATUS_SUCCESS));
    }

    @Test
    public void testResetAllAdSelectionConfigRemoteOverridesFailsWithDevOptionsDisabled()
            throws Exception {
        String myAppPackageName = "com.google.ppapi.test";

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mExecutorService,
                        CONTEXT,
                        mAdServicesLoggerSpy,
                        mFlags);

        AdSelectionConfig adSelectionConfig1 = mAdSelectionConfigBuilder.build();
        AdSelectionConfig adSelectionConfig2 =
                mAdSelectionConfigBuilder
                        .setSeller(AdTechIdentifier.fromString("adidas.com"))
                        .setDecisionLogicUri(Uri.parse("https://adidas.com/decisoin_logic_url"))
                        .build();
        AdSelectionConfig adSelectionConfig3 =
                mAdSelectionConfigBuilder
                        .setSeller(AdTechIdentifier.fromString("nike.com"))
                        .setDecisionLogicUri(Uri.parse("https://nike.com/decisoin_logic_url"))
                        .build();

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
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.getStringForm())
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride2 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId2)
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.getStringForm())
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride3 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId3)
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.getStringForm())
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

        assertThrows(SecurityException.class, () -> callResetAllOverrides(adSelectionService));

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId1, myAppPackageName));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId2, myAppPackageName));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId3, myAppPackageName));

        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(SHORT_API_NAME_RESET_ALL_OVERRIDES, STATUS_INTERNAL_ERROR);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                SHORT_API_NAME_RESET_ALL_OVERRIDES, STATUS_INTERNAL_ERROR));
    }

    @Test
    public void testCloseJSScriptEngineConnectionAtShutDown() {
        MockitoSession staticMockitoSession =
                ExtendedMockito.mockitoSession().spyStatic(JSScriptEngine.class).startMocking();
        JSScriptEngine jsScriptEngineMock = mock(JSScriptEngine.class);
        when(JSScriptEngine.getInstance(any())).thenReturn(jsScriptEngineMock);

        try {
            AdSelectionServiceImpl adSelectionService =
                    new AdSelectionServiceImpl(
                            mAdSelectionEntryDao,
                            mCustomAudienceDao,
                            mClient,
                            mDevContextFilter,
                            mExecutorService,
                            CONTEXT,
                            mAdServicesLoggerSpy,
                            mFlags);

            adSelectionService.destroy();
            verify(jsScriptEngineMock).shutdown();
        } finally {
            staticMockitoSession.finishMocking();
        }
    }

    private AdSelectionOverrideTestCallback callAddOverride(
            AdSelectionServiceImpl adSelectionService,
            AdSelectionConfig adSelectionConfig,
            String decisionLogicJS,
            AdSelectionSignals trustedScoringSignals)
            throws Exception {
        // Counted down in 1) callback and 2) logApiCall
        CountDownLatch resultLatch = new CountDownLatch(2);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        // Wait for the logging call, which happens after the callback
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer).when(mAdServicesLoggerSpy).logApiCallStats(any());

        adSelectionService.overrideAdSelectionConfigRemoteInfo(
                adSelectionConfig, decisionLogicJS, trustedScoringSignals, callback);
        resultLatch.await();
        return callback;
    }

    private AdSelectionOverrideTestCallback callRemoveOverride(
            AdSelectionServiceImpl adSelectionService, AdSelectionConfig adSelectionConfig)
            throws Exception {
        // Counted down in 1) callback and 2) logApiCall
        CountDownLatch resultLatch = new CountDownLatch(2);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        // Wait for the logging call, which happens after the callback
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer).when(mAdServicesLoggerSpy).logApiCallStats(any());

        adSelectionService.removeAdSelectionConfigRemoteInfoOverride(adSelectionConfig, callback);
        resultLatch.await();
        return callback;
    }

    @Test
    public void testAdSelectionConfigInvalidSellerAndSellerUrls() throws Exception {
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

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignals();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(customAudienceSignals)
                        .setContextualSignals(mContextualSignals.getStringForm())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URL)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);
        AdSelectionConfig invalidAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setSeller(SELLER_VALID)
                        .setDecisionLogicUri(DECISION_LOGIC_URI_INCONSISTENT)
                        .build();
        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mExecutorService,
                        CONTEXT,
                        mAdServicesLoggerSpy,
                        mFlags);
        ReportImpressionInput request =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(INCORRECT_AD_SELECTION_ID)
                        .setAdSelectionConfig(invalidAdSelectionConfig)
                        .build();

        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> callReportImpression(adSelectionService, request));

        ValidatorTestUtil.assertValidationFailuresMatch(
                thrown,
                String.format(
                        "Invalid object of type %s. The violations are:",
                        AdSelectionConfig.class.getName()),
                ImmutableList.of(
                        String.format(
                                AdSelectionConfigValidator.SELLER_AND_URI_HOST_ARE_INCONSISTENT,
                                Uri.parse("https://" + SELLER_VALID).getHost(),
                                DECISION_LOGIC_URI_INCONSISTENT.getHost(),
                                DECISION_LOGIC_URI_TYPE)));
        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                        STATUS_INVALID_ARGUMENT);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                                STATUS_INVALID_ARGUMENT));
    }

    private AdSelectionOverrideTestCallback callResetAllOverrides(
            AdSelectionServiceImpl adSelectionService) throws Exception {
        // Counted down in 1) callback and 2) logApiCall
        CountDownLatch resultLatch = new CountDownLatch(2);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        // Wait for the logging call, which happens after the callback
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer).when(mAdServicesLoggerSpy).logApiCallStats(any());

        adSelectionService.resetAllAdSelectionConfigRemoteOverrides(callback);
        resultLatch.await();
        return callback;
    }

    private ReportImpressionTestCallback callReportImpression(
            AdSelectionServiceImpl adSelectionService, ReportImpressionInput requestParams)
            throws Exception {
        // Counted down in 1) callback and 2) logApiCall
        CountDownLatch resultLatch = new CountDownLatch(2);
        ReportImpressionTestCallback callback = new ReportImpressionTestCallback(resultLatch);

        // Wait for the logging call, which happens after the callback
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer).when(mAdServicesLoggerSpy).logApiCallStats(any());

        adSelectionService.reportImpression(requestParams, callback);
        resultLatch.await();
        return callback;
    }

    public static class ReportImpressionTestCallback extends ReportImpressionCallback.Stub {
        private final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

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
        private final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

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
