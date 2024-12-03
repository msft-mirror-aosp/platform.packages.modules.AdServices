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

    // Consent Notification debug mode keys.
    public static final String KEY_CONSENT_NOTIFICATION_DEBUG_MODE =
            "consent_notification_debug_mode";

    public static final String KEY_CONSENT_NOTIFIED_DEBUG_MODE = "consent_notified_debug_mode";

    // Consent notification activity debug mode keys.
    public static final String KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE =
            "consent_notification_activity_debug_mode";

    // Consent Manager debug mode keys.
    public static final String KEY_CONSENT_MANAGER_DEBUG_MODE = "consent_manager_debug_mode";

    // Consent Manager ota debug mode keys.
    public static final String KEY_CONSENT_MANAGER_OTA_DEBUG_MODE =
            "consent_manager_ota_debug_mode";

    /** Key for feature flagging app signals CLI. */
    public static final String KEY_PROTECTED_APP_SIGNALS_CLI_ENABLED =
            "fledge_is_app_signals_cli_enabled";

    /** Key for feature flagging the broadcast after encoder logic registration (for testing). */
    public static final String
            KEY_PROTECTED_APP_SIGNALS_ENCODER_LOGIC_REGISTERED_BROADCAST_ENABLED =
                    "protected_app_signals_encoder_logic_registered_broadcast_enabled";

    /** Key for feature flagging adselection CLI. */
    public static final String KEY_AD_SELECTION_CLI_ENABLED = "fledge_is_ad_selection_cli_enabled";

    /** Key for feature flagging developer mode feature. */
    public static final String KEY_DEVELOPER_SESSION_FEATURE_ENABLED =
            "developer_session_feature_enabled";

    /** Key for setting the debug flag to enable console messages in logcat */
    public static final String KEY_AD_SERVICES_JS_ISOLATE_CONSOLE_MESSAGES_IN_LOGS_ENABLED =
            "ad_services_js_isolate_console_messages_in_logs_enabled";

    /** Key for feature flagging custom audiences CLI. */
    public static final String KEY_FLEDGE_IS_CUSTOM_AUDIENCE_CLI_ENABLED =
            "fledge_is_custom_audience_cli_enabled";

    /** Key for feature flagging consented debugging CLI. */
    public static final String KEY_FLEDGE_IS_CONSENTED_DEBUGGING_CLI_ENABLED =
            "fledge_is_consented_debugging_cli_enabled";

    public static final String KEY_FLEDGE_AUCTION_SERVER_CONSENTED_DEBUGGING_ENABLED =
            "fledge_auction_server_consented_debugging_enabled";

    public static final String KEY_RECORD_TOPICS_COMPLETE_BROADCAST_ENABLED =
            "record_topics_complete_broadcast_enabled";

    public static final String KEY_FLEDGE_SCHEDULE_CA_COMPLETE_BROADCAST_ENABLED =
            "fledge_schedule_ca_complete_broadcast_enabled";

    public static final String KEY_FLEDGE_BACKGROUND_FETCH_COMPLETE_BROADCAST_ENABLED =
            "fledge_background_fetch_complete_broadcast_enabled";

    public static final String KEY_FLEDGE_BACKGROUND_KEY_FETCH_COMPLETE_BROADCAST_ENABLED =
            "fledge_background_key_fetch_complete_broadcast_enabled";

    public static final String KEY_PERIODIC_ENCODING_JOB_COMPLETE_BROADCAST_ENABLED =
            "fledge_periodic_encoding_complete_broadcast_enabled";

    public static final String KEY_FORCED_ENCODING_JOB_COMPLETE_BROADCAST_ENABLED =
            "fledge_forced_encoding_complete_broadcast_enabled";
}
