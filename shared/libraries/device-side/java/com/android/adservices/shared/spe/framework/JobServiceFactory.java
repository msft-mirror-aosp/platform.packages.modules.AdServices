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

import com.android.adservices.shared.common.flags.ModuleSharedFlags;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.proto.ModuleJobPolicy;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.shared.spe.logging.JobServiceLogger;
import com.android.adservices.shared.spe.scheduling.PolicyJobScheduler;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * The configuration a module should set in order to use the SPE (Scheduling Policy Engine)
 * framework, including {@link AbstractJobService} and {@link PolicyJobScheduler}. {@code
 * adservices/service-core/java/com/android/adservices/spe/AdServicesJobServiceConfig.java} can be
 * used as an example.
 */
public interface JobServiceFactory {
    /**
     * Creates a {@link JobWorker} instance given a unique job ID. This method helps to invoke
     * associated methods belonging to a {@link JobWorker} when the Platform scheduler invokes it
     * during the {@link android.app.job.JobService}'s Lifecycle.
     *
     * @param jobId the unique job ID in a module.
     * @return the instance of the job associated with the given job ID.
     */
    JobWorker getJobWorkerInstance(int jobId);

    /**
     * Gets a mapping between the job ID and the job name for a job. This method helps to provide
     * informative logging.
     *
     * @return the mapping of job ID and job name.
     */
    Map<Integer, String> getJobIdToNameMap();

    /** Gets an instance of {@link JobServiceLogger}. */
    JobServiceLogger getJobServiceLogger();

    /** Gets an instance of {@link JobSchedulingLogger}. */
    JobSchedulingLogger getJobSchedulingLogger();

    /** Gets an instance of {@link AdServicesErrorLogger}. */
    AdServicesErrorLogger getErrorLogger();

    /**
     * Gets the executor used for framework-level tasks. For example, logging, cancelling jobs in
     * the background, scheduling jobs in the background, etc.
     *
     * <p>Note it recommends to use background executor because the task is not light and doesn't
     * require Internet.
     */
    Executor getBackgroundExecutor();

    /** Gets the {@link ModuleJobPolicy}. It stores the policy info parsed from the server. */
    ModuleJobPolicy getModuleJobPolicy();

    /** Gets the flags configured by a module to pass into SPE. */
    ModuleSharedFlags getFlags();
}
