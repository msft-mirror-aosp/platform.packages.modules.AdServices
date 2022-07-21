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
 *
 * <p>For Feature Flags that are backed by PH, please see {@link PhFlags}
 */
public class AdServicesConfig {
    /** Job ID for idle maintenance job ({@link MaintenanceJobService}). */
    public static final int MAINTENANCE_JOB_ID = 1;

    /**
     * Job ID for Topics Epoch Computation Job ({@link
     * com.android.adservices.service.topics.EpochJobService})
     */
    public static final int TOPICS_EPOCH_JOB_ID = 2;

    /**
     * Job ID for Measurement Event Main Reporting Job ({@link
     * com.android.adservices.service.measurement.EventReportingJobService})
     */
    public static final int MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID = 3;

    public static long MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS = TimeUnit.HOURS.toMillis(4);

    public static long getMeasurementEventMainReportingJobPeriodMs() {
        return FlagsFactory.getFlags().getMeasurementEventMainReportingJobPeriodMs();
    }

    /**
     * Job ID for Measurement Delete Expired Records Job ({@link
     * com.android.adservices.service.measurement.DeleteExpiredJobService})
     */
    public static final int MEASUREMENT_DELETE_EXPIRED_JOB_ID = 4;

    public static long MEASUREMENT_DELETE_EXPIRED_JOB_PERIOD_MS = TimeUnit.HOURS.toMillis(24);
    public static long MEASUREMENT_DELETE_EXPIRED_WINDOW_MS = TimeUnit.DAYS.toMillis(30);

    /**
     * Returns the min time period (in millis) between each expired-record deletion maintenance job
     * run.
     */
    public static long getMeasurementDeleteExpiredJobPeriodMs() {
        return MEASUREMENT_DELETE_EXPIRED_JOB_PERIOD_MS;
    }

    /**
     * Job ID for Measurement Attribution Job ({@link
     * com.android.adservices.service.measurement.attribution.AttributionJobService}).
     */
    public static final int MEASUREMENT_ATTRIBUTION_JOB_ID = 5;

    /**
     * Job ID for Measurement Fallback Reporting Job ({@link
     * com.android.adservices.service.measurement.EventFallbackReportingJobService})
     */
    public static final int MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_ID = 6;

    public static long MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS =
            TimeUnit.HOURS.toMillis(24);

    /** Returns the min time period (in millis) between each event fallback reporting job run. */
    public static long getMeasurementEventFallbackReportingJobPeriodMs() {
        return FlagsFactory.getFlags().getMeasurementEventFallbackReportingJobPeriodMs();
    }

    /**
     * Returns the URL for fetching public encryption keys for aggregatable reports.
     */
    public static String getMeasurementAggregateEncryptionKeyCoordinatorUrl() {
        return FlagsFactory.getFlags().getMeasurementAggregateEncryptionKeyCoordinatorUrl();
    }

    /**
     * Job ID for Measurement Aggregate Main Reporting Job ({@link
     * com.android.adservices.service.measurement.AggregateReportingJobService})
     */
    public static final int MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID = 7;

    /** Returns the min time period (in millis) between each aggregate main reporting job run. */
    public static long getMeasurementAggregateMainReportingJobPeriodMs() {
        return FlagsFactory.getFlags().getMeasurementAggregateMainReportingJobPeriodMs();
    }

    /**
     * Job ID for Measurement Aggregate Fallback Reporting Job ({@link
     * com.android.adservices.service.measurement.AggregateFallbackReportingJobService})
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

    /**
     * Job ID for FLEDGE Background Fetch Job ({@link
     * com.android.adservices.service.customaudience.BackgroundFetchJobService})
     */
    public static final int FLEDGE_BACKGROUND_FETCH_JOB_ID = 9;

    /** Job ID for Consent Notification Job. */
    public static final int CONSENT_NOTIFICATION_JOB_ID = 10;
}
