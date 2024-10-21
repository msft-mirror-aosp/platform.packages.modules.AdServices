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

import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.DISABLED_SOMEHOW;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.PERSISTENT;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.UNSUPPORTED;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.UNTIL_REBOOT;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.FakeDeviceConfig;
import com.android.adservices.shared.meta_testing.SharedSidelessTestCase;
import com.android.adservices.shared.testing.EqualsTester;
import com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest;

import org.junit.Test;

public final class SetSyncModeActionTest extends SharedSidelessTestCase {

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
    public void testConstructor_notSettable() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SetSyncModeAction(mFakeLogger, mFakeDeviceConfig, UNSUPPORTED));

        assertThrows(
                IllegalArgumentException.class,
                () -> new SetSyncModeAction(mFakeLogger, mFakeDeviceConfig, DISABLED_SOMEHOW));
    }

    @Test
    public void testGetMode() {
        for (SyncDisabledModeForTest mode : SyncDisabledModeForTest.values()) {
            if (mode.isSettable()) {
                var action = new SetSyncModeAction(mFakeLogger, mFakeDeviceConfig, mode);
                expect.withMessage("getMode()").that(action.getMode()).isEqualTo(mode);
            }
        }
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

        expect.withMessage("execute()").that(result).isFalse();
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
    public void testExecuteAndRevert_previousReturnNull() throws Exception {
        mFakeDeviceConfig.setSyncDisabledMode(null);
        SetSyncModeAction action =
                new SetSyncModeAction(mFakeLogger, mFakeDeviceConfig, PERSISTENT);

        boolean result = action.execute();

        expect.withMessage("execute()").that(result).isFalse();
        expect.withMessage("device config mode after execute")
                .that(mFakeDeviceConfig.getSyncDisabledMode())
                .isEqualTo(PERSISTENT);

        // Should not call it as it was null before
        mFakeDeviceConfig.onSetSyncDisabledModeCallback(
                () -> {
                    throw new RuntimeException("Y U CALLED ME?");
                });
        action.revert();
        expect.withMessage("device config mode after revert")
                .that(mFakeDeviceConfig.getSyncDisabledMode())
                .isEqualTo(PERSISTENT);
    }

    @Test
    public void testExecuteAndRevert_previousReturnUnsupported() throws Exception {
        mFakeDeviceConfig.setSyncDisabledMode(UNSUPPORTED);
        mFakeDeviceConfig.onSetSyncDisabledModeCallback(
                () -> {
                    throw new RuntimeException("Y U CALLED ME?");
                });
        SetSyncModeAction action =
                new SetSyncModeAction(mFakeLogger, mFakeDeviceConfig, PERSISTENT);

        boolean result = action.execute();

        expect.withMessage("execute()").that(result).isFalse();
        expect.withMessage("device config mode after execute")
                .that(mFakeDeviceConfig.getSyncDisabledMode())
                .isEqualTo(UNSUPPORTED);
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
    public void testExecuteAndRevert_changedFromDisabledSomehow() throws Exception {
        mFakeDeviceConfig.setSyncDisabledMode(DISABLED_SOMEHOW);
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
    public void testOnRevertWhenNotExecuted() throws Exception {
        // This is kind of an "overkill" test, as onRevert() should not be called directly, but it
        // doesn't hurt to be sure...
        mFakeDeviceConfig.setSyncDisabledMode(PERSISTENT);
        SetSyncModeAction action =
                new SetSyncModeAction(mFakeLogger, mFakeDeviceConfig, PERSISTENT);

        action.execute();

        assertThrows(IllegalStateException.class, () -> action.onRevertLocked());
    }

    @Test
    public void testOnReset() throws Exception {
        mFakeDeviceConfig.setSyncDisabledMode(PERSISTENT);
        SetSyncModeAction action =
                new SetSyncModeAction(mFakeLogger, mFakeDeviceConfig, UNTIL_REBOOT);
        expect.withMessage("previous mode initially ").that(action.getPreviousMode()).isNull();
        action.execute();
        expect.withMessage("previous mode after execute")
                .that(action.getPreviousMode())
                .isEqualTo(PERSISTENT);

        action.onResetLocked();

        expect.withMessage("previous mode before reset").that(action.getPreviousMode()).isNull();
    }

    @Test
    public void testEqualsAndHashCode() {
        var baseline = new SetSyncModeAction(mFakeLogger, mFakeDeviceConfig, UNTIL_REBOOT);
        var different = new SetSyncModeAction(mFakeLogger, mFakeDeviceConfig, UNTIL_REBOOT);
        var et = new EqualsTester(expect);

        et.expectObjectsAreNotEqual(baseline, different);
    }

    @Test
    public void testToString() throws Exception {
        SetSyncModeAction action =
                new SetSyncModeAction(mFakeLogger, mFakeDeviceConfig, UNTIL_REBOOT);

        expect.withMessage("toString() before execute")
                .that(action.toString())
                .isEqualTo("SetSyncModeAction[mode=UNTIL_REBOOT, previousMode=null]");

        action.execute();

        expect.withMessage("toString() after execute")
                .that(action.toString())
                .isEqualTo("SetSyncModeAction[mode=UNTIL_REBOOT, previousMode=NONE]");
    }
}
