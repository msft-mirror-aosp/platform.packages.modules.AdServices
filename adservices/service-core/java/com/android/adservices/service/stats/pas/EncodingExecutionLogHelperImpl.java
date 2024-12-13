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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.ENCODING_JS_EXECUTION_SIGNAL_SIZE_BUCKETS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_EXECUTION_LATENCY_BUCKETS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.computeSize;

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.shared.util.Clock;

import java.util.Objects;

/** Standard implementation of EncodingExecutionLogHelper when logging is enabled. */
public class EncodingExecutionLogHelperImpl implements EncodingExecutionLogHelper {

    @NonNull private final AdServicesLogger mAdServicesLogger;

    @NonNull private final Clock mClock;

    @NonNull private final EnrollmentDao mEnrollmentDao;

    @NonNull private final EncodingJsExecutionStats.Builder mStatsBuilder;

    @NonNull private long mStartTime;

    @NonNull private boolean mFinished;

    public EncodingExecutionLogHelperImpl(
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull Clock clock,
            @NonNull EnrollmentDao enrollmentDao) {
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(clock);
        Objects.requireNonNull(enrollmentDao);

        mAdServicesLogger = adServicesLogger;
        mClock = clock;
        mEnrollmentDao = enrollmentDao;
        mStatsBuilder = EncodingJsExecutionStats.builder();
        mFinished = false;
    }

    @Override
    public void startClock() {
        mStartTime = mClock.currentTimeMillis();
    }

    @Override
    public void setStatus(int status) {
        mStatsBuilder.setRunStatus(status);
    }

    @Override
    public void setAdtech(AdTechIdentifier adtech) {
        EnrollmentData data = mEnrollmentDao.getEnrollmentDataForPASByAdTechIdentifier(adtech);
        if (data != null) {
            mStatsBuilder.setAdTechId(data.getEnrollmentId());
        }
    }

    @Override
    public void setEncodedSignalSize(int encodedSignalSize) {
        mStatsBuilder.setEncodedSignalsSize(
                computeSize(encodedSignalSize, ENCODING_JS_EXECUTION_SIGNAL_SIZE_BUCKETS));
    }

    @Override
    public void finish() {
        if (!mFinished) {
            long time = mClock.currentTimeMillis() - mStartTime;
            mStatsBuilder.setJsLatency(computeSize(time, JS_EXECUTION_LATENCY_BUCKETS));
            mAdServicesLogger.logEncodingJsExecutionStats(mStatsBuilder.build());
            mFinished = true;
        }
    }
}
