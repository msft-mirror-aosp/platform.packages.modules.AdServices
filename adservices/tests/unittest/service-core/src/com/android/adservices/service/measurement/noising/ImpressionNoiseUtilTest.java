/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.measurement.noising;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Unit tests for ImpressionNoiseUtil class.
 */
public class ImpressionNoiseUtilTest {

    @FunctionalInterface
    public interface ThreeArgumentFunc<T1, T2, T3> {
        void apply(T1 t1, T2 t2, T3 t3);
    }

    private final ThreeArgumentFunc<ImpressionNoiseParams, List<int[]>, Integer>
            mGenerateReportConfigTester = (noiseParams, expectedReports, sequenceIndex) -> {
                List<int[]> actualReports = ImpressionNoiseUtil
                        .getReportConfigsForSequenceIndex(noiseParams, sequenceIndex);
                assertReportEquality(expectedReports, actualReports);
            };

    private final ThreeArgumentFunc<ImpressionNoiseParams, List<int[]>, Random>
            mStateSelectionTester = (noiseParams, expectedReports, rand) -> {
                List<int[]> actualReports = ImpressionNoiseUtil
                        .selectRandomStateAndGenerateReportConfigs(noiseParams, rand);
                assertReportEquality(expectedReports, actualReports);
            };

    private void assertReportEquality(List<int[]> expectedReports, List<int[]> actualReports) {
        assertEquals(expectedReports.size(), actualReports.size());
        for (int i = 0; i < expectedReports.size(); i++) {
            assertArrayEquals(expectedReports.get(i), actualReports.get(i));
        }
    }

    @Test
    public void selectRandomStateAndGenerateReportConfigs_event() {
        // Total states: {nCk ∋ n = 3 (2 * 1 + 1), k = 1} -> 3
        ImpressionNoiseParams noiseParams = new ImpressionNoiseParams(
                /* reportCount= */ 1,
                /* triggerDataCardinality= */ 2,
                /* reportingWindowCount= */ 1);

        Random rand = new Random(/* seed= */ 12);
        mStateSelectionTester.apply(
                /* impressionNoise= */ noiseParams,
                /* expectedReports= */ Collections.emptyList(),
                /* rand= */ rand
        );

        mStateSelectionTester.apply(
                /* impressionNoise= */ noiseParams,
                /* expectedReports= */ Collections.singletonList(
                        new int[]{1, 0}),
                /* rand= */ rand
        );
    }

    @Test
    public void selectRandomStateAndGenerateReportConfigs_eventWithInstallAttribution() {
        // Total states: {nCk ∋ n = 6 (2 * 2 + 2), k = 2} -> 15
        ImpressionNoiseParams noiseParams = new ImpressionNoiseParams(
                /* reportCount= */ 2,
                /* triggerDataCardinality= */ 2,
                /* reportingWindowCount= */ 2);

        Random rand = new Random(/* seed= */ 12);
        mStateSelectionTester.apply(
                /* impressionNoise= */ noiseParams,
                /* expectedReports= */ Collections.singletonList(
                        new int[]{0, 1}),
                /* rand= */ rand
        );

        mStateSelectionTester.apply(
                /* impressionNoise= */ noiseParams,
                /* expectedReports= */ Arrays.asList(new int[]{0, 0}, new int[]{0, 0}),
                /* rand= */ rand
        );
    }

    @Test
    public void selectRandomStateAndGenerateReportConfigs_navigation() {
        // Total states: {nCk ∋ n = 27 (8 * 3 + 3), k = 3} -> 2925
        ImpressionNoiseParams noiseParams = new ImpressionNoiseParams(
                /* reportCount= */ 3,
                /* triggerDataCardinality= */ 8,
                /* reportingWindowCount= */ 3);

        Random rand = new Random(/* seed= */ 12);

        mStateSelectionTester.apply(
                /* impressionNoise= */ noiseParams,
                /* expectedReports= */ Arrays.asList(
                        new int[]{2, 2},
                        new int[]{3, 1},
                        new int[]{7, 0}),
                /* rand= */ rand
        );

        mStateSelectionTester.apply(
                /* impressionNoise= */ noiseParams,
                /* expectedReports= */ Arrays.asList(
                        new int[]{0, 2},
                        new int[]{7, 1},
                        new int[]{6, 0}),
                /* rand= */ rand
        );
    }


    @Test
    public void getReportConfigsForSequenceIndex_event() {
        // Total states: {nCk ∋ n = 3 (2 * 1 + 1), k = 1} -> 3
        ImpressionNoiseParams eventNoiseParams = new ImpressionNoiseParams(
                /* reportCount= */ 1,
                /* triggerDataCardinality= */ 2,
                /* reportingWindowCount= */ 1);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ eventNoiseParams,
                /*expectedReports=*/ Collections.emptyList(),
                /*sequenceIndex=*/ 0);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ eventNoiseParams,
                /*expectedReports=*/ Collections.singletonList(new int[]{0, 0}),
                /*sequenceIndex=*/ 1);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ eventNoiseParams,
                /*expectedReports=*/ Collections.singletonList(new int[]{1, 0}),
                /*sequenceIndex=*/ 2);
    }

    @Test
    public void getReportConfigsForSequenceIndex_navigation() {
        // Total states: {nCk ∋ n = 27 (8 * 3 + 3), k = 3} -> 2925
        ImpressionNoiseParams navigationNoiseParams = new ImpressionNoiseParams(
                /* reportCount= */ 3,
                /* triggerDataCardinality= */ 8,
                /* reportingWindowCount= */ 3);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ navigationNoiseParams,
                /*expectedReports=*/ Collections.emptyList(),
                /*sequenceIndex=*/ 0);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ navigationNoiseParams,
                /*expectedReports=*/ Collections.singletonList(new int[]{3, 0}),
                /*sequenceIndex=*/ 20);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ navigationNoiseParams,
                /*expectedReports=*/ Arrays.asList(new int[]{4, 0}, new int[]{2, 0}),
                /*sequenceIndex=*/ 41);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ navigationNoiseParams,
                /*expectedReports=*/ Arrays.asList(new int[]{4, 0}, new int[]{4, 0}),
                /*sequenceIndex=*/ 50);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ navigationNoiseParams,
                /*expectedReports=*/
                Arrays.asList(new int[]{1, 2}, new int[]{6, 1}, new int[]{7, 0}),
                /*sequenceIndex=*/ 1268);
    }

    @Test
    public void getReportConfigsForSequenceIndex_eventWithInstallAttribution() {
        // Total states: {nCk ∋ n = 6 (2 * 2 + 2), k = 2} -> 15
        ImpressionNoiseParams eventWithInstallAttributionNoiseParams = new ImpressionNoiseParams(
                /* reportCount= */ 2,
                /* triggerDataCardinality= */ 2,
                /* reportingWindowCount= */ 2);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ eventWithInstallAttributionNoiseParams,
                /*expectedReports=*/ Collections.emptyList(),
                /*sequenceIndex=*/ 0);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ eventWithInstallAttributionNoiseParams,
                /*expectedReports=*/ Collections.singletonList(new int[]{0, 0}),
                /*sequenceIndex=*/ 1);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ eventWithInstallAttributionNoiseParams,
                /*expectedReports=*/ Arrays.asList(new int[]{0, 0}, new int[]{0, 0}),
                /*sequenceIndex=*/ 2);
        mGenerateReportConfigTester.apply(
                /*impressionNoise=*/ eventWithInstallAttributionNoiseParams,
                /*expectedReports=*/ Collections.singletonList(new int[]{1, 1}),
                /*sequenceIndex=*/ 10);
    }
}
