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

package com.android.adservices.shared.metriclogger;

import com.android.adservices.shared.SharedMockitoTestCase;
import com.android.adservices.shared.metriclogger.logsampler.PerEventSamplingConfig;
import com.android.adservices.shared.metriclogger.logsampler.deviceselection.PerDeviceSamplingConfig;
import com.android.adservices.shared.proto.LogSamplingConfig;
import com.android.adservices.shared.proto.MetricId;

import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.Executor;

public final class MetricLoggerConfigTest extends SharedMockitoTestCase {

    @Mock private LogUploader<ExampleStats> mMockLogUploader;
    @Mock private Executor mMockExecutor;

    private static final double SAMPLE_RATE = 0.5;
    private static final LogSamplingConfig EXAMPLE_SAMPLING_CONFIG =
            LogSamplingConfig.newBuilder()
                    .setPerEventSampling(
                            LogSamplingConfig.PerEventSampling.newBuilder()
                                    .setSamplingRate(SAMPLE_RATE)
                                    .build())
                    .build();
    private static final LogSamplingConfig.PerDeviceSampling EXAMPLE_PER_DEVICE_SAMPLING_PROTO =
            LogSamplingConfig.PerDeviceSampling.newBuilder()
                    .setRotationPeriodDays(50)
                    .setStaggeringPeriodDays(2)
                    .setSamplingRate(0.8)
                    .build();
    private static final LogSamplingConfig EXAMPLE_SAMPLING_CONFIG_WITH_PER_DEVICE_SAMPLING =
            LogSamplingConfig.newBuilder()
                    .setPerEventSampling(
                            LogSamplingConfig.PerEventSampling.newBuilder()
                                    .setSamplingRate(SAMPLE_RATE)
                                    .build())
                    .setPerDeviceSampling(EXAMPLE_PER_DEVICE_SAMPLING_PROTO)
                    .build();

    @Test
    public void testBuilder() {
        MetricLoggerConfig<ExampleStats> actual =
                MetricLoggerConfig.builder(
                                MetricId.EXAMPLE_STATS,
                                EXAMPLE_SAMPLING_CONFIG,
                                mMockExecutor,
                                mMockExecutor,
                                mMockContext,
                                mMockLogUploader)
                        .build();

        expect.withMessage("metricId").that(actual.getMetricId()).isEqualTo(MetricId.EXAMPLE_STATS);
        expect.withMessage("perEventSamplingConfig")
                .that(actual.getPerEventSamplingConfig())
                .isEqualTo(
                        PerEventSamplingConfig.createPerEventSamplingConfig(
                                EXAMPLE_SAMPLING_CONFIG.getPerEventSampling()));
        expect.withMessage("logUploader").that(actual.getLogUploader()).isEqualTo(mMockLogUploader);
    }

    @Test
    public void testBuilder_withPerDeviceSampling() {
        MetricLoggerConfig<ExampleStats> actual =
                MetricLoggerConfig.builder(
                                MetricId.EXAMPLE_STATS,
                                EXAMPLE_SAMPLING_CONFIG_WITH_PER_DEVICE_SAMPLING,
                                mMockExecutor,
                                mMockExecutor,
                                mMockContext,
                                mMockLogUploader)
                        .build();

        expect.withMessage("metricId").that(actual.getMetricId()).isEqualTo(MetricId.EXAMPLE_STATS);
        expect.withMessage("perEventSamplingConfig")
                .that(actual.getPerEventSamplingConfig())
                .isEqualTo(
                        PerEventSamplingConfig.createPerEventSamplingConfig(
                                EXAMPLE_SAMPLING_CONFIG_WITH_PER_DEVICE_SAMPLING
                                        .getPerEventSampling()));
        expect.withMessage("perDeviceSamplingConfig")
                .that(actual.getPerDeviceSamplingConfig())
                .isEqualTo(
                        PerDeviceSamplingConfig.createPerDeviceSamplingConfig(
                                EXAMPLE_SAMPLING_CONFIG_WITH_PER_DEVICE_SAMPLING
                                        .getPerDeviceSampling()));
        expect.withMessage("logUploader").that(actual.getLogUploader()).isEqualTo(mMockLogUploader);
    }

    @Test
    public void testBuilder_defaultLogSamplingConfig() {
        MetricLoggerConfig<ExampleStats> actual =
                MetricLoggerConfig.builder(
                                MetricId.EXAMPLE_STATS,
                                LogSamplingConfig.getDefaultInstance(),
                                mMockExecutor,
                                mMockExecutor,
                                mMockContext,
                                mMockLogUploader)
                        .build();

        expect.withMessage("metricId").that(actual.getMetricId()).isEqualTo(MetricId.EXAMPLE_STATS);
        expect.withMessage("perEventSamplingConfig")
                .that(actual.getPerEventSamplingConfig())
                .isNull();
        expect.withMessage("perDeviceSamplingConfig")
                .that(actual.getPerDeviceSamplingConfig())
                .isNull();
        expect.withMessage("logUploader").that(actual.getLogUploader()).isEqualTo(mMockLogUploader);
    }

    @Test
    public void testCreatePerEventSampling() {
        MetricLoggerConfig<ExampleStats> config =
                MetricLoggerConfig.builder(
                                MetricId.EXAMPLE_STATS,
                                EXAMPLE_SAMPLING_CONFIG,
                                mMockExecutor,
                                mMockExecutor,
                                mMockContext,
                                mMockLogUploader)
                        .build();

        expect.withMessage("perEventSampling").that(config.createPerEventSampling()).isNotNull();
    }

    @Test
    public void testCreatePerDeviceSampling() {
        LogSamplingConfig perDeviceSamplingConfig =
                LogSamplingConfig.newBuilder()
                        .setPerDeviceSampling(EXAMPLE_PER_DEVICE_SAMPLING_PROTO)
                        .build();
        MetricLoggerConfig<ExampleStats> config =
                MetricLoggerConfig.builder(
                                MetricId.EXAMPLE_STATS,
                                perDeviceSamplingConfig,
                                mMockExecutor,
                                mMockExecutor,
                                mMockContext,
                                mMockLogUploader)
                        .build();

        expect.withMessage("perDeviceSampling").that(config.createPerDeviceSampling()).isNotNull();
    }

    private static final class ExampleStats {
        private ExampleStats() {}
    }
}
