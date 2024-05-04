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

/**
 * Defines constants used by {@code DebugFlags}.
 *
 * <p><b>NOTE: </b>cannot have any dependency on Android or other AdServices code.
 */
public final class DebugFlagsConstants {
    private DebugFlagsConstants() {
        throw new UnsupportedOperationException("Contains only static constants");
    }

    // Consent Manager ota debug mode keys.
    public static final String KEY_CONSENT_MANAGER_OTA_DEBUG_MODE =
            "consent_manager_ota_debug_mode";

    /** Key for feature flagging app signals CLI. */
    public static final String KEY_PROTECTED_APP_SIGNALS_CLI_ENABLED =
            "fledge_is_app_signals_cli_enabled";

    /** Key for feature flagging adselection CLI. */
    public static final String KEY_AD_SELECTION_CLI_ENABLED = "fledge_is_ad_selection_cli_enabled";
}
