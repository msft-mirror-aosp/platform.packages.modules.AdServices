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

public final class TopicsEncryptionGetTopicsReportedStatsTest extends AdServicesUnitTestCase {
    private static final int COUNT_OF_ENCRYPTED_TOPICS = 5;
    private static final int LATENCY_OF_READING_ENCRYPTED_TOPICS_FROM_DB_MS = 10;

    @Test
    public void testBuildTopicsEncryptionGetTopicsReportedStats() {
        TopicsEncryptionGetTopicsReportedStats stats =
                TopicsEncryptionGetTopicsReportedStats.builder()
                        .setCountOfEncryptedTopics(COUNT_OF_ENCRYPTED_TOPICS)
                        .setLatencyOfReadingEncryptedTopicsFromDbMs(
                                LATENCY_OF_READING_ENCRYPTED_TOPICS_FROM_DB_MS)
                        .build();

        expect.that(stats.getCountOfEncryptedTopics()).isEqualTo(COUNT_OF_ENCRYPTED_TOPICS);
        expect.that(stats.getLatencyOfReadingEncryptedTopicsFromDbMs())
                .isEqualTo(LATENCY_OF_READING_ENCRYPTED_TOPICS_FROM_DB_MS);
    }
}
