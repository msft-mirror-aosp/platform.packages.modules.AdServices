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

public final class AdCounterHistogramUpdaterReportedStatsTest extends AdServicesUnitTestCase {
    private static final int LATENCY_IN_MILLIS = 100;
    private static final int STATUS_CODE = 0;
    private static final int TOTAL_NUMBER_OF_EVENTS_IN_DATABASE_AFTER_INSERT = 20;
    private static final int NUMBER_OF_INSERTED_EVENT = 25;
    private static final int NUMBER_OF_EVICTED_EVENT = 25;

    @Test
    public void testBuildAdCounterHistogramUpdaterReportedStats() {
        AdCounterHistogramUpdaterReportedStats stats =
                AdCounterHistogramUpdaterReportedStats.builder()
                        .setLatencyInMillis(LATENCY_IN_MILLIS)
                        .setStatusCode(STATUS_CODE)
                        .setTotalNumberOfEventsInDatabaseAfterInsert(
                                TOTAL_NUMBER_OF_EVENTS_IN_DATABASE_AFTER_INSERT)
                        .setNumberOfInsertedEvent(NUMBER_OF_INSERTED_EVENT)
                        .setNumberOfEvictedEvent(NUMBER_OF_EVICTED_EVENT)
                        .build();

        expect.that(stats.getStatusCode()).isEqualTo(STATUS_CODE);
        expect.that(stats.getLatencyInMillis()).isEqualTo(LATENCY_IN_MILLIS);
        expect.that(stats.getTotalNumberOfEventsInDatabaseAfterInsert())
                .isEqualTo(TOTAL_NUMBER_OF_EVENTS_IN_DATABASE_AFTER_INSERT);
        expect.that(stats.getNumberOfInsertedEvent()).isEqualTo(NUMBER_OF_INSERTED_EVENT);
        expect.that(stats.getNumberOfEvictedEvent()).isEqualTo(NUMBER_OF_EVICTED_EVENT);
    }
}
