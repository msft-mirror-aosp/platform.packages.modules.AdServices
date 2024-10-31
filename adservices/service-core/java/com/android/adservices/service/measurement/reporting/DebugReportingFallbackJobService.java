/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.measurement.reporting;

import static com.android.adservices.service.measurement.util.JobLockHolder.Type.DEBUG_REPORTING;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKeyManager;
import com.android.adservices.service.measurement.util.JobLockHolder;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.android.libraries.mobiledatadownload.internal.AndroidTimeSource;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Future;

/**
 * Fallback service for scheduling debug reporting jobs. This runs periodically to handle any
 * reports that the {@link DebugReportingJobService } failed/missed. The actual job execution logic
 * is part of {@link EventReportingJobHandler } and {@link AggregateReportingJobHandler}.
 */
public class DebugReportingFallbackJobService extends JobService {

    private static final int MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID =
            MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB.getJobId();

    private static final ListeningExecutorService sBlockingExecutor =
            AdServicesExecutors.getBlockingExecutor();

    private Future mExecutorFuture;

    @Override
    public boolean onStartJob(JobParameters params) {
        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    "Disabling DebugReportingFallbackJobService job because it's running in"
                            + " ExtServices on T+");
            return skipAndCancelBackgroundJob(params, /* skipReason=*/ 0, /* doRecord=*/ false);
        }

        AdServicesJobServiceLogger.getInstance()
                .recordOnStartJob(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID);

        if (FlagsFactory.getFlags().getMeasurementDebugReportingFallbackJobKillSwitch()) {
            LoggerFactory.getMeasurementLogger().e("DebugReportingFallbackJobService is disabled.");
            return skipAndCancelBackgroundJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                    /* doRecord */ true);
        }

        Instant jobStartTime = Clock.systemUTC().instant();
        LoggerFactory.getMeasurementLogger()
                .d(
                        "DebugReportingFallbackJobService.onStartJob " + "at %s",
                        jobStartTime.toString());
        mExecutorFuture =
                sBlockingExecutor.submit(
                        () -> {
                            sendReports();
                            boolean shouldRetry = false;
                            AdServicesJobServiceLogger.getInstance()
                                    .recordJobFinished(
                                            MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID,
                                            /* isSuccessful */ true,
                                            shouldRetry);
                            jobFinished(params, false);
                        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LoggerFactory.getMeasurementLogger().d("DebugReportingJobService.onStopJob");
        boolean shouldRetry = true;
        if (mExecutorFuture != null) {
            shouldRetry = mExecutorFuture.cancel(/* mayInterruptIfRunning */ true);
        }
        AdServicesJobServiceLogger.getInstance()
                .recordOnStopJob(params, MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID, shouldRetry);
        return shouldRetry;
    }

    @VisibleForTesting
    protected static void schedule(JobScheduler jobScheduler, JobInfo job) {
        jobScheduler.schedule(job);
    }

    /**
     * Schedule Debug Reporting Fallback Job Service if it is not already scheduled.
     *
     * @param context the context
     * @param forceSchedule flag to indicate whether to force rescheduling the job.
     */
    public static void scheduleIfNeeded(Context context, boolean forceSchedule) {
        Flags flags = FlagsFactory.getFlags();
        if (flags.getMeasurementDebugReportingFallbackJobKillSwitch()) {
            LoggerFactory.getMeasurementLogger()
                    .e("DebugReportingFallbackJobService is disabled, skip scheduling");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LoggerFactory.getMeasurementLogger().e("JobScheduler not found");
            return;
        }

        final JobInfo scheduledJob =
                jobScheduler.getPendingJob(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID);
        // Schedule if it hasn't been scheduled already or force rescheduling
        JobInfo jobInfo = buildJobInfo(context, flags);
        if (forceSchedule || !jobInfo.equals(scheduledJob)) {
            schedule(jobScheduler, jobInfo);
            LoggerFactory.getMeasurementLogger().d("Scheduled DebugReportingFallbackJobService");
        } else {
            LoggerFactory.getMeasurementLogger()
                    .d("DebugReportingFallbackJobService already scheduled, skipping reschedule");
        }
    }

    private boolean skipAndCancelBackgroundJob(
            final JobParameters params, int skipReason, boolean doRecord) {
        final JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID);
        }

        if (doRecord) {
            AdServicesJobServiceLogger.getInstance()
                    .recordJobSkipped(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID, skipReason);
        }

        // Tell the JobScheduler that the job is done and does not need to be rescheduled
        jobFinished(params, false);

        // Returning false to reschedule this job.
        return false;
    }

    private static JobInfo buildJobInfo(Context context, Flags flags) {
        return new JobInfo.Builder(
                        MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID,
                        new ComponentName(context, DebugReportingFallbackJobService.class))
                .setRequiredNetworkType(
                        flags.getMeasurementDebugReportingFallbackJobRequiredNetworkType())
                .setPeriodic(flags.getMeasurementDebugReportingFallbackJobPeriodMs())
                .setPersisted(flags.getMeasurementDebugReportingFallbackJobPersisted())
                .build();
    }

    @VisibleForTesting
    void sendReports() {
        JobLockHolder.getInstance(DEBUG_REPORTING)
                .runWithLock(
                        "DebugReportingFallbackJobService",
                        () -> {
                            DatastoreManager datastoreManager =
                                    DatastoreManagerFactory.getDatastoreManager();
                            AndroidTimeSource timeSource = new AndroidTimeSource();
                            new EventReportingJobHandler(
                                            datastoreManager,
                                            FlagsFactory.getFlags(),
                                            AdServicesLoggerImpl.getInstance(),
                                            ReportingStatus.ReportType.DEBUG_EVENT,
                                            ReportingStatus.UploadMethod.FALLBACK,
                                            getApplicationContext(),
                                            timeSource)
                                    .setIsDebugInstance(true)
                                    .performScheduledPendingReportsInWindow(0, 0);
                            new AggregateReportingJobHandler(
                                            datastoreManager,
                                            new AggregateEncryptionKeyManager(
                                                    datastoreManager, getApplicationContext()),
                                            FlagsFactory.getFlags(),
                                            AdServicesLoggerImpl.getInstance(),
                                            ReportingStatus.ReportType.DEBUG_AGGREGATE,
                                            ReportingStatus.UploadMethod.FALLBACK,
                                            getApplicationContext(),
                                            timeSource)
                                    .setIsDebugInstance(true)
                                    .performScheduledPendingReportsInWindow(0, 0);
                        });
    }

    @VisibleForTesting
    Future getFutureForTesting() {
        return mExecutorFuture;
    }
}
