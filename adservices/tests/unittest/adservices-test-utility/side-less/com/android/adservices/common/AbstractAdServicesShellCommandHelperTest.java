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

import static org.mockito.Mockito.when;

import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.StandardStreamsLogger;
import com.android.adservices.shared.testing.shell.CommandResult;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

/**
 * Test case for {@link AbstractAdServicesShellCommandHelper} implementation.
 *
 * <p>Since {@link AbstractAdServicesShellCommandHelper} is an abstract class, we provide fake
 * implementation of abstract methods.
 */
public final class AbstractAdServicesShellCommandHelperTest extends AdServicesMockitoTestCase {

    private static final String CMD_ECHO = "echo";
    private static final String CMD_ECHO_OUT = "hello";
    private static final String ERR = "something went wrong";
    private static final String SAMPLE_COMMAND_OUT = "CommandOut:\n  " + CMD_ECHO_OUT;
    private static final String SAMPLE_COMMAND_ERR = "CommandErr:\n  " + ERR;
    private static final String SAMPLE_DUMPSYS =
            "TASK 10145:com.google.android.ext.services id=13 userId=0\nACTIVITY com.google"
                    + ".android.ext.services/com.android.adservices.shell.ShellCommandActivity "
                    + "a3ccaeb pid=6721\n"
                    + "-- ShellCommandActivity dump --\n"
                    + "CommandStatus: FINISHED\n"
                    + "CommandRes: 0\n";
    private static final String SAMPLE_DUMPSYS_WITH_OUT = SAMPLE_DUMPSYS + SAMPLE_COMMAND_OUT;
    private static final String SAMPLE_DUMPSYS_WITH_ERR = SAMPLE_DUMPSYS + SAMPLE_COMMAND_ERR;
    private static final String SAMPLE_DUMPSYS_WITH_OUT_AND_ERR =
            SAMPLE_DUMPSYS + SAMPLE_COMMAND_OUT + "\n" + SAMPLE_COMMAND_ERR;
    private static final String STATUS_FINISHED = "FINISHED";

    private static final String ADEXTSERVICES_PACKAGE_NAME = "com.google.android.ext.services";

    private static final String SAMPLE_DUMPSYS_UNKNOWN_COMMAND_OUTPUT =
            "Unknown command:"
                    + " com.google.android.ext.services/com.android.adservices.shell"
                    + ".ShellCommandActivity";

    @Mock private AbstractDeviceSupportHelper mAbstractDeviceSupportHelper;

    private final Logger.RealLogger mRealLogger = StandardStreamsLogger.getInstance();

    private final FakeAdServicesShellCommandHelper mAdServicesShellCommandHelper =
            new FakeAdServicesShellCommandHelper(
                    mAbstractDeviceSupportHelper, mRealLogger, TIRAMISU);

    @Before
    public void setup() {
        when(mAbstractDeviceSupportHelper.getAdServicesPackageName())
                .thenReturn(ADEXTSERVICES_PACKAGE_NAME);
    }

    @Test
    public void testParseResultFromDumpsys_success() {
        CommandResult commandResult =
                mAdServicesShellCommandHelper.parseResultFromDumpsys(SAMPLE_DUMPSYS_WITH_OUT);

        expect.withMessage("out").that(commandResult.getOut()).isEqualTo(CMD_ECHO_OUT);
        expect.withMessage("err").that(commandResult.getErr()).isEmpty();
        expect.withMessage("commandStatus")
                .that(commandResult.getCommandStatus())
                .isEqualTo(STATUS_FINISHED);
    }

    @Test
    public void testParseResultFromDumpsys_onlyErrPresent() {
        CommandResult commandResult =
                mAdServicesShellCommandHelper.parseResultFromDumpsys(SAMPLE_DUMPSYS_WITH_ERR);

        expect.withMessage("out").that(commandResult.getOut()).isEmpty();
        expect.withMessage("err").that(commandResult.getErr()).isEqualTo(ERR);
        expect.withMessage("commandStatus")
                .that(commandResult.getCommandStatus())
                .isEqualTo(STATUS_FINISHED);
    }

    @Test
    public void testParseResultFromDumpsys_bothOutAndErrPresent() {
        CommandResult commandResult =
                mAdServicesShellCommandHelper.parseResultFromDumpsys(
                        SAMPLE_DUMPSYS_WITH_OUT_AND_ERR);

        expect.withMessage("out").that(commandResult.getOut()).isEqualTo(CMD_ECHO_OUT);
        expect.withMessage("err").that(commandResult.getErr()).isEqualTo(ERR);
        expect.withMessage("commandStatus")
                .that(commandResult.getCommandStatus())
                .isEqualTo(STATUS_FINISHED);
    }

    @Test
    public void testParseResultFromDumpsys_fails() {
        String input =
                "TASK 10145:com.google.android.ext.services id=13 userId=0\n"
                        + "ACTIVITY"
                        + " com.google.android.ext.services/com.android.adservices.shell"
                        + ".ShellCommandActivity"
                        + " a3ccaeb pid=6721";

        CommandResult commandResult = mAdServicesShellCommandHelper.parseResultFromDumpsys(input);

        expect.that(commandResult.getOut()).isEqualTo(input);
        expect.withMessage("commandStatus")
                .that(commandResult.getCommandStatus())
                .isEqualTo(STATUS_FINISHED);
    }

    @Test
    public void testRunCommand_deviceLevelTPlus() {
        String res = mAdServicesShellCommandHelper.runCommand("%s %s", CMD_ECHO, CMD_ECHO_OUT);

        expect.that(res).isEqualTo(CMD_ECHO_OUT);
    }

    @Test
    public void testRunCommand_deviceLevelS() {
        FakeAdServicesShellCommandHelper adServicesShellCommandHelperOnS =
                new FakeAdServicesShellCommandHelper(
                        mAbstractDeviceSupportHelper, mRealLogger, S_V2);

        String res = adServicesShellCommandHelperOnS.runCommand("%s %s", CMD_ECHO, CMD_ECHO_OUT);

        expect.that(res).isEqualTo(CMD_ECHO_OUT);
    }

    @Test
    public void testRunCommand_deviceLevelT_usesSdkSandbox() {
        FakeAdServicesShellCommandHelper adServicesShellCommandHelper =
                new FakeAdServicesShellCommandHelper(
                        mAbstractDeviceSupportHelper,
                        mRealLogger,
                        TIRAMISU,
                        /* usesSdkSandbox= */ true);

        String res = adServicesShellCommandHelper.runCommand("%s %s", CMD_ECHO, CMD_ECHO_OUT);

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
                new FakeAdServicesShellCommandHelper(
                        mAbstractDeviceSupportHelper, mRealLogger, S_V2);

        CommandResult res =
                adServicesShellCommandHelperOnS.runCommandRwe("%s %s", CMD_ECHO, CMD_ECHO_OUT);

        expect.withMessage("out").that(res.getOut()).isEqualTo(CMD_ECHO_OUT);
        expect.withMessage("err").that(res.getErr()).isEmpty();
    }

    @Test
    public void testRunCommandRwe_deviceLevelT_usesSdkSandbox() {
        FakeAdServicesShellCommandHelper adServicesShellCommandHelper =
                new FakeAdServicesShellCommandHelper(
                        mAbstractDeviceSupportHelper,
                        mRealLogger,
                        TIRAMISU,
                        /* usesSdkSandbox= */ true);

        CommandResult res =
                adServicesShellCommandHelper.runCommandRwe("%s %s", CMD_ECHO, CMD_ECHO_OUT);

        expect.withMessage("out").that(res.getOut()).isEqualTo(CMD_ECHO_OUT);
        expect.withMessage("err").that(res.getErr()).isEmpty();
    }

    private static final class FakeAdServicesShellCommandHelper
            extends AbstractAdServicesShellCommandHelper {

        private final int mDeviceLevel;
        private final boolean mUsesSdkSandbox;
        private int mDumpsysCommandCount;

        FakeAdServicesShellCommandHelper(
                AbstractDeviceSupportHelper deviceSupportHelper,
                Logger.RealLogger logger,
                int deviceLevel) {
            this(deviceSupportHelper, logger, deviceLevel, /* usesSdkSandbox= */ false);
        }

        FakeAdServicesShellCommandHelper(
                AbstractDeviceSupportHelper deviceSupportHelper,
                Logger.RealLogger logger,
                int deviceLevel,
                boolean usesSdkSandbox) {
            super(deviceSupportHelper, logger);
            mUsesSdkSandbox = usesSdkSandbox;
            mDeviceLevel = deviceLevel;
            mDumpsysCommandCount = 0;
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
            } else if (cmd.contains("cmd sdk_sandbox adservices")) {
                return CMD_ECHO_OUT;
            } else if (cmd.equals(ADSERVICES_MANAGER_SERVICE_CHECK)) {
                return mUsesSdkSandbox ? " not found" : "found";
            } else if (cmd.equals(
                    getDumpsysGetResultShellCommand(
                            String.format(
                                    "%s/%s", ADEXTSERVICES_PACKAGE_NAME, SHELL_ACTIVITY_NAME)))) {
                String out =
                        mDumpsysCommandCount == 0
                                ? SAMPLE_DUMPSYS_WITH_OUT
                                : SAMPLE_DUMPSYS_UNKNOWN_COMMAND_OUTPUT;
                mDumpsysCommandCount++;
                return out;
            }
            return "";
        }
    }
}
