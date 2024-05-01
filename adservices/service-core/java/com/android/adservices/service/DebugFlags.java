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
import static com.android.adservices.service.DebugFlagsConstants.KEY_PROTECTED_APP_SIGNALS_CLI_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_AD_SELECTION_CLI_ENABLED;
import static com.android.adservices.service.Flags.CONSENT_MANAGER_DEBUG_MODE;
import static com.android.adservices.service.Flags.CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE;
import static com.android.adservices.service.Flags.CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.Flags.CONSENT_NOTIFIED_DEBUG_MODE;
import static com.android.adservices.service.Flags.DEFAULT_CONSENT_MANAGER_OTA_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_NOTIFIED_DEBUG_MODE;

import androidx.annotation.VisibleForTesting;

/**
 * Flags that are only used for development / testing purposes.
 *
 * <p>They're never pushed to devices (through `DeviceConfig`) and must be manually set by the
 * developer (or automatically set by the test), so they're implemented using System Properties.
 *
 * <p><b>NOTE: </b> the value of these flags should be such that the behavior they're changing is
 * not changed or the feature they're guarding is disabled, so usually their default value should be
 * {@code false}.
 */
public final class DebugFlags extends CommonDebugFlags {
    private static final DebugFlags sInstance = new DebugFlags();

    /** Default for if FLEDGE app signals CLI is enabled. */
    @VisibleForTesting static final boolean DEFAULT_PROTECTED_APP_SIGNALS_CLI_ENABLED = false;

    /** Default for if FLEDGE ad selection CLI is enabled. */
    @VisibleForTesting static final boolean DEFAULT_AD_SELECTION_CLI_ENABLED = false;

    public static DebugFlags getInstance() {
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

    public boolean getProtectedAppSignalsCommandsEnabled() {
        return getDebugFlag(
                KEY_PROTECTED_APP_SIGNALS_CLI_ENABLED, DEFAULT_PROTECTED_APP_SIGNALS_CLI_ENABLED);
    }

    public boolean getAdSelectionCommandsEnabled() {
        return getDebugFlag(KEY_AD_SELECTION_CLI_ENABLED, DEFAULT_AD_SELECTION_CLI_ENABLED);
    }
}
