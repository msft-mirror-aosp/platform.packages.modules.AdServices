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
import static com.android.adservices.service.stats.AdServicesLoggerUtil.FIELD_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.PER_BUYER_SIGNAL_SIZE_BUCKETS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.computeSize;

import com.android.adservices.service.signals.evict.EvictionPriority;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdsRelevanceStatusUtils.SignalEvictorType;
import com.android.adservices.shared.util.Clock;

import java.util.HashSet;
import java.util.Set;

public class UpdateSignalsProcessReportedLoggerImpl implements UpdateSignalsProcessReportedLogger {

    private final AdServicesLogger mAdServicesLogger;
    private final Clock mClock;
    private long mUpdateSignalsStartTimestamp;
    private int mAdservicesApiStatusCode;
    private int mSignalsWrittenCount;
    private int mKeysStoredCount;
    private int mValuesStoredCount;
    private int mEvictionRulesCount;
    private int mPerBuyerSignalSize;
    private float mMaxRawProtectedSignalsSizeBytes;
    private float mMinRawProtectedSignalsSizeBytes;
    private Set<Integer> mSignalEvictorsUsed;
    private Set<EvictionPriority> mUpdatedSignalEvictionPriorities;
    private Set<EvictionPriority> mEvictedSignalEvictionPriorities;
    private int mPerBuyerEvictedSignalSize;
    private Set<String> mUpdatedSignalsWithEvictionPriority;
    private int mSignalUpdateSchemaVersion;

    /** Constructs a {@link UpdateSignalsProcessReportedLoggerImpl} instance. */
    public UpdateSignalsProcessReportedLoggerImpl(AdServicesLogger adServicesLogger, Clock clock) {
        mAdServicesLogger = adServicesLogger;
        mClock = clock;
        mAdservicesApiStatusCode = FIELD_UNSET;
        mSignalsWrittenCount = SIZE_UNSET;
        mPerBuyerSignalSize = SIZE_UNSET;
        mSignalEvictorsUsed = new HashSet<>();
        mUpdatedSignalEvictionPriorities = new HashSet<>();
        mEvictedSignalEvictionPriorities = new HashSet<>();
        mPerBuyerEvictedSignalSize = SIZE_UNSET;
        mUpdatedSignalsWithEvictionPriority = new HashSet<>();
        mSignalUpdateSchemaVersion = FIELD_UNSET;
    }

    @Override
    public void logUpdateSignalsProcessReportedStats() {
        int updateSignalsProcessLatencyMillis =
                mUpdateSignalsStartTimestamp == 0
                        ? FIELD_UNSET
                        : (int) (mClock.elapsedRealtime() - mUpdateSignalsStartTimestamp);
        float meanRawProtectedSignalsSizeBytes =
                mSignalsWrittenCount == SIZE_UNSET
                        ? SIZE_UNSET
                        : (float) mPerBuyerSignalSize / mSignalsWrittenCount;
        int bucketedPerBuyerSignalSize =
                mPerBuyerSignalSize == SIZE_UNSET
                        ? SIZE_UNSET
                        : computeSize(mPerBuyerSignalSize, PER_BUYER_SIGNAL_SIZE_BUCKETS);
        int bucketedPerBuyerEvictedSignalSize =
                mPerBuyerEvictedSignalSize == SIZE_UNSET
                        ? SIZE_UNSET
                        : computeSize(mPerBuyerEvictedSignalSize, PER_BUYER_SIGNAL_SIZE_BUCKETS);
        mAdServicesLogger.logUpdateSignalsProcessReportedStats(
                UpdateSignalsProcessReportedStats.builder()
                        .setUpdateSignalsProcessLatencyMillis(updateSignalsProcessLatencyMillis)
                        .setAdservicesApiStatusCode(mAdservicesApiStatusCode)
                        .setSignalsWrittenCount(mSignalsWrittenCount)
                        .setKeysStoredCount(mKeysStoredCount)
                        .setValuesStoredCount(mValuesStoredCount)
                        .setEvictionRulesCount(mEvictionRulesCount)
                        .setPerBuyerSignalSize(bucketedPerBuyerSignalSize)
                        .setMeanRawProtectedSignalsSizeBytes(meanRawProtectedSignalsSizeBytes)
                        .setMaxRawProtectedSignalsSizeBytes(mMaxRawProtectedSignalsSizeBytes)
                        .setMinRawProtectedSignalsSizeBytes(mMinRawProtectedSignalsSizeBytes)
                        .setSignalEvictorsUsed(mSignalEvictorsUsed)
                        .setUpdatedSignalEvictionPriorities(mUpdatedSignalEvictionPriorities)
                        .setEvictedSignalEvictionPriorities(mEvictedSignalEvictionPriorities)
                        .setPerBuyerEvictedSignalSize(bucketedPerBuyerEvictedSignalSize)
                        .setUpdatedSignalsWithEvictionPriorityCount(
                                mUpdatedSignalsWithEvictionPriority.size())
                        .setSignalUpdateSchemaVersion(mSignalUpdateSchemaVersion)
                        .build());
    }

    @Override
    public void setUpdateSignalsStartTimestamp(long updateSignalsStartTimestamp) {
        mUpdateSignalsStartTimestamp = updateSignalsStartTimestamp;
    }

    @Override
    public void setAdservicesApiStatusCode(int adservicesApiStatusCode) {
        mAdservicesApiStatusCode = adservicesApiStatusCode;
    }

    @Override
    public void setSignalsWrittenAndValuesCount(int signalsWrittenAndValuesCount) {
        mSignalsWrittenCount = signalsWrittenAndValuesCount;

        // Every signal has exactly one value, thus the values count is equal to signals count
        mValuesStoredCount = signalsWrittenAndValuesCount;
    }

    @Override
    public void setKeysStoredCount(int keysStoredCount) {
        mKeysStoredCount = keysStoredCount;
    }

    @Override
    public void setEvictionRulesCount(int evictionRulesCount) {
        mEvictionRulesCount = evictionRulesCount;
    }

    @Override
    public void setPerBuyerSignalSize(int perBuyerSignalSize) {
        mPerBuyerSignalSize = perBuyerSignalSize;
    }

    @Override
    public void setMaxRawProtectedSignalsSizeBytes(float maxRawProtectedSignalsSizeBytes) {
        mMaxRawProtectedSignalsSizeBytes = maxRawProtectedSignalsSizeBytes;
    }

    @Override
    public void setMinRawProtectedSignalsSizeBytes(float minRawProtectedSignalsSizeBytes) {
        mMinRawProtectedSignalsSizeBytes = minRawProtectedSignalsSizeBytes;
    }

    @Override
    public void setSignalEvictorsUsed(Set<@SignalEvictorType Integer> evictorTypes) {
        mSignalEvictorsUsed = new HashSet<>(evictorTypes);
    }

    @Override
    public void addSignalEvictorUsed(@SignalEvictorType int evictorType) {
        mSignalEvictorsUsed.add(evictorType);
    }

    @Override
    public void setUpdatedSignalEvictionPriorities(Set<EvictionPriority> evictionPriorities) {
        mUpdatedSignalEvictionPriorities = new HashSet<>(evictionPriorities);
    }

    @Override
    public void addUpdatedSignalEvictionPriority(EvictionPriority evictionPriority) {
        mUpdatedSignalEvictionPriorities.add(evictionPriority);
    }

    @Override
    public void setEvictedSignalEvictionPriorities(Set<EvictionPriority> evictionPriorities) {
        mEvictedSignalEvictionPriorities = new HashSet<>(evictionPriorities);
    }

    @Override
    public void addEvictedSignalEvictionPriority(EvictionPriority evictionPriority) {
        mEvictedSignalEvictionPriorities.add(evictionPriority);
    }

    @Override
    public void setPerBuyerEvictedSignalSize(int evictedSignalSize) {
        mPerBuyerEvictedSignalSize = evictedSignalSize;
    }

    @Override
    public void setUpdatedSignalsWithEvictionPriorityForCount(
            Set<String> updatedSignalsWithEvictionPriority) {
        mUpdatedSignalsWithEvictionPriority = new HashSet<>(updatedSignalsWithEvictionPriority);
    }

    @Override
    public void addUpdatedSignalWithEvictionPriorityForCount(String key) {
        mUpdatedSignalsWithEvictionPriority.add(key);
    }

    @Override
    public void setSignalUpdateSchemaVersion(@UpdateSchemaVersion int updateSchemaVersion) {
        mSignalUpdateSchemaVersion = updateSchemaVersion;
    }
}
