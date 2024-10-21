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

import static com.android.adservices.shared.testing.SdkSandbox.State.DISABLED;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.PERSISTENT;

import com.android.adservices.shared.meta_testing.AbstractFlagsPreparerClassRuleIntegrationTestCase;
import com.android.adservices.shared.meta_testing.DeviceConfigWrapper;
import com.android.adservices.shared.meta_testing.FakeDeviceConfig;
import com.android.adservices.shared.meta_testing.FakeDeviceGateway;
import com.android.adservices.shared.meta_testing.FakeSdkLevelSupportedRule;
import com.android.adservices.shared.meta_testing.FakeSdkSandbox;
import com.android.adservices.shared.meta_testing.SdkSandboxWrapper;
import com.android.adservices.shared.testing.AbstractSdkLevelSupportedRule;
import com.android.adservices.shared.testing.AndroidSdk.Level;
import com.android.adservices.shared.testing.DynamicLogger;
import com.android.adservices.shared.testing.SdkSandbox;
import com.android.adservices.shared.testing.device.DeviceConfig;
import com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest;
import com.android.adservices.shared.testing.flags.AbstractFlagsPreparerClassRuleIntegrationTest.FakeFlagsPreparerClassRule;

/**
 * Default integration test case for {@link AbstractFlagsPreparerClassRule} implementations.
 *
 * <p>It uses a {@link FakeFlagsPreparerClassRule bogus rule} so it can be run by IDEs. but
 * subclasses should implement {@link #newRule(SyncDisabledModeForTest, DeviceConfig)}.
 *
 * <p>Notice that currently there is not Android project to run these side-less tests, so you would
 * need to use either the device-side ({@code AdServicesSharedLibrariesUnitTests}) or host-side
 * ({@code AdServicesSharedLibrariesHostTests}) project:
 *
 * <ul>
 *   <li>{@code atest
 *       AdServicesSharedLibrariesUnitTests:AbstractFlagsPreparerClassRuleIntegrationTest}
 *   <li>{@code atest
 *       AdServicesSharedLibrariesHostTests:AbstractFlagsPreparerClassRuleIntegrationTest}
 * </ul>
 *
 * <p>Notice that when running the host-side tests, you can use the {@code --host} option so it
 * doesn't require a connected device.
 */
public final class AbstractFlagsPreparerClassRuleIntegrationTest
        extends AbstractFlagsPreparerClassRuleIntegrationTestCase<FakeFlagsPreparerClassRule> {

    protected FakeFlagsPreparerClassRule newRule(
            SdkSandboxWrapper sdkSandbox, DeviceConfigWrapper deviceConfig) {
        sdkSandbox.setWrapped(new FakeSdkSandbox().setState(DISABLED));
        // NOTE: ideally initial mode should be NONE, but somehow tradefed / atest set it to
        // PERSISTENT, and this class should mimic the real flag as much as possible
        deviceConfig.setWrapped(new FakeDeviceConfig().setSyncDisabledMode(PERSISTENT));
        return new FakeFlagsPreparerClassRule(sdkSandbox, deviceConfig);
    }

    @Override
    protected AbstractSdkLevelSupportedRule getSdkLevelSupportRule() {
        return new FakeSdkLevelSupportedRule(new FakeDeviceGateway().setSdkLevel(Level.T));
    }

    public static final class FakeFlagsPreparerClassRule
            extends AbstractFlagsPreparerClassRule<FakeFlagsPreparerClassRule> {

        FakeFlagsPreparerClassRule(SdkSandbox sdkSandbox, DeviceConfig deviceConfig) {
            super(DynamicLogger.getInstance(), sdkSandbox, deviceConfig);
        }
    }

}
