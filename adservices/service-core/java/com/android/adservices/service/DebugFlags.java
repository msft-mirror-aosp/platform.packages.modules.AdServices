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

import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_MANAGER_OTA_DEBUG_MODE;
import static com.android.adservices.service.Flags.CONSENT_MANAGER_DEBUG_MODE;
import static com.android.adservices.service.Flags.CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE;
import static com.android.adservices.service.Flags.CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.Flags.CONSENT_NOTIFIED_DEBUG_MODE;
import static com.android.adservices.service.Flags.DEFAULT_CONSENT_MANAGER_OTA_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_NOTIFIED_DEBUG_MODE;
import static com.android.adservices.service.PhFlags.getSystemPropertyName;

import android.os.SystemProperties;

/** Debug Flags Implementation that delegates to System Properties. */
public final class DebugFlags {
    private static final DebugFlags sInstance = new DebugFlags();

    static DebugFlags getInstance() {
        return sInstance;
    }

    private DebugFlags() {}

    public boolean getConsentNotificationDebugMode() {
        return getDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE, CONSENT_NOTIFICATION_DEBUG_MODE);
    }

    public boolean getConsentNotifiedDebugMode() {
        return getDebugFlag(KEY_CONSENT_NOTIFIED_DEBUG_MODE, CONSENT_NOTIFIED_DEBUG_MODE);
    }

    public boolean getConsentNotificationActivityDebugMode() {
        return getDebugFlag(
                KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE,
                CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE);
    }

    public boolean getConsentManagerDebugMode() {
        return getDebugFlag(KEY_CONSENT_MANAGER_DEBUG_MODE, CONSENT_MANAGER_DEBUG_MODE);
    }

    public boolean getConsentManagerOTADebugMode() {
        return getDebugFlag(
                KEY_CONSENT_MANAGER_OTA_DEBUG_MODE, DEFAULT_CONSENT_MANAGER_OTA_DEBUG_MODE);
    }

    private boolean getDebugFlag(String name, boolean defaultValue) {
        return SystemProperties.getBoolean(getSystemPropertyName(name), defaultValue);
    }
}
