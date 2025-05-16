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

/** Base class for stress scenarios that stress the system unless all CUJs are running. */
public abstract class HoldingStressScenarioTestAction extends StressScenarioTestAction {
    private static final int MAIN_LOOP_DELAY_MS = 500;

    protected static final String TAG = "StressScenarioTestAction";

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
