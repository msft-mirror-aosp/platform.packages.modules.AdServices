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

import static com.android.adservices.service.shell.AbstractShellCommand.ERROR_TEMPLATE_INVALID_ARGS;
import static com.android.adservices.service.shell.EchoCommand.CMD_ECHO;
import static com.android.adservices.service.shell.EchoCommand.HELP_ECHO;

import org.junit.Test;

import java.util.Arrays;

public final class EchoCommandTest extends ShellCommandTest<EchoCommand> {

    public EchoCommandTest() {
        super(new EchoCommand());
    }

    @Test
    public void testRun_invalid() throws Exception {
        // no args
        expectInvalidArgument(HELP_ECHO, CMD_ECHO);
        // empty message
        expectInvalidArgument(HELP_ECHO, CMD_ECHO, "");
        // more than 1 arg
        expectInvalidArgument(HELP_ECHO, CMD_ECHO, "4", "8", "15", "16", "23", "42");
    }

    @Test
    public void testRun_valid() {
        Result actualResult = run(CMD_ECHO, "108");

        expectSuccess(actualResult, "108\n");
    }

    private void expectInvalidArgument(String syntax, String... args) throws Exception {
        Result actualResult = run(args);
        String expectedErr =
                String.format(ERROR_TEMPLATE_INVALID_ARGS, Arrays.toString(args), syntax);

        expectFailure(actualResult, expectedErr);
    }
}
