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

import static com.android.adservices.common.AndroidSdk.RVC;
import static com.android.adservices.common.AndroidSdk.SC;
import static com.android.adservices.common.AndroidSdk.SC_V2;
import static com.android.adservices.service.Flags.APPSEARCH_ONLY;
import static com.android.adservices.service.Flags.DEFAULT_BLOCKED_TOPICS_SOURCE_OF_TRUTH;
import static com.android.adservices.service.Flags.DEFAULT_CONSENT_SOURCE_OF_TRUTH;
import static com.android.adservices.service.Flags.DEFAULT_RVC_UX_ENABLED;
import static com.android.adservices.service.Flags.ENABLE_ADEXT_SERVICE_CONSENT_DATA;
import static com.android.adservices.service.Flags.ENABLE_APPSEARCH_CONSENT_DATA;
import static com.android.adservices.service.Flags.ENABLE_MIGRATION_FROM_ADEXT_SERVICE;
import static com.android.adservices.service.Flags.GLOBAL_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_ROLLBACK_DELETION_R_ENABLED;
import static com.android.adservices.service.Flags.MEASUREMENT_KILL_SWITCH;
import static com.android.adservices.service.Flags.PPAPI_AND_ADEXT_SERVICE;
import static com.android.adservices.service.Flags.PPAPI_AND_SYSTEM_SERVER;

import android.util.Log;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.common.RequiresSdkLevelAtLeastS;
import com.android.adservices.common.RequiresSdkLevelAtLeastT;
import com.android.adservices.common.RequiresSdkRange;
import com.android.internal.util.Preconditions;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class FlagsTest extends AdServicesUnitTestCase {

    private static final String TAG = FlagsTest.class.getSimpleName();

    static final String REASON_TO_NOT_MOCK_SDK_LEVEL =
            "Uses Flags.java constant that checks SDK level when the class is instantiated, hence"
                    + " calls to static SdkLevel methods cannot be mocked";

    private final Flags mFlags = new Flags() {};

    private final Flags mGlobalKsOnFlags = new GlobalKillSwitchAwareFlags(true);
    private final Flags mGlobalKsOffFlags = new GlobalKillSwitchAwareFlags(false);

    private final Flags mMsmtEnabledFlags = new MsmtFeatureAwareFlags(true);
    private final Flags mMsmtDisabledFlags = new MsmtFeatureAwareFlags(false);

    private final Flags mMsmtKsOnFlags = new MsmtKillSwitchAwareFlags(true);
    private final Flags mMsmtKsOffFlags = new MsmtKillSwitchAwareFlags(false);

    @Test
    @RequiresSdkRange(atMost = RVC, reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testConsentSourceOfTruth_isR() {
        assertConsentSourceOfTruth(PPAPI_AND_ADEXT_SERVICE);
    }

    @Test
    @RequiresSdkRange(atLeast = SC, atMost = SC_V2, reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testConsentSourceOfTruth_isS() {
        assertConsentSourceOfTruth(APPSEARCH_ONLY);
    }

    @Test
    @RequiresSdkLevelAtLeastT(reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testConsentSourceOfTruth_isAtLeastT() {
        assertConsentSourceOfTruth(PPAPI_AND_SYSTEM_SERVER);
    }

    private void assertConsentSourceOfTruth(int expected) {
        expect.withMessage("DEFAULT_CONSENT_SOURCE_OF_TRUTH")
                .that(DEFAULT_CONSENT_SOURCE_OF_TRUTH)
                .isEqualTo(expected);

        expect.withMessage("getConsentSourceOfTruth()")
                .that(mFlags.getConsentSourceOfTruth())
                .isEqualTo(expected);
    }

    @Test
    @RequiresSdkRange(atMost = RVC, reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testBlockedTopicsConsentSourceOfTruth_isR() {
        assertBlockedTopicsSourceOfTruth(PPAPI_AND_ADEXT_SERVICE);
    }

    @Test
    @RequiresSdkRange(atLeast = SC, atMost = SC_V2, reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testBlockedTopicsConsentSourceOfTruth_isS() {
        assertBlockedTopicsSourceOfTruth(APPSEARCH_ONLY);
    }

    @Test
    @RequiresSdkLevelAtLeastT(reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testBlockedTopicsConsentSourceOfTruth_isAtLeastT() {
        assertBlockedTopicsSourceOfTruth(PPAPI_AND_SYSTEM_SERVER);
    }

    private void assertBlockedTopicsSourceOfTruth(int expected) {
        expect.withMessage("DEFAULT_BLOCKED_TOPICS_SOURCE_OF_TRUTH")
                .that(DEFAULT_BLOCKED_TOPICS_SOURCE_OF_TRUTH)
                .isEqualTo(expected);

        expect.withMessage("getBlockedTopicsSourceOfTruth()")
                .that(mFlags.getBlockedTopicsSourceOfTruth())
                .isEqualTo(expected);
    }

    @Test
    @RequiresSdkRange(atMost = RVC, reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testEnableAppsearchConsentData_isR() {
        assertEnableAppsearchConsentData(false);
    }

    @Test
    @RequiresSdkRange(atLeast = SC, atMost = SC_V2, reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testEnableAppsearchConsentData_isS() {
        assertEnableAppsearchConsentData(true);
    }

    @Test
    @RequiresSdkLevelAtLeastT(reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testEnableAppsearchConsentData_isAtLeastT() {
        assertEnableAppsearchConsentData(false);
    }

    private void assertEnableAppsearchConsentData(boolean expected) {
        expect.withMessage("ENABLE_APPSEARCH_CONSENT_DATA")
                .that(ENABLE_APPSEARCH_CONSENT_DATA)
                .isEqualTo(expected);

        expect.withMessage("getEnableAppsearchConsentData()")
                .that(mFlags.getEnableAppsearchConsentData())
                .isEqualTo(expected);
    }

    @Test
    @RequiresSdkRange(atMost = RVC, reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testEnableAdExtServiceConsentData_isR() {
        assertEnableAdExtServiceConsentData(true);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testEnableAdExtServiceConsentData_isAtLeastS() {
        assertEnableAdExtServiceConsentData(false);
    }

    private void assertEnableAdExtServiceConsentData(boolean expected) {
        expect.withMessage("ENABLE_ADEXT_SERVICE_CONSENT_DATA")
                .that(ENABLE_ADEXT_SERVICE_CONSENT_DATA)
                .isEqualTo(expected);

        expect.withMessage("getEnableAdExtServiceConsentData()")
                .that(mFlags.getEnableAdExtServiceConsentData())
                .isEqualTo(expected);
    }

    @Test
    @RequiresSdkRange(atMost = RVC, reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testEnableRvcUx_isR() {
        assertEnableRvcUx(true);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testEnableRvcUx_isAtLeastS() {
        assertEnableRvcUx(false);
    }

    private void assertEnableRvcUx(boolean expected) {
        expect.withMessage("DEFAULT_RVC_UX_ENABLED")
                .that(DEFAULT_RVC_UX_ENABLED)
                .isEqualTo(expected);

        expect.withMessage("getEnableRvcUx()").that(mFlags.getEnableRvcUx()).isEqualTo(expected);
    }

    @Test
    @RequiresSdkRange(atMost = RVC, reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testMeasurementRollbackDeletionREnabled_isR() {
        assertMeasurementRollbackDeletionREnabled(true);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testaMeasurementRollbackDeletionREnabled_isAtLeastS() {
        assertMeasurementRollbackDeletionREnabled(false);
    }

    private void assertMeasurementRollbackDeletionREnabled(boolean expected) {
        expect.withMessage("MEASUREMENT_ROLLBACK_DELETION_R_ENABLED")
                .that(MEASUREMENT_ROLLBACK_DELETION_R_ENABLED)
                .isEqualTo(expected);

        expect.withMessage("getMeasurementRollbackDeletionREnabled()")
                .that(mFlags.getMeasurementRollbackDeletionREnabled())
                .isEqualTo(expected);
    }

    @Test
    @RequiresSdkRange(atMost = RVC, reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testEnableMigrationFromAdExtService_isR() {
        assertEnableMigrationFromAdExtService(false);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testEnableMigrationFromAdExtService_isAtLeastS() {
        assertEnableMigrationFromAdExtService(true);
    }

    private void assertEnableMigrationFromAdExtService(boolean expected) {
        expect.withMessage("ENABLE_MIGRATION_FROM_ADEXT_SERVICE")
                .that(ENABLE_MIGRATION_FROM_ADEXT_SERVICE)
                .isEqualTo(expected);

        expect.withMessage("getEnableMigrationFromAdExtService()")
                .that(mFlags.getEnableMigrationFromAdExtService())
                .isEqualTo(expected);
    }

    /* ********************************************************************************************
     * Tests for (legacy) kill-switch flags that are already in production - the flag name cannot
     * change, but their underlying getter / constants might.
     * ********************************************************************************************/

    @Test
    public void testGetGlobalKillSwitch() {
        // Getter
        expect.withMessage("getGlobalKillSwitch()").that(mFlags.getGlobalKillSwitch()).isTrue();

        // Constant
        expect.withMessage("GLOBAL_KILL_SWITCH").that(GLOBAL_KILL_SWITCH).isTrue();
    }

    // Should not be called directly
    private void internalHelperFortKillSwitchGuardedByGlobalKillSwitch(
            String name,
            AiPoweredKillSwitchAkaFeatureFlagTestatorPlus flaginator,
            boolean rampedUp) {
        boolean defaultValue = getConstantValue(name);

        // Getter
        expect.withMessage("getter for %s when global kill_switch is on", name)
                .that(flaginator.getFlagValue(mGlobalKsOnFlags))
                .isTrue();

        expect.withMessage("getter for %s when global kill_switch is off", name)
                .that(flaginator.getFlagValue(mGlobalKsOffFlags))
                .isEqualTo(defaultValue);

        // Constant
        expect.withMessage("%s", name).that(defaultValue).isEqualTo(!rampedUp);
    }

    private void testRampedUpKillSwitchGuardedByGlobalKillSwitch(
            String name, AiPoweredKillSwitchAkaFeatureFlagTestatorPlus flaginator) {
        internalHelperFortKillSwitchGuardedByGlobalKillSwitch(
                name, flaginator, /* rampedUp= */ true);
    }

    private void testNewKillSwitchGuardedByGlobalKillSwitch(
            String name, AiPoweredKillSwitchAkaFeatureFlagTestatorPlus flaginator) {
        internalHelperFortKillSwitchGuardedByGlobalKillSwitch(
                name, flaginator, /* rampedUp= */ false);
    }

    @Test
    public void testGetTopicsKillSwitch() {
        testNewKillSwitchGuardedByGlobalKillSwitch(
                "TOPICS_KILL_SWITCH", flags -> flags.getTopicsKillSwitch());
    }

    @Test
    public void testGetLegacyMeasurementKillSwitch() {
        testRampedUpKillSwitchGuardedByGlobalKillSwitch(
                "MEASUREMENT_KILL_SWITCH", flags -> flags.getLegacyMeasurementKillSwitch());
        expect.withMessage("getLegacyMeasurementKillSwitch()")
                .that(mFlags.getLegacyMeasurementKillSwitch())
                .isEqualTo(!mFlags.getMeasurementEnabled());
        expect.withMessage("getLegacyMeasurementKillSwitch() when global kill_switch is enabled")
                .that(mGlobalKsOnFlags.getLegacyMeasurementKillSwitch())
                .isEqualTo(!mGlobalKsOnFlags.getMeasurementEnabled());
        expect.withMessage("getLegacyMeasurementKillSwitch() when global kill_switch is enabled")
                .that(mGlobalKsOffFlags.getLegacyMeasurementKillSwitch())
                .isEqualTo(!mGlobalKsOffFlags.getMeasurementEnabled());
    }

    private void testFeatureFlagBasedOnLegacyKillSwitchAndGuardedByGlobalKillSwitch(
            String getterName,
            boolean defaultKillSwitchValue,
            AiPoweredKillSwitchAkaFeatureFlagTestatorPlus flaginator) {
        expect.withMessage("%s when global kill_switch is on", getterName)
                .that(flaginator.getFlagValue(mGlobalKsOnFlags))
                .isFalse();
        expect.withMessage("%s when global kill_switch is off", getterName)
                .that(flaginator.getFlagValue(mGlobalKsOffFlags))
                .isEqualTo(!defaultKillSwitchValue);
    }

    @Test
    public void testGetMeasurementEnabled() {
        testFeatureFlagBasedOnLegacyKillSwitchAndGuardedByGlobalKillSwitch(
                "getMeasurementEnabled()",
                MEASUREMENT_KILL_SWITCH,
                flags -> flags.getMeasurementEnabled());
    }

    /* ********************************************************************************************
     * Tests for feature flags                                                                    *
     * ********************************************************************************************/

    @Test
    public void testGetProtectedSignalsEnabled() {
        testFeatureFlagGuardedByGlobalKillSwitch(
                "PROTECTED_SIGNALS_ENABLED", flags -> flags.getProtectedSignalsEnabled());
    }

    @Test
    public void testGetCobaltLoggingEnabled() {
        testFeatureFlagGuardedByGlobalKillSwitch(
                "COBALT_LOGGING_ENABLED", flags -> flags.getCobaltLoggingEnabled());
    }

    private void testFeatureFlagGuardedByGlobalKillSwitch(
            String name, AiPoweredKillSwitchAkaFeatureFlagTestatorPlus flaginator) {
        boolean defaultValue = getConstantValue(name);

        // Getter
        expect.withMessage("getter for %s when global kill_switch is on", name)
                .that(flaginator.getFlagValue(mGlobalKsOnFlags))
                .isFalse();

        expect.withMessage("getter for %s when global kill_switch is off", name)
                .that(flaginator.getFlagValue(mGlobalKsOffFlags))
                .isEqualTo(defaultValue);

        // Constant
        expect.withMessage("%s", name).that(defaultValue).isFalse();
    }

    /* ********************************************************************************************
     * Tests for (legacy) kill-switch flags that will be refactored as feature flag - they should be
     * move to the block above once refactored.
     * ********************************************************************************************/

    // TODO(b/325074749) - remove once all flags have been converted
    /**
     * @deprecated - flags that are converted should call some method like {@code
     *     testFeatureFlagGuardedByMsmtFeatureFlag} instead.
     */
    private void testLegacyKillSwitchGuardedByMsmtKillSwitch(
            String name, AiPoweredKillSwitchAkaFeatureFlagTestatorPlus flaginator) {
        boolean defaultValue = getConstantValue(name);

        // Getter
        expect.withMessage("getter for %s when global kill_switch is on", name)
                .that(flaginator.getFlagValue(mGlobalKsOnFlags))
                .isTrue();

        expect.withMessage("getter for %s when msmt_enabled is true", name)
                .that(flaginator.getFlagValue(mMsmtEnabledFlags))
                .isEqualTo(defaultValue);
        expect.withMessage("getter for %s when msmt enabled is false", name)
                .that(flaginator.getFlagValue(mMsmtDisabledFlags))
                .isFalse();

        // TODO(b/325074749): remove 2 checks below once Flags.getLegacyMeasurementKillSwitch() is
        // gone
        // Getter using msmt_kill_switch
        expect.withMessage("getter for %s when msmt kill_switch is on", name)
                .that(flaginator.getFlagValue(mMsmtKsOnFlags))
                .isTrue();
        expect.withMessage("getter for %s when msmt kill_switch is off", name)
                .that(flaginator.getFlagValue(mMsmtKsOffFlags))
                .isEqualTo(defaultValue);

        // Constant
        expect.withMessage("%s", name).that(defaultValue).isFalse();
    }

    @Test
    public void testGetMeasurementAttributionFallbackJobKillSwitch() {
        testLegacyKillSwitchGuardedByMsmtKillSwitch(
                "MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_KILL_SWITCH",
                flag -> flag.getMeasurementAttributionFallbackJobKillSwitch());
    }

    /* ********************************************************************************************
     * Tests for feature flags that already launched - they will eventually be removed (once the
     * underlying getter is removed)
     * ********************************************************************************************/
    @Test
    public void testGetAppConfigReturnsEnabledByDefault() {
        testRetiredFeatureFlag(
                "APP_CONFIG_RETURNS_ENABLED_BY_DEFAULT",
                flags -> flags.getAppConfigReturnsEnabledByDefault());
    }

    private void testRetiredFeatureFlag(
            String name, AiPoweredKillSwitchAkaFeatureFlagTestatorPlus flaginator) {
        boolean defaultValue = getConstantValue(name);

        // Getter
        expect.withMessage("getter for %s", name)
                .that(flaginator.getFlagValue(mFlags))
                .isEqualTo(defaultValue);

        // Constant
        expect.withMessage("%s", name).that(defaultValue).isTrue();
    }

    // Not passing type (and using type.cast(value)) because most of the flags are primitive types
    // (like boolean) and T would be their object equivalent (like Boolean)
    @SuppressWarnings("TypeParameterUnusedInFormals")
    private static <T> T getConstantValue(String name) {
        Field field;
        try {
            field = Flags.class.getDeclaredField(name);
        } catch (NoSuchFieldException | SecurityException e) {
            throw new IllegalArgumentException("Could not get field " + name + ": " + e);
        }

        int modifiers = field.getModifiers();
        Preconditions.checkArgument(
                Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers),
                "field %s is not static final",
                name);

        Object value;
        try {
            value = field.get(null);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new IllegalArgumentException("Could not get value of field " + name + ": " + e);
        }

        Log.v(TAG, "getConstant(): " + name + "=" + value);

        @SuppressWarnings("unchecked")
        T castValue = (T) value;

        return castValue;
    }

    private static class GlobalKillSwitchAwareFlags implements Flags {
        private final boolean mGlobalKsOnFlags;

        GlobalKillSwitchAwareFlags(boolean globalKsEnabled) {
            mGlobalKsOnFlags = globalKsEnabled;
        }

        @Override
        public boolean getGlobalKillSwitch() {
            Log.d(TAG, this + ".getGlobalKillSwitch(): returning " + mGlobalKsOnFlags);
            return mGlobalKsOnFlags;
        }

        @Override
        public String toString() {
            StringBuilder string = new StringBuilder(getClass().getSimpleName()).append('[');
            decorateToString(string);
            return string.append(']').toString();
        }

        protected void decorateToString(StringBuilder toString) {
            toString.append("globalKsEnabled=").append(mGlobalKsOnFlags);
        }
    }

    private static final class MsmtFeatureAwareFlags extends GlobalKillSwitchAwareFlags {
        private final boolean mMsmtEnabled;

        MsmtFeatureAwareFlags(boolean msmtEnabled) {
            super(false);
            mMsmtEnabled = msmtEnabled;
        }

        @Override
        public boolean getMeasurementEnabled() {
            Log.d(TAG, this + ".getMeasurementEnabled(): returning " + mMsmtEnabled);
            return mMsmtEnabled;
        }

        @Override
        protected void decorateToString(StringBuilder toString) {
            super.decorateToString(toString);
            toString.append(", msmtEnabled=").append(mMsmtEnabled);
        }
    }

    /**
     * @deprecated - TODO(b/325074749): remove once all methods are changed to use
     *     !getMeasurementEnabled()
     */
    @Deprecated
    private static final class MsmtKillSwitchAwareFlags extends GlobalKillSwitchAwareFlags {
        private final boolean mMsmtKsEnabled;

        MsmtKillSwitchAwareFlags(boolean msmtKsEnabled) {
            super(false);
            mMsmtKsEnabled = msmtKsEnabled;
        }

        @Override
        public boolean getLegacyMeasurementKillSwitch() {
            Log.d(TAG, this + ".getLegacyMeasurementKillSwitch(): returning " + mMsmtKsEnabled);
            return mMsmtKsEnabled;
        }

        @Override
        protected void decorateToString(StringBuilder toString) {
            super.decorateToString(toString);
            toString.append(", msmtKsEnabled=").append(mMsmtKsEnabled);
        }
    }

    /**
     * "Fancy" interface used to build lambdas that can test a speficic flag on multiple {@code
     * Flag} objects.
     */
    private interface AiPoweredKillSwitchAkaFeatureFlagTestatorPlus {
        boolean getFlagValue(Flags flags);
    }

    // TODO(b/325135083): add a test to make sure all constants are annotated with FeatureFlag or
    // ConfigFlag (and only one FeatureFlag is LEGACY_KILL_SWITCH_GLOBAL). Might need to be added in
    // a separate file / Android.bp project as the annotation is currently retained on SOURCE only.
}
