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
package com.android.adservices.shared.testing;

import android.platform.test.annotations.DisabledOnRavenwood;

import com.android.adservices.shared.meta_testing.DeviceConfigShellCmdImplIntegrationTestCase;
import com.android.adservices.shared.testing.device.DeviceGateway;
import com.android.adservices.shared.testing.device.DeviceGatewayImpl;

@DisabledOnRavenwood(reason = "Uses shell command")
public final class DeviceSideDeviceConfigShellCmdImplIntegrationTest
        extends DeviceConfigShellCmdImplIntegrationTestCase {

    @Override
    protected AbstractSdkLevelSupportedRule getSdkLevelSupportRule() {
        return SdkLevelSupportRule.forAnyLevel();
    }

    @Override
    protected DeviceGateway getDeviceGateway() {
        return new DeviceGatewayImpl();
    }
}
