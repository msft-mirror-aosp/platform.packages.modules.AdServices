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

import com.android.adservices.shared.testing.ConsoleLogger;
import com.android.adservices.shared.testing.device.DeviceConfig;
import com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest;
import com.android.adservices.shared.testing.device.DeviceConfigShellCmdImpl;
import com.android.adservices.shared.testing.device.HostSideDeviceGateway;

// TODO(b/370596037): not really working yet because TestDeviceHelper is not set (as this is a
// class rule). Will need to either make it (optionally) a normal rule, or create a custom
// subclass of JarHostTest
/**
 * Default implementation of {@link AbstractFlagsPreparerClassRule} for device-side tests.
 *
 * <p>Note: it's {@code abstract} by design, so it's extended by project-specific classes (like
 * {@code AdServicesHostSideFlagsPreparerClassRule}.
 */
public abstract class HostSideFlagsPreparerClassRule extends AbstractFlagsPreparerClassRule {

    public HostSideFlagsPreparerClassRule() {
        this(
                new DeviceConfigShellCmdImpl(
                        ConsoleLogger.getInstance(), new HostSideDeviceGateway()),
                SyncDisabledModeForTest.PERSISTENT);
    }

    public HostSideFlagsPreparerClassRule(DeviceConfig deviceConfig, SyncDisabledModeForTest mode) {
        super(ConsoleLogger.getInstance(), deviceConfig, mode);
    }
}
