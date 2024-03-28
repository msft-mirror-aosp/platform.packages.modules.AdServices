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

import com.google.auto.value.AutoValue;

/** Class for logging Topics encryption during epoch computation. */
@AutoValue
public abstract class TopicsEncryptionEpochComputationReportedStats {
    /** Returns number of topics before encryption during epoch computation process. */
    public abstract int getCountOfTopicsBeforeEncryption();

    /** Returns number of empty encrypted topics during epoch computation process. */
    public abstract int getCountOfEmptyEncryptedTopics();

    /** Returns number of encrypted topics during epoch computation process. */
    public abstract int getCountOfEncryptedTopics();

    /** Returns the latency in milliseconds of the whole encryption process
     * during epoch computation. */
    public abstract int getLatencyOfWholeEncryptionProcessMs();

    /** Returns the latency in milliseconds of encryption of each topic. */
    public abstract int getLatencyOfEncryptionPerTopicMs();

    /** Returns the latency in milliseconds of persisting encrypted topics to database
     * during epoch computation. */
    public abstract int getLatencyOfPersistingEncryptedTopicsToDbMs();

    /** Returns generic builder. */
    public static Builder builder() {
        return new AutoValue_TopicsEncryptionEpochComputationReportedStats.Builder();
    }

    /** Builder class for TopicsEncryptionEpochComputationReportedStats. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setCountOfTopicsBeforeEncryption(int value);

        public abstract Builder setCountOfEmptyEncryptedTopics(int value);

        public abstract Builder setCountOfEncryptedTopics(int value);

        public abstract Builder setLatencyOfWholeEncryptionProcessMs(int value);

        public abstract Builder setLatencyOfEncryptionPerTopicMs(int value);

        public abstract Builder setLatencyOfPersistingEncryptedTopicsToDbMs(int value);

        public abstract TopicsEncryptionEpochComputationReportedStats build();
    }
}
