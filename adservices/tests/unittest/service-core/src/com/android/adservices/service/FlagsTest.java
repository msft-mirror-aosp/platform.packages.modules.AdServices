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
import static com.android.adservices.service.Flags.MEASUREMENT_KILL_SWITCH;
import static com.android.adservices.service.Flags.MEASUREMENT_ROLLBACK_DELETION_R_ENABLED;
import static com.android.adservices.service.Flags.PPAPI_AND_ADEXT_SERVICE;
import static com.android.adservices.service.Flags.PPAPI_AND_SYSTEM_SERVER;
import static com.android.adservices.service.Flags.PROTECTED_SIGNALS_ENABLED;
import static com.android.adservices.service.Flags.TOPICS_KILL_SWITCH;

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

    private final Flags mGlobalKsEnabled = new GlobalKillSwitchAwareFlags(true);
    private final Flags mGlobalKsDisabled = new GlobalKillSwitchAwareFlags(false);

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

    private void testKillSwitchGuardedByGlobalKillSwitch(
            String name, AiPoweredKillSwitchAkaFeatureFlagTestatorPlus flaginator) {
        boolean defaultValue = getConstantValue(name);

        // Getter
        expect.withMessage("getter for %s when global kill_switch is on", name)
                .that(flaginator.getFlagValue(mGlobalKsEnabled))
                .isTrue();

        expect.withMessage("getter for %s when global kill_switch is off", name)
                .that(flaginator.getFlagValue(mGlobalKsDisabled))
                .isEqualTo(defaultValue);

        // Constant
        expect.withMessage("%s", name).that(defaultValue).isTrue();
    }

    @Test
    public void testGetTopicsKillSwitch() {
        if (true) { // TODO(b/325149426): fix constant and remove this statement
            // Getter
            expect.withMessage("getTopicsKillSwitch() when global kill_switch is enabled")
                    .that(mGlobalKsEnabled.getTopicsKillSwitch())
                    .isTrue();

            expect.withMessage("getTopicsKillSwitch() when global kill_switch is" + " disabled")
                    .that(mGlobalKsDisabled.getTopicsKillSwitch())
                    .isEqualTo(TOPICS_KILL_SWITCH);

            // Constant
            expect.withMessage("TOPICS_KILL_SWITCH").that(TOPICS_KILL_SWITCH).isFalse();

            return;
        }
        testKillSwitchGuardedByGlobalKillSwitch(
                "TOPICS_KILL_SWITCH", flags -> flags.getTopicsKillSwitch());
    }

    @Test
    public void testGetMeasurementKillSwitch() {
        if (true) { // TODO(b/325144327): fix constant and remove this statement
            // Getter
            expect.withMessage("getMeasurementKillSwitch()")
                    .that(mGlobalKsEnabled.getMeasurementKillSwitch())
                    .isTrue();
            expect.withMessage("getMeasurementKillSwitch()")
                    .that(mGlobalKsDisabled.getMeasurementKillSwitch())
                    .isEqualTo(MEASUREMENT_KILL_SWITCH);

            // Constant
            expect.withMessage("MEASUREMENT_KILL_SWITCH").that(MEASUREMENT_KILL_SWITCH).isFalse();
            return;
        }
        testKillSwitchGuardedByGlobalKillSwitch(
                "MEASUREMENT_KILL_SWITCH", flags -> flags.getMeasurementKillSwitch());
    }

    /* ********************************************************************************************
     * Tests for new feature flags.
     * ********************************************************************************************/

    @Test
    public void testGetProtectedSignalsEnabled() {
        if (true) { // TODO(b/323972771): fix constant value and remove this statement
            // Getter
            expect.withMessage(
                            "getProtectedSignalsServiceKillSwitch() when global kill_switch is"
                                    + " enabled")
                    .that(mGlobalKsEnabled.getProtectedSignalsEnabled())
                    .isFalse();

            expect.withMessage(
                            "getProtectedSignalsServiceKillSwitch() when global kill_switch is"
                                    + " disabled")
                    .that(mGlobalKsDisabled.getProtectedSignalsEnabled())
                    .isEqualTo(PROTECTED_SIGNALS_ENABLED);

            expect.withMessage("PROTECTED_SIGNALS_ENABLED")
                    .that(PROTECTED_SIGNALS_ENABLED)
                    .isTrue();
            return;
        }

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
                .that(flaginator.getFlagValue(mGlobalKsEnabled))
                .isFalse();

        expect.withMessage("getter for %s when global kill_switch is off", name)
                .that(flaginator.getFlagValue(mGlobalKsDisabled))
                .isEqualTo(defaultValue);

        // Constant
        expect.withMessage("%s", name).that(defaultValue).isFalse();
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

        // Cannot use type.cast(value) because most of the flags are primitive types (like boolean)
        // and T would be their object equivalent (like Boolean)
        @SuppressWarnings("unchecked")
        T castValue = (T) value;

        return castValue;
    }

    private static final class GlobalKillSwitchAwareFlags implements Flags {
        private final boolean mEnabled;

        GlobalKillSwitchAwareFlags(boolean enabled) {
            mEnabled = enabled;
        }

        @Override
        public boolean getGlobalKillSwitch() {
            Log.d(
                    TAG,
                    GlobalKillSwitchAwareFlags.this
                            + ".getGlobalKillSwitch(): returning "
                            + mEnabled);
            return mEnabled;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[globalKsEnabled=" + mEnabled + "]";
        }
    }

    /**
     * "Fancy" interface used to build lambdas that can test a speficic flag on multiple {@code
     * Flag} objects.
     */
    private interface AiPoweredKillSwitchAkaFeatureFlagTestatorPlus {
        boolean getFlagValue(Flags flags);
    }
}
