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

import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.Nullable;
import com.android.adservices.shared.testing.device.DeviceConfig;
import com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest;

import com.google.common.annotations.VisibleForTesting;

import java.util.Objects;

/** Action used to set {@code DeviceConfig}'s {@link SyncDisabledModeForTest}. */
public final class SetSyncModeAction extends DeviceConfigAction {

    @Nullable private SyncDisabledModeForTest mPreviousMode;

    private final SyncDisabledModeForTest mMode;

    /** Useless javadoc to make checkstyle happy... */
    public SetSyncModeAction(
            Logger logger, DeviceConfig deviceConfig, SyncDisabledModeForTest mode) {
        super(logger, deviceConfig);
        mMode = Objects.requireNonNull(mode, "mode cannot be null");
        if (!mode.isSettable()) {
            throw new IllegalArgumentException("invalid mode: " + mode);
        }
    }

    /** Gets the mode that will be set by the action. */
    public SyncDisabledModeForTest getMode() {
        return mMode;
    }

    @Override
    protected boolean onExecuteLocked() throws Exception {
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
        if (UNSUPPORTED.equals(mPreviousMode)) {
            mLog.d(
                    "%s: not setting sync mode (%s) because device_config doesn't support it",
                    this, mMode);
            mPreviousMode = null;
            return false;
        }

        if (DISABLED_SOMEHOW.equals(mPreviousMode)) {
            // We don't know which mode it is, but most like is PERSISTENT...
            mPreviousMode = PERSISTENT;
        }

        mDeviceConfig.setSyncDisabledMode(mMode);

        return mPreviousMode != null && mPreviousMode.isSettable();
    }

    @Override
    protected void onRevertLocked() throws Exception {
        if (mPreviousMode == null || !mPreviousMode.isSettable()) {
            throw new IllegalStateException("should not have been called when it didn't change");
        }
        mDeviceConfig.setSyncDisabledMode(mPreviousMode);
    }

    @Override
    protected void onResetLocked() {
        mPreviousMode = null;
    }

    @VisibleForTesting
    @Nullable
    SyncDisabledModeForTest getPreviousMode() {
        return mPreviousMode;
    }

    @Override
    public String toString() {
        return "SetSyncModeAction[mode=" + mMode + ", previousMode=" + mPreviousMode + ']';
    }
}
