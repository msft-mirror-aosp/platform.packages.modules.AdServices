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

import com.android.adservices.shared.meta_testing.AbstractFlagsSetterRuleTestCase;
import com.android.adservices.shared.testing.AbstractFlagsSetterRule;
import com.android.adservices.shared.testing.DynamicLogger;
import com.android.adservices.shared.testing.NameValuePair;
import com.android.adservices.shared.testing.device.DeviceGateway;
import com.android.adservices.shared.testing.flags.AbstractFlagsSetterRuleTest.FakeFlagsSetterRule;

import java.util.function.Consumer;

/**
 * Default test case for {@link AbstractFlagsSetterRuleTest} implementations.
 *
 * <p>It uses a {@link FakeFlagsSetterRule bogus rule} so it can be run by IDEs. but subclasses
 * should implement {@link #newRule(DeviceGateway, Consumer)}.
 *
 * <p>Notice that currently there is not Android project to run these side-less tests, so you would
 * need to use either the device-side ({@code AdServicesSharedLibrariesUnitTests}) or host-side
 * ({@code AdServicesSharedLibrariesHostTests}) project:
 *
 * <ul>
 *   <li>{@code atest AdServicesSharedLibrariesUnitTests:AbstractFlagsSetterRuleTest}
 *   <li>{@code atest AdServicesSharedLibrariesHostTests:AbstractFlagsSetterRuleTest}
 * </ul>
 *
 * <p>Notice that when running the host-side tests, you can use the {@code --host} option so it
 * doesn't require a connected device.
 */
public final class AbstractFlagsSetterRuleTest
        extends AbstractFlagsSetterRuleTestCase<FakeFlagsSetterRule> {

    @Override
    protected FakeFlagsSetterRule newRule(
            DeviceGateway deviceGateway, Consumer<NameValuePair> flagsSetter) {
        return new FakeFlagsSetterRule(deviceGateway, flagsSetter);
    }

    public static final class FakeFlagsSetterRule
            extends AbstractFlagsSetterRule<FakeFlagsSetterRule> {

        private final DeviceGateway mDeviceGateway;

        public FakeFlagsSetterRule(
                DeviceGateway deviceGateway, Consumer<NameValuePair> flagsSetter) {
            super(DynamicLogger.getInstance(), flagsSetter);
            mDeviceGateway = deviceGateway;
        }

        @Override
        protected int getDeviceSdk() {
            return mDeviceGateway.getSdkLevel().getLevel();
        }
    }
}
