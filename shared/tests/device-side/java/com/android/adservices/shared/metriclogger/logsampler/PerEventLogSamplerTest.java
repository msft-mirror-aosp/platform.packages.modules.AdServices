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

package com.android.adservices.shared.metriclogger.logsampler;

import com.android.adservices.shared.SharedUnitTestCase;
import com.android.adservices.shared.proto.LogSamplingConfig.PerEventSampling;
import com.android.adservices.shared.proto.MetricId;

import org.junit.Test;

public final class PerEventLogSamplerTest extends SharedUnitTestCase {

    @Test
    public void testShouldLog_samplingRateIsHalf_probabilityIsCorrect() {
        // Arrange sets the sampling rate and instantiate the per event log sampler.
        PerEventSampling configHalfSamplingRateProto =
                PerEventSampling.newBuilder().setSamplingRate(0.5).build();

        PerEventLogSampler<ExampleStats> perEventSampling =
                new PerEventLogSampler<>(
                        MetricId.EXAMPLE_STATS,
                        PerEventSamplingConfig.createPerEventSamplingConfig(
                                configHalfSamplingRateProto));

        int logSuccess = 0;

        // Act Simulate 1000 events and count how many times shouldLog() returns true.
        for (int i = 0; i < 1000; i++) {
            if (perEventSampling.shouldLog()) {
                logSuccess++;
            }
        }

        // Assert the number of logged events is within the expected range for a 50% sampling rate
        // with some variance.
        expect.that(logSuccess).isAtLeast(400);
        expect.that(logSuccess).isAtMost(600);
    }

    @Test
    public void testShouldLog_nullConfig() {
        PerEventLogSampler<ExampleStats> perEventSamplingImpl =
                new PerEventLogSampler<>(MetricId.EXAMPLE_STATS, null);

        expect.that(perEventSamplingImpl.shouldLog()).isTrue();
    }

    @Test
    public void testShouldLog_samplingRateOne_alwaysLog() {
        PerEventSampling SamplingRateOneProto =
                PerEventSampling.newBuilder().setSamplingRate(1).build();

        PerEventLogSampler<ExampleStats> perEventSampling =
                new PerEventLogSampler<>(
                        MetricId.EXAMPLE_STATS,
                        PerEventSamplingConfig.createPerEventSamplingConfig(SamplingRateOneProto));

        expect.that(perEventSampling.shouldLog()).isTrue();
    }

    @Test
    public void testShouldLog_samplingRateZero_neverLog() {
        PerEventSampling SamplingRateZeroProto =
                PerEventSampling.newBuilder().setSamplingRate(0).build();

        PerEventLogSampler<ExampleStats> perEventSampling =
                new PerEventLogSampler<>(
                        MetricId.EXAMPLE_STATS,
                        PerEventSamplingConfig.createPerEventSamplingConfig(SamplingRateZeroProto));

        expect.that(perEventSampling.shouldLog()).isFalse();
    }

    private static final class ExampleStats {
        private ExampleStats() {}
    }
}
