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

import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdsRelevanceStatusUtils;

/**
 * The implementation of {@link EncodingJobRunStatsLogger}. The class for logging {@link
 * EncodingJobRunStats}.
 */
public final class EncodingJobRunStatsLoggerImpl implements EncodingJobRunStatsLogger {

    private final AdServicesLogger mAdServicesLogger;
    private final EncodingJobRunStats.Builder mBuilder;
    private final boolean mFledgeEnableForcedEncodingAfterSignalsUpdate;

    private int mCountOfSignalEncodingSuccesses;
    private int mCountOfSignalEncodingFailures;
    private int mCountOfSignalEncodingSkips;
    private int mSizeOfFilteredBuyerEncodingList;

    public EncodingJobRunStatsLoggerImpl(
            AdServicesLogger adServicesLogger,
            EncodingJobRunStats.Builder builder,
            boolean fledgeEnableForcedEncodingAfterSignalsUpdate) {
        mAdServicesLogger = adServicesLogger;
        mBuilder = builder;
        mFledgeEnableForcedEncodingAfterSignalsUpdate =
                fledgeEnableForcedEncodingAfterSignalsUpdate;
    }

    /** Logs {@link EncodingJobRunStats}
     * in {@link com.android.adservices.service.signals.PeriodicEncodingJobWorker}. */
    @Override
    public void logEncodingJobRunStats() {
        // The count of signal encoding successes is equal to size of filtered buyer encoding list
        // subtract count of signal encoding skips and count of signal encoding failures.
        mCountOfSignalEncodingSuccesses =
                mSizeOfFilteredBuyerEncodingList
                        - mCountOfSignalEncodingFailures
                        - mCountOfSignalEncodingSkips;
        mBuilder
                .setSignalEncodingSuccesses(mCountOfSignalEncodingSuccesses)
                .setSignalEncodingFailures(mCountOfSignalEncodingFailures)
                .setSignalEncodingSkips(mCountOfSignalEncodingSkips);
        mAdServicesLogger.logEncodingJobRunStats(mBuilder.build());
    }

    @Override
    public void addOneSignalEncodingFailures() {
        mCountOfSignalEncodingFailures += 1;
    }

    @Override
    public void addOneSignalEncodingSkips() {
        mCountOfSignalEncodingSkips += 1;
    }

    @Override
    public void setSizeOfFilteredBuyerEncodingList(int sizeOfFilteredBuyerEncodingList) {
        mSizeOfFilteredBuyerEncodingList = sizeOfFilteredBuyerEncodingList;
    }

    @Override
    public void resetStatsWithEncodingSourceType(
            @AdsRelevanceStatusUtils.PasEncodingSourceType int encodingSourceType) {
        // The logger instance is reused in PeriodicEncodingJobWorker. The counter of each signal
        // encoding status should be reset before counting.
        resetCountOfSignalEncodingStatus();
        if (mFledgeEnableForcedEncodingAfterSignalsUpdate) {
            mBuilder.setEncodingSourceType(encodingSourceType);
        }
    }

    private void resetCountOfSignalEncodingStatus() {
        mCountOfSignalEncodingSuccesses = 0;
        mCountOfSignalEncodingFailures = 0;
        mCountOfSignalEncodingSkips = 0;
        mSizeOfFilteredBuyerEncodingList = 0;
    }
}
