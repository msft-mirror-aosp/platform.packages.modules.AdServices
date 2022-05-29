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

import java.util.concurrent.TimeUnit;

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
     * Job Id for Measurement Event Main Reporting Job ({@link EventReportingJobService})
     */
    public static final int MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID = 3;
    public static long MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS =
            TimeUnit.HOURS.toMillis(4);

    /**
     * Returns the min time period (in millis) between each event main reporting job run.
     */

    public static long getMeasurementEventMainReportingJobPeriodMs() {
        return FlagsFactory.getFlags().getMeasurementEventMainReportingJobPeriodMs();
    }

    /**
     * Job Id for Measurement Delete Expired Records Job ({@link DeleteExpiredJobService})
     */
    public static final int MEASUREMENT_DELETE_EXPIRED_JOB_ID = 4;
    public static long MEASUREMENT_DELETE_EXPIRED_JOB_PERIOD_MS =
            TimeUnit.HOURS.toMillis(24);
    public static long MEASUREMENT_DELETE_EXPIRED_WINDOW_MS = TimeUnit.DAYS.toMillis(30);

    /**
     * Returns the min time period (in millis) between each expired-record deletion maintenance job
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
     * Job Id for Measurement Fallback Reporting Job ({@link EventFallbackReportingJobService})
     */
    public static final int MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_ID = 6;
    public static long MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS =
            TimeUnit.HOURS.toMillis(24);

    /**
     * Returns the min time period (in millis) between each event fallback reporting job run.
     */
    public static long getMeasurementEventFallbackReportingJobPeriodMs() {
        return FlagsFactory.getFlags().getMeasurementEventFallbackReportingJobPeriodMs();
    }

    /**
     * Job Id for Measurement Aggregate Main Reporting Job ({@link AggregateReportingJobService})
     */
    public static final int MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID = 7;
    public static long MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS =
            TimeUnit.HOURS.toMillis(4);

    /**
     * Returns the min time period (in millis) between each aggregate main reporting job run.
     */
    public static long getMeasurementAggregateMainReportingJobPeriodMs() {
        return FlagsFactory.getFlags().getMeasurementAggregateMainReportingJobPeriodMs();
    }

    /**
     * Job Id for Measurement Aggregate Fallback Reporting Job
     * ({@link AggregateFallbackReportingJobService})
     */
    public static final int MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID = 8;
    public static long MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS =
            TimeUnit.HOURS.toMillis(24);

    /**
     * Returns the min time period (in millis) between each aggregate fallback reporting job run.
     */
    public static long getMeasurementAggregateFallbackReportingJobPeriodMs() {
        return FlagsFactory.getFlags().getMeasurementAggregateFallbackReportingJobPeriodMs();
    }
}
