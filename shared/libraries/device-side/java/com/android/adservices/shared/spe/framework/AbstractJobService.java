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

package com.android.adservices.shared.spe.framework;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_JOB_EXECUTION_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_JOB_ON_STOP_EXECUTION_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;
import static com.android.adservices.shared.spe.JobServiceConstants.ERROR_CODE_JOB_SCHEDULER_IS_UNAVAILABLE;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.adservices.shared.spe.JobServiceConstants.SKIP_REASON_JOB_NOT_CONFIGURED;
import static com.android.adservices.shared.spe.framework.ExecutionResult.CANCELLED_BY_SCHEDULER;
import static com.android.adservices.shared.spe.framework.ExecutionResult.FAILURE_WITHOUT_RETRY;
import static com.android.adservices.shared.spe.framework.ExecutionResult.FAILURE_WITH_RETRY;
import static com.android.adservices.shared.spe.framework.ExecutionRuntimeParameters.convertJobParameters;
import static com.android.internal.annotations.VisibleForTesting.Visibility.PROTECTED;

import android.annotation.Nullable;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;

import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.proto.JobPolicy;
import com.android.adservices.shared.spe.JobUtil;
import com.android.adservices.shared.spe.logging.JobServiceLogger;
import com.android.adservices.shared.spe.scheduling.BackoffPolicy;
import com.android.adservices.shared.spe.scheduling.PolicyJobScheduler;
import com.android.adservices.shared.util.LogUtil;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * The execution part of SPE (Scheduling Policy Engine) framework on top of Platform's {@link
 * JobScheduler} to provide simple and reliable background job implementations. See the scheduling
 * part in {@link PolicyJobScheduler}.
 *
 * <p>To onboard SPE instance for your own module, it needs to,
 *
 * <ul>
 *   <li>Implement this class {@link AbstractJobService} by providing a {@link JobServiceFactory}
 *       that configures specific components for your module. See details in {@link
 *       JobServiceFactory}.
 *   <li>Register your module's instance of {@link AbstractJobService} in the Manifest.xml as a
 *       service.
 *   <li>Create an instance of {@link PolicyJobScheduler} for your module and use this instance to
 *       schedule jobs in your module.
 *   <li>(Optional) Create a Flag in the flag server and point {@link
 *       JobServiceFactory#getModuleJobPolicy()} to this flag to get the encoded String, so that the
 *       {@link PolicyJobScheduler} can sync the {@link JobPolicy} from the flag server.
 * </ul>
 */
public abstract class AbstractJobService extends JobService {
    // Store the running listenable futures used by onStopJob() to cancel the running execution
    // future if there is any unsatisfied constraint. Note the instance of this Service class won't
    // be destroyed if onStartJob() hasn't finished. And in onStopJob(), the live listenable future
    // will be cancelled. Therefore, the lifetime of the futures stored in this map should be no
    // longer than the lifetime of this Service class.
    protected final ConcurrentHashMap<Integer, ListenableFuture<ExecutionResult>>
            mRunningFuturesMap = new ConcurrentHashMap<>();

    private JobServiceFactory mJobServiceFactory;
    private JobServiceLogger mJobServiceLogger;
    private AdServicesErrorLogger mErrorLogger;
    private Executor mExecutor;
    private Map<Integer, String> mJobIdToNameMap;

    protected abstract JobServiceFactory getJobServiceFactory();

    @Override
    public void onCreate() {
        super.onCreate();
        mJobServiceFactory = getJobServiceFactory();
        mJobServiceLogger = mJobServiceFactory.getJobServiceLogger();
        mExecutor = mJobServiceFactory.getBackgroundExecutor();
        mJobIdToNameMap = mJobServiceFactory.getJobIdToNameMap();
        mErrorLogger = mJobServiceFactory.getErrorLogger();
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        int jobId = params.getJobId();
        JobUtil.logV("Starting executing onStartJob() for jobId = %d.", jobId);

        mJobServiceLogger.recordOnStartJob(jobId);

        String jobName = getJobName(params, jobId);
        if (jobName == null) {
            return false;
        }

        LogUtil.v("Running onStartJob() for %s, jobId = %d.", jobName, jobId);

        JobWorker worker = getJobWorker(params, jobId, jobName);
        if (worker == null) {
            return false;
        }

        JobUtil.logV("Begin job execution for %s...", jobName);
        ListenableFuture<ExecutionResult> executionFuture = mRunningFuturesMap.get(jobId);
        // Cancel the unfinished future for the same job. This should rarely happen due to
        // JobScheduler doesn't call onStartJob() again before the end of previous call on the same
        // job (with the same job ID). Nevertheless, do the defensive programming here.
        if (executionFuture != null) {
            executionFuture.cancel(/* mayInterruptIfRunning= */ true);
        }
        executionFuture = worker.getExecutionFuture(this, convertJobParameters(params));
        mRunningFuturesMap.put(jobId, executionFuture);

        // Add Logging callback.
        // TODO(b/331285831): Standardize the usage of unused future.
        ListenableFuture<Void> unusedFuture =
                onJobPostExecution(
                        executionFuture, jobId, jobName, params, worker.getBackoffPolicy());

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        int jobId = params.getJobId();
        String jobName = getJobName(params, jobId);

        JobUtil.logV("Running onStopJob() for %s...", jobName);

        JobWorker worker = getJobWorker(params, jobId, jobName);
        // This should never happen as onStartJob() has done the null check, but do it here to
        // bypass the null-check warning below.
        if (worker == null) {
            return false;
        }

        // Cancel the running execution future.
        ListenableFuture<ExecutionResult> executionFuture = mRunningFuturesMap.get(jobId);
        if (executionFuture != null) {
            executionFuture.cancel(/* mayInterruptIfRunning= */ true);
        }

        // Execute customized logic if the execution is stopped by the JobScheduler.
        // TODO(b/326150705): Add a callback for this future and maybe log the result.
        @SuppressWarnings("unused")
        ListenableFuture<Void> executionStopFuture =
                FluentFuture.from(worker.getExecutionStopFuture(this, convertJobParameters(params)))
                        .catching(
                                RuntimeException.class,
                                e -> {
                                    LogUtil.e(
                                            e,
                                            "The customized logic in onStopJob() encounters error"
                                                    + " for %s!",
                                            jobName);
                                    mErrorLogger.logErrorWithExceptionInfo(
                                            e,
                                            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_JOB_ON_STOP_EXECUTION_FAILURE,
                                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
                                    return null;
                                },
                                mExecutor);

        // Add logging.
        boolean shouldReschedule = worker.getBackoffPolicy().shouldRetryOnExecutionStop();
        mJobServiceLogger.recordOnStopJob(params, jobId, shouldReschedule);

        return shouldReschedule;
    }

    /**
     * Skips the execution and also cancels the job.
     *
     * @param params the {@link JobParameters} when the execution is invoked.
     * @param skipReason the reason to skip the current job.
     */
    @VisibleForTesting(visibility = PROTECTED)
    public void skipAndCancelBackgroundJob(JobParameters params, int skipReason) {
        mExecutor.execute(
                () -> {
                    int jobId = params.getJobId();
                    JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);

                    if (jobScheduler != null) {
                        jobScheduler.cancel(jobId);
                        LogUtil.d("Job %d has been cancelled.", jobId);
                    } else {
                        LogUtil.e(
                                "Cannot fetch JobScheduler! Failed to cancel %s.",
                                mJobIdToNameMap.get(jobId));
                        mErrorLogger.logError(
                                ERROR_CODE_JOB_SCHEDULER_IS_UNAVAILABLE,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
                    }

                    mJobServiceLogger.recordJobSkipped(jobId, skipReason);

                    jobFinished(params, /* wantsReschedule= */ false);
                });
    }

    @Nullable
    protected JobWorker getJobWorker(JobParameters params, int jobId, String jobName) {
        JobWorker worker = mJobServiceFactory.getJobWorkerInstance(jobId);
        if (worker == null) {
            skipForJobInfoNotConfigured(params);
            return null;
        }

        int isEnabled = worker.getJobEnablementStatus();
        if (isEnabled != JOB_ENABLED_STATUS_ENABLED) {
            LogUtil.v("Stop execution and cancel %s due to reason %d", jobName, isEnabled);

            skipAndCancelBackgroundJob(params, isEnabled);
            return null;
        }

        return worker;
    }

    // Logs the execution stats and decides whether to retry the job.
    @VisibleForTesting
    ListenableFuture<Void> onJobPostExecution(
            ListenableFuture<ExecutionResult> executionFuture,
            int jobId,
            String jobName,
            JobParameters params,
            BackoffPolicy backoffPolicy) {
        return FluentFuture.from(executionFuture)
                // Handle execution failures. Fall back to RETRY or FAILURE based on BackoffPolicy.
                .catching(
                        Exception.class,
                        e -> {
                            LogUtil.e(e, "Encounter Job Execution Failure for Job %s.", jobName);

                            if (e instanceof CancellationException) {
                                return CANCELLED_BY_SCHEDULER;
                            }

                            mErrorLogger.logErrorWithExceptionInfo(
                                    e,
                                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_JOB_EXECUTION_FAILURE,
                                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);

                            return backoffPolicy.shouldRetryOnExecutionFailure()
                                    ? FAILURE_WITH_RETRY
                                    : FAILURE_WITHOUT_RETRY;
                        },
                        mExecutor)
                .transform(
                        executionResult -> {
                            JobUtil.logV(
                                    "Job execution is finished for %s, result is %s.",
                                    jobName, executionResult);

                            // onStopJob() handles both logging and whether to retry. Skip them
                            // here.
                            if (!executionResult.equals(CANCELLED_BY_SCHEDULER)) {
                                mJobServiceLogger.recordJobFinished(jobId, executionResult);
                            }

                            jobFinished(params, executionResult.equals(FAILURE_WITH_RETRY));
                            return null;
                        },
                        mExecutor);
    }

    @VisibleForTesting
    protected void skipForJobInfoNotConfigured(JobParameters params) {
        LogUtil.e(
                "Disabling %d job because it's not configured in JobInfo or"
                        + " JobConfig! Please check the setup.",
                params.getJobId());

        skipAndCancelBackgroundJob(params, SKIP_REASON_JOB_NOT_CONFIGURED);
    }

    private String getJobName(JobParameters parameters, int jobId) {
        if (mJobIdToNameMap == null) {
            skipForJobInfoNotConfigured(parameters);
            return null;
        }

        String jobName = mJobIdToNameMap.get(jobId);

        if (jobName == null) {
            skipForJobInfoNotConfigured(parameters);
            return null;
        }

        return jobName;
    }
}
