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

import static com.android.adservices.service.DebugFlagsConstants.KEY_AD_SELECTION_CLI_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_AD_SERVICES_JS_ISOLATE_CONSOLE_MESSAGES_IN_LOGS_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_MANAGER_OTA_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFIED_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_DEVELOPER_SESSION_FEATURE_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_FLEDGE_AUCTION_SERVER_CONSENTED_DEBUGGING_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_COMPLETE_BROADCAST_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_FLEDGE_BACKGROUND_KEY_FETCH_COMPLETE_BROADCAST_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_FLEDGE_IS_CONSENTED_DEBUGGING_CLI_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_FLEDGE_IS_CUSTOM_AUDIENCE_CLI_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_FLEDGE_SCHEDULE_CA_COMPLETE_BROADCAST_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_FORCED_ENCODING_JOB_COMPLETE_BROADCAST_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_PERIODIC_ENCODING_JOB_COMPLETE_BROADCAST_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_PROTECTED_APP_SIGNALS_CLI_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_PROTECTED_APP_SIGNALS_ENCODER_LOGIC_REGISTERED_BROADCAST_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_RECORD_TOPICS_COMPLETE_BROADCAST_ENABLED;

import androidx.annotation.VisibleForTesting;

import java.io.PrintWriter;

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

    /** Default value for fledge auction server consented debug enabled. */
    @VisibleForTesting
    static final boolean DEFAULT_FLEDGE_AUCTION_SERVER_CONSENTED_DEBUGGING_ENABLED = false;

    /** Default value for console messages from js isolate be available in logcat. */
    @VisibleForTesting
    static final boolean DEFAULT_JS_ISOLATE_CONSOLE_MESSAGES_IN_LOGS_ENABLED = false;

    /** Default value for status of custom audiences CLI feature */
    @VisibleForTesting static final boolean DEFAULT_FLEDGE_CUSTOM_AUDIENCE_CLI_ENABLED = false;

    /** Default value for status of consented debugging CLI feature */
    @VisibleForTesting static final boolean DEFAULT_FLEDGE_CONSENTED_DEBUGGING_CLI_ENABLED = false;

    /** Default value for status of developer mode feature. */
    @VisibleForTesting static final boolean DEFAULT_DEVELOPER_SESSION_FEATURE_ENABLED = false;

    /** Default value for sending a broadcast when record topics is completed. */
    @VisibleForTesting
    static final boolean DEFAULT_RECORD_TOPICS_COMPLETE_BROADCAST_ENABLED = false;

    /** Default value for sending a broadcast when schedule custom audience is completed. */
    @VisibleForTesting static final boolean DEFAULT_SCHEDULE_CA_COMPLETE_BROADCAST_ENABLED = false;

    /** Default value for sending a broadcast when schedule custom audience is completed. */
    @VisibleForTesting
    static final boolean DEFAULT_FLEDGE_BACKGROUND_FETCH_COMPLETE_BROADCAST_ENABLED = false;

    /** Default value for sending a broadcast when background key fetch is completed. */
    @VisibleForTesting
    static final boolean DEFAULT_FLEDGE_BACKGROUND_KEY_FETCH_COMPLETE_BROADCAST_ENABLED = false;

    /** Default value for sending a broadcast when periodic encoding is completed. */
    @VisibleForTesting
    static final boolean DEFAULT_PERIODIC_ENCODING_JOB_COMPLETE_BROADCAST_ENABLED = false;

    /** Default value for sending a broadcast when forced encoding is completed. */
    @VisibleForTesting
    static final boolean DEFAULT_FORCED_ENCODING_COMPLETE_BROADCAST_ENABLED = false;

    static final boolean CONSENT_NOTIFICATION_DEBUG_MODE = false;
    static final boolean CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE = false;
    static final boolean CONSENT_NOTIFIED_DEBUG_MODE = false;
    static final boolean CONSENT_MANAGER_DEBUG_MODE = false;
    static final boolean DEFAULT_CONSENT_MANAGER_OTA_DEBUG_MODE = false;

    /** Default for if Protected App Signals sends a broadcast after encoder logic is registered. */
    static final boolean DEFAULT_PROTECTED_APP_SIGNALS_ENCODER_LOGIC_REGISTERED_BROADCAST_ENABLED =
            false;

    public static DebugFlags getInstance() {
        return sInstance;
    }

    private DebugFlags() {}

    public boolean getConsentNotificationDebugMode() {
        return getBoolean(KEY_CONSENT_NOTIFICATION_DEBUG_MODE, CONSENT_NOTIFICATION_DEBUG_MODE);
    }

    /** Returns whether to suppress consent notified state. */
    public boolean getConsentNotifiedDebugMode() {
        return getBoolean(KEY_CONSENT_NOTIFIED_DEBUG_MODE, CONSENT_NOTIFIED_DEBUG_MODE);
    }

    /** Returns the consent notification activity debug mode. */
    public boolean getConsentNotificationActivityDebugMode() {
        return getBoolean(
                KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE,
                CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE);
    }

    public boolean getConsentManagerDebugMode() {
        return getBoolean(KEY_CONSENT_MANAGER_DEBUG_MODE, CONSENT_MANAGER_DEBUG_MODE);
    }

    /** When enabled, the device is treated as OTA device. */
    public boolean getConsentManagerOTADebugMode() {
        return getBoolean(
                KEY_CONSENT_MANAGER_OTA_DEBUG_MODE, DEFAULT_CONSENT_MANAGER_OTA_DEBUG_MODE);
    }

    public boolean getProtectedAppSignalsCommandsEnabled() {
        return getBoolean(
                KEY_PROTECTED_APP_SIGNALS_CLI_ENABLED, DEFAULT_PROTECTED_APP_SIGNALS_CLI_ENABLED);
    }

    public boolean getProtectedAppSignalsEncoderLogicRegisteredBroadcastEnabled() {
        return getBoolean(
                KEY_PROTECTED_APP_SIGNALS_ENCODER_LOGIC_REGISTERED_BROADCAST_ENABLED,
                DEFAULT_PROTECTED_APP_SIGNALS_ENCODER_LOGIC_REGISTERED_BROADCAST_ENABLED);
    }

    public boolean getAdSelectionCommandsEnabled() {
        return getBoolean(KEY_AD_SELECTION_CLI_ENABLED, DEFAULT_AD_SELECTION_CLI_ENABLED);
    }

    /** Returns whether developer mode feature is enabled. */
    public boolean getDeveloperSessionFeatureEnabled() {
        return getBoolean(
                KEY_DEVELOPER_SESSION_FEATURE_ENABLED, DEFAULT_DEVELOPER_SESSION_FEATURE_ENABLED);
    }

    /** Returns whether Consented Debugging is enabled for server auctions. */
    public boolean getFledgeAuctionServerConsentedDebuggingEnabled() {
        return getBoolean(
                KEY_FLEDGE_AUCTION_SERVER_CONSENTED_DEBUGGING_ENABLED,
                DEFAULT_FLEDGE_AUCTION_SERVER_CONSENTED_DEBUGGING_ENABLED);
    }

    /** Returns the enabled status for custom audiences CLI feature. */
    public boolean getFledgeConsentedDebuggingCliEnabledStatus() {
        return getBoolean(
                KEY_FLEDGE_IS_CONSENTED_DEBUGGING_CLI_ENABLED,
                DEFAULT_FLEDGE_CONSENTED_DEBUGGING_CLI_ENABLED);
    }

    /** Returns the enabled status for custom audiences CLI feature. */
    public boolean getFledgeCustomAudienceCLIEnabledStatus() {
        return getBoolean(
                KEY_FLEDGE_IS_CUSTOM_AUDIENCE_CLI_ENABLED,
                DEFAULT_FLEDGE_CUSTOM_AUDIENCE_CLI_ENABLED);
    }

    /** Returns whether sending a broadcast when record topics is completed is enabled. */
    public boolean getRecordTopicsCompleteBroadcastEnabled() {
        return getBoolean(
                KEY_RECORD_TOPICS_COMPLETE_BROADCAST_ENABLED,
                DEFAULT_RECORD_TOPICS_COMPLETE_BROADCAST_ENABLED);
    }

    /** Returns whether sending a broadcast when schedule CA is completed is enabled. */
    public boolean getFledgeScheduleCACompleteBroadcastEnabled() {
        return getBoolean(
                KEY_FLEDGE_SCHEDULE_CA_COMPLETE_BROADCAST_ENABLED,
                DEFAULT_SCHEDULE_CA_COMPLETE_BROADCAST_ENABLED);
    }

    /** Returns whether sending a broadcast when background fetch job is completed is enabled. */
    public boolean getFledgeBackgroundFetchCompleteBroadcastEnabled() {
        return getBoolean(
                KEY_FLEDGE_BACKGROUND_FETCH_COMPLETE_BROADCAST_ENABLED,
                DEFAULT_FLEDGE_BACKGROUND_FETCH_COMPLETE_BROADCAST_ENABLED);
    }

    /** Returns whether sending a broadcast when Background Key Fetch completed is enabled. */
    public boolean getFledgeBackgroundKeyFetchCompleteBroadcastEnabled() {
        return getBoolean(
                KEY_FLEDGE_BACKGROUND_KEY_FETCH_COMPLETE_BROADCAST_ENABLED,
                DEFAULT_FLEDGE_BACKGROUND_KEY_FETCH_COMPLETE_BROADCAST_ENABLED);
    }

    /** Returns whether sending a broadcast when Periodic encoding job is completed is enabled. */
    public boolean getPeriodicEncodingJobCompleteBroadcastEnabled() {
        return getBoolean(
                KEY_PERIODIC_ENCODING_JOB_COMPLETE_BROADCAST_ENABLED,
                DEFAULT_PERIODIC_ENCODING_JOB_COMPLETE_BROADCAST_ENABLED);
    }

    /** Returns whether sending a broadcast when forced encoding job is completed is enabled. */
    public boolean getForcedEncodingJobCompleteBroadcastEnabled() {
        return getBoolean(
                KEY_FORCED_ENCODING_JOB_COMPLETE_BROADCAST_ENABLED,
                DEFAULT_FORCED_ENCODING_COMPLETE_BROADCAST_ENABLED);
    }

    /**
     * Returns a boolean to indicate if console messages from js isolate should be available in
     * logcat or not.
     */
    public boolean getAdServicesJsIsolateConsoleMessagesInLogsEnabled() {
        return getBoolean(
                KEY_AD_SERVICES_JS_ISOLATE_CONSOLE_MESSAGES_IN_LOGS_ENABLED,
                DEFAULT_JS_ISOLATE_CONSOLE_MESSAGES_IN_LOGS_ENABLED);
    }

    @Override
    public void dump(PrintWriter pw) {
        super.dump(pw);

        dump(pw, KEY_CONSENT_NOTIFICATION_DEBUG_MODE, getConsentNotificationDebugMode());
        dump(pw, KEY_CONSENT_NOTIFIED_DEBUG_MODE, getConsentNotifiedDebugMode());
        dump(
                pw,
                KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE,
                getConsentNotificationActivityDebugMode());
        dump(pw, KEY_CONSENT_MANAGER_DEBUG_MODE, getConsentManagerDebugMode());
        dump(pw, KEY_CONSENT_MANAGER_OTA_DEBUG_MODE, getConsentManagerOTADebugMode());
        dump(pw, KEY_PROTECTED_APP_SIGNALS_CLI_ENABLED, getProtectedAppSignalsCommandsEnabled());
        dump(
                pw,
                KEY_PROTECTED_APP_SIGNALS_ENCODER_LOGIC_REGISTERED_BROADCAST_ENABLED,
                getProtectedAppSignalsEncoderLogicRegisteredBroadcastEnabled());
        dump(pw, KEY_AD_SELECTION_CLI_ENABLED, getAdSelectionCommandsEnabled());
        dump(
                pw,
                KEY_FLEDGE_AUCTION_SERVER_CONSENTED_DEBUGGING_ENABLED,
                getFledgeAuctionServerConsentedDebuggingEnabled());
        dump(
                pw,
                KEY_FLEDGE_IS_CONSENTED_DEBUGGING_CLI_ENABLED,
                getFledgeConsentedDebuggingCliEnabledStatus());
        dump(
                pw,
                KEY_FLEDGE_IS_CUSTOM_AUDIENCE_CLI_ENABLED,
                getFledgeCustomAudienceCLIEnabledStatus());
        dump(
                pw,
                KEY_RECORD_TOPICS_COMPLETE_BROADCAST_ENABLED,
                getRecordTopicsCompleteBroadcastEnabled());
        dump(
                pw,
                KEY_FLEDGE_SCHEDULE_CA_COMPLETE_BROADCAST_ENABLED,
                getFledgeScheduleCACompleteBroadcastEnabled());
        dump(
                pw,
                KEY_AD_SERVICES_JS_ISOLATE_CONSOLE_MESSAGES_IN_LOGS_ENABLED,
                getAdServicesJsIsolateConsoleMessagesInLogsEnabled());
        dump(
                pw,
                KEY_FLEDGE_BACKGROUND_FETCH_COMPLETE_BROADCAST_ENABLED,
                getFledgeBackgroundFetchCompleteBroadcastEnabled());
        dump(
                pw,
                KEY_FLEDGE_BACKGROUND_KEY_FETCH_COMPLETE_BROADCAST_ENABLED,
                getFledgeBackgroundKeyFetchCompleteBroadcastEnabled());
        dump(
                pw,
                KEY_PERIODIC_ENCODING_JOB_COMPLETE_BROADCAST_ENABLED,
                getPeriodicEncodingJobCompleteBroadcastEnabled());
        dump(
                pw,
                KEY_DEVELOPER_SESSION_FEATURE_ENABLED,
                getDeveloperSessionFeatureEnabled());
        dump(
                pw,
                KEY_FORCED_ENCODING_JOB_COMPLETE_BROADCAST_ENABLED,
                getForcedEncodingJobCompleteBroadcastEnabled());
    }
}
