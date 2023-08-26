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

import static com.android.adservices.common.TestDeviceHelper.runShellCommand;

import com.android.adservices.common.DeviceConfigHelper.SyncDisabledModeForTest;

import java.util.Objects;

/** Host-side implementation of {@link DeviceConfigHelper.Interface}. */
final class HostSideDeviceConfigHelper implements DeviceConfigHelper.Interface {

    private static final Logger sLogger =
            new Logger(ConsoleLogger.getInstance(), HostSideDeviceConfigHelper.class);

    private final String mNamespace;

    HostSideDeviceConfigHelper(String namespace) {
        mNamespace = Objects.requireNonNull(namespace);
    }

    @Override
    public void setSyncDisabledModeForTest(SyncDisabledModeForTest mode) {
        String value = mode.name().toLowerCase();
        sLogger.v("SyncDisabledModeForTest(%s)", value);
        runShellCommand("device_config set_sync_disabled_for_test %s", value);
    }

    @Override
    public String get(String name, String defaultValue) {
        String value = runShellCommand("device_config get %s %s", mNamespace, name).trim();
        sLogger.v(
                "get(%s, %s): raw value is '%s' (is null: %b)",
                name, defaultValue, value, value == null);
        if (!value.equals("null")) {
            return value;
        }
        // "null" could mean the value doesn't exist, or it's the string "null", so we need to check
        // them
        String allFlags = runShellCommand("device_config list %s", mNamespace);
        for (String line : allFlags.split("\n")) {
            if (line.equals(name + "=null")) {
                sLogger.v("Value of flag %s is indeed \"%s\"", name, value);
                return value;
            }
        }
        return defaultValue;
    }

    @Override
    public void set(String name, String value) {
        runShellCommand("device_config put %s %s %s", mNamespace, name, value);
    }

    @Override
    public void delete(String name) {
        runShellCommand("device_config delete %s %s", mNamespace, name);
    }

    @Override
    public String dump() {
        return runShellCommand("device_config list%s", mNamespace).trim();
    }

    @Override
    public String toString() {
        return HostSideDeviceConfigHelper.class.getSimpleName();
    }
}
