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

package android.adservices.test.longevity.concurrent;

import android.platform.test.scenario.annotation.Scenario;
import android.util.Log;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Stress test used to utilize the CPU while running in parallel with CUJs. */
@Scenario
@RunWith(JUnit4.class)
public final class StressCpuUtilization extends StressScenarioTestAction {
    private static final int NUMBER_OF_THREADS = 200;
    private static final int INNER_LOOP_ITERATIONS = 1000000;
    private static final int THREAD_SLEEP_MS = 100;
    private static final String TAG = "StressCpuUtilization";

    @Test
    public void startCpuStress() {
        for (int i = 0; i < NUMBER_OF_THREADS; i++) {
            new Thread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    for (int j = 0; j < INNER_LOOP_ITERATIONS; j++) {
                                        performComplexCalculations();
                                        if (needToCancel()) {
                                            return;
                                        }
                                        try {
                                            Thread.sleep(THREAD_SLEEP_MS);
                                        } catch (InterruptedException e) {
                                            Log.e(TAG, "CPU stress thread interrupted", e);
                                            Thread.currentThread().interrupt();
                                        }
                                    }
                                }
                            })
                    .start();
        }

        holdUntilCujsRunning();
    }

    /** A very long calculation to keep the CPU busy. */
    private void performComplexCalculations() {
        int n = 30;
        long result = fibonacci(n);
        Log.d(TAG, "Fibonacci(" + n + ") = " + result);
    }

    private long fibonacci(int n) {
        if (n <= 1) {
            return n;
        } else {
            return fibonacci(n - 1) + fibonacci(n - 2);
        }
    }
}
