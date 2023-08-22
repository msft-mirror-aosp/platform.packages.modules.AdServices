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

import static com.android.adservices.common.TestDeviceHelper.getTestDevice;
import static com.android.adservices.common.TestDeviceHelper.runShellCommand;

import com.android.tradefed.device.DeviceNotAvailableException;

/** Host-side implementation of {@link SystemPropertiesHelper.Interface}. */
final class HostSideSystemPropertiesHelper implements SystemPropertiesHelper.Interface {

    private static final Logger sLogger =
            new Logger(ConsoleLogger.getInstance(), HostSideSystemPropertiesHelper.class);

    @Override
    public String get(String name) throws DeviceNotAvailableException {
        return getTestDevice().getProperty(name);
    }

    @Override
    public void set(String name, String value) throws DeviceNotAvailableException {
        sLogger.v("set(%s, %s)", name, value);
        getTestDevice().setProperty(name, value);
    }

    @Override
    public String dumpSystemProperties() throws DeviceNotAvailableException {
        return runShellCommand("getprop").trim();
    }

    @Override
    public String toString() {
        return HostSideSystemPropertiesHelper.class.getSimpleName();
    }
}
