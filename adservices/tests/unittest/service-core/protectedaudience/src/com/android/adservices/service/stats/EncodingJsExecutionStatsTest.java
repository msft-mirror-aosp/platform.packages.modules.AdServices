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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_SUCCESS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_MEDIUM;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_UNSET;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.service.stats.pas.EncodingJsExecutionStats;

import org.junit.Test;

public class EncodingJsExecutionStatsTest extends AdServicesUnitTestCase {
    private static final int JS_LATENCY = 123;
    private static final int ENCODED_SIGNALS_SIZE = SIZE_MEDIUM;
    private static final int RUN_STATUS = JS_RUN_STATUS_SUCCESS;
    private static final int JS_MEMORY_USED = 20;
    private static final String AD_TECH_ID = "com.google.android";

    @Test
    public void testBuildEncodingJsExecutionStats() {
        EncodingJsExecutionStats stats =
                EncodingJsExecutionStats.builder()
                        .setJsLatency(JS_LATENCY)
                        .setEncodedSignalsSize(ENCODED_SIGNALS_SIZE)
                        .setRunStatus(RUN_STATUS)
                        .setJsMemoryUsed(JS_MEMORY_USED)
                        .setAdTechId(AD_TECH_ID)
                        .build();

        expect.that(stats.getJsLatency()).isEqualTo(JS_LATENCY);
        expect.that(stats.getEncodedSignalsSize()).isEqualTo(ENCODED_SIGNALS_SIZE);
        expect.that(stats.getRunStatus()).isEqualTo(RUN_STATUS);
        expect.that(stats.getJsMemoryUsed()).isEqualTo(JS_MEMORY_USED);
        expect.that(stats.getAdTechId()).isEqualTo(AD_TECH_ID);
    }

    @Test
    public void testBuildEncodingJsExecutionStats_defaultValue() {
        EncodingJsExecutionStats stats =
                EncodingJsExecutionStats.builder().build();

        expect.that(stats.getJsLatency()).isEqualTo(SIZE_UNSET);
        expect.that(stats.getEncodedSignalsSize()).isEqualTo(SIZE_UNSET);
        expect.that(stats.getRunStatus()).isEqualTo(JS_RUN_STATUS_UNSET);
        expect.that(stats.getJsMemoryUsed()).isEqualTo(0);
        expect.that(stats.getAdTechId()).isEqualTo("");
    }
}
