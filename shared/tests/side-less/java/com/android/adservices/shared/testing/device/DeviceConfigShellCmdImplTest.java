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
package com.android.adservices.shared.testing.device;

import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.NONE;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.PERSISTENT;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.UNSUPPORTED;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.UNTIL_REBOOT;
import static com.android.adservices.shared.testing.device.ShellCommandOutput.EMPTY_RESULT;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.FakeDeviceGateway;
import com.android.adservices.shared.meta_testing.FakeLogger;
import com.android.adservices.shared.testing.AndroidSdk.Level;
import com.android.adservices.shared.testing.SharedSidelessTestCase;
import com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest;

import org.junit.Test;

public final class DeviceConfigShellCmdImplTest extends SharedSidelessTestCase {

    private static final SyncDisabledModeForTest[] REAL_MODES = {NONE, PERSISTENT, UNTIL_REBOOT};

    private final FakeLogger mFakeLogger = new FakeLogger();
    private final FakeDeviceGateway mGateway = new FakeDeviceGateway();
    private final DeviceConfigShellCmdImpl mImpl =
            new DeviceConfigShellCmdImpl(mFakeLogger, mGateway);

    @Test
    public void testSyncDisabledModeForTest_getShellCommandString() {
        expect.withMessage("%s.getShellCommandString()", NONE)
                .that(NONE.getShellCommandString())
                .isEqualTo("none");
        expect.withMessage("%s.getShellCommandString()", PERSISTENT)
                .that(PERSISTENT.getShellCommandString())
                .isEqualTo("persistent");
        expect.withMessage("%s.getShellCommandString()", UNTIL_REBOOT)
                .that(UNTIL_REBOOT.getShellCommandString())
                .isEqualTo("until_reboot");
    }

    @Test
    public void testNullConstructor() {
        assertThrows(
                NullPointerException.class,
                () -> new DeviceConfigShellCmdImpl(mFakeLogger, /* gateway= */ null));
        assertThrows(
                NullPointerException.class,
                () -> new DeviceConfigShellCmdImpl(/* realLogger= */ null, mGateway));
    }

    @Test
    public void testSetSyncDisabledMode_null() {
        assertThrows(NullPointerException.class, () -> mImpl.setSyncDisabledMode(null));
    }

    @Test
    public void testSetSyncDisabledMode_R() {
        mGateway.setSdkLevel(Level.R);

        for (var mode : REAL_MODES) {
            mImpl.setSyncDisabledMode(mode);

            mGateway.expectNothingCalled(expect);
        }
    }

    @Test
    public void testSetSyncDisabledMode_SPlus() {
        mGateway.setSdkLevel(Level.S);

        for (var mode : REAL_MODES) {
            ShellCommandInput input =
                    new ShellCommandInput(
                            "device_config set_sync_disabled_for_tests %s",
                            mode.getShellCommandString());
            mGateway.onCommand(input, EMPTY_RESULT);
            mImpl.setSyncDisabledMode(mode);
            mGateway.expectCalled(expect, input);
        }
    }

    @Test
    public void testSetSyncDisabledMode_failedBecauseOutIsNotEmpty() {
        mGateway.setSdkLevel(Level.S);
        ShellCommandInput input =
                new ShellCommandInput("device_config set_sync_disabled_for_tests persistent");
        ShellCommandOutput output = new ShellCommandOutput("D'OH!");
        mGateway.onCommand(input, output);

        var thrown =
                assertThrows(
                        InvalidShellCommandResultException.class,
                        () -> mImpl.setSyncDisabledMode(PERSISTENT));

        expect.withMessage("input on exception").that(thrown.getInput()).isEqualTo(input);
        expect.withMessage("output on exception").that(thrown.getOutput()).isSameInstanceAs(output);
    }

    @Test
    public void testSetSyncDisabledMode_failedBecauseErrIsNotEmpty() {
        mGateway.setSdkLevel(Level.S);
        ShellCommandInput input =
                new ShellCommandInput("device_config set_sync_disabled_for_tests persistent");
        ShellCommandOutput output =
                new ShellCommandOutput.Builder().setOut("").setErr("Annoyed grunt").build();
        mGateway.onCommand(input, output);

        var thrown =
                assertThrows(
                        InvalidShellCommandResultException.class,
                        () -> mImpl.setSyncDisabledMode(PERSISTENT));

        expect.withMessage("input on exception").that(thrown.getInput()).isEqualTo(input);
        expect.withMessage("output on exception").that(thrown.getOutput()).isSameInstanceAs(output);
    }

    @Test
    public void testGetSyncDisabledMode_R() {
        mGateway.setSdkLevel(Level.R);

        expect.withMessage("getSyncDisabledMode()")
                .that(mImpl.getSyncDisabledMode())
                .isEqualTo(UNSUPPORTED);
    }

    @Test
    public void testGetSyncDisabledMode_invalidResult_S() {
        testGetSyncDisabledModeInvalidResultForSLevels(Level.S);
    }

    @Test
    public void testGetSyncDisabledMode_invalidResult_S2() {
        testGetSyncDisabledModeInvalidResultForSLevels(Level.S2);
    }

    private void testGetSyncDisabledModeInvalidResultForSLevels(Level level) {
        mGateway.setSdkLevel(level);
        ShellCommandInput input = new ShellCommandInput("device_config is_sync_disabled_for_tests");
        ShellCommandOutput output = new ShellCommandOutput("D'OH!");
        mGateway.onCommand(input, output);

        var thrown =
                assertThrows(
                        InvalidShellCommandResultException.class,
                        () -> mImpl.getSyncDisabledMode());

        expect.withMessage("input on exception").that(thrown.getInput()).isEqualTo(input);
        expect.withMessage("output on exception").that(thrown.getOutput()).isSameInstanceAs(output);
    }

    @Test
    public void testGetSyncDisabledMode_invalidResult_TPlus() {
        mGateway.setSdkLevel(Level.T);
        ShellCommandInput input =
                new ShellCommandInput("device_config get_sync_disabled_for_tests");
        ShellCommandOutput output = new ShellCommandOutput("D'OH!");
        mGateway.onCommand(input, output);

        var thrown =
                assertThrows(
                        InvalidShellCommandResultException.class,
                        () -> mImpl.getSyncDisabledMode());

        expect.withMessage("input on exception").that(thrown.getInput()).isEqualTo(input);
        expect.withMessage("output on exception").that(thrown.getOutput()).isSameInstanceAs(output);
    }

    @Test
    public void testGetSyncDisabledMode_S() {
        testGetSyncDisabledModeForSLevels(Level.S);
    }

    @Test
    public void testGetSyncDisabledMode_S2() {
        testGetSyncDisabledModeForSLevels(Level.S2);
    }

    private void testGetSyncDisabledModeForSLevels(Level level) {
        mGateway.setSdkLevel(level);
        for (var mode : REAL_MODES) {
            mGateway.onCommand(
                    new ShellCommandInput("device_config is_sync_disabled_for_tests"),
                    new ShellCommandOutput(mode.getShellCommandString()));

            var actualMode = mImpl.getSyncDisabledMode();

            expect.withMessage("getSyncDisabledMode()").that(actualMode).isEqualTo(mode);
        }
    }

    @Test
    public void testGetSyncDisabledMode_S_cmdReturnedStandardError() {
        testGetSyncDisabledModeForSLevelsWhenCmdReturnedStandardError(Level.S);
    }

    @Test
    public void testGetSyncDisabledMode_S2_cmdReturnedStandardError() {
        testGetSyncDisabledModeForSLevelsWhenCmdReturnedStandardError(Level.S2);
    }

    private void testGetSyncDisabledModeForSLevelsWhenCmdReturnedStandardError(Level level) {
        mGateway.setSdkLevel(level);
        ShellCommandInput input = new ShellCommandInput("device_config is_sync_disabled_for_tests");
        ShellCommandOutput output =
                new ShellCommandOutput.Builder().setOut("").setErr("D'OH!").build();
        mGateway.onCommand(input, output);

        var thrown =
                assertThrows(
                        InvalidShellCommandResultException.class,
                        () -> mImpl.getSyncDisabledMode());

        expect.withMessage("input on exception").that(thrown.getInput()).isEqualTo(input);
        expect.withMessage("output on exception").that(thrown.getOutput()).isSameInstanceAs(output);
    }

    @Test
    public void testGetSyncDisabledMode_TPlus() {
        mGateway.setSdkLevel(Level.T);
        for (var mode : REAL_MODES) {
            mGateway.onCommand(
                    new ShellCommandInput("device_config get_sync_disabled_for_tests"),
                    new ShellCommandOutput(mode.getShellCommandString()));

            var actualMode = mImpl.getSyncDisabledMode();

            expect.withMessage("getSyncDisabledMode()").that(actualMode).isEqualTo(mode);
        }
    }

    @Test
    public void testGetSyncDisabledMode_TPlus_cmdReturnedStandardError() {
        mGateway.setSdkLevel(Level.T);
        ShellCommandInput input =
                new ShellCommandInput("device_config get_sync_disabled_for_tests");
        ShellCommandOutput output =
                new ShellCommandOutput.Builder().setOut("").setErr("D'OH!").build();
        mGateway.onCommand(input, output);

        var thrown =
                assertThrows(
                        InvalidShellCommandResultException.class,
                        () -> mImpl.getSyncDisabledMode());

        expect.withMessage("input on exception").that(thrown.getInput()).isEqualTo(input);
        expect.withMessage("output on exception").that(thrown.getOutput()).isSameInstanceAs(output);
    }
}
