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

import static com.android.adservices.service.stats.AdServicesStatsLog.RUN_AD_BIDDING_PROCESS_REPORTED__GET_BUYERS_CUSTOM_AUDIENCE_RESULT_CODE__RUN_AD_SELECTION_STATUS_SUCCESS;
import static com.android.adservices.service.stats.AdServicesStatsLog.RUN_AD_BIDDING_PROCESS_REPORTED__RUN_AD_BIDDING_RESULT_CODE__RUN_AD_SELECTION_STATUS_SUCCESS;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Unit tests for {@link RunAdBiddingProcessReportedStats}. */
public class RunAdBiddingProcessReportedStatsTest {
    private static final int GET_BUYERS_CUSTOM_AUDIENCE_LATENCY_IN_MILLIS = 10;
    private static final int NUM_BUYERS_REQUESTED = 5;
    private static final int NUM_BUYERS_FETCHED = 3;
    private static final int NUM_OF_ADS_ENTERING_BIDDING = 20;
    private static final int NUM_OF_CAS_ENTERING_BIDDING = 10;
    private static final int NUM_OF_CAS_POSTING_BIDDING = 2;
    private static final float RATIO_OF_CAS_SELECTING_RMKT_ADS = 0.80f;
    private static final int RUN_AD_BIDDING_LATENCY_IN_MILLIS = 10;
    private static final int TOTAL_AD_BIDDING_STAGE_LATENCY_IN_MILLIS = 20;
    private static final int GET_BUYERS_CUSTOM_AUDIENCE_RESULT_CODE =
            RUN_AD_BIDDING_PROCESS_REPORTED__GET_BUYERS_CUSTOM_AUDIENCE_RESULT_CODE__RUN_AD_SELECTION_STATUS_SUCCESS;
    private static final int RUN_AD_BIDDING_RESULT_CODE =
            RUN_AD_BIDDING_PROCESS_REPORTED__RUN_AD_BIDDING_RESULT_CODE__RUN_AD_SELECTION_STATUS_SUCCESS;

    @Test
    public void testBuilderCreateSuccess() {
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
        assertEquals(
                GET_BUYERS_CUSTOM_AUDIENCE_LATENCY_IN_MILLIS,
                stats.getGetBuyersCustomAudienceLatencyInMills());
        assertEquals(
                GET_BUYERS_CUSTOM_AUDIENCE_RESULT_CODE,
                stats.getGetBuyersCustomAudienceResultCode());
        assertEquals(NUM_BUYERS_REQUESTED, stats.getNumBuyersRequested());
        assertEquals(NUM_BUYERS_FETCHED, stats.getNumBuyersFetched());
        assertEquals(NUM_OF_ADS_ENTERING_BIDDING, stats.getNumOfAdsEnteringBidding());
        assertEquals(NUM_OF_CAS_ENTERING_BIDDING, stats.getNumOfCasEnteringBidding());
        assertEquals(NUM_OF_CAS_POSTING_BIDDING, stats.getNumOfCasPostBidding());
        assertEquals(RATIO_OF_CAS_SELECTING_RMKT_ADS, stats.getRatioOfCasSelectingRmktAds(), 0.0f);
        assertEquals(RUN_AD_BIDDING_LATENCY_IN_MILLIS, stats.getRunAdBiddingLatencyInMillis());
        assertEquals(RUN_AD_BIDDING_RESULT_CODE, stats.getRunAdBiddingResultCode());
        assertEquals(
                TOTAL_AD_BIDDING_STAGE_LATENCY_IN_MILLIS,
                stats.getTotalAdBiddingStageLatencyInMillis());
    }
}
