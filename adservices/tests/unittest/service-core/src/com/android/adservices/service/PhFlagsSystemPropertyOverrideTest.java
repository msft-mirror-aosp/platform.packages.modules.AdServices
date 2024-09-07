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

import static com.android.adservices.service.DeviceConfigAndSystemPropertiesExpectations.mockGetAdServicesFlag;
import static com.android.adservices.service.Flags.DEFAULT_CLASSIFIER_TYPE;
import static com.android.adservices.service.Flags.MAINTENANCE_JOB_FLEX_MS;
import static com.android.adservices.service.Flags.MAINTENANCE_JOB_PERIOD_MS;
import static com.android.adservices.service.Flags.MEASUREMENT_ROLLBACK_DELETION_R_ENABLED;
import static com.android.adservices.service.Flags.TOPICS_EPOCH_JOB_FLEX_MS;
import static com.android.adservices.service.Flags.TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC;
import static com.android.adservices.service.FlagsConstants.KEY_ADID_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_APPSETID_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_CLASSIFIER_TYPE;
import static com.android.adservices.service.FlagsConstants.KEY_COBALT_LOGGING_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_BACK_COMPAT;
import static com.android.adservices.service.FlagsConstants.KEY_ENCRYPTION_KEY_NEW_ENROLLMENT_FETCH_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_ENCRYPTION_KEY_PERIODIC_FETCH_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SELECT_ADS_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_GLOBAL_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MAINTENANCE_JOB_FLEX_MS;
import static com.android.adservices.service.FlagsConstants.KEY_MAINTENANCE_JOB_PERIOD_MS;
import static com.android.adservices.service.FlagsConstants.KEY_MDD_BACKGROUND_TASK_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MDD_LOGGER_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_SOURCES_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_STATUS_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_JOB_DEBUG_REPORTING_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_JOB_DELETE_UNINSTALLED_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_JOB_VERBOSE_DEBUG_REPORTING_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_REGISTRATION_FALLBACK_JOB_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ROLLBACK_DELETION_R_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_EPOCH_JOB_FLEX_MS;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC;
import static com.android.adservices.service.FlagsConstants.KEY_UI_OTA_RESOURCES_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsTest.getConstantValue;
import static com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.AdServicesSystemPropertiesDumperRule;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.fixture.TestableSystemProperties;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.TestableDeviceConfig;

import org.junit.Rule;
import org.junit.Test;

@SpyStatic(SdkLevel.class)
public final class PhFlagsSystemPropertyOverrideTest extends AdServicesExtendedMockitoTestCase {

    private final Flags mPhFlags = PhFlags.getInstance();

    private final PhFlagsTestHelper mFlagsTestHelper = new PhFlagsTestHelper(mPhFlags, expect);

    private final FlagGuard mMsmtKillSwitchGuard =
            value -> mFlagsTestHelper.setMsmtKillSwitch(!value);

    @Rule
    public final AdServicesSystemPropertiesDumperRule sysPropDumper =
            new AdServicesSystemPropertiesDumperRule();

    // Overriding DeviceConfig stub to avoid Read device config permission errors and to also
    // test the behavior of flags, when both device config and system properties are set.
    @Override
    protected AdServicesExtendedMockitoRule getAdServicesExtendedMockitoRule() {
        return newDefaultAdServicesExtendedMockitoRuleBuilder()
                .addStaticMockFixtures(TestableDeviceConfig::new)
                .addStaticMockFixtures(TestableSystemProperties::new)
                .build();
    }

    @Test
    @SpyStatic(SdkLevel.class)
    public void testGetGlobalKillSwitch_TPlus() {
        mocker.mockIsAtLeastT(true);

        // This is the value hardcoded by a constant on Flags.java
        boolean constantValue = getConstantValue("GLOBAL_KILL_SWITCH");
        expect.withMessage("GlobalKillSwitch default value")
                .that(mPhFlags.getGlobalKillSwitch())
                .isEqualTo(constantValue);

        // Only set global kill switch system property.
        mFlagsTestHelper.setSystemProperty(KEY_GLOBAL_KILL_SWITCH, !constantValue);
        expect.withMessage("GlobalKillSwitch overridden value")
                .that(mPhFlags.getGlobalKillSwitch())
                .isEqualTo(!constantValue);

        // Now set the device config value as well, system property should still take precedence.
        mockGetAdServicesFlag(KEY_GLOBAL_KILL_SWITCH, constantValue);
        expect.withMessage("Overridden global_kill_switch system property value")
                .that(mPhFlags.getGlobalKillSwitch())
                .isEqualTo(!constantValue);
    }

    @Test
    @SpyStatic(SdkLevel.class)
    public void testGetGlobalKillSwitch_TMinus() {
        mocker.mockIsAtLeastT(false);

        // This is the value hardcoded by a constant on Flags.java
        boolean constantValue = getConstantValue("GLOBAL_KILL_SWITCH");
        expect.withMessage("GlobalKillSwitch default value")
                .that(mPhFlags.getGlobalKillSwitch())
                .isEqualTo(constantValue);

        // Set back-compat flag..
        mFlagsTestHelper.setGlobalKillSwitch(!constantValue);
        expect.withMessage("GlobalKillSwitch overridden value")
                .that(mPhFlags.getGlobalKillSwitch())
                .isEqualTo(!constantValue);
    }

    @Test
    public void testGetTopicsEpochJobFlexMs() {
        mFlagsTestHelper.testPositiveConfigFlagBackedBySystemProperty(
                KEY_TOPICS_EPOCH_JOB_FLEX_MS,
                TOPICS_EPOCH_JOB_FLEX_MS,
                Flags::getTopicsEpochJobFlexMs);
    }

    @Test
    public void testGetTopicsPercentageForRandomTopic() {
        mFlagsTestHelper.testPositiveConfigFlagBackedBySystemProperty(
                KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC,
                TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC,
                Flags::getTopicsPercentageForRandomTopic);
    }

    @Test
    public void testGetAdIdKillSwitch() {
        // Values of globalKS should be ignored.
        mFlagsTestHelper.setGlobalKillSwitch(true);

        mFlagsTestHelper.testUnguardedLegacyKillSwitchBackedBySystemProperty(
                KEY_ADID_KILL_SWITCH, "ADID_KILL_SWITCH", Flags::getAdIdKillSwitch);
    }

    @Test
    public void testGetLegacyMeasurementKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_KILL_SWITCH,
                "MEASUREMENT_KILL_SWITCH",
                Flags::getLegacyMeasurementKillSwitch);
    }

    @Test
    public void testGetMeasurementApiDeleteRegistrationsKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH,
                "MEASUREMENT_KILL_SWITCH",
                Flags::getMeasurementApiDeleteRegistrationsKillSwitch);
    }

    @Test
    public void testGetMeasurementApiDeleteRegistrationsKillSwitch_measurementOverride() {
        mFlagsTestHelper.testLegacyKillSwitchGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH,
                "MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH",
                value -> mFlagsTestHelper.setMsmtKillSwitch(!value),
                Flags::getMeasurementApiDeleteRegistrationsKillSwitch);
    }

    @Test
    public void testUiOtaResourcesFeatureEnabled() {
        mFlagsTestHelper.testFeatureFlagBackedBySystemPropertyGuardedByGlobalKs(
                KEY_UI_OTA_RESOURCES_FEATURE_ENABLED,
                "UI_OTA_RESOURCES_FEATURE_ENABLED",
                Flags::getUiOtaResourcesFeatureEnabled);
    }

    @Test
    public void testGetMeasurementRollbackDeletionAppSearchKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH,
                "MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH",
                Flags::getMeasurementRollbackDeletionAppSearchKillSwitch);
    }

    @Test
    public void testGetMeasurementRollbackDeletionAppSearchKillSwitch_measurementOverride() {
        mFlagsTestHelper.testLegacyKillSwitchGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH,
                "MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH",
                value -> mFlagsTestHelper.setMsmtKillSwitch(!value),
                Flags::getMeasurementRollbackDeletionAppSearchKillSwitch);
    }

    @Test
    public void testGetFledgeCustomAudienceServiceKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH,
                "FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH",
                Flags::getFledgeCustomAudienceServiceKillSwitch);
    }

    @Test
    public void testGetFledgeSelectAdsKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_FLEDGE_SELECT_ADS_KILL_SWITCH,
                "FLEDGE_SELECT_ADS_KILL_SWITCH",
                Flags::getFledgeSelectAdsKillSwitch);
    }

    @Test
    public void testGetAppSetIdKillSwitch() {
        // Values of globalKS should be ignored.
        mFlagsTestHelper.setGlobalKillSwitch(true);

        mFlagsTestHelper.testUnguardedLegacyKillSwitchBackedBySystemProperty(
                KEY_APPSETID_KILL_SWITCH, "APPSETID_KILL_SWITCH", Flags::getAppSetIdKillSwitch);
    }

    @Test
    public void testGetMddBackgroundTaskKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MDD_BACKGROUND_TASK_KILL_SWITCH,
                "MDD_BACKGROUND_TASK_KILL_SWITCH",
                Flags::getMddBackgroundTaskKillSwitch);
    }

    @Test
    public void testGetEncryptionKeyPeriodicFetchKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_ENCRYPTION_KEY_PERIODIC_FETCH_KILL_SWITCH,
                "ENCRYPTION_KEY_PERIODIC_FETCH_KILL_SWITCH",
                Flags::getEncryptionKeyPeriodicFetchKillSwitch);
    }

    @Test
    public void testGetEncryptionKeyNewEnrollmentFetchKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_ENCRYPTION_KEY_NEW_ENROLLMENT_FETCH_KILL_SWITCH,
                "ENCRYPTION_KEY_NEW_ENROLLMENT_FETCH_KILL_SWITCH",
                Flags::getEncryptionKeyNewEnrollmentFetchKillSwitch);
    }

    @Test
    public void testGetTopicsKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_TOPICS_KILL_SWITCH, "TOPICS_KILL_SWITCH", Flags::getTopicsKillSwitch);
    }

    @Test
    public void testGetOnDeviceClassifierKillSwitch() {
        // Values of globalKS should be ignored.
        mFlagsTestHelper.setGlobalKillSwitch(true);

        mFlagsTestHelper.testUnguardedLegacyKillSwitchBackedBySystemProperty(
                KEY_TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH,
                "TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH",
                Flags::getTopicsOnDeviceClassifierKillSwitch);
    }

    @Test
    public void testGetMeasurementRollbackDeletionREnabled() {
        // Disable global_kill_switch so that this flag can be tested.
        mFlagsTestHelper.setGlobalKillSwitch(false);
        mockGetAdServicesFlag(KEY_ENABLE_BACK_COMPAT, true);
        mocker.mockIsAtLeastT(false);
        mocker.mockIsAtLeastS(false);

        expect.that(mPhFlags.getMeasurementRollbackDeletionREnabled())
                .isEqualTo(MEASUREMENT_ROLLBACK_DELETION_R_ENABLED);

        boolean phOverridingValue = !MEASUREMENT_ROLLBACK_DELETION_R_ENABLED;
        mockGetAdServicesFlag(KEY_MEASUREMENT_ROLLBACK_DELETION_R_ENABLED, phOverridingValue);

        expect.that(mPhFlags.getMeasurementRollbackDeletionREnabled()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetMeasurementApiStatusKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_API_STATUS_KILL_SWITCH,
                "MEASUREMENT_API_STATUS_KILL_SWITCH",
                Flags::getMeasurementApiStatusKillSwitch);
    }

    @Test
    public void testGetMeasurementApiStatusKillSwitch_measurementOverride() {
        mFlagsTestHelper.testLegacyKillSwitchGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_API_STATUS_KILL_SWITCH,
                "MEASUREMENT_API_STATUS_KILL_SWITCH",
                value -> mFlagsTestHelper.setMsmtKillSwitch(!value),
                Flags::getMeasurementApiStatusKillSwitch);
    }

    @Test
    public void testGetMeasurementApiRegisterSourceKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH,
                "MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH",
                Flags::getMeasurementApiRegisterSourceKillSwitch);
    }

    @Test
    public void testGetMeasurementApiRegisterSourceKillSwitch_measurementOverride() {
        mFlagsTestHelper.testLegacyKillSwitchGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH,
                "MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH",
                value -> mFlagsTestHelper.setMsmtKillSwitch(!value),
                Flags::getMeasurementApiRegisterSourceKillSwitch);
    }

    @Test
    public void testGetMeasurementAttributionFallbackJobEnabled() {
        mFlagsTestHelper.testFeatureFlagBackedBySystemPropertyGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_KILL_SWITCH,
                "MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_KILL_SWITCH",
                mMsmtKillSwitchGuard,
                Flags::getMeasurementAttributionFallbackJobEnabled);
    }

    @Test
    public void testGetMeasurementApiRegisterTriggerKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH,
                "MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH",
                Flags::getMeasurementApiRegisterTriggerKillSwitch);
    }

    @Test
    public void testGetMeasurementApiRegisterTriggerKillSwitch_measurementOverride() {
        mFlagsTestHelper.testLegacyKillSwitchGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH,
                "MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH",
                mMsmtKillSwitchGuard,
                Flags::getMeasurementApiRegisterTriggerKillSwitch);
    }

    @Test
    public void testGetMeasurementApiRegisterWebSourceKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH,
                "MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH",
                Flags::getMeasurementApiRegisterWebSourceKillSwitch);
    }

    @Test
    public void testGetMeasurementApiRegisterWebSourceKillSwitch_measurementOverride() {
        mFlagsTestHelper.testLegacyKillSwitchGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH,
                "MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH",
                mMsmtKillSwitchGuard,
                Flags::getMeasurementApiRegisterWebSourceKillSwitch);
    }

    @Test
    public void testGetMeasurementApiRegisterSourcesKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_API_REGISTER_SOURCES_KILL_SWITCH,
                "MEASUREMENT_API_REGISTER_SOURCES_KILL_SWITCH",
                Flags::getMeasurementApiRegisterSourcesKillSwitch);
    }

    @Test
    public void testGetMeasurementApiRegisterSourcesKillSwitch_measurementOverride() {
        mFlagsTestHelper.testLegacyKillSwitchGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_API_REGISTER_SOURCES_KILL_SWITCH,
                "MEASUREMENT_API_REGISTER_SOURCES_KILL_SWITCH",
                mMsmtKillSwitchGuard,
                Flags::getMeasurementApiRegisterSourcesKillSwitch);
    }

    @Test
    public void testGetMeasurementApiRegisterWebTriggerKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH,
                "MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH",
                Flags::getMeasurementApiRegisterWebTriggerKillSwitch);
    }

    @Test
    public void testGetMeasurementApiRegisterWebTriggerKillSwitch_measurementOverride() {
        mFlagsTestHelper.testLegacyKillSwitchGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH,
                "MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH",
                mMsmtKillSwitchGuard,
                Flags::getMeasurementApiRegisterWebTriggerKillSwitch);
    }

    @Test
    public void testGetMeasurementJobAggregateFallbackReportingKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH,
                "MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH",
                Flags::getMeasurementJobAggregateFallbackReportingKillSwitch);
    }

    @Test
    public void testGetMeasurementJobAggregateFallbackReportingKillSwitch_measurementOverride() {
        mFlagsTestHelper.testLegacyKillSwitchGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH,
                "MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH",
                mMsmtKillSwitchGuard,
                Flags::getMeasurementJobAggregateFallbackReportingKillSwitch);
    }

    @Test
    public void testGetMeasurementJobAggregateReportingKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH,
                "MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH",
                Flags::getMeasurementJobAggregateReportingKillSwitch);
    }

    @Test
    public void testGetMeasurementJobAggregateReportingKillSwitch_measurementOverride() {
        mFlagsTestHelper.testLegacyKillSwitchGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH,
                "MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH",
                mMsmtKillSwitchGuard,
                Flags::getMeasurementJobAggregateReportingKillSwitch);
    }

    @Test
    public void testGetMeasurementJobAttributionKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH,
                "MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH",
                Flags::getMeasurementJobAttributionKillSwitch);
    }

    @Test
    public void testGetMeasurementJobAttributionKillSwitch_measurementOverride() {
        mFlagsTestHelper.testLegacyKillSwitchGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH,
                "MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH",
                mMsmtKillSwitchGuard,
                Flags::getMeasurementJobAttributionKillSwitch);
    }

    @Test
    public void testGetMeasurementVerboseDebugReportingFallbackJobKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH,
                "MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH",
                Flags::getMeasurementVerboseDebugReportingFallbackJobKillSwitch);
    }

    @Test
    public void testGetMeasurementVerboseDebugReportingFallbackJobKillSwitch_measurementOverride() {
        mFlagsTestHelper.testLegacyKillSwitchGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH,
                "MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH",
                mMsmtKillSwitchGuard,
                Flags::getMeasurementVerboseDebugReportingFallbackJobKillSwitch);
    }

    @Test
    public void testGetMeasurementJobVerboseDebugReportingKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_JOB_VERBOSE_DEBUG_REPORTING_KILL_SWITCH,
                "MEASUREMENT_JOB_VERBOSE_DEBUG_REPORTING_KILL_SWITCH",
                Flags::getMeasurementJobVerboseDebugReportingKillSwitch);
    }

    @Test
    public void testGetMeasurementJobVerboseDebugReportingKillSwitch_measurementOverride() {
        mFlagsTestHelper.testLegacyKillSwitchGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_JOB_VERBOSE_DEBUG_REPORTING_KILL_SWITCH,
                "MEASUREMENT_JOB_VERBOSE_DEBUG_REPORTING_KILL_SWITCH",
                mMsmtKillSwitchGuard,
                Flags::getMeasurementJobVerboseDebugReportingKillSwitch);
    }

    @Test
    public void testGetMeasurementJobDebugReportingKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_JOB_DEBUG_REPORTING_KILL_SWITCH,
                "MEASUREMENT_JOB_DEBUG_REPORTING_KILL_SWITCH",
                Flags::getMeasurementJobDebugReportingKillSwitch);
    }

    @Test
    public void testGetMeasurementJobDebugReportingKillSwitch_measurementOverride() {
        mFlagsTestHelper.testLegacyKillSwitchGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_JOB_DEBUG_REPORTING_KILL_SWITCH,
                "MEASUREMENT_JOB_DEBUG_REPORTING_KILL_SWITCH",
                mMsmtKillSwitchGuard,
                Flags::getMeasurementJobDebugReportingKillSwitch);
    }

    @Test
    public void testGetMeasurementDebugReportingFallbackJobKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH,
                "MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH",
                Flags::getMeasurementDebugReportingFallbackJobKillSwitch);
    }

    @Test
    public void testGetMeasurementDebugReportingFallbackJobKillSwitch_measurementOverride() {
        mFlagsTestHelper.testLegacyKillSwitchGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH,
                "MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_KILL_SWITCH",
                mMsmtKillSwitchGuard,
                Flags::getMeasurementDebugReportingFallbackJobKillSwitch);
    }

    @Test
    public void testGetMeasurementJobDeleteExpiredKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH,
                "MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH",
                Flags::getMeasurementJobDeleteExpiredKillSwitch);
    }

    @Test
    public void testGetMeasurementJobDeleteExpiredKillSwitch_measurementOverride() {
        mFlagsTestHelper.testLegacyKillSwitchGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH,
                "MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH",
                mMsmtKillSwitchGuard,
                Flags::getMeasurementJobDeleteExpiredKillSwitch);
    }

    @Test
    public void testGetMeasurementJobDeleteUninstalledKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_JOB_DELETE_UNINSTALLED_KILL_SWITCH,
                "MEASUREMENT_JOB_DELETE_UNINSTALLED_KILL_SWITCH",
                Flags::getMeasurementJobDeleteUninstalledKillSwitch);
    }

    @Test
    public void testGetMeasurementJobDeleteUninstalledKillSwitch_measurementOverride() {
        mFlagsTestHelper.testLegacyKillSwitchGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_JOB_DELETE_UNINSTALLED_KILL_SWITCH,
                "MEASUREMENT_JOB_DELETE_UNINSTALLED_KILL_SWITCH",
                mMsmtKillSwitchGuard,
                Flags::getMeasurementJobDeleteUninstalledKillSwitch);
    }

    @Test
    public void testGetMeasurementJobEventFallbackReportingKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH,
                "MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH",
                Flags::getMeasurementJobEventFallbackReportingKillSwitch);
    }

    @Test
    public void testGetMeasurementJobEventFallbackReportingKillSwitch_measurementOverride() {
        mFlagsTestHelper.testLegacyKillSwitchGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH,
                "MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH",
                mMsmtKillSwitchGuard,
                Flags::getMeasurementJobEventFallbackReportingKillSwitch);
    }

    @Test
    public void testGetMeasurementJobEventReportingKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH,
                "MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH",
                Flags::getMeasurementJobEventReportingKillSwitch);
    }

    @Test
    public void testGetMeasurementJobEventReportingKillSwitch_measurementOverride() {
        mFlagsTestHelper.testLegacyKillSwitchGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH,
                "MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH",
                mMsmtKillSwitchGuard,
                Flags::getMeasurementJobEventReportingKillSwitch);
    }

    @Test
    public void testGetMeasurementReceiverInstallAttributionKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH,
                "MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH",
                Flags::getMeasurementReceiverInstallAttributionKillSwitch);
    }

    @Test
    public void testGetMeasurementReceiverInstallAttributionKillSwitch_measurementOverride() {
        mFlagsTestHelper.testLegacyKillSwitchGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH,
                "MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH",
                mMsmtKillSwitchGuard,
                Flags::getMeasurementReceiverInstallAttributionKillSwitch);
    }

    @Test
    public void testGetMeasurementReceiverDeletePackagesKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH,
                "MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH",
                Flags::getMeasurementReceiverDeletePackagesKillSwitch);
    }

    @Test
    public void testGetMeasurementReceiverDeletePackagesKillSwitch_measurementOverride() {
        mFlagsTestHelper.testLegacyKillSwitchGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH,
                "MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH",
                mMsmtKillSwitchGuard,
                Flags::getMeasurementReceiverDeletePackagesKillSwitch);
    }

    @Test
    public void testGetMeasurementRollbackDeletionKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH,
                "MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH",
                Flags::getMeasurementRollbackDeletionKillSwitch);
    }

    @Test
    public void testGetMeasurementRollbackDeletionKillSwitch_measurementOverride() {
        mFlagsTestHelper.testLegacyKillSwitchGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH,
                "MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH",
                mMsmtKillSwitchGuard,
                Flags::getMeasurementRollbackDeletionKillSwitch);
    }

    @Test
    public void testGetMeasurementRegistrationJobQueueKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH,
                "MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH",
                Flags::getAsyncRegistrationJobQueueKillSwitch);
    }

    @Test
    public void testGetMeasurementRegistrationJobQueueKillSwitch_measurementOverride() {
        mFlagsTestHelper.testLegacyKillSwitchGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH,
                "MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH",
                mMsmtKillSwitchGuard,
                Flags::getAsyncRegistrationJobQueueKillSwitch);
    }

    @Test
    public void testGetMeasurementRegistrationFallbackJobKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitchBackedBySystemProperty(
                KEY_MEASUREMENT_REGISTRATION_FALLBACK_JOB_KILL_SWITCH,
                "MEASUREMENT_REGISTRATION_FALLBACK_JOB_KILL_SWITCH",
                Flags::getAsyncRegistrationFallbackJobKillSwitch);
    }

    @Test
    public void testGetMeasurementRegistrationFallbackJobKillSwitch_measurementOverride() {
        mFlagsTestHelper.testLegacyKillSwitchGuardedByLegacyKillSwitch(
                KEY_MEASUREMENT_REGISTRATION_FALLBACK_JOB_KILL_SWITCH,
                "MEASUREMENT_REGISTRATION_FALLBACK_JOB_KILL_SWITCH",
                mMsmtKillSwitchGuard,
                Flags::getAsyncRegistrationFallbackJobKillSwitch);
    }

    @Test
    public void testGetCobaltLoggingEnabled() {
        mFlagsTestHelper.testFeatureFlagBackedBySystemPropertyGuardedByGlobalKs(
                KEY_COBALT_LOGGING_ENABLED,
                "COBALT_LOGGING_ENABLED",
                Flags::getCobaltLoggingEnabled);
    }

    @Test
    public void testGetMddLoggerEnabled() {
        mFlagsTestHelper.testFeatureFlagBackedBySystemPropertyGuardedByLegacyKillSwitch(
                KEY_MDD_LOGGER_KILL_SWITCH, "MDD_LOGGER_KILL_SWITCH", Flags::getMddLoggerEnabled);
    }

    @Test
    public void testClassifierType() {
        mFlagsTestHelper.testConfigFlagBackedBySystemProperty(
                KEY_CLASSIFIER_TYPE, DEFAULT_CLASSIFIER_TYPE, Flags::getClassifierType);
    }

    @Test
    public void testGetMaintenanceJobPeriodMs() {
        mFlagsTestHelper.testPositiveConfigFlagBackedBySystemProperty(
                KEY_MAINTENANCE_JOB_PERIOD_MS,
                MAINTENANCE_JOB_PERIOD_MS,
                Flags::getMaintenanceJobPeriodMs);
    }

    @Test
    public void testGetMaintenanceJobFlexMs() {
        mFlagsTestHelper.testPositiveConfigFlagBackedBySystemProperty(
                KEY_MAINTENANCE_JOB_FLEX_MS,
                MAINTENANCE_JOB_FLEX_MS,
                Flags::getMaintenanceJobFlexMs);
    }
}
