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
import com.android.adservices.service.stats.pas.EncodingJobRunStats;

import org.junit.Test;

public class EncodingJobRunStatsTest extends AdServicesUnitTestCase {
    private static final int SIGNAL_ENCODING_SUCCESSES = 5;
    private static final int SIGNAL_ENCODING_FAILURE = 4;
    private static final int SIGNAL_ENCODING_SKIPS = 3;

    @Test
    public void testBuildEncodingJobRunStats() {
        EncodingJobRunStats stats =
                EncodingJobRunStats.builder()
                        .setSignalEncodingSuccesses(SIGNAL_ENCODING_SUCCESSES)
                        .setSignalEncodingFailures(SIGNAL_ENCODING_FAILURE)
                        .setSignalEncodingSkips(SIGNAL_ENCODING_SKIPS)
                        .build();

        expect.that(stats.getSignalEncodingSuccesses()).isEqualTo(SIGNAL_ENCODING_SUCCESSES);
        expect.that(stats.getSignalEncodingFailures()).isEqualTo(SIGNAL_ENCODING_FAILURE);
        expect.that(stats.getSignalEncodingSkips()).isEqualTo(SIGNAL_ENCODING_SKIPS);
    }
}
