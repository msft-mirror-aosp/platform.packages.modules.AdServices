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

import static com.android.adservices.common.DeviceConfigUtil.setAdservicesFlag;
import static com.android.adservices.service.Flags.TOPICS_EPOCH_JOB_FLEX_MS;
import static com.android.adservices.service.Flags.MEASUREMENT_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_GLOBAL_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_EPOCH_JOB_FLEX_MS;
import static com.android.adservices.service.FlagsConstants.NAMESPACE_ADSERVICES;

import android.os.SystemProperties;

import androidx.test.filters.FlakyTest;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.mockito.ExtendedMockitoExpectations;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;
import com.android.modules.utils.testing.TestableDeviceConfig;

import org.junit.Test;

/* TODO(b/326254556): test fail if properties are already set. Example:
 *
 * adb shell setprop debug.adservices.topics_epoch_job_flex_ms 42
 * adb shell setprop debug.adservices.test.measurement_kill_switch true
 */

@SpyStatic(SystemProperties.class)
public class PhFlagsSystemPropertyOverrideTest extends AdServicesExtendedMockitoTestCase {

    private final Flags mPhFlags = PhFlags.getInstance();

    private final FlagGuard mGlobalKillSwitchGuard = value -> setGlobalKillSwitch(!value);
    private final FlagGuard mMsmtKillSwitchesGuard = value -> setMsmmtKillSwitch(!value);

    // Overriding DeviceConfig stub to avoid Read device config permission errors and to also
    // test the behavior of flags, when both device config and system properties are set.
    @Override
    protected AdServicesExtendedMockitoRule getAdServicesExtendedMockitoRule() {
        return newDefaultAdServicesExtendedMockitoRuleBuilder()
                .addStaticMockFixtures(TestableDeviceConfig::new)
                .build();
    }

    @FlakyTest(bugId = 326254556)
    @Test
    public void testGetTopicsEpochJobFlexMs() {
        testUnguardedFlag(
                KEY_TOPICS_EPOCH_JOB_FLEX_MS,
                TOPICS_EPOCH_JOB_FLEX_MS,
                flags -> flags.getTopicsEpochJobFlexMs());
    }

    @FlakyTest(bugId = 326254556)
    @Test
    public void testGetLegacyMeasurementKillSwitch() {
        testLegacyKillSwitchGuardedByGlobalKillSwitch(
                KEY_MEASUREMENT_KILL_SWITCH,
                MEASUREMENT_KILL_SWITCH,
                flags -> flags.getLegacyMeasurementKillSwitch());
    }

    @FlakyTest(bugId = 326254556)
    @Test
    public void testGetMeasurementEnabled() {
        testFeatureFlagGuardedByGlobalKillSwitch(
                KEY_MEASUREMENT_KILL_SWITCH,
                !MEASUREMENT_KILL_SWITCH,
                flags -> flags.getMeasurementEnabled());
    }

    @FlakyTest(bugId = 326254556)
    @Test
    public void testGetMeasurementAttributionFallbackJobEnabled() {
        testFeatureFlag(
                KEY_MEASUREMENT_KILL_SWITCH,
                !MEASUREMENT_KILL_SWITCH,
                mMsmtKillSwitchesGuard,
                flags -> flags.getMeasurementAttributionFallbackJobEnabled());
    }

    /**
     * Tests the behavior of a boolean flag that is not guarded by any other flag.
     *
     * @param name name of the flag
     * @param defaultValue default value of the flag
     * @param flaginator helper object used to get the value of the flag
     */
    private void testUnguardedFlag(String name, long defaultValue, Flaginator<Long> flaginator) {
        // Without any overriding, the value is the hard coded constant.
        expect.withMessage("getter for %s by default", name)
                .that(flaginator.getFlagValue(mPhFlags))
                .isEqualTo(defaultValue);

        // Now overriding with the value in both system properties and device config.
        long systemPropertyValue = defaultValue + 1;
        long deviceConfigValue = defaultValue + 2;
        mockGetSystemProperty(name, systemPropertyValue);

        setAdservicesFlag(name, deviceConfigValue);

        expect.withMessage("getter for %s prefers system property value", name)
                .that(flaginator.getFlagValue(mPhFlags))
                .isEqualTo(systemPropertyValue);
    }

    /**
     * Tests the behavior of a boolean flag that is guarded by the global kill switch.
     *
     * @param flagName name of the flag
     * @param defaultValue default value of the flag
     * @param flaginator helper object used to get the value of the flag being tested
     */
    private void testFeatureFlagGuardedByGlobalKillSwitch(
            String flagName, boolean defaultValue, Flaginator<Boolean> flaginator) {
        testFeatureFlag(flagName, defaultValue, mGlobalKillSwitchGuard, flaginator);
    }

    /**
     * Tests the behavior of a boolean flag that is guarded by the global kill switch.
     *
     * @param flagName name of the flag
     * @param defaultValue default value of the flag
     * @param flaginator helper object used to get the value of the flag being tested
     */
    private void testLegacyKillSwitchGuardedByGlobalKillSwitch(
            String flagName, boolean defaultValue, Flaginator<Boolean> flaginator) {
        internalTestFeatureFlag(
                flagName,
                defaultValue,
                /* isLegacyKillSwitch= */ true,
                mGlobalKillSwitchGuard,
                flaginator);
    }

    /**
     * Tests the behavior of a feature flag that is guarded by a "generic" guard (typically the
     * global kill switch and a per-API kill switch).
     *
     * @param flagName name of the legacy kill switch flag
     * @param defaultValue default value of the flag
     * @param guard helper object used enable / disable the guarding flags
     * @param flaginator helper object used to get the value of the flag being tested
     */
    private void testFeatureFlag(
            String flagName,
            boolean defaultValue,
            FlagGuard guard,
            Flaginator<Boolean> flaginator) {
        internalTestFeatureFlag(
                flagName, defaultValue, /* isLegacyKillSwitch= */ false, guard, flaginator);
    }

    // Should not be called by tests directly
    private void internalTestFeatureFlag(
            String flagName,
            boolean defaultValue,
            boolean isLegacyKillSwitch,
            FlagGuard guard,
            Flaginator<Boolean> flaginator) {
        // First check the behavior when the guarding flags are disabled (like kill switches on) -
        // should always be false
        guard.setEnabled(false);

        boolean disabledValue = isLegacyKillSwitch;
        expect.withMessage("getter of %s by default when guarding kill switches are on", flagName)
                .that(flaginator.getFlagValue(mPhFlags))
                .isEqualTo(disabledValue);

        // Make sure neither DeviceConfig or SystemProperty was called
        verifyGetBooleanSystemPropertyNotCalled(flagName);
        verifyGetBooleanDeviceConfigFlagNotCalled(flagName);

        // Then enable the guarding flags
        guard.setEnabled(true);

        // Without any overriding, the value is the hard coded constant.
        expect.withMessage("getter of %s by default when guarding kill switches are off", flagName)
                .that(flaginator.getFlagValue(mPhFlags))
                .isEqualTo(defaultValue);

        // Now overriding the device config flag so the expected value is the system properties one
        boolean systemPropertyValue = !defaultValue;
        boolean deviceConfigValue = defaultValue;
        boolean expectedFlagValue = isLegacyKillSwitch ? systemPropertyValue : !systemPropertyValue;

        mockGetSystemProperty(flagName, systemPropertyValue);
        setAdservicesFlag(flagName, deviceConfigValue);

        expect.withMessage("getter of %s prefers system property value", flagName)
                .that(flaginator.getFlagValue(mPhFlags))
                .isEqualTo(expectedFlagValue);
    }

    private void setGlobalKillSwitch(boolean value) {
        ExtendedMockitoExpectations.mockGetAdServicesFlag(KEY_GLOBAL_KILL_SWITCH, value);
    }

    private void setMsmmtKillSwitch(boolean value) {
        setGlobalKillSwitch(value);
        ExtendedMockitoExpectations.mockGetAdServicesFlag(KEY_MEASUREMENT_KILL_SWITCH, value);
    }

    private void mockGetSystemProperty(String name, long value) {
        ExtendedMockitoExpectations.mockGetSystemProperty(
                PhFlags.getSystemPropertyName(name), value);
    }

    private void mockGetSystemProperty(String name, boolean value) {
        ExtendedMockitoExpectations.mockGetSystemProperty(
                PhFlags.getSystemPropertyName(name), value);
    }

    private void verifyGetBooleanSystemPropertyNotCalled(String name) {
        ExtendedMockitoExpectations.verifyGetBooleanSystemPropertyNotCalled(
                PhFlags.getSystemPropertyName(name));
    }

    private void verifyGetBooleanDeviceConfigFlagNotCalled(String name) {
        ExtendedMockitoExpectations.verifyGetBooleanDeviceConfigFlagNotCalled(
                NAMESPACE_ADSERVICES, name);
    }

    /** Interface used to abstract the feature flags / kill switches guarding a flag. */
    private interface FlagGuard {
        void setEnabled(boolean value);
    }
}
