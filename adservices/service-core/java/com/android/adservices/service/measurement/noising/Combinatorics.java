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

import com.android.adservices.service.measurement.PrivacyParams;

import com.google.common.math.BigIntegerMath;
import com.google.common.math.DoubleMath;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Combinatorics utilities used for randomization.
 */
public class Combinatorics {

    /**
     * Returns the k-combination associated with the number {@code combinationIndex}. In
     * other words, returns the combination of {@code k} integers uniquely indexed by
     * {@code combinationIndex} in the combinatorial number system.
     * https://en.wikipedia.org/wiki/Combinatorial_number_system
     *
     * @return combinationIndex-th lexicographically smallest k-combination.
     * @throws ArithmeticException in case of int overflow
     */
    static BigInteger[] getKCombinationAtIndex(BigInteger combinationIndex, int k) {
        // Computes the combinationIndex-th lexicographically smallest k-combination.
        // https://en.wikipedia.org/wiki/Combinatorial_number_system
        //
        // A k-combination is a sequence of k non-negative integers in decreasing order.
        // a_k > a_{k-1} > ... > a_2 > a_1 >= 0.
        // k-combinations can be ordered lexicographically, with the smallest
        // k-combination being a_k=k-1, a_{k-1}=k-2, .., a_1=0. Given an index
        // combinationIndex>=0, and an order k, this method returns the
        // combinationIndex-th smallest k-combination.
        //
        // Given an index combinationIndex, the combinationIndex-th k-combination
        // is the unique set of k non-negative integers
        // a_k > a_{k-1} > ... > a_2 > a_1 >= 0
        // such that combinationIndex = \sum_{i=1}^k {a_i}\choose{i}
        //
        // We find this set via a simple greedy algorithm.
        // http://math0.wvstateu.edu/~baker/cs405/code/Combinadics.html
        BigInteger[] result = new BigInteger[k];
        if (k == 0) {
            return result;
        }
        // To find a_k, iterate candidates upwards from 0 until we've found the
        // maximum a such that (a choose k) <= combinationIndex. Let a_k = a. Use
        // the previous binomial coefficient to compute the next one. Note: possible
        // to speed this up via something other than incremental search.
        BigInteger target = combinationIndex;
        BigInteger candidate = BigInteger.valueOf((long) k - 1L);
        BigInteger binomialCoefficient = BigInteger.ZERO;
        BigInteger nextBinomialCoefficient = BigInteger.ONE;
        while (nextBinomialCoefficient.compareTo(target) <= 0) {
            candidate = candidate.add(BigInteger.ONE);
            binomialCoefficient = nextBinomialCoefficient;
            // (n + 1 choose k) = (n choose k) * (n + 1) / (n + 1 - k)
            nextBinomialCoefficient = binomialCoefficient.multiply(candidate.add(BigInteger.ONE));
            nextBinomialCoefficient = nextBinomialCoefficient.divide(
                    candidate.add(BigInteger.ONE).subtract(BigInteger.valueOf((long) k)));
        }
        // We know from the k-combination definition, all subsequent values will be
        // strictly decreasing. Find them all by decrementing candidate.
        // Use the previous binomial coefficient to compute the next one.
        BigInteger currentK = BigInteger.valueOf((long) k);
        int currentIndex = 0;
        while (true) {
            if (binomialCoefficient.compareTo(target) <= 0) {
                result[currentIndex] = candidate;
                currentIndex++;
                target = target.subtract(binomialCoefficient);
                if (currentIndex == k) {
                    return result;
                }
                // (n - 1 choose k - 1) = (n choose k) * k / n
                binomialCoefficient = binomialCoefficient.multiply(currentK).divide(candidate);
                currentK = currentK.subtract(BigInteger.ONE);
            } else {
                // (n - 1 choose k) = (n choose k) * (n - k) / n
                binomialCoefficient = binomialCoefficient.multiply(candidate.subtract(currentK))
                        .divide(candidate);
            }
            candidate = candidate.subtract(BigInteger.ONE);
        }
    }

    /**
     * Returns the number of possible sequences of "stars and bars" sequences
     * https://en.wikipedia.org/wiki/Stars_and_bars_(combinatorics),
     * which is equivalent to (numStars + numBars choose numStars).
     *
     * @param numStars number of stars
     * @param numBars  number of bars
     * @return number of possible sequences
     */
    public static BigInteger getNumberOfStarsAndBarsSequences(int numStars, int numBars) {
        return BigIntegerMath.binomial(numStars + numBars, numStars);
    }

    /**
     * Returns an array of the indices of every star in the stars and bars sequence indexed by
     * {@code sequenceIndex}.
     *
     * @param numStars number of stars in the sequence
     * @param sequenceIndex index of the sequence
     * @return list of indices of every star in stars & bars sequence
     */
    public static BigInteger[] getStarIndices(int numStars, BigInteger sequenceIndex) {
        return getKCombinationAtIndex(sequenceIndex, numStars);
    }

    /**
     * From an array with the index of every star in a stars and bars sequence, returns an array
     * which, for every star, counts the number of bars preceding it.
     *
     * @param starIndices indices of the stars in descending order
     * @return count of bars preceding every star
     */
    public static BigInteger[] getBarsPrecedingEachStar(BigInteger[] starIndices) {
        for (int i = 0; i < starIndices.length; i++) {
            BigInteger starIndex = starIndices[i];
            // There are {@code starIndex} prior positions in the sequence, and `i` prior
            // stars, so there are {@code starIndex - i} prior bars.
            starIndices[i] = starIndex.subtract(
                    BigInteger.valueOf((long) starIndices.length - 1L - (long) i));
        }
        return starIndices;
    }

    /**
     * Compute number of states from the trigger specification
     *
     * @param numBucketIncrements number of bucket increments (equivalent to number of triggers)
     * @param numTriggerData number of trigger data. (equivalent to number of metadata)
     * @param numWindows number of reporting windows
     * @return number of states
     */
    public static BigInteger getNumStatesArithmetic(
            int numBucketIncrements, int numTriggerData, int numWindows) {
        int numStars = numBucketIncrements;
        int numBars = Math.multiplyExact(numTriggerData, numWindows);
        return getNumberOfStarsAndBarsSequences(numStars, numBars);
    }

    /**
     * Using dynamic programming to compute number of states. Assuming the parameter validation has
     * been checked to avoid overflow or out of memory error
     *
     * @param totalCap total incremental cap
     * @param perTypeNumWindowList reporting window per trigger data
     * @param perTypeCapList cap per trigger data
     * @return number of states
     */
    private static BigInteger getNumStatesRecursive(
            int totalCap, int[] perTypeNumWindowList, int[] perTypeCapList) {
        int index = perTypeNumWindowList.length - 1;
        return getNumStatesRecursive(
                totalCap,
                index,
                perTypeNumWindowList[index],
                perTypeCapList[index],
                perTypeNumWindowList,
                perTypeCapList,
                new HashMap<>());
    }

    private static BigInteger getNumStatesRecursive(
            int totalCap,
            int index,
            int winVal,
            int capVal,
            int[] perTypeNumWindowList,
            int[] perTypeCapList,
            Map<List<Integer>, BigInteger> dp) {
        List<Integer> key = List.of(totalCap, index, winVal, capVal);
        if (!dp.containsKey(key)) {
            if (winVal == 0 && index == 0) {
                dp.put(key, BigInteger.ONE);
            } else if (winVal == 0) {
                dp.put(key, getNumStatesRecursive(
                        totalCap,
                        index - 1,
                        perTypeNumWindowList[index - 1],
                        perTypeCapList[index - 1],
                        perTypeNumWindowList,
                        perTypeCapList,
                        dp));
            } else {
                BigInteger result = BigInteger.ZERO;
                for (int i = 0; i <= Math.min(totalCap, capVal); i++) {
                    result = result.add(getNumStatesRecursive(
                            totalCap - i,
                            index,
                            winVal - 1,
                            capVal - i,
                            perTypeNumWindowList,
                            perTypeCapList,
                            dp));
                }
                dp.put(key, result);
            }
        }
        return dp.get(key);
    }

    /**
     * Compute number of states for flexible event report API
     *
     * @param totalCap number of total increments
     * @param perTypeNumWindowList reporting window for each trigger data
     * @param perTypeCapList limit of the increment of each trigger data
     * @return number of states
     */
    public static BigInteger getNumStatesFlexApi(
            int totalCap, int[] perTypeNumWindowList, int[] perTypeCapList) {
        if (perTypeNumWindowList.length == 0 || perTypeCapList.length == 0) {
            return BigInteger.ONE;
        }
        for (int i = 1; i < perTypeNumWindowList.length; i++) {
            if (perTypeNumWindowList[i] != perTypeNumWindowList[i - 1]) {
                return getNumStatesRecursive(totalCap, perTypeNumWindowList, perTypeCapList);
            }
        }
        for (int n : perTypeCapList) {
            if (n < totalCap) {
                return getNumStatesRecursive(totalCap, perTypeNumWindowList, perTypeCapList);
            }
        }

        return getNumStatesArithmetic(totalCap, perTypeCapList.length, perTypeNumWindowList[0]);
    }

    /**
     * @param numOfStates Number of States
     * @return the probability to use fake reports
     */
    public static double getFlipProbability(BigInteger numOfStates) {
        double numStatesDouble = numOfStates.doubleValue();
        double epsilon = (double) PrivacyParams.getPrivacyEpsilon();
        return numStatesDouble / (numStatesDouble + Math.exp(epsilon) - 1D);
    }

    private static double getBinaryEntropy(double x) {
        if (DoubleMath.fuzzyEquals(x, 0.0d, PrivacyParams.NUMBER_EQUAL_THRESHOLD)
                || DoubleMath.fuzzyEquals(x, 1.0d, PrivacyParams.NUMBER_EQUAL_THRESHOLD)) {
            return 0;
        }
        return (-1.0) * x * DoubleMath.log2(x) - (1 - x) * DoubleMath.log2(1 - x);
    }

    /**
     * @param numOfStates Number of States
     * @param flipProbability Flip Probability
     * @return the information gain
     */
    public static double getInformationGain(BigInteger numOfStates, double flipProbability) {
        if (numOfStates.compareTo(BigInteger.ONE) <= 0) {
            return 0d;
        }
        double numStatesDouble = numOfStates.doubleValue();
        double fakeProbability = flipProbability * (numStatesDouble - 1D) / numStatesDouble;
        return DoubleMath.log2(numStatesDouble)
                - getBinaryEntropy(fakeProbability)
                - fakeProbability * DoubleMath.log2(numStatesDouble - 1D);
    }

    /**
     * Generate fake report set given a trigger specification and the rank order number
     *
     * @param totalCap total_cap
     * @param perTypeNumWindowList per type number of window list
     * @param perTypeCapList per type cap list
     * @return a report set based on the input rank
     */
    public static List<AtomReportState> getReportSetBasedOnRank(
            int totalCap,
            int[] perTypeNumWindowList,
            int[] perTypeCapList,
            BigInteger rank,
            Map<List<Integer>, BigInteger> dp) {
        int triggerTypeIndex = perTypeNumWindowList.length - 1;

        return getReportSetBasedOnRankRecursive(
                totalCap,
                triggerTypeIndex,
                perTypeNumWindowList[triggerTypeIndex],
                perTypeCapList[triggerTypeIndex],
                rank,
                perTypeNumWindowList,
                perTypeCapList,
                dp);
    }

    private static List<AtomReportState> getReportSetBasedOnRankRecursive(
            int totalCap,
            int triggerTypeIndex,
            int winVal,
            int capVal,
            BigInteger rank,
            int[] perTypeNumWindowList,
            int[] perTypeCapList,
            Map<List<Integer>, BigInteger> numStatesLookupTable) {

        if (winVal == 0 && triggerTypeIndex == 0) {
            return new ArrayList<>();
        } else if (winVal == 0) {
            return getReportSetBasedOnRankRecursive(
                    totalCap,
                    triggerTypeIndex - 1,
                    perTypeNumWindowList[triggerTypeIndex - 1],
                    perTypeCapList[triggerTypeIndex - 1],
                    rank,
                    perTypeNumWindowList,
                    perTypeCapList,
                    numStatesLookupTable);
        }
        for (int i = 0; i <= Math.min(totalCap, capVal); i++) {
            BigInteger currentNumStates =
                    getNumStatesRecursive(
                            totalCap - i,
                            triggerTypeIndex,
                            winVal - 1,
                            capVal - i,
                            perTypeNumWindowList,
                            perTypeCapList,
                            numStatesLookupTable);
            if (currentNumStates.compareTo(rank) > 0) {
                // The triggers to be appended.
                List<AtomReportState> toAppend = new ArrayList<>();
                for (int k = 0; k < i; k++) {
                    toAppend.add(new AtomReportState(triggerTypeIndex, winVal - 1));
                }
                List<AtomReportState> otherReports =
                        getReportSetBasedOnRankRecursive(
                                totalCap - i,
                                triggerTypeIndex,
                                winVal - 1,
                                capVal - i,
                                rank,
                                perTypeNumWindowList,
                                perTypeCapList,
                                numStatesLookupTable);
                toAppend.addAll(otherReports);
                return toAppend;
            } else {
                rank = rank.subtract(currentNumStates);
            }
        }
        // will not reach here
        return new ArrayList<>();
    }

    /** A single report including triggerDataType and window index for the fake report generation */
    public static class AtomReportState {
        private final int mTriggerDataType;
        private final int mWindowIndex;

        public AtomReportState(int triggerDataType, int windowIndex) {
            this.mTriggerDataType = triggerDataType;
            this.mWindowIndex = windowIndex;
        }

        public int getTriggerDataType() {
            return mTriggerDataType;
        }
        ;

        public final int getWindowIndex() {
            return mWindowIndex;
        }
        ;

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof AtomReportState)) {
                return false;
            }
            AtomReportState t = (AtomReportState) obj;
            return mTriggerDataType == t.mTriggerDataType && mWindowIndex == t.mWindowIndex;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mWindowIndex, mTriggerDataType);
        }
    }
}
