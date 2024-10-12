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

import com.android.adservices.shared.testing.ActionBasedRule;
import com.android.adservices.shared.testing.Logger.RealLogger;
import com.android.adservices.shared.testing.SafeAction;
import com.android.adservices.shared.testing.TestHelper;
import com.android.adservices.shared.testing.device.DeviceConfig;
import com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * A rule used to prepare the Flags before a test class is executed.
 *
 * <p>For now it's just setting the {@link SyncDisabledModeForTest}, but in the future it will be
 * extended to perform more actions like clearing all the flags.
 *
 * @param <R> concrete rule class
 */
public abstract class AbstractFlagsPreparerClassRule<R extends AbstractFlagsPreparerClassRule<R>>
        extends ActionBasedRule<R> {

    protected AbstractFlagsPreparerClassRule(
            RealLogger logger, DeviceConfig deviceConfig, SyncDisabledModeForTest mode) {
        super(logger);
        // TODO(b/297085722): remove SafeAction wrapper once tests don't run on R anymore
        addAction(new SafeAction(mLog, new SetSyncModeAction(mLog, deviceConfig, mode)));
    }

    @Override
    protected final void preExecuteActions(Statement base, Description description) {
        TestHelper.throwIfTest(description);
    }
}
