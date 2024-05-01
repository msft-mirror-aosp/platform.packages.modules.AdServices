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

package com.android.adservices.shared.spe.logging;

import static com.android.adservices.shared.spe.JobServiceConstants.MAX_PERCENTAGE;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULER_TYPE_JOB_SCHEDULER;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULER_TYPE_SPE;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_LOGGING_UNKNOWN_MODULE_NAME;
import static com.android.adservices.shared.util.LogUtil.VERBOSE;

import android.annotation.NonNull;

import com.android.adservices.shared.common.flags.ModuleSharedFlags;
import com.android.adservices.shared.spe.JobServiceConstants.JobSchedulingResultCode;
import com.android.adservices.shared.util.LogUtil;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Executor;

/** Class for job scheduling logging methods. */
public final class JobSchedulingLogger {
    private static final Random sRandom = new Random();
    private final StatsdJobServiceLogger mStatsdLogger;
    private final Executor mLoggingExecutor;
    private final ModuleSharedFlags mFlags;

    /** Creates an instance of {@link JobSchedulingLogger}. */
    public JobSchedulingLogger(
            @NonNull StatsdJobServiceLogger statsdLogger,
            @NonNull Executor loggingExecutor,
            @NonNull ModuleSharedFlags flags) {
        mStatsdLogger = Objects.requireNonNull(statsdLogger);
        mLoggingExecutor = Objects.requireNonNull(loggingExecutor);
        mFlags = Objects.requireNonNull(flags);
    }

    /**
     * Records the result of the scheduling event using SPE (Scheduling Policy Engine).
     *
     * @param jobId the job ID of the job to schedule.
     * @param resultCode the result code of current scheduling event.
     */
    public void recordOnScheduling(int jobId, @JobSchedulingResultCode int resultCode) {
        if (!mFlags.getJobSchedulingLoggingEnabled()) {
            return;
        }

        mLoggingExecutor.execute(
                () -> logSchedulingStatsHelper(jobId, resultCode, SCHEDULER_TYPE_SPE));
    }

    /**
     * Records the result of the scheduling event using JobScheduler.
     *
     * @param jobId the job ID of the job to schedule.
     * @param resultCode the result code of current scheduling event.
     */
    // TODO(b/325292968): remove this once all jobs are migrated to using SPE.
    public void recordOnSchedulingLegacy(int jobId, @JobSchedulingResultCode int resultCode) {
        if (!mFlags.getJobSchedulingLoggingEnabled()) {
            return;
        }

        mLoggingExecutor.execute(
                () -> logSchedulingStatsHelper(jobId, resultCode, SCHEDULER_TYPE_JOB_SCHEDULER));
    }

    /**
     * Logs the stats to the logging server.
     *
     * @param jobId the job ID of the job to schedule.
     * @param resultCode the result code of current scheduling event.
     * @param schedulerType the type of the scheduler used for current scheduling event.
     */
    @VisibleForTesting
    void logSchedulingStatsHelper(
            int jobId, @JobSchedulingResultCode int resultCode, int schedulerType) {
        if (!shouldLog()) {
            if (VERBOSE) {
                LogUtil.v("Job scheduling logging isn't selected for sampling logging, skip...");
            }

            return;
        }

        SchedulingReportedStats stats =
                SchedulingReportedStats.builder()
                        .setJobId(jobId)
                        .setResultCode(resultCode)
                        .setSchedulerType(schedulerType)
                        .setModuleName(SCHEDULING_LOGGING_UNKNOWN_MODULE_NAME)
                        .build();

        mStatsdLogger.logSchedulingReportedStats(stats);

        if (VERBOSE) {
            LogUtil.v(
                    "[Background job scheduling logging] jobId: %d, resultCode: %d, schedulerType:"
                            + " %d, moduleName:%d",
                    jobId, resultCode, schedulerType, SCHEDULING_LOGGING_UNKNOWN_MODULE_NAME);
        }
    }

    // Make a random draw to determine if a logging event should be uploaded to the logging server.
    @VisibleForTesting
    boolean shouldLog() {
        int loggingRatio = mFlags.getJobSchedulingLoggingSamplingRate();

        return sRandom.nextInt(MAX_PERCENTAGE) < loggingRatio;
    }
}
