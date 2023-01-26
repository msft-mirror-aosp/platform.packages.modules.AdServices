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

import static android.adservices.adselection.ReportInteractionInput.DESTINATION_BUYER;
import static android.adservices.adselection.ReportInteractionInput.DESTINATION_SELLER;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static com.android.adservices.service.adselection.ImpressionReporter.REPORT_IMPRESSION_THROTTLED;
import static com.android.adservices.service.adselection.ImpressionReporter.UNABLE_TO_FIND_AD_SELECTION_WITH_GIVEN_ID;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfigFixture;
import android.adservices.adselection.AdSelectionFromOutcomesInput;
import android.adservices.adselection.AdSelectionOverrideCallback;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.adservices.adselection.ReportImpressionCallback;
import android.adservices.adselection.ReportImpressionInput;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.http.MockWebServerRule;
import android.content.Context;
import android.net.Uri;
import android.os.Process;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.adselection.DBAdSelectionFromOutcomesOverride;
import com.android.adservices.data.adselection.DBAdSelectionOverride;
import com.android.adservices.data.adselection.DBBuyerDecisionLogic;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdServicesHttpsClient;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.AppImportanceFilter.WrongCallingApplicationStateException;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.AdSelectionDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AdSelectionServiceImplTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    private static final Uri RENDER_URI = Uri.parse("https://test.com/advert/");
    private static final Instant ACTIVATION_TIME = CLOCK.instant().truncatedTo(ChronoUnit.MILLIS);
    private static final Uri BUYER_BIDDING_LOGIC_URI = Uri.parse("https://test.com");
    private static final long AD_SELECTION_ID = 1;
    private static final long INCORRECT_AD_SELECTION_ID = 2;
    private static final double BID = 5.0;
    private static final AdTechIdentifier SELLER_VALID = AdTechIdentifier.fromString("test.com");
    private static final Uri DECISION_LOGIC_URI_INCONSISTENT =
            Uri.parse("https://testinconsistent.com/test/decisions_logic_uris");
    private static final String DUMMY_DECISION_LOGIC_JS =
            "function test() { return \"hello world\"; }";
    private static final String DUMMY_SELECTION_LOGIC_JS =
            "function selection() { return \"hello world\"; }";
    private static final AdSelectionSignals DUMMY_TRUSTED_SCORING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"render_uri_1\": \"signals_for_1\",\n"
                            + "\t\"render_uri_2\": \"signals_for_2\"\n"
                            + "}");
    private static final AdSelectionSignals DUMMY_SELECTION_SIGNALS =
            AdSelectionSignals.fromString("{\"selection\": \"signal_1\"}");

    private static final long AD_SELECTION_ID_1 = 12345L;
    private static final long AD_SELECTION_ID_2 = 123456L;
    private static final long AD_SELECTION_ID_3 = 1234567L;

    // Auto-generated variable names are too long for lint check
    private static final int SHORT_API_NAME_OVERRIDE =
            AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO;
    private static final int SHORT_API_NAME_REMOVE_OVERRIDE =
            AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE;
    private static final int SHORT_API_NAME_RESET_ALL_OVERRIDES =
            AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES;
    private static final String TIMEOUT_MESSAGE = "Timed out:";

    // Event reporting contestants
    private static final String CLICK_EVENT_SELLER = "click_seller";
    private static final String HOVER_EVENT_SELLER = "hover_seller";

    private static final String CLICK_SELLER_PATH = "/click/seller";
    private static final String HOVER_SELLER_PATH = "/hover/seller";

    private static final String CLICK_EVENT_BUYER = "click_buyer";
    private static final String HOVER_EVENT_BUYER = "hover_buyer";

    private static final String CLICK_BUYER_PATH = "/click/buyer";
    private static final String HOVER_BUYER_PATH = "/hover/buyer";

    private final ExecutorService mLightweightExecutorService =
            AdServicesExecutors.getLightWeightExecutor();
    private final ExecutorService mBackgroundExecutorService =
            AdServicesExecutors.getBackgroundExecutor();
    private final ScheduledThreadPoolExecutor mScheduledExecutor =
            AdServicesExecutors.getScheduler();
    private final String mSellerReportingPath = "/reporting/seller";
    private final String mBuyerReportingPath = "/reporting/buyer";
    private final String mFetchJavaScriptPathSeller = "/fetchJavascript/seller";
    private final String mFetchJavaScriptPathBuyer = "/fetchJavascript/buyer";
    private final String mFetchTrustedScoringSignalsPath = "/fetchTrustedSignals/";
    private final AdTechIdentifier mContextualSignals =
            AdTechIdentifier.fromString("{\"contextual_signals\":1}");
    private final int mBytesPerPeriod = 1;
    private final AdServicesHttpsClient mClient =
            new AdServicesHttpsClient(
                    AdServicesExecutors.getBlockingExecutor(),
                    CacheProviderFactory.createNoOpCache());
    private final AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    // This object access some system APIs
    @Mock public DevContextFilter mDevContextFilter;
    @Mock public AppImportanceFilter mAppImportanceFilter;

    @Spy
    FledgeAuthorizationFilter mFledgeAuthorizationFilterSpy =
            new FledgeAuthorizationFilter(
                    CONTEXT.getPackageManager(),
                    new EnrollmentDao(CONTEXT, DbTestUtil.getDbHelperForTest()),
                    mAdServicesLoggerMock);

    private Flags mFlagsGaUxDisabled = new FlagsWithEnrollmentCheckEnabledSwitch(false, false);
    private Flags mFlagsGaUxEnabled = new FlagsWithEnrollmentCheckEnabledSwitch(false, true);

    @Spy
    FledgeAllowListsFilter mFledgeAllowListsFilterSpy =
            new FledgeAllowListsFilter(mFlagsGaUxDisabled, mAdServicesLoggerMock);

    @Spy
    FledgeAllowListsFilter mFledgeAllowListsFilterGaUxEnabledSpy =
            new FledgeAllowListsFilter(mFlagsGaUxEnabled, mAdServicesLoggerMock);

    private MockitoSession mStaticMockSession = null;
    @Mock private ConsentManager mConsentManagerMock;
    private CustomAudienceDao mCustomAudienceDao;
    private AdSelectionEntryDao mAdSelectionEntryDao;
    private AdSelectionConfig.Builder mAdSelectionConfigBuilder;

    private Uri mBiddingLogicUri;
    private CustomAudienceSignals mCustomAudienceSignals;

    @Before
    public void setUp() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(JSScriptEngine.class)
                        // mAdServicesLoggerMock is not referenced in many tests
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();
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

        mBiddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        mCustomAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignalsBuilder()
                        .setBuyer(AdTechIdentifier.fromString(mBiddingLogicUri.getHost()))
                        .build();

        Map<AdTechIdentifier, AdSelectionSignals> perBuyerSignals =
                Map.of(
                        AdTechIdentifier.fromString("test.com"),
                        AdSelectionSignals.fromString("{\"buyer_signals\":1}"),
                        AdTechIdentifier.fromString("test2.com"),
                        AdSelectionSignals.fromString("{\"buyer_signals\":2}"),
                        AdTechIdentifier.fromString("test3.com"),
                        AdSelectionSignals.fromString("{\"buyer_signals\":3}"),
                        AdTechIdentifier.fromString(mBiddingLogicUri.getHost()),
                        AdSelectionSignals.fromString("{\"buyer_signals\":0}"));

        mAdSelectionConfigBuilder =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(mFetchJavaScriptPathSeller)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(mFetchJavaScriptPathSeller))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(mFetchTrustedScoringSignalsPath))
                        .setPerBuyerSignals(perBuyerSignals);
    }

    @After
    public void tearDown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testReportImpressionSuccessGaUxDisabled() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        reportImpressionSuccess(mFlagsGaUxDisabled);
    }

    @Test
    public void testReportImpressionSuccessGaUxEnabled() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        reportImpressionSuccess(mFlagsGaUxEnabled);
    }

    private void reportImpressionSuccess(Flags flags) throws Exception {
        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
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
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        flags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        flags.getGaUxFeatureEnabled()
                                ? mFledgeAllowListsFilterGaUxEnabledSpy
                                : mFledgeAllowListsFilterSpy);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertTrue(callback.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionSuccessfullyRegistersEventUris() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        Uri clickUriSeller = mMockWebServerRule.uriForPath(CLICK_SELLER_PATH);
        Uri hoverUriSeller = mMockWebServerRule.uriForPath(HOVER_SELLER_PATH);
        Uri clickUriBuyer = mMockWebServerRule.uriForPath(CLICK_BUYER_PATH);
        Uri hoverUriBuyer = mMockWebServerRule.uriForPath(HOVER_BUYER_PATH);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "    registerAdBeacon('click_seller', '"
                        + clickUriSeller
                        + "');\n"
                        + "    registerAdBeacon('hover_seller', '"
                        + hoverUriSeller
                        + "');\n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "    registerAdBeacon('click_buyer', '"
                        + clickUriBuyer
                        + "');\n"
                        + "    registerAdBeacon('hover_buyer', '"
                        + hoverUriBuyer
                        + "');\n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
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
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertTrue(callback.mIsSuccess);

        // Check that database has correct seller registered events
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, DESTINATION_SELLER));
        assertEquals(
                clickUriSeller,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, DESTINATION_SELLER));

        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, DESTINATION_SELLER));
        assertEquals(
                hoverUriSeller,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, DESTINATION_SELLER));

        // Check that database has correct buyer registered events
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, DESTINATION_BUYER));
        assertEquals(
                clickUriBuyer,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, DESTINATION_BUYER));

        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_BUYER, DESTINATION_BUYER));
        assertEquals(
                hoverUriBuyer,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, HOVER_EVENT_BUYER, DESTINATION_BUYER));

        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionSucceedsButDesNotRegisterUrisThatFailDomainValidation()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        // Register uris with valid domains
        Uri hoverUriSeller = mMockWebServerRule.uriForPath(HOVER_SELLER_PATH);
        Uri clickUriBuyer = mMockWebServerRule.uriForPath(CLICK_BUYER_PATH);

        // Register uris with invalid domains by instantiating another mock server
        MockWebServer differentServer = new MockWebServer();
        differentServer.play();
        Uri clickUriSeller = Uri.parse(differentServer.getUrl(CLICK_SELLER_PATH).toString());
        Uri hoverUriBuyer = Uri.parse(differentServer.getUrl(HOVER_BUYER_PATH).toString());

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "    registerAdBeacon('click_seller', '"
                        + clickUriSeller
                        + "');\n"
                        + "    registerAdBeacon('hover_seller', '"
                        + hoverUriSeller
                        + "');\n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "    registerAdBeacon('click_buyer', '"
                        + clickUriBuyer
                        + "');\n"
                        + "    registerAdBeacon('hover_buyer', '"
                        + hoverUriBuyer
                        + "');\n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
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
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertTrue(callback.mIsSuccess);

        // Check that seller click uri was not registered
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, DESTINATION_SELLER));

        // Check that seller hover uri was registered
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, DESTINATION_SELLER));
        assertEquals(
                hoverUriSeller,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, DESTINATION_SELLER));

        // Check that buyer click uri was registered
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, DESTINATION_BUYER));
        assertEquals(
                clickUriBuyer,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, DESTINATION_BUYER));

        // Check that buyer hover uri was not registered
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_BUYER, DESTINATION_BUYER));

        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionSucceedsButDesNotRegisterMalformedUris() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        // Register valid uris
        Uri hoverUriSeller = mMockWebServerRule.uriForPath(HOVER_SELLER_PATH);
        Uri clickUriBuyer = mMockWebServerRule.uriForPath(CLICK_BUYER_PATH);

        // Register malformed uris
        Uri clickUriSeller = Uri.parse(CLICK_SELLER_PATH);
        Uri hoverUriBuyer = Uri.parse(HOVER_BUYER_PATH);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "    registerAdBeacon('click_seller', '"
                        + clickUriSeller
                        + "');\n"
                        + "    registerAdBeacon('hover_seller', '"
                        + hoverUriSeller
                        + "');\n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "    registerAdBeacon('click_buyer', '"
                        + clickUriBuyer
                        + "');\n"
                        + "    registerAdBeacon('hover_buyer', '"
                        + hoverUriBuyer
                        + "');\n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
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
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertTrue(callback.mIsSuccess);

        // Check that seller click uri was not registered
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, DESTINATION_SELLER));

        // Check that seller hover uri was registered
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, DESTINATION_SELLER));
        assertEquals(
                hoverUriSeller,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, DESTINATION_SELLER));

        // Check that buyer click uri was registered
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, DESTINATION_BUYER));
        assertEquals(
                clickUriBuyer,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, DESTINATION_BUYER));

        // Check that buyer hover uri was not registered
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_BUYER, DESTINATION_BUYER));

        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionOnlyRegisterSellerUrisWhenBuyerJSFails() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        Uri clickUriSeller = mMockWebServerRule.uriForPath(CLICK_SELLER_PATH);
        Uri hoverUriSeller = mMockWebServerRule.uriForPath(HOVER_SELLER_PATH);
        Uri clickUriBuyer = mMockWebServerRule.uriForPath(CLICK_BUYER_PATH);
        Uri hoverUriBuyer = mMockWebServerRule.uriForPath(HOVER_BUYER_PATH);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "    registerAdBeacon('click_seller', '"
                        + clickUriSeller
                        + "');\n"
                        + "    registerAdBeacon('hover_seller', '"
                        + hoverUriSeller
                        + "');\n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "    registerAdBeacon('click_buyer', '"
                        + clickUriBuyer
                        + "');\n"
                        + "    registerAdBeacon('hover_buyer', '"
                        + hoverUriBuyer
                        + "');\n"
                        + " return {'status': -1, 'results': {'reporting_uri': '"
                        + buyerReportingUri
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
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertFalse(callback.mIsSuccess);

        // Check that database has correct seller registered events
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, DESTINATION_SELLER));
        assertEquals(
                clickUriSeller,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, DESTINATION_SELLER));

        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, DESTINATION_SELLER));
        assertEquals(
                hoverUriSeller,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, DESTINATION_SELLER));

        // Check that buyer events were not registered
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, DESTINATION_BUYER));

        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_BUYER, DESTINATION_BUYER));

        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INTERNAL_ERROR);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testReportImpressionDoesNotRegisterUrisWhenSellerJSFails() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        Uri clickUriSeller = mMockWebServerRule.uriForPath(CLICK_SELLER_PATH);
        Uri hoverUriSeller = mMockWebServerRule.uriForPath(HOVER_SELLER_PATH);
        Uri clickUriBuyer = mMockWebServerRule.uriForPath(CLICK_BUYER_PATH);
        Uri hoverUriBuyer = mMockWebServerRule.uriForPath(HOVER_BUYER_PATH);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "    registerAdBeacon('click_seller', '"
                        + clickUriSeller
                        + "');\n"
                        + "    registerAdBeacon('hover_seller', '"
                        + hoverUriSeller
                        + "');\n"
                        + " return {'status': -1, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "    registerAdBeacon('click_buyer', '"
                        + clickUriBuyer
                        + "');\n"
                        + "    registerAdBeacon('hover_buyer', '"
                        + hoverUriBuyer
                        + "');\n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
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
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertFalse(callback.mIsSuccess);

        // Check that no events were registered
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, DESTINATION_SELLER));

        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, DESTINATION_SELLER));

        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, DESTINATION_BUYER));

        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_BUYER, DESTINATION_BUYER));

        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INTERNAL_ERROR);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testReportImpressionDoesNotRegisterMoreThanMaxEventUrisFromPhFlag()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        Uri clickUriSeller = mMockWebServerRule.uriForPath(CLICK_SELLER_PATH);
        Uri hoverUriSeller = mMockWebServerRule.uriForPath(HOVER_SELLER_PATH);
        Uri clickUriBuyer = mMockWebServerRule.uriForPath(CLICK_BUYER_PATH);
        Uri hoverUriBuyer = mMockWebServerRule.uriForPath(HOVER_BUYER_PATH);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "    registerAdBeacon('click_seller', '"
                        + clickUriSeller
                        + "');\n"
                        + "    registerAdBeacon('hover_seller', '"
                        + hoverUriSeller
                        + "');\n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "    registerAdBeacon('click_buyer', '"
                        + clickUriBuyer
                        + "');\n"
                        + "    registerAdBeacon('hover_buyer', '"
                        + hoverUriBuyer
                        + "');\n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
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
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        // Create new flag with overridden value so that only one pairing per ad-tech can be
        // registered
        Flags flagsWithSmallerMaxEventUris =
                new FlagsWithEnrollmentCheckEnabledSwitch(false, false) {
                    @Override
                    public long getReportImpressionMaxEventUriEntriesCount() {
                        return 1;
                    }
                };

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        flagsWithSmallerMaxEventUris,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertTrue(callback.mIsSuccess);

        // Check that only the first seller event uri pairing was registered
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, DESTINATION_SELLER));
        assertEquals(
                clickUriSeller,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, DESTINATION_SELLER));

        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, DESTINATION_SELLER));

        // Check that only the first buyer event uri pairing was registered
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, DESTINATION_BUYER));
        assertEquals(
                clickUriBuyer,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, DESTINATION_BUYER));

        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_BUYER, DESTINATION_BUYER));

        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionWithRevokedUserConsentSuccessGaUxDisabled() throws Exception {
        doReturn(AdServicesApiConsent.REVOKED).when(mConsentManagerMock).getConsent();
        reportImpressionWithRevokedUserConsentSuccess(mFlagsGaUxDisabled);
    }

    @Test
    public void testReportImpressionWithRevokedUserConsentSuccessGaUxEnabled() throws Exception {
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        reportImpressionWithRevokedUserConsentSuccess(mFlagsGaUxEnabled);
    }

    private void reportImpressionWithRevokedUserConsentSuccess(Flags flags) throws Exception {
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                throw new IllegalStateException(
                                        "No calls should be made without user consent");
                            }
                        });

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        flags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        flags.getGaUxFeatureEnabled()
                                ? mFledgeAllowListsFilterGaUxEnabledSpy
                                : mFledgeAllowListsFilterSpy);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertTrue(callback.mIsSuccess);
        assertEquals(0, server.getRequestCount());

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        anyInt());
    }

    @Test
    public void testReportImpressionFailsWhenReportResultTakesTooLong() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String jsWaitMoreThanAllowed =
                insertJsWait(2 * mFlagsGaUxDisabled.getReportImpressionOverallTimeoutMs());

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + jsWaitMoreThanAllowed
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
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

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INTERNAL_ERROR);
        assertTrue(callback.mFledgeErrorResponse.getErrorMessage().contains(TIMEOUT_MESSAGE));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testReportImpressionFailsWhenReportWinTakesTooLong() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String jsWaitMoreThanAllowed =
                insertJsWait(2 * mFlagsGaUxDisabled.getReportImpressionOverallTimeoutMs());

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + jsWaitMoreThanAllowed
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
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

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INTERNAL_ERROR);
        assertTrue(callback.mFledgeErrorResponse.getErrorMessage().contains(TIMEOUT_MESSAGE));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testReportImpressionFailsWhenOverallJSTimesOut() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String jsWaitMoreThanAllowed =
                insertJsWait(
                        (long) (.25 * mFlagsGaUxDisabled.getReportImpressionOverallTimeoutMs()));

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + jsWaitMoreThanAllowed
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + jsWaitMoreThanAllowed
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        mMockWebServerRule.startMockWebServer(
                List.of(
                        new MockResponse()
                                .setBody(sellerDecisionLogicJs)
                                .throttleBody(
                                        mBytesPerPeriod,
                                        (long)
                                                (.5
                                                        * mFlagsGaUxDisabled
                                                                .getReportImpressionOverallTimeoutMs()),
                                        TimeUnit.MILLISECONDS),
                        new MockResponse(),
                        new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INTERNAL_ERROR);
        assertTrue(callback.mFledgeErrorResponse.getErrorMessage().contains(TIMEOUT_MESSAGE));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testReportImpressionFailsWhenJSFetchTakesTooLong() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        mMockWebServerRule.startMockWebServer(
                List.of(
                        new MockResponse()
                                .setBody(sellerDecisionLogicJs)
                                .throttleBody(
                                        mBytesPerPeriod,
                                        mFlagsGaUxDisabled.getReportImpressionOverallTimeoutMs(),
                                        TimeUnit.MILLISECONDS),
                        new MockResponse(),
                        new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INTERNAL_ERROR);
        assertTrue(callback.mFledgeErrorResponse.getErrorMessage().contains(TIMEOUT_MESSAGE));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testReportImpressionFailsWithInvalidAdSelectionId() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
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

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
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
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        ReportImpressionInput request =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(INCORRECT_AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, request);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INVALID_ARGUMENT);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_INVALID_ARGUMENT),
                        anyInt());
    }

    @Test
    public void testReportImpressionBadSellerJavascriptFailsWithInternalError() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String invalidSellerDecisionLogicJsMissingCurlyBracket =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': 'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        mMockWebServerRule.startMockWebServer(
                List.of(
                        new MockResponse().setBody(invalidSellerDecisionLogicJsMissingCurlyBracket),
                        new MockResponse(),
                        new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
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
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        ReportImpressionInput request =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, request);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INTERNAL_ERROR);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testReportImpressionBadBuyerJavascriptFailsWithInternalError() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String inValidBuyerDecisionLogicJsMissingCurlyBracket =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': 'reporting_uri': '"
                        + buyerReportingUri
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
                        .setBuyerDecisionLogicJs(inValidBuyerDecisionLogicJsMissingCurlyBracket)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
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
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        ReportImpressionInput request =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, request);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INTERNAL_ERROR);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testReportImpressionUseDevOverrideForSellerJS() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
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

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        // Set dev override for this AdSelection

        DBAdSelectionOverride adSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(
                                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                        adSelectionConfig))
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(sellerDecisionLogicJs)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();
        mAdSelectionEntryDao.persistAdSelectionOverride(adSelectionOverride);

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);
        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertTrue(callback.mIsSuccess);
        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionUseDevOverrideForSellerJSSuccessfullyRegistersEventUris()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri clickUriSeller = mMockWebServerRule.uriForPath(CLICK_SELLER_PATH);
        Uri hoverUriSeller = mMockWebServerRule.uriForPath(HOVER_SELLER_PATH);
        Uri clickUriBuyer = mMockWebServerRule.uriForPath(CLICK_BUYER_PATH);
        Uri hoverUriBuyer = mMockWebServerRule.uriForPath(HOVER_BUYER_PATH);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "    registerAdBeacon('click_seller', '"
                        + clickUriSeller
                        + "');\n"
                        + "    registerAdBeacon('hover_seller', '"
                        + hoverUriSeller
                        + "');\n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "    registerAdBeacon('click_buyer', '"
                        + clickUriBuyer
                        + "');\n"
                        + "    registerAdBeacon('hover_buyer', '"
                        + hoverUriBuyer
                        + "');\n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
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

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        // Set dev override for this AdSelection

        DBAdSelectionOverride adSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(
                                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                        adSelectionConfig))
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(sellerDecisionLogicJs)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();
        mAdSelectionEntryDao.persistAdSelectionOverride(adSelectionOverride);

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);
        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertTrue(callback.mIsSuccess);

        // Check that database has correct seller registered events
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, DESTINATION_SELLER));
        assertEquals(
                clickUriSeller,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, DESTINATION_SELLER));

        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, DESTINATION_SELLER));
        assertEquals(
                hoverUriSeller,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, DESTINATION_SELLER));

        // Check that database has correct buyer registered events
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, DESTINATION_BUYER));
        assertEquals(
                clickUriBuyer,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, DESTINATION_BUYER));

        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_BUYER, DESTINATION_BUYER));
        assertEquals(
                hoverUriBuyer,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, HOVER_EVENT_BUYER, DESTINATION_BUYER));

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testOverrideAdSelectionConfigRemoteInfoSuccess() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        AdSelectionOverrideTestCallback callback =
                callAddOverrideForSelectAds(
                        adSelectionService,
                        adSelectionConfig,
                        DUMMY_DECISION_LOGIC_JS,
                        DUMMY_TRUSTED_SCORING_SIGNALS);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                adSelectionConfig),
                        TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(eq(SHORT_API_NAME_OVERRIDE), eq(STATUS_SUCCESS), anyInt());
    }

    @Test
    public void testOverrideAdSelectionConfigRemoteInfoWithRevokedUserConsentSuccess()
            throws Exception {
        doReturn(AdServicesApiConsent.REVOKED).when(mConsentManagerMock).getConsent();

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        AdSelectionOverrideTestCallback callback =
                callAddOverrideForSelectAds(
                        adSelectionService,
                        adSelectionConfig,
                        DUMMY_DECISION_LOGIC_JS,
                        DUMMY_TRUSTED_SCORING_SIGNALS);

        assertTrue(callback.mIsSuccess);
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                adSelectionConfig),
                        TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(SHORT_API_NAME_OVERRIDE), eq(STATUS_USER_CONSENT_REVOKED), anyInt());
    }

    @Test
    public void testOverrideAdSelectionConfigRemoteInfoFailsWithDevOptionsDisabled() {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        assertThrows(
                SecurityException.class,
                () ->
                        callAddOverrideForSelectAds(
                                adSelectionService,
                                adSelectionConfig,
                                DUMMY_DECISION_LOGIC_JS,
                                DUMMY_TRUSTED_SCORING_SIGNALS));

        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                adSelectionConfig),
                        TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(SHORT_API_NAME_OVERRIDE), eq(STATUS_INTERNAL_ERROR), anyInt());
    }

    @Test
    public void testRemoveAdSelectionConfigRemoteInfoOverrideSuccess() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        String adSelectionConfigId =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig);

        DBAdSelectionOverride dbAdSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        AdSelectionOverrideTestCallback callback =
                callRemoveOverride(adSelectionService, adSelectionConfig);

        assertTrue(callback.mIsSuccess);
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(SHORT_API_NAME_REMOVE_OVERRIDE), eq(STATUS_SUCCESS), anyInt());
    }

    @Test
    public void testRemoveAdSelectionConfigRemoteInfoOverrideWithRevokedUserConsentSuccess()
            throws Exception {
        doReturn(AdServicesApiConsent.REVOKED).when(mConsentManagerMock).getConsent();

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        String adSelectionConfigId =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig);

        DBAdSelectionOverride dbAdSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        AdSelectionOverrideTestCallback callback =
                callRemoveOverride(adSelectionService, adSelectionConfig);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(SHORT_API_NAME_REMOVE_OVERRIDE),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        anyInt());
    }

    @Test
    public void testRemoveAdSelectionConfigRemoteInfoOverrideFailsWithDevOptionsDisabled() {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        String adSelectionConfigId =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig);

        DBAdSelectionOverride dbAdSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        assertThrows(
                SecurityException.class,
                () -> callRemoveOverride(adSelectionService, adSelectionConfig));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(SHORT_API_NAME_REMOVE_OVERRIDE), eq(STATUS_INTERNAL_ERROR), anyInt());
    }

    @Test
    public void testRemoveAdSelectionConfigRemoteInfoOverrideDoesNotDeleteWithIncorrectPackageName()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
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
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        String adSelectionConfigId =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig);

        DBAdSelectionOverride dbAdSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        AdSelectionOverrideTestCallback callback =
                callRemoveOverride(adSelectionService, adSelectionConfig);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(SHORT_API_NAME_REMOVE_OVERRIDE), eq(STATUS_SUCCESS), anyInt());
    }

    @Test
    public void testResetAllAdSelectionConfigRemoteOverridesDoesNotDeleteWithIncorrectPackageName()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
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
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionConfig adSelectionConfig1 = mAdSelectionConfigBuilder.build();
        AdSelectionConfig adSelectionConfig2 =
                mAdSelectionConfigBuilder
                        .setSeller(AdTechIdentifier.fromString("adidas.com"))
                        .setDecisionLogicUri(Uri.parse("https://adidas.com/decisoin_logic_uri"))
                        .build();
        AdSelectionConfig adSelectionConfig3 =
                mAdSelectionConfigBuilder
                        .setSeller(AdTechIdentifier.fromString("nike.com"))
                        .setDecisionLogicUri(Uri.parse("https://nike.com/decisoin_logic_uri"))
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
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride2 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId2)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride3 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId3)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride1);
        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride2);
        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride3);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId3, TEST_PACKAGE_NAME));

        AdSelectionOverrideTestCallback callback = callResetAllOverrides(adSelectionService);

        assertTrue(callback.mIsSuccess);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId3, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(SHORT_API_NAME_RESET_ALL_OVERRIDES), eq(STATUS_SUCCESS), anyInt());
    }

    @Test
    public void testResetAllAdSelectionConfigRemoteOverridesSuccess() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionConfig adSelectionConfig1 = mAdSelectionConfigBuilder.build();
        AdSelectionConfig adSelectionConfig2 =
                mAdSelectionConfigBuilder
                        .setSeller(AdTechIdentifier.fromString("adidas.com"))
                        .setDecisionLogicUri(Uri.parse("https://adidas.com/decisoin_logic_uri"))
                        .build();
        AdSelectionConfig adSelectionConfig3 =
                mAdSelectionConfigBuilder
                        .setSeller(AdTechIdentifier.fromString("nike.com"))
                        .setDecisionLogicUri(Uri.parse("https://nike.com/decisoin_logic_uri"))
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
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride2 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId2)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride3 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId3)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride1);
        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride2);
        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride3);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId3, TEST_PACKAGE_NAME));

        AdSelectionOverrideTestCallback callback = callResetAllOverrides(adSelectionService);

        assertTrue(callback.mIsSuccess);

        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId1, TEST_PACKAGE_NAME));
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId2, TEST_PACKAGE_NAME));
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId3, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(SHORT_API_NAME_RESET_ALL_OVERRIDES), eq(STATUS_SUCCESS), anyInt());
    }

    @Test
    public void testResetAllAdSelectionConfigRemoteOverridesWithRevokedUserConsentSuccess()
            throws Exception {
        doReturn(AdServicesApiConsent.REVOKED).when(mConsentManagerMock).getConsent();

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionConfig adSelectionConfig1 = mAdSelectionConfigBuilder.build();
        AdSelectionConfig adSelectionConfig2 =
                mAdSelectionConfigBuilder
                        .setSeller(AdTechIdentifier.fromString("adidas.com"))
                        .setDecisionLogicUri(Uri.parse("https://adidas.com/decisoin_logic_uri"))
                        .build();
        AdSelectionConfig adSelectionConfig3 =
                mAdSelectionConfigBuilder
                        .setSeller(AdTechIdentifier.fromString("nike.com"))
                        .setDecisionLogicUri(Uri.parse("https://nike.com/decisoin_logic_uri"))
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
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride2 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId2)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride3 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId3)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride1);
        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride2);
        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride3);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId3, TEST_PACKAGE_NAME));

        AdSelectionOverrideTestCallback callback = callResetAllOverrides(adSelectionService);

        assertTrue(callback.mIsSuccess);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId3, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(SHORT_API_NAME_RESET_ALL_OVERRIDES),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        anyInt());
    }

    @Test
    public void testResetAllAdSelectionConfigRemoteOverridesFailsWithDevOptionsDisabled() {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionConfig adSelectionConfig1 = mAdSelectionConfigBuilder.build();
        AdSelectionConfig adSelectionConfig2 =
                mAdSelectionConfigBuilder
                        .setSeller(AdTechIdentifier.fromString("adidas.com"))
                        .setDecisionLogicUri(Uri.parse("https://adidas.com/decisoin_logic_uri"))
                        .build();
        AdSelectionConfig adSelectionConfig3 =
                mAdSelectionConfigBuilder
                        .setSeller(AdTechIdentifier.fromString("nike.com"))
                        .setDecisionLogicUri(Uri.parse("https://nike.com/decisoin_logic_uri"))
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
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride2 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId2)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride3 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId3)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride1);
        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride2);
        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride3);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId3, TEST_PACKAGE_NAME));

        assertThrows(SecurityException.class, () -> callResetAllOverrides(adSelectionService));

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId3, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(SHORT_API_NAME_RESET_ALL_OVERRIDES),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testCloseJSScriptEngineConnectionAtShutDown() {
        JSScriptEngine jsScriptEngineMock = mock(JSScriptEngine.class);
        when(JSScriptEngine.getInstance(any())).thenReturn(jsScriptEngineMock);

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        adSelectionService.destroy();
        verify(jsScriptEngineMock).shutdown();
    }

    @Test
    public void testReportImpressionForegroundCheckEnabledFails_throwsException() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        doThrow(new WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(
                        Process.myUid(),
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                        null);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        ReportImpressionInput request =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(INCORRECT_AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, request);

        // The call fails because there is no ad selection with the given ID
        assertFalse(callback.mIsSuccess);
        assertEquals(
                AdServicesStatusUtils.STATUS_BACKGROUND_CALLER,
                callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                AdServicesStatusUtils.ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());
    }

    @Test
    public void testReportImpressionForegroundCheckDisabled_acceptBackgroundApp() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        doThrow(new WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(
                        Process.myUid(),
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                        null);

        AdSelectionConfig adSelectionConfig =
                mAdSelectionConfigBuilder
                        .setCustomAudienceBuyers(ImmutableList.of())
                        .setPerBuyerSignals(ImmutableMap.of())
                        .build();

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        FlagsWithOverriddenFledgeChecks.createFlagsWithFledgeChecksDisabled(),
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        ReportImpressionInput request =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(INCORRECT_AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, request);

        // The call fails because there is no ad selection with the given ID
        assertFalse(callback.mIsSuccess);
        assertEquals(
                UNABLE_TO_FIND_AD_SELECTION_WITH_GIVEN_ID,
                callback.mFledgeErrorResponse.getErrorMessage());
    }

    @Test
    public void testOverrideAdSelectionForegroundCheckEnabledFails_throwsException()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        doThrow(new WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO,
                        null);

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        AdSelectionOverrideTestCallback callback =
                callAddOverrideForSelectAds(
                        adSelectionService,
                        adSelectionConfig,
                        DUMMY_DECISION_LOGIC_JS,
                        DUMMY_TRUSTED_SCORING_SIGNALS);

        // The call fails because there is no ad selection with the given ID
        assertFalse(callback.mIsSuccess);
        assertEquals(
                AdServicesStatusUtils.STATUS_BACKGROUND_CALLER,
                callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                AdServicesStatusUtils.ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());
    }

    @Test
    public void testOverrideAdSelectionForegroundCheckDisabled_acceptBackgroundApp()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        doThrow(new WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO,
                        null);

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        FlagsWithOverriddenFledgeChecks.createFlagsWithFledgeChecksDisabled(),
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        AdSelectionOverrideTestCallback callback =
                callAddOverrideForSelectAds(
                        adSelectionService,
                        adSelectionConfig,
                        DUMMY_DECISION_LOGIC_JS,
                        DUMMY_TRUSTED_SCORING_SIGNALS);

        assertTrue(callback.mIsSuccess);
    }

    @Test
    public void testRemoveOverrideForegroundCheckEnabledFails_throwsException() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        int apiName =
                AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE;
        doThrow(new WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(Process.myUid(), apiName, null);

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        AdSelectionOverrideTestCallback callback =
                callRemoveOverride(adSelectionService, adSelectionConfig);

        // The call fails because there is no ad selection with the given ID
        assertFalse(callback.mIsSuccess);
        assertEquals(
                AdServicesStatusUtils.STATUS_BACKGROUND_CALLER,
                callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                AdServicesStatusUtils.ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());
    }

    @Test
    public void testRemoveOverrideForegroundCheckDisabled_acceptBackgroundApp() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        int apiName =
                AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE;
        doThrow(new WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(Process.myUid(), apiName, null);

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        FlagsWithOverriddenFledgeChecks.createFlagsWithFledgeChecksDisabled(),
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        AdSelectionOverrideTestCallback callback =
                callRemoveOverride(adSelectionService, adSelectionConfig);

        assertTrue(callback.mIsSuccess);
    }

    @Test
    public void testResetAllOverridesForegroundCheckEnabledFails_throwsException()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        int apiName =
                AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES;
        doThrow(new WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(Process.myUid(), apiName, null);

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionOverrideTestCallback callback = callResetAllOverrides(adSelectionService);

        // The call fails because there is no ad selection with the given ID
        assertFalse(callback.mIsSuccess);
        assertEquals(
                AdServicesStatusUtils.STATUS_BACKGROUND_CALLER,
                callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                AdServicesStatusUtils.ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());
    }

    @Test
    public void testResetAllOverridesForegroundCheckDisabled_acceptBackgroundApp()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        int apiName =
                AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES;
        doThrow(new WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(Process.myUid(), apiName, null);

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        FlagsWithOverriddenFledgeChecks.createFlagsWithFledgeChecksDisabled(),
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionOverrideTestCallback callback = callResetAllOverrides(adSelectionService);

        assertTrue(callback.mIsSuccess);
    }

    @Test
    public void testReportImpressionFailsWithInvalidPackageName() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        String otherPackageName = CommonFixture.TEST_PACKAGE_NAME + "incorrectPackage";

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
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

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(otherPackageName)
                        .build();

        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_UNAUTHORIZED);
        assertEquals(
                callback.mFledgeErrorResponse.getErrorMessage(),
                AdServicesStatusUtils
                        .SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE);
    }

    @Test
    public void testReportImpressionFailsWhenAppCannotUsePPApi() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mFledgeAllowListsFilterSpy)
                .assertAppCanUsePpapi(
                        TEST_PACKAGE_NAME, AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION);

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
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

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_CALLER_NOT_ALLOWED, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());

        // TODO(b/242139312): Remove atLeastOnce once this the double logging is addressed
        Mockito.verify(mAdServicesLoggerMock, Mockito.atLeastOnce())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_CALLER_NOT_ALLOWED),
                        anyInt());
    }

    @Test
    public void testReportImpressionFailsWhenAdTechFailsEnrollmentCheck() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        // Reset flags to perform enrollment check
        mFlagsGaUxDisabled = new FlagsWithEnrollmentCheckEnabledSwitch(true, false);

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
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

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_CALLER_NOT_ALLOWED, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());

        // TODO(b/242139312): Remove atLeastOnce once this the double logging is addressed
        Mockito.verify(mAdServicesLoggerMock, Mockito.atLeastOnce())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_CALLER_NOT_ALLOWED),
                        anyInt());
    }

    @Test
    public void testReportImpressionSucceedsWhenAdTechPassesEnrollmentCheck() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        // Reset flags to perform enrollment check
        mFlagsGaUxDisabled = new FlagsWithEnrollmentCheckEnabledSwitch(true, false);

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
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

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        doNothing()
                .when(mFledgeAuthorizationFilterSpy)
                .assertAdTechAllowed(
                        CONTEXT,
                        TEST_PACKAGE_NAME,
                        adSelectionConfig.getSeller(),
                        AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION);

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertTrue(callback.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testAdSelectionConfigInvalidSellerAndSellerUris() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
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
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);
        ReportImpressionInput request =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(INCORRECT_AD_SELECTION_ID)
                        .setAdSelectionConfig(invalidAdSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, request);

        assertFalse(callback.mIsSuccess);

        FledgeErrorResponse response = callback.mFledgeErrorResponse;
        assertEquals(
                "Error response code mismatch", STATUS_INVALID_ARGUMENT, response.getStatusCode());

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_INVALID_ARGUMENT),
                        anyInt());
    }

    @Test
    public void testReportImpressionSuccessThrottledSubsequentCallFailure() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
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

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        Flags flagsWithThrottling =
                new Flags() {
                    @Override
                    public boolean getEnforceForegroundStatusForFledgeRunAdSelection() {
                        return true;
                    }

                    @Override
                    public boolean getEnforceForegroundStatusForFledgeReportImpression() {
                        return true;
                    }

                    @Override
                    public boolean getEnforceForegroundStatusForFledgeOverrides() {
                        return true;
                    }

                    @Override
                    public long getReportImpressionOverallTimeoutMs() {
                        return 500;
                    }

                    @Override
                    public boolean getEnforceIsolateMaxHeapSize() {
                        return false;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }

                    @Override
                    public float getSdkRequestPermitsPerSecond() {
                        // Getting default value of flags with throttling enabled
                        return 1;
                    }
                };
        Throttler.destroyExistingThrottler();
        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        flagsWithThrottling,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // First call should succeed
        ReportImpressionTestCallback callbackFirstCall =
                callReportImpression(adSelectionService, input);

        // Immediately made subsequent call should fail
        ReportImpressionTestCallback callbackSubsequentCall =
                callReportImpression(adSelectionService, input);

        assertTrue(callbackFirstCall.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_SUCCESS),
                        anyInt());

        assertFalse(callbackSubsequentCall.mIsSuccess);
        assertEquals(
                STATUS_RATE_LIMIT_REACHED,
                callbackSubsequentCall.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                callbackSubsequentCall.mFledgeErrorResponse.getErrorMessage(),
                REPORT_IMPRESSION_THROTTLED);
        resetThrottlerToNoRateLimits();
    }

    @Test
    public void testReportImpressionDoestNotReportWhenUrisDoNotMatchDomain() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        // Instantiate a server with different domain from buyer and seller for reporting
        MockWebServer reportingServer = new MockWebServer();
        reportingServer.play();
        Uri sellerReportingUri = Uri.parse(reportingServer.getUrl(mSellerReportingPath).toString());
        Uri buyerReportingUri = Uri.parse(reportingServer.getUrl(mBuyerReportingPath).toString());

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse().setBody(sellerDecisionLogicJs)));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);
        assertTrue(callback.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        // Assert that reporting didn't happen
        assertEquals(reportingServer.getRequestCount(), 0);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionOnlyReportsBuyerWhenSellerReportingUriDoesNotMatchDomain()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        // Instantiate a server with a different domain than seller
        MockWebServer sellerServer = new MockWebServer();
        sellerServer.play();
        Uri sellerReportingUri = Uri.parse(sellerServer.getUrl(mSellerReportingPath).toString());

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);
        assertTrue(callback.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        assertEquals(mBuyerReportingPath, server.takeRequest().getPath());

        // Assert that buyer reporting didn't happen
        assertEquals(sellerServer.getRequestCount(), 0);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionOnlyReportsSellerWhenBuyerReportingUriDoesNotMatchDomain()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);

        // Instantiate a server with a different domain than buyer
        MockWebServer buyerServer = new MockWebServer();
        buyerServer.play();
        Uri buyerReportingUri = Uri.parse(buyerServer.getUrl(mBuyerReportingPath).toString());

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);
        assertTrue(callback.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        assertEquals(mSellerReportingPath, server.takeRequest().getPath());

        // Assert that buyer reporting didn't happen
        assertEquals(buyerServer.getRequestCount(), 0);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testAddOverrideAdSelectionFromOutcomesConfigRemoteInfoSuccess() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        AdSelectionOverrideTestCallback callback =
                callAddOverrideForSelectAds(
                        adSelectionService,
                        config,
                        DUMMY_SELECTION_LOGIC_JS,
                        DUMMY_SELECTION_SIGNALS);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(
                                config),
                        TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testOverrideAdSelectionFromOutcomesConfigRemoteInfoWithRevokedUserConsentSuccess()
            throws Exception {
        doReturn(AdServicesApiConsent.REVOKED).when(mConsentManagerMock).getConsent();

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        AdSelectionOverrideTestCallback callback =
                callAddOverrideForSelectAds(
                        adSelectionService,
                        config,
                        DUMMY_SELECTION_LOGIC_JS,
                        DUMMY_SELECTION_SIGNALS);

        assertTrue(callback.mIsSuccess);
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(
                                config),
                        TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        anyInt());
    }

    @Test
    public void testOverrideAdSelectionFromOutcomesConfigRemoteInfoFailsWithDevOptionsDisabled() {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        assertThrows(
                SecurityException.class,
                () ->
                        callAddOverrideForSelectAds(
                                adSelectionService,
                                config,
                                DUMMY_DECISION_LOGIC_JS,
                                DUMMY_TRUSTED_SCORING_SIGNALS));

        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(
                                config),
                        TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testRemoveAdSelectionFromOutcomesConfigRemoteInfoOverrideSuccess()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        String configId =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config);

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId, TEST_PACKAGE_NAME));

        AdSelectionOverrideTestCallback callback = callRemoveOverride(adSelectionService, config);

        assertTrue(callback.mIsSuccess);
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        configId, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void
            testRemoveAdSelectionFromOutcomesConfigRemoteInfoOverrideWithRevokedUserConsentSuccess()
                    throws Exception {
        doReturn(AdServicesApiConsent.REVOKED).when(mConsentManagerMock).getConsent();

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        String adSelectionConfigId =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config);

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(adSelectionConfigId)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        AdSelectionOverrideTestCallback callback = callRemoveOverride(adSelectionService, config);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        anyInt());
    }

    @Test
    public void
            testRemoveAdSelectionFromOutcomesConfigRemoteInfoOverrideFailsWithDevOptionsDisabled() {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        String adSelectionConfigId =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config);

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(adSelectionConfigId)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        assertThrows(SecurityException.class, () -> callRemoveOverride(adSelectionService, config));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testRemoveAdSelectionFromOutcomesConfigRemoteOverrideNotDeleteIncorrectPackageName()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
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
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        String adSelectionConfigId =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config);

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(adSelectionConfigId)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        AdSelectionOverrideTestCallback callback = callRemoveOverride(adSelectionService, config);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testResetAllAdSelectionFromOutcomesConfigRemoteOverridesSuccess() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionFromOutcomesConfig config1 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();
        AdSelectionFromOutcomesConfig config2 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        AdTechIdentifier.fromString("adidas.com"),
                        Uri.parse("https://adidas.com/decisoin_logic_uri"));
        AdSelectionFromOutcomesConfig config3 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        AdTechIdentifier.fromString("nike.com"),
                        Uri.parse("https://nike.com/decisoin_logic_uri"));

        String configId1 =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config1);
        String configId2 =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config2);
        String configId3 =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config3);

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride1 =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId1)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride2 =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId2)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride3 =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId3)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride1);
        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride2);
        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride3);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId3, TEST_PACKAGE_NAME));

        AdSelectionOverrideTestCallback callback =
                callResetAllSelectionOutcomesOverrides(adSelectionService);

        assertTrue(callback.mIsSuccess);

        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId1, TEST_PACKAGE_NAME));
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId2, TEST_PACKAGE_NAME));
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId3, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void
            testResetAllAdSelectionFromOutcomesConfigRemoteOverridesWithRevokedUserConsentSuccess()
                    throws Exception {
        doReturn(AdServicesApiConsent.REVOKED).when(mConsentManagerMock).getConsent();

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionFromOutcomesConfig config1 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();
        AdSelectionFromOutcomesConfig config2 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        AdTechIdentifier.fromString("adidas.com"),
                        Uri.parse("https://adidas.com/decisoin_logic_uri"));
        AdSelectionFromOutcomesConfig config3 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        AdTechIdentifier.fromString("nike.com"),
                        Uri.parse("https://nike.com/decisoin_logic_uri"));

        String configId1 =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config1);
        String configId2 =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config2);
        String configId3 =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config3);

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride1 =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId1)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride2 =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId2)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride3 =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId3)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride1);
        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride2);
        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride3);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId3, TEST_PACKAGE_NAME));

        AdSelectionOverrideTestCallback callback =
                callResetAllSelectionOutcomesOverrides(adSelectionService);

        assertTrue(callback.mIsSuccess);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId3, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        anyInt());
    }

    @Test
    public void
            testResetAllAdSelectionFromOutcomesConfigRemoteOverridesFailsWithDevOptionsDisabled() {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionFromOutcomesConfig config1 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();
        AdSelectionFromOutcomesConfig config2 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        AdTechIdentifier.fromString("adidas.com"),
                        Uri.parse("https://adidas.com/decisoin_logic_uri"));
        AdSelectionFromOutcomesConfig config3 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        AdTechIdentifier.fromString("nike.com"),
                        Uri.parse("https://nike.com/decisoin_logic_uri"));

        String configId1 =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config1);
        String configId2 =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config2);
        String configId3 =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config3);

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride1 =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId1)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride2 =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId2)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride3 =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId3)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride1);
        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride2);
        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride3);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId3, TEST_PACKAGE_NAME));

        assertThrows(
                SecurityException.class,
                () -> callResetAllSelectionOutcomesOverrides(adSelectionService));

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId3, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testResetAllAdSelectionFromOutcomesConfigRemoteOverrideNotDeleteIncorrectPkgName()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
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
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionFromOutcomesConfig config1 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();
        AdSelectionFromOutcomesConfig config2 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        AdTechIdentifier.fromString("adidas.com"),
                        Uri.parse("https://adidas.com/decisoin_logic_uri"));
        AdSelectionFromOutcomesConfig config3 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        AdTechIdentifier.fromString("nike.com"),
                        Uri.parse("https://nike.com/decisoin_logic_uri"));

        String configId1 =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config1);
        String configId2 =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config2);
        String configId3 =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config3);

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride1 =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId1)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride2 =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId2)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride3 =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId3)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride1);
        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride2);
        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride3);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId3, TEST_PACKAGE_NAME));

        AdSelectionOverrideTestCallback callback =
                callResetAllSelectionOutcomesOverrides(adSelectionService);

        assertTrue(callback.mIsSuccess);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId3, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testOverrideAdSelectionConfigRemoteOverridesSuccess() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        Map<Long, Double> adSelectionIdToBidMap =
                Map.of(
                        AD_SELECTION_ID_1, 10.0,
                        AD_SELECTION_ID_2, 20.0,
                        AD_SELECTION_ID_3, 30.0);
        persistAdSelectionEntryDaoResults(adSelectionIdToBidMap);

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        List.of(AD_SELECTION_ID_1, AD_SELECTION_ID_2, AD_SELECTION_ID_3),
                        AdSelectionSignals.EMPTY,
                        Uri.parse("https://this.uri.isnt/called"));

        final String selectionPickSmallestAdSelectionIdLogicJs =
                "function selectOutcome(outcomes, selection_signals) {\n"
                        + "    outcomes.sort(function(a, b) { return a.id - b.id;});\n"
                        + "    return {'status': 0, 'result': outcomes[0]};\n"
                        + "}";

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlagsGaUxDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        AdSelectionOverrideTestCallback overridesCallback =
                callAddOverrideForSelectAds(
                        adSelectionService,
                        config,
                        selectionPickSmallestAdSelectionIdLogicJs,
                        AdSelectionSignals.EMPTY);

        assertTrue(overridesCallback.mIsSuccess);
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(
                                config),
                        TEST_PACKAGE_NAME));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_SUCCESS),
                        anyInt());

        AdSelectionFromOutcomesTestCallback selectionCallback =
                invokeSelectAdsFromOutcomes(adSelectionService, config, TEST_PACKAGE_NAME);

        assertTrue(selectionCallback.mIsSuccess);
        assertEquals(AD_SELECTION_ID_1, selectionCallback.mAdSelectionResponse.getAdSelectionId());
    }

    private void persistAdSelectionEntryDaoResults(Map<Long, Double> adSelectionIdToBidMap) {
        persistAdSelectionEntryDaoResults(adSelectionIdToBidMap, TEST_PACKAGE_NAME);
    }

    private void persistAdSelectionEntryDaoResults(
            Map<Long, Double> adSelectionIdToBidMap, String callerPackageName) {
        final Uri biddingLogicUri1 = Uri.parse("https://www.domain.com/logic/1");
        final Uri renderUri = Uri.parse("https://www.domain.com/advert/");
        final Instant activationTime = Instant.now();
        final String contextualSignals = "contextual_signals";
        final CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignals();

        for (Map.Entry<Long, Double> entry : adSelectionIdToBidMap.entrySet()) {
            final DBAdSelection dbAdSelectionEntry =
                    new DBAdSelection.Builder()
                            .setAdSelectionId(entry.getKey())
                            .setCustomAudienceSignals(customAudienceSignals)
                            .setContextualSignals(contextualSignals)
                            .setBiddingLogicUri(biddingLogicUri1)
                            .setWinningAdRenderUri(renderUri)
                            .setWinningAdBid(entry.getValue())
                            .setCreationTimestamp(activationTime)
                            .setCallerPackageName(callerPackageName)
                            .build();
            mAdSelectionEntryDao.persistAdSelection(dbAdSelectionEntry);
        }
    }

    /**
     * Given Throttler is singleton, & shared across tests, this method should be invoked after
     * tests that impose restrictive rate limits.
     */
    private void resetThrottlerToNoRateLimits() {
        Throttler.destroyExistingThrottler();
        final float noRateLimit = -1;
        Flags mockNoRateLimitFlags = mock(Flags.class);
        doReturn(noRateLimit).when(mockNoRateLimitFlags).getSdkRequestPermitsPerSecond();
        Throttler.getInstance(mockNoRateLimitFlags);
    }

    private AdSelectionOverrideTestCallback callAddOverrideForSelectAds(
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
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        adSelectionService.overrideAdSelectionConfigRemoteInfo(
                adSelectionConfig, decisionLogicJS, trustedScoringSignals, callback);
        resultLatch.await();
        return callback;
    }

    private AdSelectionOverrideTestCallback callAddOverrideForSelectAds(
            AdSelectionServiceImpl adSelectionService,
            AdSelectionFromOutcomesConfig adSelectionFromOutcomesConfig,
            String selectionLogic,
            AdSelectionSignals selectionSignals)
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
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        adSelectionService.overrideAdSelectionFromOutcomesConfigRemoteInfo(
                adSelectionFromOutcomesConfig, selectionLogic, selectionSignals, callback);
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
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        adSelectionService.removeAdSelectionConfigRemoteInfoOverride(adSelectionConfig, callback);
        resultLatch.await();
        return callback;
    }

    private AdSelectionOverrideTestCallback callRemoveOverride(
            AdSelectionServiceImpl adSelectionService, AdSelectionFromOutcomesConfig config)
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
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        adSelectionService.removeAdSelectionFromOutcomesConfigRemoteInfoOverride(config, callback);
        resultLatch.await();
        return callback;
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
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        adSelectionService.resetAllAdSelectionConfigRemoteOverrides(callback);
        resultLatch.await();
        return callback;
    }

    private AdSelectionOverrideTestCallback callResetAllSelectionOutcomesOverrides(
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
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        adSelectionService.resetAllAdSelectionFromOutcomesConfigRemoteOverrides(callback);
        resultLatch.await();
        return callback;
    }

    private AdSelectionFromOutcomesTestCallback invokeSelectAdsFromOutcomes(
            AdSelectionServiceImpl adSelectionService,
            AdSelectionFromOutcomesConfig adSelectionFromOutcomesConfig,
            String callerPackageName)
            throws InterruptedException, RemoteException {

        CountDownLatch countdownLatch = new CountDownLatch(1);
        AdSelectionFromOutcomesTestCallback adSelectionTestCallback =
                new AdSelectionFromOutcomesTestCallback(countdownLatch);

        AdSelectionFromOutcomesInput input =
                new AdSelectionFromOutcomesInput.Builder()
                        .setAdSelectionFromOutcomesConfig(adSelectionFromOutcomesConfig)
                        .setCallerPackageName(callerPackageName)
                        .build();

        adSelectionService.selectAdsFromOutcomes(input, null, adSelectionTestCallback);
        adSelectionTestCallback.mCountDownLatch.await();
        return adSelectionTestCallback;
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
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        adSelectionService.reportImpression(requestParams, callback);
        resultLatch.await();
        return callback;
    }

    private String insertJsWait(long waitTime) {
        return "    const wait = (ms) => {\n"
                + "       var start = new Date().getTime();\n"
                + "       var end = start;\n"
                + "       while(end < start + ms) {\n"
                + "         end = new Date().getTime();\n"
                + "      }\n"
                + "    }\n"
                + String.format(Locale.ENGLISH, "    wait(\"%d\");\n", waitTime);
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

    static class AdSelectionFromOutcomesTestCallback extends AdSelectionCallback.Stub {

        final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        AdSelectionResponse mAdSelectionResponse;
        FledgeErrorResponse mFledgeErrorResponse;

        AdSelectionFromOutcomesTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
            mAdSelectionResponse = null;
            mFledgeErrorResponse = null;
        }

        @Override
        public void onSuccess(AdSelectionResponse adSelectionResponse) throws RemoteException {
            mIsSuccess = true;
            mAdSelectionResponse = adSelectionResponse;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mIsSuccess = false;
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }

    private static class FlagsWithEnrollmentCheckEnabledSwitch implements Flags {
        private final boolean mEnrollmentCheckEnabled;
        private final boolean mIsGaUxEnabled;

        FlagsWithEnrollmentCheckEnabledSwitch(
                boolean enrollmentCheckEnabled, boolean isGaUxEnabled) {
            mEnrollmentCheckEnabled = enrollmentCheckEnabled;
            mIsGaUxEnabled = isGaUxEnabled;
        }

        @Override
        public boolean getDisableFledgeEnrollmentCheck() {
            return !mEnrollmentCheckEnabled;
        }

        @Override
        public boolean getEnforceForegroundStatusForFledgeRunAdSelection() {
            return true;
        }

        @Override
        public boolean getEnforceForegroundStatusForFledgeReportImpression() {
            return true;
        }

        @Override
        public boolean getEnforceForegroundStatusForFledgeOverrides() {
            return true;
        }

        @Override
        public long getReportImpressionOverallTimeoutMs() {
            return 500;
        }

        @Override
        public boolean getEnforceIsolateMaxHeapSize() {
            return false;
        }

        @Override
        public boolean getGaUxFeatureEnabled() {
            return mIsGaUxEnabled;
        }

        @Override
        public float getSdkRequestPermitsPerSecond() {
            // Unlimited rate for unit tests to avoid flake in tests due to rate limiting
            return -1;
        }
    }
}
