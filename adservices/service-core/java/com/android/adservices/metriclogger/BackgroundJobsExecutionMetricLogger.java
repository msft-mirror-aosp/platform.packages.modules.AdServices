/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.adservices.metriclogger;

import static com.android.adservices.metriclogger.SamplingConfigFlagReader.getSamplingConfigOrDefault;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__MODULE_NAME__MODULE_NAME_ADSERVICES;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.errorlogging.AdServicesErrorLoggerImpl;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.metriclogger.AbstractMetricLogger;
import com.android.adservices.shared.metriclogger.MetricLoggerConfig;
import com.android.adservices.shared.metriclogger.SamplingMetadata;
import com.android.adservices.shared.proto.LogSamplingConfig;
import com.android.adservices.shared.proto.MetricId;
import com.android.adservices.shared.spe.logging.ExecutionReportedStats;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

/**
 * Logs metrics related to the execution of background jobs within the Ad Services module. This
 * class handles sampling and logging of job execution data.
 */
public final class BackgroundJobsExecutionMetricLogger
        extends AbstractMetricLogger<ExecutionReportedStats> {
    @VisibleForTesting
    static final int MODULE_NAME_AD_SERVICES =
            AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__MODULE_NAME__MODULE_NAME_ADSERVICES;

    private static final Supplier<BackgroundJobsExecutionMetricLogger> LOGGER_SUPPLIER =
            Suppliers.memoize(
                    () -> new BackgroundJobsExecutionMetricLogger(FlagsFactory.getFlags()));

    private BackgroundJobsExecutionMetricLogger(Flags flags) {
        this(
                buildConfig(
                        getSamplingConfigOrDefault(
                                flags::getAdServicesJobExecutionSamplingConfig,
                                AdServicesErrorLoggerImpl.getInstance())));
    }

    @VisibleForTesting
    BackgroundJobsExecutionMetricLogger(MetricLoggerConfig<ExecutionReportedStats> config) {
        super(config);
    }

    /** Returns an instance of {@link BackgroundJobsExecutionMetricLogger}. */
    public static BackgroundJobsExecutionMetricLogger get() {
        return LOGGER_SUPPLIER.get();
    }

    @VisibleForTesting
    static MetricLoggerConfig<ExecutionReportedStats> buildConfig(LogSamplingConfig configProto) {
        return MetricLoggerConfig.builder(
                        MetricId.BACKGROUND_JOBS_EXECUTION_REPORTED_STATS,
                        configProto,
                        AdServicesExecutors.getLightWeightExecutor(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        ApplicationContextSingleton.get(),
                        BackgroundJobsExecutionMetricLogger::logStats)
                .build();
    }

    @VisibleForTesting
    static void logStats(ExecutionReportedStats stats, SamplingMetadata metadata) {
        AdServicesStatsLog.write(
                AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED,
                stats.getJobId(),
                stats.getExecutionLatencyMs(),
                stats.getExecutionPeriodMinute(),
                stats.getExecutionResultCode(),
                stats.getStopReason(),
                MODULE_NAME_AD_SERVICES);
    }
}
