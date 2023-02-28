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

package com.android.adservices.service.adselection;

import static android.adservices.common.AdServicesStatusUtils.RATE_LIMIT_REACHED_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.adservices.adselection.ReportInteractionCallback;
import android.adservices.adselection.ReportInteractionInput;
import android.adservices.adselection.ReportInteractionRequest;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.http.MockWebServerRule;
import android.content.Context;
import android.net.Uri;
import android.os.LimitExceededException;
import android.os.Process;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.adselection.DBRegisteredAdInteraction;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.FledgeServiceFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.stubbing.Answer;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class InteractionReporterTest {
    private static final int MY_UID = Process.myUid();
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    private static final int BID = 6;
    private static final Instant ACTIVATION_TIME = Instant.now();
    private static final long AD_SELECTION_ID = 1;

    private static final String SELLER_INTERACTION_REPORTING_PATH = "/seller/interactionReporting/";
    private static final String BUYER_INTERACTION_REPORTING_PATH = "/buyer/interactionReporting/";
    private static final Uri RENDER_URI = Uri.parse("https://test.com/advert/");

    private static final int BUYER_DESTINATION =
            ReportInteractionRequest.FLAG_REPORTING_DESTINATION_BUYER;
    private static final int SELLER_DESTINATION =
            ReportInteractionRequest.FLAG_REPORTING_DESTINATION_SELLER;
    private static final String CLICK_EVENT = "click";

    private AdSelectionEntryDao mAdSelectionEntryDao;
    private final AdServicesHttpsClient mHttpClient =
            new AdServicesHttpsClient(
                    AdServicesExecutors.getBlockingExecutor(),
                    CacheProviderFactory.createNoOpCache());

    @Mock private AdServicesLogger mAdServicesLoggerMock;

    private final ListeningExecutorService mLightweightExecutorService =
            AdServicesExecutors.getLightWeightExecutor();
    private final ListeningExecutorService mBackgroundExecutorService =
            AdServicesExecutors.getBackgroundExecutor();

    @Mock FledgeAuthorizationFilter mFledgeAuthorizationFilterMock;
    private Flags mFlags = FlagsFactory.getFlagsForTest();

    private static final Flags FLAGS_ENROLLMENT_CHECK =
            new Flags() {
                @Override
                public boolean getDisableFledgeEnrollmentCheck() {
                    return false;
                }
            };

    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();

    @Mock private FledgeServiceFilter mFledgeServiceFilterMock;

    private InteractionReporter mInteractionReporter;

    private DBAdSelection mDBAdSelection;
    private DBRegisteredAdInteraction mDBRegisteredAdInteractionSellerClick;
    private DBRegisteredAdInteraction mDBRegisteredAdInteractionBuyerClick;
    private String mInteractionData;

    private AdTechIdentifier mAdTech = AdTechIdentifier.fromString("localhost");

    private MockitoSession mStaticMockSession = null;

    @Before
    public void setup() throws Exception {
        mStaticMockSession = ExtendedMockito.mockitoSession().initMocks(this).startMocking();

        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        mInteractionReporter =
                new InteractionReporter(
                        CONTEXT,
                        mAdSelectionEntryDao,
                        mHttpClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mAdServicesLoggerMock,
                        mFlags,
                        mFledgeServiceFilterMock,
                        MY_UID,
                        mFledgeAuthorizationFilterMock);

        Uri baseBuyerUri = mMockWebServerRule.uriForPath(BUYER_INTERACTION_REPORTING_PATH);

        mAdTech = AdTechIdentifier.fromString(baseBuyerUri.getHost());

        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignalsBuilder()
                        .setBuyer(mAdTech)
                        .build();

        String biddingLogicPath = "/buyer/bidding";
        mDBAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(customAudienceSignals)
                        .setContextualSignals("{}")
                        .setBiddingLogicUri(mMockWebServerRule.uriForPath(biddingLogicPath))
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mDBRegisteredAdInteractionBuyerClick =
                DBRegisteredAdInteraction.builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setInteractionKey(CLICK_EVENT)
                        .setReportingDestination(BUYER_DESTINATION)
                        .setInteractionReportingUri(
                                mMockWebServerRule.uriForPath(
                                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT))
                        .build();

        mDBRegisteredAdInteractionSellerClick =
                DBRegisteredAdInteraction.builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setInteractionKey(CLICK_EVENT)
                        .setReportingDestination(SELLER_DESTINATION)
                        .setInteractionReportingUri(
                                mMockWebServerRule.uriForPath(
                                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT))
                        .build();

        mInteractionData = new JSONObject().put("x", "10").put("y", "12").toString();
    }

    @After
    public void tearDown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testReportInteractionSuccessfullyReportsRegisteredInteractions() throws Exception {
        mAdSelectionEntryDao.persistAdSelection(mDBAdSelection);
        mAdSelectionEntryDao.persistDBRegisteredAdInteractions(
                List.of(
                        mDBRegisteredAdInteractionBuyerClick,
                        mDBRegisteredAdInteractionSellerClick));

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse(), new MockResponse()));

        ReportInteractionInput inputParams =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .setInteractionKey(CLICK_EVENT)
                        .setInteractionData(mInteractionData)
                        .setReportingDestinations(BUYER_DESTINATION | SELLER_DESTINATION)
                        .build();

        ReportInteractionTestCallback callback = callReportInteraction(inputParams);

        assertTrue(callback.mIsSuccess);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_SUCCESS),
                        anyInt());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications)
                .containsExactly(
                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT,
                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT);
    }

    @Test
    public void testReportInteractionOnlyReportsBuyersRegisteredInteractions() throws Exception {
        mAdSelectionEntryDao.persistAdSelection(mDBAdSelection);

        mAdSelectionEntryDao.persistDBRegisteredAdInteractions(
                List.of(
                        mDBRegisteredAdInteractionBuyerClick,
                        mDBRegisteredAdInteractionSellerClick));

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                if (request.getPath()
                                        .equals(BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT)) {
                                    return new MockResponse();
                                } else {
                                    throw new IllegalStateException(
                                            "Only buyer reporting can occur!");
                                }
                            }
                        });

        ReportInteractionInput inputParams =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .setInteractionKey(CLICK_EVENT)
                        .setInteractionData(mInteractionData)
                        .setReportingDestinations(BUYER_DESTINATION)
                        .build();

        ReportInteractionTestCallback callback = callReportInteraction(inputParams);

        assertTrue(callback.mIsSuccess);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Assert buyer reporting was done
        assertEquals(
                BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT, server.takeRequest().getPath());
    }

    @Test
    public void testReportInteractionOnlyReportsSellerRegisteredInteractions() throws Exception {
        mAdSelectionEntryDao.persistAdSelection(mDBAdSelection);

        mAdSelectionEntryDao.persistDBRegisteredAdInteractions(
                List.of(
                        mDBRegisteredAdInteractionBuyerClick,
                        mDBRegisteredAdInteractionSellerClick));

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                if (request.getPath()
                                        .equals(SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT)) {
                                    return new MockResponse();
                                } else {
                                    throw new IllegalStateException(
                                            "Only seller reporting can occur!");
                                }
                            }
                        });

        ReportInteractionInput inputParams =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .setInteractionKey(CLICK_EVENT)
                        .setInteractionData(mInteractionData)
                        .setReportingDestinations(SELLER_DESTINATION)
                        .build();

        ReportInteractionTestCallback callback = callReportInteraction(inputParams);

        assertTrue(callback.mIsSuccess);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Assert seller reporting was done
        assertEquals(
                SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT, server.takeRequest().getPath());
    }

    @Test
    public void testReportInteractionReturnsOnlyReportsUriThatPassesEnrollmentCheck()
            throws Exception {
        mAdSelectionEntryDao.persistAdSelection(mDBAdSelection);
        mAdSelectionEntryDao.persistDBRegisteredAdInteractions(
                List.of(
                        mDBRegisteredAdInteractionBuyerClick,
                        mDBRegisteredAdInteractionSellerClick));

        mFlags = FLAGS_ENROLLMENT_CHECK;

        // Re init interaction reporter
        mInteractionReporter =
                new InteractionReporter(
                        CONTEXT,
                        mAdSelectionEntryDao,
                        mHttpClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mAdServicesLoggerMock,
                        mFlags,
                        mFledgeServiceFilterMock,
                        MY_UID,
                        mFledgeAuthorizationFilterMock);

        // Allow the first call and filter the second
        doNothing()
                .doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mFledgeAuthorizationFilterMock)
                .assertAdTechAllowed(
                        CONTEXT,
                        TEST_PACKAGE_NAME,
                        mAdTech,
                        AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN);

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            AtomicInteger mNumCalls = new AtomicInteger(0);

                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                if (mNumCalls.get() > 0) {
                                    throw new IllegalStateException(
                                            "Only first reporting URI has fledge enrollment!");
                                } else {
                                    mNumCalls.addAndGet(1);
                                    return new MockResponse();
                                }
                            }
                        });

        ReportInteractionInput inputParams =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .setInteractionKey(CLICK_EVENT)
                        .setInteractionData(mInteractionData)
                        .setReportingDestinations(BUYER_DESTINATION | SELLER_DESTINATION)
                        .build();

        ReportInteractionTestCallback callback = callReportInteraction(inputParams);

        assertTrue(callback.mIsSuccess);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Assert only one reporting call to http client was made
        RecordedRequest recordedRequest = server.takeRequest();
        assertTrue(recordedRequest.getPath().contains(CLICK_EVENT));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void
            testReportInteractionReturnsSuccessButDoesNotDoReportingWhenBothFailEnrollmentCheck()
                    throws Exception {
        mAdSelectionEntryDao.persistAdSelection(mDBAdSelection);
        mAdSelectionEntryDao.persistDBRegisteredAdInteractions(
                List.of(
                        mDBRegisteredAdInteractionBuyerClick,
                        mDBRegisteredAdInteractionSellerClick));

        mFlags = FLAGS_ENROLLMENT_CHECK;

        // Re init interaction reporter
        mInteractionReporter =
                new InteractionReporter(
                        CONTEXT,
                        mAdSelectionEntryDao,
                        mHttpClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mAdServicesLoggerMock,
                        mFlags,
                        mFledgeServiceFilterMock,
                        MY_UID,
                        mFledgeAuthorizationFilterMock);

        doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mFledgeAuthorizationFilterMock)
                .assertAdTechAllowed(
                        CONTEXT,
                        TEST_PACKAGE_NAME,
                        mAdTech,
                        AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN);

        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made without fledge enrollment!");
                    }
                });

        ReportInteractionInput inputParams =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .setInteractionKey(CLICK_EVENT)
                        .setInteractionData(mInteractionData)
                        .setReportingDestinations(BUYER_DESTINATION | SELLER_DESTINATION)
                        .build();

        ReportInteractionTestCallback callback = callReportInteraction(inputParams);

        assertTrue(callback.mIsSuccess);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportInteractionFailsWithInvalidPackageName() throws Exception {
        mAdSelectionEntryDao.persistAdSelection(mDBAdSelection);
        mAdSelectionEntryDao.persistDBRegisteredAdInteractions(
                List.of(
                        mDBRegisteredAdInteractionBuyerClick,
                        mDBRegisteredAdInteractionSellerClick));

        doThrow(new FledgeAuthorizationFilter.CallerMismatchException())
                .when(mFledgeServiceFilterMock)
                .filterRequest(
                        null,
                        TEST_PACKAGE_NAME,
                        true,
                        MY_UID,
                        AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN,
                        Throttler.ApiKey.FLEDGE_API_REPORT_INTERACTION);

        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made when app package name invalid!");
                    }
                });

        ReportInteractionInput inputParams =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .setInteractionKey(CLICK_EVENT)
                        .setInteractionData(mInteractionData)
                        .setReportingDestinations(BUYER_DESTINATION | SELLER_DESTINATION)
                        .build();

        ReportInteractionTestCallback callback = callReportInteraction(inputParams);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_UNAUTHORIZED);
        assertEquals(
                callback.mFledgeErrorResponse.getErrorMessage(),
                AdServicesStatusUtils
                        .SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_UNAUTHORIZED),
                        anyInt());
    }

    @Test
    public void testReportInteractionFailsWhenForegroundCheckFails() throws Exception {
        mAdSelectionEntryDao.persistAdSelection(mDBAdSelection);
        mAdSelectionEntryDao.persistDBRegisteredAdInteractions(
                List.of(
                        mDBRegisteredAdInteractionBuyerClick,
                        mDBRegisteredAdInteractionSellerClick));

        doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                .when(mFledgeServiceFilterMock)
                .filterRequest(
                        null,
                        TEST_PACKAGE_NAME,
                        true,
                        MY_UID,
                        AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN,
                        Throttler.ApiKey.FLEDGE_API_REPORT_INTERACTION);

        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made when app not in foreground!");
                    }
                });

        ReportInteractionInput inputParams =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .setInteractionKey(CLICK_EVENT)
                        .setInteractionData(mInteractionData)
                        .setReportingDestinations(BUYER_DESTINATION | SELLER_DESTINATION)
                        .build();

        ReportInteractionTestCallback callback = callReportInteraction(inputParams);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_BACKGROUND_CALLER);
        assertEquals(
                callback.mFledgeErrorResponse.getErrorMessage(),
                AdServicesStatusUtils.ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_BACKGROUND_CALLER),
                        anyInt());
    }

    @Test
    public void testReportInteractionFailsWhenThrottled() throws Exception {
        mAdSelectionEntryDao.persistAdSelection(mDBAdSelection);
        mAdSelectionEntryDao.persistDBRegisteredAdInteractions(
                List.of(
                        mDBRegisteredAdInteractionBuyerClick,
                        mDBRegisteredAdInteractionSellerClick));

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            AtomicInteger mNumCalls = new AtomicInteger(0);

                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                if (mNumCalls.get() > 2) {
                                    throw new IllegalStateException(
                                            "Only first 2 POST requests are not throttled!");
                                } else {
                                    mNumCalls.addAndGet(1);
                                    return new MockResponse();
                                }
                            }
                        });

        ReportInteractionInput inputParams =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .setInteractionKey(CLICK_EVENT)
                        .setInteractionData(mInteractionData)
                        .setReportingDestinations(BUYER_DESTINATION | SELLER_DESTINATION)
                        .build();

        // First call should succeed
        ReportInteractionTestCallback callbackFirstCall = callReportInteraction(inputParams);

        doThrow(new LimitExceededException(RATE_LIMIT_REACHED_ERROR_MESSAGE))
                .when(mFledgeServiceFilterMock)
                .filterRequest(
                        null,
                        TEST_PACKAGE_NAME,
                        true,
                        MY_UID,
                        AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN,
                        Throttler.ApiKey.FLEDGE_API_REPORT_INTERACTION);

        // Immediately made subsequent call should fail
        ReportInteractionTestCallback callbackSubsequentCall = callReportInteraction(inputParams);

        assertTrue(callbackFirstCall.mIsSuccess);
        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications)
                .containsExactly(
                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT,
                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_SUCCESS),
                        anyInt());

        assertFalse(callbackSubsequentCall.mIsSuccess);
        assertEquals(
                STATUS_RATE_LIMIT_REACHED,
                callbackSubsequentCall.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                callbackSubsequentCall.mFledgeErrorResponse.getErrorMessage(),
                RATE_LIMIT_REACHED_ERROR_MESSAGE);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_RATE_LIMIT_REACHED),
                        anyInt());
    }

    @Test
    public void testReportInteractionFailsWhenAppNotInAllowList() throws Exception {
        mAdSelectionEntryDao.persistAdSelection(mDBAdSelection);
        mAdSelectionEntryDao.persistDBRegisteredAdInteractions(
                List.of(
                        mDBRegisteredAdInteractionBuyerClick,
                        mDBRegisteredAdInteractionSellerClick));

        doThrow(new FledgeAllowListsFilter.AppNotAllowedException())
                .when(mFledgeServiceFilterMock)
                .filterRequest(
                        null,
                        TEST_PACKAGE_NAME,
                        true,
                        MY_UID,
                        AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN,
                        Throttler.ApiKey.FLEDGE_API_REPORT_INTERACTION);

        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made when app not in allowlist!");
                    }
                });

        ReportInteractionInput inputParams =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .setInteractionKey(CLICK_EVENT)
                        .setInteractionData(mInteractionData)
                        .setReportingDestinations(BUYER_DESTINATION | SELLER_DESTINATION)
                        .build();

        ReportInteractionTestCallback callback = callReportInteraction(inputParams);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_CALLER_NOT_ALLOWED);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_CALLER_NOT_ALLOWED),
                        anyInt());
    }

    @Test
    public void testReportInteractionFailsSilentlyWithoutConsent() throws Exception {
        mAdSelectionEntryDao.persistAdSelection(mDBAdSelection);
        mAdSelectionEntryDao.persistDBRegisteredAdInteractions(
                List.of(
                        mDBRegisteredAdInteractionBuyerClick,
                        mDBRegisteredAdInteractionSellerClick));

        doThrow(new ConsentManager.RevokedConsentException())
                .when(mFledgeServiceFilterMock)
                .filterRequest(
                        null,
                        TEST_PACKAGE_NAME,
                        true,
                        MY_UID,
                        AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN,
                        Throttler.ApiKey.FLEDGE_API_REPORT_INTERACTION);

        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made without user consent!");
                    }
                });

        ReportInteractionInput inputParams =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .setInteractionKey(CLICK_EVENT)
                        .setInteractionData(mInteractionData)
                        .setReportingDestinations(BUYER_DESTINATION | SELLER_DESTINATION)
                        .build();

        ReportInteractionTestCallback callback = callReportInteraction(inputParams);

        assertTrue(callback.mIsSuccess);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        anyInt());
    }

    @Test
    public void testReportInteractionFailsWithUnknownAdSelectionId() throws Exception {
        mAdSelectionEntryDao.persistAdSelection(mDBAdSelection);
        mAdSelectionEntryDao.persistDBRegisteredAdInteractions(
                List.of(
                        mDBRegisteredAdInteractionBuyerClick,
                        mDBRegisteredAdInteractionSellerClick));

        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made with invalid adselection id!");
                    }
                });

        ReportInteractionInput inputParams =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID + 1)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .setInteractionKey(CLICK_EVENT)
                        .setInteractionData(mInteractionData)
                        .setReportingDestinations(BUYER_DESTINATION | SELLER_DESTINATION)
                        .build();

        ReportInteractionTestCallback callback = callReportInteraction(inputParams);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INVALID_ARGUMENT);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_INVALID_ARGUMENT),
                        anyInt());
    }

    @Test
    public void testReportInteractionSuccedsWhenNotFindingRegisteredAdInteractions()
            throws Exception {
        mAdSelectionEntryDao.persistAdSelection(mDBAdSelection);

        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made since ad interactions are not"
                                        + " registered!");
                    }
                });

        ReportInteractionInput inputParams =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .setInteractionKey(CLICK_EVENT)
                        .setInteractionData(mInteractionData)
                        .setReportingDestinations(BUYER_DESTINATION | SELLER_DESTINATION)
                        .build();

        ReportInteractionTestCallback callback = callReportInteraction(inputParams);

        assertTrue(callback.mIsSuccess);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    private ReportInteractionTestCallback callReportInteraction(ReportInteractionInput inputParams)
            throws Exception {
        // Counted down in 1) callback and 2) logApiCall
        CountDownLatch resultLatch = new CountDownLatch(2);
        ReportInteractionTestCallback callback = new ReportInteractionTestCallback(resultLatch);

        // Wait for the logging call, which happens after the callback
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        mInteractionReporter.reportInteraction(inputParams, callback);
        resultLatch.await();
        return callback;
    }

    static class ReportInteractionTestCallback extends ReportInteractionCallback.Stub {
        private final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        ReportInteractionTestCallback(CountDownLatch countDownLatch) {
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
