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
import com.android.adservices.shared.testing.SdkSandbox;
import com.android.adservices.shared.testing.SetSdkSandboxStateAction;
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
 *
 * @param <R> concrete rule class
 */
public abstract class AbstractFlagsPreparerClassRule<R extends AbstractFlagsPreparerClassRule<R>>
        extends ActionBasedRule<R> {

    private final DeviceConfig mDeviceConfig;
    private final SdkSandbox mSdkSandbox;

    protected AbstractFlagsPreparerClassRule(
            RealLogger logger,
            SdkSandbox sdkSandbox,
            DeviceConfig deviceConfig,
            SyncDisabledModeForTest mode) {
        super(logger);

        mDeviceConfig = Objects.requireNonNull(deviceConfig, "deviceConfig cannot be null");
        mSdkSandbox = Objects.requireNonNull(sdkSandbox, "sdkSandbox cannot be null");

        // TODO(b/297085722): remove SafeAction wrapper once tests don't run on R anymore
        addAction(new SafeAction(mLog, new SetSyncModeAction(mLog, deviceConfig, mode)));
    }

    @Override
    protected final void preExecuteActions(Statement base, Description description) {
        TestHelper.throwIfTest(description);
    }

    @Override
    protected void decorateToString(StringBuilder string) {
        super.decorateToString(string);

        string.append(", mSdkSandbox=")
                .append(mSdkSandbox)
                .append(", mDeviceConfig=")
                .append(mDeviceConfig);
    }

    /**
     * Sets or cache the {@code mode}.
     *
     * <p>If the test is running it's set right away and not reset at the end; if the test is not
     * running yet, it's set after the test starts and reset after it finishes.
     */
    public final R setSyncDisabledModeForTest(SyncDisabledModeForTest mode) throws Exception {
        mLog.d("setSyncDisabledModeForTest(%s)", mode);
        Objects.requireNonNull(mode, "mode cannot be null");
        executeOrCache(new SetSyncModeAction(mLog, mDeviceConfig, mode));
        return getSelf();
    }

    /**
     * Sets or cache the {@link SdkSandbox} {@code state}.
     *
     * <p>If the test is running it's set right away and not reset at the end; if the test is not
     * running yet, it's set after the test starts and reset after it finishes.
     */
    public final R setSdkSandboxState(boolean enabled) throws Exception {
        mLog.d("setSdkSandboxState(%b)", enabled);
        executeOrCache(
                new SetSdkSandboxStateAction(
                        mLog,
                        mSdkSandbox,
                        enabled ? SdkSandbox.State.ENABLED : SdkSandbox.State.DISABLED));
        return getSelf();
    }
}
