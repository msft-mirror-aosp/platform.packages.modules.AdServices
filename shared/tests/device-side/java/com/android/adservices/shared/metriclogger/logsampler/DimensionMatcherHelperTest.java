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

package com.android.adservices.shared.metriclogger.logsampler;

import com.android.adservices.shared.SharedUnitTestCase;
import com.android.adservices.shared.proto.Dimension;
import com.android.adservices.shared.proto.DimensionMatcher;
import com.android.adservices.shared.proto.DimensionName;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.Test;

import java.util.List;
import java.util.function.Function;

public final class DimensionMatcherHelperTest extends SharedUnitTestCase {

    private static final double CUSTOM_SAMPLE_RATE = 0.01;
    private static final double CUSTOM_SAMPLE_RATE_2 = 0.001;

    private static final ImmutableList<DimensionMatcher> SINGLE_DIMENSION_MATCHERS =
            ImmutableList.of(
                    DimensionMatcher.newBuilder()
                            .setSamplingRate(CUSTOM_SAMPLE_RATE)
                            .addDimension(
                                    Dimension.newBuilder()
                                            .setName(DimensionName.CEL_ERROR_CODE)
                                            .addAllValue(List.of(101, 201, 301))
                                            .build())
                            .build());

    private static final ImmutableList<DimensionMatcher> MULTI_DIMENSION_MATCHERS =
            ImmutableList.of(
                    DimensionMatcher.newBuilder()
                            .setSamplingRate(CUSTOM_SAMPLE_RATE)
                            .addDimension(
                                    Dimension.newBuilder()
                                            .setName(DimensionName.CEL_ERROR_CODE)
                                            .addAllValue(List.of(101, 201, 301))
                                            .build())
                            .addDimension(
                                    Dimension.newBuilder()
                                            .setName(DimensionName.CEL_PPAPI_NAME)
                                            .addAllValue(List.of(2, 4, 6))
                                            .build())
                            .build(),
                    DimensionMatcher.newBuilder()
                            .setSamplingRate(CUSTOM_SAMPLE_RATE_2)
                            .addDimension(
                                    Dimension.newBuilder()
                                            .setName(DimensionName.CEL_ERROR_CODE)
                                            .addAllValue(List.of(401, 501, 601))
                                            .build())
                            .addDimension(
                                    Dimension.newBuilder()
                                            .setName(DimensionName.CEL_PPAPI_NAME)
                                            .addAllValue(List.of(2, 4, 6))
                                            .build())
                            .build());

    private static final ImmutableMap<DimensionName, Function<ExampleEvent, Integer>>
            VALUE_EXTRACTOR_FUNCTION_MAP =
                    ImmutableMap.of(
                            DimensionName.CEL_ERROR_CODE,
                            ExampleEvent::getErrorCode,
                            DimensionName.CEL_PPAPI_NAME,
                            ExampleEvent::getId);

    private static final double DEFAULT_SAMPLE_RATE = 0.3;

    @Test
    public void testGetDimensionSampleRateOrDefault_emptyDimensionMatcherList() {
        ExampleEvent event = new ExampleEvent(/* id= */ 1, /* errorCode= */ 101, /* value= */ 1000);

        double actual =
                DimensionMatcherHelper.getDimensionSampleRateOrDefault(
                        List.of(), VALUE_EXTRACTOR_FUNCTION_MAP, () -> event, DEFAULT_SAMPLE_RATE);
        expect.that(actual).isEqualTo(DEFAULT_SAMPLE_RATE);
    }

    @Test
    public void testGetDimensionSampleRateOrDefault_valueExtractorFunctionMissing() {
        ExampleEvent event = new ExampleEvent(/* id= */ 1, /* errorCode= */ 101, /* value= */ 1000);

        double actual =
                DimensionMatcherHelper.getDimensionSampleRateOrDefault(
                        List.of(), ImmutableMap.of(), () -> event, DEFAULT_SAMPLE_RATE);
        expect.that(actual).isEqualTo(DEFAULT_SAMPLE_RATE);
    }

    @Test
    public void testGetDimensionSampleRateOrDefault_dimensionMatches() {
        List<Integer> errorCodes = List.of(101, 201, 301);
        for (int errorCode : errorCodes) {
            ExampleEvent event = new ExampleEvent(/* id= */ 1, errorCode, /* value= */ 1000);

            double actual =
                    DimensionMatcherHelper.getDimensionSampleRateOrDefault(
                            SINGLE_DIMENSION_MATCHERS,
                            VALUE_EXTRACTOR_FUNCTION_MAP,
                            () -> event,
                            DEFAULT_SAMPLE_RATE);
            expect.withMessage("errorCode=" + errorCode).that(actual).isEqualTo(CUSTOM_SAMPLE_RATE);
        }
    }

    @Test
    public void testGetDimensionSampleRateOrDefault_dimensionDoesNotMatch() {
        List<Integer> errorCodes = List.of(110, 210, 310);
        for (int errorCode : errorCodes) {
            ExampleEvent event = new ExampleEvent(/* id= */ 1, errorCode, /* value= */ 1000);

            double actual =
                    DimensionMatcherHelper.getDimensionSampleRateOrDefault(
                            SINGLE_DIMENSION_MATCHERS,
                            VALUE_EXTRACTOR_FUNCTION_MAP,
                            () -> event,
                            DEFAULT_SAMPLE_RATE);
            expect.withMessage("errorCode=" + errorCode)
                    .that(actual)
                    .isEqualTo(DEFAULT_SAMPLE_RATE);
        }
    }

    @Test
    public void testGetDimensionSampleRateOrDefault_multiDimensionMatches() {
        List<Integer> errorCodes = List.of(101, 201, 301);
        List<Integer> ids = List.of(2, 4, 6);
        for (int errorCode : errorCodes) {
            for (int id : ids) {
                ExampleEvent event = new ExampleEvent(id, errorCode, /* value= */ 1000);

                double actual =
                        DimensionMatcherHelper.getDimensionSampleRateOrDefault(
                                MULTI_DIMENSION_MATCHERS,
                                VALUE_EXTRACTOR_FUNCTION_MAP,
                                () -> event,
                                DEFAULT_SAMPLE_RATE);
                expect.withMessage(String.format("errorCode=%d, id=%d", errorCode, id))
                        .that(actual)
                        .isEqualTo(CUSTOM_SAMPLE_RATE);
            }
        }

        List<Integer> errorCodes2 = List.of(401, 501, 601);
        for (int errorCode : errorCodes2) {
            for (int id : ids) {
                ExampleEvent event = new ExampleEvent(id, errorCode, /* value= */ 1000);

                double actual =
                        DimensionMatcherHelper.getDimensionSampleRateOrDefault(
                                MULTI_DIMENSION_MATCHERS,
                                VALUE_EXTRACTOR_FUNCTION_MAP,
                                () -> event,
                                DEFAULT_SAMPLE_RATE);
                expect.withMessage(String.format("errorCode=%d, id=%d", errorCode, id))
                        .that(actual)
                        .isEqualTo(CUSTOM_SAMPLE_RATE_2);
            }
        }
    }

    @Test
    public void testGetDimensionSampleRateOrDefault_multiDimensionDoesNotMatch() {
        List<Integer> errorCodes = List.of(101, 201, 301);
        List<Integer> ids = List.of(20, 21, 22);
        for (int errorCode : errorCodes) {
            for (int id : ids) {
                ExampleEvent event = new ExampleEvent(id, errorCode, /* value= */ 1000);

                double actual =
                        DimensionMatcherHelper.getDimensionSampleRateOrDefault(
                                MULTI_DIMENSION_MATCHERS,
                                VALUE_EXTRACTOR_FUNCTION_MAP,
                                () -> event,
                                DEFAULT_SAMPLE_RATE);
                expect.withMessage(String.format("errorCode=%d, id=%d", errorCode, id))
                        .that(actual)
                        .isEqualTo(DEFAULT_SAMPLE_RATE);
            }
        }

        List<Integer> errorCodes2 = List.of(210, 310, 410);
        List<Integer> ids2 = List.of(2, 4, 6);
        for (int errorCode : errorCodes2) {
            for (int id : ids2) {
                ExampleEvent event = new ExampleEvent(id, errorCode, /* value= */ 1000);

                double actual =
                        DimensionMatcherHelper.getDimensionSampleRateOrDefault(
                                MULTI_DIMENSION_MATCHERS,
                                VALUE_EXTRACTOR_FUNCTION_MAP,
                                () -> event,
                                DEFAULT_SAMPLE_RATE);
                expect.withMessage(String.format("errorCode=%d, id=%d", errorCode, id))
                        .that(actual)
                        .isEqualTo(DEFAULT_SAMPLE_RATE);
            }
        }
    }

    private static final class ExampleEvent {
        private final int mId;
        private final int mErrorCode;
        private final int mValue;

        private ExampleEvent(int id, int errorCode, int value) {
            mId = id;
            mErrorCode = errorCode;
            mValue = value;
        }

        public int getId() {
            return mId;
        }

        public int getErrorCode() {
            return mErrorCode;
        }

        public int getValue() {
            return mValue;
        }
    }
}
