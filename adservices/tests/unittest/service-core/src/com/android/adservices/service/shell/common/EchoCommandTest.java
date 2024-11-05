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

package com.android.adservices.service.shell.common;

import static com.android.adservices.service.shell.common.EchoCommand.CMD_ECHO;
import static com.android.adservices.service.shell.common.EchoCommand.HELP_ECHO;

import com.android.adservices.service.shell.ShellCommandTestCase;
import com.android.adservices.service.stats.ShellCommandStats;

import org.junit.Test;

public final class EchoCommandTest extends ShellCommandTestCase<EchoCommand> {

    @Test
    public void testRun_invalid() {
        EchoCommand echoCommand = new EchoCommand();

        // no args
        runAndExpectInvalidArgument(
                echoCommand, HELP_ECHO, ShellCommandStats.COMMAND_ECHO, CMD_ECHO);
        // empty message
        runAndExpectInvalidArgument(
                echoCommand, HELP_ECHO, ShellCommandStats.COMMAND_ECHO, CMD_ECHO, "");
    }

    @Test
    public void testRun_valid() {
        Result actualResult = run(new EchoCommand(), CMD_ECHO, "108");

        expectSuccess(actualResult, "108\n", ShellCommandStats.COMMAND_ECHO);
    }

    @Test
    public void testRun_multipleArgs_valid() {
        Result actualResult = run(new EchoCommand(), CMD_ECHO, "I", "am", "Groot");

        expectSuccess(actualResult, "I am Groot\n", ShellCommandStats.COMMAND_ECHO);
    }

    @Test
    public void testGetCommandName_valid() {
        expect.that(new EchoCommand().getCommandName()).isEqualTo(CMD_ECHO);
    }

    @Test
    public void testGetCommandHelp_valid() {
        expect.that(new EchoCommand().getCommandHelp()).isEqualTo(HELP_ECHO);
    }
}
