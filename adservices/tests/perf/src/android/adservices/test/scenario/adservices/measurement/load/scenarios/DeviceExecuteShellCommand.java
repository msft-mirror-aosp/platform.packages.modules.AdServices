/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.adservices.test.scenario.adservices.measurement.load.scenarios;

import android.platform.test.option.StringOption;
import android.platform.test.scenario.annotation.Scenario;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@Scenario
@RunWith(JUnit4.class)
public class DeviceExecuteShellCommand extends AbstractTestAction {

    private static final String OPTION_SHELL_COMMAND = "shell_command";

    @Rule public final StringOption shellCommandOption = new StringOption(OPTION_SHELL_COMMAND);

    @Test
    public void executeShellCommand() {
        delayPre();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .executeShellCommand("%s".formatted(shellCommandOption.get()));
        delayPost();
        Log.i(TAG, generateLog("Shell Command Ran: %s".formatted(shellCommandOption.get())));
    }
}
