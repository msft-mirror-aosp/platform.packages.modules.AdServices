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
package com.android.adservices.service.common;

import static com.android.adservices.spe.AdServicesJobInfo.AD_PACKAGE_DENY_PRE_PROCESS_JOB;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.spe.AdServicesJobServiceLogger;

import com.google.android.libraries.mobiledatadownload.tracing.PropagatedFutures;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Class schedules a periodic job to load package deny data from mdd into cache on Android S where
 * adding a new package does not trigger an event for adservices.
 */
@RequiresApi(Build.VERSION_CODES.S)
public final class AdPackageDenyPreProcessJobService extends JobService {

    @Override
    public boolean onStartJob(JobParameters params) {
        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    "Disabling AdPackageDenyPreProcessJobService because it's running in"
                            + " ExtServices on T+");
            // Do not log via the AdservicesJobServiceLogger because the it might cause
            // ClassNotFound exception on earlier beta versions.
            return skipAndCancelBackgroundJob(params);
        }

        // Record the invocation of onStartJob() for logging purpose.
        LogUtil.d("AdPackageDenyPreProcessJobService.onStartJob");
        AdServicesJobServiceLogger.getInstance()
                .recordOnStartJob(AD_PACKAGE_DENY_PRE_PROCESS_JOB.getJobId());
        if (!FlagsFactory.getFlags().getEnablePackageDenyBgJob()) {
            return skipAndCancelBackgroundJob(params);
        }
        ListenableFuture<AdPackageDenyResolver.PackageDenyMddProcessStatus> future =
                PropagatedFutures.submitAsync(
                        () -> {
                            LogUtil.d("AdPackageDenyPreProcessJobService.onStart Job.");
                            return AdPackageDenyResolver.getInstance().loadDenyDataFromMdd();
                        },
                        AdServicesExecutors.getBlockingExecutor());

        // Background job logging in onSuccess and OnFailure have to happen before jobFinished() is
        // called. Due to JobScheduler infra, the JobService instance will end its lifecycle (call
        // onDestroy()) once jobFinished() is invoked.
        Futures.addCallback(
                future,
                new FutureCallback<AdPackageDenyResolver.PackageDenyMddProcessStatus>() {
                    @Override
                    public void onSuccess(
                            AdPackageDenyResolver.PackageDenyMddProcessStatus result) {
                        LogUtil.d("AdPackageDenyPreProcessJobService job succeeded.");

                        // Tell the JobScheduler that the job has completed and does not
                        // need to be rescheduled.
                        boolean shouldRetry = false;
                        AdServicesJobServiceLogger.getInstance()
                                .recordJobFinished(
                                        AD_PACKAGE_DENY_PRE_PROCESS_JOB.getJobId(),
                                        /* isSuccessful= */ true,
                                        shouldRetry);
                        jobFinished(params, shouldRetry);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(t, "Failed to handle AdPackageDenyPreProcessJobService job");

                        // When failure, also tell the JobScheduler that the job has completed and
                        // does not need to be rescheduled.
                        boolean shouldRetry = false;
                        AdServicesJobServiceLogger.getInstance()
                                .recordJobFinished(
                                        AD_PACKAGE_DENY_PRE_PROCESS_JOB.getJobId(),
                                        /* isSuccessful= */ false,
                                        shouldRetry);
                        jobFinished(params, shouldRetry);
                    }
                },
                directExecutor());
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogUtil.d("AdPackageDenyPreProcessJobService.onStopJob");
        // Tell JobScheduler not to reschedule the job because it's unknown at this stage if the
        // execution is completed or not to avoid executing the task twice.
        boolean shouldRetry = false;

        AdServicesJobServiceLogger.getInstance()
                .recordOnStopJob(params, AD_PACKAGE_DENY_PRE_PROCESS_JOB.getJobId(), shouldRetry);
        return shouldRetry;
    }

    /**
     * Schedules AdPackageDenyPreProcessJobService if needed: there is no scheduled job with name
     * job parameters.
     *
     * @return a {@code boolean} to indicate if the service job is actually scheduled.
     */
    public static boolean scheduleIfNeeded() {
        if (!FlagsFactory.getFlags().getEnablePackageDenyBgJob()) {
            LogUtil.d("AdPackageDenyPreProcessJobService is disabled");
            return false;
        }
        Context context = ApplicationContextSingleton.get();
        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LogUtil.e("Cannot fetch job scheduler.");
            return false;
        }
        JobInfo scheduledJobJobInfo =
                jobScheduler.getPendingJob(AD_PACKAGE_DENY_PRE_PROCESS_JOB.getJobId());
        final JobInfo job = buildJobInfo(context);
        if (job.equals(scheduledJobJobInfo)) {
            LogUtil.i(
                    "AdPackageDenyPreProcessJobService has been scheduled with same "
                            + "parameters, skip "
                            + "rescheduling.");
        } else {
            schedule(job, jobScheduler);
        }
        return true;
    }

    @VisibleForTesting
    static void schedule(JobInfo job, JobScheduler jobScheduler) {
        jobScheduler.schedule(job);
        LogUtil.d("Scheduling AdDenyAppPreProcessorJobService job ...");
    }

    // TODO(b/311183933): Remove passed in Context from static method.
    @SuppressWarnings("AvoidStaticContext")
    private static JobInfo buildJobInfo(Context context) {
        // TODO(b/374326249) move periodic flex time config to flags
        long flexIntervalMillis = 60 * 60 * 1000; // 1 hr
        return new JobInfo.Builder(
                        AD_PACKAGE_DENY_PRE_PROCESS_JOB.getJobId(),
                        new ComponentName(context, AdPackageDenyPreProcessJobService.class))
                .setPeriodic(
                        FlagsFactory.getFlags().getPackageDenyBackgroundJobPeriodMillis(),
                        flexIntervalMillis)
                .setPersisted(true)
                .build();
    }

    private boolean skipAndCancelBackgroundJob(JobParameters params) {
        LogUtil.d("Cancelling the AdPackageDenyPreProcessJobService job");
        JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(AD_PACKAGE_DENY_PRE_PROCESS_JOB.getJobId());
        }
        AdServicesJobServiceLogger.getInstance()
                .recordJobSkipped(AD_PACKAGE_DENY_PRE_PROCESS_JOB.getJobId(), 0);
        jobFinished(params, false);
        return false;
    }
}
