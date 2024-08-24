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

import static com.android.adservices.service.stats.AdServicesLoggerUtil.FIELD_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.PER_BUYER_SIGNAL_SIZE_BUCKETS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.computeSize;

import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.shared.util.Clock;

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

    /** Constructs a {@link UpdateSignalsProcessReportedLoggerImpl} instance. */
    public UpdateSignalsProcessReportedLoggerImpl(AdServicesLogger adServicesLogger, Clock clock) {
        mAdServicesLogger = adServicesLogger;
        mClock = clock;
        mAdservicesApiStatusCode = FIELD_UNSET;
        mSignalsWrittenCount = SIZE_UNSET;
        mPerBuyerSignalSize = SIZE_UNSET;
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
}
