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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.shared.errorlogging.AdServicesErrorStats;
import com.android.adservices.shared.metriclogger.MetricLoggerConfig;
import com.android.adservices.shared.metriclogger.SamplingMetadata;
import com.android.adservices.shared.metriclogger.logsampler.PerEventSamplingConfig;
import com.android.adservices.shared.proto.LogSamplingConfig;
import com.android.adservices.shared.proto.MetricId;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;

@SpyStatic(FlagsFactory.class)
@SpyStatic(AdServicesStatsLog.class)
public final class CelMetricLoggerTest extends AdServicesExtendedMockitoTestCase {
    @Before
    public void setup() {
        mocker.mockGetFlags(mFakeFlags);
    }

    @Test
    public void testGetInstance() {
        CelMetricLogger instance1 = CelMetricLogger.get();
        CelMetricLogger instance2 = CelMetricLogger.get();

        expect.withMessage("CelMetricLogger.get()").that(instance1).isSameInstanceAs(instance2);
    }

    @Test
    public void testLogStats() {
        String className = "TopicsService";
        String methodName = "getTopics";
        int lineNumber = 100;
        String exceptionName = "SQLiteException";
        AdServicesErrorStats stats =
                AdServicesErrorStats.builder()
                        .setErrorCode(
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION)
                        .setPpapiName(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS)
                        .setClassName(className)
                        .setMethodName(methodName)
                        .setLineNumber(lineNumber)
                        .setLastObservedExceptionName(exceptionName)
                        .build();
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyInt(),
                                        anyInt(),
                                        anyString(),
                                        anyString(),
                                        anyInt(),
                                        anyString()));
        SamplingMetadata metadata =
                new SamplingMetadata(/* perDeviceSampleRate= */ 0.5, /* perEventSampleRate= */ 0.5);

        CelMetricLogger.logStats(stats, metadata);

        verify(
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_ERROR_REPORTED),
                                eq(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION),
                                eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS),
                                eq(className),
                                eq(methodName),
                                eq(lineNumber),
                                eq(exceptionName)));
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
        MetricLoggerConfig<AdServicesErrorStats> actual = CelMetricLogger.buildConfig(configProto);

        expect.withMessage("metricId")
                .that(actual.getMetricId())
                .isEqualTo(MetricId.CLIENT_ERROR_LOGGING_STATS);
        expect.withMessage("perEventSamplingConfig")
                .that(actual.getPerEventSamplingConfig())
                .isEqualTo(
                        PerEventSamplingConfig.createPerEventSamplingConfig(
                                configProto.getPerEventSampling()));
    }
}
