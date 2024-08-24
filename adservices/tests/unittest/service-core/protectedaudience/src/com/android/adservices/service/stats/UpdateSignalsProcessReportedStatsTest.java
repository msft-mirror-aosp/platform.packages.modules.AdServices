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

import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_MEDIUM;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedStats;

import org.junit.Test;

public class UpdateSignalsProcessReportedStatsTest extends AdServicesUnitTestCase {
    private static final int UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS = 200;
    private static final int ADSERVICES_API_STATUS_CODE = STATUS_SUCCESS;
    private static final int SIGNALS_WRITTEN_COUNT = 10;
    private static final int KEYS_STORED_COUNT = 6;
    private static final int VALUES_STORED_COUNT = 10;
    private static final int EVICTION_RULES_COUNT = 8;
    private static final int PER_BUYER_SIGNAL_SIZE = SIZE_MEDIUM;
    private static final float MEAN_RAW_PROTECTED_SIGNALS_SIZE_BYTES = 123.4f;
    private static final float MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES = 345.67f;
    private static final float MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES = 0.0001f;

    @Test
    public void testBuildUpdateSignalsApiCalledStats() {
        UpdateSignalsProcessReportedStats stats =
                UpdateSignalsProcessReportedStats.builder()
                        .setUpdateSignalsProcessLatencyMillis(UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS)
                        .setAdservicesApiStatusCode(ADSERVICES_API_STATUS_CODE)
                        .setSignalsWrittenCount(SIGNALS_WRITTEN_COUNT)
                        .setKeysStoredCount(KEYS_STORED_COUNT)
                        .setValuesStoredCount(VALUES_STORED_COUNT)
                        .setEvictionRulesCount(EVICTION_RULES_COUNT)
                        .setPerBuyerSignalSize(PER_BUYER_SIGNAL_SIZE)
                        .setMeanRawProtectedSignalsSizeBytes(MEAN_RAW_PROTECTED_SIGNALS_SIZE_BYTES)
                        .setMaxRawProtectedSignalsSizeBytes(MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES)
                        .setMinRawProtectedSignalsSizeBytes(MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES)
                        .build();

        expect.that(stats.getUpdateSignalsProcessLatencyMillis())
                .isEqualTo(UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS);
        expect.that(stats.getAdservicesApiStatusCode()).isEqualTo(ADSERVICES_API_STATUS_CODE);
        expect.that(stats.getSignalsWrittenCount()).isEqualTo(SIGNALS_WRITTEN_COUNT);
        expect.that(stats.getKeysStoredCount()).isEqualTo(KEYS_STORED_COUNT);
        expect.that(stats.getValuesStoredCount()).isEqualTo(VALUES_STORED_COUNT);
        expect.that(stats.getEvictionRulesCount()).isEqualTo(EVICTION_RULES_COUNT);
        expect.that(stats.getPerBuyerSignalSize()).isEqualTo(PER_BUYER_SIGNAL_SIZE);
        expect.that(stats.getMeanRawProtectedSignalsSizeBytes())
                .isEqualTo(MEAN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMaxRawProtectedSignalsSizeBytes())
                .isEqualTo(MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMinRawProtectedSignalsSizeBytes())
                .isEqualTo(MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
    }
}
