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
import static com.android.adservices.service.measurement.util.JobLockHolder.Type.EVENT_REPORTING;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_REPORTING_JOB;

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
import com.android.adservices.service.measurement.KeyValueData;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKeyManager;
import com.android.adservices.service.measurement.util.JobLockHolder;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.android.libraries.mobiledatadownload.internal.AndroidTimeSource;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.Optional;
import java.util.concurrent.Future;

/**
 * Service for sending event and aggregate reports. Reporting logic contained in {@link
 * EventReportingJobHandler} and {@link AggregateReportingJobHandler}.
 *
 * <p>Bug(b/342687912): This will eventually replace {@link EventReportingJobService} and {@link
 * AggregateReportingJobService}.
 */
// TODO(b/311183933): Remove passed in Context from static method.
@SuppressWarnings("AvoidStaticContext")
public final class ReportingJobService extends JobService {
    private static final ListeningExecutorService sBlockingExecutor =
            AdServicesExecutors.getBlockingExecutor();
    private Future mExecutorFuture;
    private static final String JOB_NEXT_EXECUTION_TIME = "job_next_execution_time";
    private static final String JOB_LAST_EXECUTION_TIME = "job_last_execution_time";

    @Override
    public boolean onStartJob(JobParameters params) {
        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    "Disabling %s job because it's running in" + " ExtServices on T+",
                    this.getClass().getSimpleName());
            return skipAndCancelBackgroundJob(params, /* skipReason= */ 0, /* doRecord= */ false);
        }

        AdServicesJobServiceLogger.getInstance()
                .recordOnStartJob(MEASUREMENT_REPORTING_JOB.getJobId());

        if (!FlagsFactory.getFlags().getMeasurementReportingJobServiceEnabled()) {
            LoggerFactory.getMeasurementLogger()
                    .e("%s is disabled", this.getClass().getSimpleName());
            return skipAndCancelBackgroundJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                    /* doRecord= */ true);
        }

        LoggerFactory.getMeasurementLogger().d("%s.onStartJob", this.getClass().getSimpleName());
        mExecutorFuture =
                sBlockingExecutor.submit(
                        () -> {
                            try {
                                saveExecutionStartTime();
                                processPendingAggregateReports();
                                processPendingEventReports();

                                AdServicesJobServiceLogger.getInstance()
                                        .recordJobFinished(
                                                MEASUREMENT_REPORTING_JOB.getJobId(),
                                                /* isSuccessful= */ true,
                                                /* shouldRetry= */ false);
                                jobFinished(params, /* wantsReschedule= */ false);
                            } finally {
                                scheduleIfNeeded(
                                        getApplicationContext(), /* forceSchedule= */ false);
                            }
                        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LoggerFactory.getMeasurementLogger().d("%s.onStopJob", this.getClass().getSimpleName());
        boolean shouldRetry = true;
        if (mExecutorFuture != null) {
            shouldRetry = mExecutorFuture.cancel(/* mayInterruptIfRunning */ true);
        }
        AdServicesJobServiceLogger.getInstance()
                .recordOnStopJob(params, MEASUREMENT_REPORTING_JOB.getJobId(), shouldRetry);
        return shouldRetry;
    }

    /**
     * Schedule execution of this job service based on pending reports in the database, either
     * aggregate or event.
     *
     * <p>If there are no pending reports, this service will not be scheduled.
     *
     * <p>This job will be scheduled to the latest report within the batching window. The batching
     * window is the window of time between the next earliest report and the window length specified
     * in the flags.
     *
     * <p>Job scheduling will also be throttled by a minimum execution window specified in the
     * flags.
     *
     * @param context application context
     * @param forceSchedule true if the job is to be scheduled at the next pending report,
     *     disregarding the minimum execution window. If there is no pending report, this job will
     *     not be scheduled.
     */
    public static void scheduleIfNeeded(Context context, boolean forceSchedule) {
        Flags flags = FlagsFactory.getFlags();
        if (!flags.getMeasurementReportingJobServiceEnabled()) {
            LoggerFactory.getMeasurementLogger()
                    .e("ReportingJobService is disabled, skip scheduling");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LoggerFactory.getMeasurementLogger().e("JobScheduler not found");
            return;
        }

        Optional<Long> latestReportTimeInBatchOpt = getLastReportTimeInBatch(context, flags);
        if (latestReportTimeInBatchOpt.isEmpty()) {
            LoggerFactory.getMeasurementLogger()
                    .d("ReportingJobService found no pending reports. Aborting scheduling.");
            return;
        }

        long latestReportTimeInBatch = latestReportTimeInBatchOpt.get();
        long lastExecution = getLastExecution(context);
        Long nextScheduledExecution = getNextScheduledExecution(context);
        long minExecutionWindowEnd =
                lastExecution + flags.getMeasurementReportingJobServiceMinExecutionWindowMillis();

        final JobInfo scheduledJob =
                jobScheduler.getPendingJob(MEASUREMENT_REPORTING_JOB.getJobId());

        long nextExecutionTime =
                getNextExecutionTime(forceSchedule, latestReportTimeInBatch, minExecutionWindowEnd);
        JobInfo jobInfo = buildJobInfo(context, flags, nextExecutionTime);
        if (forceSchedule
                || !isNextReportScheduled(
                        scheduledJob, nextScheduledExecution, latestReportTimeInBatch)) {
            jobScheduler.schedule(jobInfo);
            saveNextExecution(context, latestReportTimeInBatch);
            LoggerFactory.getMeasurementLogger().d("Scheduled ReportingJobService");
        }
    }

    private static long getNextExecutionTime(
            boolean forceSchedule, long latestReportTimeInBatch, long minExecutionWindowEnd) {
        return forceSchedule
                ? latestReportTimeInBatch
                : Math.max(minExecutionWindowEnd, latestReportTimeInBatch);
    }

    private static void saveNextExecution(Context context, Long latestReportTimeInBatch) {
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager();
        datastoreManager.runInTransaction(getSaveNextExecutionConsumer(latestReportTimeInBatch));
    }

    private static DatastoreManager.ThrowingCheckedConsumer getSaveNextExecutionConsumer(
            Long latestReportTimeInBatch) {
        return measurementDao -> {
            KeyValueData nextScheduledExecution =
                    measurementDao.getKeyValueData(
                            JOB_NEXT_EXECUTION_TIME, KeyValueData.DataType.JOB_NEXT_EXECUTION_TIME);
            nextScheduledExecution.setReportingJobNextExecutionTime(latestReportTimeInBatch);
            measurementDao.insertOrUpdateKeyValueData(nextScheduledExecution);
        };
    }

    private static boolean isNextReportScheduled(
            JobInfo scheduledJob, Long nextScheduledExecution, long latestReportTimeInBatch) {
        return scheduledJob != null
                && nextScheduledExecution != null
                && nextScheduledExecution == latestReportTimeInBatch;
    }

    private static Long getNextScheduledExecution(Context context) {
        DatastoreManager dataStoreManager = DatastoreManagerFactory.getDatastoreManager();

        KeyValueData kvData =
                dataStoreManager
                        .runInTransactionWithResult(
                                measurementDao ->
                                        measurementDao.getKeyValueData(
                                                JOB_NEXT_EXECUTION_TIME,
                                                KeyValueData.DataType.JOB_NEXT_EXECUTION_TIME))
                        .orElseThrow();

        return kvData.getReportingJobNextExecutionTime();
    }

    private static Optional<Long> getLastReportTimeInBatch(Context context, Flags flags) {
        DatastoreManager dataStoreManager = DatastoreManagerFactory.getDatastoreManager();

        return dataStoreManager.runInTransactionWithResult(
                measurementDao ->
                        measurementDao.getLatestReportTimeInBatchWindow(
                                flags.getMeasurementReportingJobServiceBatchWindowMillis()));
    }

    private void saveExecutionStartTime() {
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager();
        datastoreManager.runInTransaction(getSaveExecutionTimeConsumer());
    }

    private DatastoreManager.ThrowingCheckedConsumer getSaveExecutionTimeConsumer() {
        return measurementDao -> {
            KeyValueData lastExecution =
                    measurementDao.getKeyValueData(
                            JOB_LAST_EXECUTION_TIME, KeyValueData.DataType.JOB_LAST_EXECUTION_TIME);

            lastExecution.setReportingJobLastExecutionTime(System.currentTimeMillis());
            measurementDao.insertOrUpdateKeyValueData(lastExecution);
        };
    }

    private static long getLastExecution(Context context) {
        DatastoreManager dataStoreManager = DatastoreManagerFactory.getDatastoreManager();

        KeyValueData lastExecution =
                dataStoreManager
                        .runInTransactionWithResult(
                                measurementDao ->
                                        measurementDao.getKeyValueData(
                                                JOB_LAST_EXECUTION_TIME,
                                                KeyValueData.DataType.JOB_LAST_EXECUTION_TIME))
                        .orElseThrow();

        return lastExecution.getReportingJobLastExecutionTime() != null
                ? lastExecution.getReportingJobLastExecutionTime()
                : Long.MIN_VALUE;
    }

    private boolean skipAndCancelBackgroundJob(
            final JobParameters params, int skipReason, boolean doRecord) {
        final JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(MEASUREMENT_REPORTING_JOB.getJobId());
            saveNextExecution(getApplicationContext(), null);
        }

        if (doRecord) {
            AdServicesJobServiceLogger.getInstance()
                    .recordJobSkipped(MEASUREMENT_REPORTING_JOB.getJobId(), skipReason);
        }

        // Tell the JobScheduler that the job has completed and does not need to be rescheduled.
        jobFinished(params, false);

        // Returning false means that this job has completed its work.
        return false;
    }

    private static JobInfo buildJobInfo(Context context, Flags flags, long nextExecutionTime) {
        JobInfo.Builder builder =
                new JobInfo.Builder(
                                MEASUREMENT_REPORTING_JOB.getJobId(),
                                new ComponentName(context, ReportingJobService.class))
                        .setRequiresBatteryNotLow(
                                flags.getMeasurementReportingJobRequiredBatteryNotLow())
                        .setRequiredNetworkType(
                                flags.getMeasurementReportingJobRequiredNetworkType())
                        .setPersisted(flags.getMeasurementReportingJobPersisted());
        // nextExecutionTime could potentially be in the past, i.e. for Aggregate Reports with
        // trigger context ids. Using such a timestamp would result in a negative minimum latency.
        if (nextExecutionTime > System.currentTimeMillis()) {
            builder.setMinimumLatency(nextExecutionTime - System.currentTimeMillis());
        }

        return builder.build();
    }

    @VisibleForTesting
    void processPendingAggregateReports() {
        JobLockHolder.getInstance(AGGREGATE_REPORTING)
                .runWithLock(
                        "ReportingJobService",
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

    @VisibleForTesting
    void processPendingEventReports() {
        JobLockHolder.getInstance(EVENT_REPORTING)
                .runWithLock(
                        "ReportingJobService",
                        () -> {
                            long maxEventReportUploadRetryWindowMs =
                                    FlagsFactory.getFlags()
                                            .getMeasurementMaxEventReportUploadRetryWindowMs();
                            new EventReportingJobHandler(
                                            DatastoreManagerFactory.getDatastoreManager(),
                                            FlagsFactory.getFlags(),
                                            AdServicesLoggerImpl.getInstance(),
                                            ReportingStatus.ReportType.EVENT,
                                            ReportingStatus.UploadMethod.REGULAR,
                                            getApplicationContext(),
                                            new AndroidTimeSource())
                                    .performScheduledPendingReportsInWindow(
                                            System.currentTimeMillis()
                                                    - maxEventReportUploadRetryWindowMs,
                                            System.currentTimeMillis());
                        });
    }

    @VisibleForTesting
    static void schedule(JobScheduler jobScheduler, JobInfo jobInfo) {
        jobScheduler.schedule(jobInfo);
    }

    @VisibleForTesting
    Future getFutureForTesting() {
        return mExecutorFuture;
    }
}
