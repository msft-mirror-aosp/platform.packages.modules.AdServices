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

import android.annotation.Nullable;

import com.android.adservices.shared.common.flags.ModuleSharedFlags;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.proto.ModuleJobPolicy;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.shared.spe.logging.JobServiceLogger;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** An implementation of {@link JobServiceFactory} used in unit tests. */
public final class TestJobServiceFactory implements JobServiceFactory {
    public static final int JOB_ID_1 = 1;
    public static final String JOB_NAME_1 = "TestJob1";

    private final JobWorker mJobWorker;
    private final JobServiceLogger mJobServiceLogger;
    private final ModuleJobPolicy mModuleJobPolicy;
    private final AdServicesErrorLogger mErrorLogger;

    private final JobSchedulingLogger mJobSchedulingLogger;

    public TestJobServiceFactory(
            @Nullable JobWorker jobWorker,
            @Nullable JobServiceLogger logger,
            @Nullable ModuleJobPolicy moduleJobPolicy,
            @Nullable AdServicesErrorLogger errorLogger,
            @Nullable JobSchedulingLogger jobSchedulingLogger) {
        mJobWorker = jobWorker;
        mJobServiceLogger = logger;
        mModuleJobPolicy = moduleJobPolicy;
        mErrorLogger = errorLogger;
        mJobSchedulingLogger = jobSchedulingLogger;
    }

    @Override
    public JobWorker getJobWorkerInstance(int jobId) {
        if (jobId == JOB_ID_1) {
            return mJobWorker;
        }

        return null;
    }

    @Override
    public Map<Integer, String> getJobIdToNameMap() {
        return Map.of(JOB_ID_1, JOB_NAME_1);
    }

    @Override
    public JobServiceLogger getJobServiceLogger() {
        return mJobServiceLogger;
    }

    @Override
    public AdServicesErrorLogger getErrorLogger() {
        return mErrorLogger;
    }

    @Override
    public Executor getBackgroundExecutor() {
        return Executors.newCachedThreadPool();
    }

    @Override
    public ModuleJobPolicy getModuleJobPolicy() {
        return mModuleJobPolicy;
    }

    @Override
    public ModuleSharedFlags getFlags() {
        return new ModuleSharedFlags() {};
    }

    @Override
    public JobSchedulingLogger getJobSchedulingLogger() {
        return mJobSchedulingLogger;
    }
}
