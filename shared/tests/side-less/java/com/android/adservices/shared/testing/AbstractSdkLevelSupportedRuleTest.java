/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.adservices.shared.testing;

import com.android.adservices.shared.meta_testing.FakeDeviceGateway;
import com.android.adservices.shared.meta_testing.FakeSdkLevelSupportedRule;
import com.android.adservices.shared.testing.AndroidSdk.Level;
import com.android.adservices.shared.testing.AndroidSdk.Range;

/**
 * Default test case for {@link AbstractSdkLevelSupportedRule} implementations.
 *
 * <p>By default, it uses a {@link FakeSdkLevelSupportedRule bogus rule} so it can be run by IDEs,
 * but subclasses should implement {@link #newRule(Level, Level)} and {@link
 * #newRuleForDeviceLevelAndRuleAtLeastLevel(Level)}.
 *
 * <p>Notice that currently there is not an Android project to run these side-less tests, so you
 * need to use either the device-side ({@code AdServicesSharedLibrariesUnitTests}) or host-side
 * ({@code AdServicesSharedLibrariesHostTests}) project:
 *
 * <ul>
 *   <li>{@code atest AdServicesSharedLibrariesUnitTests:AbstractSdkLevelSupportedRuleTest}
 *   <li>{@code atest AdServicesSharedLibrariesHostTests:AbstractSdkLevelSupportedRuleTest}
 * </ul>
 *
 * <p>Similarly, you could use run the "side-specific" test as well:
 *
 * <ul>
 *   <li>{@code atest AdServicesSharedLibrariesUnitTests:SdkLevelSupportedRuleTest}
 *   <li>{@code atest AdServicesSharedLibrariesHostTests:HostSideSdkLevelSupportedRuleTest}
 * </ul>
 *
 * <p>Notice that when running the host-side tests, you can use the {@code --host} option so it
 * doesn't require a connected device.
 */
public final class AbstractSdkLevelSupportedRuleTest extends AbstractSdkLevelSupportedRuleTestCase {

    @Override
    protected AbstractSdkLevelSupportedRule newRule(Range ruleRange, @Nullable Level deviceLevel) {
        FakeDeviceGateway fakeDeviceGateway = new FakeDeviceGateway();
        if (deviceLevel != null) {
            fakeDeviceGateway.setSdkLevel(deviceLevel);
        }
        return new FakeSdkLevelSupportedRule(
                DynamicLogger.getInstance(), fakeDeviceGateway, ruleRange);
    }
}
