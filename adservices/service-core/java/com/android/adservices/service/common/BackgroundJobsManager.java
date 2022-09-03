/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.common;

import android.annotation.NonNull;
import android.app.job.JobScheduler;
import android.content.Context;

import com.android.adservices.download.MddJobService;
import com.android.adservices.service.AdServicesConfig;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.MaintenanceJobService;
import com.android.adservices.service.measurement.DeleteExpiredJobService;
import com.android.adservices.service.measurement.attribution.AttributionJobService;
import com.android.adservices.service.measurement.reporting.AggregateFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.AggregateReportingJobService;
import com.android.adservices.service.measurement.reporting.EventFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.EventReportingJobService;
import com.android.adservices.service.topics.EpochJobService;

import java.util.Objects;

/** Provides functionality to schedule or unschedule all relevant background jobs. */
public class BackgroundJobsManager {
    /**
     * Tries to schedule all the relevant background jobs.
     *
     * @param context application context.
     */
    public static void scheduleAllBackgroundJobs(@NonNull Context context) {
        if (!FlagsFactory.getFlags().getTopicsKillSwitch()) {
            EpochJobService.scheduleIfNeeded(context, false);
            MaintenanceJobService.scheduleIfNeeded(context, false);
            MddJobService.scheduleIfNeeded(context, /* forceSchedule */ false);
        }

        if (!FlagsFactory.getFlags().getMeasurementKillSwitch()) {
            AggregateReportingJobService.scheduleIfNeeded(context, false);
            AggregateFallbackReportingJobService.scheduleIfNeeded(context, false);
            AttributionJobService.scheduleIfNeeded(context, false);
            EventReportingJobService.scheduleIfNeeded(context, false);
            EventFallbackReportingJobService.scheduleIfNeeded(context, false);
            DeleteExpiredJobService.scheduleIfNeeded(context, false);
        }
    }

    /**
     * Tries to unschedule all the relevant background jobs.
     *
     * @param jobScheduler Job scheduler to cancel the jobs.
     */
    public static void unscheduleAllBackgroundJobs(@NonNull JobScheduler jobScheduler) {
        Objects.requireNonNull(jobScheduler);

        jobScheduler.cancel(AdServicesConfig.MAINTENANCE_JOB_ID);
        jobScheduler.cancel(AdServicesConfig.TOPICS_EPOCH_JOB_ID);

        jobScheduler.cancel(AdServicesConfig.MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID);
        jobScheduler.cancel(AdServicesConfig.MEASUREMENT_DELETE_EXPIRED_JOB_ID);
        jobScheduler.cancel(AdServicesConfig.MEASUREMENT_ATTRIBUTION_JOB_ID);
        jobScheduler.cancel(AdServicesConfig.MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_ID);
        jobScheduler.cancel(AdServicesConfig.MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID);
        jobScheduler.cancel(AdServicesConfig.MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID);

        jobScheduler.cancel(AdServicesConfig.FLEDGE_BACKGROUND_FETCH_JOB_ID);

        jobScheduler.cancel(AdServicesConfig.CONSENT_NOTIFICATION_JOB_ID);

        MddJobService.unscheduleAllJobs(jobScheduler);
    }
}
