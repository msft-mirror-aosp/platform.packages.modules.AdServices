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

import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.Context;

import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.shared.spe.JobServiceConstants.JobEnablementStatus;
import com.android.adservices.shared.spe.scheduling.BackoffPolicy;
import com.android.adservices.shared.spe.scheduling.PolicyJobScheduler;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Future;

/**
 * The interface used to implement a background job using the SPE (Scheduling Policy Engine)
 * framework. For SPE, the execution part will be processed by {@link AbstractJobService} and the
 * scheduling part will be processed by {@link PolicyJobScheduler}.
 *
 * <p>At the minimum effort, the job owner only needs to implement below methods,
 *
 * <ul>
 *   <li>{@link JobWorker#getExecutionFuture(Context, ExecutionRuntimeParameters)}
 *   <li>{@link JobWorker#getJobEnablementStatus()}
 * </ul>
 *
 * Override below methods if needed,
 *
 * <ul>
 *   <li>{@link JobWorker#getExecutionStopFuture(Context, ExecutionRuntimeParameters)}
 * </ul>
 */
public interface JobWorker {
    /**
     * Gets the Future for the job execution. It should be the specific task for a job to execute
     * when JobScheduler invokes {@link JobService#onStartJob(JobParameters)}. For example, if the
     * job is DbDeletionJob, this future should contain the database deletion logic. See {@link
     * AbstractJobService} for more details.
     *
     * <p>Please chain multiple tasks using mature {@link Future} libraries like {@link
     * com.google.common.util.concurrent.FluentFuture} to consolidate them into a single {@link
     * Future} to return.
     *
     * @param context the context of the app of a module.
     * @param executionRuntimeParameters the jobParameters passed in from {@link
     *     JobService#onStartJob(JobParameters)} and extract useful info into a {@link
     *     ExecutionRuntimeParameters}.
     * @return a {@link ListenableFuture} that will be executed by {@link JobScheduler} in {@link
     *     JobService#onStartJob(JobParameters)}. For {@link ExecutionResult}, please use {@link
     *     ExecutionResult#SUCCESS}. Check {@link ExecutionResult} for more details if you configure
     *     the job that needs to compute the execution result dynamically.
     */
    ListenableFuture<ExecutionResult> getExecutionFuture(
            Context context, ExecutionRuntimeParameters executionRuntimeParameters);

    /**
     * Determines if the job execution is enabled. For example, the condition could be a combination
     * of kill switches and feature flags. The execution will be disabled if the flags indicate so.
     *
     * <p><b>Note</b>: In production, it's suggested to always use one (or more) flag(s) to guard
     * the job execution. For example,
     *
     * <p>{@code public int isEnabled() { if (!FlagsFactory.getInstance().getIsFooFeatureEnabled) {
     * return JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON; } } }
     *
     * @return {@link JobEnablementStatus#JOB_ENABLED_STATUS_ENABLED} if the execution is enabled.
     *     Return other values in {@link JobEnablementStatus} if the execution needs to be disabled.
     *     Please add a skip reason to {@link AdServicesStatsLog} and {@link JobEnablementStatus} if
     *     needed. This will be used for logging in the server.
     */
    @JobEnablementStatus
    int getJobEnablementStatus();

    /**
     * A default method to get the extra logic to be executed if it's stopped by {@link
     * JobScheduler} due to reasons like device issues, constraint not met, etc.
     *
     * @param context the context of the app of a module.
     * @param executionRuntimeParameters the jobParameters passed in from {@link
     *     JobService#onStartJob(JobParameters)} and extract useful info into a {@link
     *     ExecutionRuntimeParameters}.
     * @return a {@link Future} that will be executed by {@link JobScheduler}. By default, it
     *     returns an immediate void future.
     */
    default ListenableFuture<Void> getExecutionStopFuture(
            Context context, ExecutionRuntimeParameters executionRuntimeParameters) {
        return Futures.immediateVoidFuture();
    }

    /**
     * Determines if the job scheduling is enabled.
     *
     * <p>By default, it returns the same value as {@link #getJobEnablementStatus()}. Override this
     * method when the condition of enablement in job execution and scheduling are different.
     *
     * @return {@link JobEnablementStatus#JOB_ENABLED_STATUS_ENABLED} if the scheduling is enabled.
     *     Return other values in {@link JobEnablementStatus} if the scheduling needs to be
     *     disabled. Please add a skip reason to {@link AdServicesStatsLog} and {@link
     *     JobEnablementStatus} if needed.
     */
    @JobEnablementStatus
    default int getJobSchedulingEnablementStatus() {
        return getJobEnablementStatus();
    }

    /**
     * A default method to get the backoff policy for failed executions. By default, the failed
     * execution doesn't retry.
     *
     * @return a {@link BackoffPolicy}.
     */
    default BackoffPolicy getBackoffPolicy() {
        return new BackoffPolicy.Builder().build();
    }
}
