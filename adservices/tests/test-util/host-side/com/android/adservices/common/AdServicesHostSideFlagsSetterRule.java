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
package com.android.adservices.common;

import com.android.adservices.shared.testing.ConsoleLogger;
import com.android.adservices.shared.testing.HostSideDeviceConfigHelper;
import com.android.adservices.shared.testing.HostSideSystemPropertiesHelper;
import com.android.adservices.shared.testing.TestDeviceHelper;

/** See {@link AbstractAdServicesFlagsSetterRule}. */
public final class AdServicesHostSideFlagsSetterRule
        extends AbstractAdServicesFlagsSetterRule<AdServicesHostSideFlagsSetterRule> {

    /** Factory method that only sets the AdServices logcat tags. */
    private static AdServicesHostSideFlagsSetterRule withAllLogcatTags() {
        return new AdServicesHostSideFlagsSetterRule().setAllLogcatTags();
    }

    /** Factory method that disables the global kill switch. */
    public static AdServicesHostSideFlagsSetterRule forGlobalKillSwitchDisabledTests() {
        return withAllLogcatTags().setGlobalKillSwitch(false);
    }

    /** Factory method that enables the compat mode flags (if needed). */
    public static AdServicesHostSideFlagsSetterRule forCompatModeEnabledTests() {
        return forGlobalKillSwitchDisabledTests().setCompatModeFlags();
    }

    @Override
    protected int getDeviceSdk() {
        return TestDeviceHelper.getApiLevel();
    }

    private AdServicesHostSideFlagsSetterRule() {
        super(
                ConsoleLogger.getInstance(),
                namespace -> new HostSideDeviceConfigHelper(namespace),
                HostSideSystemPropertiesHelper.getInstance());
    }
}
