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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.spe.AdservicesJobInfo.MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB;

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
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.spe.AdservicesJobServiceLogger;
import com.android.internal.annotations.VisibleForTesting;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Executor;

/**
 * Fallback service for scheduling debug reporting jobs. This runs periodically to handle any
 * reports that the {@link DebugReportingJobService } failed/missed. The actual job execution logic
 * is part of {@link DebugReportingJobHandler }.
 */
public class VerboseDebugReportingFallbackJobService extends JobService {

    private static final int MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID =
            MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB.getJobId();

    private static final Executor sBlockingExecutor = AdServicesExecutors.getBlockingExecutor();

    @Override
    public boolean onStartJob(JobParameters params) {
        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    "Disabling VerboseDebugReportingFallbackJobService job because it's running in"
                            + " ExtServices on T+");
            return skipAndCancelBackgroundJob(params, /* skipReason=*/ 0, /* doRecord=*/ false);
        }

        AdservicesJobServiceLogger.getInstance(this)
                .recordOnStartJob(MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID);

        if (FlagsFactory.getFlags().getMeasurementVerboseDebugReportingFallbackJobKillSwitch()) {
            LogUtil.e("VerboseDebugReportingFallbackJobService is disabled.");
            return skipAndCancelBackgroundJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                    /* doRecord */ true);
        }

        Instant jobStartTime = Clock.systemUTC().instant();
        LogUtil.d(
                "VerboseDebugReportingFallbackJobService.onStartJob " + "at %s",
                jobStartTime.toString());
        sBlockingExecutor.execute(
                () -> {
                    sendReports();
                    boolean shouldRetry = false;
                    AdservicesJobServiceLogger.getInstance(
                                    VerboseDebugReportingFallbackJobService.this)
                            .recordJobFinished(
                                    MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID,
                                    /* isSuccessful */ true,
                                    shouldRetry);

                    jobFinished(params, false);
                });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogUtil.d("VerboseDebugReportingJobService.onStopJob");
        boolean shouldRetry = true;
        AdservicesJobServiceLogger.getInstance(this)
                .recordOnStopJob(
                        params, MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID, shouldRetry);
        return shouldRetry;
    }

    @VisibleForTesting
    protected static void schedule(Context context, JobScheduler jobScheduler) {
        final JobInfo job =
                new JobInfo.Builder(
                                MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID,
                                new ComponentName(
                                        context, VerboseDebugReportingFallbackJobService.class))
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPeriodic(
                                FlagsFactory.getFlags()
                                        .getMeasurementVerboseDebugReportingFallbackJobPeriodMs())
                        .setPersisted(true)
                        .build();
        jobScheduler.schedule(job);
    }

    /**
     * Schedule Verbose Debug Reporting Fallback Job Service if it is not already scheduled.
     *
     * @param context the context
     * @param forceSchedule flag to indicate whether to force rescheduling the job.
     */
    public static void scheduleIfNeeded(Context context, boolean forceSchedule) {
        if (FlagsFactory.getFlags().getMeasurementVerboseDebugReportingFallbackJobKillSwitch()) {
            LogUtil.e("VerboseDebugReportingFallbackJobService is disabled, skip scheduling");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LogUtil.e("JobScheduler not found");
            return;
        }

        final JobInfo job =
                jobScheduler.getPendingJob(MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID);
        // Schedule if it hasn't been scheduled already or force rescheduling
        if (job == null || forceSchedule) {
            schedule(context, jobScheduler);
            LogUtil.d("Scheduled VerboseDebugReportingFallbackJobService");
        } else {
            LogUtil.d(
                    "VerboseDebugReportingFallbackJobService already scheduled, skipping"
                            + " reschedule");
        }
    }

    private boolean skipAndCancelBackgroundJob(
            final JobParameters params, int skipReason, boolean doRecord) {
        final JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID);
        }

        if (doRecord) {
            AdservicesJobServiceLogger.getInstance(this)
                    .recordJobSkipped(
                            MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_ID, skipReason);
        }

        // Tell the JobScheduler that the job is done and does not need to be rescheduled
        jobFinished(params, false);

        // Returning false to reschedule this job.
        return false;
    }

    private void sendReports() {
        EnrollmentDao enrollmentDao = EnrollmentDao.getInstance(getApplicationContext());
        DatastoreManager datastoreManager =
                DatastoreManagerFactory.getDatastoreManager(getApplicationContext());
        new DebugReportingJobHandler(enrollmentDao, datastoreManager, FlagsFactory.getFlags())
                .performScheduledPendingReports();
    }
}
