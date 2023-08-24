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

import com.android.tradefed.device.ITestDevice;

/** See {@link AbstractAdServicesFlagsSetterRule}. */
public final class AdServicesFlagsSetterRule
        extends AbstractAdServicesFlagsSetterRule<AdServicesFlagsSetterRule> {

    private static AdServicesFlagsSetterRule withDefaultLogcatTags() {
        return new AdServicesFlagsSetterRule().setDefaultLogcatTags();
    }

    /** Factory method that only disables the global kill switch. */
    public static AdServicesFlagsSetterRule forGlobalKillSwitchDisabledTests() {
        return withDefaultLogcatTags().setGlobalKillSwitch(false);
    }

    @Override
    protected int getDeviceSdk() {
        return TestDeviceHelper.getApiLevel();
    }

    public void setDevice(ITestDevice device) {
        TestDeviceHelper.setTestDevice(device);
    }

    private AdServicesFlagsSetterRule() {
        super(
                ConsoleLogger.getInstance(),
                namespace -> new HostSideDeviceConfigHelper(namespace),
                new HostSideSystemPropertiesHelper());
    }
}
