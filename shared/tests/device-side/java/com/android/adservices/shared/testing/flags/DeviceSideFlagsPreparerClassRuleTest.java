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

import com.android.adservices.shared.meta_testing.DeviceSideFlagsPreparerClassRuleTestCase;
import com.android.adservices.shared.testing.SdkSandbox;
import com.android.adservices.shared.testing.device.DeviceConfig;
import com.android.adservices.shared.testing.flags.DeviceSideFlagsPreparerClassRuleTest.ConcreteDeviceSideFlagsPreparerClassRule;

public final class DeviceSideFlagsPreparerClassRuleTest
        extends DeviceSideFlagsPreparerClassRuleTestCase<ConcreteDeviceSideFlagsPreparerClassRule> {

    @Override
    protected ConcreteDeviceSideFlagsPreparerClassRule newRule() {
        return new ConcreteDeviceSideFlagsPreparerClassRule();
    }

    @Override
    protected ConcreteDeviceSideFlagsPreparerClassRule newRule(
            SdkSandbox sdkSandbox, DeviceConfig deviceConfig) {
        return new ConcreteDeviceSideFlagsPreparerClassRule(sdkSandbox, deviceConfig);
    }

    public static final class ConcreteDeviceSideFlagsPreparerClassRule
            extends DeviceSideFlagsPreparerClassRule<ConcreteDeviceSideFlagsPreparerClassRule> {

        public ConcreteDeviceSideFlagsPreparerClassRule() {
            super();
        }

        public ConcreteDeviceSideFlagsPreparerClassRule(
                SdkSandbox sdkSandbox, DeviceConfig deviceConfig) {
            super(sdkSandbox, deviceConfig);
        }
    }
}
