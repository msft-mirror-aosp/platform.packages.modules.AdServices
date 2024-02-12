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
import static com.android.adservices.service.Flags.ENABLE_ADEXT_SERVICE_TO_APPSEARCH_MIGRATION;
import static com.android.adservices.service.Flags.ENABLE_APPSEARCH_CONSENT_DATA;
import static com.android.adservices.service.Flags.MEASUREMENT_ROLLBACK_DELETION_R_ENABLED;
import static com.android.adservices.service.Flags.PPAPI_AND_ADEXT_SERVICE;
import static com.android.adservices.service.Flags.PPAPI_AND_SYSTEM_SERVER;

import android.util.Log;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.common.RequiresSdkLevelAtLeastS;
import com.android.adservices.common.RequiresSdkLevelAtLeastT;
import com.android.adservices.common.RequiresSdkRange;

import org.junit.Test;

public final class FlagsTest extends AdServicesUnitTestCase {

    private static final String TAG = FlagsTest.class.getSimpleName();

    static final String REASON_TO_NOT_MOCK_SDK_LEVEL =
            "Uses Flags.java constant that checks SDK level when the class is instantiated, hence"
                    + " calls to static SdkLevel methods cannot be mocked";

    private final Flags mFlags = new Flags() {};
    private final Flags mGlobalKsEnabled = new GlobalKsAwareFlags(true);
    private final Flags mGlobalKsDisabled = new GlobalKsAwareFlags(false);

    @Test
    public void testGetProtectedSignalsServiceKillSwitch() {
        expect.withMessage(
                        "getProtectedSignalsServiceKillSwitch() when global kill_switch is enabled")
                .that(mGlobalKsEnabled.getProtectedSignalsEnabled())
                .isFalse();

        expect.withMessage(
                        "getProtectedSignalsServiceKillSwitch() when global kill_switch is"
                                + " disabled")
                .that(mGlobalKsDisabled.getProtectedSignalsEnabled())
                .isEqualTo(Flags.PROTECTED_SIGNALS_ENABLED);
    }

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
    public void testEnableAdExtServiceToAppSearchMigration_isR() {
        assertEnableAdExtServiceToAppSearchMigration(false);
    }

    @Test
    @RequiresSdkRange(atLeast = SC, atMost = SC_V2, reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testEnableAdExtServiceToAppSearchMigration_isS() {
        assertEnableAdExtServiceToAppSearchMigration(true);
    }

    @Test
    @RequiresSdkLevelAtLeastT(reason = REASON_TO_NOT_MOCK_SDK_LEVEL)
    public void testEnableAdExtServiceToAppSearchMigration_isAtLeastT() {
        assertEnableAdExtServiceToAppSearchMigration(false);
    }

    private void assertEnableAdExtServiceToAppSearchMigration(boolean expected) {
        expect.withMessage("ENABLE_ADEXT_SERVICE_TO_APPSEARCH_MIGRATION")
                .that(ENABLE_ADEXT_SERVICE_TO_APPSEARCH_MIGRATION)
                .isEqualTo(expected);

        expect.withMessage("getEnableAdExtServiceToAppSearchMigration()")
                .that(mFlags.getEnableAdExtServiceToAppSearchMigration())
                .isEqualTo(expected);
    }

    private final class GlobalKsAwareFlags implements Flags {
        private final boolean mEnabled;

        GlobalKsAwareFlags(boolean enabled) {
            mEnabled = enabled;
        }

        @Override
        public boolean getGlobalKillSwitch() {
            Log.d(mTag, GlobalKsAwareFlags.this + ".getGlobalKillSwitch(): returning " + mEnabled);
            return mEnabled;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[enabled=" + mEnabled + "]";
        }
    }
}
