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
package com.android.adservices.cobalt;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_COBALT_LOGGER_EVENT_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_COBALT_LOGGER_EVENT_REPORTED__COBALT_LOGGING_EVENT__LOGGING_EVENT_OVER_EVENT_VECTOR_BUFFER_MAX;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_COBALT_LOGGER_EVENT_REPORTED__COBALT_LOGGING_EVENT__LOGGING_EVENT_OVER_MAX_VALUE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_COBALT_LOGGER_EVENT_REPORTED__COBALT_LOGGING_EVENT__LOGGING_EVENT_OVER_STRING_BUFFER_MAX;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_COBALT_PERIODIC_JOB_EVENT_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_COBALT_PERIODIC_JOB_EVENT_REPORTED__COBALT_PERIODIC_JOB_EVENT__UPLOAD_EVENT_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_COBALT_PERIODIC_JOB_EVENT_REPORTED__COBALT_PERIODIC_JOB_EVENT__UPLOAD_EVENT_SUCCESS;

import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.cobalt.logging.CobaltOperationLogger;

/** Implementation of Cobalt operational metrics logger. */
public final class CobaltOperationLoggerImpl implements CobaltOperationLogger {
    private final boolean mLoggerEnabled;

    public CobaltOperationLoggerImpl(boolean enabled) {
        mLoggerEnabled = enabled;
    }

    /**
     * Logs a Cobalt logging event exceeding string buffer max to Cobalt operational metric.
     *
     * @param metricId the Cobalt metric id of the event that is being logged
     * @param reportId the Cobalt report id of the event that is being logged
     */
    public void logStringBufferMaxExceeded(int metricId, int reportId) {
        if (!mLoggerEnabled) {
            return;
        }

        AdServicesStatsLog.write(
                AD_SERVICES_COBALT_LOGGER_EVENT_REPORTED,
                metricId,
                reportId,
                AD_SERVICES_COBALT_LOGGER_EVENT_REPORTED__COBALT_LOGGING_EVENT__LOGGING_EVENT_OVER_STRING_BUFFER_MAX);
    }

    /**
     * Logs a Cobalt logging event exceeding event vector buffer max to Cobalt operational metric.
     *
     * @param metricId the Cobalt metric id of the event that is being logged
     * @param reportId the Cobalt report id of the event that is being logged
     */
    public void logEventVectorBufferMaxExceeded(int metricId, int reportId) {
        if (!mLoggerEnabled) {
            return;
        }

        AdServicesStatsLog.write(
                AD_SERVICES_COBALT_LOGGER_EVENT_REPORTED,
                metricId,
                reportId,
                AD_SERVICES_COBALT_LOGGER_EVENT_REPORTED__COBALT_LOGGING_EVENT__LOGGING_EVENT_OVER_EVENT_VECTOR_BUFFER_MAX);
    }

    /**
     * Logs that a Cobalt logging event exceeds the max value to Cobalt operational metric.
     *
     * @param metricId the Cobalt metric id of the event that is being logged
     * @param reportId the Cobalt report id of the event that is being logged
     */
    public void logMaxValueExceeded(int metricId, int reportId) {
        if (!mLoggerEnabled) {
            return;
        }

        AdServicesStatsLog.write(
                AD_SERVICES_COBALT_LOGGER_EVENT_REPORTED,
                metricId,
                reportId,
                AD_SERVICES_COBALT_LOGGER_EVENT_REPORTED__COBALT_LOGGING_EVENT__LOGGING_EVENT_OVER_MAX_VALUE);
    }

    /** Logs that Cobalt periodic job failed to upload envelopes. */
    public void logUploadFailure() {
        if (!mLoggerEnabled) {
            return;
        }

        AdServicesStatsLog.write(
                AD_SERVICES_COBALT_PERIODIC_JOB_EVENT_REPORTED,
                AD_SERVICES_COBALT_PERIODIC_JOB_EVENT_REPORTED__COBALT_PERIODIC_JOB_EVENT__UPLOAD_EVENT_FAILURE);
    }

    /** Logs that Cobalt periodic job uploaded envelopes successfully. */
    public void logUploadSuccess() {
        if (!mLoggerEnabled) {
            return;
        }

        AdServicesStatsLog.write(
                AD_SERVICES_COBALT_PERIODIC_JOB_EVENT_REPORTED,
                AD_SERVICES_COBALT_PERIODIC_JOB_EVENT_REPORTED__COBALT_PERIODIC_JOB_EVENT__UPLOAD_EVENT_SUCCESS);
    }
}
