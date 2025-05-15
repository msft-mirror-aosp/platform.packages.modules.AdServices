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

import static org.junit.Assert.assertTrue;

import android.platform.test.option.IntegerOption;

import org.junit.Rule;

/** Base class for stress scenarios. */
public abstract class StressScenarioTestAction {
    private static final int MAIN_LOOP_DELAY_MS = 500;

    // Common Options
    private static final String ALLOCATE_AMOUNT = "allocate_amount";
    private static final String ALLOCATE_PERCENTAGE = "allocate_percentage";

    protected static final String TAG = "StressScenarioTestAction";

    @Rule
    public final IntegerOption mAllocateAmountOption =
            new IntegerOption(ALLOCATE_AMOUNT).setRequired(false).setDefault(-1);

    @Rule
    public final IntegerOption mAllocatePercentageOption =
            new IntegerOption(ALLOCATE_PERCENTAGE).setRequired(false).setDefault(-1);

    protected boolean needToCancel() {
        return ConcurrentScenariosStatement.needToCancelStressTests();
    }

    /** Holds the thread to stress the system while the CUJs in the current scenario are running. */
    protected void holdUntilCujsRunning() {
        while (!needToCancel()) {
            try {
                Thread.sleep(MAIN_LOOP_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                assertTrue(false);
            }
        }
    }
}
