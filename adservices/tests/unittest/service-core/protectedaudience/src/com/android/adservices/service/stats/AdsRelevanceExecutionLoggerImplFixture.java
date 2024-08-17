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

public final class AdsRelevanceExecutionLoggerImplFixture {
    public static final int GET_AD_SELECTION_DATA_LATENCY_MS = 1;
    public static final int PERSIST_AD_SELECTION_RESULT_LATENCY_MS = 1;

    public static final long BINDER_ELAPSED_TIMESTAMP = 90L;

    public static final CallerMetadata sCallerMetadata =
            new CallerMetadata.Builder()
                    .setBinderElapsedTimestamp(BINDER_ELAPSED_TIMESTAMP)
                    .build();
    private static final int BINDER_LATENCY_MS = 2;

    public static final long GET_AD_SELECTION_DATA_START_TIMESTAMP =
            BINDER_ELAPSED_TIMESTAMP + (long) BINDER_LATENCY_MS / 2;
    public static final long GET_AD_SELECTION_DATA_END_TIMESTAMP =
            GET_AD_SELECTION_DATA_START_TIMESTAMP + GET_AD_SELECTION_DATA_LATENCY_MS;
    public static final int GET_AD_SELECTION_DATA_INTERNAL_FINAL_LATENCY_MS =
            (int) (GET_AD_SELECTION_DATA_END_TIMESTAMP - GET_AD_SELECTION_DATA_START_TIMESTAMP);
    public static final int GET_AD_SELECTION_DATA_OVERALL_LATENCY_MS =
            BINDER_LATENCY_MS + GET_AD_SELECTION_DATA_INTERNAL_FINAL_LATENCY_MS;

    public static final long PERSIST_AD_SELECTION_RESULT_START_TIMESTAMP =
            BINDER_ELAPSED_TIMESTAMP + (long) BINDER_LATENCY_MS / 2;
    public static final long PERSIST_AD_SELECTION_RESULT_END_TIMESTAMP =
            PERSIST_AD_SELECTION_RESULT_START_TIMESTAMP + PERSIST_AD_SELECTION_RESULT_LATENCY_MS;
    public static final int PERSIST_AD_SELECTION_RESULT_INTERNAL_FINAL_LATENCY_MS =
            (int)
                    (PERSIST_AD_SELECTION_RESULT_END_TIMESTAMP
                            - PERSIST_AD_SELECTION_RESULT_START_TIMESTAMP);
    public static final int PERSIST_AD_SELECTION_RESULT_OVERALL_LATENCY_MS =
            BINDER_LATENCY_MS + PERSIST_AD_SELECTION_RESULT_INTERNAL_FINAL_LATENCY_MS;
}
