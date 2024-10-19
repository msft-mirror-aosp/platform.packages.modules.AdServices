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
package com.android.adservices.shared.meta_testing;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.adservices.shared.testing.device.DeviceConfig;
import com.android.adservices.shared.testing.device.DeviceConfigShellCmdImpl;
import com.android.adservices.shared.testing.device.DeviceGateway;

/** Integration test for {@link DeviceConfig} implemented by {@link DeviceConfigShellCmdImpl}. */
public abstract class DeviceConfigShellCmdImplIntegrationTestCase
        extends DeviceConfigIntegrationTestCase<DeviceConfigShellCmdImpl> {

    protected abstract DeviceGateway getDeviceGateway();

    @Override
    protected final DeviceConfigShellCmdImpl newFixture() {
        DeviceGateway gateway = getDeviceGateway();
        assertWithMessage("getDeviceGateway()").that(gateway).isNotNull();
        return new DeviceConfigShellCmdImpl(mRealLogger, gateway);
    }
}
