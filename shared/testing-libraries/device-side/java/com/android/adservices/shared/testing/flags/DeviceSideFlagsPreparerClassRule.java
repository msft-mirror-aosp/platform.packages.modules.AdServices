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
import com.android.adservices.shared.testing.SdkSandbox;
import com.android.adservices.shared.testing.SdkSandboxShellCmdImpl;
import com.android.adservices.shared.testing.device.DeviceConfig;
import com.android.adservices.shared.testing.device.DeviceConfigShellCmdImpl;
import com.android.adservices.shared.testing.device.DeviceGateway;
import com.android.adservices.shared.testing.device.DeviceGatewayImpl;

import com.google.common.annotations.VisibleForTesting;

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

    /** Default constructor, uses default {@link DeviceConfig} and {@link SdkSandbox}. */
    protected DeviceSideFlagsPreparerClassRule() {
        this(new DeviceGatewayImpl());
    }

    private DeviceSideFlagsPreparerClassRule(DeviceGateway deviceGateway) {
        this(
                new SdkSandboxShellCmdImpl(AndroidLogger.getInstance(), deviceGateway),
                // TODO(b/373470077): use custom client-side DeviceConfig implementation
                new DeviceConfigShellCmdImpl(AndroidLogger.getInstance(), deviceGateway));
    }

    @VisibleForTesting
    protected DeviceSideFlagsPreparerClassRule(SdkSandbox sdkSandbox, DeviceConfig deviceConfig) {
        super(AndroidLogger.getInstance(), sdkSandbox, deviceConfig);
    }
}
