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
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__MODULE_NAME__UNKNOWN_MODULE_NAME;
import static com.android.adservices.service.stats.AdServicesStatsLog.BACKGROUND_JOB_SCHEDULING_REPORTED;
import static com.android.adservices.spe.AdServicesStatsdJobServiceLogger.MODULE_NAME_AD_SERVICES;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.mockito.ArgumentMatchers.anyInt;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.shared.spe.logging.ExecutionReportedStats;
import com.android.adservices.shared.spe.logging.SchedulingReportedStats;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;

/** Unit tests for {@link AdServicesStatsdJobServiceLogger}. */
@SpyStatic(AdServicesStatsLog.class)
public final class AdServicesStatsdJobServiceLoggerTest extends AdServicesExtendedMockitoTestCase {
    private final AdServicesStatsdJobServiceLogger mLogger = new AdServicesStatsdJobServiceLogger();

    @Test
    public void testLogExecutionReportedStats() {
        int jobId = 1;
        int executionLatencyMs = 2;
        int executionPeriodMinute = 3;
        int executionResultCode = 4;
        int stopReason = 5;
        int moduleName =
                AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__MODULE_NAME__UNKNOWN_MODULE_NAME;

        // Mock to let AdServicesStatsLog do NOT actually upload logs.
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                                        anyInt()));
        ExecutionReportedStats stats =
                ExecutionReportedStats.builder()
                        .setJobId(jobId)
                        .setExecutionLatencyMs(executionLatencyMs)
                        .setExecutionPeriodMinute(executionPeriodMinute)
                        .setExecutionResultCode(executionResultCode)
                        .setStopReason(stopReason)
                        .setModuleName(moduleName)
                        .build();

        mLogger.logExecutionReportedStats(stats);

        verify(
                () ->
                        AdServicesStatsLog.write(
                                AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED,
                                jobId,
                                executionLatencyMs,
                                executionPeriodMinute,
                                executionResultCode,
                                stopReason,
                                MODULE_NAME_AD_SERVICES));
    }

    @Test
    public void testLogSchedulingReportedStats() {
        int jobId = 1;
        int resultCode = 2;
        int schedulerType = 3;
        int moduleName =
                AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__MODULE_NAME__UNKNOWN_MODULE_NAME;

        // Mock to let AdServicesStatsLog do NOT actually upload logs.
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(), anyInt(), anyInt(), anyInt(), anyInt()));
        SchedulingReportedStats stats =
                SchedulingReportedStats.builder()
                        .setJobId(jobId)
                        .setResultCode(resultCode)
                        .setSchedulerType(schedulerType)
                        .setModuleName(moduleName)
                        .build();

        mLogger.logSchedulingReportedStats(stats);

        verify(
                () ->
                        AdServicesStatsLog.write(
                                BACKGROUND_JOB_SCHEDULING_REPORTED,
                                jobId,
                                resultCode,
                                schedulerType,
                                MODULE_NAME_AD_SERVICES));
    }
}
