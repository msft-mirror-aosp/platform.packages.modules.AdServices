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

package com.android.adservices.service;

import static com.android.adservices.service.CommonFlagsConstants.KEY_ADSERVICES_SHELL_COMMAND_ENABLED;

import android.os.SystemProperties;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Common debug flags shared between system-server and service that are only used for development /
 * testing purposes.
 *
 * <p>They're never pushed to devices (through `DeviceConfig`) and must be manually set by the
 * developer (or automatically set by the test), so they're implemented using System Properties.
 *
 * <p><b>NOTE: </b> the value of these flags should be such that the behavior they're changing is
 * not changed or the feature they're guarding is disabled, so usually their default value should be
 * {@code false}.
 *
 * @hide
 */
@SuppressWarnings("AvoidSystemPropertiesUsage") // Helper / infra class
public abstract class CommonDebugFlags {
    private static final String SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX = "debug.adservices.";

    @VisibleForTesting static final boolean DEFAULT_ADSERVICES_SHELL_COMMAND_ENABLED = false;

    public boolean getAdServicesShellCommandEnabled() {
        return getDebugFlag(
                KEY_ADSERVICES_SHELL_COMMAND_ENABLED, DEFAULT_ADSERVICES_SHELL_COMMAND_ENABLED);
    }

    static boolean getDebugFlag(String name, boolean defaultValue) {
        return SystemProperties.getBoolean(getSystemPropertyName(name), defaultValue);
    }

    private static String getSystemPropertyName(String key) {
        return SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX + key;
    }
}
