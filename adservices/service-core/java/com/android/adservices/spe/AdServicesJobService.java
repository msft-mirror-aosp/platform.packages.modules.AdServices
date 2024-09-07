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

package com.android.adservices.spe;

import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_BACK_COMPAT_OTA;
import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_BACKGROUND_FETCH_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_CHARGING_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_MAINTENANCE_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.TOPICS_EPOCH_JOB;

import android.app.job.JobParameters;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.shared.spe.framework.AbstractJobService;
import com.android.adservices.shared.spe.framework.JobServiceFactory;
import com.android.internal.annotations.VisibleForTesting;

/** The Adservices' implementation of {@link AbstractJobService}. */
@RequiresApi(Build.VERSION_CODES.S)
public final class AdServicesJobService extends AbstractJobService {
    @Override
    protected JobServiceFactory getJobServiceFactory() {
        return AdServicesJobServiceFactory.getInstance();
    }

    /**
     * Overrides {@link AbstractJobService#onStartJob(JobParameters)} to add the logic to cancel
     * Android S- job in T+ build.
     */
    @Override
    public boolean onStartJob(JobParameters params) {
        int jobId = params.getJobId();
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            getJobServiceFactory().getJobServiceLogger().recordOnStartJob(jobId);

            LogUtil.d("Disabling job %d because it's running in ExtServices on T+", jobId);
            skipAndCancelBackgroundJob(params, JOB_ENABLED_STATUS_DISABLED_FOR_BACK_COMPAT_OTA);
            return false;
        }

        // Switch to the legacy job scheduling if SPE is disabled. Since job ID remains the same,
        // the scheduled job will be cancelled and rescheduled with the legacy method.
        //
        // And after the job is rescheduled, it will execute once instantly so don't log execution
        // stats here.
        if (shouldRescheduleWithLegacyMethod(jobId)) {
            LogUtil.d(
                    "SPE is disabled. Reschedule SPE job instance of jobId=%d with its legacy"
                            + " JobService scheduling method.",
                    jobId);
            AdServicesJobServiceFactory factory =
                    (AdServicesJobServiceFactory) getJobServiceFactory();
            factory.rescheduleJobWithLegacyMethod(jobId);
            return false;
        }

        return super.onStartJob(params);
    }

    // Determine whether we should cancel and reschedule current job with the legacy JobService
    // class. It could happen when SPE has a production issue.
    //
    // First batch pilot jobs: MddJobService, job ID = 11, 12, 13, 14.
    // Second batch pilot jobs:
    //     - EpochJobService, job ID = 2.
    //     - BackgroundFetchJobService, job ID = 9.
    //     - AsyncRegistrationFallbackJobService, job ID = 19.
    @VisibleForTesting
    boolean shouldRescheduleWithLegacyMethod(int jobId) {
        Flags flags = FlagsFactory.getFlags();

        if (isFirstBatchPilotJobDisabledForSpe(jobId, flags)) {
            return true;
        }

        if (isSecondBatchPilotJobDisabledForSpe(jobId, flags)) {
            return true;
        }

        return false;
    }

    private boolean isFirstBatchPilotJobDisabledForSpe(int jobId, Flags flags) {
        return (jobId == MDD_WIFI_CHARGING_PERIODIC_TASK_JOB.getJobId()
                        || jobId == MDD_MAINTENANCE_PERIODIC_TASK_JOB.getJobId()
                        || jobId == MDD_CHARGING_PERIODIC_TASK_JOB.getJobId()
                        || jobId == MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB.getJobId())
                && !flags.getSpeOnPilotJobsEnabled();
    }

    private boolean isSecondBatchPilotJobDisabledForSpe(int jobId, Flags flags) {
        if ((jobId == TOPICS_EPOCH_JOB.getJobId() && !flags.getSpeOnEpochJobEnabled())
                || (jobId == FLEDGE_BACKGROUND_FETCH_JOB.getJobId()
                        && !flags.getSpeOnBackgroundFetchJobEnabled())
                || (jobId == MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB.getJobId()
                        && !flags.getSpeOnAsyncRegistrationFallbackJobEnabled())) {
            return true;
        }
        return false;
    }
}
