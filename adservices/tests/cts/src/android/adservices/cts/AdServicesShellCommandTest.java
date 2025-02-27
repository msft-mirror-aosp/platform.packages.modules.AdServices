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

package android.adservices.cts;

import static com.android.adservices.service.CommonDebugFlagsConstants.KEY_ADSERVICES_SHELL_COMMAND_ENABLED;

import com.android.adservices.common.AdServicesShellCommandHelper;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.shell.CommandResult;

import org.junit.Test;

@EnableDebugFlag(KEY_ADSERVICES_SHELL_COMMAND_ENABLED)
public final class AdServicesShellCommandTest extends CtsAdServicesDeviceTestCase {
    private static final String CMD_ECHO = "echo";
    private static final String CMD_ECHO_OUT = "hello";

    private final AdServicesShellCommandHelper mShellCommandHelper =
            new AdServicesShellCommandHelper();

    @Test
    public void testRunCommand_echoCommand() {
        String out = mShellCommandHelper.runCommand("%s %s", CMD_ECHO, CMD_ECHO_OUT);

        expect.withMessage("out").that(out).isEqualTo(CMD_ECHO_OUT);
    }

    @Test
    public void testRunCommandRwe_echoCommand() {
        CommandResult out = mShellCommandHelper.runCommandRwe("%s %s", CMD_ECHO, CMD_ECHO_OUT);

        expect.withMessage("out").that(out.getOut()).isEqualTo(CMD_ECHO_OUT);
        expect.withMessage("err").that(out.getErr()).isEmpty();
    }
}
