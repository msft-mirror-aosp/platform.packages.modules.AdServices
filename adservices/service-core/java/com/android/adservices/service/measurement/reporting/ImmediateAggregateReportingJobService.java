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

package com.android.adservices.service.measurement.reporting;

import static com.android.adservices.service.measurement.util.JobLockHolder.Type.AGGREGATE_REPORTING;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB;

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

import java.util.concurrent.Future;

/**
 * Service for immediately scheduling aggregate reporting jobs. Aggregate reports that have trigger
 * context ids are meant for immediate delivery. The actual job execution logic is part of {@link
 * AggregateReportingJobHandler}
 */
public final class ImmediateAggregateReportingJobService extends JobService {
    private static final int MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_ID =
            MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB.getJobId();
    private static final ListeningExecutorService sBlockingExecutor =
            AdServicesExecutors.getBlockingExecutor();

    private Future mExecutorFuture;

    @Override
    public boolean onStartJob(JobParameters params) {
        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    String.format(
                            "Disabling %s job because it's running in" + " ExtServices on T+",
                            this.getClass().getSimpleName()));
            return skipAndCancelBackgroundJob(params, /* skipReason=*/ 0, /* doRecord=*/ false);
        }

        AdServicesJobServiceLogger.getInstance()
                .recordOnStartJob(MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_ID);

        if (FlagsFactory.getFlags().getMeasurementJobImmediateAggregateReportingKillSwitch()) {
            LoggerFactory.getMeasurementLogger()
                    .e(this.getClass().getSimpleName() + " is disabled");
            return skipAndCancelBackgroundJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                    /* doRecord=*/ true);
        }

        LoggerFactory.getMeasurementLogger().d(this.getClass().getSimpleName() + ".onStartJob");
        mExecutorFuture =
                sBlockingExecutor.submit(
                        () -> {
                            processPendingReports();

                            AdServicesJobServiceLogger.getInstance()
                                    .recordJobFinished(
                                            MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_ID,
                                            /* isSuccessful= */ true,
                                            /* shouldRetry= */ false);

                            jobFinished(params, /* wantsReschedule= */ false);
                        });
        return true;
    }

    @VisibleForTesting
    void processPendingReports() {
        JobLockHolder.getInstance(AGGREGATE_REPORTING)
                .runWithLock(
                        this.getClass().getSimpleName(),
                        () -> {
                            long maxAggregateReportUploadRetryWindowMs =
                                    FlagsFactory.getFlags()
                                            .getMeasurementMaxAggregateReportUploadRetryWindowMs();
                            DatastoreManager datastoreManager =
                                    DatastoreManagerFactory.getDatastoreManager();
                            new AggregateReportingJobHandler(
                                            datastoreManager,
                                            new AggregateEncryptionKeyManager(
                                                    datastoreManager, getApplicationContext()),
                                            FlagsFactory.getFlags(),
                                            AdServicesLoggerImpl.getInstance(),
                                            ReportingStatus.ReportType.AGGREGATE,
                                            ReportingStatus.UploadMethod.REGULAR,
                                            getApplicationContext(),
                                            new AndroidTimeSource())
                                    .performScheduledPendingReportsInWindow(
                                            System.currentTimeMillis()
                                                    - maxAggregateReportUploadRetryWindowMs,
                                            System.currentTimeMillis());
                        });
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LoggerFactory.getMeasurementLogger().d(this.getClass().getSimpleName() + ".onStopJob");
        boolean shouldRetry = true;
        if (mExecutorFuture != null) {
            shouldRetry = mExecutorFuture.cancel(/* mayInterruptIfRunning */ true);
        }
        AdServicesJobServiceLogger.getInstance()
                .recordOnStopJob(
                        params, MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_ID, shouldRetry);
        return shouldRetry;
    }

    /** Schedules {@link ImmediateAggregateReportingJobService} */
    @VisibleForTesting
    static void schedule(JobScheduler jobScheduler, JobInfo job) {
        jobScheduler.schedule(job);
    }

    /**
     * Schedule Immediate Aggregate Reporting Job if it is not already scheduled
     *
     * @param context the context
     * @param forceSchedule flag to indicate whether to force rescheduling the job.
     */
    // TODO(b/311183933): Remove passed in Context from static method.
    @SuppressWarnings("AvoidStaticContext")
    public static void scheduleIfNeeded(Context context, boolean forceSchedule) {
        Flags flags = FlagsFactory.getFlags();
        if (flags.getMeasurementJobImmediateAggregateReportingKillSwitch()) {
            LoggerFactory.getMeasurementLogger()
                    .d("ImmediateAggregateReportingJobService is disabled, skip scheduling");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LoggerFactory.getMeasurementLogger().e("JobScheduler not found");
            return;
        }

        final JobInfo scheduledJobInfo =
                jobScheduler.getPendingJob(MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_ID);
        JobInfo jobInfo = buildJobInfo(context, flags);
        // Schedule if it hasn't been scheduled already or force rescheduling
        if (forceSchedule || !jobInfo.equals(scheduledJobInfo)) {
            schedule(jobScheduler, jobInfo);
            LoggerFactory.getMeasurementLogger()
                    .d("Scheduled ImmediateAggregateReportingJobService");
        } else {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "ImmediateAggregateReportingJobService already scheduled, skipping"
                                    + " reschedule");
        }
    }

    // TODO(b/311183933): Remove passed in Context from static method.
    @SuppressWarnings("AvoidStaticContext")
    private static JobInfo buildJobInfo(Context context, Flags flags) {
        return new JobInfo.Builder(
                        MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_ID,
                        new ComponentName(context, ImmediateAggregateReportingJobService.class))
                .setRequiresBatteryNotLow(
                        flags.getMeasurementImmediateAggregateReportingJobRequiredBatteryNotLow())
                .setRequiredNetworkType(
                        flags.getMeasurementImmediateAggregateReportingJobRequiredNetworkType())
                .setPersisted(flags.getMeasurementImmediateAggregateReportingJobPersisted())
                .build();
    }

    private boolean skipAndCancelBackgroundJob(
            final JobParameters params, int skipReason, boolean doRecord) {
        final JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_ID);
        }

        if (doRecord) {
            AdServicesJobServiceLogger.getInstance()
                    .recordJobSkipped(MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB_ID, skipReason);
        }

        // Tell the JobScheduler that the job has completed and does not need to be rescheduled.
        jobFinished(params, false);

        // Returning false means that this job has completed its work.
        return false;
    }

    @VisibleForTesting
    Future getFutureForTesting() {
        return mExecutorFuture;
    }
}
