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

package com.android.adservices.service.measurement;

import static com.android.adservices.service.AdServicesConfig.MEASUREMENT_DELETE_EXPIRED_JOB_ID;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.AdServicesConfig;
import com.android.adservices.service.FlagsFactory;
import com.android.internal.annotations.VisibleForTesting;

import java.util.concurrent.Executor;

/**
 * Service for scheduling delete-expired-records job.
 */
public final class DeleteExpiredJobService extends JobService {

    private static final Executor sBackgroundExecutor = AdServicesExecutors.getBackgroundExecutor();

    @Override
    public boolean onStartJob(JobParameters params) {
        if (FlagsFactory.getFlags().getMeasurementJobDeleteExpiredKillSwitch()) {
            LogUtil.e("DeleteExpiredJobService is disabled");
            return skipAndCancelBackgroundJob(params);
        }

        LogUtil.d("DeleteExpiredJobService.onStartJob");
        sBackgroundExecutor.execute(() -> {
            DatastoreManagerFactory
                    .getDatastoreManager(this)
                    .runInTransaction(IMeasurementDao::deleteExpiredRecords);
            jobFinished(params, false);
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogUtil.d("DeleteExpiredJobService.onStopJob");
        return false;
    }

    /** Schedule the job. */
    @VisibleForTesting
    static void schedule(Context context, JobScheduler jobScheduler) {
        final JobInfo job = new JobInfo.Builder(MEASUREMENT_DELETE_EXPIRED_JOB_ID,
                new ComponentName(context, DeleteExpiredJobService.class))
                .setRequiresDeviceIdle(true)
                .setPeriodic(AdServicesConfig.getMeasurementDeleteExpiredJobPeriodMs())
                .build();
        jobScheduler.schedule(job);
    }

    /**
     * Schedule Delete Expired Job Service if it is not already scheduled
     *
     * @param context the context
     * @param forceSchedule flag to indicate whether to force rescheduling the job.
     */
    public static void scheduleIfNeeded(Context context, boolean forceSchedule) {
        if (FlagsFactory.getFlags().getMeasurementJobDeleteExpiredKillSwitch()) {
            LogUtil.e("DeleteExpiredJobService is disabled, skip scheduling");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LogUtil.e("JobScheduler not found");
            return;
        }

        final JobInfo job = jobScheduler.getPendingJob(MEASUREMENT_DELETE_EXPIRED_JOB_ID);
        // Schedule if it hasn't been scheduled already or force rescheduling
        if (job == null || forceSchedule) {
            schedule(context, jobScheduler);
            LogUtil.d("Scheduled DeleteExpiredJobService");
        } else {
            LogUtil.d("DeleteExpiredJobService already scheduled, skipping reschedule");
        }
    }

    private boolean skipAndCancelBackgroundJob(final JobParameters params) {
        final JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(MEASUREMENT_DELETE_EXPIRED_JOB_ID);
        }
        // Tell the JobScheduler that the job has completed and does not need to be rescheduled.
        jobFinished(params, false);

        // Returning false means that this job has completed its work.
        return false;
    }
}
