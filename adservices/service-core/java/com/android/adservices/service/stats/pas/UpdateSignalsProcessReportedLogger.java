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

package com.android.adservices.service.stats.pas;

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SignalEvictorType;

import com.android.adservices.service.signals.evict.EvictionPriority;

import java.util.Set;

public interface UpdateSignalsProcessReportedLogger {
    /** Invokes the logger to log {@link UpdateSignalsProcessReportedStats}. */
    void logUpdateSignalsProcessReportedStats();

    /** Sets the timestamp to start the update signals process. */
    void setUpdateSignalsStartTimestamp(long updateSignalsStartTimestamp);

    /** Sets the AdServices API status code for this API call. */
    void setAdservicesApiStatusCode(int adservicesApiStatusCode);

    /** Sets the number of signals written for this API call. */
    void setSignalsWrittenAndValuesCount(int signalsWrittenAndValuesCount);

    /** Sets the number of keys from downloaded JSON for this API call. */
    void setKeysStoredCount(int keysStoredCount);

    /** Sets the number of eviction rules for this API call. */
    void setEvictionRulesCount(int evictionRulesCount);

    /** Sets the total size, in bytes, of the raw protected signals stored by the caller. */
    void setPerBuyerSignalSize(int perBuyerSignalSize);

    /** Sets the size, in bytes, of the largest raw protected signal stored by the caller. */
    void setMaxRawProtectedSignalsSizeBytes(float maxRawProtectedSignalsSizeBytes);

    /** Sets the size, in bytes, of the smallest raw protected signal stored by the caller. */
    void setMinRawProtectedSignalsSizeBytes(float minRawProtectedSignalsSizeBytes);

    /** Sets the unique values of Evictor types used in an eviction. */
    void setSignalEvictorsUsed(Set<@SignalEvictorType Integer> evictorTypes);

    /** Adds an Evictor type to the set. */
    void addSignalEvictorUsed(@SignalEvictorType int evictorType);

    /** Sets the unique values of Eviction priorities included in a Signals update. */
    void setUpdatedSignalEvictionPriorities(Set<EvictionPriority> evictionPriorities);

    /** Adds an Eviction priority to the set. */
    void addUpdatedSignalEvictionPriority(EvictionPriority evictionPriority);

    /** Sets the unique values of Eviction priorities in evicted Signals. */
    void setEvictedSignalEvictionPriorities(Set<EvictionPriority> evictionPriorities);

    /** Adds an Eviction priority to the evicted set. */
    void addEvictedSignalEvictionPriority(EvictionPriority evictionPriority);

    /** Sets the raw byte size of evicted Signals. */
    void setPerBuyerEvictedSignalSize(int evictedSignalSize);

    /**
     * Sets the unique keys in the update JSON who have an eviction priority assigned. The keys will
     * not be logged, while the intention is to collect the size of the keys by collecting the keys
     * first.
     */
    void setUpdatedSignalsWithEvictionPriorityForCount(
            Set<String> updatedSignalsWithEvictionPriority);

    /** Adds a key in the update JSON who have an eviction priority assigned. */
    void addUpdatedSignalWithEvictionPriorityForCount(String key);

    /** Sets the X-UPDATE-SCHEMA-VERSION from the update schema response header */
    void setSignalUpdateSchemaVersion(int updateSchemaVersion);
}
