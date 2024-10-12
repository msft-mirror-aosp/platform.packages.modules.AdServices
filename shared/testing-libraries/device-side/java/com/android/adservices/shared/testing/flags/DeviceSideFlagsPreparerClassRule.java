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

import com.android.adservices.shared.testing.AndroidLogger;
import com.android.adservices.shared.testing.device.DeviceConfig;
import com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest;
import com.android.adservices.shared.testing.device.DeviceConfigShellCmdImpl;
import com.android.adservices.shared.testing.device.DeviceGatewayImpl;

/**
 * Default implementation of {@link AbstractFlagsPreparerClassRule} for device-side tests.
 *
 * <p>Note: it's {@code abstract} by design, so it's extended by project-specific classes (like
 * {@code AdServicesFlagsPreparerClassRule}.
 *
 * @param <R> concrete rule class
 */
public abstract class DeviceSideFlagsPreparerClassRule<
                R extends DeviceSideFlagsPreparerClassRule<R>>
        extends AbstractFlagsPreparerClassRule<R> {

    public DeviceSideFlagsPreparerClassRule() {
        this(
                new DeviceConfigShellCmdImpl(AndroidLogger.getInstance(), new DeviceGatewayImpl()),
                SyncDisabledModeForTest.PERSISTENT);
    }

    public DeviceSideFlagsPreparerClassRule(
            DeviceConfig deviceConfig, SyncDisabledModeForTest mode) {
        super(AndroidLogger.getInstance(), deviceConfig, mode);
    }
}
