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

package com.android.adservices.service.topics;

import static com.android.adservices.service.Flags.TOPICS_EPOCH_JOB_FLEX_MS;
import static com.android.adservices.service.Flags.TOPICS_EPOCH_JOB_PERIOD_MS;
import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_CHARGING;
import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_UNKNOWN;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.adservices.shared.spe.framework.ExecutionResult.SUCCESS;
import static com.android.adservices.spe.AdServicesJobInfo.TOPICS_EPOCH_JOB;

import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.proto.JobPolicy;
import com.android.adservices.shared.spe.framework.ExecutionResult;
import com.android.adservices.shared.spe.framework.ExecutionRuntimeParameters;
import com.android.adservices.shared.spe.framework.JobWorker;
import com.android.adservices.shared.spe.scheduling.JobSpec;
import com.android.adservices.spe.AdServicesJobScheduler;
import com.android.adservices.spe.AdServicesJobServiceFactory;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/** Epoch computation job. This will be run approximately once per epoch to compute Topics. */
@RequiresApi(Build.VERSION_CODES.S)
public final class EpochJob implements JobWorker {
    @Override
    public ListenableFuture<ExecutionResult> getExecutionFuture(
            Context context, ExecutionRuntimeParameters executionRuntimeParameters) {
        return Futures.submit(
                () -> {
                    TopicsWorker.getInstance().computeEpoch();
                    return SUCCESS;
                },
                AdServicesExecutors.getBackgroundExecutor());
    }

    @Override
    public int getJobEnablementStatus() {
        if (FlagsFactory.getFlags().getTopicsKillSwitch()) {
            LoggerFactory.getTopicsLogger()
                    .e("Topics API is disabled, skipping and cancelling EpochJobService");
            return JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
        }

        return JOB_ENABLED_STATUS_ENABLED;
    }

    /** Schedules the {@link EpochJob}. */
    public static void schedule() {
        // If SPE is not enabled, force to schedule the job with the old JobService.
        if (!FlagsFactory.getFlags().getSpeOnEpochJobEnabled()) {
            LoggerFactory.getTopicsLogger()
                    .d("SPE is not enabled. Schedule the job with EpochJobService.");
            int resultCode = EpochJobService.scheduleIfNeeded(/* forceSchedule= */ false);

            AdServicesJobServiceFactory.getInstance()
                    .getJobSchedulingLogger()
                    .recordOnSchedulingLegacy(TOPICS_EPOCH_JOB.getJobId(), resultCode);
            return;
        }

        AdServicesJobScheduler.getInstance().schedule(createDefaultJobSpec());
    }

    @VisibleForTesting
    static JobSpec createDefaultJobSpec() {
        JobPolicy.BatteryType batteryType = BATTERY_TYPE_UNKNOWN;
        if (FlagsFactory.getFlags().getTopicsEpochJobBatteryNotLowInsteadOfCharging()) {
            batteryType = BATTERY_TYPE_REQUIRE_NOT_LOW;
        } else {
            batteryType = BATTERY_TYPE_REQUIRE_CHARGING;
        }
        JobPolicy jobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(TOPICS_EPOCH_JOB.getJobId())
                        .setBatteryType(batteryType)
                        .setIsPersisted(true)
                        .setPeriodicJobParams(
                                JobPolicy.PeriodicJobParams.newBuilder()
                                        .setPeriodicIntervalMs(TOPICS_EPOCH_JOB_PERIOD_MS)
                                        .setFlexInternalMs(TOPICS_EPOCH_JOB_FLEX_MS)
                                        .build())
                        .build();
        LoggerFactory.getTopicsLogger().d(
                "SPE is enabled. Battery type of EpochJob: " + jobPolicy.getBatteryType());

        return new JobSpec.Builder(jobPolicy).build();
    }
}
