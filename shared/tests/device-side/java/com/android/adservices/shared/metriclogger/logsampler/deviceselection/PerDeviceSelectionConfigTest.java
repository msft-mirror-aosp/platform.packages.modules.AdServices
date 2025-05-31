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

package com.android.adservices.shared.metriclogger.logsampler.deviceselection;

import static com.android.adservices.shared.metriclogger.logsampler.deviceselection.PerDeviceSamplingConfig.DEFAULT_GROUP_NAME;
import static com.android.adservices.shared.metriclogger.logsampler.deviceselection.PerDeviceSamplingConfig.DEFAULT_ROTATION_PERIOD;
import static com.android.adservices.shared.metriclogger.logsampler.deviceselection.PerDeviceSamplingConfig.DEFAULT_SAMPLING_RATE;
import static com.android.adservices.shared.metriclogger.logsampler.deviceselection.PerDeviceSamplingConfig.DEFAULT_STAGGERING_PERIOD;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.SharedUnitTestCase;
import com.android.adservices.shared.proto.Dimension;
import com.android.adservices.shared.proto.DimensionMatcher;
import com.android.adservices.shared.proto.DimensionName;
import com.android.adservices.shared.proto.LogSamplingConfig.PerDeviceSampling;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

public final class PerDeviceSelectionConfigTest extends SharedUnitTestCase {

    private static final PerDeviceSampling EXAMPLE_PER_DEVICE_SAMPLING_CONFIG =
            PerDeviceSampling.newBuilder()
                    .setSamplingRate(0.5)
                    .setRotationPeriodDays(50)
                    .setStaggeringPeriodDays(2)
                    .setGroupName("example")
                    .build();

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
    public void testCreatePerDeviceSamplingConfig_defaultConfig_alwaysLog() {
        PerDeviceSamplingConfig<ExampleEvent> config =
                PerDeviceSamplingConfig.createPerDeviceSamplingConfig(
                        PerDeviceSampling.getDefaultInstance(),
                        ImmutableMap.of(),
                        /* supportDimensionInLogSamplingEnabled= */ false);

        expect.withMessage("samplingRate")
                .that(config.getSamplingRate())
                .isEqualTo(DEFAULT_SAMPLING_RATE);
        expect.withMessage("rotationPeriod")
                .that(config.getRotationPeriod())
                .isEqualTo(DEFAULT_ROTATION_PERIOD);
        expect.withMessage("staggeringPeriod")
                .that(config.getStaggeringPeriod())
                .isEqualTo(DEFAULT_STAGGERING_PERIOD);
        expect.withMessage("groupName").that(config.getGroupName()).isEqualTo(DEFAULT_GROUP_NAME);
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
    public void testCreatePerDeviceSamplingConfig() {
        PerDeviceSamplingConfig<ExampleEvent> config =
                PerDeviceSamplingConfig.createPerDeviceSamplingConfig(
                        EXAMPLE_PER_DEVICE_SAMPLING_CONFIG,
                        ImmutableMap.of(),
                        /* supportDimensionInLogSamplingEnabled= */ false);

        expect.withMessage("samplingRate").that(config.getSamplingRate()).isEqualTo(0.5);
        expect.withMessage("rotationPeriod")
                .that(config.getRotationPeriod())
                .isEqualTo(Duration.ofDays(50));
        expect.withMessage("staggeringPeriod")
                .that(config.getStaggeringPeriod())
                .isEqualTo(Duration.ofDays(2));
        expect.withMessage("groupName").that(config.getGroupName()).isEqualTo("example");
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
    public void testCreatePerDeviceSamplingConfig_dimensionMatcherPresent() {
        PerDeviceSamplingConfig<ExampleEvent> config =
                PerDeviceSamplingConfig.createPerDeviceSamplingConfig(
                        EXAMPLE_PER_DEVICE_SAMPLING_CONFIG.toBuilder()
                                .addAllDimensionMatcher(SINGLE_DIMENSION_MATCHERS)
                                .build(),
                        VALUE_EXTRACTOR_FUNCTION_MAP,
                        /* supportDimensionInLogSamplingEnabled= */ true);

        expect.withMessage("samplingRate").that(config.getSamplingRate()).isEqualTo(0.5);
        expect.withMessage("rotationPeriod")
                .that(config.getRotationPeriod())
                .isEqualTo(Duration.ofDays(50));
        expect.withMessage("staggeringPeriod")
                .that(config.getStaggeringPeriod())
                .isEqualTo(Duration.ofDays(2));
        expect.withMessage("groupName").that(config.getGroupName()).isEqualTo("example");
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
    public void testCreatePerDeviceSamplingConfig_invalidSamplingRate_alwaysLog() {
        // Upper bound is invalid
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PerDeviceSamplingConfig.createPerDeviceSamplingConfig(
                                PerDeviceSampling.newBuilder().setSamplingRate(1.5).build(),
                                ImmutableMap.of(),
                                /* supportDimensionInLogSamplingEnabled= */ false));

        // Lower bound is invalid
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PerDeviceSamplingConfig.createPerDeviceSamplingConfig(
                                PerDeviceSampling.newBuilder().setSamplingRate(-1).build(),
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
