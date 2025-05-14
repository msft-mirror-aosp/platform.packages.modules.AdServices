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

import static com.android.adservices.metriclogger.BackgroundJobsExecutionMetricLogger.MODULE_NAME_AD_SERVICES;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__MODULE_NAME__UNKNOWN_MODULE_NAME;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.mockito.ArgumentMatchers.anyInt;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.shared.metriclogger.MetricLoggerConfig;
import com.android.adservices.shared.metriclogger.SamplingMetadata;
import com.android.adservices.shared.metriclogger.logsampler.PerEventSamplingConfig;
import com.android.adservices.shared.proto.LogSamplingConfig;
import com.android.adservices.shared.proto.MetricId;
import com.android.adservices.shared.spe.logging.ExecutionReportedStats;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.Test;

@SpyStatic(FlagsFactory.class)
@SpyStatic(AdServicesStatsLog.class)
public final class BackgroundJobsExecutionMetricLoggerTest
        extends AdServicesExtendedMockitoTestCase {
    @Before
    public void setup() {
        mocker.mockGetFlags(mFakeFlags);
    }

    @Test
    public void testGetInstance() {
        BackgroundJobsExecutionMetricLogger instance1 = BackgroundJobsExecutionMetricLogger.get();
        BackgroundJobsExecutionMetricLogger instance2 = BackgroundJobsExecutionMetricLogger.get();

        expect.withMessage("BackgroundJobsExecutionLogger.get()")
                .that(instance1)
                .isSameInstanceAs(instance2);
    }

    @Test
    public void testLogStats() {
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

        SamplingMetadata metadata =
                new SamplingMetadata(/* perDeviceSampleRate= */ 0.5, /* perEventSampleRate= */ 0.5);

        BackgroundJobsExecutionMetricLogger.logStats(stats, metadata);

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
    public void testBuildConfig() {
        LogSamplingConfig configProto =
                LogSamplingConfig.newBuilder()
                        .setPerEventSampling(
                                LogSamplingConfig.PerEventSampling.newBuilder()
                                        .setSamplingRate(0.5)
                                        .build())
                        .build();
        MetricLoggerConfig<ExecutionReportedStats> actual =
                BackgroundJobsExecutionMetricLogger.buildConfig(configProto);

        expect.withMessage("metricId")
                .that(actual.getMetricId())
                .isEqualTo(MetricId.BACKGROUND_JOBS_EXECUTION_REPORTED_STATS);
        expect.withMessage("perEventSamplingConfig")
                .that(actual.getPerEventSamplingConfig())
                .isEqualTo(
                        PerEventSamplingConfig.createPerEventSamplingConfig(
                                configProto.getPerEventSampling(),
                                ImmutableMap.of(),
                                /* supportDimensionInLogSamplingEnabled= */ false));
    }
}
