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

import static com.android.adservices.data.adselection.AdSelectionDatabase.DATABASE_NAME;
import static com.android.adservices.service.adselection.AdSelectionFromOutcomesConfigValidator.AD_SELECTION_IDS_DONT_EXIST;
import static com.android.adservices.service.adselection.AdSelectionFromOutcomesConfigValidator.HTTPS_PREFIX;
import static com.android.adservices.service.adselection.AdSelectionFromOutcomesConfigValidator.SELLER_AND_URI_HOST_ARE_INCONSISTENT;
import static com.android.adservices.service.adselection.OutcomeSelectionRunner.SELECTED_OUTCOME_MUST_BE_ONE_OF_THE_INPUTS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.DB_AD_SELECTION_FILE_SIZE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfigFixture;
import android.adservices.adselection.AdSelectionFromOutcomesInput;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallerMetadata;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.http.MockWebServerRule;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;
import android.os.SystemClock;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AdServicesHttpsClient;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

import java.io.File;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class AdSelectionFromOutcomesE2ETest {
    private static final String SELECTION_PICK_HIGHEST_LOGIC_JS_PATH = "/selectionPickHighestJS/";
    private static final String SELECTION_PICK_NONE_LOGIC_JS_PATH = "/selectionPickNoneJS/";
    private static final String SELECTION_WATERFALL_LOGIC_JS_PATH = "/selectionWaterfallJS/";
    private static final String SELECTION_FAULTY_LOGIC_JS_PATH = "/selectionFaultyJS/";
    private static final String SELECTION_PICK_HIGHEST_LOGIC_JS =
            "function selectOutcome(outcomes, selection_signals) {\n"
                    + "    let max_bid = 0;\n"
                    + "    let winner_outcome = null;\n"
                    + "    for (let outcome of outcomes) {\n"
                    + "        if (outcome.bid > max_bid) {\n"
                    + "            max_bid = outcome.bid;\n"
                    + "            winner_outcome = outcome;\n"
                    + "        }\n"
                    + "    }\n"
                    + "    return {'status': 0, 'result': winner_outcome};\n"
                    + "}";
    private static final String SELECTION_PICK_NONE_LOGIC_JS =
            "function selectOutcome(outcomes, selection_signals) {\n"
                    + "    return {'status': 0, 'result': null};\n"
                    + "}";
    private static final String SELECTION_WATERFALL_LOGIC_JS =
            "function selectOutcome(outcomes, selection_signals) {\n"
                    + "    if (outcomes.length != 1 || selection_signals.bid_floor =="
                    + " undefined) return null;\n"
                    + "\n"
                    + "    const outcome_1p = outcomes[0];\n"
                    + "    return {'status': 0, 'result': (outcome_1p.bid >"
                    + " selection_signals.bid_floor) ? outcome_1p : null};\n"
                    + "}";
    private static final String SELECTION_FAULTY_LOGIC_JS =
            "function selectOutcome(outcomes, selection_signals) {\n"
                    + "    return {'status': 0, 'result': {\"id\": outcomes[0].id + 1, \"bid\": "
                    + "outcomes[0].bid}};\n"
                    + "}";
    private static final String BID_FLOOR_SELECTION_SIGNAL_TEMPLATE = "{\"bid_floor\":%s}";

    private static final AdTechIdentifier SELLER_INCONSISTENT_WITH_SELECTION_URI =
            AdTechIdentifier.fromString("inconsistent.developer.android.com");
    private static final long BINDER_ELAPSED_TIME_MS = 100L;
    private static final String CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final long AD_SELECTION_ID_1 = 12345L;
    private static final long AD_SELECTION_ID_2 = 123456L;
    private static final long AD_SELECTION_ID_3 = 1234567L;

    private final AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);
    private final Flags mFlags = new AdSelectionFromOutcomesE2ETest.TestFlags();

    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    // Mocking DevContextFilter to test behavior with and without override api authorization
    @Mock DevContextFilter mDevContextFilter;
    @Mock AppImportanceFilter mAppImportanceFilter;
    @Mock CallerMetadata mMockCallerMetadata;

    @Spy
    FledgeAllowListsFilter mFledgeAllowListsFilterSpy =
            new FledgeAllowListsFilter(mFlags, mAdServicesLoggerMock);

    @Spy private Context mContext = ApplicationProvider.getApplicationContext();
    @Mock private File mMockDBAdSelectionFile;

    @Spy
    FledgeAuthorizationFilter mFledgeAuthorizationFilterSpy =
            new FledgeAuthorizationFilter(
                    mContext.getPackageManager(),
                    new EnrollmentDao(mContext, DbTestUtil.getDbHelperForTest()),
                    mAdServicesLoggerMock);

    @Mock private ConsentManager mConsentManagerMock;
    private MockitoSession mStaticMockSession = null;
    private ExecutorService mLightweightExecutorService;
    private ExecutorService mBackgroundExecutorService;
    private ScheduledThreadPoolExecutor mScheduledExecutor;
    private CustomAudienceDao mCustomAudienceDao;
    @Spy private AdSelectionEntryDao mAdSelectionEntryDaoSpy;
    private AdServicesHttpsClient mAdServicesHttpsClient;
    private AdSelectionServiceImpl mAdSelectionService;
    private Dispatcher mDispatcher;

    @Before
    public void setUp() throws Exception {
        mAdSelectionEntryDaoSpy =
                Room.inMemoryDatabaseBuilder(mContext, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        // Test applications don't have the required permissions to read config P/H flags, and
        // injecting mocked flags everywhere is annoying and non-trivial for static methods
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .initMocks(this)
                        .strictness(Strictness.LENIENT)
                        .startMocking();

        // Initialize dependencies for the AdSelectionService
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mScheduledExecutor = AdServicesExecutors.getScheduler();
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(mContext, CustomAudienceDatabase.class)
                        .build()
                        .customAudienceDao();

        mAdServicesHttpsClient =
                new AdServicesHttpsClient(AdServicesExecutors.getBlockingExecutor());

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());
        when(mMockCallerMetadata.getBinderElapsedTimestamp())
                .thenReturn(SystemClock.elapsedRealtime() - BINDER_ELAPSED_TIME_MS);
        // Create an instance of AdSelection Service with real dependencies
        mAdSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDaoSpy,
                        mCustomAudienceDao,
                        mAdServicesHttpsClient,
                        mDevContextFilter,
                        mAppImportanceFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mContext,
                        mConsentManagerMock,
                        mAdServicesLoggerMock,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy);

        // Create a dispatcher that helps map a request -> response in mockWebServer
        mDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        switch (request.getPath()) {
                            case SELECTION_PICK_HIGHEST_LOGIC_JS_PATH:
                                return new MockResponse().setBody(SELECTION_PICK_HIGHEST_LOGIC_JS);
                            case SELECTION_PICK_NONE_LOGIC_JS_PATH:
                                return new MockResponse().setBody(SELECTION_PICK_NONE_LOGIC_JS);
                            case SELECTION_WATERFALL_LOGIC_JS_PATH:
                                return new MockResponse().setBody(SELECTION_WATERFALL_LOGIC_JS);
                            case SELECTION_FAULTY_LOGIC_JS_PATH:
                                return new MockResponse().setBody(SELECTION_FAULTY_LOGIC_JS);
                            default:
                                return new MockResponse().setResponseCode(404);
                        }
                    }
                };

        when(mContext.getDatabasePath(DATABASE_NAME)).thenReturn(mMockDBAdSelectionFile);
        when(mMockDBAdSelectionFile.length()).thenReturn(DB_AD_SELECTION_FILE_SIZE);
    }

    @After
    public void tearDown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testSelectAdsFromOutcomesPickHighestSuccess() throws Exception {
        doReturn(new AdSelectionFromOutcomesE2ETest.TestFlags()).when(FlagsFactory::getFlags);
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);
        final String selectionLogicPath = SELECTION_PICK_HIGHEST_LOGIC_JS_PATH;

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
                        mMockWebServerRule.uriForPath(selectionLogicPath));

        AdSelectionFromOutcomesE2ETest.AdSelectionFromOutcomesTestCallback resultsCallback =
                invokeSelectAdsFromOutcomes(mAdSelectionService, config, CALLER_PACKAGE_NAME);

        assertThat(resultsCallback.mIsSuccess).isTrue();
        assertThat(resultsCallback.mAdSelectionResponse).isNotNull();
        assertEquals(resultsCallback.mAdSelectionResponse.getAdSelectionId(), AD_SELECTION_ID_3);
        mMockWebServerRule.verifyMockServerRequests(
                server, 1, Collections.singletonList(selectionLogicPath), String::equals);
    }

    @Test
    public void testSelectAdsFromOutcomesWaterfallMediationAdBidHigherThanBidFloorSuccess()
            throws Exception {
        doReturn(new AdSelectionFromOutcomesE2ETest.TestFlags()).when(FlagsFactory::getFlags);
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);
        final String selectionLogicPath = SELECTION_WATERFALL_LOGIC_JS_PATH;

        Map<Long, Double> adSelectionIdToBidMap = Map.of(AD_SELECTION_ID_1, 10.0);
        persistAdSelectionEntryDaoResults(adSelectionIdToBidMap);

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        Collections.singletonList(AD_SELECTION_ID_1),
                        AdSelectionSignals.fromString(
                                String.format(BID_FLOOR_SELECTION_SIGNAL_TEMPLATE, 9)),
                        mMockWebServerRule.uriForPath(selectionLogicPath));

        AdSelectionFromOutcomesE2ETest.AdSelectionFromOutcomesTestCallback resultsCallback =
                invokeSelectAdsFromOutcomes(mAdSelectionService, config, CALLER_PACKAGE_NAME);

        assertThat(resultsCallback.mIsSuccess).isTrue();
        assertThat(resultsCallback.mAdSelectionResponse).isNotNull();
        assertEquals(resultsCallback.mAdSelectionResponse.getAdSelectionId(), AD_SELECTION_ID_1);
        mMockWebServerRule.verifyMockServerRequests(
                server, 1, Collections.singletonList(selectionLogicPath), String::equals);
    }

    @Test
    public void testSelectAdsFromOutcomesWaterfallMediationAdBidLowerThanBidFloorSuccess()
            throws Exception {
        doReturn(new AdSelectionFromOutcomesE2ETest.TestFlags()).when(FlagsFactory::getFlags);
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);
        final String selectionLogicPath = SELECTION_WATERFALL_LOGIC_JS_PATH;

        Map<Long, Double> adSelectionIdToBidMap = Map.of(AD_SELECTION_ID_1, 10.0);
        persistAdSelectionEntryDaoResults(adSelectionIdToBidMap);

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        Collections.singletonList(AD_SELECTION_ID_1),
                        AdSelectionSignals.fromString(
                                String.format(BID_FLOOR_SELECTION_SIGNAL_TEMPLATE, 11)),
                        mMockWebServerRule.uriForPath(selectionLogicPath));

        AdSelectionFromOutcomesE2ETest.AdSelectionFromOutcomesTestCallback resultsCallback =
                invokeSelectAdsFromOutcomes(mAdSelectionService, config, CALLER_PACKAGE_NAME);

        assertThat(resultsCallback.mIsSuccess).isTrue();
        assertThat(resultsCallback.mAdSelectionResponse).isNull();
        mMockWebServerRule.verifyMockServerRequests(
                server, 1, Collections.singletonList(selectionLogicPath), String::equals);
    }

    @Test
    public void testSelectAdsFromOutcomesReturnsNullSuccess() throws Exception {
        doReturn(new AdSelectionFromOutcomesE2ETest.TestFlags()).when(FlagsFactory::getFlags);
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);
        final String selectionLogicPath = SELECTION_PICK_NONE_LOGIC_JS_PATH;

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
                        mMockWebServerRule.uriForPath(selectionLogicPath));

        AdSelectionFromOutcomesE2ETest.AdSelectionFromOutcomesTestCallback resultsCallback =
                invokeSelectAdsFromOutcomes(mAdSelectionService, config, CALLER_PACKAGE_NAME);

        assertThat(resultsCallback.mIsSuccess).isTrue();
        assertThat(resultsCallback.mAdSelectionResponse).isNull();
        mMockWebServerRule.verifyMockServerRequests(
                server, 1, Collections.singletonList(selectionLogicPath), String::equals);
    }

    @Test
    public void testSelectAdsFromOutcomesInvalidSellerFailure() throws Exception {
        doReturn(new AdSelectionFromOutcomesE2ETest.TestFlags()).when(FlagsFactory::getFlags);
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);
        final String selectionLogicPath = "/unreachableLogicJS/";

        long adSelectionId1 = 12345L;
        long adSelectionId2 = 123456L;
        long adSelectionId3 = 1234567L;

        Map<Long, Double> adSelectionIdToBidMap =
                Map.of(
                        adSelectionId1, 10.0,
                        adSelectionId2, 20.0,
                        adSelectionId3, 30.0);
        persistAdSelectionEntryDaoResults(adSelectionIdToBidMap);

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        SELLER_INCONSISTENT_WITH_SELECTION_URI,
                        List.of(adSelectionId1, adSelectionId2, adSelectionId3),
                        AdSelectionSignals.EMPTY,
                        mMockWebServerRule.uriForPath(selectionLogicPath));

        AdSelectionFromOutcomesE2ETest.AdSelectionFromOutcomesTestCallback resultsCallback =
                invokeSelectAdsFromOutcomes(mAdSelectionService, config, CALLER_PACKAGE_NAME);

        assertThat(resultsCallback.mIsSuccess).isFalse();
        assertThat(resultsCallback.mFledgeErrorResponse).isNotNull();
        assertThat(resultsCallback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(STATUS_INVALID_ARGUMENT);
        assertThat(resultsCallback.mFledgeErrorResponse.getErrorMessage())
                .contains(
                        String.format(
                                SELLER_AND_URI_HOST_ARE_INCONSISTENT,
                                Uri.parse(HTTPS_PREFIX + SELLER_INCONSISTENT_WITH_SELECTION_URI)
                                        .getHost(),
                                mMockWebServerRule.uriForPath(selectionLogicPath).getHost()));
        mMockWebServerRule.verifyMockServerRequests(
                server, 0, Collections.emptyList(), String::equals);
    }

    @Test
    public void testSelectAdsFromOutcomesAdSelectionIdOwnedByDifferentCallerPackageFailure()
            throws Exception {
        doReturn(new AdSelectionFromOutcomesE2ETest.TestFlags()).when(FlagsFactory::getFlags);
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);
        final String selectionLogicPath = "/unreachableLogicJS/";

        long adSelectionId1 = 12345L;
        long adSelectionId2 = 123456L;
        long adSelectionId3 = 1234567L;

        Map<Long, Double> adSelectionIdToBidMap =
                Map.of(
                        adSelectionId1, 10.0,
                        adSelectionId2, 20.0);
        persistAdSelectionEntryDaoResults(adSelectionIdToBidMap);
        // Intentionally persisting adSelectionId3 with a different caller package name
        persistAdSelectionEntryDaoResults(
                Collections.singletonMap(adSelectionId3, 30.0), "com.another.package");

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        List.of(adSelectionId1, adSelectionId2, adSelectionId3),
                        AdSelectionSignals.EMPTY,
                        mMockWebServerRule.uriForPath(selectionLogicPath));

        AdSelectionFromOutcomesE2ETest.AdSelectionFromOutcomesTestCallback resultsCallback =
                invokeSelectAdsFromOutcomes(mAdSelectionService, config, CALLER_PACKAGE_NAME);

        assertThat(resultsCallback.mIsSuccess).isFalse();
        assertThat(resultsCallback.mFledgeErrorResponse).isNotNull();
        assertThat(resultsCallback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(STATUS_INVALID_ARGUMENT);
        assertThat(resultsCallback.mFledgeErrorResponse.getErrorMessage())
                .contains(
                        String.format(
                                AD_SELECTION_IDS_DONT_EXIST,
                                Collections.singletonList(adSelectionId3)));
        mMockWebServerRule.verifyMockServerRequests(
                server, 0, Collections.emptyList(), String::equals);
    }

    @Test
    public void testSelectAdsFromOutcomesJsReturnsFaultyAdSelectionIdFailure() throws Exception {
        doReturn(new AdSelectionFromOutcomesE2ETest.TestFlags()).when(FlagsFactory::getFlags);
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);
        final String selectionLogicPath = SELECTION_FAULTY_LOGIC_JS_PATH;

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
                        mMockWebServerRule.uriForPath(selectionLogicPath));

        AdSelectionFromOutcomesE2ETest.AdSelectionFromOutcomesTestCallback resultsCallback =
                invokeSelectAdsFromOutcomes(mAdSelectionService, config, CALLER_PACKAGE_NAME);

        assertThat(resultsCallback.mIsSuccess).isFalse();
        assertThat(resultsCallback.mFledgeErrorResponse).isNotNull();
        assertThat(resultsCallback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(STATUS_INTERNAL_ERROR);
        assertThat(resultsCallback.mFledgeErrorResponse.getErrorMessage())
                .contains(SELECTED_OUTCOME_MUST_BE_ONE_OF_THE_INPUTS);
        mMockWebServerRule.verifyMockServerRequests(
                server, 1, Collections.singletonList(selectionLogicPath), String::equals);
    }

    private AdSelectionFromOutcomesE2ETest.AdSelectionFromOutcomesTestCallback
            invokeSelectAdsFromOutcomes(
                    AdSelectionServiceImpl adSelectionService,
                    AdSelectionFromOutcomesConfig adSelectionFromOutcomesConfig,
                    String callerPackageName)
                    throws InterruptedException, RemoteException {

        CountDownLatch countdownLatch = new CountDownLatch(1);
        AdSelectionFromOutcomesE2ETest.AdSelectionFromOutcomesTestCallback adSelectionTestCallback =
                new AdSelectionFromOutcomesE2ETest.AdSelectionFromOutcomesTestCallback(
                        countdownLatch);

        AdSelectionFromOutcomesInput input =
                new AdSelectionFromOutcomesInput.Builder()
                        .setAdSelectionFromOutcomesConfig(adSelectionFromOutcomesConfig)
                        .setCallerPackageName(callerPackageName)
                        .build();

        adSelectionService.selectAdsFromOutcomes(
                input, mMockCallerMetadata, adSelectionTestCallback);
        adSelectionTestCallback.mCountDownLatch.await();
        return adSelectionTestCallback;
    }

    private void persistAdSelectionEntryDaoResults(Map<Long, Double> adSelectionIdToBidMap) {
        persistAdSelectionEntryDaoResults(adSelectionIdToBidMap, CALLER_PACKAGE_NAME);
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
            mAdSelectionEntryDaoSpy.persistAdSelection(dbAdSelectionEntry);
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

    private static class TestFlags implements Flags {
        @Override
        public boolean getEnforceIsolateMaxHeapSize() {
            return false;
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
        public boolean getDisableFledgeEnrollmentCheck() {
            return true;
        }

        @Override
        public float getSdkRequestPermitsPerSecond() {
            // Unlimited rate for unit tests to avoid flake in tests due to rate
            // limiting
            return -1;
        }
    }
}
