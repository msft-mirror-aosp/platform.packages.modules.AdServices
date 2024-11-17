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

package com.android.adservices.service.measurement.registration;

import static com.android.adservices.service.measurement.util.JobLockHolder.Type.ASYNC_REGISTRATION_PROCESSING;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_FAILED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SKIPPED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB;

import android.annotation.RequiresApi;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

import com.android.adservices.LogUtil;
import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.measurement.util.JobLockHolder;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.spe.JobServiceConstants.JobSchedulingResultCode;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.internal.annotations.VisibleForTesting;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Future;

/** Fallback Job Service for servicing queued registration requests */
// TODO(b/328287543): Since Rb has released to R so functionally this class should support R. Due to
// Legacy issue, class such as BackgroundJobsManager and MddJobService which have to support R also
// have this annotation. It won't have production impact but is needed to bypass the build error.
@RequiresApi(Build.VERSION_CODES.S)
public class AsyncRegistrationFallbackJobService extends JobService {
    private static final int MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID =
            MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB.getJobId();

    private Future mExecutorFuture;

    @Override
    public boolean onStartJob(JobParameters params) {
        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    "Disabling AsyncRegistrationFallbackJobService job because it's running in"
                            + " ExtServices on T+");
            return skipAndCancelBackgroundJob(params, /* skipReason= */ 0, /* doRecord= */ false);
        }

        // Reschedule jobs with SPE if it's enabled. Note scheduled jobs by this
        // AsyncRegistrationFallbackJobService will be cancelled for the same job ID.
        //
        // Note the job without a flex period will execute immediately after rescheduling with the
        // same ID. Therefore, ending the execution here and let it run in the new SPE job.
        if (FlagsFactory.getFlags().getSpeOnAsyncRegistrationFallbackJobEnabled()) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "SPE is enabled. Reschedule AsyncRegistrationFallbackJobService with"
                                    + " AsyncRegistrationFallbackJob.");
            AsyncRegistrationFallbackJob.schedule();
            return false;
        }

        AdServicesJobServiceLogger.getInstance()
                .recordOnStartJob(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID);

        if (FlagsFactory.getFlags().getAsyncRegistrationFallbackJobKillSwitch()) {
            LoggerFactory.getMeasurementLogger()
                    .e("AsyncRegistrationFallbackJobService is disabled");
            return skipAndCancelBackgroundJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                    /* doRecord= */ true);
        }

        Instant jobStartTime = Clock.systemUTC().instant();
        LoggerFactory.getMeasurementLogger()
                .d(
                        "AsyncRegistrationFallbackJobService.onStartJob " + "at %s",
                        jobStartTime.toString());

        mExecutorFuture =
                AdServicesExecutors.getBlockingExecutor()
                        .submit(
                                () -> {
                                    processAsyncRecords();

                                    boolean shouldRetry = false;
                                    AdServicesJobServiceLogger.getInstance()
                                            .recordJobFinished(
                                                    MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID,
                                                    /* isSuccessful */ true,
                                                    shouldRetry);

                                    jobFinished(params, false);
                                });
        return true;
    }

    @VisibleForTesting
    void processAsyncRecords() {
        JobLockHolder.getInstance(ASYNC_REGISTRATION_PROCESSING)
                .runWithLock(
                        "AsyncRegistrationFallbackQueueJobService",
                        () ->
                                AsyncRegistrationQueueRunner.getInstance()
                                        .runAsyncRegistrationQueueWorker());
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LoggerFactory.getMeasurementLogger().d("AsyncRegistrationFallbackJobService.onStopJob");
        boolean shouldRetry = true;
        if (mExecutorFuture != null) {
            shouldRetry = mExecutorFuture.cancel(/* mayInterruptIfRunning */ true);
        }
        AdServicesJobServiceLogger.getInstance()
                .recordOnStopJob(
                        params, MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID, shouldRetry);
        return shouldRetry;
    }

    @VisibleForTesting
    protected static void schedule(JobScheduler jobScheduler, JobInfo jobInfo) {
        jobScheduler.schedule(jobInfo);
    }

    /**
     * Schedule Fallback Async Registration Job Service if it is not already scheduled
     *
     * @param forceSchedule flag to indicate whether to force rescheduling the job.
     */
    @JobSchedulingResultCode
    public static int scheduleIfNeeded(boolean forceSchedule) {
        Context context = ApplicationContextSingleton.get();
        Flags flags = FlagsFactory.getFlags();
        if (flags.getAsyncRegistrationFallbackJobKillSwitch()) {
            LoggerFactory.getMeasurementLogger()
                    .e("AsyncRegistrationFallbackJobService is disabled, skip scheduling");
            return SCHEDULING_RESULT_CODE_SKIPPED;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LoggerFactory.getMeasurementLogger().e("JobScheduler not found");
            return SCHEDULING_RESULT_CODE_FAILED;
        }

        final JobInfo scheduledJob =
                jobScheduler.getPendingJob(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID);
        // Schedule if it hasn't been scheduled already or force rescheduling
        final JobInfo jobInfo = buildJobInfo(context, flags);
        if (forceSchedule || !jobInfo.equals(scheduledJob)) {
            schedule(jobScheduler, jobInfo);
            LoggerFactory.getMeasurementLogger().d("Scheduled AsyncRegistrationFallbackJobService");
            return SCHEDULING_RESULT_CODE_SUCCESSFUL;
        } else {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "AsyncRegistrationFallbackJobService already scheduled, skipping"
                                    + " reschedule");
            return SCHEDULING_RESULT_CODE_SKIPPED;
        }
    }

    // TODO(b/311183933): Remove passed in Context from static method.
    @SuppressWarnings("AvoidStaticContext")
    private static JobInfo buildJobInfo(Context context, Flags flags) {
        return new JobInfo.Builder(
                        MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID,
                        new ComponentName(context, AsyncRegistrationFallbackJobService.class))
                .setRequiresBatteryNotLow(
                        flags.getMeasurementAsyncRegistrationFallbackJobRequiredBatteryNotLow())
                .setPeriodic(flags.getAsyncRegistrationJobQueueIntervalMs())
                .setRequiredNetworkType(
                        flags.getMeasurementAsyncRegistrationFallbackJobRequiredNetworkType())
                .setPersisted(flags.getMeasurementAsyncRegistrationFallbackJobPersisted())
                .build();
    }

    private boolean skipAndCancelBackgroundJob(
            final JobParameters params, int skipReason, boolean doRecord) {
        final JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID);
        }

        if (doRecord) {
            AdServicesJobServiceLogger.getInstance()
                    .recordJobSkipped(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID, skipReason);
        }

        // Tell the JobScheduler that the job is done and does not need to be rescheduled
        jobFinished(params, false);

        // Returning false to reschedule this job.
        return false;
    }

    @VisibleForTesting
    Future getFutureForTesting() {
        return mExecutorFuture;
    }
}
