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

import static android.adservices.adselection.DataHandlersFixture.AD_SELECTION_ID_2;
import static android.adservices.common.AdServicesStatusUtils.ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.RATE_LIMIT_REACHED_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static com.android.adservices.service.adselection.EventReporter.INTERACTION_DATA_SIZE_MAX_EXCEEDED;
import static com.android.adservices.service.adselection.EventReporter.INTERACTION_KEY_SIZE_MAX_EXCEEDED;
import static com.android.adservices.service.common.AppManifestConfigCall.API_AD_SELECTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.adservices.adselection.DataHandlersFixture;
import android.adservices.adselection.ReportEventRequest;
import android.adservices.adselection.ReportInteractionInput;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.http.MockWebServerRule;
import android.content.Context;
import android.net.Uri;
import android.os.LimitExceededException;
import android.os.Process;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.adselection.DBRegisteredAdInteraction;
import com.android.adservices.data.adselection.datahandlers.RegisteredAdInteraction;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.AllowLists;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.PermissionHelper;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.measurement.MeasurementImpl;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.ReportInteractionApiCalledStats;
import com.android.adservices.shared.testing.AnswerSyncCallback;
import com.android.adservices.shared.testing.concurrency.SyncCallbackFactory;
import com.android.adservices.shared.testing.concurrency.SyncCallbackSettings;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@MockStatic(ConsentManager.class)
@MockStatic(PermissionHelper.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(DebugFlags.class)
public final class ReportAndRegisterEventFallbackImplTest
        extends AdServicesExtendedMockitoTestCase {
    private static final Instant ACTIVATION_TIME = Instant.now();
    private static final int MY_UID = Process.myUid();
    private static final String CALLER_SDK_NAME = "sdk.package.name";
    private static final AdTechIdentifier AD_TECH = AdTechIdentifier.fromString("localhost");
    private static final int BID = 6;
    private static final long AD_SELECTION_ID = 1;
    private static final String SELLER_INTERACTION_REPORTING_PATH = "/seller/interactionReporting/";
    private static final String BUYER_INTERACTION_REPORTING_PATH = "/buyer/interactionReporting/";
    private static final Uri RENDER_URI = Uri.parse("https://test.com/advert/");
    private static final int BUYER_DESTINATION =
            ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER;
    private static final int SELLER_DESTINATION =
            ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;
    private static final int SELLER_AND_BUYER_DESTINATION =
            ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER
                    | ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;
    private static final String CLICK_EVENT = "click";
    private static final boolean SHOULD_COUNT_LOG = true;
    private static final boolean SHOULD_NOT_COUNT_LOG = false;

    private AdSelectionEntryDao mAdSelectionEntryDao;

    @Spy
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
    private Flags mFakeFlags = new ReportEventTestFlags();
    private static final Flags FLAGS_ENROLLMENT_CHECK =
            new ReportEventTestFlags() {
                @Override
                public boolean getDisableFledgeEnrollmentCheck() {
                    return false;
                }
            };
    private final long mMaxRegisteredAdBeaconsTotalCount =
            mFakeFlags.getFledgeReportImpressionMaxRegisteredAdBeaconsTotalCount();
    private final long mMaxRegisteredAdBeaconsPerDestination =
            mFakeFlags.getFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount();

    private final IllegalStateException mRuntimeException =
            new IllegalStateException("Exception for test!");

    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    @Mock private AdSelectionServiceFilter mAdSelectionServiceFilterMock;
    @Mock private MeasurementImpl mMeasurementServiceMock;
    @Mock private ConsentManager mConsentManagerMock;
    private ReportAndRegisterEventFallbackImpl mEventReporter;
    private DBAdSelection mDBAdSelection;
    private DBRegisteredAdInteraction mDBRegisteredAdInteractionSellerClick;
    private DBRegisteredAdInteraction mDBRegisteredAdInteractionBuyerClick;
    private String mEventData;
    private ReportInteractionInput.Builder mInputBuilder;

    @Before
    public void setup() throws Exception {
        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        mEventReporter = getReportAndRegisterEventFallbackImpl(mFakeFlags);

        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignalsBuilder()
                        .setBuyer(AD_TECH)
                        .build();

        String biddingLogicPath = "/buyer/bidding";
        mDBAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(customAudienceSignals)
                        .setBuyerContextualSignals("{}")
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
                        .setDestination(BUYER_DESTINATION)
                        .setInteractionReportingUri(
                                mMockWebServerRule.uriForPath(
                                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT))
                        .build();

        mDBRegisteredAdInteractionSellerClick =
                DBRegisteredAdInteraction.builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setInteractionKey(CLICK_EVENT)
                        .setDestination(SELLER_DESTINATION)
                        .setInteractionReportingUri(
                                mMockWebServerRule.uriForPath(
                                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT))
                        .build();
        mEventData = new JSONObject().put("x", "10").put("y", "12").toString();

        mInputBuilder =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .setInteractionKey(CLICK_EVENT)
                        .setInteractionData(mEventData)
                        .setReportingDestinations(BUYER_DESTINATION | SELLER_DESTINATION)
                        .setCallerSdkName(CALLER_SDK_NAME);
    }

    @Test
    public void testImplSuccessfullyReportsRegisteredEvents() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse(), new MockResponse()));

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input, SHOULD_COUNT_LOG);

        // Verify registerEvent was called with exact parameters.
        verifyRegisterEvent(SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);
        verifyRegisterEvent(BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);

        // Confirm success was reported to caller.
        callback.assertSuccess();

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());
        assertThat(notifications)
                .containsExactly(
                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT,
                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT);
    }

    @Test
    public void testImplDoesNotCrashAfterSellerReportingThrowsAnException() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Fail seller reporting.
        Uri reportingUri = mDBRegisteredAdInteractionSellerClick.getInteractionReportingUri();
        ListenableFuture<Void> failedFuture =
                Futures.submit(
                        () -> {
                            throw mRuntimeException;
                        },
                        mLightweightExecutorService);
        doReturn(failedFuture)
                .when(mHttpClient)
                .postPlainText(reportingUri, mEventData, DevContext.createForDevOptionsDisabled());

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                if (request.getPath().contains(reportingUri.getPath())) {
                                    return new MockResponse();
                                } else {
                                    throw new IllegalStateException(
                                            "Only buyer reporting can occur!");
                                }
                            }
                        });

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        AnswerSyncCallback<Void> sellerCallback =
                syncRegisterEvent(SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);
        AnswerSyncCallback<Void> buyerCallback =
                syncRegisterEvent(BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);

        ReportEventTestCallback callback = callReportEvent(input, SHOULD_COUNT_LOG);

        // Verify registerEvent was called with exact parameters.
        sellerCallback.assertCalled();
        buyerCallback.assertCalled();

        // Confirm success was reported to caller.
        callback.assertSuccess();

        // Confirm failure caused by the exception thrown is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());

        // Verify the mock server handled only buyer reporting requests with exact paths.
        assertThat(server.takeRequest().getPath())
                .isEqualTo(BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT);
    }

    @Test
    public void testImplDoesNotCrashAfterSellerReportingAndRegisteringThrowsAnException()
            throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Fail seller reporting and registering.
        Uri reportingUri = mDBRegisteredAdInteractionSellerClick.getInteractionReportingUri();

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                if (request.getPath().contains(reportingUri.getPath())) {
                                    return new MockResponse();
                                } else {
                                    throw new IllegalStateException(
                                            "Only buyer reporting can occur!");
                                }
                            }
                        });

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        AnswerSyncCallback<Void> sellerCallback =
                AnswerSyncCallback.forSingleFailure(Void.class, mRuntimeException);
        doAnswer(sellerCallback)
                .when(mMeasurementServiceMock)
                .registerEvent(eq(reportingUri), any(), any(), anyBoolean(), any(), any(), any());
        AnswerSyncCallback<Void> buyerCallback =
                syncRegisterEvent(BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);

        ReportEventTestCallback callback = callReportEvent(input, SHOULD_COUNT_LOG);

        // Verify registerEvent was called with exact parameters.
        sellerCallback.assertCalled();
        buyerCallback.assertCalled();

        // Confirm success was reported to caller.
        callback.assertSuccess();

        // Confirm failure caused by the exception thrown is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());

        // Verify the mock server handled both buyer and seller reporting requests with exact paths.
        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());
        assertThat(notifications)
                .containsExactly(
                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT,
                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT);
    }

    @Test
    public void testImplDoesNotCrashAfterBuyerReportingThrowsAnException() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Fail buyer reporting.
        Uri reportingUri = mDBRegisteredAdInteractionBuyerClick.getInteractionReportingUri();
        ListenableFuture<Void> failedFuture =
                Futures.submit(
                        () -> {
                            throw mRuntimeException;
                        },
                        mLightweightExecutorService);
        doReturn(failedFuture)
                .when(mHttpClient)
                .postPlainText(reportingUri, mEventData, DevContext.createForDevOptionsDisabled());

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                if (request.getPath().contains(reportingUri.getPath())) {
                                    return new MockResponse();
                                } else {
                                    throw new IllegalStateException(
                                            "Only buyer reporting can occur!");
                                }
                            }
                        });

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        AnswerSyncCallback<Void> sellerCallback =
                syncRegisterEvent(SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);
        AnswerSyncCallback<Void> buyerCallback =
                syncRegisterEvent(BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);

        ReportEventTestCallback callback = callReportEvent(input, SHOULD_COUNT_LOG);

        // Verify registerEvent was called with exact parameters.
        sellerCallback.assertCalled();
        buyerCallback.assertCalled();

        // Confirm success was reported to caller.
        callback.assertSuccess();

        // Confirm failure caused by the exception thrown is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());

        // Verify the mock server handled only seller reporting requests with exact paths.
        assertThat(server.takeRequest().getPath())
                .isEqualTo(SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT);
    }

    @Test
    public void testImplDoesNotCrashAfterBuyerReportingAndRegisteringThrowsAnException()
            throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Fail buyer reporting and registering.
        Uri reportingUri = mDBRegisteredAdInteractionBuyerClick.getInteractionReportingUri();

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                if (request.getPath().contains(reportingUri.getPath())) {
                                    return new MockResponse();
                                } else {
                                    throw new IllegalStateException(
                                            "Only buyer reporting can occur!");
                                }
                            }
                        });

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        AnswerSyncCallback<Void> sellerCallback =
                syncRegisterEvent(SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);
        AnswerSyncCallback<Void> buyerCallback =
                AnswerSyncCallback.forSingleFailure(Void.class, mRuntimeException);
        doAnswer(buyerCallback)
                .when(mMeasurementServiceMock)
                .registerEvent(eq(reportingUri), any(), any(), anyBoolean(), any(), any(), any());

        ReportEventTestCallback callback = callReportEvent(input, SHOULD_COUNT_LOG);

        // Verify registerEvent was called with exact parameters.
        sellerCallback.assertCalled();
        buyerCallback.assertCalled();

        // Confirm success was reported to caller.
        callback.assertSuccess();

        // Confirm failure caused by the exception thrown is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());

        // Verify the mock server handled both buyer and seller reporting requests with exact paths.
        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());
        assertThat(notifications)
                .containsExactly(
                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT,
                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT);
    }

    @Test
    public void testImplOnlyReportsBuyersRegisteredEvents() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Mock server to report the event in addition to being registered by measurement.
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

        // Call report event with input.
        ReportInteractionInput input =
                mInputBuilder.setReportingDestinations(BUYER_DESTINATION).build();
        ReportEventTestCallback callback = callReportEvent(input, SHOULD_COUNT_LOG);

        // Verify registerEvent was called with exact parameters.
        verifyRegisterEvent(BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);

        // Confirm success was reported to caller.
        callback.assertSuccess();

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        assertThat(server.takeRequest().getPath())
                .isEqualTo(BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT);
    }

    @Test
    public void testImplOnlyReportsSellerRegisteredEvents() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Mock server to report the event in addition to being registered by measurement.
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

        // Call report event with input.
        ReportInteractionInput input =
                mInputBuilder.setReportingDestinations(SELLER_DESTINATION).build();
        ReportEventTestCallback callback = callReportEvent(input, SHOULD_COUNT_LOG);

        // Verify registerEvent was called with exact parameters.
        verifyRegisterEvent(SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);

        // Confirm success was reported to caller.
        callback.assertSuccess();

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        assertThat(server.takeRequest().getPath())
                .isEqualTo(SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT);
    }

    @Test
    public void testImplReturnsOnlyReportsUriThatPassesEnrollmentCheck() throws Exception {
        // Uses ArgumentCaptor to capture the logs in the tests.
        ArgumentCaptor<ReportInteractionApiCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(ReportInteractionApiCalledStats.class);

        enableARA();
        persistReportingArtifacts();

        // Re-initialize event reporter.
        mEventReporter = getReportAndRegisterEventFallbackImpl(FLAGS_ENROLLMENT_CHECK);

        // Allow the first call and filter the second.
        doNothing()
                .doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mFledgeAuthorizationFilterMock)
                .assertAdTechFromUriEnrolled(
                        any(Uri.class),
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(API_AD_SELECTION));

        // Mock server to report the event in addition to being registered by measurement.
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

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input, SHOULD_COUNT_LOG);

        // Verify registerEvent was called with exact parameters.
        verifyRegisterEvent(SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);

        // Confirm success was reported to caller.
        callback.assertSuccess();

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        assertThat(server.takeRequest().getPath()).contains(CLICK_EVENT);

        // Verifies ReportInteractionApiCalledStats get the correct values.
        Mockito.verify(mAdServicesLoggerMock, times(1))
                .logReportInteractionApiCalledStats(argumentCaptor.capture());
        ReportInteractionApiCalledStats stats = argumentCaptor.getValue();
        assertThat(stats.getBeaconReportingDestinationType())
                .isEqualTo(SELLER_AND_BUYER_DESTINATION);
        assertThat(stats.getNumMatchingUris()).isEqualTo(1);
    }

    @Test
    public void testImplReturnsSuccessButDoesNotDoReportingWhenBothFailEnrollmentCheck()
            throws Exception {
        // Uses ArgumentCaptor to capture the logs in the tests.
        ArgumentCaptor<ReportInteractionApiCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(ReportInteractionApiCalledStats.class);

        enableARA();
        persistReportingArtifacts();

        // Re-initialize event reporter.
        mEventReporter = getReportAndRegisterEventFallbackImpl(FLAGS_ENROLLMENT_CHECK);

        // Filter the call.
        doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mFledgeAuthorizationFilterMock)
                .assertAdTechFromUriEnrolled(
                        any(Uri.class),
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(API_AD_SELECTION));

        // Mock server to report the event in addition to being registered by measurement.
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made without fledge enrollment!");
                    }
                });

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input, SHOULD_COUNT_LOG);

        // Verify registerEvent was never called.
        verify(mMeasurementServiceMock, never())
                .registerEvent(any(), any(), any(), anyBoolean(), any(), any(), any());

        // Confirm success was reported to caller.
        callback.assertSuccess();

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verifies ReportInteractionApiCalledStats get the correct values.
        Mockito.verify(mAdServicesLoggerMock, times(1))
                .logReportInteractionApiCalledStats(argumentCaptor.capture());
        ReportInteractionApiCalledStats stats = argumentCaptor.getValue();
        assertThat(stats.getBeaconReportingDestinationType())
                .isEqualTo(SELLER_AND_BUYER_DESTINATION);
        assertThat(stats.getNumMatchingUris()).isEqualTo(0);
    }

    @Test
    public void testImplFailsWithInvalidPackageName() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Filter the call.
        doThrow(new FilterException(new FledgeAuthorizationFilter.CallerMismatchException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        null,
                        TEST_PACKAGE_NAME,
                        true,
                        true,
                        true,
                        MY_UID,
                        AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION,
                        Throttler.ApiKey.FLEDGE_API_REPORT_INTERACTION,
                        DevContext.createForDevOptionsDisabled());

        // Mock server to report the event in addition to being registered by measurement.
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made when app package name invalid!");
                    }
                });

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input);

        // Verify registerEvent was never called.
        verify(mMeasurementServiceMock, never())
                .registerEvent(any(), any(), any(), anyBoolean(), any(), any(), any());

        // Confirm failure was reported to caller.
        callback.assertErrorReceived(
                STATUS_UNAUTHORIZED, SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE);

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_UNAUTHORIZED),
                        anyInt());
    }

    @Test
    public void testImplFailsWhenForegroundCheckFails() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Filter the call.
        doThrow(
                        new FilterException(
                                new AppImportanceFilter.WrongCallingApplicationStateException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        null,
                        TEST_PACKAGE_NAME,
                        true,
                        true,
                        true,
                        MY_UID,
                        AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION,
                        Throttler.ApiKey.FLEDGE_API_REPORT_INTERACTION,
                        DevContext.createForDevOptionsDisabled());

        // Mock server to report the event in addition to being registered by measurement.
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made when app not in foreground!");
                    }
                });

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input);

        // Verify registerEvent was never called.
        verify(mMeasurementServiceMock, never())
                .registerEvent(any(), any(), any(), anyBoolean(), any(), any(), any());

        // Confirm failure was reported to caller.
        callback.assertErrorReceived(
                STATUS_BACKGROUND_CALLER, ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE);

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_BACKGROUND_CALLER),
                        anyInt());
    }

    @Test
    public void testImplFailsWhenThrottled() throws Exception {
        enableARA();
        persistReportingArtifacts();

        ReportInteractionInput input = mInputBuilder.build();

        // Allow the first call and filter the second.
        doNothing()
                .doThrow(
                        new FilterException(
                                new LimitExceededException(RATE_LIMIT_REACHED_ERROR_MESSAGE)))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        null,
                        TEST_PACKAGE_NAME,
                        true,
                        true,
                        true,
                        MY_UID,
                        AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION,
                        Throttler.ApiKey.FLEDGE_API_REPORT_INTERACTION,
                        DevContext.createForDevOptionsDisabled());

        // Mock server to report the event in addition to being registered by measurement.
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

        // First call should succeed.
        ReportEventTestCallback callbackFirstCall = callReportEvent(input, SHOULD_COUNT_LOG);

        // Verify registerEvent was called with exact parameters.
        verifyRegisterEvent(SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);
        verifyRegisterEvent(BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);

        // Confirm success was reported to caller.
        callbackFirstCall.assertSuccess();

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());
        assertThat(notifications)
                .containsExactly(
                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT,
                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT);

        // Immediately made subsequent call should fail
        ReportEventTestCallback callbackSubsequentCall = callReportEvent(input);

        // Confirm failure was reported to caller.
        callbackSubsequentCall.assertErrorReceived(
                STATUS_RATE_LIMIT_REACHED, RATE_LIMIT_REACHED_ERROR_MESSAGE);

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_RATE_LIMIT_REACHED),
                        anyInt());
    }

    @Test
    public void testImplFailsWhenAppNotInAllowList() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Filter the call.
        doThrow(new FilterException(new FledgeAllowListsFilter.AppNotAllowedException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        null,
                        TEST_PACKAGE_NAME,
                        true,
                        true,
                        true,
                        MY_UID,
                        AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION,
                        Throttler.ApiKey.FLEDGE_API_REPORT_INTERACTION,
                        DevContext.createForDevOptionsDisabled());

        // Mock server to report the event in addition to being registered by measurement.
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made when app not in allowlist!");
                    }
                });

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input);

        // Confirm failure was reported to caller.
        callback.assertErrorReceived(STATUS_CALLER_NOT_ALLOWED);

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_CALLER_NOT_ALLOWED),
                        anyInt());
    }

    @Test
    public void testImplFailsSilentlyWithoutConsent() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Filter the call.
        doThrow(new FilterException(new ConsentManager.RevokedConsentException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        null,
                        TEST_PACKAGE_NAME,
                        true,
                        true,
                        true,
                        MY_UID,
                        AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION,
                        Throttler.ApiKey.FLEDGE_API_REPORT_INTERACTION,
                        DevContext.createForDevOptionsDisabled());

        // Mock server to report the event in addition to being registered by measurement.
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made without user consent!");
                    }
                });

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input);

        // Confirm success was reported to caller.
        callback.assertSuccess();

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        anyInt());
    }

    @Test
    public void testImplFailsWithUnknownAdSelectionId() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Mock server to report the event in addition to being registered by measurement.
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made with invalid adselection id!");
                    }
                });

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.setAdSelectionId(AD_SELECTION_ID + 1).build();
        ReportEventTestCallback callback = callReportEvent(input);

        // Confirm failure was reported to caller.
        callback.assertErrorReceived(STATUS_INVALID_ARGUMENT);

        // Confirm failure caused by the input is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INVALID_ARGUMENT),
                        anyInt());
    }

    @Test
    public void testImplSucceedsWhenNotFindingRegisteredAdEvents() throws Exception {
        enableARA();

        // Only persisting the AdSelectionEntry. No events.
        mAdSelectionEntryDao.persistAdSelection(mDBAdSelection);

        // Mock server to report the event in addition to being registered by measurement.
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made since ad interactions are not"
                                        + " registered!");
                    }
                });

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input, true);

        // Confirm success was reported to caller.
        callback.assertSuccess();

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testImplFailsWhenEventDataExceedsMaxSize() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Mock server to report the event in addition to being registered by measurement.
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made when the input is not valid!");
                    }
                });

        // Call report event with input.
        char[] largePayload = new char[65 * 1024]; // 65KB
        ReportInteractionInput input =
                mInputBuilder.setInteractionData(new String(largePayload)).build();
        ReportEventTestCallback callback = callReportEvent(input, true);

        // Confirm failure was reported to caller.
        callback.assertErrorReceived(STATUS_INVALID_ARGUMENT, INTERACTION_DATA_SIZE_MAX_EXCEEDED);

        // Confirm failure caused by the input is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INVALID_ARGUMENT),
                        anyInt());
    }

    @Test
    public void testImplFailsWhenInteractionKeyExceedsMaxSize() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Mock server to report the event in addition to being registered by measurement.
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made when the input is not valid!");
                    }
                });

        // Instantiate flags with small max interaction data size.
        Flags flags =
                new ReportEventTestFlags() {
                    @Override
                    public long
                            getFledgeReportImpressionRegisteredAdBeaconsMaxInteractionKeySizeB() {
                        return 1;
                    }
                };

        // Re-initialize event reporter with new flags.
        mEventReporter = getReportAndRegisterEventFallbackImpl(flags);

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input, true);

        // Confirm failure was reported to caller.
        callback.assertErrorReceived(STATUS_INVALID_ARGUMENT, INTERACTION_KEY_SIZE_MAX_EXCEEDED);

        // Confirm failure caused by the input is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INVALID_ARGUMENT),
                        anyInt());
    }

    @Test
    public void testImpl_onlyReportsEvent_measurementKillSwitchEnabled() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Instantiate flags with kill switch turned on.
        Flags flags =
                new ReportEventTestFlags() {
                    @Override
                    public boolean getMeasurementApiRegisterSourceKillSwitch() {
                        return true;
                    }
                };

        // Re init interaction reporter with new flags
        mEventReporter = getReportAndRegisterEventFallbackImpl(flags);

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse(), new MockResponse()));

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input, true);

        // Verify registerEvent was never called.
        verify(mMeasurementServiceMock, never())
                .registerEvent(any(), any(), any(), anyBoolean(), any(), any(), any());

        // Confirm success was reported to caller.
        callback.assertSuccess();

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());
        assertThat(notifications)
                .containsExactly(
                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT,
                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT);
    }

    @Test
    public void testImpl_onlyReportsEvent_appIsNotInMeasurementAllowlisted() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Instantiate flags with no allow list.
        Flags flags =
                new ReportEventTestFlags() {
                    @Override
                    public String getMsmtApiAppAllowList() {
                        return AllowLists.ALLOW_NONE;
                    }
                };

        // Re-initialize event reporter with new flags.
        mEventReporter = getReportAndRegisterEventFallbackImpl(flags);

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse(), new MockResponse()));

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input, true);

        // Verify registerEvent was never called.
        verify(mMeasurementServiceMock, never())
                .registerEvent(any(), any(), any(), anyBoolean(), any(), any(), any());

        // Confirm success was reported to caller.
        callback.assertSuccess();

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());
        assertThat(notifications)
                .containsExactly(
                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT,
                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT);
    }

    @Test
    public void testImpl_onlyReportsEvent_measurementConsentRevoked() throws Exception {
        // Revoke consent for measurement.
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.MEASUREMENTS);
        doReturn(true)
                .when(
                        () ->
                                PermissionHelper.hasAttributionPermission(
                                        any(Context.class), anyString()));
        persistReportingArtifacts();

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse(), new MockResponse()));

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input, true);

        // Verify registerEvent was never called.
        verify(mMeasurementServiceMock, never())
                .registerEvent(any(), any(), any(), anyBoolean(), any(), any(), any());

        // Confirm success was reported to caller.
        callback.assertSuccess();

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());
        assertThat(notifications)
                .containsExactly(
                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT,
                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT);
    }

    @Test
    public void testImpl_onlyReportsEvent_noPermissionForARA() throws Exception {
        // Disable permission for ARA.
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.MEASUREMENTS);
        doReturn(false)
                .when(
                        () ->
                                PermissionHelper.hasAttributionPermission(
                                        any(Context.class), anyString()));
        persistReportingArtifacts();

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse(), new MockResponse()));

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input, true);

        // Verify registerEvent was never called.
        verify(mMeasurementServiceMock, never())
                .registerEvent(any(), any(), any(), anyBoolean(), any(), any(), any());

        // Confirm success was reported to caller.
        callback.assertSuccess();

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());
        assertThat(notifications)
                .containsExactly(
                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT,
                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT);
    }

    @Test
    public void testReportEventImplFailsWithUnknownAdSelectionId_serverAuctionEnabled()
            throws Exception {
        enableARA();
        persistReportingArtifacts();
        persistReportingArtifactsForServerAuction(AD_SELECTION_ID_2);

        // Mock server to handle fallback since measurement cannot report and register event.
        mMockWebServerRule.startMockWebServer(List.of(new MockResponse(), new MockResponse()));

        ReportInteractionInput inputParams =
                mInputBuilder.setAdSelectionId(AD_SELECTION_ID_2 + 1).build();

        Flags flags =
                new ReportEventTestFlags() {
                    @Override
                    public boolean getFledgeAuctionServerEnabledForReportEvent() {
                        return true;
                    }
                };

        // Re init interaction reporter
        mEventReporter = getReportAndRegisterEventFallbackImpl(flags);
        ReportEventTestCallback callback = callReportEvent(inputParams, true);

        callback.assertErrorReceived(STATUS_INVALID_ARGUMENT);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INVALID_ARGUMENT),
                        anyInt());
    }

    @Test
    public void test_idFoundInInitializationDb_registeredInteractionsReported() throws Exception {
        // Uses ArgumentCaptor to capture the logs in the tests.
        ArgumentCaptor<ReportInteractionApiCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(ReportInteractionApiCalledStats.class);

        enableARA();
        persistReportingArtifactsForServerAuction(AD_SELECTION_ID_2);
        Flags flags =
                new ReportEventTestFlags() {
                    @Override
                    public boolean getFledgeAuctionServerEnabledForReportEvent() {
                        return true;
                    }
                };

        // Re init interaction reporter
        mEventReporter = getReportAndRegisterEventFallbackImpl(flags);

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse(), new MockResponse()));

        ReportInteractionInput inputParams =
                mInputBuilder
                        .setAdSelectionId(AD_SELECTION_ID_2)
                        .setCallerPackageName(DataHandlersFixture.TEST_PACKAGE_NAME_1)
                        .build();

        // Count down callback + log interaction.
        ReportEventTestCallback callback = callReportEvent(inputParams, true);

        callback.assertSuccess();

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(DataHandlersFixture.TEST_PACKAGE_NAME_1),
                        eq(STATUS_SUCCESS),
                        anyInt());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications)
                .containsExactly(
                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT,
                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT);

        // Verify registerEvent was called with exact parameters.
        verifyRegisterEvent(SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT, inputParams);
        verifyRegisterEvent(BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT, inputParams);

        // Verifies ReportInteractionApiCalledStats get the correct values.
        Mockito.verify(mAdServicesLoggerMock, times(1))
                .logReportInteractionApiCalledStats(argumentCaptor.capture());
        ReportInteractionApiCalledStats stats = argumentCaptor.getValue();
        assertThat(stats.getBeaconReportingDestinationType())
                .isEqualTo(SELLER_AND_BUYER_DESTINATION);
        assertThat(stats.getNumMatchingUris()).isEqualTo(2);
    }

    private void persistReportingArtifactsForServerAuction(long adSelectionId) {
        RegisteredAdInteraction buyerClick =
                RegisteredAdInteraction.builder()
                        .setInteractionKey(CLICK_EVENT)
                        .setInteractionReportingUri(
                                mMockWebServerRule.uriForPath(
                                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT))
                        .build();

        RegisteredAdInteraction sellerClick =
                RegisteredAdInteraction.builder()
                        .setInteractionKey(CLICK_EVENT)
                        .setInteractionReportingUri(
                                mMockWebServerRule.uriForPath(
                                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT))
                        .build();

        mAdSelectionEntryDao.persistAdSelectionInitialization(
                adSelectionId, DataHandlersFixture.AD_SELECTION_INITIALIZATION_1);
        mAdSelectionEntryDao.safelyInsertRegisteredAdInteractionsForDestination(
                adSelectionId,
                BUYER_DESTINATION,
                List.of(buyerClick),
                mMaxRegisteredAdBeaconsTotalCount,
                mMaxRegisteredAdBeaconsPerDestination);

        mAdSelectionEntryDao.safelyInsertRegisteredAdInteractionsForDestination(
                adSelectionId,
                SELLER_DESTINATION,
                List.of(sellerClick),
                mMaxRegisteredAdBeaconsTotalCount,
                mMaxRegisteredAdBeaconsPerDestination);
    }

    private void persistReportingArtifacts() {
        mAdSelectionEntryDao.persistAdSelection(mDBAdSelection);
        mAdSelectionEntryDao.safelyInsertRegisteredAdInteractions(
                AD_SELECTION_ID,
                List.of(mDBRegisteredAdInteractionBuyerClick),
                mMaxRegisteredAdBeaconsTotalCount,
                mMaxRegisteredAdBeaconsPerDestination,
                BUYER_DESTINATION);

        mAdSelectionEntryDao.safelyInsertRegisteredAdInteractions(
                AD_SELECTION_ID,
                List.of(mDBRegisteredAdInteractionSellerClick),
                mMaxRegisteredAdBeaconsTotalCount,
                mMaxRegisteredAdBeaconsPerDestination,
                SELLER_DESTINATION);
    }

    private void verifyRegisterEvent(String path, ReportInteractionInput input) {
        verify(mMeasurementServiceMock)
                .registerEvent(
                        mMockWebServerRule.uriForPath(path),
                        input.getCallerPackageName(),
                        input.getCallerSdkName(),
                        input.getAdId() != null,
                        mEventData,
                        input.getInputEvent(),
                        input.getAdId());
    }

    private void enableARA() {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.MEASUREMENTS);
        doReturn(true)
                .when(
                        () ->
                                PermissionHelper.hasAttributionPermission(
                                        any(Context.class), anyString()));
    }

    private ReportAndRegisterEventFallbackImpl getReportAndRegisterEventFallbackImpl(Flags flags) {
        return new ReportAndRegisterEventFallbackImpl(
                mAdSelectionEntryDao,
                mHttpClient,
                mLightweightExecutorService,
                mBackgroundExecutorService,
                mAdServicesLoggerMock,
                flags,
                mMockDebugFlags,
                mAdSelectionServiceFilterMock,
                MY_UID,
                mFledgeAuthorizationFilterMock,
                DevContext.createForDevOptionsDisabled(),
                mMeasurementServiceMock,
                mConsentManagerMock,
                mMockContext,
                false);
    }

    private ReportEventTestCallback callReportEvent(ReportInteractionInput inputParams)
            throws Exception {
        return callReportEvent(inputParams, SHOULD_NOT_COUNT_LOG);
    }

    /**
     * @param shouldCountLog if true, adds a latch to the log interaction as well.
     */
    private ReportEventTestCallback callReportEvent(
            ReportInteractionInput inputParams, boolean shouldCountLog) throws Exception {
        SyncCallbackSettings settings =
                SyncCallbackFactory.newSettingsBuilder()
                        .setExpectedNumberCalls(shouldCountLog ? 2 : 1)
                        .build();
        // Counted down in callback
        if (shouldCountLog) {
            // Wait for the logging call, which happens after the callback
            AnswerSyncCallback<Void> countDownAnswer = AnswerSyncCallback.forVoidAnswers(settings);
            doAnswer(countDownAnswer)
                    .when(mAdServicesLoggerMock)
                    .logFledgeApiCallStats(anyInt(), anyString(), anyInt(), anyInt());
        }

        ReportEventTestCallback callback = new ReportEventTestCallback(settings);
        mEventReporter.reportInteraction(inputParams, callback);
        callback.assertCalled();

        return callback;
    }

    // Use a SyncCallback to block until register event happens.
    private AnswerSyncCallback<Void> syncRegisterEvent(String path, ReportInteractionInput input) {
        AnswerSyncCallback<Void> callback = AnswerSyncCallback.forSingleVoidAnswer();
        doAnswer(callback)
                .when(mMeasurementServiceMock)
                .registerEvent(
                        mMockWebServerRule.uriForPath(path),
                        input.getCallerPackageName(),
                        input.getCallerSdkName(),
                        input.getAdId() != null,
                        mEventData,
                        input.getInputEvent(),
                        input.getAdId());

        return callback;
    }



    private static class ReportEventTestFlags implements Flags {
        @Override
        public boolean getMeasurementApiRegisterSourceKillSwitch() {
            return false;
        }

        @Override
        public String getMsmtApiAppAllowList() {
            return AllowLists.ALLOW_ALL;
        }

        @Override
        public boolean getGaUxFeatureEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeBeaconReportingMetricsEnabled() {
            return true;
        }

        @Override
        public boolean getEnforceForegroundStatusForFledgeReportInteraction() {
            return true;
        }
    }
}
