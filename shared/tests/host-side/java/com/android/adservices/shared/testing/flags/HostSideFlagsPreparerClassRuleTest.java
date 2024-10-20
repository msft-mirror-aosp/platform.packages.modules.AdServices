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
import com.android.adservices.shared.testing.SdkSandbox;
import com.android.adservices.shared.testing.device.DeviceConfig;
import com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest;
import com.android.adservices.shared.testing.flags.HostSideFlagsPreparerClassRuleTest.ConcreteHostSideFlagsPreparerClassRule;

public final class HostSideFlagsPreparerClassRuleTest
        extends AbstractFlagsPreparerClassRuleTestCase<ConcreteHostSideFlagsPreparerClassRule> {

    @Override
    protected ConcreteHostSideFlagsPreparerClassRule newRule(
            SdkSandbox sdkSandbox, DeviceConfig deviceConfig, SyncDisabledModeForTest syncMode) {
        return new ConcreteHostSideFlagsPreparerClassRule(sdkSandbox, deviceConfig, syncMode);
    }

    public static final class ConcreteHostSideFlagsPreparerClassRule
            extends HostSideFlagsPreparerClassRule<ConcreteHostSideFlagsPreparerClassRule> {

        public ConcreteHostSideFlagsPreparerClassRule(
                SdkSandbox sdkSandbox,
                DeviceConfig deviceConfig,
                SyncDisabledModeForTest syncMode) {
            super(sdkSandbox, deviceConfig, syncMode);
        }
    }
}
