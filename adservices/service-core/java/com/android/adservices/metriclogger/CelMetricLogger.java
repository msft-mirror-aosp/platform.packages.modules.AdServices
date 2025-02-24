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
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.errorlogging.AdServicesErrorStats;
import com.android.adservices.shared.metriclogger.AbstractMetricLogger;
import com.android.adservices.shared.metriclogger.MetricLoggerConfig;
import com.android.adservices.shared.metriclogger.SamplingMetadata;
import com.android.adservices.shared.proto.LogSamplingConfig;
import com.android.adservices.shared.proto.MetricId;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

/**
 * Logs metrics related to the errors/exceptions within the Ad Services module. This class handles
 * sampling and logging of client error logging data. class handles sampling and logging of client
 * error logging data.
 */
public final class CelMetricLogger extends AbstractMetricLogger<AdServicesErrorStats> {

    private static final Supplier<CelMetricLogger> LOGGER_SUPPLIER =
            Suppliers.memoize(() -> new CelMetricLogger(FlagsFactory.getFlags()));

    // A no-op logger to prevent circular dependencies during proto parsing of config.
    private static final AdServicesErrorLogger NO_OP_ERROR_LOGGER = new NoOpAdServicesErrorLogger();

    private CelMetricLogger(Flags flags) {
        this(
                buildConfig(
                        getSamplingConfigOrDefault(
                                flags::getAdServicesCelSamplingConfig, NO_OP_ERROR_LOGGER)));
    }

    @VisibleForTesting
    CelMetricLogger(MetricLoggerConfig<AdServicesErrorStats> config) {
        super(config);
    }

    /** Returns an instance of {@link CelMetricLogger}. */
    public static CelMetricLogger get() {
        return LOGGER_SUPPLIER.get();
    }

    @VisibleForTesting
    static MetricLoggerConfig<AdServicesErrorStats> buildConfig(LogSamplingConfig configProto) {
        return MetricLoggerConfig.builder(
                        MetricId.CLIENT_ERROR_LOGGING_STATS,
                        configProto,
                        AdServicesExecutors.getLightWeightExecutor(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        ApplicationContextSingleton.get(),
                        CelMetricLogger::logStats)
                .build();
    }

    @VisibleForTesting
    static void logStats(AdServicesErrorStats stats, SamplingMetadata metadata) {
        AdServicesStatsLog.write(
                AD_SERVICES_ERROR_REPORTED,
                stats.getErrorCode(),
                stats.getPpapiName(),
                stats.getClassName(),
                stats.getMethodName(),
                stats.getLineNumber(),
                stats.getLastObservedExceptionName());
    }

    private static class NoOpAdServicesErrorLogger implements AdServicesErrorLogger {
        @Override
        public void logError(int errorCode, int ppapiName) {}

        @Override
        public void logErrorWithExceptionInfo(Throwable tr, int errorCode, int ppapiName) {}

        @Override
        public void logError(Throwable tr, int errorCode, int ppapiName) {}
    }
}
