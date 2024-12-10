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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.platform.test.annotations.DisabledOnRavenwood;

import com.android.adservices.shared.SharedMockitoTestCase;
import com.android.adservices.shared.proto.LogSamplingConfig;
import com.android.adservices.shared.proto.MetricId;

import com.google.common.base.Supplier;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;

public final class AbstractMetricLoggerTest extends SharedMockitoTestCase {

    private static final LogSamplingConfig EXAMPLE_CONFIG_RATE_100_PERCENT =
            LogSamplingConfig.newBuilder()
                    .setPerEventSampling(
                            LogSamplingConfig.PerEventSampling.newBuilder()
                                    .setSamplingRate(1)
                                    .build())
                    .build();
    private static final LogSamplingConfig EXAMPLE_CONFIG_RATE_0_PERCENT =
            LogSamplingConfig.newBuilder()
                    .setPerEventSampling(
                            LogSamplingConfig.PerEventSampling.newBuilder()
                                    .setSamplingRate(0)
                                    .build())
                    .build();

    private static final ExampleStats EVENT = new ExampleStats(/* exampleValue= */ 10);

    private final ArgumentCaptor<SamplingMetadata> mMetadataArgumentCaptor =
            ArgumentCaptor.forClass(SamplingMetadata.class);

    @Mock private LogUploader<ExampleStats> mMockLogUploader;

    @Test
    public void testLog() {
        MetricLoggerConfig<ExampleStats> config =
                MetricLoggerConfig.builder(
                                MetricId.EXAMPLE_STATS,
                                EXAMPLE_CONFIG_RATE_100_PERCENT,
                                mMockLogUploader)
                        .build();
        AbstractMetricLogger<ExampleStats> logger = new ExampleStatsLogger(config);

        logger.log(EVENT);

        verify(mMockLogUploader).accept(eq(EVENT), mMetadataArgumentCaptor.capture());
        expect.withMessage("sampleRate")
                .that(mMetadataArgumentCaptor.getValue().getPerEventSamplingRate())
                .isEqualTo(1);
    }

    @Test
    public void testLog_usingLogSupplier() {
        MetricLoggerConfig<ExampleStats> config =
                MetricLoggerConfig.builder(
                                MetricId.EXAMPLE_STATS,
                                EXAMPLE_CONFIG_RATE_100_PERCENT,
                                mMockLogUploader)
                        .build();
        AbstractMetricLogger<ExampleStats> logger = new ExampleStatsLogger(config);

        logger.log(() -> EVENT);

        verify(mMockLogUploader).accept(eq(EVENT), mMetadataArgumentCaptor.capture());
        expect.withMessage("sampleRate")
                .that(mMetadataArgumentCaptor.getValue().getPerEventSamplingRate())
                .isEqualTo(1);
    }

    @Test
    public void testLog_withDefaultLogSamplingProto_alwaysLogs() {
        // Per-event sampling will be unset with default proto, always log i.e. no sampling.
        MetricLoggerConfig<ExampleStats> config =
                MetricLoggerConfig.builder(
                                MetricId.EXAMPLE_STATS,
                                LogSamplingConfig.getDefaultInstance(),
                                mMockLogUploader)
                        .build();
        AbstractMetricLogger<ExampleStats> logger = new ExampleStatsLogger(config);

        logger.log(EVENT);

        verify(mMockLogUploader).accept(eq(EVENT), mMetadataArgumentCaptor.capture());
        expect.withMessage("sampleRate")
                .that(mMetadataArgumentCaptor.getValue().getPerEventSamplingRate())
                .isEqualTo(1);
    }

    @Test
    public void testLog_zeroPerEventSamplingRate_noLog() {
        // Per event sampling rate set to 0%, performs no event logging.
        MetricLoggerConfig<ExampleStats> config =
                MetricLoggerConfig.builder(
                                MetricId.EXAMPLE_STATS,
                                EXAMPLE_CONFIG_RATE_0_PERCENT,
                                mMockLogUploader)
                        .build();
        AbstractMetricLogger<ExampleStats> logger = new ExampleStatsLogger(config);

        logger.log(EVENT);

        verify(mMockLogUploader, times(0)).accept(any(), any());
    }

    // TODO(b/335935200): fix this
    @DisabledOnRavenwood(reason = "Uses Mockito.spy")
    @Test
    public void testLog_withSupplierZeroPerEventSamplingRate_noLog() {
        // Per event sampling rate set to 0%, performs no event logging.
        MetricLoggerConfig<ExampleStats> config =
                MetricLoggerConfig.builder(
                                MetricId.EXAMPLE_STATS,
                                EXAMPLE_CONFIG_RATE_0_PERCENT,
                                mMockLogUploader)
                        .build();
        AbstractMetricLogger<ExampleStats> logger = new ExampleStatsLogger(config);
        Supplier<ExampleStats> eventSupplierSpy = Mockito.spy(() -> EVENT);

        logger.log(eventSupplierSpy);

        verify(mMockLogUploader, times(0)).accept(any(), any());
        verify(eventSupplierSpy, times(0)).get();
    }

    private static class ExampleStats {
        private final int mExampleValue;

        private ExampleStats(int exampleValue) {
            mExampleValue = exampleValue;
        }
    }

    private static class ExampleStatsLogger extends AbstractMetricLogger<ExampleStats> {

        private ExampleStatsLogger(MetricLoggerConfig<ExampleStats> config) {
            super(config);
        }
    }
}
