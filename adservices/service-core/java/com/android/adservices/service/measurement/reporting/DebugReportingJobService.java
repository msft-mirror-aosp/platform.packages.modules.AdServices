/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.adservices.spe.AdservicesJobInfo.MEASUREMENT_DEBUG_REPORT_JOB;

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
import com.android.internal.annotations.VisibleForTesting;

import java.util.concurrent.Executor;

/**
 * Main service for scheduling debug reporting jobs. The actual job execution logic is part of
 * {@link EventReportingJobHandler } and {@link AggregateReportingJobHandler}
 */
public final class DebugReportingJobService extends JobService {
    private static final Executor sBlockingExecutor = AdServicesExecutors.getBlockingExecutor();
    private static final int DEBUG_REPORT_JOB_ID = MEASUREMENT_DEBUG_REPORT_JOB.getJobId();

    @Override
    public boolean onStartJob(JobParameters params) {
        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    "Disabling DebugReportingJobService job because it's running in ExtServices on"
                            + " T+");
            return skipAndCancelBackgroundJob(params);
        }

        if (FlagsFactory.getFlags().getMeasurementJobDebugReportingKillSwitch()) {
            LogUtil.e("DebugReportingJobService is disabled");
            return skipAndCancelBackgroundJob(params);
        }

        LogUtil.d("DebugReportingJobService.onStartJob");
        sBlockingExecutor.execute(
                () -> {
                    sendReports();
                    jobFinished(params, /* wantsReschedule= */ false);
                });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogUtil.d("DebugReportingJobService.onStopJob");
        return true;
    }

    /** Schedules {@link DebugReportingJobService} */
    @VisibleForTesting
    static void schedule(Context context, JobScheduler jobScheduler) {
        final JobInfo job =
                new JobInfo.Builder(
                                DEBUG_REPORT_JOB_ID,
                                new ComponentName(context, DebugReportingJobService.class))
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build();
        jobScheduler.schedule(job);
    }

    /**
     * Schedule Debug Reporting Job if it is not already scheduled
     *
     * @param context the context
     * @param forceSchedule flag to indicate whether to force rescheduling the job.
     */
    public static void scheduleIfNeeded(Context context, boolean forceSchedule) {
        if (FlagsFactory.getFlags().getMeasurementJobDebugReportingKillSwitch()) {
            LogUtil.d("DebugReportingJobService is disabled, skip scheduling");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LogUtil.e("JobScheduler not found");
            return;
        }

        final JobInfo job = jobScheduler.getPendingJob(DEBUG_REPORT_JOB_ID);
        // Schedule if it hasn't been scheduled already or force rescheduling
        if (job == null || forceSchedule) {
            schedule(context, jobScheduler);
            LogUtil.d("Scheduled DebugReportingJobService");
        } else {
            LogUtil.d("DebugReportingJobService already scheduled, skipping reschedule");
        }
    }

    private boolean skipAndCancelBackgroundJob(final JobParameters params) {
        final JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(DEBUG_REPORT_JOB_ID);
        }

        // Tell the JobScheduler that the job has completed and does not need to be rescheduled.
        jobFinished(params, /* wantsReschedule= */ false);

        // Returning false means that this job has completed its work.
        return false;
    }

    private void sendReports() {
        EnrollmentDao enrollmentDao = EnrollmentDao.getInstance(getApplicationContext());
        DatastoreManager datastoreManager =
                DatastoreManagerFactory.getDatastoreManager(getApplicationContext());
        new EventReportingJobHandler(
                        enrollmentDao, datastoreManager, ReportingStatus.UploadMethod.UNKNOWN)
                .setIsDebugInstance(true)
                .performScheduledPendingReportsInWindow(0, 0);
        new AggregateReportingJobHandler(
                        enrollmentDao, datastoreManager, ReportingStatus.UploadMethod.UNKNOWN)
                .setIsDebugInstance(true)
                .performScheduledPendingReportsInWindow(0, 0);
    }
}
