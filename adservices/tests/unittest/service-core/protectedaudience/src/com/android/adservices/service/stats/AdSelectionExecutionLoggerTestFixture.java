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

package com.android.adservices.service.stats;

import android.adservices.common.CallerMetadata;

public class AdSelectionExecutionLoggerTestFixture {
    public static final int GET_BUYERS_CUSTOM_AUDIENCE_LATENCY_MS = 1;
    public static final int RUN_AD_BIDDING_LATENCY_MS = 1;
    public static final int GET_AD_SELECTION_LOGIC_LATENCY_MS = 1;
    public static final int GET_TRUSTED_SCORING_SIGNALS_LATENCY_MS = 1;
    public static final int SCORE_ADS_LATENCY_MS = 1;
    public static final int PERSIST_AD_SELECTION_LATENCY_MS = 1;
    public static final long DB_AD_SELECTION_FILE_SIZE = 10L;
    public static final boolean IS_RMKT_ADS_WON_UNSET = false;
    public static final boolean IS_RMKT_ADS_WON = true;

    private static final long BINDER_ELAPSED_TIMESTAMP = 90L;
    public static final CallerMetadata sCallerMetadata =
            new CallerMetadata.Builder()
                    .setBinderElapsedTimestamp(BINDER_ELAPSED_TIMESTAMP)
                    .build();
    private static final int BINDER_LATENCY_MS = 2;
    public static final long START_ELAPSED_TIMESTAMP =
            BINDER_ELAPSED_TIMESTAMP + (long) BINDER_LATENCY_MS / 2;

    public static final long BIDDING_STAGE_START_TIMESTAMP = START_ELAPSED_TIMESTAMP + 1L;
    public static final long GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP =
            BIDDING_STAGE_START_TIMESTAMP + GET_BUYERS_CUSTOM_AUDIENCE_LATENCY_MS;
    public static final long RUN_AD_BIDDING_START_TIMESTAMP =
            GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP + 1L;
    public static final long RUN_AD_BIDDING_END_TIMESTAMP =
            RUN_AD_BIDDING_START_TIMESTAMP + RUN_AD_BIDDING_LATENCY_MS;
    public static final long BIDDING_STAGE_END_TIMESTAMP = RUN_AD_BIDDING_END_TIMESTAMP;
    public static final long TOTAL_BIDDING_STAGE_LATENCY_IN_MS =
            BIDDING_STAGE_END_TIMESTAMP - BIDDING_STAGE_START_TIMESTAMP;
    public static final long RUN_AD_SCORING_START_TIMESTAMP = RUN_AD_BIDDING_END_TIMESTAMP;
    public static final long GET_AD_SELECTION_LOGIC_START_TIMESTAMP =
            RUN_AD_SCORING_START_TIMESTAMP + 1L;
    public static final long GET_AD_SELECTION_LOGIC_END_TIMESTAMP =
            GET_AD_SELECTION_LOGIC_START_TIMESTAMP + GET_AD_SELECTION_LOGIC_LATENCY_MS;
    public static final long GET_AD_SCORES_START_TIMESTAMP = GET_AD_SELECTION_LOGIC_END_TIMESTAMP;
    public static final long GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP =
            GET_AD_SCORES_START_TIMESTAMP + 1;
    public static final long GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP =
            GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP + GET_TRUSTED_SCORING_SIGNALS_LATENCY_MS;
    public static final long SCORE_ADS_START_TIMESTAMP =
            GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP + 1L;
    public static final long SCORE_ADS_END_TIMESTAMP =
            SCORE_ADS_START_TIMESTAMP + SCORE_ADS_LATENCY_MS;
    public static final long GET_AD_SCORES_END_TIMESTAMP = SCORE_ADS_END_TIMESTAMP;
    public static final int GET_AD_SCORES_LATENCY_MS =
            (int) (GET_AD_SCORES_END_TIMESTAMP - GET_AD_SCORES_START_TIMESTAMP);
    public static final long RUN_AD_SCORING_END_TIMESTAMP = GET_AD_SCORES_END_TIMESTAMP + 1L;
    public static final int RUN_AD_SCORING_LATENCY_MS =
            (int) (RUN_AD_SCORING_END_TIMESTAMP - RUN_AD_SCORING_START_TIMESTAMP);
    public static final long PERSIST_AD_SELECTION_START_TIMESTAMP =
            RUN_AD_SCORING_END_TIMESTAMP + 1;
    public static final long PERSIST_AD_SELECTION_END_TIMESTAMP =
            PERSIST_AD_SELECTION_START_TIMESTAMP + PERSIST_AD_SELECTION_LATENCY_MS;
    public static final long STOP_ELAPSED_TIMESTAMP = PERSIST_AD_SELECTION_END_TIMESTAMP + 1;
    public static final int RUN_AD_SELECTION_INTERNAL_FINAL_LATENCY_MS =
            (int) (STOP_ELAPSED_TIMESTAMP - START_ELAPSED_TIMESTAMP);
    public static final int RUN_AD_SELECTION_OVERALL_LATENCY_MS =
            BINDER_LATENCY_MS + RUN_AD_SELECTION_INTERNAL_FINAL_LATENCY_MS;
}
