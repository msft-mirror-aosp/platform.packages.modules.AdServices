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

import com.android.adservices.shared.meta_testing.AbstractFlagsPreparerClassRuleTestCase;
import com.android.adservices.shared.testing.DynamicLogger;
import com.android.adservices.shared.testing.SdkSandbox;
import com.android.adservices.shared.testing.device.DeviceConfig;
import com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest;
import com.android.adservices.shared.testing.flags.AbstractFlagsPreparerClassRuleTest.FakeFlagsPreparerClassRule;

/**
 * Default test case for {@link AbstractFlagsPreparerClassRule} implementations.
 *
 * <p>It uses a {@link FakeFlagsPreparerClassRule bogus rule} so it can be run by IDEs. but
 * subclasses should implement {@link #newRule(DeviceConfig, SyncDisabledModeForTest)}.
 *
 * <p>Notice that currently there is not Android project to run these side-less tests, so you would
 * need to use either the device-side ({@code AdServicesSharedLibrariesUnitTests}) or host-side
 * ({@code AdServicesSharedLibrariesHostTests}) project:
 *
 * <ul>
 *   <li>{@code atest AdServicesSharedLibrariesUnitTests:AbstractFlagsPreparerClassRuleTest}
 *   <li>{@code atest AdServicesSharedLibrariesHostTests:AbstractFlagsPreparerClassRuleTest}
 * </ul>
 *
 * <p>Notice that when running the host-side tests, you can use the {@code --host} option so it
 * doesn't require a connected device.
 */
public final class AbstractFlagsPreparerClassRuleTest
        extends AbstractFlagsPreparerClassRuleTestCase<FakeFlagsPreparerClassRule> {

    @Override
    protected FakeFlagsPreparerClassRule newRule(SdkSandbox sdkSandbox, DeviceConfig deviceConfig) {
        return new FakeFlagsPreparerClassRule(sdkSandbox, deviceConfig);
    }

    public static final class FakeFlagsPreparerClassRule
            extends AbstractFlagsPreparerClassRule<FakeFlagsPreparerClassRule> {

        FakeFlagsPreparerClassRule(SdkSandbox sdkSandbox, DeviceConfig deviceConfig) {
            super(DynamicLogger.getInstance(), sdkSandbox, deviceConfig);
        }
    }
}
