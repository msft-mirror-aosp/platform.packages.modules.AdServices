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

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.SharedUnitTestCase;
import com.android.adservices.shared.proto.Dimension;
import com.android.adservices.shared.proto.DimensionMatcher;
import com.android.adservices.shared.proto.DimensionName;
import com.android.adservices.shared.proto.LogSamplingConfig.PerEventSampling;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.Test;

import java.util.List;
import java.util.function.Function;

public final class PerEventSamplingConfigTest extends SharedUnitTestCase {

    private static final double SAMPLE_RATE_50_PERCENT = 0.5;
    private static final double SAMPLE_RATE_100_PERCENT = 1.0;
    private static final PerEventSampling EXAMPLE_PER_EVENT_SAMPLING_CONFIG =
            PerEventSampling.newBuilder().setSamplingRate(SAMPLE_RATE_50_PERCENT).build();

    private static final ImmutableList<DimensionMatcher> SINGLE_DIMENSION_MATCHERS =
            ImmutableList.of(
                    DimensionMatcher.newBuilder()
                            .setSamplingRate(0.01)
                            .addDimension(
                                    Dimension.newBuilder()
                                            .setName(DimensionName.CEL_ERROR_CODE)
                                            .addAllValue(List.of(101, 201, 301))
                                            .build())
                            .build());

    private static final ImmutableMap<DimensionName, Function<ExampleEvent, Integer>>
            VALUE_EXTRACTOR_FUNCTION_MAP =
                    ImmutableMap.of(
                            DimensionName.CEL_ERROR_CODE,
                            ExampleEvent::getErrorCode,
                            DimensionName.CEL_PPAPI_NAME,
                            ExampleEvent::getId);

    @Test
    public void testCreatePerEventSamplingConfig() {
        PerEventSamplingConfig<ExampleEvent> config =
                PerEventSamplingConfig.createPerEventSamplingConfig(
                        EXAMPLE_PER_EVENT_SAMPLING_CONFIG,
                        ImmutableMap.of(),
                        /* supportDimensionInLogSamplingEnabled= */ false);

        expect.withMessage("sampleRate")
                .that(config.getSamplingRate())
                .isEqualTo(SAMPLE_RATE_50_PERCENT);
        expect.withMessage("dimensionNameToValueFunctionMap")
                .that(config.getDimensionNameToValueFunctionMap())
                .isEqualTo(ImmutableMap.of());
        expect.withMessage("dimensionMatcherList")
                .that(config.getDimensionMatcherList())
                .isEqualTo(ImmutableList.of());
        expect.withMessage("supportDimensionInLogSamplingEnabled")
                .that(config.getSupportDimensionInLogSamplingEnabled())
                .isEqualTo(false);
    }

    @Test
    public void testCreatePerEventSamplingConfig_defaultConfig_alwaysLog() {
        PerEventSamplingConfig<ExampleEvent> config =
                PerEventSamplingConfig.createPerEventSamplingConfig(
                        PerEventSampling.getDefaultInstance(),
                        ImmutableMap.of(),
                        /* supportDimensionInLogSamplingEnabled= */ false);

        expect.withMessage("sampleRate")
                .that(config.getSamplingRate())
                .isEqualTo(SAMPLE_RATE_100_PERCENT);
        expect.withMessage("dimensionNameToValueFunctionMap")
                .that(config.getDimensionNameToValueFunctionMap())
                .isEqualTo(ImmutableMap.of());
        expect.withMessage("dimensionMatcherList")
                .that(config.getDimensionMatcherList())
                .isEqualTo(ImmutableList.of());
        expect.withMessage("supportDimensionInLogSamplingEnabled")
                .that(config.getSupportDimensionInLogSamplingEnabled())
                .isEqualTo(false);
    }

    @Test
    public void testCreatePerEventSamplingConfig_dimensionMatcherPresent() {
        PerEventSamplingConfig<ExampleEvent> config =
                PerEventSamplingConfig.createPerEventSamplingConfig(
                        EXAMPLE_PER_EVENT_SAMPLING_CONFIG.toBuilder()
                                .addAllDimensionMatcher(SINGLE_DIMENSION_MATCHERS)
                                .build(),
                        VALUE_EXTRACTOR_FUNCTION_MAP,
                        /* supportDimensionInLogSamplingEnabled= */ true);

        expect.withMessage("sampleRate")
                .that(config.getSamplingRate())
                .isEqualTo(SAMPLE_RATE_50_PERCENT);
        expect.withMessage("dimensionNameToValueFunctionMap")
                .that(config.getDimensionNameToValueFunctionMap())
                .isEqualTo(VALUE_EXTRACTOR_FUNCTION_MAP);
        expect.withMessage("dimensionMatcherList")
                .that(config.getDimensionMatcherList())
                .isEqualTo(SINGLE_DIMENSION_MATCHERS);
        expect.withMessage("supportDimensionInLogSamplingEnabled")
                .that(config.getSupportDimensionInLogSamplingEnabled())
                .isEqualTo(true);
    }

    @Test
    public void testCreatePerEventSamplingConfig_invalidSamplingRate_alwaysLog() {
        // Upper bound is invalid
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PerEventSamplingConfig.createPerEventSamplingConfig(
                                PerEventSampling.newBuilder().setSamplingRate(1.5).build(),
                                ImmutableMap.of(),
                                /* supportDimensionInLogSamplingEnabled= */ false));

        // Lower bound is invalid
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PerEventSamplingConfig.createPerEventSamplingConfig(
                                PerEventSampling.newBuilder().setSamplingRate(-1).build(),
                                ImmutableMap.of(),
                                /* supportDimensionInLogSamplingEnabled= */ false));
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
