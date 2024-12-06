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

package com.android.adservices.service.topics;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_API_DISABLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_FETCH_JOB_SCHEDULER_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_HANDLE_JOB_SERVICE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_SKIP_RESCHEDULE_EMPTY_JOB_SCHEDULER;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_SKIP_RESCHEDULE_EMPTY_PENDING_JOB;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_FAILED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SKIPPED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.adservices.spe.AdServicesJobInfo.TOPICS_EPOCH_JOB;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import android.annotation.NonNull;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.stats.TopicsScheduleEpochJobSettingReportedStatsLogger;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.spe.JobServiceConstants.JobSchedulingResultCode;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;

/** Epoch computation job. This will be run approximately once per epoch to compute Topics. */
@RequiresApi(Build.VERSION_CODES.S)
public final class EpochJobService extends JobService {
    private static final int TOPICS_EPOCH_JOB_ID = TOPICS_EPOCH_JOB.getJobId();

    @Override
    public boolean onStartJob(JobParameters params) {
        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d("Disabling EpochJobService job because it's running in ExtServices on T+");
            return skipAndCancelBackgroundJob(params, /* skipReason= */ 0, /* doRecord= */ false);
        }

        LoggerFactory.getTopicsLogger().d("EpochJobService.onStartJob");

        AdServicesJobServiceLogger.getInstance().recordOnStartJob(TOPICS_EPOCH_JOB_ID);

        if (FlagsFactory.getFlags().getTopicsKillSwitch()) {
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_API_DISABLED,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            LoggerFactory.getTopicsLogger()
                    .e("Topics API is disabled, skipping and cancelling EpochJobService");
            return skipAndCancelBackgroundJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                    /* doRecord= */ true);
        }

        // This service executes each incoming job on a Handler running on the application's
        // main thread. This means that we must offload the execution logic to background executor.
        // TODO(b/225382268): Handle cancellation.
        ListenableFuture<Void> epochComputationFuture =
                Futures.submit(
                        () -> TopicsWorker.getInstance().computeEpoch(),
                        AdServicesExecutors.getBackgroundExecutor());

        Futures.addCallback(
                epochComputationFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        LoggerFactory.getTopicsLogger().d("Epoch Computation succeeded!");

                        boolean shouldRetry = false;
                        AdServicesJobServiceLogger.getInstance()
                                .recordJobFinished(
                                        TOPICS_EPOCH_JOB_ID, /* isSuccessful= */ true, shouldRetry);

                        // Tell the JobScheduler that the job has completed and does not need to be
                        // rescheduled.
                        jobFinished(params, shouldRetry);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        ErrorLogUtil.e(
                                t,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_HANDLE_JOB_SERVICE_FAILURE,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
                        LoggerFactory.getTopicsLogger()
                                .e(t, "Failed to handle JobService: " + params.getJobId());

                        boolean shouldRetry = false;
                        AdServicesJobServiceLogger.getInstance()
                                .recordJobFinished(
                                        TOPICS_EPOCH_JOB_ID,
                                        /* isSuccessful= */ false,
                                        shouldRetry);

                        //  When failure, also tell the JobScheduler that the job has completed and
                        // does not need to be rescheduled.
                        // TODO(b/225909845): Revisit this. We need a retry policy.
                        jobFinished(params, shouldRetry);
                    }
                },
                directExecutor());

        if (FlagsFactory.getFlags().getTopicsJobSchedulerRescheduleEnabled()) {
            // Reschedule Topics Epoch job if the charging setting of previous epoch job is changed.
            rescheduleEpochJob();
        }

        // Reschedule jobs with SPE if it's enabled. Note scheduled jobs by this EpochJobService
        // will be cancelled for the same job ID.
        //
        // Also for a job with Flex Period, it will NOT execute immediately after rescheduling it.
        // Reschedule it here to let the execution complete and the next cycle will execute with
        // the EpochJob.schedule().
        if (FlagsFactory.getFlags().getSpeOnEpochJobEnabled()) {
            LoggerFactory.getTopicsLogger()
                    .d("SPE is enabled. Reschedule EpochJob with SPE framework.");
            EpochJob.schedule();
        }

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LoggerFactory.getTopicsLogger().d("EpochJobService.onStopJob");

        // Tell JobScheduler not to reschedule the job because it's unknown at this stage if the
        // execution is completed or not to avoid executing the task twice.
        boolean shouldRetry = false;

        AdServicesJobServiceLogger.getInstance()
                .recordOnStopJob(params, TOPICS_EPOCH_JOB_ID, shouldRetry);
        return shouldRetry;
    }

    @VisibleForTesting
    static void schedule(
            @NonNull JobScheduler jobScheduler,
            JobInfo jobInfo) {

        LoggerFactory.getTopicsLogger().d(
                "EpochJobService requires charging: "
                        + jobInfo.isRequireCharging()
                        + "\nEpochJobService requires battery not low: "
                        + jobInfo.isRequireBatteryNotLow()
                        + "\nEpochJobService epoch length (ms): "
                        + jobInfo.getIntervalMillis()
                        + "\nEpochJobService flex time (ms): "
                        + jobInfo.getFlexMillis());

        jobScheduler.schedule(jobInfo);
        LoggerFactory.getTopicsLogger().d("Scheduling Epoch job ...");
    }

    @VisibleForTesting
    static JobInfo getJobInfo() {
        Context context = ApplicationContextSingleton.get();
        JobInfo.Builder jobInfoBuilder =
                new JobInfo.Builder(
                        TOPICS_EPOCH_JOB_ID,
                        new ComponentName(context, EpochJobService.class))
                        .setPersisted(true)
                        .setPeriodic(
                                FlagsFactory.getFlags().getTopicsEpochJobPeriodMs(),
                                FlagsFactory.getFlags().getTopicsEpochJobFlexMs());

        boolean flagsTopicsEpochJobBatteryNotLowInsteadOfCharging =
                FlagsFactory.getFlags().getTopicsEpochJobBatteryNotLowInsteadOfCharging();

        if (flagsTopicsEpochJobBatteryNotLowInsteadOfCharging) {
            jobInfoBuilder
                    .setRequiresCharging(false)
                    .setRequiresBatteryNotLow(true);
        } else {
            jobInfoBuilder
                    .setRequiresCharging(true)
                    .setRequiresBatteryNotLow(false);
        }

        return jobInfoBuilder.build();
    }

    /**
     * Schedule Epoch Job Service if needed: there is no scheduled job with same job parameters.
     *
     * @param forceSchedule a flag to indicate whether to force rescheduling the job.
     * @return a {@code boolean} to indicate if the service job is actually scheduled.
     */
    @JobSchedulingResultCode
    public static int scheduleIfNeeded(boolean forceSchedule) {
        return scheduleIfNeededCalledFromRescheduleEpochJob(
                forceSchedule,
                TopicsScheduleEpochJobSettingReportedStatsLogger.getInstance());
    }

    /**
     * Schedule Epoch Job Service if needed: there is no scheduled job with same job parameters.
     *
     * @param forceSchedule a flag to indicate whether to force rescheduling the job.
     * @param topicsScheduleEpochJobSettingReportedStatsLogger a class for Topics schedule epoch job
     *                                                         setting logger.
     * @return a {@code boolean} to indicate if the service job is actually scheduled.
     */
    @JobSchedulingResultCode
    public static int scheduleIfNeededCalledFromRescheduleEpochJob(
            boolean forceSchedule,
            TopicsScheduleEpochJobSettingReportedStatsLogger
                    topicsScheduleEpochJobSettingReportedStatsLogger) {
        Context context = ApplicationContextSingleton.get();

        if (FlagsFactory.getFlags().getTopicsKillSwitch()) {
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_API_DISABLED,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            LoggerFactory.getTopicsLogger()
                    .e("Topics API is disabled, skip scheduling the EpochJobService");
            return SCHEDULING_RESULT_CODE_SKIPPED;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_FETCH_JOB_SCHEDULER_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            LoggerFactory.getTopicsLogger().e("Cannot fetch Job Scheduler!");
            return SCHEDULING_RESULT_CODE_FAILED;
        }

        JobInfo scheduledJobInfo = jobScheduler.getPendingJob(TOPICS_EPOCH_JOB_ID);
        JobInfo newJobInfo = getJobInfo();

        if (scheduledJobInfo == null || forceSchedule) {
            topicsScheduleEpochJobSettingReportedStatsLogger.logScheduleIfNeeded();
            schedule(jobScheduler, newJobInfo);
            LoggerFactory.getTopicsLogger().v(
                    "Topics Epoch Job Service is scheduled successfully "
                            + "because no pending job in jobScheduler or forceSchedule is true.");
            return SCHEDULING_RESULT_CODE_SUCCESSFUL;
        } else {
            if (newJobInfo.equals(scheduledJobInfo)) {
                // Skip to reschedule the job if there is same scheduled job with same parameters.
                LoggerFactory.getTopicsLogger().v(
                        "Epoch Job Service has been scheduled with same parameters, "
                                + "skip rescheduling!");
                return SCHEDULING_RESULT_CODE_SKIPPED;
            } else {
                // Clear all topics data when epoch job's configuration is changed.
                if (FlagsFactory.getFlags()
                        .getTopicsCleanDBWhenEpochJobSettingsChanged()) {
                    LoggerFactory.getTopicsLogger().v(
                            "Cleaning Topics DB because epoch job's configuration is changed.");
                    TopicsWorker.getInstance().clearAllTopicsData(new ArrayList<>());
                }
                LoggerFactory.getTopicsLogger().v(
                        "Rescheduling Topics epoch job because its configuration is changed.");
                LoggerFactory.getTopicsLogger().d(
                        "EpochJobPeriodMs in pending epoch job is: "
                                + scheduledJobInfo.getIntervalMillis()
                                + ", new epoch job is: "
                                + newJobInfo.getIntervalMillis() +
                                "\nEpochJobFlexMs in pending epoch job is: "
                                + scheduledJobInfo.getFlexMillis()
                                + ", new epoch job is: "
                                + newJobInfo.getFlexMillis() +
                                "\nRequires battery not low in pending epoch job is: "
                                + scheduledJobInfo.isRequireBatteryNotLow()
                                + ", new epoch job is: "
                                + newJobInfo.isRequireBatteryNotLow());
                topicsScheduleEpochJobSettingReportedStatsLogger.logScheduleIfNeeded();
                schedule(jobScheduler, newJobInfo);
                return SCHEDULING_RESULT_CODE_SUCCESSFUL;
            }
        }
    }

    private boolean skipAndCancelBackgroundJob(
            final JobParameters params, int skipReason, boolean doRecord) {
        JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(TOPICS_EPOCH_JOB_ID);
        }

        if (doRecord) {
            AdServicesJobServiceLogger.getInstance()
                    .recordJobSkipped(TOPICS_EPOCH_JOB_ID, skipReason);
        }

        // Tell the JobScheduler that the job has completed and does not need to be
        // rescheduled.
        jobFinished(params, false);

        // Returning false means that this job has completed its work.
        return false;
    }

    @VisibleForTesting
    static void rescheduleEpochJob() {
        JobScheduler jobScheduler =
                ApplicationContextSingleton.get().getSystemService(JobScheduler.class);
        JobInfo previousEpochJobInfo = null;
        // The default EpochJob doesn't require battery not low, but requires charging.
        boolean isScheduledEpochJobRequireBatteryNotLow = false;

        TopicsScheduleEpochJobSettingReportedStatsLogger
                topicsScheduleEpochJobSettingReportedStatsLogger =
                TopicsScheduleEpochJobSettingReportedStatsLogger.getInstance();

        if (jobScheduler != null) {
            previousEpochJobInfo = jobScheduler.getPendingJob(TOPICS_EPOCH_JOB_ID);
        } else {
            LoggerFactory.getTopicsLogger().d(
                    "There is no existing JobScheduler, skip rescheduleEpochJob.");
            topicsScheduleEpochJobSettingReportedStatsLogger
                    .logSkipRescheduleEpochJob(
                            TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_SKIP_RESCHEDULE_EMPTY_JOB_SCHEDULER);
            return;
        }

        boolean flagTopicsEpochJobBatteryNotLowInsteadOfCharging =
                FlagsFactory.getFlags().getTopicsEpochJobBatteryNotLowInsteadOfCharging();
        if (previousEpochJobInfo != null) {
            isScheduledEpochJobRequireBatteryNotLow = previousEpochJobInfo.isRequireBatteryNotLow();
            // If the battery not low setting of an EpochJob is changed,
            // the EpochJob should be rescheduled.
            if (isScheduledEpochJobRequireBatteryNotLow
                    != flagTopicsEpochJobBatteryNotLowInsteadOfCharging) {
                topicsScheduleEpochJobSettingReportedStatsLogger
                        .setPreviousEpochJobStatus(isScheduledEpochJobRequireBatteryNotLow);
                scheduleIfNeededCalledFromRescheduleEpochJob(
                        true, topicsScheduleEpochJobSettingReportedStatsLogger);
                LoggerFactory.getTopicsLogger().d(
                        "Rescheduled EpochJobService because requires "
                                + "battery not low is changed to: "
                                + flagTopicsEpochJobBatteryNotLowInsteadOfCharging);
            }
        } else {
            LoggerFactory.getTopicsLogger().d(
                    "There is no existing pending epoch job, skip rescheduleEpochJob.");
            topicsScheduleEpochJobSettingReportedStatsLogger
                    .logSkipRescheduleEpochJob(
                            TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_SKIP_RESCHEDULE_EMPTY_PENDING_JOB);
        }
    }
}
