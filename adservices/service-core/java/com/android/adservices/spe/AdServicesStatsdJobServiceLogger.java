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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__MODULE_NAME__MODULE_NAME_ADSERVICES;
import static com.android.adservices.service.stats.AdServicesStatsLog.BACKGROUND_JOB_SCHEDULING_REPORTED;

import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.StatsdAdServicesLogger;
import com.android.adservices.shared.spe.logging.ExecutionReportedStats;
import com.android.adservices.shared.spe.logging.SchedulingReportedStats;
import com.android.adservices.shared.spe.logging.StatsdJobServiceLogger;
import com.android.internal.annotations.VisibleForTesting;

/** The AdServices' implementation of {@link StatsdAdServicesLogger}. */
public final class AdServicesStatsdJobServiceLogger implements StatsdJobServiceLogger {
    // Shorten the length of a single line.
    @VisibleForTesting
    static final int MODULE_NAME_AD_SERVICES =
            AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__MODULE_NAME__MODULE_NAME_ADSERVICES;

    @Override
    public void logExecutionReportedStats(ExecutionReportedStats stats) {
        AdServicesStatsLog.write(
                AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED,
                stats.getJobId(),
                stats.getExecutionLatencyMs(),
                stats.getExecutionPeriodMinute(),
                stats.getExecutionResultCode(),
                stats.getStopReason(),
                MODULE_NAME_AD_SERVICES);
    }

    @Override
    public void logSchedulingReportedStats(SchedulingReportedStats stats) {
        AdServicesStatsLog.write(
                BACKGROUND_JOB_SCHEDULING_REPORTED,
                stats.getJobId(),
                stats.getResultCode(),
                stats.getSchedulerType(),
                MODULE_NAME_AD_SERVICES);
    }
}
