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

public class TopicsEncryptionEpochComputationReportedStatsTest extends AdServicesUnitTestCase {
    private static final int COUNT_OF_TOPICS_BEFORE_ENCRYPTION = 5;
    private static final int COUNT_OF_EMPTY_ENCRYPTED_TOPICS = 3;
    private static final int COUNT_OF_ENCRYPTED_TOPICS = 2;
    private static final int LATENCY_OF_WHOLE_ENCRYPTION_PROCESS_MS = 100;
    private static final int LATENCY_OF_ENCRYPTION_PER_TOPIC_MS = 50;
    private static final int LATENCY_OF_PERSISTING_ENCRYPTED_TOPICS_TO_DB_MS = 10;

    @Test
    public void testBuildTopicsEncryptionEpochComputationReportedStats() {
        TopicsEncryptionEpochComputationReportedStats stats =
                TopicsEncryptionEpochComputationReportedStats.builder()
                        .setCountOfTopicsBeforeEncryption(COUNT_OF_TOPICS_BEFORE_ENCRYPTION)
                        .setCountOfEmptyEncryptedTopics(COUNT_OF_EMPTY_ENCRYPTED_TOPICS)
                        .setCountOfEncryptedTopics(COUNT_OF_ENCRYPTED_TOPICS)
                        .setLatencyOfWholeEncryptionProcessMs(
                                LATENCY_OF_WHOLE_ENCRYPTION_PROCESS_MS)
                        .setLatencyOfEncryptionPerTopicMs(LATENCY_OF_ENCRYPTION_PER_TOPIC_MS)
                        .setLatencyOfPersistingEncryptedTopicsToDbMs(
                                LATENCY_OF_PERSISTING_ENCRYPTED_TOPICS_TO_DB_MS)
                        .build();

        expect.that(stats.getCountOfTopicsBeforeEncryption())
                .isEqualTo(COUNT_OF_TOPICS_BEFORE_ENCRYPTION);
        expect.that(stats.getCountOfEmptyEncryptedTopics())
                .isEqualTo(COUNT_OF_EMPTY_ENCRYPTED_TOPICS);
        expect.that(stats.getCountOfEncryptedTopics()).isEqualTo(COUNT_OF_ENCRYPTED_TOPICS);
        expect.that(stats.getLatencyOfWholeEncryptionProcessMs())
                .isEqualTo(LATENCY_OF_WHOLE_ENCRYPTION_PROCESS_MS);
        expect.that(stats.getLatencyOfEncryptionPerTopicMs())
                .isEqualTo(LATENCY_OF_ENCRYPTION_PER_TOPIC_MS);
        expect.that(stats.getLatencyOfPersistingEncryptedTopicsToDbMs())
                .isEqualTo(LATENCY_OF_PERSISTING_ENCRYPTED_TOPICS_TO_DB_MS);
    }
}
