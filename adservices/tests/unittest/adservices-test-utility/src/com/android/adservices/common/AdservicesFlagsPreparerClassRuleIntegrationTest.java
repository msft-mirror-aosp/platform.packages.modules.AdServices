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
package com.android.adservices.common;

import com.android.adservices.shared.meta_testing.AbstractFlagsPreparerClassRuleIntegrationTestCase;
import com.android.adservices.shared.testing.SdkSandbox;
import com.android.adservices.shared.testing.SdkSandboxShellCmdImpl;
import com.android.adservices.shared.testing.device.DeviceConfig;
import com.android.adservices.shared.testing.device.DeviceConfigShellCmdImpl;
import com.android.adservices.shared.testing.device.DeviceGatewayImpl;

public final class AdservicesFlagsPreparerClassRuleIntegrationTest
        extends AbstractFlagsPreparerClassRuleIntegrationTestCase<
                AdServicesFlagsPreparerClassRule> {

    private final DeviceGatewayImpl mDeviceGateway = new DeviceGatewayImpl();

    @Override
    protected AdServicesFlagsPreparerClassRule newRule() {
        return new AdServicesFlagsPreparerClassRule();
    }

    @Override
    protected SdkSandbox newSdkSandbox() {
        return new SdkSandboxShellCmdImpl(mRealLogger, mDeviceGateway);
    }

    @Override
    protected DeviceConfig newDeviceConfig() {
        return new DeviceConfigShellCmdImpl(mRealLogger, mDeviceGateway);
    }
}
