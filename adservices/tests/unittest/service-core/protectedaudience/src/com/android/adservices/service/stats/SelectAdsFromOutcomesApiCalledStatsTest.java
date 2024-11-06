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

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public class SelectAdsFromOutcomesApiCalledStatsTest extends AdServicesUnitTestCase {

    private static final int COUNT_IDS = 5;
    private static final int COUNT_NON_EXISTING_IDS = 1;
    private static final boolean USED_PREBUILT_SCRIPT = false;
    private static final int DOWNLOAD_RESULT_CODE = 0;
    private static final int DOWNLOAD_LATENCY_MILLIS = 200;
    private static final int EXECUTION_RESULT_CODE = 0;
    private static final int EXECUTION_LATENCY_MILLIS = 150;

    @Test
    public void testBuildSelectAdsFromOutcomesApiCalledStats() {
        SelectAdsFromOutcomesApiCalledStats stats =
                SelectAdsFromOutcomesApiCalledStats.builder()
                        .setCountIds(COUNT_IDS)
                        .setCountNonExistingIds(COUNT_NON_EXISTING_IDS)
                        .setUsedPrebuilt(USED_PREBUILT_SCRIPT)
                        .setDownloadResultCode(DOWNLOAD_RESULT_CODE)
                        .setDownloadLatencyMillis(DOWNLOAD_LATENCY_MILLIS)
                        .setExecutionResultCode(EXECUTION_RESULT_CODE)
                        .setExecutionLatencyMillis(EXECUTION_LATENCY_MILLIS)
                        .build();

        expect.that(stats.getCountIds()).isEqualTo(COUNT_IDS);
        expect.that(stats.getCountNonExistingIds()).isEqualTo(COUNT_NON_EXISTING_IDS);
        expect.that(stats.getUsedPrebuilt()).isEqualTo(USED_PREBUILT_SCRIPT);
        expect.that(stats.getDownloadResultCode()).isEqualTo(DOWNLOAD_RESULT_CODE);
        expect.that(stats.getDownloadLatencyMillis()).isEqualTo(DOWNLOAD_LATENCY_MILLIS);
        expect.that(stats.getExecutionResultCode()).isEqualTo(EXECUTION_RESULT_CODE);
        expect.that(stats.getExecutionLatencyMillis()).isEqualTo(EXECUTION_LATENCY_MILLIS);
    }
}
