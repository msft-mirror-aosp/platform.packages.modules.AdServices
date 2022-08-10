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

package com.android.adservices.service;

import static com.android.adservices.service.AdServicesConfig.MAINTENANCE_JOB_ID;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import android.annotation.NonNull;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.topics.TopicsWorker;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/** Maintenance job to clean up. */
public final class MaintenanceJobService extends JobService {

    @Override
    public boolean onStartJob(JobParameters params) {
        if (FlagsFactory.getFlags().getTopicsKillSwitch()) {
            LogUtil.e("Topics API is disabled");
            // Returning false means that this job has completed its work.
            return false;
        }

        LogUtil.d("MaintenanceJobService.onStartJob");
        ListenableFuture<Void> appReconciliationFuture =
                Futures.submit(
                        () -> TopicsWorker.getInstance(this).reconcileApplicationUpdate(this),
                        AdServicesExecutors.getBackgroundExecutor());

        Futures.addCallback(
                appReconciliationFuture,
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        LogUtil.d("App Update Reconciliation is done!");
                        jobFinished(params, /* wantsReschedule = */ false);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(
                                t, "Failed to handle MaintenanceJobService: " + params.getJobId());
                        jobFinished(params, /* wantsReschedule = */ false);
                    }
                },
                directExecutor());
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogUtil.d("MaintenanceJobService.onStopJob");
        return false;
    }

    private static void schedule(
            Context context,
            @NonNull JobScheduler jobScheduler,
            long maintenanceJobPeriodMs,
            long maintenanceJobFlexMs) {
        final JobInfo job =
                new JobInfo.Builder(
                                MAINTENANCE_JOB_ID,
                                new ComponentName(context, MaintenanceJobService.class))
                        .setRequiresCharging(true)
                        .setPeriodic(maintenanceJobPeriodMs, maintenanceJobFlexMs)
                        .build();

        jobScheduler.schedule(job);
        LogUtil.d("Scheduling maintenance job ...");
    }

    // TODO(b/241866524): Support Killswitch in scheduling
    /**
     * Schedule Maintenance Job Service if needed: there is no scheduled job with same job
     * parameters.
     *
     * @param context the context
     * @param forceSchedule a flag to indicate whether to force rescheduling the job.
     * @return a {@code boolean} to indicate if the service job is actually scheduled.
     */
    public static boolean scheduleIfNeeded(Context context, boolean forceSchedule) {
        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LogUtil.e("Cannot fetch Job Scheduler!");
            return false;
        }

        long flagsMaintenanceJobPeriodMs = FlagsFactory.getFlags().getMaintenanceJobPeriodMs();
        long flagsMaintenanceJobFlexMs = FlagsFactory.getFlags().getMaintenanceJobFlexMs();

        JobInfo job = jobScheduler.getPendingJob(MAINTENANCE_JOB_ID);
        // Skip to reschedule the job if there is same scheduled job with same parameters.
        if (job != null && !forceSchedule) {
            long maintenanceJobPeriodMs = job.getIntervalMillis();
            long maintenanceJobFlexMs = job.getFlexMillis();

            if (flagsMaintenanceJobPeriodMs == maintenanceJobPeriodMs
                    && flagsMaintenanceJobFlexMs == maintenanceJobFlexMs) {
                LogUtil.i(
                        "Maintenance Job Service has been scheduled with same parameters, skip"
                                + " rescheduling!");
                return false;
            }
        }

        schedule(context, jobScheduler, flagsMaintenanceJobPeriodMs, flagsMaintenanceJobFlexMs);
        return true;
    }
}
