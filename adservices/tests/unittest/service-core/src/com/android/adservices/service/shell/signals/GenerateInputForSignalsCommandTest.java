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

package com.android.adservices.service.shell.signals;

import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_SIGNALS_GENERATE_INPUT_FOR_SIGNALS;

import android.adservices.common.AdTechIdentifier;

import com.android.adservices.service.shell.ShellCommandTestCase;
import com.android.adservices.service.stats.ShellCommandStats;

import org.junit.Test;

public class GenerateInputForSignalsCommandTest
        extends ShellCommandTestCase<GenerateInputForSignalsCommand> {
    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("example.com");

    @ShellCommandStats.Command
    private static final int EXPECTED_COMMAND = COMMAND_SIGNALS_GENERATE_INPUT_FOR_SIGNALS;

    @Test
    public void testRun_happyPath_returnsSuccess() {
        Result actualResult = runCommandAndGetResult();

        expectSuccess(actualResult, EXPECTED_COMMAND);
    }

    private Result runCommandAndGetResult() {
        return run(
                new GenerateInputForSignalsCommand(),
                SignalsShellCommandFactory.COMMAND_PREFIX,
                GenerateInputForSignalsCommand.CMD,
                "--buyer",
                BUYER.toString());
    }
}
