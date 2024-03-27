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

import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockGetAdServicesFlag;
import static com.android.adservices.service.Flags.TOPICS_EPOCH_JOB_FLEX_MS;
import static com.android.adservices.service.FlagsConstants.KEY_ADID_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_COBALT_LOGGING_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_GLOBAL_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MDD_LOGGER_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_EPOCH_JOB_FLEX_MS;
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

public final class PhFlagsSystemPropertyOverrideTest extends AdServicesExtendedMockitoTestCase {

    private final Flags mPhFlags = PhFlags.getInstance();

    private final PhFlagsTestHelper mFlagsTestHelper = new PhFlagsTestHelper(mPhFlags, expect);

    private final FlagGuard mMsmtKillSwitchGuard =
            value -> mFlagsTestHelper.setMsmmtKillSwitch(!value);

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
        extendedMockito.mockIsAtLeastT(true);

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
        extendedMockito.mockIsAtLeastT(false);

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
        mFlagsTestHelper.testUnguardedFlag(
                KEY_TOPICS_EPOCH_JOB_FLEX_MS,
                TOPICS_EPOCH_JOB_FLEX_MS,
                flags -> flags.getTopicsEpochJobFlexMs());
    }

    @Test
    public void testGetAdIdKillSwitch() {
        mFlagsTestHelper.testUnguardedLegacyKillSwitch(
                KEY_ADID_KILL_SWITCH, "ADID_KILL_SWITCH", flags -> flags.getAdIdKillSwitch());
    }

    @Test
    public void testGetLegacyMeasurementKillSwitch() {
        mFlagsTestHelper.testLegacyKillSwitch(
                KEY_MEASUREMENT_KILL_SWITCH,
                "MEASUREMENT_KILL_SWITCH",
                flags -> flags.getLegacyMeasurementKillSwitch());
    }

    @Test
    public void testGetMeasurementEnabled() {
        mFlagsTestHelper.testFeatureFlagBackedByLegacyKillSwitch(
                KEY_MEASUREMENT_KILL_SWITCH,
                "MEASUREMENT_KILL_SWITCH",
                flags -> flags.getMeasurementEnabled());
    }

    @Test
    public void testGetMeasurementAttributionFallbackJobEnabled() {
        mFlagsTestHelper.testFeatureFlagBackedByLegacyKillSwitch(
                KEY_MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_KILL_SWITCH,
                "MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_KILL_SWITCH",
                mMsmtKillSwitchGuard,
                flags -> flags.getMeasurementAttributionFallbackJobEnabled());
    }

    @Test
    public void testGetCobaltLoggingEnabled() {
        mFlagsTestHelper.testFeatureFlagGuardedByGlobalKs(
                KEY_COBALT_LOGGING_ENABLED,
                "COBALT_LOGGING_ENABLED",
                flags -> flags.getCobaltLoggingEnabled());
    }

    @Test
    public void testGetMddLoggerEnabled() {
        mFlagsTestHelper.testFeatureFlagBackedByLegacyKillSwitch(
                KEY_MDD_LOGGER_KILL_SWITCH,
                "MDD_LOGGER_KILL_SWITCH",
                flags -> flags.getMddLoggerEnabled());
    }
}
