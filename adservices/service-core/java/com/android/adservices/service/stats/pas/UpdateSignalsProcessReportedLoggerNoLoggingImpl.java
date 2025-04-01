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

import static com.android.adservices.service.signals.SignalUpdates.UpdateSchemaVersion;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SignalEvictorType;

import com.android.adservices.service.signals.evict.EvictionPriority;

import java.util.Set;

public class UpdateSignalsProcessReportedLoggerNoLoggingImpl
        implements UpdateSignalsProcessReportedLogger {
    @Override
    public void logUpdateSignalsProcessReportedStats() {}

    @Override
    public void setUpdateSignalsStartTimestamp(long updateSignalsStartTimestamp) {}

    @Override
    public void setAdservicesApiStatusCode(int adservicesApiStatusCode) {}

    @Override
    public void setSignalsWrittenAndValuesCount(int signalsWrittenAndValuesCount) {}

    @Override
    public void setKeysStoredCount(int keysStoredCount) {}

    @Override
    public void setEvictionRulesCount(int evictionRulesCount) {}

    @Override
    public void setPerBuyerSignalSize(int perBuyerSignalSize) {}

    @Override
    public void setMaxRawProtectedSignalsSizeBytes(float maxRawProtectedSignalsSizeBytes) {}

    @Override
    public void setMinRawProtectedSignalsSizeBytes(float minRawProtectedSignalsSizeBytes) {}

    @Override
    public void setSignalEvictorsUsed(Set<@SignalEvictorType Integer> evictorTypes) {}

    @Override
    public void addSignalEvictorUsed(@SignalEvictorType int evictorType) {}

    @Override
    public void setUpdatedSignalEvictionPriorities(Set<EvictionPriority> evictionPriorities) {}

    @Override
    public void addUpdatedSignalEvictionPriority(EvictionPriority evictionPriority) {}

    @Override
    public void setEvictedSignalEvictionPriorities(Set<EvictionPriority> evictionPriorities) {}

    @Override
    public void addEvictedSignalEvictionPriority(EvictionPriority evictionPriority) {}

    @Override
    public void setPerBuyerEvictedSignalSize(int evictedSignalSize) {}

    @Override
    public void setUpdatedSignalsWithEvictionPriorityForCount(
            Set<String> updatedSignalsWithEvictionPriority) {}

    @Override
    public void addUpdatedSignalWithEvictionPriorityForCount(String key) {}

    @Override
    public void setSignalUpdateSchemaVersion(@UpdateSchemaVersion int updateSchemaVersion) {}
}
