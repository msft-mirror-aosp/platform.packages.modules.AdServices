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
            mImpl.setSyncDisabledMode(mode);

            mGateway.expectCalled(
                    expect,
                    "device_config set_sync_disabled_for_tests %s",
                    mode.name().toString().toLowerCase());
        }
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
        mGateway.onCommand("D'OH!", "device_config is_sync_disabled_for_tests");

        var thrown = assertThrows(IllegalStateException.class, () -> mImpl.getSyncDisabledMode());

        expect.withMessage("exception message")
                .that(thrown)
                .hasMessageThat()
                .isEqualTo(
                        "'device_config is_sync_disabled_for_tests' returned unexpected result:"
                                + " D'OH!");
    }

    @Test
    public void testGetSyncDisabledMode_invalidResult_TPlus() {
        mGateway.setSdkLevel(Level.T);
        mGateway.onCommand("D'OH!", "device_config get_sync_disabled_for_tests");

        var thrown = assertThrows(IllegalStateException.class, () -> mImpl.getSyncDisabledMode());

        expect.withMessage("exception message")
                .that(thrown)
                .hasMessageThat()
                .isEqualTo(
                        "'device_config get_sync_disabled_for_tests' returned unexpected result:"
                                + " D'OH!");
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
                    mode.name().toString().toLowerCase(),
                    "device_config is_sync_disabled_for_tests");

            var actualMode = mImpl.getSyncDisabledMode();

            expect.withMessage("getSyncDisabledMode()").that(actualMode).isEqualTo(mode);
        }
    }

    @Test
    public void testGetSyncDisabledMode_TPlus() {
        mGateway.setSdkLevel(Level.T);
        for (var mode : REAL_MODES) {
            mGateway.onCommand(
                    mode.name().toString().toLowerCase(),
                    "device_config get_sync_disabled_for_tests");

            var actualMode = mImpl.getSyncDisabledMode();

            expect.withMessage("getSyncDisabledMode()").that(actualMode).isEqualTo(mode);
        }
    }
}
