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

package com.android.adservices.service.measurement.attribution;

import static com.android.adservices.service.measurement.util.JobLockHolder.Type.ATTRIBUTION_PROCESSING;
import static com.android.adservices.service.profiling.RbATraceProvider.FeatureNames.MEASUREMENT_API;
import static com.android.adservices.service.profiling.TracingNames.CLASS_NAME_ATTRIBUTION_FALLBACK_JOB_SERVICE;
import static com.android.adservices.service.profiling.TracingNames.METHOD_NAME_ON_START_JOB;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_BACKGROUND_JOB_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ATTRIBUTION_FALLBACK_JOB;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.reporting.AggregateDebugReportApi;
import com.android.adservices.service.measurement.reporting.DebugReportApi;
import com.android.adservices.service.measurement.reporting.DebugReportingJobService;
import com.android.adservices.service.measurement.reporting.ImmediateAggregateReportingJobService;
import com.android.adservices.service.measurement.reporting.ReportingJobService;
import com.android.adservices.service.measurement.util.JobLockHolder;
import com.android.adservices.service.profiling.RbATraceProvider;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.concurrent.Future;

/**
 * Fallback attribution job. The actual job execution logic is part of {@link
 * AttributionJobHandler}.
 */
// TODO(b/311183933): Remove passed in Context from static method.
@SuppressWarnings("AvoidStaticContext")
public final class AttributionFallbackJobService extends JobService {
    private static final int MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID =
            MEASUREMENT_ATTRIBUTION_FALLBACK_JOB.getJobId();
    private static final ListeningExecutorService sBackgroundExecutor =
            AdServicesExecutors.getBackgroundExecutor();
    private ListenableFuture<Void> mExecutorFuture;

    @Override
    public void onCreate() {
        LogUtil.d("AttributionFallbackJobService.onCreate");
        super.onCreate();
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    "Disabling AttributionFallbackJobService job because it's running in"
                            + " ExtServices on T+");
            return skipAndCancelBackgroundJob(params, /* skipReason=*/ 0, /* doRecord=*/ false);
        }

        int traceCookie =
                RbATraceProvider.beginAsyncSection(
                        MEASUREMENT_API,
                        CLASS_NAME_ATTRIBUTION_FALLBACK_JOB_SERVICE,
                        METHOD_NAME_ON_START_JOB,
                        FlagsFactory.getFlags());

        AdServicesJobServiceLogger.getInstance()
                .recordOnStartJob(MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID);

        if (!FlagsFactory.getFlags().getMeasurementAttributionFallbackJobEnabled()) {
            LoggerFactory.getMeasurementLogger().e("AttributionFallbackJobService is disabled");
            return skipAndCancelBackgroundJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                    /* doRecord=*/ true);
        }

        LoggerFactory.getMeasurementLogger().d("AttributionFallbackJobService.onStartJob");
        mExecutorFuture = Futures.submit(this::processPendingAttributions, sBackgroundExecutor);

        Futures.addCallback(
                mExecutorFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        onSuccessCallback(params, traceCookie);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        onFailureCallback(t, params, traceCookie);
                    }
                },
                sBackgroundExecutor);

        return true;
    }

    private void onSuccessCallback(JobParameters params, int traceCookie) {
        boolean isSuccessful = true;
        try {
            DebugReportingJobService.scheduleIfNeeded(
                    getApplicationContext(), /* forceSchedule */ false);

            // TODO(b/342687685): fold this service into ReportingJobService
            ImmediateAggregateReportingJobService.scheduleIfNeeded(
                    getApplicationContext(), /* forceSchedule */ false);

            ReportingJobService.scheduleIfNeeded(
                    getApplicationContext(), /* forceSchedule */ false);
        } catch (Exception e) {
            LoggerFactory.getMeasurementLogger()
                    .e(e, "AttributionFallbackJobService: exception during onSuccess callback");
            isSuccessful = false;
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_BACKGROUND_JOB_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
        } finally {
            AdServicesJobServiceLogger.getInstance()
                    .recordJobFinished(
                            MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID,
                            isSuccessful,
                            /* shouldRetry */ false);
            RbATraceProvider.endAsyncSection(
                    MEASUREMENT_API,
                    CLASS_NAME_ATTRIBUTION_FALLBACK_JOB_SERVICE,
                    METHOD_NAME_ON_START_JOB,
                    traceCookie,
                    FlagsFactory.getFlags());
            jobFinished(params, /* wantsReschedule= */ false);
        }
    }

    private void onFailureCallback(Throwable t, JobParameters params, int traceCookie) {
        // Futures doesn't distinguish between cancellation vs. failure, so the same callback is
        // used for both cases. onStopJob calls cancel on the future. Metrics are logged in
        // onStopJob, so we return early here to avoid double counting. jobFinished does not need to
        // be called if onStopJob is called.
        if (mExecutorFuture.isCancelled()) {
            return;
        }

        LoggerFactory.getMeasurementLogger()
                .e(t, "AttributionFallbackJobService: exception during onStartJob background work");
        ErrorLogUtil.e(
                t,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_BACKGROUND_JOB_FAILURE,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
        boolean shouldRetry = false;
        AdServicesJobServiceLogger.getInstance()
                .recordJobFinished(
                        MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID,
                        /* isSuccessful= */ false,
                        shouldRetry);
        RbATraceProvider.endAsyncSection(
                MEASUREMENT_API,
                CLASS_NAME_ATTRIBUTION_FALLBACK_JOB_SERVICE,
                METHOD_NAME_ON_START_JOB,
                traceCookie,
                FlagsFactory.getFlags());

        jobFinished(params, shouldRetry);
    }

    @VisibleForTesting
    void processPendingAttributions() {
        JobLockHolder.getInstance(ATTRIBUTION_PROCESSING)
                .runWithLock(
                        "AttributionFallbackJobService",
                        () -> {
                            new AttributionJobHandler(
                                            DatastoreManagerFactory.getDatastoreManager(),
                                            new DebugReportApi(
                                                    getApplicationContext(),
                                                    FlagsFactory.getFlags()),
                                            new AggregateDebugReportApi(FlagsFactory.getFlags()))
                                    .performPendingAttributions();
                        });
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LoggerFactory.getMeasurementLogger().d("AttributionFallbackJobService.onStopJob");
        boolean shouldRetry = true;
        if (mExecutorFuture != null) {
            shouldRetry = mExecutorFuture.cancel(/* mayInterruptIfRunning */ true);
        }
        AdServicesJobServiceLogger.getInstance()
                .recordOnStopJob(params, MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID, shouldRetry);
        return shouldRetry;
    }

    /**
     * Schedules {@link AttributionFallbackJobService} to observer {@link Trigger} content URI
     * change.
     */
    @VisibleForTesting
    static void schedule(JobScheduler jobScheduler, JobInfo job) {
        jobScheduler.schedule(job);
    }

    private static JobInfo buildJobInfo(Context context, Flags flags) {
        return new JobInfo.Builder(
                        MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID,
                        new ComponentName(context, AttributionFallbackJobService.class))
                .setPeriodic(flags.getMeasurementAttributionFallbackJobPeriodMs())
                .setPersisted(flags.getMeasurementAttributionFallbackJobPersisted())
                .build();
    }

    /**
     * Schedule Attribution Fallback Job if it is not already scheduled
     *
     * @param context the context
     * @param forceSchedule flag to indicate whether to force rescheduling the job.
     */
    // TODO(b/311183933): Remove passed in Context from static method.
    @SuppressWarnings("AvoidStaticContext")
    public static void scheduleIfNeeded(Context context, boolean forceSchedule) {
        Flags flags = FlagsFactory.getFlags();
        if (!flags.getMeasurementAttributionFallbackJobEnabled()) {
            LoggerFactory.getMeasurementLogger()
                    .e("AttributionFallbackJobService is disabled, skip scheduling");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LoggerFactory.getMeasurementLogger().e("JobScheduler not found");
            return;
        }

        final JobInfo scheduledJob =
                jobScheduler.getPendingJob(MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID);
        // Schedule if it hasn't been scheduled already or force rescheduling
        JobInfo jobInfo = buildJobInfo(context, flags);
        if (forceSchedule || !jobInfo.equals(scheduledJob)) {
            schedule(jobScheduler, jobInfo);
            LoggerFactory.getMeasurementLogger().d("Scheduled AttributionFallbackJobService");
        } else {
            LoggerFactory.getMeasurementLogger()
                    .d("AttributionFallbackJobService already scheduled, skipping reschedule");
        }
    }

    private boolean skipAndCancelBackgroundJob(
            final JobParameters params, int skipReason, boolean doRecord) {
        final JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID);
        }

        if (doRecord) {
            AdServicesJobServiceLogger.getInstance()
                    .recordJobSkipped(MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID, skipReason);
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
