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

package com.android.adservices.service.customaudience;

import static com.android.adservices.service.consent.AdServicesApiType.FLEDGE;
import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_USER_CONSENT_REVOKED;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.adservices.shared.spe.framework.ExecutionResult.SUCCESS;
import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_BACKGROUND_FETCH_JOB;

import android.annotation.RequiresApi;
import android.content.Context;
import android.os.Build;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.shared.proto.JobPolicy;
import com.android.adservices.shared.spe.framework.ExecutionResult;
import com.android.adservices.shared.spe.framework.ExecutionRuntimeParameters;
import com.android.adservices.shared.spe.framework.JobWorker;
import com.android.adservices.shared.spe.scheduling.JobSpec;
import com.android.adservices.spe.AdServicesJobScheduler;
import com.android.adservices.spe.AdServicesJobServiceFactory;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.ListenableFuture;

import java.time.Instant;

/**
 * Background fetch for FLEDGE Custom Audience API, executing periodic garbage collection and custom
 * audience updates.
 */
@RequiresApi(Build.VERSION_CODES.S)
public class BackgroundFetchJob implements JobWorker {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @Override
    public ListenableFuture<ExecutionResult> getExecutionFuture(
            Context context, ExecutionRuntimeParameters executionRuntimeParameters) {
        // TODO(b/235841960): Consider using com.android.adservices.service.stats.Clock instead of
        // Java Clock.
        Instant jobStartTime = java.time.Clock.systemUTC().instant();
        sLogger.d("Starting FLEDGE background fetch job at %s", jobStartTime.toString());

        return BackgroundFetchWorker.getInstance(context)
                .runBackgroundFetch()
                .transform(voidResult -> SUCCESS, AdServicesExecutors.getBackgroundExecutor());
    }

    @Override
    public int getJobEnablementStatus() {
        Flags flags = FlagsFactory.getFlags();

        if (!flags.getFledgeBackgroundFetchEnabled()) {
            sLogger.d("FLEDGE background fetch is disabled; skipping and cancelling job");
            return JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
        }

        if (flags.getFledgeCustomAudienceServiceKillSwitch()) {
            sLogger.d("FLEDGE Custom Audience API is disabled ; skipping and cancelling job");
            return JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
        }

        // Skip the execution and cancel the job if user consent is revoked.
        // Use the per-API consent with GA UX.
        if (!ConsentManager.getInstance().getConsent(FLEDGE).isGiven()) {
            sLogger.d("User Consent is revoked ; skipping and cancelling job");
            return JOB_ENABLED_STATUS_DISABLED_FOR_USER_CONSENT_REVOKED;
        }
        return JOB_ENABLED_STATUS_ENABLED;
    }

    /**
     * Attempts to schedule the FLEDGE Background Fetch as a singleton periodic job if it is not
     * already scheduled.
     *
     * <p>The background fetch primarily updates custom audiences' ads and bidding data. It also
     * prunes the custom audience database of any expired data.
     */
    public static void schedule(Flags flags) {
        // If SPE is not enabled, force to schedule the job with the old JobService.
        if (!flags.getSpeOnPilotJobsBatch2Enabled()) {
            sLogger.d("SPE is not enabled. Schedule the job with BackgroundFetchJobService.");
            int resultCode =
                    BackgroundFetchJobService.scheduleIfNeeded(flags, /* forceSchedule= */ false);

            AdServicesJobServiceFactory.getInstance()
                    .getJobSchedulingLogger()
                    .recordOnSchedulingLegacy(FLEDGE_BACKGROUND_FETCH_JOB.getJobId(), resultCode);
            return;
        }

        AdServicesJobScheduler.getInstance().schedule(createJobSpec(flags));
    }

    @VisibleForTesting
    static JobSpec createJobSpec(Flags flags) {
        JobPolicy jobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(FLEDGE_BACKGROUND_FETCH_JOB.getJobId())
                        .setBatteryType(BATTERY_TYPE_REQUIRE_NOT_LOW)
                        .setRequireDeviceIdle(true)
                        .setPeriodicJobParams(
                                JobPolicy.PeriodicJobParams.newBuilder()
                                        .setPeriodicIntervalMs(
                                                flags.getFledgeBackgroundFetchJobPeriodMs())
                                        .setFlexInternalMs(
                                                flags.getFledgeBackgroundFetchJobFlexMs())
                                        .build())
                        .setNetworkType(JobPolicy.NetworkType.NETWORK_TYPE_UNMETERED)
                        .setIsPersisted(true)
                        .build();

        return new JobSpec.Builder(jobPolicy).build();
    }
}
