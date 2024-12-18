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

package com.android.adservices.shared.spe.scheduling;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_INVALID_JOB_POLICY_SYNC;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_JOB_SCHEDULING_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;
import static com.android.adservices.shared.spe.JobErrorMessage.ERROR_MESSAGE_POLICY_JOB_SCHEDULER_INVALID_JOB_INFO;
import static com.android.adservices.shared.spe.JobServiceConstants.ERROR_CODE_JOB_SCHEDULER_IS_UNAVAILABLE;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_FAILED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SKIPPED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.adservices.shared.spe.JobServiceConstants.UNAVAILABLE_KEY;
import static com.android.adservices.shared.spe.JobUtil.jobInfoToString;

import android.annotation.Nullable;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;

import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.proto.JobPolicy;
import com.android.adservices.shared.proto.ModuleJobPolicy;
import com.android.adservices.shared.spe.JobServiceConstants.JobSchedulingResultCode;
import com.android.adservices.shared.spe.JobUtil;
import com.android.adservices.shared.spe.framework.AbstractJobService;
import com.android.adservices.shared.spe.framework.JobServiceFactory;
import com.android.adservices.shared.spe.framework.JobWorker;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.shared.util.LogUtil;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * The scheduling part of SPE (Scheduling Policy Engine) framework on top of Platform's {@link
 * JobScheduler} to provide simple and reliable background job implementations. See the execution
 * part in {@link AbstractJobService}.
 *
 * <p>To onboard SPE instance for your own module, it needs to,
 *
 * <ul>
 *   <li>Create an instance of this class {@link PolicyJobScheduler} for your module, and it will be
 *       invoked by your module's services to schedule jobs.
 *   <li>Implement this class {@link AbstractJobService} by providing a {@link JobServiceFactory}
 *       that configures specific components for your module. See details in {@link
 *       JobServiceFactory}.
 *   <li>Register your module's instance of {@link AbstractJobService} in the Manifest.xml as a
 *       service.
 *   <li>(Optional) Create a Flag in the flag server and point {@link
 *       JobServiceFactory#getModuleJobPolicy()} to this flag to get the encoded String, so that the
 *       {@link PolicyJobScheduler} can sync the {@link JobPolicy} from the flag server.
 * </ul>
 *
 * @param <T> A class that implements {@link AbstractJobService}. This class will be the {@link
 *     android.app.job.JobService} instance for your module and be registered in the Manifest.xml
 */
// TODO(b/331610744): Do null check for constructor members.
public class PolicyJobScheduler<T extends AbstractJobService> {
    private final JobServiceFactory mJobServiceFactory;
    private final Executor mExecutor;
    private final Map<Integer, String> mJobIdToNameMap;
    private final Class<T> mJobServiceClass;
    private final AdServicesErrorLogger mErrorLogger;
    private final JobSchedulingLogger mJobSchedulingLogger;

    public PolicyJobScheduler(JobServiceFactory jobServiceFactory, Class<T> jobServiceClass) {
        mJobServiceFactory = jobServiceFactory;
        mExecutor = mJobServiceFactory.getBackgroundExecutor();
        mJobIdToNameMap = mJobServiceFactory.getJobIdToNameMap();
        mErrorLogger = mJobServiceFactory.getErrorLogger();
        mJobServiceClass = jobServiceClass;
        mJobSchedulingLogger = mJobServiceFactory.getJobSchedulingLogger();
    }

    /**
     * Schedules a job based on {@link JobWorker} and {@link JobServiceFactory} given a job ID.
     *
     * <p>Most use cases of job scheduling happen on the serving/main thread. Move it to a specific
     * executor, such as background executor, to avoid ANRs. Also, keep the method name as
     * "schedule" to be consistent with Platform JobScheduler.
     *
     * @param context the context of the app scheduling a job.
     * @param jobSpec the unique job ID of the app to schedule this job.
     */
    public void schedule(Context context, JobSpec jobSpec) {
        ListenableFuture<Integer> schedulingFuture =
                Futures.submit(() -> scheduleJob(context, jobSpec), mExecutor);

        addCallbackToSchedulingFuture(schedulingFuture, jobSpec.getJobPolicy().getJobId());
    }

    @VisibleForTesting
    @JobSchedulingResultCode
    int scheduleJob(Context context, JobSpec jobSpec) {
        int jobId = jobSpec.getJobPolicy().getJobId();
        String jobName = mJobIdToNameMap.get(jobId);
        boolean forceSchedule = jobSpec.getShouldForceSchedule();
        LogUtil.v("Start to schedule %s with jobId = %d.", jobName, jobId);

        JobWorker worker = mJobServiceFactory.getJobWorkerInstance(jobId);
        if (jobName == null || worker == null) {
            throw new IllegalStateException(
                    "Failed to schedule job because it's not configured in JobInfo or JobConfig!"
                            + " Please check the setup. Requested job ID = "
                            + jobId);
        }

        if (!shouldSchedule(worker)) {
            LogUtil.v("The job scheduling for %s is skipped due to job is not enabled.", jobName);
            return SCHEDULING_RESULT_CODE_SKIPPED;
        }

        // Get the jobInfo to schedule.
        JobInfo jobInfoToSchedule = getJobInfoToSchedule(context, jobSpec, jobName);

        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LogUtil.e("Cannot fetch JobScheduler! Failed to schedule %s.", jobName);
            mErrorLogger.logError(
                    ERROR_CODE_JOB_SCHEDULER_IS_UNAVAILABLE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
            return SCHEDULING_RESULT_CODE_FAILED;
        }

        // Get the existing jobInfo, if existing, scheduled in the JobScheduler.
        JobInfo existingJobInfo = jobScheduler.getPendingJob(jobId);
        if (existingJobInfo == null) {
            jobScheduler.schedule(jobInfoToSchedule);
            logcatJobScheduledInfo(jobInfoToSchedule, existingJobInfo, jobName, forceSchedule);
            return SCHEDULING_RESULT_CODE_SUCCESSFUL;
        }
        // The "Extras" field in JobInfo isn't unparcelled when queried from the JobScheduler. Do
        // a data fetching to unparcel it in order to call equals() following.
        existingJobInfo.getExtras().getString(UNAVAILABLE_KEY);

        // Do NOT reschedule the job if not asked to force rescheduling or jobInfo doesn't change.
        if (!forceSchedule && existingJobInfo.equals(jobInfoToSchedule)) {
            LogUtil.v(
                    "The job %s has been scheduled with the same info, skip rescheduling it. %s",
                    jobName, JobUtil.jobInfoToString(existingJobInfo));
            return SCHEDULING_RESULT_CODE_SKIPPED;
        }

        jobScheduler.schedule(jobInfoToSchedule);
        logcatJobScheduledInfo(jobInfoToSchedule, existingJobInfo, jobName, forceSchedule);

        return SCHEDULING_RESULT_CODE_SUCCESSFUL;
    }

    // Gets a JobInfo to schedule the job. It's computed by merging the default JobPolicy from job
    // scheduling and the synced JobPolicy from Mendel server.
    @VisibleForTesting
    JobInfo getJobInfoToSchedule(Context context, JobSpec jobSpec, String jobName) {
        int jobId = jobSpec.getJobPolicy().getJobId();

        JobPolicy defaultJobPolicy = jobSpec.getJobPolicy();
        JobPolicy serverJobPolicy = getPolicyFromFlagServer(jobId, jobName);

        JobInfo.Builder builder = createBaseJobInfoBuilder(context, jobId);
        // Apply the Extras from the jobSpec.
        PersistableBundle extras = jobSpec.getExtras();
        if (extras != null) {
            builder.setExtras(extras);
        }

        // By default, use the default JobPolicy from the binary.
        JobPolicy mergedJobPolicy = JobPolicy.newBuilder(defaultJobPolicy).build();
        try {
            // Merge default JobPolicy and JobPolicy synced from server side. Note values from
            // server side will prevail if a field is set in both JobPolicies.
            mergedJobPolicy =
                    PolicyProcessor.mergeTwoJobPolicies(defaultJobPolicy, serverJobPolicy);
        } catch (IllegalArgumentException e) {
            LogUtil.e(e, ERROR_MESSAGE_POLICY_JOB_SCHEDULER_INVALID_JOB_INFO, jobName);
            mErrorLogger.logErrorWithExceptionInfo(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_INVALID_JOB_POLICY_SYNC,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
        }

        return PolicyProcessor.applyPolicyToJobInfo(builder, mergedJobPolicy);
    }

    // Get the JobPolicy from the Flag Server. Returns null if the policy doesn't exist or the
    // policy doesn't contain current job ID.
    @Nullable
    @VisibleForTesting
    JobPolicy getPolicyFromFlagServer(int jobId, String jobName) {
        ModuleJobPolicy moduleJobPolicy = mJobServiceFactory.getModuleJobPolicy();
        // Returns the default jobInfo if policy proto doesn't exist.
        if (moduleJobPolicy == null) {
            LogUtil.d(
                    "The Job policy is empty. Stop syncing policy from server. Use"
                            + " default JobInfo for %s.",
                    jobName);
            return null;
        }

        Map<Integer, JobPolicy> jobPolicyMap = moduleJobPolicy.getJobPolicyMap();
        // Returns the default jobInfo if this job doesn't have a policy configured in server.
        if (!jobPolicyMap.containsKey(jobId)) {
            LogUtil.v(
                    "There is no policy synced from server for job %s. Stop syncing policy from"
                            + " server and use default JobInfo.",
                    jobName);

            return null;
        }

        return jobPolicyMap.get(jobId);
    }

    @VisibleForTesting
    JobInfo.Builder createBaseJobInfoBuilder(Context context, int jobId) {
        return new JobInfo.Builder(jobId, new ComponentName(context, mJobServiceClass));
    }

    // Add a callback for logging.
    protected void addCallbackToSchedulingFuture(
            ListenableFuture<Integer> schedulingFuture, int jobId) {
        Futures.addCallback(
                schedulingFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(@JobSchedulingResultCode Integer result) {
                        LogUtil.v("Job %d has been scheduled by the configured executor.", jobId);

                        mJobSchedulingLogger.recordOnScheduling(jobId, result);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(t, "Job %d scheduling encountered an issue!", jobId);
                        mErrorLogger.logErrorWithExceptionInfo(
                                t,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_JOB_SCHEDULING_FAILURE,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);

                        mJobSchedulingLogger.recordOnScheduling(
                                jobId, SCHEDULING_RESULT_CODE_FAILED);
                    }
                },
                mExecutor);
    }

    private void logcatJobScheduledInfo(
            JobInfo jobInfoToSchedule,
            JobInfo existingJobInfo,
            String jobName,
            boolean forceSchedule) {
        if (existingJobInfo == null) {
            LogUtil.v(
                    "The job %s is scheduled for the first time with jobInfo %s",
                    jobName, jobInfoToString(jobInfoToSchedule));
            return;
        }

        if (forceSchedule) {
            LogUtil.v(
                    "The job %s is forced to be scheduled with jobInfo %s",
                    jobName, jobInfoToString(jobInfoToSchedule));
        } else {
            LogUtil.v(
                    "Job %s is scheduled with jobInfo %s. The old jobInfo is %s",
                    jobName, jobInfoToString(jobInfoToSchedule), jobInfoToString(existingJobInfo));
        }
    }

    // TODO(b/335461255): add more result code for scheduling logging.
    private boolean shouldSchedule(JobWorker worker) {
        return worker.getJobSchedulingEnablementStatus() == JOB_ENABLED_STATUS_ENABLED;
    }
}
