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

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Stress test used to enable the Battery Saver mode while running in parallel with CUJs. */
@Scenario
@RunWith(JUnit4.class)
public final class StressBatterySaverMode extends StressScenarioTestAction {

    private static final String TAG = "StressBatteryUtilization";

    @Test
    public void startBatterySaverMode() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .executeShellCommand(
                        "cmd battery unplug && adb shell settings put global low_power 1");
        Log.i(TAG, "Battery Saver Mode is on");

        holdUntilCujsRunning();

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .executeShellCommand(
                        "cmd battery reset && adb shell settings delete global low_power");
        Log.i(TAG, "Battery Saver Mode is off");
    }
}
