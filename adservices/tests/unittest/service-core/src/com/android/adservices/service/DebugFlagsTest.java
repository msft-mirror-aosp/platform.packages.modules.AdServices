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

import static com.android.adservices.service.CommonDebugFlags.DUMP_EQUALS;
import static com.android.adservices.service.CommonDebugFlags.DUMP_PREFIX;
import static com.android.adservices.service.DebugFlags.CONSENT_MANAGER_DEBUG_MODE;
import static com.android.adservices.service.DebugFlags.CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE;
import static com.android.adservices.service.DebugFlags.CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.DebugFlags.CONSENT_NOTIFIED_DEBUG_MODE;
import static com.android.adservices.service.DebugFlags.DEFAULT_AD_SELECTION_CLI_ENABLED;
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
import static com.android.adservices.shared.meta_testing.FlagsTestLittleHelper.expectDumpHasAllFlags;

import static com.google.common.truth.Truth.assertWithMessage;

import android.util.Log;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.fixture.TestableSystemProperties;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule;

import org.junit.Test;

/** Unit tests for {@link com.android.adservices.service.DebugFlags} */
@ExtendedMockitoRule.SpyStatic(SdkLevel.class)
public final class DebugFlagsTest extends AdServicesExtendedMockitoTestCase {

    private final DebugFlags mDebugFlags = DebugFlags.getInstance();

    @Override
    protected AdServicesExtendedMockitoRule getAdServicesExtendedMockitoRule() {
        return newDefaultAdServicesExtendedMockitoRuleBuilder()
                .addStaticMockFixtures(TestableSystemProperties::new)
                .build();
    }

    @Test
    public void testConsentNotificationDebugMode() {
        testDebugFlag(
                KEY_CONSENT_NOTIFICATION_DEBUG_MODE,
                CONSENT_NOTIFICATION_DEBUG_MODE,
                DebugFlags::getConsentNotificationDebugMode);
    }

    @Test
    public void testConsentNotifiedDebugMode() {
        testDebugFlag(
                KEY_CONSENT_NOTIFIED_DEBUG_MODE,
                CONSENT_NOTIFIED_DEBUG_MODE,
                DebugFlags::getConsentNotifiedDebugMode);
    }

    @Test
    public void testConsentNotificationActivityDebugMode() {
        testDebugFlag(
                KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE,
                CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE,
                DebugFlags::getConsentNotificationActivityDebugMode);
    }

    @Test
    public void testConsentManagerDebugMode() {
        testDebugFlag(
                KEY_CONSENT_MANAGER_DEBUG_MODE,
                CONSENT_MANAGER_DEBUG_MODE,
                DebugFlags::getConsentManagerDebugMode);
    }

    @Test
    public void testConsentManagerOTADebugMode() {
        testDebugFlag(
                KEY_CONSENT_MANAGER_OTA_DEBUG_MODE,
                DEFAULT_CONSENT_MANAGER_OTA_DEBUG_MODE,
                DebugFlags::getConsentManagerOTADebugMode);
    }

    @Test
    public void testDeveloperSessionFeatureEnabled() {
        testDebugFlag(
                KEY_DEVELOPER_SESSION_FEATURE_ENABLED,
                DEFAULT_DEVELOPER_SESSION_FEATURE_ENABLED,
                DebugFlags::getDeveloperSessionFeatureEnabled);
    }

    @Test
    public void testProtectedAppSignalsCommandsEnabled() {
        testDebugFlag(
                KEY_PROTECTED_APP_SIGNALS_CLI_ENABLED,
                DEFAULT_PROTECTED_APP_SIGNALS_CLI_ENABLED,
                DebugFlags::getProtectedAppSignalsCommandsEnabled);
    }

    @Test
    public void testAdSelectionCommandsEnabled() {
        testDebugFlag(
                KEY_AD_SELECTION_CLI_ENABLED,
                DEFAULT_AD_SELECTION_CLI_ENABLED,
                DebugFlags::getAdSelectionCommandsEnabled);
    }

    @Test
    public void testGetFledgeAuctionServerConsentedDebuggingEnabled() {
        testDebugFlag(
                KEY_FLEDGE_AUCTION_SERVER_CONSENTED_DEBUGGING_ENABLED,
                DEFAULT_FLEDGE_AUCTION_SERVER_CONSENTED_DEBUGGING_ENABLED,
                DebugFlags::getFledgeAuctionServerConsentedDebuggingEnabled);
    }

    @Test
    public void testGetFledgConsentedDebuggingCliEnabledStatusFlag() {
        testDebugFlag(
                KEY_FLEDGE_IS_CONSENTED_DEBUGGING_CLI_ENABLED,
                DEFAULT_FLEDGE_CONSENTED_DEBUGGING_CLI_ENABLED,
                DebugFlags::getFledgeConsentedDebuggingCliEnabledStatus);
    }

    @Test
    public void testGetFledgeCustomAudienceCliEnabledStatusFlag() {
        testDebugFlag(
                KEY_FLEDGE_IS_CUSTOM_AUDIENCE_CLI_ENABLED,
                DEFAULT_FLEDGE_CUSTOM_AUDIENCE_CLI_ENABLED,
                DebugFlags::getFledgeCustomAudienceCLIEnabledStatus);
    }

    @Test
    public void testGetAdServicesJsIsolateConsoleMessagesInLogsEnabled() {
        testDebugFlag(
                KEY_AD_SERVICES_JS_ISOLATE_CONSOLE_MESSAGES_IN_LOGS_ENABLED,
                DEFAULT_JS_ISOLATE_CONSOLE_MESSAGES_IN_LOGS_ENABLED,
                DebugFlags::getAdServicesJsIsolateConsoleMessagesInLogsEnabled);
    }

    @Test
    public void testRecordTopicsCompleteBroadcastEnabled() {
        testDebugFlag(
                KEY_RECORD_TOPICS_COMPLETE_BROADCAST_ENABLED,
                DEFAULT_RECORD_TOPICS_COMPLETE_BROADCAST_ENABLED,
                DebugFlags::getRecordTopicsCompleteBroadcastEnabled);
    }

    @Test
    public void testGetProtectedAppSignalsEncoderLogicRegisteredBroadcastEnabled() {
        testDebugFlag(
                KEY_PROTECTED_APP_SIGNALS_ENCODER_LOGIC_REGISTERED_BROADCAST_ENABLED,
                DEFAULT_PROTECTED_APP_SIGNALS_ENCODER_LOGIC_REGISTERED_BROADCAST_ENABLED,
                DebugFlags::getProtectedAppSignalsEncoderLogicRegisteredBroadcastEnabled);
    }

    @Test
    public void testGetForcedEncodingJobCompleteBroadcastEnabled() {
        testDebugFlag(
                KEY_FORCED_ENCODING_JOB_COMPLETE_BROADCAST_ENABLED,
                DEFAULT_FORCED_ENCODING_COMPLETE_BROADCAST_ENABLED,
                DebugFlags::getForcedEncodingJobCompleteBroadcastEnabled);
    }

    @Test
    public void testDump() throws Exception {
        expectDumpHasAllFlags(
                expect,
                DebugFlagsConstants.class,
                pw -> mDebugFlags.dump(pw),
                flag -> (DUMP_PREFIX + flag.second + DUMP_EQUALS));
        // Must also check the flags from its superclass
        expectDumpHasAllFlags(
                expect,
                CommonDebugFlagsConstants.class,
                pw -> mDebugFlags.dump(pw),
                flag -> (DUMP_PREFIX + flag.second + DUMP_EQUALS));
    }

    private void testDebugFlag(
            String flagName, Boolean defaultValue, Flaginator<DebugFlags, Boolean> flaginator) {
        // Without any overriding, the value is the hard coded constant.
        assertWithMessage("before overriding")
                .that(flaginator.getFlagValue(mDebugFlags))
                .isEqualTo(defaultValue);

        boolean phOverridingValue = !defaultValue;
        setSystemProperty(flagName, String.valueOf(phOverridingValue));
        assertWithMessage("after overriding")
                .that(flaginator.getFlagValue(mDebugFlags))
                .isEqualTo(phOverridingValue);
    }

    private void setSystemProperty(String name, String value) {
        Log.v(mTag, "setSystemProperty(): " + name + "=" + value);
        TestableSystemProperties.set(PhFlags.getSystemPropertyName(name), "" + value);
    }
}
