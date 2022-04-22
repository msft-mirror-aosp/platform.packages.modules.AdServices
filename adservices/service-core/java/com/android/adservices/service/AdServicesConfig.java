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

package com.android.adservices.service;


/**
 * Hard Coded Configs for AdServices.
 * For Feature Flags that are backed by PH, please see {@link PhFlags}
 */
public class AdServicesConfig {
    /**
     * Job Id for idle maintenance job ({@link MaintenanceJobService}).
     */
    public static final int MAINTENANCE_JOB_ID = 1;

    /**
     * Job Id for Topics Epoch Computation Job ({@link EpochJobService})
     */
    public static final int TOPICS_EPOCH_JOB_ID = 2;

    /**
     * Job Id for Measurement Main Reporting Job ({@link ReportingJobService})
     */
    public static final int MEASUREMENT_MAIN_REPORTING_JOB_ID = 3;
    public static long MEASUREMENT_MAIN_REPORTING_JOB_PERIOD_MS =
            4L * 60L * 60L * 1000L; // 4 hours.

    /**
     * Returns the max time period (in millis) between each main reporting maintenance job run.
     */
    public static long getMeasurementMainReportingJobPeriodMs() {
        return MEASUREMENT_MAIN_REPORTING_JOB_PERIOD_MS;
    }

    /**
     * Job Id for Measurement Delete Expired Records Job ({@link DeleteExpiredJobService})
     */
    public static final int MEASUREMENT_DELETE_EXPIRED_JOB_ID = 4;
    public static long MEASUREMENT_DELETE_EXPIRED_JOB_PERIOD_MS =
            24L * 60L * 60L * 1000L; // 24 hours.
    public static long MEASUREMENT_DELETE_EXPIRED_WINDOW_MS =
            28L * 24L * 60L * 60L * 1000L; // 28 days.

    /**
     * Returns the max time period (in millis) between each expired-record deletion maintenance job
     * run.
     */
    public static long getMeasurementDeleteExpiredJobPeriodMs() {
        return MEASUREMENT_DELETE_EXPIRED_JOB_PERIOD_MS;
    }

    /**
     * Job Id for Measurement Attribution Job
     * ({@link com.android.adservices.service.measurement.AttributionJobService}).
     */
    public static final int MEASUREMENT_ATTRIBUTION_JOB_ID = 5;

    /**
     * Job Id for Measurement Fallback Reporting Job ({@link FallbackReportingJobService})
     */
    public static final int MEASUREMENT_FALLBACK_REPORTING_JOB_ID = 6;
    public static long MEASUREMENT_FALLBACK_REPORTING_JOB_PERIOD_MS =
            24L * 60L * 60L * 1000L; // 24 hours.

    public static long getMeasurementFallbackReportingJobPeriodMs() {
        return MEASUREMENT_FALLBACK_REPORTING_JOB_PERIOD_MS;
    }
}
