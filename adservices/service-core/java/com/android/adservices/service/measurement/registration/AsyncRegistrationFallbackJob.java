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

package com.android.adservices.service.measurement.registration;

import static com.android.adservices.service.Flags.ASYNC_REGISTRATION_JOB_QUEUE_INTERVAL_MS;
import static com.android.adservices.service.measurement.util.JobLockHolder.Type.ASYNC_REGISTRATION_PROCESSING;
import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
import static com.android.adservices.shared.proto.JobPolicy.NetworkType.NETWORK_TYPE_ANY;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.adservices.shared.spe.framework.ExecutionResult.SUCCESS;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB;

import android.annotation.RequiresApi;
import android.content.Context;
import android.os.Build;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.util.JobLockHolder;
import com.android.adservices.shared.proto.JobPolicy;
import com.android.adservices.shared.spe.framework.ExecutionResult;
import com.android.adservices.shared.spe.framework.ExecutionRuntimeParameters;
import com.android.adservices.shared.spe.framework.JobWorker;
import com.android.adservices.shared.spe.scheduling.BackoffPolicy;
import com.android.adservices.shared.spe.scheduling.JobSpec;
import com.android.adservices.spe.AdServicesJobScheduler;
import com.android.adservices.spe.AdServicesJobServiceFactory;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/** Fallback Job Service for servicing queued registration requests. */
// TODO(b/328287543): Since Rb has released to R so functionally this class should support R. Due to
// Legacy issue, class such as BackgroundJobsManager and MddJobService which have to support R also
// have this annotation. It won't have production impact but is needed to bypass the build error.
@RequiresApi(Build.VERSION_CODES.S)
public final class AsyncRegistrationFallbackJob implements JobWorker {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getMeasurementLogger();

    @Override
    public ListenableFuture<ExecutionResult> getExecutionFuture(
            Context context, ExecutionRuntimeParameters executionRuntimeParameters) {
        return Futures.submit(
                () -> {
                    processAsyncRecords(context);
                    return SUCCESS;
                },
                AdServicesExecutors.getBlockingExecutor());
    }

    @Override
    public int getJobEnablementStatus() {
        if (FlagsFactory.getFlags().getAsyncRegistrationFallbackJobKillSwitch()) {
            return JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
        }

        return JOB_ENABLED_STATUS_ENABLED;
    }

    /** Schedules the {@link AsyncRegistrationFallbackJob}. */
    public static void schedule() {
        // If SPE is not enabled, force to schedule the job with the old JobService.
        if (!FlagsFactory.getFlags().getSpeOnPilotJobsBatch2Enabled()) {
            sLogger.d(
                    "SPE is not enabled. Schedule the job with"
                            + " AsyncRegistrationFallbackJobService.");
            int resultCode =
                    AsyncRegistrationFallbackJobService.scheduleIfNeeded(
                            /* forceSchedule= */ false);

            AdServicesJobServiceFactory.getInstance()
                    .getJobSchedulingLogger()
                    .recordOnSchedulingLegacy(
                            MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB.getJobId(), resultCode);
            return;
        }

        AdServicesJobScheduler.getInstance().schedule(createDefaultJobSpec());
    }

    @VisibleForTesting
    static JobSpec createDefaultJobSpec() {
        JobPolicy jobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB.getJobId())
                        .setBatteryType(BATTERY_TYPE_REQUIRE_NOT_LOW)
                        .setPeriodicJobParams(
                                JobPolicy.PeriodicJobParams.newBuilder()
                                        .setPeriodicIntervalMs(
                                                ASYNC_REGISTRATION_JOB_QUEUE_INTERVAL_MS)
                                        .build())
                        .setNetworkType(NETWORK_TYPE_ANY)
                        .setIsPersisted(true)
                        .build();

        BackoffPolicy backoffPolicy =
                new BackoffPolicy.Builder().setShouldRetryOnExecutionStop(true).build();

        return new JobSpec.Builder(jobPolicy).setBackoffPolicy(backoffPolicy).build();
    }

    @VisibleForTesting
    void processAsyncRecords(Context context) {
        final JobLockHolder lock = JobLockHolder.getInstance(ASYNC_REGISTRATION_PROCESSING);
        if (lock.tryLock()) {
            try {
                AsyncRegistrationQueueRunner.getInstance(context).runAsyncRegistrationQueueWorker();
                return;
            } finally {
                lock.unlock();
            }
        }

        sLogger.d("AsyncRegistrationFallbackJob did not acquire the lock");
    }
}
