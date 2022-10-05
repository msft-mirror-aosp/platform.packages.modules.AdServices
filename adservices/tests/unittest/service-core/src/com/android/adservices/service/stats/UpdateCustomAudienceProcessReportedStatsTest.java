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

import static com.android.adservices.service.stats.AdServicesStatsLog.RUN_AD_BIDDING_PROCESS_REPORTED__RUN_AD_BIDDING_RESULT_CODE__RUN_AD_SELECTION_STATUS_SUCCESS;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Unit tests for {@link UpdateCustomAudienceProcessReportedStats}. */
public class UpdateCustomAudienceProcessReportedStatsTest {
    private static final int LATENCY_IN_MILLIS = 10;
    private static final int RESULT_CODE =
            RUN_AD_BIDDING_PROCESS_REPORTED__RUN_AD_BIDDING_RESULT_CODE__RUN_AD_SELECTION_STATUS_SUCCESS;
    private static final int DATA_SIZE_OF_ADS_IN_BYTES = 10;
    private static final int NUM_OF_ADS = 5;

    @Test
    public void testBuilderCreateSuccess() {
        UpdateCustomAudienceProcessReportedStats stats =
                UpdateCustomAudienceProcessReportedStats.builder()
                        .setLatencyInMills(LATENCY_IN_MILLIS)
                        .setResultCode(RESULT_CODE)
                        .setDataSizeOfAdsInBytes(DATA_SIZE_OF_ADS_IN_BYTES)
                        .setNumOfAds(NUM_OF_ADS)
                        .build();
        assertEquals(LATENCY_IN_MILLIS, stats.getLatencyInMills());
        assertEquals(RESULT_CODE, stats.getResultCode());
        assertEquals(DATA_SIZE_OF_ADS_IN_BYTES, stats.getDataSizeOfAdsInBytes());
        assertEquals(NUM_OF_ADS, stats.getNumOfAds());
    }
}
