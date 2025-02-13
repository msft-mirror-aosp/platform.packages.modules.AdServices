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

package com.android.adservices.errorlogging;

import com.android.adservices.metriclogger.CelMetricLogger;
import com.android.adservices.shared.errorlogging.AdServicesErrorStats;
import com.android.adservices.shared.errorlogging.StatsdAdServicesErrorLogger;

import com.google.common.base.Supplier;

/**
 * Implementation of StatsdAdServicesErrorLogger that may perform sampling and then log error stats
 * to Statsd.
 *
 * <p>This class was duplicated from shared directory to isolate adservices' metric logger
 * dependency.
 */
final class SampledStatsdAdServicesErrorLoggerImpl implements StatsdAdServicesErrorLogger {
    private static final Supplier<StatsdAdServicesErrorLogger> INSTANCE =
            SampledStatsdAdServicesErrorLoggerImpl::new;

    private SampledStatsdAdServicesErrorLoggerImpl() {}

    /** Returns an instance of {@link StatsdAdServicesErrorLogger}. */
    public static StatsdAdServicesErrorLogger getInstance() {
        return INSTANCE.get();
    }

    @Override
    public void logAdServicesError(AdServicesErrorStats stats) {
        CelMetricLogger.get().log(stats);
    }
}
