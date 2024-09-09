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

import com.android.adservices.shared.testing.AbstractRule;
import com.android.adservices.shared.testing.Logger.RealLogger;
import com.android.adservices.shared.testing.TestHelper;
import com.android.adservices.shared.testing.device.DeviceConfig;
import com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.Objects;

/**
 * A rule used to prepare the Flags before a test class is executed.
 *
 * <p>For now it's just setting the {@link SyncDisabledModeForTest}, but in the future it will be
 * extended to perform more actions like clearing all the flags.
 */
public abstract class AbstractFlagsPreparerClassRule extends AbstractRule {

    private final DeviceConfig mDeviceConfig;
    // TODO(b/362977985): it should be more generic and use reversible commands instead.
    private final SyncDisabledModeForTest mMode;

    protected AbstractFlagsPreparerClassRule(
            RealLogger logger, DeviceConfig deviceConfig, SyncDisabledModeForTest mode) {
        super(logger);

        mDeviceConfig = Objects.requireNonNull(deviceConfig, "deviceConfig cannot be null");
        mMode = Objects.requireNonNull(mode, "mode cannot be null");
    }

    @Override
    protected void evaluate(Statement base, Description description) throws Throwable {
        TestHelper.throwIfTest(description);

        SyncDisabledModeForTest modeBefore = null;
        try {
            modeBefore = mDeviceConfig.getSyncDisabledMode();
            mLog.d("Sync mode before %s: %s", getTestName(), modeBefore);
        } catch (Exception e) {
            mLog.e(e, "Failed to get Sync mode before %s", getTestName());
        }

        boolean set = false;
        if (mMode.equals(modeBefore)) {
            mLog.d("Not setting sync mode as it's already %s", mMode);
        } else {
            set = safeSetSyncMode(mMode);
        }

        try {
            base.evaluate();
        } finally {
            if (modeBefore != null && set) {
                safeSetSyncMode(modeBefore);
            }
        }
    }

    private boolean safeSetSyncMode(SyncDisabledModeForTest mode) {
        try {
            mDeviceConfig.setSyncDisabledMode(mode);
            return true;
        } catch (Exception e) {
            mLog.e(e, "Failed to set sync mode to %s", mode);
            return false;
        }
    }
}
