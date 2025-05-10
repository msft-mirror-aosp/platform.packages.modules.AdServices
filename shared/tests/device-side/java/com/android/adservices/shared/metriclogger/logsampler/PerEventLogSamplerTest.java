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

import static com.android.adservices.shared.metriclogger.logsampler.SamplerResult.ALWAYS_LOG_SAMPLING_RESULT;
import static com.android.adservices.shared.metriclogger.logsampler.SamplerResult.NEVER_LOG_SAMPLING_RESULT;

import com.android.adservices.shared.SharedUnitTestCase;
import com.android.adservices.shared.proto.Dimension;
import com.android.adservices.shared.proto.DimensionMatcher;
import com.android.adservices.shared.proto.DimensionName;
import com.android.adservices.shared.proto.LogSamplingConfig.PerEventSampling;
import com.android.adservices.shared.proto.MetricId;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.Test;

import java.util.List;
import java.util.function.Function;

public final class PerEventLogSamplerTest extends SharedUnitTestCase {
    private static final Supplier<ExampleEvent> EXAMPLE_STATS =
            () -> new ExampleEvent(/* id= */ 1, /* errorCode= */ 101);

    private static final ImmutableList<DimensionMatcher> SINGLE_DIMENSION_MATCHERS =
            ImmutableList.of(
                    DimensionMatcher.newBuilder()
                            .setSamplingRate(0.7)
                            .addDimension(
                                    Dimension.newBuilder()
                                            .setName(DimensionName.CEL_ERROR_CODE)
                                            .addAllValue(List.of(101, 201, 301))
                                            .build())
                            .build());

    private static final ImmutableMap<DimensionName, Function<ExampleEvent, Integer>>
            VALUE_EXTRACTOR_FUNCTION_MAP =
                    ImmutableMap.of(DimensionName.CEL_ERROR_CODE, ExampleEvent::getErrorCode);

    @Test
    public void testShouldLog_samplingRateIsHalf_probabilityIsCorrect() {
        // Arrange sets the sampling rate and instantiate the per event log sampler.
        PerEventSampling configHalfSamplingRateProto =
                PerEventSampling.newBuilder().setSamplingRate(0.5).build();

        PerEventLogSampler<ExampleEvent> perEventSampling =
                new PerEventLogSampler<>(
                        MetricId.EXAMPLE_STATS,
                        PerEventSamplingConfig.createPerEventSamplingConfig(
                                configHalfSamplingRateProto,
                                ImmutableMap.of(),
                                /* supportDimensionInLogSamplingEnabled= */ false));

        int logSuccess = 0;

        // Act Simulate 1000 events and count how many times shouldLog() returns true.
        for (int i = 0; i < 1000; i++) {
            if (perEventSampling.shouldLog(EXAMPLE_STATS).getShouldLogEvent()) {
                logSuccess++;
            }
        }

        // Assert the number of logged events is within the expected range for a 50% sampling rate
        // with some variance.
        expect.that(logSuccess).isAtLeast(400);
        expect.that(logSuccess).isAtMost(600);
    }

    @Test
    public void
            testShouldLog_matchesDimensionSampleRate_probabilityIsCorrect_dimensionFlagEnabled() {
        // Arrange sets the sampling rate and instantiate the per event log sampler.
        PerEventSampling configHalfSamplingRateProto =
                PerEventSampling.newBuilder()
                        .setSamplingRate(0.5)
                        .addAllDimensionMatcher(SINGLE_DIMENSION_MATCHERS)
                        .build();

        PerEventLogSampler<ExampleEvent> perEventSampling =
                new PerEventLogSampler<>(
                        MetricId.EXAMPLE_STATS,
                        PerEventSamplingConfig.createPerEventSamplingConfig(
                                configHalfSamplingRateProto,
                                VALUE_EXTRACTOR_FUNCTION_MAP,
                                /* supportDimensionInLogSamplingEnabled= */ true));

        int logSuccess = 0;

        // Act Simulate 1000 events and count how many times shouldLog() returns true.
        for (int i = 0; i < 1000; i++) {
            if (perEventSampling.shouldLog(EXAMPLE_STATS).getShouldLogEvent()) {
                logSuccess++;
            }
        }

        // Assert the number of logged events is within the expected range for a 70% sampling rate
        // with some variance.
        expect.that(logSuccess).isAtLeast(600);
        expect.that(logSuccess).isAtMost(800);
    }

    @Test
    public void testShouldLog_supportDimensionFlagDisabled_usesDefaultSampleRate() {
        // Arrange sets the sampling rate and instantiate the per event log sampler.
        PerEventSampling configHalfSamplingRateProto =
                PerEventSampling.newBuilder()
                        .setSamplingRate(0.5)
                        .addAllDimensionMatcher(SINGLE_DIMENSION_MATCHERS)
                        .build();

        PerEventLogSampler<ExampleEvent> perEventSampling =
                new PerEventLogSampler<>(
                        MetricId.EXAMPLE_STATS,
                        PerEventSamplingConfig.createPerEventSamplingConfig(
                                configHalfSamplingRateProto,
                                VALUE_EXTRACTOR_FUNCTION_MAP,
                                /* supportDimensionInLogSamplingEnabled= */ false));

        int logSuccess = 0;

        // Act Simulate 1000 events and count how many times shouldLog() returns true.
        for (int i = 0; i < 1000; i++) {
            if (perEventSampling.shouldLog(EXAMPLE_STATS).getShouldLogEvent()) {
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
        PerEventLogSampler<ExampleEvent> perEventSamplingImpl =
                new PerEventLogSampler<>(MetricId.EXAMPLE_STATS, null);

        expect.that(perEventSamplingImpl.shouldLog(EXAMPLE_STATS))
                .isEqualTo(ALWAYS_LOG_SAMPLING_RESULT);
    }

    @Test
    public void testShouldLog_samplingRateOne_alwaysLog() {
        PerEventSampling SamplingRateOneProto =
                PerEventSampling.newBuilder().setSamplingRate(1).build();

        PerEventLogSampler<ExampleEvent> perEventSampling =
                new PerEventLogSampler<>(
                        MetricId.EXAMPLE_STATS,
                        PerEventSamplingConfig.createPerEventSamplingConfig(
                                SamplingRateOneProto,
                                ImmutableMap.of(),
                                /* supportDimensionInLogSamplingEnabled= */ false));

        expect.that(perEventSampling.shouldLog(EXAMPLE_STATS))
                .isEqualTo(ALWAYS_LOG_SAMPLING_RESULT);
    }

    @Test
    public void testShouldLog_samplingRateZero_neverLog() {
        PerEventSampling SamplingRateZeroProto =
                PerEventSampling.newBuilder().setSamplingRate(0).build();

        PerEventLogSampler<ExampleEvent> perEventSampling =
                new PerEventLogSampler<>(
                        MetricId.EXAMPLE_STATS,
                        PerEventSamplingConfig.createPerEventSamplingConfig(
                                SamplingRateZeroProto,
                                ImmutableMap.of(),
                                /* supportDimensionInLogSamplingEnabled= */ false));

        expect.that(perEventSampling.shouldLog(EXAMPLE_STATS)).isEqualTo(NEVER_LOG_SAMPLING_RESULT);
    }

    private static final class ExampleEvent {
        private final int mId;
        private final int mErrorCode;

        private ExampleEvent(int id, int errorCode) {
            mId = id;
            mErrorCode = errorCode;
        }

        public int getId() {
            return mId;
        }

        public int getErrorCode() {
            return mErrorCode;
        }
    }
}
