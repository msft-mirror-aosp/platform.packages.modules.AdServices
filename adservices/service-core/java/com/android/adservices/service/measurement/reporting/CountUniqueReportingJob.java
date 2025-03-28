/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.adservices.shared.spe.framework.ExecutionResult.SUCCESS;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_COUNT_UNIQUE_REPORTING_JOB;

import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.proto.JobPolicy;
import com.android.adservices.shared.spe.framework.ExecutionResult;
import com.android.adservices.shared.spe.framework.ExecutionRuntimeParameters;
import com.android.adservices.shared.spe.framework.JobWorker;
import com.android.adservices.shared.spe.scheduling.JobSpec;
import com.android.adservices.spe.AdServicesJobScheduler;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Main job for scheduling count unique reporting. The actual job execution logic is part of {@link
 * CountUniqueReportingJobHandler}
 */
@RequiresApi(Build.VERSION_CODES.S)
public class CountUniqueReportingJob implements JobWorker {
    @Override
    public ListenableFuture<ExecutionResult> getExecutionFuture(
            Context context, ExecutionRuntimeParameters executionRuntimeParameters) {
        return Futures.submit(
                () -> {
                    CountUniqueReportingJobHandler.getInstance().performScheduledPendingReports();
                    return SUCCESS;
                },
                AdServicesExecutors.getBackgroundExecutor());
    }

    @Override
    public int getJobEnablementStatus() {
        Flags flags = FlagsFactory.getFlags();
        return isJobEnabled(flags)
                ? JOB_ENABLED_STATUS_ENABLED
                : JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
    }

    /** Schedule the Count Unique reporting job. */
    public static void schedule() {
        AdServicesJobScheduler.getInstance().schedule(createJobSpec());
    }

    @VisibleForTesting
    static JobSpec createJobSpec() {
        Flags flags = FlagsFactory.getFlags();
        JobPolicy.PeriodicJobParams periodicJobParams =
                JobPolicy.PeriodicJobParams.newBuilder()
                        .setPeriodicIntervalMs(
                                flags.getMeasurementCountUniqueReportingJobPeriodMs())
                        .build();
        JobPolicy jobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(MEASUREMENT_COUNT_UNIQUE_REPORTING_JOB.getJobId())
                        .setBatteryType(JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NONE)
                        .setNetworkType(JobPolicy.NetworkType.NETWORK_TYPE_UNMETERED)
                        .setPeriodicJobParams(periodicJobParams)
                        .setIsPersisted(true)
                        .build();

        return new JobSpec.Builder(jobPolicy).build();
    }

    private boolean isJobEnabled(Flags flags) {
        return (flags.getMeasurementEnableCountUniqueService()
                && flags.getMeasurementEnableCountUniqueReportingJob());
    }
}
