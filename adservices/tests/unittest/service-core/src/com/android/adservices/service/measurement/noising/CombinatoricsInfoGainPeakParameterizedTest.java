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

import static com.google.common.truth.Truth.assertThat;

import android.util.Log;

import com.android.adservices.service.Flags;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RunWith(Parameterized.class)
public class CombinatoricsInfoGainPeakParameterizedTest {
    private static final String TAG = "Measurement";
    private static final int NUM_TESTS = 50;
    private static final int MAX_Q1 = 1000;
    private static final int MAX_Q2 = 500;
    private static final int MAX_K = 500;
    private final int mQ1;
    private final int mQ2;
    private final int mK;

    public CombinatoricsInfoGainPeakParameterizedTest(int q1, int q2, int k) {
        mQ1 = q1;
        mQ2 = q2;
        mK = k;
    }

    @Parameterized.Parameters()
    public static Collection<Object[]> getTestCaseForRandomState() {
        Random random = new Random();
        // Test Case: {q1, q2, k}}
        return IntStream.range(0, NUM_TESTS)
                .mapToObj(
                        i ->
                                new Object[] {
                                    random.nextInt(MAX_Q1 - 1) + 1,
                                    random.nextInt(MAX_Q2 - 1) + 1,
                                    random.nextInt(MAX_K - 1) + 1
                                })
                .collect(Collectors.toList());
    }

    @Test
    public void runTest() {
        getSingleMaxInfoGainWithAttributionScope_binarySearchVsBruteForce_returnsSame(mQ1, mQ2, mK);
    }

    // Examples of information trending down as q2 is increased:
    // For q1 = 1000, k = 208:
    //     q2 = 409 ⇒ information gain = 14.902019973313834
    //     q2 = 410 ⇒ information gain = 14.902018418778523
    // For q1 = 1000, k = 282:
    //     q2 = 302 ⇒ information gain = 14.903115348611545
    //     q2 = 303 ⇒ information gain = 14.903106034436702
    private static void
            getSingleMaxInfoGainWithAttributionScope_binarySearchVsBruteForce_returnsSame(
                    int rQ1, int rQ2, int rK) {
        Log.i(
                TAG,
                "MaxInfoGainWithAttributionScope test for : rQ1 : "
                        + rQ1
                        + ", rQ2: "
                        + rQ2
                        + ", rK : "
                        + rK);
        double bruteForce = 0.0;
        for (int k = 0; k < rK; ++k) {
            for (int q2 = 1; q2 <= rQ2; ++q2) {
                bruteForce =
                        Math.max(
                                bruteForce,
                                Combinatorics.calculateInformationGainWithAttributionScope(
                                        rQ1, k, q2, Flags.DEFAULT_MEASUREMENT_PRIVACY_EPSILON));
            }
        }
        assertThat(bruteForce)
                .isEqualTo(
                        Combinatorics.getMaxInformationGainWithAttributionScope(
                                rQ1, rK, rQ2, Flags.DEFAULT_MEASUREMENT_PRIVACY_EPSILON));
    }
}
