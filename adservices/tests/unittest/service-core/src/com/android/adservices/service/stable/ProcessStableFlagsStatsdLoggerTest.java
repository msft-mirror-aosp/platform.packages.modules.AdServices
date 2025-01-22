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
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;

/** Unit tests for {@link ProcessStableFlagsStatsdLogger}. */
@SpyStatic(AdServicesStatsLog.class)
public final class ProcessStableFlagsStatsdLoggerTest extends AdServicesExtendedMockitoTestCase {
    private final ProcessStableFlagsStatsdLogger mStatsdLogger =
            new ProcessStableFlagsStatsdLogger();

    @Test
    public void testLogAdServicesProcessRestart() {
        mStatsdLogger.logAdServicesProcessRestart();

        verify(
                () ->
                        AdServicesStatsLog.write(
                                AD_SERVICES_PROCESS_LIFECYCLE_REPORTED,
                                AD_SERVICES_PROCESS_LIFECYCLE_REPORTED__EVENT_TYPE__RESTART));
    }

    @Test
    public void testLogBatchReadFromDeviceConfigLatencyMs() {
        long latencyMs = 1L;

        mStatsdLogger.logBatchReadFromDeviceConfigLatencyMs(latencyMs);

        verify(
                () ->
                        AdServicesStatsLog.write(
                                AD_SERVICES_PROCESS_STABLE_FLAGS_REPORTED, latencyMs));
    }

    @Test
    public void testLogAdServicesProcessLowMemoryLevel() {
        mStatsdLogger.logAdServicesProcessLowMemoryLevel();

        verify(
                () ->
                        AdServicesStatsLog.write(
                                AD_SERVICES_PROCESS_LIFECYCLE_REPORTED,
                                AD_SERVICES_PROCESS_LIFECYCLE_REPORTED__EVENT_TYPE__LOW_MEMORY_LEVEL));
    }

    @Test
    public void testLogAdServicesFlagsUpdateEvent() {
        int numOfCacheMissFlags = 1;

        mStatsdLogger.logAdServicesFlagsUpdateEvent(numOfCacheMissFlags);

        verify(
                () ->
                        AdServicesStatsLog.write(
                                AD_SERVICES_FLAG_UPDATE_REPORTED, numOfCacheMissFlags));
    }
}
