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

package com.android.adservices.service.stats;

import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static com.android.adservices.service.stats.AdServicesEncryptionKeyDbTransactionEndedStats.DbTransactionStatus.INSERT_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesEncryptionKeyDbTransactionEndedStats.DbTransactionType.WRITE_TRANSACTION_TYPE;
import static com.android.adservices.service.stats.AdServicesEncryptionKeyDbTransactionEndedStats.MethodName.INSERT_KEY;
import static com.android.adservices.service.stats.AdServicesEncryptionKeyFetchedStats.FetchJobType.ENCRYPTION_KEY_DAILY_FETCH_JOB;
import static com.android.adservices.service.stats.AdServicesEncryptionKeyFetchedStats.FetchStatus.IO_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__TARGETING;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_ATTRIBUTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_DELAYED_SOURCE_REGISTRATION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_NOTIFY_REGISTRATION_TO_ODP;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_PROCESS_ODP_REGISTRATION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__FAILURE_TYPE__UNKNOWN_REGISTRATION_FAILURE_TYPE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__EVENT_SOURCE_TYPE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__SUCCESS_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__SURFACE_TYPE__APP_REGISTRATION_SURFACE_TYPE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__FAILURE_TYPE__UNKNOWN_REPORT_UPLOAD_FAILURE_TYPE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__RESPONSE_CODE__SUCCESS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__TYPE__EVENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__UPLOAD_METHOD__FALLBACK_REPORT_UPLOAD_METHOD;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_WIPEOUT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MESUREMENT_REPORTS_UPLOADED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_OUT_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.ENCODING_FETCH_STATUS_SUCCESS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JSON_PROCESSING_STATUS_TOO_BIG;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_OUTPUT_NON_ZERO_RESULT;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_LARGE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_MEDIUM;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_SMALL;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.WINNER_TYPE_PAS_WINNER;
import static com.android.adservices.service.stats.BackgroundFetchProcessReportedStatsTest.LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.BackgroundFetchProcessReportedStatsTest.NUM_OF_ELIGIBLE_TO_UPDATE_CAS;
import static com.android.adservices.service.stats.BackgroundFetchProcessReportedStatsTest.RESULT_CODE;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.BUYER_DECISION_LOGIC_SCRIPT_TYPE;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.FETCHED_BUYER_DECISION_LOGIC_SCRIPT_SIZE_IN_BYTES;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.FETCHED_TRUSTED_BIDDING_SIGNALS_DATA_SIZE_IN_BYTES;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.GENERATE_BIDS_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.GENERATE_BID_BUYER_ADDITIONAL_SIGNALS_CONTAINED_DATA_VERSION;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.GENERATE_BID_JS_SCRIPT_RESULT_CODE;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.GET_BUYER_DECISION_LOGIC_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.GET_BUYER_DECISION_LOGIC_RESULT_CODE;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.GET_TRUSTED_BIDDING_SIGNALS_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.GET_TRUSTED_BIDDING_SIGNALS_RESULT_CODE;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.NUM_OF_ADS_FOR_BIDDING;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.RUN_AD_BIDDING_PER_CA_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.RUN_AD_BIDDING_PER_CA_RESULT_CODE;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.RUN_AD_BIDDING_PER_CA_RETURNED_AD_COST;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.RUN_BIDDING_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.RUN_BIDDING_RESULT_CODE;
import static com.android.adservices.service.stats.RunAdBiddingProcessReportedStatsTest.GET_BUYERS_CUSTOM_AUDIENCE_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdBiddingProcessReportedStatsTest.GET_BUYERS_CUSTOM_AUDIENCE_RESULT_CODE;
import static com.android.adservices.service.stats.RunAdBiddingProcessReportedStatsTest.NUM_BUYERS_FETCHED;
import static com.android.adservices.service.stats.RunAdBiddingProcessReportedStatsTest.NUM_BUYERS_REQUESTED;
import static com.android.adservices.service.stats.RunAdBiddingProcessReportedStatsTest.NUM_OF_ADS_ENTERING_BIDDING;
import static com.android.adservices.service.stats.RunAdBiddingProcessReportedStatsTest.NUM_OF_CAS_ENTERING_BIDDING;
import static com.android.adservices.service.stats.RunAdBiddingProcessReportedStatsTest.NUM_OF_CAS_POSTING_BIDDING;
import static com.android.adservices.service.stats.RunAdBiddingProcessReportedStatsTest.RATIO_OF_CAS_SELECTING_RMKT_ADS;
import static com.android.adservices.service.stats.RunAdBiddingProcessReportedStatsTest.RUN_AD_BIDDING_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdBiddingProcessReportedStatsTest.RUN_AD_BIDDING_RESULT_CODE;
import static com.android.adservices.service.stats.RunAdBiddingProcessReportedStatsTest.TOTAL_AD_BIDDING_STAGE_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.FETCHED_AD_SELECTION_LOGIC_SCRIPT_SIZE_IN_BYTES;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.FETCHED_TRUSTED_SCORING_SIGNALS_DATA_SIZE_IN_BYTES;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.GET_AD_SCORES_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.GET_AD_SCORES_RESULT_CODE;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.GET_AD_SELECTION_LOGIC_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.GET_AD_SELECTION_LOGIC_RESULT_CODE;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.GET_AD_SELECTION_LOGIC_SCRIPT_TYPE;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.GET_TRUSTED_SCORING_SIGNALS_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.GET_TRUSTED_SCORING_SIGNALS_RESULT_CODE;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.NUM_OF_CAS_ENTERING_SCORING;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.NUM_OF_CONTEXTUAL_ADS_ENTERING_SCORING;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.NUM_OF_REMARKETING_ADS_ENTERING_SCORING;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.RUN_AD_SCORING_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.RUN_AD_SCORING_RESULT_CODE;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.SCORE_ADS_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.SCORE_AD_JS_SCRIPT_RESULT_CODE;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.SCORE_AD_SELLER_ADDITIONAL_SIGNALS_CONTAINED_DATA_VERSION;
import static com.android.adservices.service.stats.RunAdSelectionProcessReportedStatsTest.DB_AD_SELECTION_SIZE_IN_BYTES;
import static com.android.adservices.service.stats.RunAdSelectionProcessReportedStatsTest.IS_RMKT_ADS_WON;
import static com.android.adservices.service.stats.RunAdSelectionProcessReportedStatsTest.PERSIST_AD_SELECTION_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdSelectionProcessReportedStatsTest.PERSIST_AD_SELECTION_RESULT_CODE;
import static com.android.adservices.service.stats.RunAdSelectionProcessReportedStatsTest.RUN_AD_SELECTION_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdSelectionProcessReportedStatsTest.RUN_AD_SELECTION_RESULT_CODE;
import static com.android.adservices.service.stats.UpdateCustomAudienceProcessReportedStatsTest.DATA_SIZE_OF_ADS_IN_BYTES;
import static com.android.adservices.service.stats.UpdateCustomAudienceProcessReportedStatsTest.NUM_OF_ADS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.adservices.adselection.ReportEventRequest;

import com.android.adservices.cobalt.AppNameApiErrorLogger;
import com.android.adservices.cobalt.MeasurementCobaltLogger;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AppManifestConfigCall;
import com.android.adservices.service.common.AppManifestConfigCall.ApiType;
import com.android.adservices.service.common.AppManifestConfigCall.Result;
import com.android.adservices.service.enrollment.EnrollmentStatus;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.WipeoutStatus;
import com.android.adservices.service.measurement.attribution.AttributionStatus;
import com.android.adservices.service.measurement.ondevicepersonalization.OdpApiCallStatus;
import com.android.adservices.service.measurement.ondevicepersonalization.OdpRegistrationStatus;
import com.android.adservices.service.stats.pas.EncodingFetchStats;
import com.android.adservices.service.stats.pas.EncodingJobRunStats;
import com.android.adservices.service.stats.pas.EncodingJsExecutionStats;
import com.android.adservices.service.stats.pas.PersistAdSelectionResultCalledStats;
import com.android.adservices.service.stats.pas.UpdateSignalsApiCalledStats;
import com.android.adservices.shared.testing.AnswerSyncCallback;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

/** Unit tests for {@link AdServicesLoggerImpl}. */
@SpyStatic(FlagsFactory.class)
@SpyStatic(AppNameApiErrorLogger.class)
@SpyStatic(MeasurementCobaltLogger.class)
public final class AdServicesLoggerImplTest extends AdServicesExtendedMockitoTestCase {
    private static final String TEST_SOURCE_REGISTRATION = "android-app://com.registrant";
    private static final String TEST_ENROLLMENT_ID = "EnrollmentId";

    @Mock private StatsdAdServicesLogger mStatsdLoggerMock;
    @Mock private Flags mMockFlags;
    @Mock private AppNameApiErrorLogger mMockAppNameApiErrorLogger;
    @Mock private MeasurementCobaltLogger mMeasurementCobaltLogger;
    private AdServicesLoggerImpl mAdservicesLogger;

    @Before
    public void setUp() {
        mAdservicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
    }

    @Test
    public void testLogFledgeApiCallStats() {
        int latencyMs = 10;
        mAdservicesLogger.logFledgeApiCallStats(
                AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS, STATUS_SUCCESS, latencyMs);
        verify(mStatsdLoggerMock)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS, STATUS_SUCCESS, latencyMs);
    }

    @Test
    public void testLogFledgeApiCallStatsWithAppPackageNameLogging() throws Exception {
        mockAppNameApiErrorLogger();
        int apiName = AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS;
        String appPackageName = TEST_PACKAGE_NAME;
        int resultCode = STATUS_SUCCESS;
        int latencyMs = 10;
        AnswerSyncCallback<Void> callback = AnswerSyncCallback.forSingleVoidAnswer();
        doAnswer(callback)
                .when(mMockAppNameApiErrorLogger)
                .logErrorOccurrence(
                        appPackageName,
                        AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        STATUS_SUCCESS);

        mAdservicesLogger.logFledgeApiCallStats(apiName, appPackageName, resultCode, latencyMs);

        // Verify method logging app package name is called.
        verify(mStatsdLoggerMock)
                .logFledgeApiCallStats(apiName, appPackageName, resultCode, latencyMs);

        callback.assertCalled();
    }

    @Test
    public void testLogFledgeApiCallStatsWithAppPackageName_nullPackageName() throws Exception {
        assertThrows(
                NullPointerException.class,
                () ->
                        mAdservicesLogger.logFledgeApiCallStats(
                                AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                                /* appPackageName= */ null,
                                STATUS_SUCCESS,
                                /* latencyMs= */ 42));
    }

    @Test
    public void testLogRunAdSelectionProcessReportedStats() {
        RunAdSelectionProcessReportedStats stats =
                RunAdSelectionProcessReportedStats.builder()
                        .setIsRemarketingAdsWon(IS_RMKT_ADS_WON)
                        .setDBAdSelectionSizeInBytes(DB_AD_SELECTION_SIZE_IN_BYTES)
                        .setPersistAdSelectionLatencyInMillis(
                                PERSIST_AD_SELECTION_LATENCY_IN_MILLIS)
                        .setPersistAdSelectionResultCode(PERSIST_AD_SELECTION_RESULT_CODE)
                        .setRunAdSelectionLatencyInMillis(RUN_AD_SELECTION_LATENCY_IN_MILLIS)
                        .setRunAdSelectionResultCode(RUN_AD_SELECTION_RESULT_CODE)
                        .build();
        mAdservicesLogger.logRunAdSelectionProcessReportedStats(stats);
        ArgumentCaptor<RunAdSelectionProcessReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(RunAdSelectionProcessReportedStats.class);
        verify(mStatsdLoggerMock).logRunAdSelectionProcessReportedStats(argumentCaptor.capture());
        RunAdSelectionProcessReportedStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getIsRemarketingAdsWon()).isEqualTo(IS_RMKT_ADS_WON);
        expect.that(loggedStats.getDBAdSelectionSizeInBytes())
                .isEqualTo(DB_AD_SELECTION_SIZE_IN_BYTES);
        expect.that(loggedStats.getPersistAdSelectionLatencyInMillis())
                .isEqualTo(PERSIST_AD_SELECTION_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getRunAdSelectionResultCode())
                .isEqualTo(PERSIST_AD_SELECTION_RESULT_CODE);
        expect.that(loggedStats.getRunAdSelectionLatencyInMillis())
                .isEqualTo(RUN_AD_SELECTION_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getRunAdSelectionResultCode())
                .isEqualTo(RUN_AD_SELECTION_RESULT_CODE);
    }

    @Test
    public void testLogRunAdScoringProcessReportedStats() {
        RunAdScoringProcessReportedStats stats =
                RunAdScoringProcessReportedStats.builder()
                        .setGetAdSelectionLogicLatencyInMillis(
                                GET_AD_SELECTION_LOGIC_LATENCY_IN_MILLIS)
                        .setGetAdSelectionLogicResultCode(GET_AD_SELECTION_LOGIC_RESULT_CODE)
                        .setGetAdSelectionLogicScriptType(GET_AD_SELECTION_LOGIC_SCRIPT_TYPE)
                        .setFetchedAdSelectionLogicScriptSizeInBytes(
                                FETCHED_AD_SELECTION_LOGIC_SCRIPT_SIZE_IN_BYTES)
                        .setGetTrustedScoringSignalsLatencyInMillis(
                                GET_TRUSTED_SCORING_SIGNALS_LATENCY_IN_MILLIS)
                        .setGetTrustedScoringSignalsResultCode(
                                GET_TRUSTED_SCORING_SIGNALS_RESULT_CODE)
                        .setFetchedTrustedScoringSignalsDataSizeInBytes(
                                FETCHED_TRUSTED_SCORING_SIGNALS_DATA_SIZE_IN_BYTES)
                        .setScoreAdsLatencyInMillis(SCORE_ADS_LATENCY_IN_MILLIS)
                        .setGetAdScoresLatencyInMillis(GET_AD_SCORES_LATENCY_IN_MILLIS)
                        .setGetAdScoresResultCode(GET_AD_SCORES_RESULT_CODE)
                        .setNumOfCasEnteringScoring(NUM_OF_CAS_ENTERING_SCORING)
                        .setNumOfRemarketingAdsEnteringScoring(
                                NUM_OF_REMARKETING_ADS_ENTERING_SCORING)
                        .setNumOfContextualAdsEnteringScoring(
                                NUM_OF_CONTEXTUAL_ADS_ENTERING_SCORING)
                        .setRunAdScoringLatencyInMillis(RUN_AD_SCORING_LATENCY_IN_MILLIS)
                        .setRunAdScoringResultCode(RUN_AD_SCORING_RESULT_CODE)
                        .setScoreAdSellerAdditionalSignalsContainedDataVersion(
                                SCORE_AD_SELLER_ADDITIONAL_SIGNALS_CONTAINED_DATA_VERSION)
                        .setScoreAdJsScriptResultCode(SCORE_AD_JS_SCRIPT_RESULT_CODE)
                        .build();
        mAdservicesLogger.logRunAdScoringProcessReportedStats(stats);
        ArgumentCaptor<RunAdScoringProcessReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(RunAdScoringProcessReportedStats.class);
        verify(mStatsdLoggerMock).logRunAdScoringProcessReportedStats(argumentCaptor.capture());
        RunAdScoringProcessReportedStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getGetAdSelectionLogicLatencyInMillis())
                .isEqualTo(GET_AD_SELECTION_LOGIC_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getGetAdSelectionLogicResultCode())
                .isEqualTo(GET_AD_SELECTION_LOGIC_RESULT_CODE);
        expect.that(loggedStats.getGetAdSelectionLogicScriptType())
                .isEqualTo(GET_AD_SELECTION_LOGIC_SCRIPT_TYPE);
        expect.that(loggedStats.getFetchedAdSelectionLogicScriptSizeInBytes())
                .isEqualTo(FETCHED_AD_SELECTION_LOGIC_SCRIPT_SIZE_IN_BYTES);
        expect.that(loggedStats.getGetTrustedScoringSignalsLatencyInMillis())
                .isEqualTo(GET_TRUSTED_SCORING_SIGNALS_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getGetTrustedScoringSignalsResultCode())
                .isEqualTo(GET_TRUSTED_SCORING_SIGNALS_RESULT_CODE);
        expect.that(loggedStats.getFetchedTrustedScoringSignalsDataSizeInBytes())
                .isEqualTo(FETCHED_TRUSTED_SCORING_SIGNALS_DATA_SIZE_IN_BYTES);
        expect.that(loggedStats.getScoreAdsLatencyInMillis())
                .isEqualTo(SCORE_ADS_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getGetAdScoresLatencyInMillis())
                .isEqualTo(GET_AD_SCORES_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getGetAdScoresResultCode()).isEqualTo(GET_AD_SCORES_RESULT_CODE);
        expect.that(loggedStats.getNumOfCasEnteringScoring())
                .isEqualTo(NUM_OF_CAS_ENTERING_SCORING);
        expect.that(loggedStats.getNumOfRemarketingAdsEnteringScoring())
                .isEqualTo(NUM_OF_REMARKETING_ADS_ENTERING_SCORING);
        expect.that(loggedStats.getNumOfContextualAdsEnteringScoring())
                .isEqualTo(NUM_OF_CONTEXTUAL_ADS_ENTERING_SCORING);
        expect.that(loggedStats.getRunAdScoringLatencyInMillis())
                .isEqualTo(RUN_AD_SCORING_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getRunAdScoringResultCode()).isEqualTo(RUN_AD_SCORING_RESULT_CODE);
        expect.that(loggedStats.getScoreAdSellerAdditionalSignalsContainedDataVersion())
                .isEqualTo(SCORE_AD_SELLER_ADDITIONAL_SIGNALS_CONTAINED_DATA_VERSION);
        expect.that(loggedStats.getScoreAdJsScriptResultCode())
                .isEqualTo(SCORE_AD_JS_SCRIPT_RESULT_CODE);
    }

    @Test
    public void testLogRunAdBiddingProcessReportedStats() {
        RunAdBiddingProcessReportedStats stats =
                RunAdBiddingProcessReportedStats.builder()
                        .setGetBuyersCustomAudienceLatencyInMills(
                                GET_BUYERS_CUSTOM_AUDIENCE_LATENCY_IN_MILLIS)
                        .setGetBuyersCustomAudienceResultCode(
                                GET_BUYERS_CUSTOM_AUDIENCE_RESULT_CODE)
                        .setNumBuyersRequested(NUM_BUYERS_REQUESTED)
                        .setNumBuyersFetched(NUM_BUYERS_FETCHED)
                        .setNumOfAdsEnteringBidding(NUM_OF_ADS_ENTERING_BIDDING)
                        .setNumOfCasEnteringBidding(NUM_OF_CAS_ENTERING_BIDDING)
                        .setNumOfCasPostBidding(NUM_OF_CAS_POSTING_BIDDING)
                        .setRatioOfCasSelectingRmktAds(RATIO_OF_CAS_SELECTING_RMKT_ADS)
                        .setRunAdBiddingLatencyInMillis(RUN_AD_BIDDING_LATENCY_IN_MILLIS)
                        .setRunAdBiddingResultCode(RUN_AD_BIDDING_RESULT_CODE)
                        .setTotalAdBiddingStageLatencyInMillis(
                                TOTAL_AD_BIDDING_STAGE_LATENCY_IN_MILLIS)
                        .build();
        mAdservicesLogger.logRunAdBiddingProcessReportedStats(stats);
        ArgumentCaptor<RunAdBiddingProcessReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(RunAdBiddingProcessReportedStats.class);
        verify(mStatsdLoggerMock).logRunAdBiddingProcessReportedStats(argumentCaptor.capture());
        RunAdBiddingProcessReportedStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getGetBuyersCustomAudienceLatencyInMills())
                .isEqualTo(GET_BUYERS_CUSTOM_AUDIENCE_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getGetBuyersCustomAudienceResultCode())
                .isEqualTo(GET_BUYERS_CUSTOM_AUDIENCE_RESULT_CODE);
        expect.that(loggedStats.getNumBuyersRequested()).isEqualTo(NUM_BUYERS_REQUESTED);
        expect.that(loggedStats.getNumBuyersFetched()).isEqualTo(NUM_BUYERS_FETCHED);
        expect.that(loggedStats.getNumOfAdsEnteringBidding())
                .isEqualTo(NUM_OF_ADS_ENTERING_BIDDING);
        expect.that(loggedStats.getNumOfCasEnteringBidding())
                .isEqualTo(NUM_OF_CAS_ENTERING_BIDDING);
        expect.that(loggedStats.getNumOfCasPostBidding()).isEqualTo(NUM_OF_CAS_POSTING_BIDDING);
        expect.that(loggedStats.getRatioOfCasSelectingRmktAds())
                .isWithin(0.0f)
                .of(RATIO_OF_CAS_SELECTING_RMKT_ADS);
        expect.that(loggedStats.getRunAdBiddingLatencyInMillis())
                .isEqualTo(RUN_AD_BIDDING_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getRunAdBiddingResultCode()).isEqualTo(RUN_AD_BIDDING_RESULT_CODE);
        expect.that(loggedStats.getTotalAdBiddingStageLatencyInMillis())
                .isEqualTo(TOTAL_AD_BIDDING_STAGE_LATENCY_IN_MILLIS);
    }

    @Test
    public void testLogRunAdBiddingPerCAProcessReportedStats() {
        RunAdBiddingPerCAProcessReportedStats stats =
                RunAdBiddingPerCAProcessReportedStats.builder()
                        .setNumOfAdsForBidding(NUM_OF_ADS_FOR_BIDDING)
                        .setRunAdBiddingPerCaLatencyInMillis(
                                RUN_AD_BIDDING_PER_CA_LATENCY_IN_MILLIS)
                        .setRunAdBiddingPerCaResultCode(RUN_AD_BIDDING_PER_CA_RESULT_CODE)
                        .setGetBuyerDecisionLogicLatencyInMillis(
                                GET_BUYER_DECISION_LOGIC_LATENCY_IN_MILLIS)
                        .setGetBuyerDecisionLogicResultCode(GET_BUYER_DECISION_LOGIC_RESULT_CODE)
                        .setBuyerDecisionLogicScriptType(BUYER_DECISION_LOGIC_SCRIPT_TYPE)
                        .setFetchedBuyerDecisionLogicScriptSizeInBytes(
                                FETCHED_BUYER_DECISION_LOGIC_SCRIPT_SIZE_IN_BYTES)
                        .setNumOfKeysOfTrustedBiddingSignals(NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS)
                        .setFetchedTrustedBiddingSignalsDataSizeInBytes(
                                FETCHED_TRUSTED_BIDDING_SIGNALS_DATA_SIZE_IN_BYTES)
                        .setGetTrustedBiddingSignalsLatencyInMillis(
                                GET_TRUSTED_BIDDING_SIGNALS_LATENCY_IN_MILLIS)
                        .setGetTrustedBiddingSignalsResultCode(
                                GET_TRUSTED_BIDDING_SIGNALS_RESULT_CODE)
                        .setGenerateBidsLatencyInMillis(GENERATE_BIDS_LATENCY_IN_MILLIS)
                        .setRunBiddingLatencyInMillis(RUN_BIDDING_LATENCY_IN_MILLIS)
                        .setRunBiddingResultCode(RUN_BIDDING_RESULT_CODE)
                        .setRunAdBiddingPerCaReturnedAdCost(RUN_AD_BIDDING_PER_CA_RETURNED_AD_COST)
                        .setGenerateBidBuyerAdditionalSignalsContainedDataVersion(
                                GENERATE_BID_BUYER_ADDITIONAL_SIGNALS_CONTAINED_DATA_VERSION)
                        .setGenerateBidJsScriptResultCode(GENERATE_BID_JS_SCRIPT_RESULT_CODE)
                        .build();
        mAdservicesLogger.logRunAdBiddingPerCAProcessReportedStats(stats);
        ArgumentCaptor<RunAdBiddingPerCAProcessReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(RunAdBiddingPerCAProcessReportedStats.class);
        verify(mStatsdLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(argumentCaptor.capture());
        RunAdBiddingPerCAProcessReportedStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getNumOfAdsForBidding()).isEqualTo(NUM_OF_ADS_FOR_BIDDING);
        expect.that(loggedStats.getRunAdBiddingPerCaLatencyInMillis())
                .isEqualTo(RUN_AD_BIDDING_PER_CA_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getRunAdBiddingPerCaResultCode())
                .isEqualTo(RUN_AD_BIDDING_PER_CA_RESULT_CODE);
        expect.that(loggedStats.getGetBuyerDecisionLogicLatencyInMillis())
                .isEqualTo(GET_BUYER_DECISION_LOGIC_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getGetBuyerDecisionLogicResultCode())
                .isEqualTo(GET_BUYER_DECISION_LOGIC_RESULT_CODE);
        expect.that(loggedStats.getBuyerDecisionLogicScriptType())
                .isEqualTo(BUYER_DECISION_LOGIC_SCRIPT_TYPE);
        expect.that(loggedStats.getFetchedBuyerDecisionLogicScriptSizeInBytes())
                .isEqualTo(FETCHED_BUYER_DECISION_LOGIC_SCRIPT_SIZE_IN_BYTES);
        expect.that(loggedStats.getNumOfKeysOfTrustedBiddingSignals())
                .isEqualTo(NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS);
        expect.that(loggedStats.getFetchedTrustedBiddingSignalsDataSizeInBytes())
                .isEqualTo(FETCHED_TRUSTED_BIDDING_SIGNALS_DATA_SIZE_IN_BYTES);
        expect.that(loggedStats.getGetTrustedBiddingSignalsLatencyInMillis())
                .isEqualTo(GET_TRUSTED_BIDDING_SIGNALS_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getGetTrustedBiddingSignalsResultCode())
                .isEqualTo(GET_TRUSTED_BIDDING_SIGNALS_RESULT_CODE);
        expect.that(loggedStats.getGenerateBidsLatencyInMillis())
                .isEqualTo(GENERATE_BIDS_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getRunBiddingLatencyInMillis())
                .isEqualTo(RUN_BIDDING_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getRunBiddingResultCode()).isEqualTo(RUN_BIDDING_RESULT_CODE);
        expect.that(loggedStats.getRunAdBiddingPerCaReturnedAdCost())
                .isEqualTo(RUN_AD_BIDDING_PER_CA_RETURNED_AD_COST);
        expect.that(loggedStats.getGenerateBidBuyerAdditionalSignalsContainedDataVersion())
                .isEqualTo(GENERATE_BID_BUYER_ADDITIONAL_SIGNALS_CONTAINED_DATA_VERSION);
        expect.that(loggedStats.getGenerateBidJsScriptResultCode())
                .isEqualTo(GENERATE_BID_JS_SCRIPT_RESULT_CODE);
    }

    @Test
    public void testLogBackgroundFetchProcessReportedStats() {
        BackgroundFetchProcessReportedStats stats =
                BackgroundFetchProcessReportedStats.builder()
                        .setLatencyInMillis(LATENCY_IN_MILLIS)
                        .setNumOfEligibleToUpdateCas(NUM_OF_ELIGIBLE_TO_UPDATE_CAS)
                        .setResultCode(RESULT_CODE)
                        .build();
        mAdservicesLogger.logBackgroundFetchProcessReportedStats(stats);
        ArgumentCaptor<BackgroundFetchProcessReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(BackgroundFetchProcessReportedStats.class);
        verify(mStatsdLoggerMock).logBackgroundFetchProcessReportedStats(argumentCaptor.capture());
        BackgroundFetchProcessReportedStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getLatencyInMillis()).isEqualTo(LATENCY_IN_MILLIS);
        expect.that(loggedStats.getNumOfEligibleToUpdateCas())
                .isEqualTo(NUM_OF_ELIGIBLE_TO_UPDATE_CAS);
        expect.that(loggedStats.getResultCode()).isEqualTo(RESULT_CODE);
    }

    @Test
    public void testLogUpdateCustomAudienceProcessReportedStats() {
        UpdateCustomAudienceProcessReportedStats stats =
                UpdateCustomAudienceProcessReportedStats.builder()
                        .setLatencyInMills(LATENCY_IN_MILLIS)
                        .setResultCode(RESULT_CODE)
                        .setDataSizeOfAdsInBytes(DATA_SIZE_OF_ADS_IN_BYTES)
                        .setNumOfAds(NUM_OF_ADS)
                        .build();
        mAdservicesLogger.logUpdateCustomAudienceProcessReportedStats(stats);
        ArgumentCaptor<UpdateCustomAudienceProcessReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(UpdateCustomAudienceProcessReportedStats.class);
        verify(mStatsdLoggerMock)
                .logUpdateCustomAudienceProcessReportedStats(argumentCaptor.capture());
        UpdateCustomAudienceProcessReportedStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getLatencyInMills()).isEqualTo(LATENCY_IN_MILLIS);
        expect.that(loggedStats.getResultCode()).isEqualTo(RESULT_CODE);
        expect.that(loggedStats.getDataSizeOfAdsInBytes()).isEqualTo(DATA_SIZE_OF_ADS_IN_BYTES);
        expect.that(loggedStats.getNumOfAds()).isEqualTo(NUM_OF_ADS);
    }

    @Test
    public void testLogMeasurementReports() throws Exception {
        int testUploadMethod =
                AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__UPLOAD_METHOD__FALLBACK_REPORT_UPLOAD_METHOD;
        int testReportType = AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__TYPE__EVENT;
        int testFailureType =
                AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__FAILURE_TYPE__UNKNOWN_REPORT_UPLOAD_FAILURE_TYPE;
        int testResultCode = AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__RESPONSE_CODE__SUCCESS;
        MeasurementReportsStats stats =
                new MeasurementReportsStats.Builder()
                        .setCode(AD_SERVICES_MESUREMENT_REPORTS_UPLOADED)
                        .setType(testReportType)
                        .setUploadMethod(testUploadMethod)
                        .setFailureType(testFailureType)
                        .setResultCode(testResultCode)
                        .setSourceRegistrant(TEST_SOURCE_REGISTRATION)
                        .build();
        mockMsmtReportingCobaltLogger();
        AnswerSyncCallback<Void> callback = AnswerSyncCallback.forSingleVoidAnswer();
        doAnswer(callback)
                .when(mMeasurementCobaltLogger)
                .logReportingStatusWithAppName(
                        TEST_SOURCE_REGISTRATION,
                        testReportType,
                        testUploadMethod,
                        testResultCode,
                        testFailureType);
        ArgumentCaptor<MeasurementReportsStats> argumentCaptor =
                ArgumentCaptor.forClass(MeasurementReportsStats.class);

        mAdservicesLogger.logMeasurementReports(stats);

        verify(mStatsdLoggerMock).logMeasurementReports(argumentCaptor.capture());
        MeasurementReportsStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getCode()).isEqualTo(AD_SERVICES_MESUREMENT_REPORTS_UPLOADED);
        expect.that(loggedStats.getType()).isEqualTo(testReportType);
        expect.that(loggedStats.getUploadMethod()).isEqualTo(testUploadMethod);
        expect.that(loggedStats.getResultCode()).isEqualTo(testResultCode);
        expect.that(loggedStats.getFailureType()).isEqualTo(testFailureType);
        expect.that(loggedStats.getSourceRegistrant()).isEqualTo(TEST_SOURCE_REGISTRATION);
        callback.assertCalled();
    }

    @Test
    public void testLogApiCallStats() throws Exception {
        String packageName = "com.android.test";
        String sdkName = "com.android.container";
        int latency = 100;

        mocker.mockGetFlags(mMockFlags);
        mockAppNameApiErrorLogger();

        AnswerSyncCallback<Void> callback = AnswerSyncCallback.forSingleVoidAnswer();
        doAnswer(callback)
                .when(mMockAppNameApiErrorLogger)
                .logErrorOccurrence(
                        packageName, AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS, STATUS_SUCCESS);

        ApiCallStats stats =
                new ApiCallStats.Builder()
                        .setCode(AD_SERVICES_API_CALLED)
                        .setApiClass(AD_SERVICES_API_CALLED__API_CLASS__TARGETING)
                        .setApiName(AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS)
                        .setAppPackageName(packageName)
                        .setSdkPackageName(sdkName)
                        .setLatencyMillisecond(latency)
                        .setResultCode(STATUS_SUCCESS)
                        .build();
        mAdservicesLogger.logApiCallStats(stats);
        callback.assertCalled();

        ArgumentCaptor<ApiCallStats> argumentCaptor = ArgumentCaptor.forClass(ApiCallStats.class);
        verify(mStatsdLoggerMock).logApiCallStats(argumentCaptor.capture());
        ApiCallStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getCode()).isEqualTo(AD_SERVICES_API_CALLED);
        expect.that(loggedStats.getApiClass())
                .isEqualTo(AD_SERVICES_API_CALLED__API_CLASS__TARGETING);
        expect.that(loggedStats.getApiName())
                .isEqualTo(AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS);
        expect.that(loggedStats.getAppPackageName()).isEqualTo(packageName);
        expect.that(loggedStats.getSdkPackageName()).isEqualTo(sdkName);
        expect.that(loggedStats.getLatencyMillisecond()).isEqualTo(latency);
        expect.that(loggedStats.getResultCode()).isEqualTo(STATUS_SUCCESS);
    }

    @Test
    public void testLogApiCallStats_invalidArguments() throws Exception {
        assertThrows(NullPointerException.class, () -> mAdservicesLogger.logApiCallStats(null));

        // cannot use Builder as it checks for null
        Constructor<ApiCallStats> constructor = ApiCallStats.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        ApiCallStats packageNamelessStats = constructor.newInstance();

        assertThrows(
                IllegalArgumentException.class,
                () -> mAdservicesLogger.logApiCallStats(packageNamelessStats));
    }

    @Test
    public void testCobaltLogAppNameApiError_nullPackageName() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mAdservicesLogger.cobaltLogAppNameApiError(
                                null,
                                AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS,
                                STATUS_SUCCESS));
    }

    @Test
    public void testLogUIStats() {
        UIStats stats =
                new UIStats.Builder()
                        .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                        .setRegion(AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW)
                        .setAction(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_OUT_SELECTED)
                        .build();
        mAdservicesLogger.logUIStats(stats);
        ArgumentCaptor<UIStats> argumentCaptor = ArgumentCaptor.forClass(UIStats.class);
        verify(mStatsdLoggerMock).logUIStats(argumentCaptor.capture());
        UIStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getCode()).isEqualTo(AD_SERVICES_SETTINGS_USAGE_REPORTED);
        expect.that(loggedStats.getRegion())
                .isEqualTo(AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW);
        expect.that(loggedStats.getAction())
                .isEqualTo(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_OUT_SELECTED);
    }

    @Test
    public void testLogMsmtRegistrationResponseSize() throws InterruptedException {
        boolean isEeaDevice = false;
        int metricsCode = AD_SERVICES_MEASUREMENT_REGISTRATIONS;
        int retryCount = 0;
        int registrationDelay = 100;
        int responseSize = 200;
        int registrationType = AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE;
        int interactionType = AD_SERVICES_MEASUREMENT_REGISTRATIONS__SOURCE_TYPE__EVENT_SOURCE_TYPE;
        int surfaceType =
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__SURFACE_TYPE__APP_REGISTRATION_SURFACE_TYPE;
        int registrationStatus = AD_SERVICES_MEASUREMENT_REGISTRATIONS__STATUS__SUCCESS_STATUS;
        int failureType =
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__FAILURE_TYPE__UNKNOWN_REGISTRATION_FAILURE_TYPE;
        mocker.mockGetFlags(mMockFlags);
        mockMsmtRegistrationCobaltLogger(isEeaDevice);
        AnswerSyncCallback<Void> callback = AnswerSyncCallback.forSingleVoidAnswer();
        doAnswer(callback)
                .when(mMeasurementCobaltLogger)
                .logRegistrationStatus(
                        TEST_SOURCE_REGISTRATION,
                        surfaceType,
                        registrationType,
                        interactionType,
                        registrationStatus,
                        failureType,
                        isEeaDevice);
        MeasurementRegistrationResponseStats stats =
                new MeasurementRegistrationResponseStats.Builder(
                                metricsCode,
                                registrationType,
                                responseSize,
                                interactionType,
                                surfaceType,
                                registrationStatus,
                                failureType,
                                registrationDelay,
                                TEST_SOURCE_REGISTRATION,
                                retryCount,
                                /* isRedirectOnly= */ false,
                                /* isPARequest= */ false,
                                /* num entities deleted */ 5,
                                /* isEventLevelEpsilonEnabled= */ false)
                        .setAdTechDomain(null)
                        .build();
        mAdservicesLogger.logMeasurementRegistrationsResponseSize(stats);
        ArgumentCaptor<MeasurementRegistrationResponseStats> argumentCaptor =
                ArgumentCaptor.forClass(MeasurementRegistrationResponseStats.class);
        verify(mStatsdLoggerMock).logMeasurementRegistrationsResponseSize(argumentCaptor.capture());
        MeasurementRegistrationResponseStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getCode()).isEqualTo(metricsCode);
        expect.that(loggedStats.getRegistrationStatus()).isEqualTo(registrationStatus);
        expect.that(loggedStats.getSurfaceType()).isEqualTo(surfaceType);
        expect.that(loggedStats.getResponseSize()).isEqualTo(responseSize);
        expect.that(loggedStats.getFailureType()).isEqualTo(failureType);
        expect.that(loggedStats.getRegistrationDelay()).isEqualTo(registrationDelay);
        expect.that(loggedStats.getRegistrationType()).isEqualTo(registrationType);
        expect.that(loggedStats.getInteractionType()).isEqualTo(interactionType);
        expect.that(loggedStats.getSourceRegistrant()).isEqualTo(TEST_SOURCE_REGISTRATION);
        expect.that(loggedStats.getRetryCount()).isEqualTo(retryCount);
        expect.that(loggedStats.isPARequest()).isFalse();
        expect.that(loggedStats.isRedirectOnly()).isFalse();
        expect.that(loggedStats.getAdTechDomain()).isNull();
        expect.that(loggedStats.getNumDeletedEntities()).isEqualTo(5);
        expect.that(loggedStats.isEventLevelEpsilonEnabled()).isFalse();
        callback.assertCalled();
    }

    @Test
    public void testLogMsmtDebugKeyMatchStats() {
        long hashedValue = 5000L;
        long hashLimit = 10000L;
        MsmtDebugKeysMatchStats stats =
                MsmtDebugKeysMatchStats.builder()
                        .setAdTechEnrollmentId(TEST_ENROLLMENT_ID)
                        .setMatched(true)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB)
                        .setDebugJoinKeyHashedValue(hashedValue)
                        .setDebugJoinKeyHashLimit(hashLimit)
                        .setSourceRegistrant(TEST_SOURCE_REGISTRATION)
                        .build();
        mAdservicesLogger.logMeasurementDebugKeysMatch(stats);
        ArgumentCaptor<MsmtDebugKeysMatchStats> argumentCaptor =
                ArgumentCaptor.forClass(MsmtDebugKeysMatchStats.class);
        verify(mStatsdLoggerMock).logMeasurementDebugKeysMatch(argumentCaptor.capture());
        expect.that(stats).isEqualTo(argumentCaptor.getValue());
    }

    @Test
    public void testLogMsmtAdIdMatchForDebugKeysStats() {
        long uniqueAdIds = 2L;
        long uniqueAdIdLimit = 5L;
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(TEST_ENROLLMENT_ID)
                        .setMatched(true)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB)
                        .setNumUniqueAdIds(uniqueAdIds)
                        .setNumUniqueAdIdsLimit(uniqueAdIdLimit)
                        .setSourceRegistrant(TEST_SOURCE_REGISTRATION)
                        .build();
        mAdservicesLogger.logMeasurementAdIdMatchForDebugKeysStats(stats);
        ArgumentCaptor<MsmtAdIdMatchForDebugKeysStats> argumentCaptor =
                ArgumentCaptor.forClass(MsmtAdIdMatchForDebugKeysStats.class);
        verify(mStatsdLoggerMock)
                .logMeasurementAdIdMatchForDebugKeysStats(argumentCaptor.capture());
        expect.that(stats).isEqualTo(argumentCaptor.getValue());
    }

    @Test
    public void testLogMeasurementAttributionStats() throws Exception {
        int testSourceType = AttributionStatus.SourceType.VIEW.getValue();
        int testSurfaceType = AttributionStatus.AttributionSurface.APP_WEB.getValue();
        int testStatusCode = AttributionStatus.AttributionResult.SUCCESS.getValue();
        int testFailureType = AttributionStatus.FailureType.UNKNOWN.getValue();
        long testAttributionDelay = 100L;
        mockMsmtAttributionCobaltLogger();
        MeasurementAttributionStats stats =
                new MeasurementAttributionStats.Builder()
                        .setCode(AD_SERVICES_MEASUREMENT_ATTRIBUTION)
                        .setSourceType(testSourceType)
                        .setSourceRegistrant(TEST_SOURCE_REGISTRATION)
                        .setSurfaceType(testSurfaceType)
                        .setResult(testStatusCode)
                        .setFailureType(testFailureType)
                        .setSourceDerived(false)
                        .setInstallAttribution(true)
                        .setAttributionDelay(testAttributionDelay)
                        .build();
        AnswerSyncCallback<Void> callback = AnswerSyncCallback.forSingleVoidAnswer();
        doAnswer(callback)
                .when(mMeasurementCobaltLogger)
                .logAttributionStatusWithAppName(
                        TEST_SOURCE_REGISTRATION,
                        testSurfaceType,
                        testSourceType,
                        testStatusCode,
                        testFailureType);
        ArgumentCaptor<MeasurementAttributionStats> argumentCaptor =
                ArgumentCaptor.forClass(MeasurementAttributionStats.class);

        mAdservicesLogger.logMeasurementAttributionStats(stats);

        verify(mStatsdLoggerMock).logMeasurementAttributionStats(argumentCaptor.capture());
        MeasurementAttributionStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getCode()).isEqualTo(AD_SERVICES_MEASUREMENT_ATTRIBUTION);
        expect.that(loggedStats.getSourceRegistrant()).isEqualTo(TEST_SOURCE_REGISTRATION);
        expect.that(loggedStats.getSourceType()).isEqualTo(testSourceType);
        expect.that(loggedStats.getSurfaceType()).isEqualTo(testSurfaceType);
        expect.that(loggedStats.getResult()).isEqualTo(testStatusCode);
        expect.that(loggedStats.getFailureType()).isEqualTo(testFailureType);
        expect.that(loggedStats.isSourceDerived()).isFalse();
        expect.that(loggedStats.isInstallAttribution()).isTrue();
        expect.that(loggedStats.getAttributionDelay()).isEqualTo(testAttributionDelay);
        callback.assertCalled();
    }

    @Test
    public void testLogMeasurementWipeoutStats() {
        MeasurementWipeoutStats stats =
                new MeasurementWipeoutStats.Builder()
                        .setCode(AD_SERVICES_MEASUREMENT_WIPEOUT)
                        .setWipeoutType(WipeoutStatus.WipeoutType.CONSENT_FLIP.ordinal())
                        .build();
        mAdservicesLogger.logMeasurementWipeoutStats(stats);
        ArgumentCaptor<MeasurementWipeoutStats> argumentCaptor =
                ArgumentCaptor.forClass(MeasurementWipeoutStats.class);
        verify(mStatsdLoggerMock).logMeasurementWipeoutStats(argumentCaptor.capture());
        MeasurementWipeoutStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getCode()).isEqualTo(AD_SERVICES_MEASUREMENT_WIPEOUT);
        expect.that(loggedStats.getWipeoutType())
                .isEqualTo(WipeoutStatus.WipeoutType.CONSENT_FLIP.ordinal());
    }

    @Test
    public void testLogMeasurementDelayedSourceRegistrationStats() {
        int UnknownEnumValue = 0;
        long registrationDelay = 500L;
        MeasurementDelayedSourceRegistrationStats stats =
                new MeasurementDelayedSourceRegistrationStats.Builder()
                        .setCode(AD_SERVICES_MEASUREMENT_DELAYED_SOURCE_REGISTRATION)
                        .setRegistrationStatus(UnknownEnumValue)
                        .setRegistrationDelay(registrationDelay)
                        .build();
        mAdservicesLogger.logMeasurementDelayedSourceRegistrationStats(stats);
        ArgumentCaptor<MeasurementDelayedSourceRegistrationStats> argumentCaptor =
                ArgumentCaptor.forClass(MeasurementDelayedSourceRegistrationStats.class);
        verify(mStatsdLoggerMock)
                .logMeasurementDelayedSourceRegistrationStats(argumentCaptor.capture());
        MeasurementDelayedSourceRegistrationStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getCode())
                .isEqualTo(AD_SERVICES_MEASUREMENT_DELAYED_SOURCE_REGISTRATION);
        expect.that(loggedStats.getRegistrationStatus()).isEqualTo(UnknownEnumValue);
        expect.that(loggedStats.getRegistrationDelay()).isEqualTo(registrationDelay);
    }

    @Test
    public void testLogMeasurementOdpRegistrationsStats() {
        MeasurementOdpRegistrationStats stats =
                new MeasurementOdpRegistrationStats.Builder()
                        .setCode(AD_SERVICES_MEASUREMENT_PROCESS_ODP_REGISTRATION)
                        .setRegistrationType(
                                OdpRegistrationStatus.RegistrationType.TRIGGER.getValue())
                        .setRegistrationStatus(
                                OdpRegistrationStatus.RegistrationStatus.ODP_UNAVAILABLE.getValue())
                        .build();
        mAdservicesLogger.logMeasurementOdpRegistrations(stats);
        ArgumentCaptor<MeasurementOdpRegistrationStats> argumentCaptor =
                ArgumentCaptor.forClass(MeasurementOdpRegistrationStats.class);
        verify(mStatsdLoggerMock).logMeasurementOdpRegistrations(argumentCaptor.capture());
        MeasurementOdpRegistrationStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getCode())
                .isEqualTo(AD_SERVICES_MEASUREMENT_PROCESS_ODP_REGISTRATION);
        expect.that(loggedStats.getRegistrationType())
                .isEqualTo(OdpRegistrationStatus.RegistrationType.TRIGGER.getValue());
        expect.that(loggedStats.getRegistrationStatus())
                .isEqualTo(OdpRegistrationStatus.RegistrationStatus.ODP_UNAVAILABLE.getValue());
    }

    @Test
    public void testLogMeasurementOdpApiCallStats() {
        long latency = 5L;
        MeasurementOdpApiCallStats stats =
                new MeasurementOdpApiCallStats.Builder()
                        .setCode(AD_SERVICES_MEASUREMENT_NOTIFY_REGISTRATION_TO_ODP)
                        .setLatency(latency)
                        .setApiCallStatus(OdpApiCallStatus.ApiCallStatus.SUCCESS.getValue())
                        .build();
        mAdservicesLogger.logMeasurementOdpApiCall(stats);
        ArgumentCaptor<MeasurementOdpApiCallStats> argumentCaptor =
                ArgumentCaptor.forClass(MeasurementOdpApiCallStats.class);
        verify(mStatsdLoggerMock).logMeasurementOdpApiCall(argumentCaptor.capture());
        MeasurementOdpApiCallStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getCode())
                .isEqualTo(AD_SERVICES_MEASUREMENT_NOTIFY_REGISTRATION_TO_ODP);
        expect.that(loggedStats.getLatency()).isEqualTo(latency);
        expect.that(loggedStats.getApiCallStatus())
                .isEqualTo(OdpApiCallStatus.ApiCallStatus.SUCCESS.getValue());
    }

    @Test
    public void testLogEnrollmentDataStats() {
        int transactionTypeEnumValue =
                EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.ordinal();
        mAdservicesLogger.logEnrollmentDataStats(transactionTypeEnumValue, true, 100);
        verify(mStatsdLoggerMock).logEnrollmentDataStats(transactionTypeEnumValue, true, 100);
    }

    @Test
    public void testLogEnrollmentMatchStats() {
        mAdservicesLogger.logEnrollmentMatchStats(true, 100);
        verify(mStatsdLoggerMock).logEnrollmentMatchStats(true, 100);
    }

    @Test
    public void testLogEnrollmentFileDownloadStats() {
        mAdservicesLogger.logEnrollmentFileDownloadStats(true, 100);
        verify(mStatsdLoggerMock).logEnrollmentFileDownloadStats(true, 100);
    }

    @Test
    public void testLogEnrollmentFailedStats() {
        int dataFileGroupStatusEnumValue =
                EnrollmentStatus.DataFileGroupStatus.PENDING_CUSTOM_VALIDATION.ordinal();
        int errorCauseEnumValue =
                EnrollmentStatus.ErrorCause.ENROLLMENT_BLOCKLISTED_ERROR_CAUSE.ordinal();
        mAdservicesLogger.logEnrollmentFailedStats(
                100, dataFileGroupStatusEnumValue, 10, "SomeSdkName", errorCauseEnumValue);
        verify(mStatsdLoggerMock)
                .logEnrollmentFailedStats(
                        100, dataFileGroupStatusEnumValue, 10, "SomeSdkName", errorCauseEnumValue);
    }

    @Test
    public void testLogMsmtClickVerificationStats() {
        int sourceType = Source.SourceType.NAVIGATION.getIntValue();
        boolean inputEventPresent = true;
        boolean systemClickVerificationSuccessful = true;
        boolean systemClickVerificationEnabled = true;
        long inputEventDelayMs = 200L;
        long validDelayWindowMs = 1000L;
        boolean clickDeduplicationEnabled = true;
        boolean clickDeduplicationEnforced = true;
        long maxSourcesPerClick = 1;
        boolean clickUnderLimit = true;

        MeasurementClickVerificationStats stats =
                MeasurementClickVerificationStats.builder()
                        .setSourceType(sourceType)
                        .setInputEventPresent(inputEventPresent)
                        .setSystemClickVerificationSuccessful(systemClickVerificationSuccessful)
                        .setSystemClickVerificationEnabled(systemClickVerificationEnabled)
                        .setInputEventDelayMillis(inputEventDelayMs)
                        .setValidDelayWindowMillis(validDelayWindowMs)
                        .setSourceRegistrant(TEST_SOURCE_REGISTRATION)
                        .setClickDeduplicationEnabled(clickDeduplicationEnabled)
                        .setClickDeduplicationEnforced(clickDeduplicationEnforced)
                        .setMaxSourcesPerClick(maxSourcesPerClick)
                        .setCurrentRegistrationUnderClickDeduplicationLimit(clickUnderLimit)
                        .build();
        mAdservicesLogger.logMeasurementClickVerificationStats(stats);
        ArgumentCaptor<MeasurementClickVerificationStats> argumentCaptor =
                ArgumentCaptor.forClass(MeasurementClickVerificationStats.class);
        verify(mStatsdLoggerMock).logMeasurementClickVerificationStats(argumentCaptor.capture());
        expect.that(stats).isEqualTo(argumentCaptor.getValue());
    }

    @Test
    public void testLogEncryptionKeyFetchedStats() {
        String encryptionKeyUrl = "https://www.adtech1.com/.well-known/encryption-keys";

        AdServicesEncryptionKeyFetchedStats stats =
                AdServicesEncryptionKeyFetchedStats.builder()
                        .setFetchJobType(ENCRYPTION_KEY_DAILY_FETCH_JOB)
                        .setFetchStatus(IO_EXCEPTION)
                        .setIsFirstTimeFetch(false)
                        .setAdtechEnrollmentId(TEST_ENROLLMENT_ID)
                        .setEncryptionKeyUrl(encryptionKeyUrl)
                        .build();
        mAdservicesLogger.logEncryptionKeyFetchedStats(stats);
        ArgumentCaptor<AdServicesEncryptionKeyFetchedStats> argumentCaptor =
                ArgumentCaptor.forClass(AdServicesEncryptionKeyFetchedStats.class);
        verify(mStatsdLoggerMock).logEncryptionKeyFetchedStats(argumentCaptor.capture());
        expect.that(stats).isEqualTo(argumentCaptor.getValue());
    }

    @Test
    public void testLogEncryptionKeyDbTransactionEndedStats() {
        AdServicesEncryptionKeyDbTransactionEndedStats stats =
                AdServicesEncryptionKeyDbTransactionEndedStats.builder()
                        .setDbTransactionType(WRITE_TRANSACTION_TYPE)
                        .setDbTransactionStatus(INSERT_EXCEPTION)
                        .setMethodName(INSERT_KEY)
                        .build();
        mAdservicesLogger.logEncryptionKeyDbTransactionEndedStats(stats);
        ArgumentCaptor<AdServicesEncryptionKeyDbTransactionEndedStats> argumentCaptor =
                ArgumentCaptor.forClass(AdServicesEncryptionKeyDbTransactionEndedStats.class);
        verify(mStatsdLoggerMock).logEncryptionKeyDbTransactionEndedStats(argumentCaptor.capture());
        expect.that(stats).isEqualTo(argumentCaptor.getValue());
    }

    @Test
    public void testLogDestinationRegisteredBeaconsReportedStats() {
        List<DestinationRegisteredBeaconsReportedStats.InteractionKeySizeRangeType>
                keySizeRangeTypeList =
                        Arrays.asList(
                                DestinationRegisteredBeaconsReportedStats
                                        .InteractionKeySizeRangeType.LARGER_THAN_MAXIMUM_KEY_SIZE,
                                DestinationRegisteredBeaconsReportedStats
                                        .InteractionKeySizeRangeType.SMALLER_THAN_MAXIMUM_KEY_SIZE,
                                DestinationRegisteredBeaconsReportedStats
                                        .InteractionKeySizeRangeType.EQUAL_TO_MAXIMUM_KEY_SIZE);

        DestinationRegisteredBeaconsReportedStats stats =
                DestinationRegisteredBeaconsReportedStats.builder()
                        .setBeaconReportingDestinationType(
                                ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER)
                        .setAttemptedRegisteredBeacons(5)
                        .setAttemptedKeySizesRangeType(keySizeRangeTypeList)
                        .setTableNumRows(25)
                        .setAdServicesStatusCode(0)
                        .build();
        mAdservicesLogger.logDestinationRegisteredBeaconsReportedStats(stats);
        ArgumentCaptor<DestinationRegisteredBeaconsReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(DestinationRegisteredBeaconsReportedStats.class);
        verify(mStatsdLoggerMock)
                .logDestinationRegisteredBeaconsReportedStats(argumentCaptor.capture());
        expect.that(stats).isEqualTo(argumentCaptor.getValue());
    }

    @Test
    public void testLogReportInteractionApiCalledStats() {
        ReportInteractionApiCalledStats stats =
                ReportInteractionApiCalledStats.builder()
                        .setBeaconReportingDestinationType(
                                ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER)
                        .setNumMatchingUris(5)
                        .build();
        mAdservicesLogger.logReportInteractionApiCalledStats(stats);
        ArgumentCaptor<ReportInteractionApiCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(ReportInteractionApiCalledStats.class);
        verify(mStatsdLoggerMock).logReportInteractionApiCalledStats(argumentCaptor.capture());
        expect.that(stats).isEqualTo(argumentCaptor.getValue());
    }

    @Test
    public void testLogInteractionReportingTableClearedStats() {
        InteractionReportingTableClearedStats stats =
                InteractionReportingTableClearedStats.builder()
                        .setNumUrisCleared(100)
                        .setNumUnreportedUris(50)
                        .build();
        mAdservicesLogger.logInteractionReportingTableClearedStats(stats);
        ArgumentCaptor<InteractionReportingTableClearedStats> argumentCaptor =
                ArgumentCaptor.forClass(InteractionReportingTableClearedStats.class);
        verify(mStatsdLoggerMock)
                .logInteractionReportingTableClearedStats(argumentCaptor.capture());
        expect.that(stats).isEqualTo(argumentCaptor.getValue());
    }

    @Test
    public void testLogAppManifestConfigCall() {
        String pkgName = "pkg.I.am";
        @ApiType int apiType = AppManifestConfigCall.API_TOPICS;
        @Result int result = AppManifestConfigCall.RESULT_ALLOWED_APP_ALLOWS_ALL;
        AppManifestConfigCall call = new AppManifestConfigCall(pkgName, apiType);
        call.result = result;
        mAdservicesLogger.logAppManifestConfigCall(call);
        ArgumentCaptor<AppManifestConfigCall> argumentCaptor =
                ArgumentCaptor.forClass(AppManifestConfigCall.class);
        verify(mStatsdLoggerMock).logAppManifestConfigCall(argumentCaptor.capture());
        expect.that(argumentCaptor.getValue()).isEqualTo(call);
    }

    @Test
    public void testLogGetAdSelectionDataApiCalledStats() {
        GetAdSelectionDataApiCalledStats stats =
                GetAdSelectionDataApiCalledStats.builder()
                        .setPayloadSizeKb(64)
                        .setNumBuyers(3)
                        .setStatusCode(STATUS_SUCCESS)
                        .build();
        mAdservicesLogger.logGetAdSelectionDataApiCalledStats(stats);
        verify(mStatsdLoggerMock).logGetAdSelectionDataApiCalledStats(eq(stats));
    }

    @Test
    public void testLogGetAdSelectionDataApiCalledStats_withSourceCoordinator() {
        GetAdSelectionDataApiCalledStats stats =
                GetAdSelectionDataApiCalledStats.builder()
                        .setPayloadSizeKb(64)
                        .setNumBuyers(3)
                        .setStatusCode(STATUS_SUCCESS)
                        .setServerAuctionCoordinatorSource(
                                SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT)
                        .build();
        mAdservicesLogger.logGetAdSelectionDataApiCalledStats(stats);
        verify(mStatsdLoggerMock).logGetAdSelectionDataApiCalledStats(eq(stats));
    }

    @Test
    public void testlogGetAdSelectionDataBuyerInputGeneratedStats() {
        GetAdSelectionDataBuyerInputGeneratedStats stats =
                GetAdSelectionDataBuyerInputGeneratedStats.builder()
                        .setNumCustomAudiences(2)
                        .setNumCustomAudiencesOmitAds(1)
                        .setCustomAudienceSizeMeanB(23F)
                        .setCustomAudienceSizeVarianceB(24F)
                        .setTrustedBiddingSignalsKeysSizeMeanB(25F)
                        .setTrustedBiddingSignalsKeysSizeVarianceB(26F)
                        .setUserBiddingSignalsSizeMeanB(27F)
                        .setUserBiddingSignalsSizeVarianceB(28F)
                        .setNumEncodedSignals(29)
                        .setEncodedSignalsSizeMean(30)
                        .setEncodedSignalsSizeMax(31)
                        .setEncodedSignalsSizeMin(32)
                        .build();
        mAdservicesLogger.logGetAdSelectionDataBuyerInputGeneratedStats(stats);
        verify(mStatsdLoggerMock).logGetAdSelectionDataBuyerInputGeneratedStats(eq(stats));
    }

    @Test
    public void testLogAdFilteringProcessJoinCAReportedStats() {
        AdFilteringProcessJoinCAReportedStats stats =
                AdFilteringProcessJoinCAReportedStats.builder()
                        .setStatusCode(0)
                        .setCountOfAdsWithKeysMuchSmallerThanLimitation(1)
                        .setCountOfAdsWithKeysSmallerThanLimitation(2)
                        .setCountOfAdsWithKeysEqualToLimitation(3)
                        .setCountOfAdsWithKeysLargerThanLimitation(4)
                        .setCountOfAdsWithEmptyKeys(5)
                        .setCountOfAdsWithFiltersMuchSmallerThanLimitation(6)
                        .setCountOfAdsWithFiltersSmallerThanLimitation(7)
                        .setCountOfAdsWithFiltersEqualToLimitation(7)
                        .setCountOfAdsWithFiltersLargerThanLimitation(9)
                        .setCountOfAdsWithEmptyFilters(10)
                        .setTotalNumberOfUsedKeys(11)
                        .setTotalNumberOfUsedFilters(12)
                        .build();
        mAdservicesLogger.logAdFilteringProcessJoinCAReportedStats(stats);
        verify(mStatsdLoggerMock).logAdFilteringProcessJoinCAReportedStats(eq(stats));
    }

    @Test
    public void testLogAdFilteringProcessAdSelectionReportedStats() {
        AdFilteringProcessAdSelectionReportedStats stats =
                AdFilteringProcessAdSelectionReportedStats.builder()
                        .setLatencyInMillisOfAllAdFiltering(100)
                        .setLatencyInMillisOfAppInstallFiltering(1)
                        .setLatencyInMillisOfFcapFilters(200)
                        .setStatusCode(0)
                        .setNumOfAdsFilteredOutOfBidding(3)
                        .setNumOfCustomAudiencesFilteredOutOfBidding(5)
                        .setTotalNumOfAdsBeforeFiltering(7)
                        .setTotalNumOfCustomAudiencesBeforeFiltering(2)
                        .setNumOfPackageInAppInstallFilters(4)
                        .setNumOfDbOperations(6)
                        .setFilterProcessType(0)
                        .setNumOfContextualAdsFiltered(10)
                        .setNumOfAdCounterKeysInFcapFilters(1)
                        .setNumOfContextualAdsFilteredOutOfBiddingInvalidSignatures(2)
                        .setNumOfContextualAdsFilteredOutOfBiddingNoAds(3)
                        .setTotalNumOfContextualAdsBeforeFiltering(4)
                        .build();
        mAdservicesLogger.logAdFilteringProcessAdSelectionReportedStats(stats);
        verify(mStatsdLoggerMock).logAdFilteringProcessAdSelectionReportedStats(eq(stats));
    }

    @Test
    public void testLogAdCounterHistogramUpdaterReportedStats() {
        AdCounterHistogramUpdaterReportedStats stats =
                AdCounterHistogramUpdaterReportedStats.builder()
                        .setLatencyInMillis(100)
                        .setStatusCode(0)
                        .setTotalNumberOfEventsInDatabaseAfterInsert(1)
                        .setNumberOfInsertedEvent(2)
                        .setNumberOfEvictedEvent(3)
                        .build();
        mAdservicesLogger.logAdCounterHistogramUpdaterReportedStats(stats);
        verify(mStatsdLoggerMock).logAdCounterHistogramUpdaterReportedStats(eq(stats));
    }

    @Test
    public void testLogTopicsEncryptionEpochComputationReportedStats() {
        TopicsEncryptionEpochComputationReportedStats stats =
                TopicsEncryptionEpochComputationReportedStats.builder()
                        .setCountOfTopicsBeforeEncryption(10)
                        .setCountOfEmptyEncryptedTopics(9)
                        .setCountOfEncryptedTopics(8)
                        .setLatencyOfWholeEncryptionProcessMs(5)
                        .setLatencyOfEncryptionPerTopicMs(4)
                        .setLatencyOfPersistingEncryptedTopicsToDbMs(3)
                        .build();
        mAdservicesLogger.logTopicsEncryptionEpochComputationReportedStats(stats);
        verify(mStatsdLoggerMock).logTopicsEncryptionEpochComputationReportedStats(eq(stats));
    }

    @Test
    public void testLogTopicsEncryptionGetTopicsReportedStats() {
        TopicsEncryptionGetTopicsReportedStats stats =
                TopicsEncryptionGetTopicsReportedStats.builder()
                        .setCountOfEncryptedTopics(5)
                        .setLatencyOfReadingEncryptedTopicsFromDbMs(100)
                        .build();
        mAdservicesLogger.logTopicsEncryptionGetTopicsReportedStats(stats);
        verify(mStatsdLoggerMock).logTopicsEncryptionGetTopicsReportedStats(eq(stats));
    }

    @Test
    public void testLogShellCommandStats() {
        @ShellCommandStats.Command int command = ShellCommandStats.COMMAND_ECHO;
        @ShellCommandStats.CommandResult int result = ShellCommandStats.RESULT_SUCCESS;
        int latency = 1000;
        ShellCommandStats stats = new ShellCommandStats(command, result, latency);
        mAdservicesLogger.logShellCommandStats(stats);
        ArgumentCaptor<ShellCommandStats> argumentCaptor =
                ArgumentCaptor.forClass(ShellCommandStats.class);
        verify(mStatsdLoggerMock).logShellCommandStats(argumentCaptor.capture());
        expect.that(argumentCaptor.getValue()).isEqualTo(stats);
    }

    @Test
    public void testLogEncodingJsFetchStats() {
        EncodingFetchStats stats =
                EncodingFetchStats.builder()
                        .setJsDownloadTime(SIZE_MEDIUM)
                        .setHttpResponseCode(404)
                        .setFetchStatus(ENCODING_FETCH_STATUS_SUCCESS)
                        .setAdTechId("com.google.android")
                        .build();
        mAdservicesLogger.logEncodingJsFetchStats(stats);
        verify(mStatsdLoggerMock).logEncodingJsFetchStats(eq(stats));
    }

    @Test
    public void testlogUpdateSignalsApiCalledStats() {
        UpdateSignalsApiCalledStats stats =
                UpdateSignalsApiCalledStats.builder()
                        .setJsonProcessingStatus(JSON_PROCESSING_STATUS_TOO_BIG)
                        .setHttpResponseCode(404)
                        .setJsonSize(1000)
                        .setAdTechId("ABC123")
                        .setPackageUid(42)
                        .build();
        mAdservicesLogger.logUpdateSignalsApiCalledStats(stats);
        verify(mStatsdLoggerMock).logUpdateSignalsApiCalledStats(eq(stats));
    }

    @Test
    public void testLogEncodingJsExecutionStats() {
        EncodingJsExecutionStats stats =
                EncodingJsExecutionStats.builder()
                        .setRunStatus(JS_RUN_STATUS_OUTPUT_NON_ZERO_RESULT)
                        .setJsLatency(SIZE_SMALL)
                        .setAdTechId("123")
                        .setJsMemoryUsed(SIZE_LARGE)
                        .build();
        mAdservicesLogger.logEncodingJsExecutionStats(stats);
        verify(mStatsdLoggerMock).logEncodingJsExecutionStats(eq(stats));
    }

    @Test
    public void testLogEncodingJobRunStats() {
        EncodingJobRunStats stats =
                EncodingJobRunStats.builder()
                        .setSignalEncodingSuccesses(5)
                        .setSignalEncodingFailures(3)
                        .setSignalEncodingSkips(2)
                        .build();
        mAdservicesLogger.logEncodingJobRunStats(stats);
        verify(mStatsdLoggerMock).logEncodingJobRunStats(eq(stats));
    }

    @Test
    public void testLogPersistAdSelectionResultCalledStats() {
        PersistAdSelectionResultCalledStats stats =
                PersistAdSelectionResultCalledStats.builder()
                        .setWinnerType(WINNER_TYPE_PAS_WINNER)
                        .build();
        mAdservicesLogger.logPersistAdSelectionResultCalledStats(stats);
        verify(mStatsdLoggerMock).logPersistAdSelectionResultCalledStats(eq(stats));
    }

    @Test
    public void testLogSelectAdsFromOutcomesApiCalledStats() {
        SelectAdsFromOutcomesApiCalledStats stats =
                SelectAdsFromOutcomesApiCalledStats.builder()
                        .setCountIds(5)
                        .setCountNonExistingIds(2)
                        .setUsedPrebuilt(false)
                        .setDownloadResultCode(0)
                        .setDownloadLatencyMillis(350)
                        .setExecutionResultCode(1)
                        .setExecutionLatencyMillis(180)
                        .build();
        mAdservicesLogger.logSelectAdsFromOutcomesApiCalledStats(stats);
        verify(mStatsdLoggerMock).logSelectAdsFromOutcomesApiCalledStats(eq(stats));
    }

    private void mockAppNameApiErrorLogger() {
        when(mMockFlags.getCobaltLoggingEnabled()).thenReturn(true);
        when(mMockFlags.getAppNameApiErrorCobaltLoggingEnabled()).thenReturn(true);
        doReturn(mMockAppNameApiErrorLogger).when(() -> AppNameApiErrorLogger.getInstance());
    }

    private void mockMsmtRegistrationCobaltLogger(boolean isEeaDevice) {
        when(mMockFlags.getCobaltLoggingEnabled()).thenReturn(true);
        when(mMockFlags.getMsmtRegistrationCobaltLoggingEnabled()).thenReturn(true);
        when(mMockFlags.isEeaDevice()).thenReturn(isEeaDevice);
        doReturn(mMeasurementCobaltLogger).when(() -> MeasurementCobaltLogger.getInstance());
    }

    private void mockMsmtAttributionCobaltLogger() {
        when(mMockFlags.getCobaltLoggingEnabled()).thenReturn(true);
        when(mMockFlags.getMsmtAttributionCobaltLoggingEnabled()).thenReturn(true);
        doReturn(mMeasurementCobaltLogger).when(() -> MeasurementCobaltLogger.getInstance());
    }

    private void mockMsmtReportingCobaltLogger() {
        when(mMockFlags.getCobaltLoggingEnabled()).thenReturn(true);
        when(mMockFlags.getMsmtReportingCobaltLoggingEnabled()).thenReturn(true);
        doReturn(mMeasurementCobaltLogger).when(() -> MeasurementCobaltLogger.getInstance());
    }
}
