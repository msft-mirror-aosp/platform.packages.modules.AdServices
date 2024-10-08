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

import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.UNSUPPORTED;

import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.device.DeviceConfig;
import com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest;

import java.util.Objects;

/** Action used to set {@code DeviceConfig}'s {@link SyncDisabledModeForTest}. */
public final class SetSyncModeAction extends DeviceConfigAction {

    private SyncDisabledModeForTest mPreviousMode;
    private final SyncDisabledModeForTest mMode;

    /** Default constructor. */
    public SetSyncModeAction(
            Logger logger, DeviceConfig deviceConfig, SyncDisabledModeForTest mode) {
        super(logger, deviceConfig);
        mMode = Objects.requireNonNull(mode, "mode cannot be null");
        if (mode.equals(UNSUPPORTED)) {
            throw new IllegalArgumentException("invalid mode: " + mode);
        }
    }

    @Override
    public boolean onExecute() throws Exception {
        try {
            mPreviousMode = mDeviceConfig.getSyncDisabledMode();
        } catch (Exception e) {
            mLog.e(e, "%s: failed to get sync mode; it won't be restored at the end", this);
        }

        if (mMode.equals(mPreviousMode)) {
            mLog.d("%s: not setting sync mode when it's already %s", this, mMode);
            mPreviousMode = null;
            return false;
        }

        mDeviceConfig.setSyncDisabledMode(mMode);

        return true;
    }

    @Override
    public void onRevert() throws Exception {
        if (mPreviousMode == null) {
            mLog.d("%s.revert(): ignoring when it didn't change", this);
            return;
        }
        mDeviceConfig.setSyncDisabledMode(mPreviousMode);
    }

    @Override
    public String toString() {
        return "SetSyncModeAction[" + mMode + ']';
    }
}
