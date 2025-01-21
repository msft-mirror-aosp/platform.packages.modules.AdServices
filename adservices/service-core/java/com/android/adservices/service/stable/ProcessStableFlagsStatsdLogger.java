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

package com.android.adservices.service.stable;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_FLAG_UPDATE_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_PROCESS_LIFECYCLE_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_PROCESS_LIFECYCLE_REPORTED__EVENT_TYPE__LOW_MEMORY_LEVEL;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_PROCESS_LIFECYCLE_REPORTED__EVENT_TYPE__RESTART;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_PROCESS_STABLE_FLAGS_REPORTED;

import com.android.adservices.service.stats.AdServicesStatsLog;

/** The logger logs process stable flags metrics to {@code Statsd}. */
public final class ProcessStableFlagsStatsdLogger {
    void logAdServicesProcessRestart() {
        AdServicesStatsLog.write(
                AD_SERVICES_PROCESS_LIFECYCLE_REPORTED,
                AD_SERVICES_PROCESS_LIFECYCLE_REPORTED__EVENT_TYPE__RESTART);
    }

    void logBatchReadFromDeviceConfigLatencyMs(long latencyMs) {
        AdServicesStatsLog.write(AD_SERVICES_PROCESS_STABLE_FLAGS_REPORTED, latencyMs);
    }

    void logAdServicesProcessLowMemoryLevel() {
        AdServicesStatsLog.write(
                AD_SERVICES_PROCESS_LIFECYCLE_REPORTED,
                AD_SERVICES_PROCESS_LIFECYCLE_REPORTED__EVENT_TYPE__LOW_MEMORY_LEVEL);
    }

    void logAdServicesFlagsUpdateEvent(int numOfCacheMissFlags) {
        AdServicesStatsLog.write(AD_SERVICES_FLAG_UPDATE_REPORTED, numOfCacheMissFlags);
    }
}
