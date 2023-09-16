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
import static com.android.adservices.spe.AdservicesJobInfo.MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKeyManager;
import com.android.adservices.service.measurement.util.JobLockHolder;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.spe.AdservicesJobServiceLogger;
import com.android.internal.annotations.VisibleForTesting;

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

        AdservicesJobServiceLogger.getInstance(this)
                .recordOnStartJob(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID);

        if (FlagsFactory.getFlags().getMeasurementDebugReportingFallbackJobKillSwitch()) {
            LogUtil.e("DebugReportingFallbackJobService is disabled.");
            return skipAndCancelBackgroundJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                    /* doRecord */ true);
        }

        Instant jobStartTime = Clock.systemUTC().instant();
        LogUtil.d(
                "DebugReportingFallbackJobService.onStartJob " + "at %s", jobStartTime.toString());
        mExecutorFuture =
                sBlockingExecutor.submit(
                        () -> {
                            sendReports();
                            boolean shouldRetry = false;
                            AdservicesJobServiceLogger.getInstance(
                                            DebugReportingFallbackJobService.this)
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
        LogUtil.d("DebugReportingJobService.onStopJob");
        boolean shouldRetry = true;
        if (mExecutorFuture != null) {
            shouldRetry = mExecutorFuture.cancel(/* mayInterruptIfRunning */ true);
        }
        AdservicesJobServiceLogger.getInstance(this)
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
            LogUtil.e("DebugReportingFallbackJobService is disabled, skip scheduling");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LogUtil.e("JobScheduler not found");
            return;
        }

        final JobInfo scheduledJob =
                jobScheduler.getPendingJob(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID);
        // Schedule if it hasn't been scheduled already or force rescheduling
        JobInfo jobInfo = buildJobInfo(context, flags);
        if (forceSchedule || !jobInfo.equals(scheduledJob)) {
            schedule(jobScheduler, jobInfo);
            LogUtil.d("Scheduled DebugReportingFallbackJobService");
        } else {
            LogUtil.d("DebugReportingFallbackJobService already scheduled, skipping reschedule");
        }
    }

    private boolean skipAndCancelBackgroundJob(
            final JobParameters params, int skipReason, boolean doRecord) {
        final JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID);
        }

        if (doRecord) {
            AdservicesJobServiceLogger.getInstance(this)
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
        final JobLockHolder lock = JobLockHolder.getInstance(DEBUG_REPORTING);
        if (lock.tryLock()) {
            try {
                EnrollmentDao enrollmentDao = EnrollmentDao.getInstance(getApplicationContext());
                DatastoreManager datastoreManager =
                        DatastoreManagerFactory.getDatastoreManager(getApplicationContext());
                new EventReportingJobHandler(
                                enrollmentDao,
                                datastoreManager,
                                FlagsFactory.getFlags(),
                                AdServicesLoggerImpl.getInstance(),
                                ReportingStatus.ReportType.DEBUG_EVENT,
                                ReportingStatus.UploadMethod.FALLBACK)
                        .setIsDebugInstance(true)
                        .performScheduledPendingReportsInWindow(0, 0);
                new AggregateReportingJobHandler(
                                enrollmentDao,
                                datastoreManager,
                                new AggregateEncryptionKeyManager(datastoreManager),
                                FlagsFactory.getFlags(),
                                AdServicesLoggerImpl.getInstance(),
                                ReportingStatus.ReportType.DEBUG_AGGREGATE,
                                ReportingStatus.UploadMethod.FALLBACK)
                        .setIsDebugInstance(true)
                        .performScheduledPendingReportsInWindow(0, 0);
                return;
            } finally {
                lock.unlock();
            }
        }
        LogUtil.d("DebugReportingFallbackJobService did not acquire the lock");
    }

    @VisibleForTesting
    Future getFutureForTesting() {
        return mExecutorFuture;
    }
}
