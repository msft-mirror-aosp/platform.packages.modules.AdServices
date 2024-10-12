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
import com.android.adservices.shared.testing.Action;
import com.android.adservices.shared.testing.Logger.RealLogger;
import com.android.adservices.shared.testing.SafeAction;
import com.android.adservices.shared.testing.TestHelper;
import com.android.adservices.shared.testing.device.DeviceConfig;
import com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest;

import com.google.common.annotations.VisibleForTesting;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * A rule used to prepare the Flags before a test class is executed.
 *
 * <p>For now it's just setting the {@link SyncDisabledModeForTest}, but in the future it will be
 * extended to perform more actions like clearing all the flags.
 */
public abstract class AbstractFlagsPreparerClassRule extends AbstractRule {

    // TODO(b/362977985): it should be more generic and have a list of actions instead, so it could
    // create other actions based on annotations
    private final Action mSetSyncModeAction;

    protected AbstractFlagsPreparerClassRule(
            RealLogger logger, DeviceConfig deviceConfig, SyncDisabledModeForTest mode) {
        super(logger);
        mSetSyncModeAction = new SafeAction(mLog, new SetSyncModeAction(mLog, deviceConfig, mode));
    }

    @VisibleForTesting
    @Override
    protected final void evaluate(Statement base, Description description) throws Throwable {
        TestHelper.throwIfTest(description);

        mSetSyncModeAction.execute();
        try {
            base.evaluate();
        } finally {
            mSetSyncModeAction.revert();
        }
    }
}
