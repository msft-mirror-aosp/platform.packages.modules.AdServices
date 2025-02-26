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
package com.android.adservices.shared.testing.device;

import com.android.adservices.shared.meta_testing.DeviceConfigIntegrationTestCase;
import com.android.adservices.shared.meta_testing.FakeDeviceConfig;
import com.android.adservices.shared.meta_testing.FakeDeviceGateway;
import com.android.adservices.shared.meta_testing.FakeSdkLevelSupportedRule;
import com.android.adservices.shared.testing.AbstractSdkLevelSupportedRule;
import com.android.adservices.shared.testing.AndroidSdk.Level;

/**
 * Default test case for {@link DeviceConfigIntegrationTestCase} implementations.
 *
 * <p>Notice that currently there is not Android project to run these side-less tests, so you would
 * need to use either the device-side ({@code AdServicesSharedLibrariesUnitTests}) or host-side
 * ({@code AdServicesSharedLibrariesHostTests}) project:
 *
 * <ul>
 *   <li>{@code atest AdServicesSharedLibrariesUnitTests:FakeDeviceConfigIntegrationTest}
 *   <li>{@code atest AdServicesSharedLibrariesHostTests:FakeDeviceConfigIntegrationTest}
 * </ul>
 *
 * <p>Notice that when running the host-side tests, you can use the {@code --host} option so it
 * doesn't require a connected device.
 */
public final class FakeDeviceConfigIntegrationTest
        extends DeviceConfigIntegrationTestCase<FakeDeviceConfig> {

    @Override
    protected AbstractSdkLevelSupportedRule getSdkLevelSupportRule() {
        return new FakeSdkLevelSupportedRule(new FakeDeviceGateway().setSdkLevel(Level.T));
    }

    @Override
    protected FakeDeviceConfig newFixture() {
        return new FakeDeviceConfig();
    }
}
