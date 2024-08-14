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

import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.START_ELAPSED_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.STOP_ELAPSED_TIMESTAMP;

import android.adservices.common.AdSelectionSignals;

public class RunAdBiddingPerCAExecutionLoggerTestFixture {
    public static final long RUN_AD_BIDDING_PER_CA_START_TIMESTAMP = START_ELAPSED_TIMESTAMP + 1;
    public static final long GET_BUYER_DECISION_LOGIC_START_TIMESTAMP =
            RUN_AD_BIDDING_PER_CA_START_TIMESTAMP + 1;
    public static final int GET_BUYER_DECISION_LOGIC_LATENCY_IN_MS = 1;
    public static final long GET_BUYER_DECISION_LOGIC_END_TIMESTAMP =
            GET_BUYER_DECISION_LOGIC_START_TIMESTAMP + GET_BUYER_DECISION_LOGIC_LATENCY_IN_MS;
    public static final long RUN_BIDDING_START_TIMESTAMP =
            GET_BUYER_DECISION_LOGIC_END_TIMESTAMP + 1;
    public static final long GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP =
            RUN_BIDDING_START_TIMESTAMP + 1;
    public static final int GET_TRUSTED_BIDDING_SIGNALS_IN_MS = 1;
    public static final long GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP =
            GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP + GET_TRUSTED_BIDDING_SIGNALS_IN_MS;
    public static final long GENERATE_BIDS_START_TIMESTAMP =
            GET_BUYER_DECISION_LOGIC_END_TIMESTAMP + 1;
    public static final int GENERATE_BIDS_LATENCY_IN_MS = 1;
    public static final long GENERATE_BIDS_END_TIMESTAMP =
            GENERATE_BIDS_START_TIMESTAMP + GENERATE_BIDS_LATENCY_IN_MS;
    public static final long RUN_BIDDING_END_TIMESTAMP = GENERATE_BIDS_END_TIMESTAMP + 1;
    public static final int RUN_BIDDING_LATENCY_IN_MS =
            (int) (RUN_BIDDING_END_TIMESTAMP - RUN_BIDDING_START_TIMESTAMP);
    public static final int RUN_AD_BIDDING_PER_CA_LATENCY_IN_MS =
            (int) (STOP_ELAPSED_TIMESTAMP - RUN_AD_BIDDING_PER_CA_START_TIMESTAMP);
    public static final AdSelectionSignals TRUSTED_BIDDING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"example\": \"example\",\n"
                            + "\t\"valid\": \"Also valid\",\n"
                            + "\t\"list\": \"list\",\n"
                            + "\t\"of\": \"of\",\n"
                            + "\t\"keys\": \"trusted bidding signal Values\"\n"
                            + "}");
}
