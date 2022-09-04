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

package com.android.adservices.service.common;

import static com.android.adservices.service.adselection.ImpressionReporter.CALLER_PACKAGE_NAME_MISMATCH;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_REPORT_IMPRESSIONS;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_SELECT_ADS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyBoolean;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;

import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionInput;
import android.adservices.adselection.AdSelectionOverrideCallback;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.adselection.ReportImpressionCallback;
import android.adservices.adselection.ReportImpressionInput;
import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.CustomAudienceOverrideCallback;
import android.adservices.customaudience.ICustomAudienceCallback;
import android.adservices.customaudience.TrustedBiddingData;
import android.adservices.customaudience.TrustedBiddingDataFixture;
import android.adservices.http.MockWebServerRule;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.AdSelectionServiceImpl;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.customaudience.BackgroundFetchJobService;
import com.android.adservices.service.customaudience.CustomAudienceImpl;
import com.android.adservices.service.customaudience.CustomAudienceQuantityChecker;
import com.android.adservices.service.customaudience.CustomAudienceServiceImpl;
import com.android.adservices.service.customaudience.CustomAudienceValidator;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

public class FledgeE2ETest {
    public static final String CUSTOM_AUDIENCE_SEQ_1 = "/ca1";
    public static final String CUSTOM_AUDIENCE_SEQ_2 = "/ca2";
    @Spy private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final Uri BUYER_DOMAIN_1 =
            CommonFixture.getUri(AdSelectionConfigFixture.BUYER_1, "");
    private static final Uri BUYER_DOMAIN_2 =
            CommonFixture.getUri(AdSelectionConfigFixture.BUYER_2, "");
    private static final String AD_URI_PREFIX = "/adverts/123";
    private static final String BUYER_BIDDING_LOGIC_URI_PATH = "/buyer/bidding/logic/";
    private static final String BUYER_TRUSTED_SIGNAL_URI_PATH = "/kv/buyer/signals/";
    private static final String BUYER_TRUSTED_SIGNAL_PARAMS =
            "?keys=example%2Cvalid%2Clist%2Cof%2Ckeys";
    private static final String SELLER_DECISION_LOGIC_URI_PATH = "/ssp/decision/logic/";
    private static final String SELLER_TRUSTED_SIGNAL_URI_PATH = "/kv/seller/signals/";
    private static final String SELLER_TRUSTED_SIGNAL_PARAMS = "?renderurls=";
    private static final String SELLER_REPORTING_PATH = "/reporting/seller";
    private static final String BUYER_REPORTING_PATH = "/reporting/buyer";
    private static final AdSelectionSignals TRUSTED_BIDDING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"example\": \"example\",\n"
                            + "\t\"valid\": \"Also valid\",\n"
                            + "\t\"list\": \"list\",\n"
                            + "\t\"of\": \"of\",\n"
                            + "\t\"keys\": \"trusted bidding signal Values\"\n"
                            + "}");
    private static final AdSelectionSignals TRUSTED_SCORING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"render_url_1\": \"signals_for_1\",\n"
                            + "\t\"render_url_2\": \"signals_for_2\"\n"
                            + "}");
    private static final List<Double> BIDS_FOR_BUYER_1 = ImmutableList.of(1.1, 2.2);
    private static final List<Double> BIDS_FOR_BUYER_2 = ImmutableList.of(4.5, 6.7, 10.0);
    private static final List<Double> INVALID_BIDS = ImmutableList.of(0.0, -1.0, -2.0);
    private final AdServicesLogger mAdServicesLogger = AdServicesLoggerImpl.getInstance();
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    @Mock private ConsentManager mConsentManagerMock;
    private MockitoSession mStaticMockSession = null;
    // This object access some system APIs
    @Mock private DevContextFilter mDevContextFilter;
    @Mock private AppImportanceFilter mAppImportanceFilter;
    @Mock private Throttler mMockThrottler;
    private AdSelectionConfig mAdSelectionConfig;
    private AdServicesHttpsClient mAdServicesHttpsClient;
    private CustomAudienceDao mCustomAudienceDao;
    private AdSelectionEntryDao mAdSelectionEntryDao;
    private ExecutorService mLightweightExecutorService;
    private ExecutorService mBackgroundExecutorService;
    private CustomAudienceServiceImpl mCustomAudienceService;
    private AdSelectionServiceImpl mAdSelectionService;

    private final Flags mFlags = new FledgeE2ETestFlags();
    private MockWebServerRule.RequestMatcher<String> mRequestMatcherPrefixMatch;
    private Uri mLocalhostBuyerDomain;
    private Supplier<Throttler> mThrottlerSupplier = () -> mMockThrottler;

    @Spy
    FledgeAllowListsFilter mFledgeAllowListsFilterSpy =
            new FledgeAllowListsFilter(mFlags, mAdServicesLogger);

    @Before
    public void setUp() throws Exception {
        // Test applications don't have the required permissions to read config P/H flags, and
        // injecting mocked flags everywhere is annoying and non-trivial for static methods
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .mockStatic(BackgroundFetchJobService.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();
        doReturn(mFlags).when(FlagsFactory::getFlags);

        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, CustomAudienceDatabase.class)
                        .build()
                        .customAudienceDao();

        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();

        mAdServicesHttpsClient =
                new AdServicesHttpsClient(AdServicesExecutors.getBlockingExecutor());

        mCustomAudienceService =
                new CustomAudienceServiceImpl(
                        CONTEXT,
                        new CustomAudienceImpl(
                                mCustomAudienceDao,
                                new CustomAudienceQuantityChecker(mCustomAudienceDao, mFlags),
                                new CustomAudienceValidator(
                                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI, mFlags),
                                CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                mFlags),
                        FledgeAuthorizationFilter.create(CONTEXT, mAdServicesLogger),
                        mFledgeAllowListsFilterSpy,
                        mConsentManagerMock,
                        mDevContextFilter,
                        MoreExecutors.newDirectExecutorService(),
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        mFlags,
                        mThrottlerSupplier,
                        CallingAppUidSupplierProcessImpl.create());

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        // Create an instance of AdSelection Service with real dependencies
        mAdSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mAdServicesHttpsClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLogger,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        FledgeAuthorizationFilter.create(CONTEXT, mAdServicesLogger),
                        mFledgeAllowListsFilterSpy);

        mRequestMatcherPrefixMatch = (a, b) -> !b.isEmpty() && a.startsWith(b);

        when(mMockThrottler.tryAcquire(eq(FLEDGE_API_SELECT_ADS), anyString())).thenReturn(true);
        when(mMockThrottler.tryAcquire(eq(FLEDGE_API_REPORT_IMPRESSIONS), anyString()))
                .thenReturn(true);
        when(mMockThrottler.tryAcquire(eq(FLEDGE_API_JOIN_CUSTOM_AUDIENCE), anyString()))
                .thenReturn(true);
        when(mMockThrottler.tryAcquire(eq(FLEDGE_API_LEAVE_CUSTOM_AUDIENCE), anyString()))
                .thenReturn(true);
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testFledgeFlowSuccessWithDevOverrides() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent(any());
        doReturn(false).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any(), any());
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any(), any());

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(
                                ImmutableList.of(
                                        AdTechIdentifier.fromString(BUYER_DOMAIN_1.getHost()),
                                        AdTechIdentifier.fromString(BUYER_DOMAIN_2.getHost())))
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        String decisionLogicJs =
                "function scoreAd(ad, bid, auction_config, seller_signals,"
                        + " trusted_scoring_signals, contextual_signal, user_signal,"
                        + " custom_audience_signal) { \n"
                        + "  return {'status': 0, 'score': bid };\n"
                        + "}\n"
                        + "function reportResult(ad_selection_config, render_url, bid,"
                        + " contextual_signals) { \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_url': '"
                        + SELLER_REPORTING_PATH
                        + "' } };\n"
                        + "}";
        String biddingLogicJs =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals, contextual_signals, user_signals,"
                        + " custom_audience_signals) { \n"
                        + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}\n"
                        + "function reportWin(ad_selection_signals, per_buyer_signals,"
                        + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_url': '"
                        + BUYER_REPORTING_PATH
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        request -> new MockResponse().setResponseCode(404));

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(CommonFixture.TEST_PACKAGE_NAME)
                                .build());

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        CustomAudience customAudience1 = createCustomAudience(BUYER_DOMAIN_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_DOMAIN_2, BIDS_FOR_BUYER_2);

        // Join first custom audience
        ResultCapturingCallback joinCallback1 = new ResultCapturingCallback();
        mCustomAudienceService.joinCustomAudience(
                customAudience1, CommonFixture.TEST_PACKAGE_NAME, joinCallback1);
        assertTrue(joinCallback1.isSuccess());

        // Join second custom audience
        ResultCapturingCallback joinCallback2 = new ResultCapturingCallback();
        mCustomAudienceService.joinCustomAudience(
                customAudience2, CommonFixture.TEST_PACKAGE_NAME, joinCallback2);
        assertTrue(joinCallback2.isSuccess());

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        // Add AdSelection Override
        AdSelectionOverrideTestCallback adSelectionOverrideTestCallback =
                callAddAdSelectionOverride(
                        mAdSelectionService,
                        mAdSelectionConfig,
                        decisionLogicJs,
                        TRUSTED_SCORING_SIGNALS);

        assertTrue(adSelectionOverrideTestCallback.mIsSuccess);

        // Add Custom Audience Overrides
        CustomAudienceOverrideTestCallback customAudienceOverrideTestCallback1 =
                callAddCustomAudienceOverride(
                        CommonFixture.TEST_PACKAGE_NAME,
                        customAudience1.getBuyer(),
                        customAudience1.getName(),
                        biddingLogicJs,
                        TRUSTED_BIDDING_SIGNALS,
                        mCustomAudienceService);

        assertTrue(customAudienceOverrideTestCallback1.mIsSuccess);

        CustomAudienceOverrideTestCallback customAudienceOverrideTestCallback2 =
                callAddCustomAudienceOverride(
                        CommonFixture.TEST_PACKAGE_NAME,
                        customAudience2.getBuyer(),
                        customAudience2.getName(),
                        biddingLogicJs,
                        TRUSTED_BIDDING_SIGNALS,
                        mCustomAudienceService);

        assertTrue(customAudienceOverrideTestCallback2.mIsSuccess);

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                CommonFixture.getUri(BUYER_DOMAIN_2.getHost(), AD_URI_PREFIX + "/ad3"),
                resultsCallback.mAdSelectionResponse.getRenderUri());

        // Run Report Impression
        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionConfig(mAdSelectionConfig)
                        .setAdSelectionId(resultsCallback.mAdSelectionResponse.getAdSelectionId())
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback reportImpressionTestCallback =
                callReportImpression(mAdSelectionService, input);

        assertTrue(reportImpressionTestCallback.mIsSuccess);
        mMockWebServerRule.verifyMockServerRequests(
                server, 0, Collections.emptyList(), mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowSuccessWithDevOverridesWithRevokedUserConsentForApp()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent(any());
        // Allow the first calls to succeed so that we can verify the rest of the flow works
        when(mConsentManagerMock.isFledgeConsentRevokedForApp(any(), any()))
                .thenReturn(false)
                .thenReturn(true);
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(any(), any()))
                .thenReturn(false)
                .thenReturn(true);

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(
                                ImmutableList.of(
                                        AdTechIdentifier.fromString(BUYER_DOMAIN_1.getHost()),
                                        AdTechIdentifier.fromString(BUYER_DOMAIN_2.getHost())))
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        String decisionLogicJs =
                "function scoreAd(ad, bid, auction_config, seller_signals,"
                        + " trusted_scoring_signals, contextual_signal, user_signal,"
                        + " custom_audience_signal) { \n"
                        + "  return {'status': 0, 'score': bid };\n"
                        + "}\n"
                        + "function reportResult(ad_selection_config, render_url, bid,"
                        + " contextual_signals) { \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_url': '"
                        + SELLER_REPORTING_PATH
                        + "' } };\n"
                        + "}";
        String biddingLogicJs =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals, contextual_signals, user_signals,"
                        + " custom_audience_signals) { \n"
                        + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}\n"
                        + "function reportWin(ad_selection_signals, per_buyer_signals,"
                        + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_url': '"
                        + BUYER_REPORTING_PATH
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        request -> new MockResponse().setResponseCode(404));

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(CommonFixture.TEST_PACKAGE_NAME)
                                .build());

        CustomAudience customAudience1 = createCustomAudience(BUYER_DOMAIN_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_DOMAIN_2, BIDS_FOR_BUYER_2);

        // Join first custom audience
        ResultCapturingCallback joinCallback1 = new ResultCapturingCallback();
        mCustomAudienceService.joinCustomAudience(
                customAudience1, CommonFixture.TEST_PACKAGE_NAME, joinCallback1);
        assertTrue(joinCallback1.isSuccess());

        // Join second custom audience
        ResultCapturingCallback joinCallback2 = new ResultCapturingCallback();
        mCustomAudienceService.joinCustomAudience(
                customAudience2, CommonFixture.TEST_PACKAGE_NAME, joinCallback2);
        assertTrue(joinCallback2.isSuccess());

        // Add AdSelection Override
        AdSelectionOverrideTestCallback adSelectionOverrideTestCallback =
                callAddAdSelectionOverride(
                        mAdSelectionService,
                        mAdSelectionConfig,
                        decisionLogicJs,
                        TRUSTED_SCORING_SIGNALS);

        assertTrue(adSelectionOverrideTestCallback.mIsSuccess);

        // Add Custom Audience Overrides
        CustomAudienceOverrideTestCallback customAudienceOverrideTestCallback1 =
                callAddCustomAudienceOverride(
                        CommonFixture.TEST_PACKAGE_NAME,
                        customAudience1.getBuyer(),
                        customAudience1.getName(),
                        biddingLogicJs,
                        TRUSTED_BIDDING_SIGNALS,
                        mCustomAudienceService);

        assertTrue(customAudienceOverrideTestCallback1.mIsSuccess);

        CustomAudienceOverrideTestCallback customAudienceOverrideTestCallback2 =
                callAddCustomAudienceOverride(
                        CommonFixture.TEST_PACKAGE_NAME,
                        customAudience2.getBuyer(),
                        customAudience2.getName(),
                        biddingLogicJs,
                        TRUSTED_BIDDING_SIGNALS,
                        mCustomAudienceService);

        assertTrue(customAudienceOverrideTestCallback2.mIsSuccess);

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        // Verify that CA1/ad2 won because CA2 was not joined due to user consent
        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                CommonFixture.getUri(BUYER_DOMAIN_1.getHost(), AD_URI_PREFIX + "/ad2"),
                resultsCallback.mAdSelectionResponse.getRenderUri());

        // Run Report Impression
        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionConfig(mAdSelectionConfig)
                        .setAdSelectionId(resultsCallback.mAdSelectionResponse.getAdSelectionId())
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback reportImpressionTestCallback =
                callReportImpression(mAdSelectionService, input);

        assertTrue(reportImpressionTestCallback.mIsSuccess);
        mMockWebServerRule.verifyMockServerRequests(
                server, 0, Collections.emptyList(), mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowFailsWithMismatchedPackageNames() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent(any());
        doReturn(false).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any(), any());
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any(), any());

        String otherPackageName = CommonFixture.TEST_PACKAGE_NAME + "different_package";

        // Mocking PackageManager so it passes package name validation, but fails in reporting
        // due to package mismatch
        PackageManager packageManagerMock = mock(PackageManager.class);
        when(CONTEXT.getPackageManager()).thenReturn(packageManagerMock);
        when(packageManagerMock.getPackagesForUid(Process.myUid()))
                .thenReturn(new String[] {CommonFixture.TEST_PACKAGE_NAME, otherPackageName});

        // Reinitializing service so mocking takes effect
        // Create an instance of AdSelection Service with real dependencies
        mAdSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mAdServicesHttpsClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        CONTEXT,
                        mConsentManagerMock,
                        mAdServicesLogger,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        FledgeAuthorizationFilter.create(CONTEXT, mAdServicesLogger),
                        mFledgeAllowListsFilterSpy);

        // Stubbing this check so it fails at matching validation
        doNothing()
                .when(mFledgeAllowListsFilterSpy)
                .assertAppCanUsePpapi(
                        otherPackageName, AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION);

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(
                                ImmutableList.of(
                                        AdTechIdentifier.fromString(BUYER_DOMAIN_1.getHost()),
                                        AdTechIdentifier.fromString(BUYER_DOMAIN_2.getHost())))
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        AdSelectionConfig adSelectionConfigWithDifferentCallerPackageName =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(
                                ImmutableList.of(
                                        AdTechIdentifier.fromString(BUYER_DOMAIN_1.getHost()),
                                        AdTechIdentifier.fromString(BUYER_DOMAIN_2.getHost())))
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        String decisionLogicJs =
                "function scoreAd(ad, bid, auction_config, seller_signals,"
                        + " trusted_scoring_signals, contextual_signal, user_signal,"
                        + " custom_audience_signal) { \n"
                        + "  return {'status': 0, 'score': bid };\n"
                        + "}\n"
                        + "function reportResult(ad_selection_config, render_url, bid,"
                        + " contextual_signals) { \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_url': '"
                        + SELLER_REPORTING_PATH
                        + "' } };\n"
                        + "}";
        String biddingLogicJs =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals, contextual_signals, user_signals,"
                        + " custom_audience_signals) { \n"
                        + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}\n"
                        + "function reportWin(ad_selection_signals, per_buyer_signals,"
                        + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_url': '"
                        + BUYER_REPORTING_PATH
                        + "' } };\n"
                        + "}";

        mMockWebServerRule.startMockWebServer(request -> new MockResponse().setResponseCode(404));

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(CommonFixture.TEST_PACKAGE_NAME)
                                .build());

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        CustomAudience customAudience1 = createCustomAudience(BUYER_DOMAIN_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_DOMAIN_2, BIDS_FOR_BUYER_2);

        // Join first custom audience
        ResultCapturingCallback joinCallback1 = new ResultCapturingCallback();
        mCustomAudienceService.joinCustomAudience(
                customAudience1, CommonFixture.TEST_PACKAGE_NAME, joinCallback1);
        assertTrue(joinCallback1.isSuccess());

        // Join second custom audience
        ResultCapturingCallback joinCallback2 = new ResultCapturingCallback();
        mCustomAudienceService.joinCustomAudience(
                customAudience2, CommonFixture.TEST_PACKAGE_NAME, joinCallback2);
        assertTrue(joinCallback2.isSuccess());

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        // Add AdSelection Override
        AdSelectionOverrideTestCallback adSelectionOverrideTestCallback =
                callAddAdSelectionOverride(
                        mAdSelectionService,
                        mAdSelectionConfig,
                        decisionLogicJs,
                        TRUSTED_SCORING_SIGNALS);

        assertTrue(adSelectionOverrideTestCallback.mIsSuccess);

        // Add Custom Audience Overrides
        CustomAudienceOverrideTestCallback customAudienceOverrideTestCallback1 =
                callAddCustomAudienceOverride(
                        CommonFixture.TEST_PACKAGE_NAME,
                        customAudience1.getBuyer(),
                        customAudience1.getName(),
                        biddingLogicJs,
                        TRUSTED_BIDDING_SIGNALS,
                        mCustomAudienceService);

        assertTrue(customAudienceOverrideTestCallback1.mIsSuccess);

        CustomAudienceOverrideTestCallback customAudienceOverrideTestCallback2 =
                callAddCustomAudienceOverride(
                        CommonFixture.TEST_PACKAGE_NAME,
                        customAudience2.getBuyer(),
                        customAudience2.getName(),
                        biddingLogicJs,
                        TRUSTED_BIDDING_SIGNALS,
                        mCustomAudienceService);

        assertTrue(customAudienceOverrideTestCallback2.mIsSuccess);

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                CommonFixture.getUri(BUYER_DOMAIN_2.getHost(), AD_URI_PREFIX + "/ad3"),
                resultsCallback.mAdSelectionResponse.getRenderUri());

        // Run Report Impression with different package name
        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionConfig(adSelectionConfigWithDifferentCallerPackageName)
                        .setAdSelectionId(resultsCallback.mAdSelectionResponse.getAdSelectionId())
                        .setCallerPackageName(otherPackageName)
                        .build();

        ReportImpressionTestCallback reportImpressionTestCallback =
                callReportImpression(mAdSelectionService, input);

        assertFalse(reportImpressionTestCallback.mIsSuccess);
        assertEquals(
                reportImpressionTestCallback.mFledgeErrorResponse.getStatusCode(),
                AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
        assertEquals(
                reportImpressionTestCallback.mFledgeErrorResponse.getErrorMessage(),
                CALLER_PACKAGE_NAME_MISMATCH);
    }

    @Test
    public void testFledgeFlowSuccessWithOneCAWithNegativeBidsWithDevOverrides() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent(any());
        doReturn(false).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any(), any());
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any(), any());

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(
                                ImmutableList.of(
                                        AdTechIdentifier.fromString(BUYER_DOMAIN_1.getHost()),
                                        AdTechIdentifier.fromString(BUYER_DOMAIN_2.getHost())))
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        String decisionLogicJs =
                "function scoreAd(ad, bid, auction_config, seller_signals,"
                        + " trusted_scoring_signals, contextual_signal, user_signal,"
                        + " custom_audience_signal) { \n"
                        + "  return {'status': 0, 'score': bid };\n"
                        + "}\n"
                        + "function reportResult(ad_selection_config, render_url, bid,"
                        + " contextual_signals) { \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_url': '"
                        + SELLER_REPORTING_PATH
                        + "' } };\n"
                        + "}";
        String biddingLogicJs =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals, contextual_signals, user_signals,"
                        + " custom_audience_signals) { \n"
                        + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}\n"
                        + "function reportWin(ad_selection_signals, per_buyer_signals,"
                        + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_url': '"
                        + BUYER_REPORTING_PATH
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        request -> {
                            // With overrides the server should not be called
                            return new MockResponse().setResponseCode(404);
                        });

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(CommonFixture.TEST_PACKAGE_NAME)
                                .build());

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        CustomAudience customAudience1 = createCustomAudience(BUYER_DOMAIN_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_DOMAIN_2, INVALID_BIDS);

        // Join first custom audience
        ResultCapturingCallback joinCallback1 = new ResultCapturingCallback();
        mCustomAudienceService.joinCustomAudience(
                customAudience1, CommonFixture.TEST_PACKAGE_NAME, joinCallback1);
        assertTrue(joinCallback1.isSuccess());

        // Join second custom audience
        ResultCapturingCallback joinCallback2 = new ResultCapturingCallback();
        mCustomAudienceService.joinCustomAudience(
                customAudience2, CommonFixture.TEST_PACKAGE_NAME, joinCallback2);
        assertTrue(joinCallback2.isSuccess());

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        // Add AdSelection Override
        AdSelectionOverrideTestCallback adSelectionOverrideTestCallback =
                callAddAdSelectionOverride(
                        mAdSelectionService,
                        mAdSelectionConfig,
                        decisionLogicJs,
                        TRUSTED_SCORING_SIGNALS);

        assertTrue(adSelectionOverrideTestCallback.mIsSuccess);

        // Add Custom Audience Overrides
        CustomAudienceOverrideTestCallback customAudienceOverrideTestCallback1 =
                callAddCustomAudienceOverride(
                        CommonFixture.TEST_PACKAGE_NAME,
                        customAudience1.getBuyer(),
                        customAudience1.getName(),
                        biddingLogicJs,
                        TRUSTED_BIDDING_SIGNALS,
                        mCustomAudienceService);

        assertTrue(customAudienceOverrideTestCallback1.mIsSuccess);

        CustomAudienceOverrideTestCallback customAudienceOverrideTestCallback2 =
                callAddCustomAudienceOverride(
                        CommonFixture.TEST_PACKAGE_NAME,
                        customAudience2.getBuyer(),
                        customAudience2.getName(),
                        biddingLogicJs,
                        TRUSTED_BIDDING_SIGNALS,
                        mCustomAudienceService);

        assertTrue(customAudienceOverrideTestCallback2.mIsSuccess);

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        if (!resultsCallback.mIsSuccess) {
            throw new RuntimeException(resultsCallback.mFledgeErrorResponse.getErrorMessage());
        }

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        // Expect that ad from buyer 1 won since buyer 2 had negative bids
        assertEquals(
                CommonFixture.getUri(BUYER_DOMAIN_1.getAuthority(), AD_URI_PREFIX + "/ad2"),
                resultsCallback.mAdSelectionResponse.getRenderUri());

        // Run Report Impression
        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionConfig(mAdSelectionConfig)
                        .setAdSelectionId(resultsCallback.mAdSelectionResponse.getAdSelectionId())
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback reportImpressionTestCallback =
                callReportImpression(mAdSelectionService, input);

        assertTrue(reportImpressionTestCallback.mIsSuccess);
        mMockWebServerRule.verifyMockServerRequests(
                server, 0, Collections.emptyList(), mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowFailsWithBothCANegativeBidsWithDevOverrides() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent(any());
        doReturn(false).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any(), any());
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any(), any());

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(
                                ImmutableList.of(
                                        AdTechIdentifier.fromString(BUYER_DOMAIN_1.getHost()),
                                        AdTechIdentifier.fromString(BUYER_DOMAIN_2.getHost())))
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        String decisionLogicJs =
                "function scoreAd(ad, bid, auction_config, seller_signals,"
                        + " trusted_scoring_signals, contextual_signal, user_signal,"
                        + " custom_audience_signal) { \n"
                        + "  return {'status': 0, 'score': bid };\n"
                        + "}\n"
                        + "function reportResult(ad_selection_config, render_url, bid,"
                        + " contextual_signals) { \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_url': '"
                        + SELLER_REPORTING_PATH
                        + "' } };\n"
                        + "}";
        String biddingLogicJs =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals, contextual_signals, user_signals,"
                        + " custom_audience_signals) { \n"
                        + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}\n"
                        + "function reportWin(ad_selection_signals, per_buyer_signals,"
                        + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_url': '"
                        + BUYER_REPORTING_PATH
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        request -> {
                            // with overrides the server should not be invoked
                            return new MockResponse().setResponseCode(404);
                        });

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(CommonFixture.TEST_PACKAGE_NAME)
                                .build());

        CustomAudience customAudience1 = createCustomAudience(BUYER_DOMAIN_1, INVALID_BIDS);

        CustomAudience customAudience2 = createCustomAudience(BUYER_DOMAIN_2, INVALID_BIDS);

        // Join first custom audience
        ResultCapturingCallback joinCallback1 = new ResultCapturingCallback();
        mCustomAudienceService.joinCustomAudience(
                customAudience1, CommonFixture.TEST_PACKAGE_NAME, joinCallback1);
        assertTrue(joinCallback1.isSuccess());

        // Join second custom audience
        ResultCapturingCallback joinCallback2 = new ResultCapturingCallback();
        mCustomAudienceService.joinCustomAudience(
                customAudience2, CommonFixture.TEST_PACKAGE_NAME, joinCallback2);
        assertTrue(joinCallback2.isSuccess());

        // Add AdSelection Override
        AdSelectionOverrideTestCallback adSelectionOverrideTestCallback =
                callAddAdSelectionOverride(
                        mAdSelectionService,
                        mAdSelectionConfig,
                        decisionLogicJs,
                        TRUSTED_SCORING_SIGNALS);

        assertTrue(adSelectionOverrideTestCallback.mIsSuccess);

        // Add Custom Audience Overrides
        CustomAudienceOverrideTestCallback customAudienceOverrideTestCallback1 =
                callAddCustomAudienceOverride(
                        CommonFixture.TEST_PACKAGE_NAME,
                        customAudience1.getBuyer(),
                        customAudience1.getName(),
                        biddingLogicJs,
                        AdSelectionSignals.EMPTY,
                        mCustomAudienceService);

        assertTrue(customAudienceOverrideTestCallback1.mIsSuccess);

        CustomAudienceOverrideTestCallback customAudienceOverrideTestCallback2 =
                callAddCustomAudienceOverride(
                        CommonFixture.TEST_PACKAGE_NAME,
                        customAudience2.getBuyer(),
                        customAudience2.getName(),
                        biddingLogicJs,
                        AdSelectionSignals.EMPTY,
                        mCustomAudienceService);

        assertTrue(customAudienceOverrideTestCallback2.mIsSuccess);

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        // Assert that ad selection fails since both Custom Audiences have invalid bids
        assertFalse(resultsCallback.mIsSuccess);
        assertNull(resultsCallback.mAdSelectionResponse);

        // Run Report Impression with random ad selection id
        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionConfig(mAdSelectionConfig)
                        .setAdSelectionId(1)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback reportImpressionTestCallback =
                callReportImpression(mAdSelectionService, input);

        assertFalse(reportImpressionTestCallback.mIsSuccess);
        mMockWebServerRule.verifyMockServerRequests(
                server, 0, Collections.emptyList(), mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowSuccessWithMockServer() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent(any());
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any(), any());

        Uri sellerReportingUrl = mMockWebServerRule.uriForPath(SELLER_REPORTING_PATH);
        Uri buyerReportingUrl = mMockWebServerRule.uriForPath(BUYER_REPORTING_PATH);
        mLocalhostBuyerDomain = Uri.parse(mMockWebServerRule.getServerBaseAddress());

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(
                                ImmutableList.of(
                                        AdTechIdentifier.fromString(
                                                mLocalhostBuyerDomain.getHost())))
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .setPerBuyerSignals(
                                ImmutableMap.of(
                                        AdTechIdentifier.fromString(
                                                mLocalhostBuyerDomain.getHost()),
                                        AdSelectionSignals.fromString("{\"buyer_signals\":0}")))
                        .build();

        String decisionLogicJs =
                "function scoreAd(ad, bid, auction_config, seller_signals,"
                        + " trusted_scoring_signals, contextual_signal, user_signal,"
                        + " custom_audience_signal) { \n"
                        + "  return {'status': 0, 'score': bid };\n"
                        + "}\n"
                        + "function reportResult(ad_selection_config, render_url, bid,"
                        + " contextual_signals) { \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_url': '"
                        + sellerReportingUrl
                        + "' } };\n"
                        + "}";
        String biddingLogicJs =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals, contextual_signals, user_signals,"
                        + " custom_audience_signals) { \n"
                        + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}\n"
                        + "function reportWin(ad_selection_signals, per_buyer_signals,"
                        + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_url': '"
                        + buyerReportingUrl
                        + "' } };\n"
                        + "}";

        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_2, BIDS_FOR_BUYER_2);

        // Reporting should ping twice (once each for buyer/seller)
        CountDownLatch reportingResponseLatch = new CountDownLatch(2);

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        request -> {
                            switch (request.getPath()) {
                                case SELLER_DECISION_LOGIC_URI_PATH:
                                    return new MockResponse().setBody(decisionLogicJs);
                                case BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1:
                                case BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_2:
                                    return new MockResponse().setBody(biddingLogicJs);
                                case BUYER_TRUSTED_SIGNAL_URI_PATH + BUYER_TRUSTED_SIGNAL_PARAMS:
                                    return new MockResponse()
                                            .setBody(TRUSTED_BIDDING_SIGNALS.toString());
                                case SELLER_REPORTING_PATH: // Intentional fallthrough
                                case BUYER_REPORTING_PATH:
                                    reportingResponseLatch.countDown();
                                    return new MockResponse().setResponseCode(200);
                            }

                            // The seller params vary based on runtime, so we are returning trusted
                            // signals based on correct path prefix
                            if (request.getPath()
                                    .startsWith(
                                            SELLER_TRUSTED_SIGNAL_URI_PATH
                                                    + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                                return new MockResponse()
                                        .setBody(TRUSTED_SCORING_SIGNALS.toString());
                            }
                            return new MockResponse().setResponseCode(404);
                        });

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        // Join first custom audience
        ResultCapturingCallback joinCallback1 = new ResultCapturingCallback();
        mCustomAudienceService.joinCustomAudience(
                customAudience1, CommonFixture.TEST_PACKAGE_NAME, joinCallback1);
        assertTrue(joinCallback1.isSuccess());

        // Join second custom audience
        ResultCapturingCallback joinCallback2 = new ResultCapturingCallback();
        mCustomAudienceService.joinCustomAudience(
                customAudience2, CommonFixture.TEST_PACKAGE_NAME, joinCallback2);
        assertTrue(joinCallback2.isSuccess());

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad3"),
                resultsCallback.mAdSelectionResponse.getRenderUri());

        // Run Report Impression
        ReportImpressionInput reportImpressioninput =
                new ReportImpressionInput.Builder()
                        .setAdSelectionConfig(mAdSelectionConfig)
                        .setAdSelectionId(resultSelectionId)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback reportImpressionTestCallback =
                callReportImpression(mAdSelectionService, reportImpressioninput);

        assertTrue(reportImpressionTestCallback.mIsSuccess);
        reportingResponseLatch.await();
        mMockWebServerRule.verifyMockServerRequests(
                server,
                9,
                ImmutableList.of(
                        SELLER_DECISION_LOGIC_URI_PATH,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH + BUYER_TRUSTED_SIGNAL_PARAMS,
                        SELLER_TRUSTED_SIGNAL_URI_PATH + SELLER_TRUSTED_SIGNAL_PARAMS),
                mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowSuccessWithRevokedUserConsentForApp() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent(any());
        // Allow the first join call to succeed so that we can verify the rest of the flow works
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(any(), any()))
                .thenReturn(false)
                .thenReturn(true);

        Uri sellerReportingUrl = mMockWebServerRule.uriForPath(SELLER_REPORTING_PATH);
        Uri buyerReportingUrl = mMockWebServerRule.uriForPath(BUYER_REPORTING_PATH);
        mLocalhostBuyerDomain = Uri.parse(mMockWebServerRule.getServerBaseAddress());

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(
                                ImmutableList.of(
                                        AdTechIdentifier.fromString(
                                                mLocalhostBuyerDomain.getHost())))
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .setPerBuyerSignals(
                                ImmutableMap.of(
                                        AdTechIdentifier.fromString(
                                                mLocalhostBuyerDomain.getHost()),
                                        AdSelectionSignals.fromString("{\"buyer_signals\":0}")))
                        .build();

        String decisionLogicJs =
                "function scoreAd(ad, bid, auction_config, seller_signals,"
                        + " trusted_scoring_signals, contextual_signal, user_signal,"
                        + " custom_audience_signal) { \n"
                        + "  return {'status': 0, 'score': bid };\n"
                        + "}\n"
                        + "function reportResult(ad_selection_config, render_url, bid,"
                        + " contextual_signals) { \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_url': '"
                        + sellerReportingUrl
                        + "' } };\n"
                        + "}";
        String biddingLogicJs =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals, contextual_signals, user_signals,"
                        + " custom_audience_signals) { \n"
                        + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}\n"
                        + "function reportWin(ad_selection_signals, per_buyer_signals,"
                        + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_url': '"
                        + buyerReportingUrl
                        + "' } };\n"
                        + "}";

        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_2, BIDS_FOR_BUYER_2);

        // Reporting should ping twice (once each for buyer/seller)
        CountDownLatch reportingResponseLatch = new CountDownLatch(2);

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        request -> {
                            switch (request.getPath()) {
                                case SELLER_DECISION_LOGIC_URI_PATH:
                                    return new MockResponse().setBody(decisionLogicJs);
                                case BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1:
                                    return new MockResponse().setBody(biddingLogicJs);
                                case BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_2:
                                    throw new IllegalStateException(
                                            "This should not be called without user consent");
                                case BUYER_TRUSTED_SIGNAL_URI_PATH + BUYER_TRUSTED_SIGNAL_PARAMS:
                                    return new MockResponse()
                                            .setBody(TRUSTED_BIDDING_SIGNALS.toString());
                                case SELLER_REPORTING_PATH: // Intentional fallthrough
                                case BUYER_REPORTING_PATH:
                                    reportingResponseLatch.countDown();
                                    return new MockResponse().setResponseCode(200);
                            }

                            // The seller params vary based on runtime, so we are returning trusted
                            // signals based on correct path prefix
                            if (request.getPath()
                                    .startsWith(
                                            SELLER_TRUSTED_SIGNAL_URI_PATH
                                                    + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                                return new MockResponse()
                                        .setBody(TRUSTED_SCORING_SIGNALS.toString());
                            }
                            return new MockResponse().setResponseCode(404);
                        });

        // Join first custom audience
        ResultCapturingCallback joinCallback1 = new ResultCapturingCallback();
        mCustomAudienceService.joinCustomAudience(
                customAudience1, CommonFixture.TEST_PACKAGE_NAME, joinCallback1);
        assertTrue(joinCallback1.isSuccess());

        // Join second custom audience
        ResultCapturingCallback joinCallback2 = new ResultCapturingCallback();
        mCustomAudienceService.joinCustomAudience(
                customAudience2, CommonFixture.TEST_PACKAGE_NAME, joinCallback2);
        assertTrue(joinCallback2.isSuccess());

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        // Verify that CA1/ad2 won because CA2 was not joined due to user consent
        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_1 + "/ad2"),
                resultsCallback.mAdSelectionResponse.getRenderUri());

        // Run Report Impression
        ReportImpressionInput reportImpressioninput =
                new ReportImpressionInput.Builder()
                        .setAdSelectionConfig(mAdSelectionConfig)
                        .setAdSelectionId(resultSelectionId)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback reportImpressionTestCallback =
                callReportImpression(mAdSelectionService, reportImpressioninput);

        assertTrue(reportImpressionTestCallback.mIsSuccess);
        reportingResponseLatch.await();
        mMockWebServerRule.verifyMockServerRequests(
                server,
                7,
                ImmutableList.of(
                        SELLER_DECISION_LOGIC_URI_PATH,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH + BUYER_TRUSTED_SIGNAL_PARAMS,
                        SELLER_TRUSTED_SIGNAL_URI_PATH + SELLER_TRUSTED_SIGNAL_PARAMS),
                mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowSuccessWithRevokedUserConsentForFledge() throws Exception {
        doReturn(AdServicesApiConsent.REVOKED).when(mConsentManagerMock).getConsent(any());
        doReturn(true)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any(), any());

        mLocalhostBuyerDomain = Uri.parse(mMockWebServerRule.getServerBaseAddress());

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(
                                ImmutableList.of(
                                        AdTechIdentifier.fromString(
                                                mLocalhostBuyerDomain.getHost())))
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .setPerBuyerSignals(
                                ImmutableMap.of(
                                        AdTechIdentifier.fromString(
                                                mLocalhostBuyerDomain.getHost()),
                                        AdSelectionSignals.fromString("{\"buyer_signals\":0}")))
                        .build();

        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_2, BIDS_FOR_BUYER_2);

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        request -> {
                            throw new IllegalStateException(
                                    "No calls should be made without user consent");
                        });

        // Join first custom audience
        ResultCapturingCallback joinCallback1 = new ResultCapturingCallback();
        mCustomAudienceService.joinCustomAudience(
                customAudience1, CommonFixture.TEST_PACKAGE_NAME, joinCallback1);
        assertTrue(joinCallback1.isSuccess());

        // Join second custom audience
        ResultCapturingCallback joinCallback2 = new ResultCapturingCallback();
        mCustomAudienceService.joinCustomAudience(
                customAudience2, CommonFixture.TEST_PACKAGE_NAME, joinCallback2);
        assertTrue(joinCallback2.isSuccess());

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertFalse(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(Uri.EMPTY, resultsCallback.mAdSelectionResponse.getRenderUri());

        // Run Report Impression
        ReportImpressionInput reportImpressioninput =
                new ReportImpressionInput.Builder()
                        .setAdSelectionConfig(mAdSelectionConfig)
                        .setAdSelectionId(resultSelectionId)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback reportImpressionTestCallback =
                callReportImpression(mAdSelectionService, reportImpressioninput);

        assertTrue(reportImpressionTestCallback.mIsSuccess);
        mMockWebServerRule.verifyMockServerRequests(
                server, 0, ImmutableList.of(), mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowSuccessWithOneCAWithNegativeBidsWithMockServer() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent(any());
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any(), any());

        Uri sellerReportingUrl = mMockWebServerRule.uriForPath(SELLER_REPORTING_PATH);
        Uri buyerReportingUrl = mMockWebServerRule.uriForPath(BUYER_REPORTING_PATH);
        mLocalhostBuyerDomain = Uri.parse(mMockWebServerRule.getServerBaseAddress());

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(
                                ImmutableList.of(
                                        AdTechIdentifier.fromString(
                                                mLocalhostBuyerDomain.getHost())))
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .setPerBuyerSignals(
                                ImmutableMap.of(
                                        AdTechIdentifier.fromString(
                                                mLocalhostBuyerDomain.getHost()),
                                        AdSelectionSignals.fromString("{\"buyer_signals\":0}")))
                        .build();

        String decisionLogicJs =
                "function scoreAd(ad, bid, auction_config, seller_signals,"
                        + " trusted_scoring_signals, contextual_signal, user_signal,"
                        + " custom_audience_signal) { \n"
                        + "  return {'status': 0, 'score': bid };\n"
                        + "}\n"
                        + "function reportResult(ad_selection_config, render_url, bid,"
                        + " contextual_signals) { \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_url': '"
                        + sellerReportingUrl
                        + "' } };\n"
                        + "}";
        String biddingLogicJs =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals, contextual_signals, user_signals,"
                        + " custom_audience_signals) { \n"
                        + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}\n"
                        + "function reportWin(ad_selection_signals, per_buyer_signals,"
                        + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_url': '"
                        + buyerReportingUrl
                        + "' } };\n"
                        + "}";

        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 =
                createCustomAudience(mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_2, INVALID_BIDS);

        // Reporting should ping twice (once each for buyer/seller)
        CountDownLatch reportingResponseLatch = new CountDownLatch(2);

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        request -> {
                            switch (request.getPath()) {
                                case SELLER_DECISION_LOGIC_URI_PATH:
                                    return new MockResponse().setBody(decisionLogicJs);
                                case BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1:
                                case BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_2:
                                    return new MockResponse().setBody(biddingLogicJs);
                                case BUYER_TRUSTED_SIGNAL_URI_PATH + BUYER_TRUSTED_SIGNAL_PARAMS:
                                    return new MockResponse()
                                            .setBody(TRUSTED_BIDDING_SIGNALS.toString());
                                case SELLER_REPORTING_PATH: // Intentional fallthrough
                                case BUYER_REPORTING_PATH:
                                    reportingResponseLatch.countDown();
                                    return new MockResponse().setResponseCode(200);
                            }

                            // The seller params vary based on runtime, so we are returning trusted
                            // signals based on correct path prefix
                            if (request.getPath()
                                    .startsWith(
                                            SELLER_TRUSTED_SIGNAL_URI_PATH
                                                    + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                                return new MockResponse()
                                        .setBody(TRUSTED_SCORING_SIGNALS.toString());
                            }
                            return new MockResponse().setResponseCode(404);
                        });

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        // Join first custom audience
        ResultCapturingCallback joinCallback1 = new ResultCapturingCallback();
        mCustomAudienceService.joinCustomAudience(
                customAudience1, CommonFixture.TEST_PACKAGE_NAME, joinCallback1);
        assertTrue(joinCallback1.isSuccess());

        // Join second custom audience
        ResultCapturingCallback joinCallback2 = new ResultCapturingCallback();
        mCustomAudienceService.joinCustomAudience(
                customAudience2, CommonFixture.TEST_PACKAGE_NAME, joinCallback2);
        assertTrue(joinCallback2.isSuccess());

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));

        // Expect that ad from buyer 1 won since buyer 2 had negative bids
        assertEquals(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_1 + "/ad2"),
                resultsCallback.mAdSelectionResponse.getRenderUri());

        // Run Report Impression
        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionConfig(mAdSelectionConfig)
                        .setAdSelectionId(resultsCallback.mAdSelectionResponse.getAdSelectionId())
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback reportImpressionTestCallback =
                callReportImpression(mAdSelectionService, input);

        assertTrue(reportImpressionTestCallback.mIsSuccess);
        reportingResponseLatch.await();
        mMockWebServerRule.verifyMockServerRequests(
                server,
                9,
                ImmutableList.of(
                        SELLER_DECISION_LOGIC_URI_PATH,
                        BUYER_BIDDING_LOGIC_URI_PATH,
                        BUYER_TRUSTED_SIGNAL_URI_PATH + BUYER_TRUSTED_SIGNAL_PARAMS,
                        SELLER_TRUSTED_SIGNAL_URI_PATH + SELLER_TRUSTED_SIGNAL_PARAMS),
                mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowFailsWithOnlyCANegativeBidsWithMockServer() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent(any());
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any(), any());

        Uri sellerReportingUrl = mMockWebServerRule.uriForPath(SELLER_REPORTING_PATH);
        Uri buyerReportingUrl = mMockWebServerRule.uriForPath(BUYER_REPORTING_PATH);
        mLocalhostBuyerDomain = Uri.parse(mMockWebServerRule.getServerBaseAddress());

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(
                                ImmutableList.of(
                                        AdTechIdentifier.fromString(
                                                mLocalhostBuyerDomain.getHost())))
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .setPerBuyerSignals(
                                ImmutableMap.of(
                                        AdTechIdentifier.fromString(
                                                mLocalhostBuyerDomain.getHost()),
                                        AdSelectionSignals.fromString("{\"buyer_signals\":0}")))
                        .build();

        String decisionLogicJs =
                "function scoreAd(ad, bid, auction_config, seller_signals,"
                        + " trusted_scoring_signals, contextual_signal, user_signal,"
                        + " custom_audience_signal) { \n"
                        + "  return {'status': 0, 'score': bid };\n"
                        + "}\n"
                        + "function reportResult(ad_selection_config, render_url, bid,"
                        + " contextual_signals) { \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_url': '"
                        + sellerReportingUrl
                        + "' } };\n"
                        + "}";
        String biddingLogicJs =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals, contextual_signals, user_signals,"
                        + " custom_audience_signals) { \n"
                        + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}\n"
                        + "function reportWin(ad_selection_signals, per_buyer_signals,"
                        + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_url': '"
                        + buyerReportingUrl
                        + "' } };\n"
                        + "}";

        CustomAudience customAudience1 = createCustomAudience(mLocalhostBuyerDomain, INVALID_BIDS);

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        request -> {
                            switch (request.getPath()) {
                                case SELLER_DECISION_LOGIC_URI_PATH:
                                    return new MockResponse().setBody(decisionLogicJs);
                                case BUYER_BIDDING_LOGIC_URI_PATH:
                                    return new MockResponse().setBody(biddingLogicJs);
                                case BUYER_TRUSTED_SIGNAL_URI_PATH + BUYER_TRUSTED_SIGNAL_PARAMS:
                                    return new MockResponse()
                                            .setBody(TRUSTED_BIDDING_SIGNALS.toString());
                            }

                            // The seller params vary based on runtime, so we are returning trusted
                            // signals based on correct path prefix
                            if (request.getPath()
                                    .startsWith(
                                            SELLER_TRUSTED_SIGNAL_URI_PATH
                                                    + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                                return new MockResponse()
                                        .setBody(TRUSTED_SCORING_SIGNALS.toString());
                            }
                            return new MockResponse().setResponseCode(404);
                        });

        // Join custom audience
        ResultCapturingCallback joinCallback1 = new ResultCapturingCallback();
        mCustomAudienceService.joinCustomAudience(
                customAudience1, CommonFixture.TEST_PACKAGE_NAME, joinCallback1);
        assertTrue(joinCallback1.isSuccess());

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertFalse(resultsCallback.mIsSuccess);
        assertNull(resultsCallback.mAdSelectionResponse);

        // Run Report Impression with random ad selection id
        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionConfig(mAdSelectionConfig)
                        .setAdSelectionId(1)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback reportImpressionTestCallback =
                callReportImpression(mAdSelectionService, input);

        assertFalse(reportImpressionTestCallback.mIsSuccess);
        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                ImmutableList.of(
                        BUYER_BIDDING_LOGIC_URI_PATH,
                        BUYER_TRUSTED_SIGNAL_URI_PATH + BUYER_TRUSTED_SIGNAL_PARAMS),
                mRequestMatcherPrefixMatch);
    }

    private AdSelectionTestCallback invokeRunAdSelection(
            AdSelectionServiceImpl adSelectionService,
            AdSelectionConfig adSelectionConfig,
            String callerPackageName)
            throws InterruptedException {

        CountDownLatch countdownLatch = new CountDownLatch(1);
        AdSelectionTestCallback adSelectionTestCallback =
                new AdSelectionTestCallback(countdownLatch);

        AdSelectionInput input =
                new AdSelectionInput.Builder()
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(callerPackageName)
                        .build();

        adSelectionService.runAdSelection(input, adSelectionTestCallback);
        adSelectionTestCallback.mCountDownLatch.await();
        return adSelectionTestCallback;
    }

    private AdSelectionOverrideTestCallback callAddAdSelectionOverride(
            AdSelectionServiceImpl adSelectionService,
            AdSelectionConfig adSelectionConfig,
            String decisionLogicJS,
            AdSelectionSignals trustedScoringSignals)
            throws Exception {
        // Counted down in 1) callback
        CountDownLatch resultLatch = new CountDownLatch(1);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        adSelectionService.overrideAdSelectionConfigRemoteInfo(
                adSelectionConfig, decisionLogicJS, trustedScoringSignals, callback);
        resultLatch.await();
        return callback;
    }

    private CustomAudienceOverrideTestCallback callAddCustomAudienceOverride(
            String owner,
            AdTechIdentifier buyer,
            String name,
            String biddingLogicJs,
            AdSelectionSignals trustedBiddingData,
            CustomAudienceServiceImpl customAudienceService)
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        CustomAudienceOverrideTestCallback callback =
                new CustomAudienceOverrideTestCallback(resultLatch);

        customAudienceService.overrideCustomAudienceRemoteInfo(
                owner, buyer, name, biddingLogicJs, trustedBiddingData, callback);
        resultLatch.await();
        return callback;
    }

    private ReportImpressionTestCallback callReportImpression(
            AdSelectionServiceImpl adSelectionService, ReportImpressionInput requestParams)
            throws Exception {
        // Counted down in 1) callback
        CountDownLatch resultLatch = new CountDownLatch(1);
        ReportImpressionTestCallback callback = new ReportImpressionTestCallback(resultLatch);

        adSelectionService.reportImpression(requestParams, callback);
        resultLatch.await();
        return callback;
    }

    /** See {@link #createCustomAudience(Uri, String, List)}. */
    private CustomAudience createCustomAudience(final Uri buyerDomain, List<Double> bids) {
        return createCustomAudience(buyerDomain, "", bids);
    }

    /**
     * @param buyerDomain The name of the buyer for this Custom Audience
     * @param customAudienceSeq optional numbering for ca name. Should start with slash.
     * @param bids these bids, are added to its metadata. Our JS logic then picks this value and
     *     creates ad with the provided value as bid
     * @return a real Custom Audience object that can be persisted and used in bidding and scoring
     */
    private CustomAudience createCustomAudience(
            final Uri buyerDomain, final String customAudienceSeq, List<Double> bids) {

        // Generate ads for with bids provided
        List<AdData> ads = new ArrayList<>();

        // Create ads with the buyer name and bid number as the ad URL
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            ads.add(
                    new AdData.Builder()
                            .setRenderUri(
                                    CommonFixture.getUri(
                                            buyerDomain.getAuthority(),
                                            AD_URI_PREFIX + customAudienceSeq + "/ad" + (i + 1)))
                            .setMetadata("{\"result\":" + bids.get(i) + "}")
                            .build());
        }

        return new CustomAudience.Builder()
                .setBuyer(AdTechIdentifier.fromString(buyerDomain.getHost()))
                .setName(
                        buyerDomain.getHost()
                                + customAudienceSeq
                                + CustomAudienceFixture.VALID_NAME)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setDailyUpdateUri(
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                AdTechIdentifier.fromString(buyerDomain.getAuthority())))
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        new TrustedBiddingData.Builder()
                                .setTrustedBiddingUri(
                                        CommonFixture.getUri(
                                                buyerDomain.getAuthority(),
                                                BUYER_TRUSTED_SIGNAL_URI_PATH))
                                .setTrustedBiddingKeys(
                                        TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_KEYS)
                                .build())
                .setBiddingLogicUri(
                        CommonFixture.getUri(
                                buyerDomain.getAuthority(),
                                BUYER_BIDDING_LOGIC_URI_PATH + customAudienceSeq))
                .setAds(ads)
                .build();
    }

    private static class ResultCapturingCallback implements ICustomAudienceCallback {
        private boolean mIsSuccess;
        private Exception mException;

        public boolean isSuccess() {
            return mIsSuccess;
        }

        public Exception getException() {
            return mException;
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
        }

        @Override
        public void onFailure(FledgeErrorResponse responseParcel) throws RemoteException {
            mIsSuccess = false;
            mException = AdServicesStatusUtils.asException(responseParcel);
        }

        @Override
        public IBinder asBinder() {
            throw new RuntimeException("Should not be called.");
        }
    }

    static class AdSelectionTestCallback extends AdSelectionCallback.Stub {

        final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        AdSelectionResponse mAdSelectionResponse;
        FledgeErrorResponse mFledgeErrorResponse;

        AdSelectionTestCallback(CountDownLatch countDownLatch) {
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

    public static class CustomAudienceOverrideTestCallback
            extends CustomAudienceOverrideCallback.Stub {
        private final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        public CustomAudienceOverrideTestCallback(CountDownLatch countDownLatch) {
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

    private static class FledgeE2ETestFlags implements Flags {
        @Override
        public long getAdSelectionBiddingTimeoutPerCaMs() {
            return 10000;
        }

        @Override
        public long getAdSelectionScoringTimeoutMs() {
            return 10000;
        }

        @Override
        public long getAdSelectionOverallTimeoutMs() {
            return 300000;
        }

        @Override
        public boolean getEnforceIsolateMaxHeapSize() {
            return false;
        }

        @Override
        public float getSdkRequestPermitsPerSecond() {
            // Unlimited rate for unit tests to avoid flake in tests due to rate limiting
            return -1;
        }
    }
}
