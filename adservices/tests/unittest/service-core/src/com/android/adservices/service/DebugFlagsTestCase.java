/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.android.adservices.service.DebugFlags.CONSENT_MANAGER_DEBUG_MODE;
import static com.android.adservices.service.DebugFlags.CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE;
import static com.android.adservices.service.DebugFlags.CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.DebugFlags.CONSENT_NOTIFIED_DEBUG_MODE;
import static com.android.adservices.service.DebugFlags.DEFAULT_AD_SELECTION_CLI_ENABLED;
import static com.android.adservices.service.DebugFlags.DEFAULT_ATTRIBUTION_REPORTING_CLI_ENABLED;
import static com.android.adservices.service.DebugFlags.DEFAULT_CONSENT_MANAGER_OTA_DEBUG_MODE;
import static com.android.adservices.service.DebugFlags.DEFAULT_DEVELOPER_SESSION_FEATURE_ENABLED;
import static com.android.adservices.service.DebugFlags.DEFAULT_FLEDGE_AUCTION_SERVER_CONSENTED_DEBUGGING_ENABLED;
import static com.android.adservices.service.DebugFlags.DEFAULT_FLEDGE_CONSENTED_DEBUGGING_CLI_ENABLED;
import static com.android.adservices.service.DebugFlags.DEFAULT_FLEDGE_CUSTOM_AUDIENCE_CLI_ENABLED;
import static com.android.adservices.service.DebugFlags.DEFAULT_FORCED_ENCODING_COMPLETE_BROADCAST_ENABLED;
import static com.android.adservices.service.DebugFlags.DEFAULT_JS_ISOLATE_CONSOLE_MESSAGES_IN_LOGS_ENABLED;
import static com.android.adservices.service.DebugFlags.DEFAULT_PROTECTED_APP_SIGNALS_CLI_ENABLED;
import static com.android.adservices.service.DebugFlags.DEFAULT_PROTECTED_APP_SIGNALS_ENCODER_LOGIC_REGISTERED_BROADCAST_ENABLED;
import static com.android.adservices.service.DebugFlags.DEFAULT_RECORD_TOPICS_COMPLETE_BROADCAST_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_AD_SELECTION_CLI_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_AD_SERVICES_JS_ISOLATE_CONSOLE_MESSAGES_IN_LOGS_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_ATTRIBUTION_REPORTING_CLI_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_MANAGER_OTA_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFIED_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_DEVELOPER_SESSION_FEATURE_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_FLEDGE_AUCTION_SERVER_CONSENTED_DEBUGGING_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_FLEDGE_IS_CONSENTED_DEBUGGING_CLI_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_FLEDGE_IS_CUSTOM_AUDIENCE_CLI_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_FORCED_ENCODING_JOB_COMPLETE_BROADCAST_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_PROTECTED_APP_SIGNALS_CLI_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_PROTECTED_APP_SIGNALS_ENCODER_LOGIC_REGISTERED_BROADCAST_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_RECORD_TOPICS_COMPLETE_BROADCAST_ENABLED;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;

import org.junit.Test;

/** Base class for tests of {@link DebugFlags} implementations. */
public abstract class DebugFlagsTestCase<T extends DebugFlags>
        extends AdServicesExtendedMockitoTestCase {

    /** Creates a new instance to be used by the test. */
    protected abstract T newInstance();

    /** Sets the value of a debug flag. */
    protected abstract void setDebugFlag(T debugFlags, String name, String value);

    @Test
    public final void testNewInstance() {
        assertWithMessage("newInstance()").that(newInstance()).isNotNull();
    }

    @Test
    public final void testConsentNotificationDebugMode() {
        testDebugFlag(
                KEY_CONSENT_NOTIFICATION_DEBUG_MODE,
                CONSENT_NOTIFICATION_DEBUG_MODE,
                DebugFlags::getConsentNotificationDebugMode);
    }

    @Test
    public final void testConsentNotifiedDebugMode() {
        testDebugFlag(
                KEY_CONSENT_NOTIFIED_DEBUG_MODE,
                CONSENT_NOTIFIED_DEBUG_MODE,
                DebugFlags::getConsentNotifiedDebugMode);
    }

    @Test
    public final void testConsentNotificationActivityDebugMode() {
        testDebugFlag(
                KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE,
                CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE,
                DebugFlags::getConsentNotificationActivityDebugMode);
    }

    @Test
    public final void testConsentManagerDebugMode() {
        testDebugFlag(
                KEY_CONSENT_MANAGER_DEBUG_MODE,
                CONSENT_MANAGER_DEBUG_MODE,
                DebugFlags::getConsentManagerDebugMode);
    }

    @Test
    public final void testConsentManagerOTADebugMode() {
        testDebugFlag(
                KEY_CONSENT_MANAGER_OTA_DEBUG_MODE,
                DEFAULT_CONSENT_MANAGER_OTA_DEBUG_MODE,
                DebugFlags::getConsentManagerOTADebugMode);
    }

    @Test
    public final void testDeveloperSessionFeatureEnabled() {
        testDebugFlag(
                KEY_DEVELOPER_SESSION_FEATURE_ENABLED,
                DEFAULT_DEVELOPER_SESSION_FEATURE_ENABLED,
                DebugFlags::getDeveloperSessionFeatureEnabled);
    }

    @Test
    public final void testProtectedAppSignalsCommandsEnabled() {
        testDebugFlag(
                KEY_PROTECTED_APP_SIGNALS_CLI_ENABLED,
                DEFAULT_PROTECTED_APP_SIGNALS_CLI_ENABLED,
                DebugFlags::getProtectedAppSignalsCommandsEnabled);
    }

    @Test
    public final void testAdSelectionCommandsEnabled() {
        testDebugFlag(
                KEY_AD_SELECTION_CLI_ENABLED,
                DEFAULT_AD_SELECTION_CLI_ENABLED,
                DebugFlags::getAdSelectionCommandsEnabled);
    }

    @Test
    public final void testGetFledgeAuctionServerConsentedDebuggingEnabled() {
        testDebugFlag(
                KEY_FLEDGE_AUCTION_SERVER_CONSENTED_DEBUGGING_ENABLED,
                DEFAULT_FLEDGE_AUCTION_SERVER_CONSENTED_DEBUGGING_ENABLED,
                DebugFlags::getFledgeAuctionServerConsentedDebuggingEnabled);
    }

    @Test
    public final void testGetFledgConsentedDebuggingCliEnabledStatusFlag() {
        testDebugFlag(
                KEY_FLEDGE_IS_CONSENTED_DEBUGGING_CLI_ENABLED,
                DEFAULT_FLEDGE_CONSENTED_DEBUGGING_CLI_ENABLED,
                DebugFlags::getFledgeConsentedDebuggingCliEnabledStatus);
    }

    @Test
    public final void testGetFledgeCustomAudienceCliEnabledStatusFlag() {
        testDebugFlag(
                KEY_FLEDGE_IS_CUSTOM_AUDIENCE_CLI_ENABLED,
                DEFAULT_FLEDGE_CUSTOM_AUDIENCE_CLI_ENABLED,
                DebugFlags::getFledgeCustomAudienceCLIEnabledStatus);
    }

    @Test
    public final void testGetAdServicesJsIsolateConsoleMessagesInLogsEnabled() {
        testDebugFlag(
                KEY_AD_SERVICES_JS_ISOLATE_CONSOLE_MESSAGES_IN_LOGS_ENABLED,
                DEFAULT_JS_ISOLATE_CONSOLE_MESSAGES_IN_LOGS_ENABLED,
                DebugFlags::getAdServicesJsIsolateConsoleMessagesInLogsEnabled);
    }

    @Test
    public final void testRecordTopicsCompleteBroadcastEnabled() {
        testDebugFlag(
                KEY_RECORD_TOPICS_COMPLETE_BROADCAST_ENABLED,
                DEFAULT_RECORD_TOPICS_COMPLETE_BROADCAST_ENABLED,
                DebugFlags::getRecordTopicsCompleteBroadcastEnabled);
    }

    @Test
    public final void testGetProtectedAppSignalsEncoderLogicRegisteredBroadcastEnabled() {
        testDebugFlag(
                KEY_PROTECTED_APP_SIGNALS_ENCODER_LOGIC_REGISTERED_BROADCAST_ENABLED,
                DEFAULT_PROTECTED_APP_SIGNALS_ENCODER_LOGIC_REGISTERED_BROADCAST_ENABLED,
                DebugFlags::getProtectedAppSignalsEncoderLogicRegisteredBroadcastEnabled);
    }

    @Test
    public final void testGetForcedEncodingJobCompleteBroadcastEnabled() {
        testDebugFlag(
                KEY_FORCED_ENCODING_JOB_COMPLETE_BROADCAST_ENABLED,
                DEFAULT_FORCED_ENCODING_COMPLETE_BROADCAST_ENABLED,
                DebugFlags::getForcedEncodingJobCompleteBroadcastEnabled);
    }

    @Test
    public final void testAttributionReportingCliEnabled() {
        testDebugFlag(
                KEY_ATTRIBUTION_REPORTING_CLI_ENABLED,
                DEFAULT_ATTRIBUTION_REPORTING_CLI_ENABLED,
                DebugFlags::getAttributionReportingCommandsEnabled);
    }

    private void testDebugFlag(
            String flagName, Boolean defaultValue, Flaginator<T, Boolean> flaginator) {
        T debugFlags = newInstance();
        // Without any overriding, the value is the hard coded constant.
        assertWithMessage("before overriding")
                .that(flaginator.getFlagValue(debugFlags))
                .isEqualTo(defaultValue);

        boolean phOverridingValue = !defaultValue;

        setDebugFlag(debugFlags, flagName, String.valueOf(phOverridingValue));
        assertWithMessage("after overriding")
                .that(flaginator.getFlagValue(debugFlags))
                .isEqualTo(phOverridingValue);
    }
}
