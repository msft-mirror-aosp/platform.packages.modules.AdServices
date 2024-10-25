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

import com.android.adservices.shared.testing.AndroidSdk.Level;
import com.android.adservices.shared.testing.AndroidSdk.Range;

public final class HostSideSdkLevelSupportedRuleTest extends AbstractSdkLevelSupportedRuleTestCase {

    public HostSideSdkLevelSupportedRuleTest() {
        super(ConsoleLogger.getInstance());
    }

    @Override
    protected AbstractSdkLevelSupportedRule newRuleForDeviceLevelAndRuleAtLeastLevel(Level level) {
        return newRule(level, level);
    }

    @Override
    protected AbstractSdkLevelSupportedRule newRule(Level ruleLevel, Level deviceLevel) {
        HostSideSdkLevelSupportRule rule = new HostSideSdkLevelSupportRule(ruleLevel);
        mLog.v("newRule(%s, %s): returning %s", ruleLevel, deviceLevel, rule);
        rule.setDeviceLevelSupplier(() -> deviceLevel);
        return rule;
    }

    @Override
    protected AbstractSdkLevelSupportedRule newRule(Range ruleRange, Level deviceLevel) {
        HostSideSdkLevelSupportRule rule = new HostSideSdkLevelSupportRule(ruleRange);
        mLog.v("newRule(%s, %s): returning %s", ruleRange, deviceLevel, rule);
        rule.setDeviceLevelSupplier(() -> deviceLevel);
        return rule;
    }
}
