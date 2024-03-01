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
import static org.junit.Assert.assertTrue;

import com.android.adservices.service.measurement.PrivacyParams;

import com.google.common.math.BigIntegerMath;

import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CombinatoricsTest {
    @Test
    public void testGetKCombinationAtIndex() {
        // Test Case { {combinationIndex, k}, expectedOutput}
        long[][][] testCases = {
                {{0, 0}, {}},

                {{0, 1}, {0}}, {{1, 1}, {1}}, {{2, 1}, {2}},
                {{3, 1}, {3}}, {{4, 1}, {4}}, {{5, 1}, {5}},
                {{6, 1}, {6}}, {{7, 1}, {7}}, {{8, 1}, {8}},
                {{9, 1}, {9}}, {{10, 1}, {10}}, {{11, 1}, {11}},
                {{12, 1}, {12}}, {{13, 1}, {13}}, {{14, 1}, {14}},
                {{15, 1}, {15}}, {{16, 1}, {16}}, {{17, 1}, {17}},
                {{18, 1}, {18}}, {{19, 1}, {19}},

                {{0, 2}, {1, 0}}, {{1, 2}, {2, 0}}, {{2, 2}, {2, 1}},
                {{3, 2}, {3, 0}}, {{4, 2}, {3, 1}}, {{5, 2}, {3, 2}},
                {{6, 2}, {4, 0}}, {{7, 2}, {4, 1}}, {{8, 2}, {4, 2}},
                {{9, 2}, {4, 3}}, {{10, 2}, {5, 0}}, {{11, 2}, {5, 1}},
                {{12, 2}, {5, 2}}, {{13, 2}, {5, 3}}, {{14, 2}, {5, 4}},
                {{15, 2}, {6, 0}}, {{16, 2}, {6, 1}}, {{17, 2}, {6, 2}},
                {{18, 2}, {6, 3}}, {{19, 2}, {6, 4}},

                {{0, 3}, {2, 1, 0}}, {{1, 3}, {3, 1, 0}}, {{2, 3}, {3, 2, 0}},
                {{3, 3}, {3, 2, 1}}, {{4, 3}, {4, 1, 0}}, {{5, 3}, {4, 2, 0}},
                {{6, 3}, {4, 2, 1}}, {{7, 3}, {4, 3, 0}}, {{8, 3}, {4, 3, 1}},
                {{9, 3}, {4, 3, 2}}, {{10, 3}, {5, 1, 0}}, {{11, 3}, {5, 2, 0}},
                {{12, 3}, {5, 2, 1}}, {{13, 3}, {5, 3, 0}}, {{14, 3}, {5, 3, 1}},
                {{15, 3}, {5, 3, 2}}, {{16, 3}, {5, 4, 0}}, {{17, 3}, {5, 4, 1}},
                {{18, 3}, {5, 4, 2}}, {{19, 3}, {5, 4, 3}},

                {{2924, 3}, {26, 25, 24}},
        };
        Arrays.stream(testCases).forEach((testCase) ->
                assertArrayEquals(
                        Arrays.stream(testCase[1])
                                .mapToObj(BigInteger::valueOf)
                                .toArray(BigInteger[]::new),
                        Combinatorics.getKCombinationAtIndex(
                                /*combinationIndex=*/ BigInteger.valueOf(testCase[0][0]),
                                /*k=*/ (int) testCase[0][1])));
    }

    @Test
    public void testGetKCombinationNoRepeat() {
        for (int k = 1; k < 5; k++) {
            Set<List<BigInteger>> seenCombinations = new HashSet<>();
            for (long combinationIndex = 0L; combinationIndex < 1000L; combinationIndex++) {
                List<BigInteger> combination =
                        Arrays.stream(Combinatorics.getKCombinationAtIndex(
                                BigInteger.valueOf(combinationIndex), k))
                                        .collect(Collectors.toList());
                assertTrue(seenCombinations.add(combination));
            }
        }
    }

    @Test
    public void testGetKCombinationMatchesDefinition() {
        for (int k = 1; k < 5; k++) {
            for (long index = 0L; index < 1000L; index++) {
                BigInteger[] combination = Combinatorics.getKCombinationAtIndex(
                        BigInteger.valueOf(index), k);
                BigInteger sum = BigInteger.ZERO;
                for (int i = 0; i < k; i++) {
                    int n = combination[i].intValue();
                    if (n >= k - i) {
                        sum = sum.add(BigIntegerMath.binomial(n, k - i));
                    }
                }
                assertEquals(sum, BigInteger.valueOf(index));
            }
        }
    }

    @Test
    public void testGetNumberOfStarsAndBarsSequences() {
        assertEquals(BigInteger.valueOf(3L), Combinatorics.getNumberOfStarsAndBarsSequences(
                /*numStars=*/1, /*numBars=*/2
        ));
        assertEquals(BigInteger.valueOf(2925L), Combinatorics.getNumberOfStarsAndBarsSequences(
                /*numStars=*/3, /*numBars=*/24
        ));
    }

    @Test
    public void testGetStarIndices() {
        // Test Case: { {numStars, sequenceIndex}, expectedOutput }
        long[][][] testCases = {
                {{1L, 2L, 2L}, {2L}},
                {{3L, 24L, 23L}, {6L, 3L, 0L}},
        };

        Arrays.stream(testCases).forEach((testCase) ->
                assertArrayEquals(
                        Arrays.stream(testCase[1])
                                .mapToObj(BigInteger::valueOf)
                                .toArray(BigInteger[]::new),
                        Combinatorics.getStarIndices(/*numStars=*/ (int) testCase[0][0],
                                /*sequenceIndex=*/ BigInteger.valueOf(testCase[0][2]))));

    }

    @Test
    public void testGetBarsPrecedingEachStar() {
        // Test Case: {starIndices, expectedOutput}
        long[][][] testCases = {
                {{2L}, {2L}},
                {{6L, 3L, 0L}, {4L, 2L, 0L}}
        };

        Arrays.stream(testCases).forEach((testCase) ->
                assertArrayEquals(
                        Arrays.stream(testCase[1])
                                .mapToObj(BigInteger::valueOf)
                                .toArray(BigInteger[]::new),
                        Combinatorics.getBarsPrecedingEachStar(
                            /*starIndices=*/ Arrays.stream(testCase[0])
                                    .mapToObj(BigInteger::valueOf).toArray(BigInteger[]::new))));
    }

    @Test
    public void testNumStatesArithmeticNoOverflow() {
        // Test Case: {numBucketIncrements, numTriggerData, numWindows}, {expected number of states}
        int[][][] testCases = {
            {{3, 8, 3}, {2925}},
            {{1, 1, 1}, {2}},
            {{1, 2, 3}, {7}},
            {{3, 2, 1}, {10}}
        };

        Arrays.stream(testCases)
                .forEach(
                        (testCase) ->
                                assertEquals(
                                        BigInteger.valueOf((long) testCase[1][0]),
                                        Combinatorics.getNumStatesArithmetic(
                                                testCase[0][0], testCase[0][1], testCase[0][2])));
    }

    @Test
    public void testNumStatesFlexApi() {
        // Test Case: {numBucketIncrements, perTypeNumWindows, perTypeCap}, {expected number of
        // states}
        int[][][][] testCases = {
            {{{3}, {3, 3, 3, 3, 3, 3, 3, 3}, {3, 3, 3, 3, 3, 3, 3, 3}}, {{2925}}},
            {{{2}, {2, 2}, {2, 2}}, {{15}}},
            {{{3}, {2, 2}, {2, 2}}, {{27}}},
            {{{3}, {2, 2}, {3, 3}}, {{35}}},
            {{{3}, {4, 4}, {2, 2}}, {{125}}},
            {{{7}, {2, 2}, {3, 3}}, {{100}}},
            {{{7}, {2, 2}, {4, 5}}, {{236}}},
            {{{1000}, {2, 2}, {4, 5}}, {{315}}},
            {{{1000}, {2, 2, 2}, {4, 5, 4}}, {{4725}}},
            {{{1000}, {2, 2, 2, 2}, {4, 5, 4, 2}}, {{28350}}},
            {{{5}, {2}, {5}}, {{21}}},
        };

        Arrays.stream(testCases)
                .forEach(
                        (testCase) ->
                                assertEquals(
                                        BigInteger.valueOf((long) testCase[1][0][0]),
                                        Combinatorics.getNumStatesFlexApi(
                                                testCase[0][0][0],
                                                testCase[0][1],
                                                testCase[0][2])));
    }

    @Test
    public void testFlipProbability() {
        // Test Case: {number of states}, {expected flip probability multiply 100}
        double[][] testCases = {
            {2925.0, 0.24263221679834088d},
            {3.0, 0.0002494582008677539d},
            {455.0, 0.037820279032938435d},
            {2.0, 0.0001663056055328264d},
            {1.0, 0.00008315280276d}
        };

        Arrays.stream(testCases)
                .forEach(
                        (testCase) -> {
                            double result = 100 * Combinatorics.getFlipProbability(
                                    BigInteger.valueOf((long) testCase[0]));
                            assertEquals(testCase[1], result, PrivacyParams.NUMBER_EQUAL_THRESHOLD);
                        });
    }

    @Test
    public void testInformationGain() {
        // Test Case: {number of states}, {expected flip probability multiply 100}
        double[][] testCases = {
            {2925.0, 11.461727965384876d},
            {3.0, 1.5849265115082312d},
            {455.0, 8.821556150827456d},
            {2.0, 0.9999820053790732d},
            {1.0, 0.0d}
        };

        Arrays.stream(testCases)
                .forEach(
                        (testCase) -> {
                            double result =
                                    Combinatorics.getInformationGain(
                                            BigInteger.valueOf((long) testCase[0]),
                                            Combinatorics.getFlipProbability(
                                                    BigInteger.valueOf((long) testCase[0])));
                            assertEquals(testCase[1], result, PrivacyParams.NUMBER_EQUAL_THRESHOLD);
                        });
    }

    private static boolean atomReportStateSetMeetRequirement(
            int totalCap,
            int[] perTypeNumWindowList,
            int[] perTypeCapList,
            List<Combinatorics.AtomReportState> reportSet) {
        // if number of report over max reports
        if (reportSet.size() > totalCap) {
            return false;
        }
        int[] perTypeReportList = new int[perTypeCapList.length];
        // Initialize all elements to zero
        for (int i = 0; i < perTypeCapList.length; i++) {
            perTypeReportList[i] = 0;
        }
        for (Combinatorics.AtomReportState report : reportSet) {
            int triggerDataIndex = report.getTriggerDataType();
            // if the report window larger than total report window of this trigger data
            // input perTypeNumWindowList is [3,3,3], and report windows index is 4, return false
            if (report.getWindowIndex() + 1 > perTypeNumWindowList[triggerDataIndex]) {
                return false;
            }
            perTypeReportList[triggerDataIndex]++;
            // number of report for this trigger data over the per data limit
            if (perTypeCapList[triggerDataIndex] < perTypeReportList[triggerDataIndex]) {
                return false;
            }
        }
        return true;
    }

    private static class AtomReportStateComparator
            implements Comparator<Combinatorics.AtomReportState> {
        @Override
        public int compare(Combinatorics.AtomReportState o1, Combinatorics.AtomReportState o2) {
            if (o1.getTriggerDataType() != o2.getTriggerDataType()) {
                return Integer.compare(o1.getTriggerDataType(), o2.getTriggerDataType());
            }
            return Integer.compare(o1.getWindowIndex(), o2.getWindowIndex());
        }
    }
}
