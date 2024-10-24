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

package com.android.adservices.service.shell;

import com.android.adservices.service.stats.ShellCommandStats;

import org.junit.Test;

public final class NoOpShellCommandTest extends ShellCommandTestCase<NoOpShellCommand> {

    @Test
    public void test_success() {
        String debugFlagName = "adservices";
        String commandName = "test";
        int expectedCommand = ShellCommandStats.COMMAND_ECHO;
        int expectedResult = ShellCommandStats.RESULT_NOT_ENABLED;
        NoOpShellCommand command =
                new NoOpShellCommand(commandName, expectedCommand, debugFlagName);
        Result actualResult = run(command, commandName);
        expectFailure(
                actualResult,
                String.format(NoOpShellCommand.RESPONSE_MSG, commandName),
                expectedCommand,
                expectedResult);
    }

    @Test
    public void test_getCommandName() {
        String debugFlagName = "adservices";
        String commandName = "test";
        int expectedCommand = ShellCommandStats.COMMAND_ECHO;
        NoOpShellCommand command =
                new NoOpShellCommand(commandName, expectedCommand, debugFlagName);
        expect.withMessage("getCommandName").that(command.getCommandName()).isEqualTo(commandName);
    }

    @Test
    public void test_getCommandHelp() {
        String debugFlagName = "adservices";
        String commandName = "test";
        int expectedCommand = ShellCommandStats.COMMAND_ECHO;
        NoOpShellCommand command =
                new NoOpShellCommand(commandName, expectedCommand, debugFlagName);
        expect.withMessage("getCommandHelp").that(command.getCommandHelp()).isEqualTo("");
    }
}
