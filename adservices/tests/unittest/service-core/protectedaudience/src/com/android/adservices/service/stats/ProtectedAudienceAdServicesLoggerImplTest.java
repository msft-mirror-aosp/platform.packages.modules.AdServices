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
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.android.adservices.cobalt.AppNameApiErrorLogger;
import com.android.adservices.cobalt.MeasurementCobaltLogger;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.FlagsFactory;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

/** Unit tests for {@link AdServicesLoggerImpl}. */
@SpyStatic(FlagsFactory.class)
@SpyStatic(AppNameApiErrorLogger.class)
@SpyStatic(MeasurementCobaltLogger.class)
public final class ProtectedAudienceAdServicesLoggerImplTest
        extends AdServicesExtendedMockitoTestCase {

    @Mock private StatsdAdServicesLogger mStatsdLoggerMock;
    private AdServicesLoggerImpl mAdservicesLogger;

    @Before
    public void setUp() {
        mAdservicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
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
}
