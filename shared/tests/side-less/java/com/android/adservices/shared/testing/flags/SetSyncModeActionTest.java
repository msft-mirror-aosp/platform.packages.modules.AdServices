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
package com.android.adservices.shared.testing.flags;

import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.PERSISTENT;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.UNSUPPORTED;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.UNTIL_REBOOT;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.FakeDeviceConfig;
import com.android.adservices.shared.meta_testing.FakeLogger;
import com.android.adservices.shared.meta_testing.SharedSidelessTestCase;
import com.android.adservices.shared.testing.Logger;

import org.junit.Test;

public final class SetSyncModeActionTest extends SharedSidelessTestCase {

    private final Logger mFakeLogger = new Logger(new FakeLogger(), SetSyncModeActionTest.class);
    private final FakeDeviceConfig mFakeDeviceConfig = new FakeDeviceConfig();

    @Test
    public void testConstructor_null() {
        assertThrows(
                NullPointerException.class,
                () -> new SetSyncModeAction(mFakeLogger, mFakeDeviceConfig, /* mode= */ null));
        assertThrows(
                NullPointerException.class,
                () -> new SetSyncModeAction(mFakeLogger, /* deviceConfig= */ null, UNTIL_REBOOT));
        assertThrows(
                NullPointerException.class,
                () -> new SetSyncModeAction(/* logger= */ null, mFakeDeviceConfig, UNTIL_REBOOT));
    }

    @Test
    public void testConstructor_unsupported() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SetSyncModeAction(mFakeLogger, mFakeDeviceConfig, UNSUPPORTED));
    }

    @Test
    public void testExecuteAndRevert_getPreviousFail() throws Exception {
        mFakeDeviceConfig.setSyncDisabledMode(PERSISTENT);
        SetSyncModeAction action =
                new SetSyncModeAction(mFakeLogger, mFakeDeviceConfig, UNTIL_REBOOT);

        mFakeDeviceConfig.onGetSyncDisabledModeCallback(
                () -> {
                    throw new RuntimeException("D'OH");
                });
        boolean result = action.execute();
        mFakeDeviceConfig.onGetSyncDisabledModeCallback(null); // reset as we'll check state later

        expect.withMessage("execute()").that(result).isTrue();
        expect.withMessage("device config mode after execute")
                .that(mFakeDeviceConfig.getSyncDisabledMode())
                .isEqualTo(UNTIL_REBOOT);

        action.revert();
        expect.withMessage("device config mode after revert")
                .that(mFakeDeviceConfig.getSyncDisabledMode())
                .isEqualTo(UNTIL_REBOOT);
    }

    @Test
    public void testExecuteAndRevert_notChanged() throws Exception {
        mFakeDeviceConfig.setSyncDisabledMode(PERSISTENT);
        SetSyncModeAction action =
                new SetSyncModeAction(mFakeLogger, mFakeDeviceConfig, PERSISTENT);
        mFakeDeviceConfig.onSetSyncDisabledModeCallback(
                () -> {
                    throw new RuntimeException("Y U CALLED ME?");
                });

        boolean result = action.execute();

        expect.withMessage("execute()").that(result).isFalse();
        expect.withMessage("device config mode after execute")
                .that(mFakeDeviceConfig.getSyncDisabledMode())
                .isEqualTo(PERSISTENT);

        action.revert();
        expect.withMessage("device config mode after revert")
                .that(mFakeDeviceConfig.getSyncDisabledMode())
                .isEqualTo(PERSISTENT);
    }

    @Test
    public void testExecuteAndRevert_changed() throws Exception {
        mFakeDeviceConfig.setSyncDisabledMode(PERSISTENT);
        SetSyncModeAction action =
                new SetSyncModeAction(mFakeLogger, mFakeDeviceConfig, UNTIL_REBOOT);

        boolean result = action.execute();

        expect.withMessage("execute()").that(result).isTrue();
        expect.withMessage("device config mode after execute")
                .that(mFakeDeviceConfig.getSyncDisabledMode())
                .isEqualTo(UNTIL_REBOOT);

        action.revert();
        expect.withMessage("device config mode after revert")
                .that(mFakeDeviceConfig.getSyncDisabledMode())
                .isEqualTo(PERSISTENT);
    }

    @Test
    public void testExecuteTwice() throws Exception {
        SetSyncModeAction action =
                new SetSyncModeAction(mFakeLogger, mFakeDeviceConfig, UNTIL_REBOOT);

        boolean result = action.execute();
        expect.withMessage("first call to execute()").that(result).isTrue();

        assertThrows(IllegalStateException.class, () -> action.execute());
    }

    @Test
    public void testRevertBeforeExecute() {
        SetSyncModeAction action =
                new SetSyncModeAction(mFakeLogger, mFakeDeviceConfig, UNTIL_REBOOT);
        assertThrows(IllegalStateException.class, () -> action.revert());
    }

    @Test
    public void testToString() {
        SetSyncModeAction action =
                new SetSyncModeAction(mFakeLogger, mFakeDeviceConfig, UNTIL_REBOOT);

        expect.withMessage("toString()")
                .that(action.toString())
                .isEqualTo("SetSyncModeAction[UNTIL_REBOOT]");
    }
}
