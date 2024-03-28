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

import android.app.job.JobParameters;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.shared.spe.framework.AbstractJobService;
import com.android.adservices.shared.spe.framework.JobServiceFactory;

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
        if (!FlagsFactory.getFlags().getSpeOnPilotJobsEnabled()) {
            AdServicesJobServiceFactory factory =
                    (AdServicesJobServiceFactory) getJobServiceFactory();
            factory.rescheduleJobWithLegacyMethod(jobId);
            return false;
        }

        return super.onStartJob(params);
    }
}
