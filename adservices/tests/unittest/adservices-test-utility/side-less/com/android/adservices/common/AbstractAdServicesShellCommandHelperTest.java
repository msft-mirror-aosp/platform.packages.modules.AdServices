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

package com.android.adservices.common;

import static android.os.Build.VERSION_CODES.S_V2;
import static android.os.Build.VERSION_CODES.TIRAMISU;

import com.android.adservices.common.AbstractAdServicesShellCommandHelper.CommandResult;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;

/**
 * Test case for {@link AbstractAdServicesShellCommandHelper} implementation.
 *
 * <p>Since {@link AbstractAdServicesShellCommandHelper} is an abstract class, we provide fake
 * implementation of abstract methods.
 */
public final class AbstractAdServicesShellCommandHelperTest {

    private static final String CMD_ECHO = "echo";
    private static final String CMD_ECHO_OUT = "hello";
    private static final String SAMPLE_DUMPSYS_OUTPUT =
            "com.google.android.adservices.api/com.android.adservices.shell"
                    + ".AdServicesShellCommandService 4e0649c pid=5303 user=0\nClient:\n"
                    + CMD_ECHO_OUT;

    @Rule public final Expect expect = Expect.create();

    private final Logger.RealLogger mRealLogger = StandardStreamsLogger.getInstance();

    private FakeAdServicesShellCommandHelper mAdServicesShellCommandHelper =
            new FakeAdServicesShellCommandHelper(mRealLogger, TIRAMISU);

    @Test
    public void testParseResultFromDumpsys_success() {
        String res = mAdServicesShellCommandHelper.parseResultFromDumpsys(SAMPLE_DUMPSYS_OUTPUT);

        expect.that(res).isEqualTo(CMD_ECHO_OUT);
    }

    @Test
    public void testParseResultFromDumpsys_fails() {
        String input =
                "com.google.android.adservices.api/com.android.adservices.shell"
                        + ".AdServicesShellCommandService 4e0649c pid=5303 user=0:\n hello";

        String res = mAdServicesShellCommandHelper.parseResultFromDumpsys(input);

        expect.that(res).isEqualTo(input);
    }

    @Test
    public void testRunCommand_deviceLevelTPlus() {
        String res = mAdServicesShellCommandHelper.runCommand("%s %s", CMD_ECHO, CMD_ECHO_OUT);

        expect.that(res).isEqualTo(CMD_ECHO_OUT);
    }

    @Test
    public void testRunCommand_deviceLevelS() {
        FakeAdServicesShellCommandHelper adServicesShellCommandHelperOnS =
                new FakeAdServicesShellCommandHelper(mRealLogger, S_V2);

        String res = adServicesShellCommandHelperOnS.runCommand("%s %s", CMD_ECHO, CMD_ECHO_OUT);

        expect.that(res).isEqualTo(CMD_ECHO_OUT);
    }

    @Test
    public void testRunCommandRwe_deviceLevelTPlus() {
        CommandResult res =
                mAdServicesShellCommandHelper.runCommandRwe("%s %s", CMD_ECHO, CMD_ECHO_OUT);

        expect.withMessage("out").that(res.getOut()).isEqualTo(CMD_ECHO_OUT);
        expect.withMessage("err").that(res.getErr()).isEmpty();
    }

    @Test
    public void testRunCommandRwe_deviceLevelS() {
        FakeAdServicesShellCommandHelper adServicesShellCommandHelperOnS =
                new FakeAdServicesShellCommandHelper(mRealLogger, S_V2);

        CommandResult res =
                adServicesShellCommandHelperOnS.runCommandRwe("%s %s", CMD_ECHO, CMD_ECHO_OUT);

        expect.withMessage("out").that(res.getOut()).isEqualTo(CMD_ECHO_OUT);
        expect.withMessage("err").that(res.getErr()).isEmpty();
    }

    private static final class FakeAdServicesShellCommandHelper
            extends AbstractAdServicesShellCommandHelper {

        private final int mDeviceLevel;

        FakeAdServicesShellCommandHelper(Logger.RealLogger logger, int deviceLevel) {
            super(logger);
            mDeviceLevel = deviceLevel;
        }

        @Override
        protected String runShellCommand(String cmd) {
            return sampleShellCommandOutput(cmd);
        }

        @Override
        protected CommandResult runShellCommandRwe(String cmd) {
            String res = sampleShellCommandOutput(cmd);
            return new CommandResult(res, "");
        }

        @Override
        protected int getDeviceApiLevel() {
            return mDeviceLevel;
        }

        private String sampleShellCommandOutput(String cmd) {
            if (cmd.contains("cmd adservices_manager")) {
                return CMD_ECHO_OUT;
            } else if (cmd.equals(START_SHELL_COMMAND_SERVICE)) {
                return "started";
            } else if (cmd.equals(
                    runDumpsysShellCommand(String.format("%s %s", CMD_ECHO, CMD_ECHO_OUT)))) {
                return SAMPLE_DUMPSYS_OUTPUT;
            }
            return "";
        }
    }
}
