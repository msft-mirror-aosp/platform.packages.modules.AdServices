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

package com.android.adservices.service.adselection;

import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENABLED_FOR_REPORT_IMPRESSION;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_REPORT_IMPRESSION_MAX_INTERACTION_REPORTING_URI_SIZE_B;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_REPORT_IMPRESSION_REGISTERED_AD_BEACONS_MAX_INTERACTION_KEY_SIZE_B;
import static com.android.adservices.service.common.AppImportanceFilter.WrongCallingApplicationStateException;
import static com.android.adservices.service.common.FledgeAuthorizationFilter.AdTechNotAllowedException;
import static com.android.adservices.service.common.FledgeAuthorizationFilter.CallerMismatchException;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_ERROR_FETCHING_BUYER_SCRIPT_FROM_URI;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_HTTP_GET_REPORTING_URL_FAILED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_INTERACTION_KEY_SIZE_EXCEEDS_MAX;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_INTERACTION_REPORTING_URI_SIZE_EXCEEDS_MAX;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_INVALID_BUYER_REPORTING_URI;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_INVALID_INTERACTION_URI;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_INVALID_JSON_BUYER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_INVALID_JSON_SELLER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_INVALID_SELLER_REPORTING_URI;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_NOTIFY_FAILURE_FILTER_EXCEPTION_BACKGROUND_CALLER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_NOTIFY_FAILURE_FILTER_EXCEPTION_CALLER_NOT_ALLOWED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_NOTIFY_FAILURE_FILTER_EXCEPTION_RATE_LIMIT_REACHED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_NOTIFY_FAILURE_FILTER_EXCEPTION_UNAUTHORIZED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_NOTIFY_FAILURE_INTERNAL_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_NOTIFY_FAILURE_INVALID_ARGUMENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_NOTIFY_FAILURE_TO_CALLER_FAILED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_NOTIFY_SUCCESS_TO_CALLER_FAILED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__REPORT_IMPRESSION;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.immediateVoidFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static java.time.temporal.ChronoUnit.SECONDS;

import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.ReportImpressionCallback;
import android.adservices.adselection.ReportImpressionInput;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.net.Uri;
import android.os.IBinder;
import android.os.LimitExceededException;
import android.os.Process;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.WebViewSupportUtil;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.datahandlers.AdSelectionInitialization;
import com.android.adservices.data.adselection.datahandlers.ReportingComputationData;
import com.android.adservices.data.adselection.datahandlers.ReportingData;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.FrequencyCapAdDataValidatorNoOpImpl;
import com.android.adservices.service.common.NoOpRetryStrategyImpl;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.ReportImpressionExecutionLogger;
import com.android.adservices.shared.testing.SupportedByConditionRule;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.SetFlagFalse;
import com.android.adservices.shared.testing.annotations.SetFlagTrue;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;
import com.android.adservices.shared.testing.concurrency.FailableOnResultSyncCallback;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableMap;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

import java.time.Instant;
import java.util.concurrent.ExecutionException;

@SpyStatic(FlagsFactory.class)
@SpyStatic(DebugFlags.class)
@SetFlagFalse(KEY_FLEDGE_AUCTION_SERVER_KILL_SWITCH)
@SetFlagTrue(KEY_FLEDGE_AUCTION_SERVER_ENABLED)
@SetFlagTrue(KEY_FLEDGE_AUCTION_SERVER_ENABLED_FOR_REPORT_IMPRESSION)
@SetErrorLogUtilDefaultParams(ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__REPORT_IMPRESSION)
@RequiresSdkLevelAtLeastT(reason = "test has requirements on WebView version.")
public final class ImpressionReporterTest extends AdServicesExtendedMockitoTestCase {
    private static final long AD_SELECTION_ID = 100;
    private static final int LOGGING_TIMEOUT_MS = 5_000;
    private static final ReportImpressionInput DEFAULT_INPUT =
            new ReportImpressionInput.Builder()
                    .setAdSelectionId(AD_SELECTION_ID)
                    .setAdSelectionConfig(
                            AdSelectionConfigFixture.anAdSelectionConfig(
                                    CommonFixture.VALID_BUYER_1))
                    .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                    .build();
    private static final String REPORT_RESULT_TEMPLATE =
            "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) { "
                    + "return {\"status\": 0, \"results\": {\"signals_for_buyer\": \"{}\", "
                    + "\"reporting_uri\": \"%s\"}};}";
    private static final String CALL_REGISTER_AD_BEACON_INVALID =
            "registerAdBeacon({\"key_a\": \"reporting_uri_a\", \"b\": \"reporting_uri_b\", \"c\":"
                    + " \"c\"});";
    private static final String VALID_REPORTING_URI = "https://test.com/uri";

    @Rule(order = 11)
    public final SupportedByConditionRule webViewSupportsJSSandbox =
            WebViewSupportUtil.createJSSandboxAvailableRule(
                    ApplicationProvider.getApplicationContext());

    @Mock private AdServicesHttpsClient mMockAdServicesHttpsClient;
    @Mock private AdServicesLogger mMockAdServicesLogger;
    @Mock private AdSelectionServiceFilter mMockAdSelectionServiceFilter;
    @Mock private FledgeAuthorizationFilter mMockFledgeAuthorizationFilter;
    @Mock private ReportImpressionExecutionLogger mMockReportImpressionExecutionLogger;

    private AdSelectionEntryDao mAdSelectionEntryDao;
    private CustomAudienceDao mCustomAudienceDao;

    @Before
    public void setup() {
        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(sContext, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(sContext, CustomAudienceDatabase.class)
                        .addTypeConverter(
                                new DBCustomAudience.Converters(
                                        /* frequencyCapFilteringEnabled= */ true,
                                        /* appInstallFilteringEnabled= */ true,
                                        /* adRenderIdEnabled= */ true))
                        .build()
                        .customAudienceDao();
        when(mMockAdServicesHttpsClient.getAndReadNothing(any(), any()))
                .thenReturn(immediateVoidFuture());
    }

    @Test
    public void testReportImpression_unifiedTables_persistedBuyerUriEmpty_skipsBuyer()
            throws Exception {
        setupAdSelectionEntryDaoByBuyerReportingUri(Uri.EMPTY);
        ImpressionReporter reporter = initializeReporterWithInMemoryDao();
        SyncReportImpressionCallback callback = new SyncReportImpressionCallback();

        reporter.reportImpression(DEFAULT_INPUT, callback);
        callback.assertResultReceived();

        verify(mMockAdServicesLogger, timeout(LOGGING_TIMEOUT_MS))
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        anyString(),
                        eq(STATUS_SUCCESS),
                        anyInt());
        verify(mMockAdServicesHttpsClient).getAndReadNothing(any(), any());
    }

    @Test
    public void testReportImpression_unifiedTables_persistedBuyerUriMismatched_skipsBuyer()
            throws Exception {
        setupAdSelectionEntryDaoByBuyerReportingUri(Uri.parse("this.is.not.a.valid.enrolled.uri"));
        ImpressionReporter reporter = initializeReporterWithInMemoryDao();
        SyncReportImpressionCallback callback = new SyncReportImpressionCallback();

        reporter.reportImpression(DEFAULT_INPUT, callback);
        callback.assertResultReceived();

        verify(mMockAdServicesLogger, timeout(LOGGING_TIMEOUT_MS))
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        anyString(),
                        eq(STATUS_SUCCESS),
                        anyInt());
        verify(mMockAdServicesHttpsClient).getAndReadNothing(any(), any());
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_NOTIFY_SUCCESS_TO_CALLER_FAILED,
            throwable = RemoteException.class)
    public void testReportImpression_notifySuccessToCallerFailed_logCallerFailedCel()
            throws Exception {
        setupAdSelectionEntryDaoByBuyerReportingUri(Uri.EMPTY);
        ImpressionReporter reporter = initializeReporterWithInMemoryDao();

        reporter.reportImpression(DEFAULT_INPUT, new FailToReportCallback());
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_NOTIFY_FAILURE_FILTER_EXCEPTION_BACKGROUND_CALLER,
            throwable = FilterException.class)
    public void testReportImpression_notifyFailureToCaller_logBackgroundCallerCel()
            throws Exception {
        FilterException testException =
                new FilterException(new WrongCallingApplicationStateException());
        doThrow(testException)
                .when(mMockAdSelectionServiceFilter)
                .filterRequest(
                        any(),
                        any(),
                        anyBoolean(),
                        anyBoolean(),
                        anyBoolean(),
                        anyInt(),
                        anyInt(),
                        any(),
                        any());
        setupAdSelectionEntryDaoByBuyerReportingUri(Uri.EMPTY);
        ImpressionReporter reporter = initializeReporterWithInMemoryDao();

        reporter.reportImpression(DEFAULT_INPUT, new SyncReportImpressionCallback());
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_NOTIFY_FAILURE_FILTER_EXCEPTION_CALLER_NOT_ALLOWED,
            throwable = FilterException.class)
    public void testReportImpression_notifyFailureToCaller_logCallerNotAllowedCel()
            throws Exception {
        FilterException testException = new FilterException(new AdTechNotAllowedException());
        doThrow(testException)
                .when(mMockAdSelectionServiceFilter)
                .filterRequest(
                        any(),
                        any(),
                        anyBoolean(),
                        anyBoolean(),
                        anyBoolean(),
                        anyInt(),
                        anyInt(),
                        any(),
                        any());
        setupAdSelectionEntryDaoByBuyerReportingUri(Uri.EMPTY);
        ImpressionReporter reporter = initializeReporterWithInMemoryDao();

        reporter.reportImpression(DEFAULT_INPUT, new SyncReportImpressionCallback());
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_NOTIFY_FAILURE_FILTER_EXCEPTION_RATE_LIMIT_REACHED,
            throwable = FilterException.class)
    public void testReportImpression_notifyFailureToCaller_logRateLimitReachedCel()
            throws Exception {
        FilterException testException = new FilterException(new LimitExceededException());
        doThrow(testException)
                .when(mMockAdSelectionServiceFilter)
                .filterRequest(
                        any(),
                        any(),
                        anyBoolean(),
                        anyBoolean(),
                        anyBoolean(),
                        anyInt(),
                        anyInt(),
                        any(),
                        any());
        setupAdSelectionEntryDaoByBuyerReportingUri(Uri.EMPTY);
        ImpressionReporter reporter = initializeReporterWithInMemoryDao();

        reporter.reportImpression(DEFAULT_INPUT, new SyncReportImpressionCallback());
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_NOTIFY_FAILURE_FILTER_EXCEPTION_UNAUTHORIZED,
            throwable = FilterException.class)
    public void testReportImpression_notifyFailureToCaller_logUnauthorizedCel() throws Exception {
        FilterException testException = new FilterException(new CallerMismatchException());
        doThrow(testException)
                .when(mMockAdSelectionServiceFilter)
                .filterRequest(
                        any(),
                        any(),
                        anyBoolean(),
                        anyBoolean(),
                        anyBoolean(),
                        anyInt(),
                        anyInt(),
                        any(),
                        any());
        setupAdSelectionEntryDaoByBuyerReportingUri(Uri.EMPTY);
        ImpressionReporter reporter = initializeReporterWithInMemoryDao();

        reporter.reportImpression(DEFAULT_INPUT, new SyncReportImpressionCallback());
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_NOTIFY_FAILURE_INTERNAL_ERROR,
            throwable = IllegalStateException.class)
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_NOTIFY_FAILURE_TO_CALLER_FAILED,
            throwable = RemoteException.class)
    public void testReportImpression_notifyFailureToCallerFailed_logInternalErrorCels()
            throws Exception {
        IllegalStateException testException = new IllegalStateException();
        doThrow(testException)
                .when(mMockAdSelectionServiceFilter)
                .filterRequest(
                        any(),
                        any(),
                        anyBoolean(),
                        anyBoolean(),
                        anyBoolean(),
                        anyInt(),
                        anyInt(),
                        any(),
                        any());
        setupAdSelectionEntryDaoByBuyerReportingUri(Uri.EMPTY);
        ImpressionReporter reporter = initializeReporterWithInMemoryDao();

        reporter.reportImpression(DEFAULT_INPUT, new FailToReportCallback());
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_INVALID_JSON_SELLER,
            throwable = JSONException.class)
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_NOTIFY_FAILURE_INVALID_ARGUMENT,
            throwable = IllegalArgumentException.class)
    public void testReportImpression_invalidSellerJson_logCels() throws Exception {
        mockFetchPayloadWithInvalidUriAndLogging();
        ImpressionReporter reporter =
                initializeReporterByAdSelectionEntryDao(
                        mockAdSelectionEntryDao(
                                AdSelectionSignals.EMPTY,
                                AdSelectionSignals.fromString("{invalid")));

        reporter.reportImpression(DEFAULT_INPUT, new SyncReportImpressionCallback());
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_INVALID_JSON_BUYER,
            throwable = JSONException.class)
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_NOTIFY_FAILURE_INVALID_ARGUMENT,
            throwable = IllegalArgumentException.class)
    public void testReportImpression_invalidBuyerJson_logCels() throws Exception {
        mockFetchPayloadWithInvalidUriAndLogging();
        ImpressionReporter reporter =
                initializeReporterByAdSelectionEntryDao(
                        mockAdSelectionEntryDao(
                                AdSelectionSignals.fromString("{invalid"),
                                AdSelectionSignals.EMPTY));

        reporter.reportImpression(DEFAULT_INPUT, new SyncReportImpressionCallback());
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_ERROR_FETCHING_BUYER_SCRIPT_FROM_URI,
            throwable = ExecutionException.class)
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_NOTIFY_FAILURE_INTERNAL_ERROR,
            throwable = IllegalStateException.class)
    public void testReportImpression_failedToFetchBuyerScript_logCels() throws Exception {
        RuntimeException testException = new RuntimeException();
        when(mMockAdServicesHttpsClient.fetchPayloadWithLogging(any(), any()))
                .thenReturn(
                        immediateFuture(
                                AdServicesHttpClientResponse.create(
                                        String.format(REPORT_RESULT_TEMPLATE, VALID_REPORTING_URI),
                                        ImmutableMap.of())))
                .thenThrow(testException);
        ImpressionReporter reporter =
                initializeReporterByAdSelectionEntryDao(
                        mockAdSelectionEntryDaoWithBuyerDecisionLogic(
                                AdSelectionSignals.EMPTY, AdSelectionSignals.EMPTY, ""));

        reporter.reportImpression(DEFAULT_INPUT, new SyncReportImpressionCallback());
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_INVALID_SELLER_REPORTING_URI,
            throwable = IllegalArgumentException.class)
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_INVALID_BUYER_REPORTING_URI,
            throwable = IllegalArgumentException.class)
    public void testReportImpression_invalidReportingUri_logCels() throws Exception {
        mockFetchPayloadWithInvalidUriAndLogging();
        ImpressionReporter reporter =
                initializeReporterByAdSelectionEntryDao(
                        mockAdSelectionEntryDaoWithBuyerReportingUri(
                                AdSelectionSignals.EMPTY, AdSelectionSignals.EMPTY, "invalid uri"));

        reporter.reportImpression(DEFAULT_INPUT, new SyncReportImpressionCallback());
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_HTTP_GET_REPORTING_URL_FAILED,
            throwable = IllegalStateException.class,
            times = 2)
    public void testReportImpression_getReportingUrlFailed_logCel() throws Exception {
        mockFetchPayloadWithLoggingResponseBody(
                String.format(REPORT_RESULT_TEMPLATE, VALID_REPORTING_URI));
        IllegalStateException testException = new IllegalStateException();
        when(mMockAdServicesHttpsClient.getAndReadNothing(any(), any()))
                .thenReturn(immediateFailedFuture(testException));
        ImpressionReporter reporter =
                initializeReporterByAdSelectionEntryDao(
                        mockAdSelectionEntryDao(
                                AdSelectionSignals.EMPTY, AdSelectionSignals.EMPTY));

        reporter.reportImpression(DEFAULT_INPUT, new SyncReportImpressionCallback());
    }

    @Test
    @SetFlagTrue(KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED)
    @SetIntegerFlag(
            name = KEY_FLEDGE_REPORT_IMPRESSION_REGISTERED_AD_BEACONS_MAX_INTERACTION_KEY_SIZE_B,
            value = 1)
    @SetIntegerFlag(
            name = KEY_FLEDGE_REPORT_IMPRESSION_MAX_INTERACTION_REPORTING_URI_SIZE_B,
            value = 1)
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_INVALID_INTERACTION_URI,
            throwable = IllegalArgumentException.class)
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_INTERACTION_KEY_SIZE_EXCEEDS_MAX)
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IMPRESSION_REPORTER_INTERACTION_REPORTING_URI_SIZE_EXCEEDS_MAX)
    public void testReportImpression_getReportInteractionError_logCels() throws Exception {
        mockFetchPayloadWithLoggingResponseBody(
                String.format(
                        "%s\n%s",
                        CALL_REGISTER_AD_BEACON_INVALID,
                        String.format(REPORT_RESULT_TEMPLATE, VALID_REPORTING_URI)));
        ImpressionReporter reporter =
                initializeReporterByAdSelectionEntryDao(
                        mockAdSelectionEntryDao(
                                AdSelectionSignals.EMPTY, AdSelectionSignals.EMPTY));

        reporter.reportImpression(DEFAULT_INPUT, new SyncReportImpressionCallback());
    }

    private void mockFetchPayloadWithLoggingResponseBody(String responseBody) {
        when(mMockAdServicesHttpsClient.fetchPayloadWithLogging(any(), any()))
                .thenReturn(
                        immediateFuture(
                                AdServicesHttpClientResponse.create(
                                        responseBody, ImmutableMap.of())));
    }

    private void mockFetchPayloadWithInvalidUriAndLogging() {
        mockFetchPayloadWithLoggingResponseBody(
                String.format(REPORT_RESULT_TEMPLATE, "invalid uri"));
    }

    private void setupAdSelectionEntryDaoByBuyerReportingUri(Uri uri) {
        AdSelectionInitialization initialization =
                AdSelectionInitialization.builder()
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .setCreationInstant(CommonFixture.FIXED_NOW)
                        .setSeller(CommonFixture.VALID_BUYER_1)
                        .build();
        ReportingData reportingData =
                ReportingData.builder()
                        .setBuyerWinReportingUri(uri)
                        .setSellerWinReportingUri(
                                CommonFixture.getUriWithValidSubdomain(
                                        CommonFixture.VALID_BUYER_1.toString(), "/report/seller"))
                        .build();
        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, initialization);
        mAdSelectionEntryDao.persistReportingData(AD_SELECTION_ID, reportingData);
    }

    private ImpressionReporter initializeReporterWithInMemoryDao() {
        return initializeReporterByAdSelectionEntryDao(mAdSelectionEntryDao);
    }

    private ImpressionReporter initializeReporterByAdSelectionEntryDao(
            AdSelectionEntryDao adSelectionEntryDao) {
        return new ImpressionReporter(
                AdServicesExecutors.getLightWeightExecutor(),
                AdServicesExecutors.getBackgroundExecutor(),
                AdServicesExecutors.getScheduler(),
                adSelectionEntryDao,
                mCustomAudienceDao,
                mMockAdServicesHttpsClient,
                DevContext.createForDevOptionsDisabled(),
                mMockAdServicesLogger,
                mFakeFlags,
                mFakeDebugFlags,
                mMockAdSelectionServiceFilter,
                mMockFledgeAuthorizationFilter,
                new FrequencyCapAdDataValidatorNoOpImpl(),
                Process.myUid(),
                new NoOpRetryStrategyImpl(),
                /* shouldUseUnifiedTables= */ true,
                mMockReportImpressionExecutionLogger);
    }

    private AdSelectionEntryDao mockAdSelectionEntryDao(
            AdSelectionSignals buyerContextualSignals, AdSelectionSignals sellerContextualSignals) {
        return mockAdSelectionEntryDaoWithBuyerReportingUri(
                buyerContextualSignals, sellerContextualSignals, VALID_REPORTING_URI);
    }

    private AdSelectionEntryDao mockAdSelectionEntryDaoWithBuyerReportingUri(
            AdSelectionSignals buyerContextualSignals,
            AdSelectionSignals sellerContextualSignals,
            String buyerUri) {
        return mockAdSelectionEntryDaoWithBuyerDecisionLogic(
                buyerContextualSignals,
                sellerContextualSignals,
                String.format(
                        "function reportWin(ad_selection_signals, per_buyer_signals, "
                                + "signals_for_buyer, contextual_signals, "
                                + "custom_audience_signals) { return {\"status\": 0, \"results\":"
                                + " {\"signals_for_buyer\": \"{}\", \"reporting_uri\": \"%s\"}};}",
                        buyerUri));
    }

    private AdSelectionEntryDao mockAdSelectionEntryDaoWithBuyerDecisionLogic(
            AdSelectionSignals buyerContextualSignals,
            AdSelectionSignals sellerContextualSignals,
            String buyerDecisionLogic) {

        // The ReportingComputationData and reporting Uris can not live together, while Uris are
        // required for inserting ReportingData through the actual DAO.
        // Use a mock to serve the ReportingComputationData.
        AdSelectionEntryDao mockDao = mock(AdSelectionEntryDao.class);
        doReturn(true).when(mockDao).doesAdSelectionIdAndCallerPackageNameExists(anyLong(), any());
        doReturn(
                        ReportingData.builder()
                                .setReportingComputationData(
                                        ReportingComputationData.builder()
                                                .setBuyerDecisionLogicJs(buyerDecisionLogic)
                                                .setBuyerDecisionLogicUri(
                                                        Uri.parse("buyer decision uri"))
                                                .setBuyerContextualSignals(buyerContextualSignals)
                                                .setSellerContextualSignals(sellerContextualSignals)
                                                .setWinningCustomAudienceSignals(
                                                        new CustomAudienceSignals(
                                                                "test owner",
                                                                AdTechIdentifier.fromString(
                                                                        "test.com"),
                                                                "test name",
                                                                Instant.now(),
                                                                Instant.now().plus(3600, SECONDS),
                                                                AdSelectionSignals.fromString(
                                                                        "test signals")))
                                                .setWinningRenderUri(
                                                        Uri.parse("winning render URI"))
                                                .setWinningBid(10.0)
                                                .build())
                                .build())
                .when(mockDao)
                .getReportingDataForId(anyLong(), anyBoolean());
        return mockDao;
    }

    public void testReportImpression_componentSellerEnabled_makesReportingCall() throws Exception {
        when(mMockAdServicesHttpsClient.getAndReadNothing(any(), any()))
                .thenReturn(immediateVoidFuture());
        when(mMockAdServicesHttpsClient.getAndReadNothing(any(), any()))
                .thenReturn(immediateVoidFuture());
        AdSelectionInitialization initialization =
                AdSelectionInitialization.builder()
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .setCreationInstant(CommonFixture.FIXED_NOW)
                        .setSeller(CommonFixture.VALID_BUYER_1)
                        .build();
        ReportingData reportingData =
                ReportingData.builder()
                        .setBuyerWinReportingUri(Uri.EMPTY)
                        .setSellerWinReportingUri(
                                CommonFixture.getUriWithValidSubdomain(
                                        CommonFixture.VALID_BUYER_1.toString(), "/report/seller"))
                        .setComponentSellerWinReportingUri(
                                CommonFixture.getUriWithValidSubdomain(
                                        CommonFixture.VALID_BUYER_2.toString(),
                                        "/report/componentSeller"))
                        .build();
        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, initialization);
        mAdSelectionEntryDao.persistReportingData(AD_SELECTION_ID, reportingData);
        Flags flagsWithAuctionServerAndComponentSellerEnabled =
                new Flags() {
                    @Override
                    public boolean getFledgeAuctionServerKillSwitch() {
                        return false;
                    }

                    @Override
                    public boolean getFledgeAuctionServerEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAuctionServerEnabledForReportImpression() {
                        return true;
                    }

                    @Override
                    public boolean getEnableReportEventForComponentSeller() {
                        return true;
                    }
                };
        ImpressionReporter reporter =
                new ImpressionReporter(
                        AdServicesExecutors.getLightWeightExecutor(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getScheduler(),
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mMockAdServicesHttpsClient,
                        DevContext.createForDevOptionsDisabled(),
                        mMockAdServicesLogger,
                        flagsWithAuctionServerAndComponentSellerEnabled,
                        mFakeDebugFlags,
                        mMockAdSelectionServiceFilter,
                        mMockFledgeAuthorizationFilter,
                        new FrequencyCapAdDataValidatorNoOpImpl(),
                        Process.myUid(),
                        new NoOpRetryStrategyImpl(),
                        /* shouldUseUnifiedTables= */ true,
                        mMockReportImpressionExecutionLogger);
        SyncReportImpressionCallback callback = new SyncReportImpressionCallback();
        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(
                                AdSelectionConfigFixture.anAdSelectionConfig(
                                        CommonFixture.VALID_BUYER_1))
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        reporter.reportImpression(input, callback);
        callback.assertResultReceived();

        verify(mMockAdServicesLogger, timeout(LOGGING_TIMEOUT_MS))
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        anyString(),
                        eq(STATUS_SUCCESS),
                        anyInt());
        // verify 2 http calls were made. 1 for seller and 1 for component seller
        verify(mMockAdServicesHttpsClient, times(2)).getAndReadNothing(any(), any());
    }

    @Test
    public void testReportImpression_componentSellerDisabled_doesNotMakeReportingCall()
            throws Exception {
        when(mMockAdServicesHttpsClient.getAndReadNothing(any(), any()))
                .thenReturn(immediateVoidFuture());
        AdSelectionInitialization initialization =
                AdSelectionInitialization.builder()
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .setCreationInstant(CommonFixture.FIXED_NOW)
                        .setSeller(CommonFixture.VALID_BUYER_1)
                        .build();
        ReportingData reportingData =
                ReportingData.builder()
                        .setBuyerWinReportingUri(Uri.EMPTY)
                        .setSellerWinReportingUri(
                                CommonFixture.getUriWithValidSubdomain(
                                        CommonFixture.VALID_BUYER_1.toString(), "/report/seller"))
                        .setComponentSellerWinReportingUri(
                                CommonFixture.getUriWithValidSubdomain(
                                        CommonFixture.VALID_BUYER_2.toString(),
                                        "/report/componentSeller"))
                        .build();
        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, initialization);
        mAdSelectionEntryDao.persistReportingData(AD_SELECTION_ID, reportingData);
        Flags flagsWithAuctionServerAndComponentSellerEnabled =
                new Flags() {
                    @Override
                    public boolean getFledgeAuctionServerKillSwitch() {
                        return false;
                    }

                    @Override
                    public boolean getFledgeAuctionServerEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAuctionServerEnabledForReportImpression() {
                        return true;
                    }

                    @Override
                    public boolean getEnableReportEventForComponentSeller() {
                        return true;
                    }
                };
        ImpressionReporter reporter =
                new ImpressionReporter(
                        AdServicesExecutors.getLightWeightExecutor(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getScheduler(),
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mMockAdServicesHttpsClient,
                        DevContext.createForDevOptionsDisabled(),
                        mMockAdServicesLogger,
                        flagsWithAuctionServerAndComponentSellerEnabled,
                        mFakeDebugFlags,
                        mMockAdSelectionServiceFilter,
                        mMockFledgeAuthorizationFilter,
                        new FrequencyCapAdDataValidatorNoOpImpl(),
                        Process.myUid(),
                        new NoOpRetryStrategyImpl(),
                        /* shouldUseUnifiedTables= */ true,
                        mMockReportImpressionExecutionLogger);
        SyncReportImpressionCallback callback = new SyncReportImpressionCallback();
        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(
                                AdSelectionConfigFixture.anAdSelectionConfig(
                                        CommonFixture.VALID_BUYER_1))
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        reporter.reportImpression(input, callback);
        callback.assertResultReceived();

        verify(mMockAdServicesLogger, timeout(LOGGING_TIMEOUT_MS))
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        anyString(),
                        eq(STATUS_SUCCESS),
                        anyInt());
        // verify 2 http calls were made. 1 for seller and 1 for component seller
        verify(mMockAdServicesHttpsClient, times(2)).getAndReadNothing(any(), any());
    }

    private static final class SyncReportImpressionCallback
            extends FailableOnResultSyncCallback<Object, FledgeErrorResponse>
            implements ReportImpressionCallback {
        @Override
        public void onSuccess() throws RemoteException {
            injectResult(null);
        }
    }

    private static final class FailToReportCallback implements ReportImpressionCallback {
        @Override
        public IBinder asBinder() {
            throw new IllegalStateException("not implemented");
        }

        @Override
        public void onSuccess() throws RemoteException {
            throw new RemoteException();
        }

        @Override
        public void onFailure(FledgeErrorResponse var1) throws RemoteException {
            throw new RemoteException();
        }
    }
}
