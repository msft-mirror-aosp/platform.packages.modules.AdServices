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

import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_BACK_COMPAT;
import static com.android.adservices.service.FlagsConstants.KEY_GLOBAL_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.NAMESPACE_ADSERVICES;
import static com.android.adservices.service.FlagsTest.getConstantValue;

import static org.junit.Assert.assertThrows;

import androidx.annotation.Nullable;

import com.android.adservices.service.fixture.TestableSystemProperties;
import com.android.adservices.shared.testing.AndroidLogger;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.flags.TestableFlagsBackend;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.truth.Expect;

import java.util.Objects;
import java.util.Optional;

/** Provides utility methods for tests */
public final class PhFlagsTestHelper {

    private final Logger mLog = new Logger(AndroidLogger.getInstance(), PhFlagsTestHelper.class);

    private final FlagGuard mGlobalKillSwitchGuard = value -> setGlobalKillSwitch(!value);

    private final Flags mPhFlags;
    private final TestableFlagsBackend mBackend;
    private final boolean mIsRaw;
    private final Expect mExpect;

    PhFlagsTestHelper(Flags flags, TestableFlagsBackend backend, boolean isRaw, Expect expect) {
        mLog.i("PhFlagsTestHelper(flags=%s, backend=%s, isRaw=%b)", flags, backend, isRaw);
        mPhFlags = Objects.requireNonNull(flags, "flags cannot be null");
        mBackend = Objects.requireNonNull(backend, "backend cannot be null");
        mIsRaw = isRaw;
        mExpect = Objects.requireNonNull(expect, "expect cannot be null");
    }

    /** Tests a featureFlag (DeviceConfig FeatureFlag) guarded by a {@code guard}. */
    public void testGuardedFeatureFlag(
            String flagName,
            Boolean defaultValue,
            FeatureFlagType type,
            @Nullable FlagGuard guard,
            Flaginator<Flags, Boolean> flaginator) {

        if (mIsRaw) {
            mLog.d("testGuardedFeatureFlag(%s): running as 'unguarded' on raw flags", flagName);
            testConfigFlag(flagName, defaultValue, flaginator);
            return;
        }

        // This is the value of the getter when it's "disabled"
        boolean disabledValue = type.equals(FeatureFlagType.LEGACY_KILL_SWITCH);

        if (guard != null) {
            // First check the behavior when the guarding flags are in place(like kill switches on)
            guard.setEnabled(false);
            mExpect.withMessage(
                            "getter of %s by default when guarding flags are off / kill switch on",
                            flagName)
                    .that(flaginator.getFlagValue(mPhFlags))
                    .isEqualTo(disabledValue);

            // Make sure DeviceConfig was not called
            verifyGetBooleanDeviceConfigFlagNotCalled(flagName);

            // Then enable the guarding flags [Disables the guard / turn kill switches off.]
            guard.setEnabled(true);
        }

        // Without any overriding, the value is the hard coded constant.
        mExpect.withMessage("getter of %s by default when guarding kill switches are off", flagName)
                .that(flaginator.getFlagValue(mPhFlags))
                .isEqualTo(defaultValue);

        // Now overriding the device config flag and system properties, so the expected value
        // is driven by the system properties one (and the feature flag type)
        boolean deviceConfigValue = !defaultValue;
        setAdservicesFlag(flagName, deviceConfigValue);

        mExpect.withMessage("getter of %s when overridden by device config value", flagName)
                .that(flaginator.getFlagValue(mPhFlags))
                .isEqualTo(deviceConfigValue);
    }

    private void testGuardedFeatureFlagBackedBySystemProperty(
            String flagName,
            String defaultValueConstant,
            FeatureFlagType type,
            @Nullable FlagGuard guard,
            Flaginator<Flags, Boolean> flaginator) {
        // This is the value hardcoded by a constant on Flags.java
        boolean constantValue = getConstantValue(defaultValueConstant);
        // This is the default value for the flag's getter - ideally it should be the same as the
        // constant value, but it's the opposite when it's backed by a "legacy" kill switch.
        boolean defaultValue =
                type.equals(FeatureFlagType.FEATURE_FLAG_BACKED_BY_LEGACY_KILL_SWITCH)
                        ? !constantValue
                        : constantValue;

        // This is the value of the getter when it's "disabled"
        boolean disabledValue = type.equals(FeatureFlagType.LEGACY_KILL_SWITCH);

        mLog.d(
                "testFeatureFlagBackedBySystemProperty(flagName=%s) part 1:"
                        + " defaultValueConstant=%s, constantValue=%s, type=%s, defaultValue=%s,"
                        + " disabledValue=%s",
                flagName, defaultValueConstant, constantValue, type, defaultValue, disabledValue);

        if (guard != null) {
            // First check the behavior when the guarding flags are in place(like kill switches on)
            guard.setEnabled(false);
            mExpect.withMessage(
                            "getter of %s by default when guarding kill switches are on",
                            defaultValueConstant)
                    .that(flaginator.getFlagValue(mPhFlags))
                    .isEqualTo(disabledValue);

            // Make sure neither DeviceConfig nor SystemProperty was called
            verifyGetBooleanSystemPropertyNotCalled(flagName);
            verifyGetBooleanDeviceConfigFlagNotCalled(flagName);

            // Then enable the guarding flags
            guard.setEnabled(true);
        }

        // Without any overriding, the value is the hard coded constant.
        mExpect.withMessage(
                        "getter of %s by default when guarding kill switches are off",
                        defaultValueConstant)
                .that(flaginator.getFlagValue(mPhFlags))
                .isEqualTo(defaultValue);

        if (mIsRaw) {
            mLog.d(
                    "testGuardedFeatureFlagBackedBySystemProperty(%s): skipping part 2 on raw"
                            + " flags",
                    flagName);
            return;
        }

        // Now overriding the device config flag and system properties, so the expected value
        // is driven by the system properties one (and the feature flag type)
        boolean systemPropertyValue = !constantValue;
        boolean deviceConfigValue = !systemPropertyValue;
        boolean expectedFlagValue =
                type.equals(FeatureFlagType.FEATURE_FLAG_BACKED_BY_LEGACY_KILL_SWITCH)
                        ? !systemPropertyValue
                        : systemPropertyValue;
        mLog.d(
                "testFeatureFlagBackedBySystemProperty(flagName=%s) part 2:"
                        + " defaultValueConstant=%s, systemPropertyValue=%s, deviceConfigValue=%s,"
                        + " expectedFlagValue=%s",
                flagName,
                defaultValueConstant,
                systemPropertyValue,
                deviceConfigValue,
                expectedFlagValue);
        setSystemProperty(flagName, systemPropertyValue);
        setAdservicesFlag(flagName, deviceConfigValue);

        mExpect.withMessage(
                        "getter of %s when overridden by system property value",
                        defaultValueConstant)
                .that(flaginator.getFlagValue(mPhFlags))
                .isEqualTo(expectedFlagValue);
    }

    /**
     * Tests the behavior of a boolean flag that is guarded by the global kill switch.
     *
     * @param flagName name of the flag
     * @param defaultValueConstant name of the Java constant (on Flags.java) (like {@code
     *     "COBALT_LOGGING_ENABLED"} defining the default value of the flag
     * @param flaginator helper object used to get the value of the flag being tested
     */
    public void testFeatureFlagBackedBySystemPropertyGuardedByGlobalKs(
            String flagName, String defaultValueConstant, Flaginator<Flags, Boolean> flaginator) {
        testGuardedFeatureFlagBackedBySystemProperty(
                flagName, defaultValueConstant, mGlobalKillSwitchGuard, flaginator);
    }

    // TODO(b/326254556): remove if not used (other than by testFeatureFlagGuardedByGlobalKs()
    //  above, which passes mGlobalKillSwitchGuard.
    // TODO(b/330796095): looks like testConsentManagerDebugMode() is the only caller (other than
    // testFeatureFlagGuardedByGlobalKs() from this class), so it should be removed (or made
    // private)

    /**
     * Tests the behavior of a feature flag that is guarded by a "generic" guard (typically the
     * global kill switch and a per-API kill switch).
     *
     * @param flagName name of the legacy kill switch flag
     * @param defaultValueConstant name of the Java constant (on Flags.java) (like {@code
     *     "MDD_LOGGER_KILL_SWITCH"} defining the default value of the flag
     * @param guard helper object used enable / disable the guarding flags
     * @param flaginator helper object used to get the value of the flag being tested
     */
    public void testGuardedFeatureFlagBackedBySystemProperty(
            String flagName,
            String defaultValueConstant,
            FlagGuard guard,
            Flaginator<Flags, Boolean> flaginator) {
        testGuardedFeatureFlagBackedBySystemProperty(
                flagName, defaultValueConstant, FeatureFlagType.FEATURE_FLAG, guard, flaginator);
    }

    /**
     * Tests the behavior of a feature flag and verifies default value, overridden value are
     * fetched.
     */
    public void testConfigFlag(
            String flagName, String defaultConstantValue, Flaginator<Flags, String> flaginator) {
        testConfigFlag(
                flagName,
                defaultConstantValue,
                flaginator,
                /* overriddenValue= */ "new" + defaultConstantValue);
    }

    /**
     * Tests the behavior of a feature flag and verifies default value and provided overridden
     * value.
     */
    public void testConfigFlag(
            String flagName,
            String defaultConstantValue,
            Flaginator<Flags, String> flaginator,
            String overriddenValue) {
        testFeatureFlagDefaultOverriddenAndIllegalValue(
                flagName,
                defaultConstantValue,
                flaginator,
                overriddenValue,
                /* illegalValue= */ Optional.empty());
    }

    /**
     * Tests the behavior of a config flag and verifies default value, overridden value are fetched
     * [Backed by system property], making sure it's a positive number.
     */
    public void testPositiveConfigFlagBackedBySystemProperty(
            String flagName, Long defaultConstantValue, Flaginator<Flags, Long> flaginator) {
        testFeatureFlagDefaultOverriddenAndIllegalValueBackedBySystemProperty(
                flagName,
                defaultConstantValue,
                flaginator,
                /* deviceConfigOverriddenValue= */ 1L + defaultConstantValue,
                /* systemPropertyOverriddenValue= */ 2L + defaultConstantValue,
                /* illegalValue= */ Optional.of(-1L));
    }

    /**
     * Tests the behavior of a feature flag and verifies default value, overridden value are fetched
     * [Backed by system property].
     */
    public void testConfigFlagBackedBySystemProperty(
            String flagName, Boolean defaultConstantValue, Flaginator<Flags, Boolean> flaginator) {
        testFeatureFlagDefaultOverriddenAndIllegalValueBackedBySystemProperty(
                flagName,
                defaultConstantValue,
                flaginator,
                /* deviceConfigOverriddenValue= */ !defaultConstantValue,
                /* systemPropertyOverriddenValue= */ defaultConstantValue,
                /* illegalValue= */ Optional.empty());
    }

    /**
     * Tests the behavior of a config flag and verifies default value, overridden value are fetched
     * [Backed by system property], making sure it's a positive number.
     */
    public void testPositiveConfigFlagBackedBySystemProperty(
            String flagName, Integer defaultConstantValue, Flaginator<Flags, Integer> flaginator) {
        testFeatureFlagDefaultOverriddenAndIllegalValueBackedBySystemProperty(
                flagName,
                defaultConstantValue,
                flaginator,
                /* deviceConfigOverriddenValue= */ 1 + defaultConstantValue,
                /* systemPropertyOverriddenValue= */ 2 + defaultConstantValue,
                /* illegalValue= */ Optional.of(-1));
    }

    /**
     * Tests the behavior of a feature flag and verifies default value, overridden value are fetched
     * [Backed by system property].
     */
    public void testConfigFlagBackedBySystemProperty(
            String flagName, Integer defaultConstantValue, Flaginator<Flags, Integer> flaginator) {
        testFeatureFlagDefaultOverriddenAndIllegalValueBackedBySystemProperty(
                flagName,
                defaultConstantValue,
                flaginator,
                /* deviceConfigOverriddenValue= */ 1 + defaultConstantValue,
                /* systemPropertyOverriddenValue= */ 2 + defaultConstantValue,
                /* illegalValue= */ Optional.empty());
    }

    /**
     * Tests the behavior of a feature flag and verifies default value, overridden value are
     * fetched.
     */
    public void testConfigFlag(
            String flagName, Integer defaultConstantValue, Flaginator<Flags, Integer> flaginator) {
        testFeatureFlagDefaultOverriddenAndIllegalValue(
                flagName,
                defaultConstantValue,
                flaginator,
                /* overriddenValue= */ defaultConstantValue + 1,
                /* illegalValue= */ Optional.empty());
    }

    /**
     * Tests the behavior of a feature flag and verifies default value, overridden value are
     * fetched.
     */
    public void testConfigFlag(
            String flagName, Float defaultConstantValue, Flaginator<Flags, Float> flaginator) {
        testFeatureFlagDefaultOverriddenAndIllegalValue(
                flagName,
                defaultConstantValue,
                flaginator,
                /* overriddenValue= */ defaultConstantValue + 1.0f,
                /* illegalValue= */ Optional.empty());
    }

    /**
     * Tests the behavior of a feature flag and verifies default value, overridden value are
     * fetched.
     */
    public void testConfigFlag(
            String flagName, Long defaultConstantValue, Flaginator<Flags, Long> flaginator) {
        testFeatureFlagDefaultOverriddenAndIllegalValue(
                flagName,
                defaultConstantValue,
                flaginator,
                /* overriddenValue= */ defaultConstantValue + 1,
                /* illegalValue= */ Optional.empty());
    }

    /**
     * Tests the behavior of a feature flag and verifies default value, overridden value and
     * verifies that correct exception is thrown in case a non-positive value is overridden and
     * fetched.
     */
    public void testPositiveConfigFlag(
            String flagName, Long defaultConstantValue, Flaginator<Flags, Long> flaginator) {
        testFeatureFlagDefaultOverriddenAndIllegalValue(
                flagName,
                defaultConstantValue,
                flaginator,
                /* overriddenValue= */ defaultConstantValue + 1,
                /* illegalValue= */ Optional.of(-1L));
    }

    /**
     * Tests the behavior of a feature flag and verifies default value, overridden value and
     * verifies that correct exception is thrown in case a non-positive value is overridden and
     * fetched.
     */
    public void testPositiveConfigFlag(
            String flagName, Float defaultConstantValue, Flaginator<Flags, Float> flaginator) {
        testFeatureFlagDefaultOverriddenAndIllegalValue(
                flagName,
                defaultConstantValue,
                flaginator,
                /* overriddenValue= */ defaultConstantValue + 1,
                /* illegalValue= */ Optional.of(-1f));
    }

    /**
     * Tests the behavior of a feature flag and verifies default value, overridden value and
     * verifies that correct exception is thrown in case a non-positive value is overridden and
     * fetched.
     */
    public void testPositiveConfigFlag(
            String flagName, Integer defaultConstantValue, Flaginator<Flags, Integer> flaginator) {
        testFeatureFlagDefaultOverriddenAndIllegalValue(
                flagName,
                defaultConstantValue,
                flaginator,
                /* overriddenValue= */ defaultConstantValue + 1,
                /* illegalValue= */ Optional.of(-1));
    }

    /** Tests the behavior of a feature flag and verifies default value, overridden value. */
    public void testConfigFlag(
            String flagName, Boolean defaultConstantValue, Flaginator<Flags, Boolean> flaginator) {
        testFeatureFlagDefaultOverriddenAndIllegalValue(
                flagName,
                defaultConstantValue,
                flaginator,
                /* overriddenValue= */ !defaultConstantValue,
                /* illegalValue= */ Optional.empty());
    }

    private <T> void testFeatureFlagDefaultOverriddenAndIllegalValue(
            String flagName,
            T defaultConstantValue,
            Flaginator<Flags, T> flaginator,
            T overriddenValue,
            Optional<T> illegalValue) {
        // Without any overriding, the value is the hard coded constant.
        mExpect.withMessage("getter of %s by default", flagName)
                .that(flaginator.getFlagValue(mPhFlags))
                .isEqualTo(defaultConstantValue);

        setAdservicesFlag(flagName, "" + overriddenValue);
        // After overriding, the flag returns the overridden value.
        mExpect.withMessage("getter of %s after overriding ", flagName)
                .that(flaginator.getFlagValue(mPhFlags))
                .isEqualTo(overriddenValue);

        if (mIsRaw) {
            mLog.d(
                    "testFeatureFlagDefaultOverriddenAndIllegalValue(%s):"
                            + " testFeatureFlagForIllegalValue() part on raw flags",
                    flagName);
            return;
        }

        if (illegalValue.isPresent()) {
            testFeatureFlagForIllegalValue(flagName, flaginator, illegalValue.get());
        }
    }

    private <T> void testFeatureFlagDefaultOverriddenAndIllegalValueBackedBySystemProperty(
            String flagName,
            T defaultConstantValue,
            Flaginator<Flags, T> flaginator,
            T deviceConfigOverriddenValue,
            T systemPropertyOverriddenValue,
            Optional<T> illegalValue) {
        // Without any overriding, the value is the hard coded constant.
        mExpect.withMessage("getter of %s by default", flagName)
                .that(flaginator.getFlagValue(mPhFlags))
                .isEqualTo(defaultConstantValue);

        setAdservicesFlag(flagName, "" + deviceConfigOverriddenValue);
        // After overriding the device config, the flag returns the overridden value.
        mExpect.withMessage("getter of %s after overriding ", flagName)
                .that(flaginator.getFlagValue(mPhFlags))
                .isEqualTo(deviceConfigOverriddenValue);

        if (mIsRaw) {
            mLog.d(
                    "testFeatureFlagDefaultOverriddenAndIllegalValueBackedBySystemProperty(%s):"
                            + " skipping system-property and illegal value checking on raw flags",
                    flagName);
            return;
        }

        if (illegalValue.isPresent()) {
            testFeatureFlagForIllegalValue(flagName, flaginator, illegalValue.get());
        }

        // After overriding with system property, system property should take precedence.
        setSystemProperty(flagName, systemPropertyOverriddenValue);
        mExpect.withMessage("getter of %s after overriding with system property ", flagName)
                .that(flaginator.getFlagValue(mPhFlags))
                .isEqualTo(systemPropertyOverriddenValue);
    }

    /** Checks whether setting a feature flag to an illegal value throws exception. */
    public <T> void testFeatureFlagForIllegalValue(
            String flagName, Flaginator<Flags, T> flaginator, T illegalValue) {
        if (mIsRaw) {
            mLog.d("testFeatureFlagForIllegalValue(%s): skipping on raw flags", flagName);
            return;
        }
        setAdservicesFlag(flagName, "" + illegalValue);
        // After overriding with illegal value, fetching of flag should throw exception.
        assertThrows(IllegalArgumentException.class, () -> flaginator.getFlagValue(mPhFlags));
    }

    /**
     * Tests the behavior of a feature flag that is guarded by the global kill switch but whose
     * {@code DeviceConfig} flag is a legacy kill switch flag (i.e., when the kill switch is
     * enabled, the feature flag is disabled and viceversa)
     *
     * @param flagName name of the flag
     * @param defaultValueConstant name of the Java constant (on Flags.java) (like {@code
     *     "MEASUREMENT_KILL_SWITCH"} defining the default value of the flag
     * @param flaginator helper object used to get the value of the flag being tested
     */
    public void testFeatureFlagBackedBySystemPropertyGuardedByLegacyKillSwitch(
            String flagName, String defaultValueConstant, Flaginator<Flags, Boolean> flaginator) {
        testFeatureFlagBackedBySystemPropertyGuardedByLegacyKillSwitch(
                flagName, defaultValueConstant, mGlobalKillSwitchGuard, flaginator);
    }

    /**
     * Tests the behavior of a feature flag that is guarded by a "generic" guard (typically the
     * global kill switch and a per-API kill switch) but whose {@code DeviceConfig} flag is a legacy
     * kill switch flag (i.e., when the kill switch is enabled, the feature flag is disabled and
     * viceversa)
     *
     * @param flagName name of the flag
     * @param defaultValueConstant name of the Java constant (on Flags.java) (like {@code
     *     "MEASUREMENT_KILL_SWITCH"} defining the default value of the flag
     * @param flaginator helper object used to get the value of the flag being tested
     */
    public void testFeatureFlagBackedBySystemPropertyGuardedByLegacyKillSwitch(
            String flagName,
            String defaultValueConstant,
            FlagGuard guard,
            Flaginator<Flags, Boolean> flaginator) {
        testGuardedFeatureFlagBackedBySystemProperty(
                flagName,
                defaultValueConstant,
                FeatureFlagType.FEATURE_FLAG_BACKED_BY_LEGACY_KILL_SWITCH,
                guard,
                flaginator);
    }

    public void testLegacyKillSwitchGuardedByLegacyKillSwitch(
            String flagName,
            String defaultValueConstant,
            FlagGuard guard,
            Flaginator<Flags, Boolean> flaginator) {
        testGuardedFeatureFlagBackedBySystemProperty(
                flagName,
                defaultValueConstant,
                FeatureFlagType.LEGACY_KILL_SWITCH,
                guard,
                flaginator);
    }

    /**
     * Tests the behavior of a feature flag that is not guarded by any other flag but whose {@code
     * DeviceConfig} flag is a legacy kill switch flag (i.e., when the kill switch is enabled, the
     * feature flag is disabled and viceversa)
     *
     * @param flagName name of the flag
     * @param defaultValueConstant name of the Java constant (on Flags.java) (like {@code
     *     "ADID_KILL_SWITCH"} defining the default value of the flag
     * @param flaginator helper object used to get the value of the flag being tested
     */
    public void testUnguardedLegacyKillSwitchBackedBySystemProperty(
            String flagName, String defaultValueConstant, Flaginator<Flags, Boolean> flaginator) {
        testGuardedFeatureFlagBackedBySystemProperty(
                flagName,
                defaultValueConstant,
                FeatureFlagType.LEGACY_KILL_SWITCH,
                /* guard= */ null,
                flaginator);
    }

    /**
     * Tests the behavior of a boolean flag that is guarded by the global kill switch.
     *
     * @param flagName name of the flag
     * @param defaultValueConstant default value of the flag
     * @param flaginator helper object used to get the value of the flag being tested
     */
    public void testLegacyKillSwitchBackedBySystemProperty(
            String flagName, String defaultValueConstant, Flaginator<Flags, Boolean> flaginator) {
        testGuardedFeatureFlagBackedBySystemProperty(
                flagName,
                defaultValueConstant,
                FeatureFlagType.LEGACY_KILL_SWITCH,
                mGlobalKillSwitchGuard,
                flaginator);
    }

    /** Sets the value of {@code KEY_GLOBAL_KILL_SWITCH} */
    public void setGlobalKillSwitch(boolean value) {
        if (SdkLevel.isAtLeastT()) {
            mockGetAdServicesFlag(KEY_GLOBAL_KILL_SWITCH, value);
        } else {
            mockGetAdServicesFlag(KEY_ENABLE_BACK_COMPAT, !value);
        }
    }

    /** Sets the value of {@code KEY_MEASUREMENT_KILL_SWITCH} */
    public void setMsmtKillSwitch(boolean value) {
        // NOTE: need to set global kill-switch as well, as getMeasurementEnabled() calls it first
        setGlobalKillSwitch(value);
        mockGetAdServicesFlag(KEY_MEASUREMENT_KILL_SWITCH, value);
    }

    private static void verifyGetBooleanSystemPropertyNotCalled(String name) {
        DeviceConfigAndSystemPropertiesExpectations.verifyGetBooleanSystemPropertyNotCalled(
                PhFlags.getSystemPropertyName(name));
    }

    private static void verifyGetBooleanDeviceConfigFlagNotCalled(String name) {
        DeviceConfigAndSystemPropertiesExpectations.verifyGetBooleanDeviceConfigFlagNotCalled(
                NAMESPACE_ADSERVICES, name);
    }

    /** Sets the system property of a key with {@code value}. */
    public <T> void setSystemProperty(String name, T value) {
        setSystemProperty(name, String.valueOf(value));
    }

    private void setSystemProperty(String name, String value) {
        mLog.d("setSystemProperty(): %s=%s", name, value);
        TestableSystemProperties.set(PhFlags.getSystemPropertyName(name), "" + value);
    }

    // NOTE: it would be cleaner to inline methods below and call mBackend directly, but for now
    // we're trying to minimize the changes

    void setAdservicesFlag(String name, String value) {
        mLog.d("setAdservicesFlag(%s, %s): delegating to %s", name, value, mBackend);
        mBackend.setFlag(name, value);
    }

    void setAdservicesFlag(String name, boolean value) {
        mLog.d("setAdservicesFlag(%s, %b): delegating to %s", name, value, mBackend);
        mBackend.setFlag(name, value);
    }

    void mockGetAdServicesFlag(String name, String value) {
        mLog.d("mockGetAdServicesFlag(%s, %s): delegating to %s", name, value, mBackend);
        mBackend.setFlag(name, value);
    }

    void mockGetAdServicesFlag(String name, boolean value) {
        mLog.d("mockGetAdServicesFlag(%s, %b): delegating to %s", name, value, mBackend);
        mBackend.setFlag(name, value);
    }
}
