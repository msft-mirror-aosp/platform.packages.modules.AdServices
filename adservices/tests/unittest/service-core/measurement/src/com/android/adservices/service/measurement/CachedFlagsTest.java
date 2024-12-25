/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.measurement;

import static org.mockito.Mockito.when;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.AdServicesMockitoTestCase;

import org.junit.Before;
import org.junit.Test;

@SmallTest
public final class CachedFlagsTest extends AdServicesMockitoTestCase {
    private static final boolean OVERRIDDEN = true;
    private static final boolean DEFAULT_BOOLEAN_FLAG_VALUE = false;
    private static final boolean OVERRIDDEN_BOOLEAN_FLAG_VALUE = true;
    private static final String DEFAULT_STRING_FLAG_VALUE = "default_string_flag_value";
    private static final String OVERRIDDEN_STRING_FLAG_VALUE = "overridden_string_flag_value";
    private CachedFlags mCachedFlags;

    @Before
    public void setUp() {
        // Override the flag to set the overridden value in the constructor,
        overrideFlags(OVERRIDDEN);
    }

    @Test
    public void testCachedFlags_sessionCacheEnabled() {
        // Enable the KillSwitch to "enabled" the session stable flag. Note the implementation did
        // NOT follow this convention that when KillSwitch is enabled, the feature should be
        // disabled.
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(true);
        mCachedFlags = new CachedFlags(mMockFlags);

        // Override the flag to the default values.
        overrideFlags(!OVERRIDDEN);

        // Verify the flag values are same as set in the constructor.
        assertFlagValues(OVERRIDDEN);
    }

    @Test
    public void testCachedFlags_sessionCacheDisabled() {
        // Disable the KillSwitch to "disable" the session stable flag. Note the implementation did
        // NOT follow this convention that when KillSwitch is disabled, the feature should be
        // enabled.
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(false);
        mCachedFlags = new CachedFlags(mMockFlags);

        // Override the flag to the default values.
        overrideFlags(!OVERRIDDEN);

        // Verify the flag values are NOT same as set in the constructor.
        assertFlagValues(!OVERRIDDEN);
    }

    private void assertFlagValues(boolean isOverridden) {
        boolean booleanValue =
                isOverridden ? OVERRIDDEN_BOOLEAN_FLAG_VALUE : DEFAULT_BOOLEAN_FLAG_VALUE;
        String stringValue =
                isOverridden ? OVERRIDDEN_STRING_FLAG_VALUE : DEFAULT_STRING_FLAG_VALUE;

        // Boolean values
        expect.withMessage("getMeasurementApiRegisterSourceKillSwitch")
                .that(mCachedFlags.getMeasurementApiRegisterSourceKillSwitch())
                .isEqualTo(booleanValue);
        expect.withMessage("getMeasurementApiRegisterSourcesKillSwitch")
                .that(mCachedFlags.getMeasurementApiRegisterSourcesKillSwitch())
                .isEqualTo(booleanValue);
        expect.withMessage("getMeasurementApiRegisterWebSourceKillSwitch")
                .that(mCachedFlags.getMeasurementApiRegisterWebSourceKillSwitch())
                .isEqualTo(booleanValue);
        expect.withMessage("getMeasurementApiRegisterTriggerKillSwitch")
                .that(mCachedFlags.getMeasurementApiRegisterTriggerKillSwitch())
                .isEqualTo(booleanValue);
        expect.withMessage("getMeasurementApiRegisterWebTriggerKillSwitch")
                .that(mCachedFlags.getMeasurementApiRegisterWebTriggerKillSwitch())
                .isEqualTo(booleanValue);
        expect.withMessage("getMeasurementApiDeleteRegistrationsKillSwitch")
                .that(mCachedFlags.getMeasurementApiDeleteRegistrationsKillSwitch())
                .isEqualTo(booleanValue);
        expect.withMessage("getMeasurementApiStatusKillSwitch")
                .that(mCachedFlags.getMeasurementApiStatusKillSwitch())
                .isEqualTo(booleanValue);
        expect.withMessage("getEnforceForegroundStatusForMeasurementRegisterSource")
                .that(mCachedFlags.getEnforceForegroundStatusForMeasurementRegisterSource())
                .isEqualTo(booleanValue);
        expect.withMessage("getEnforceForegroundStatusForMeasurementRegisterTrigger")
                .that(mCachedFlags.getEnforceForegroundStatusForMeasurementRegisterTrigger())
                .isEqualTo(booleanValue);
        expect.withMessage("getEnforceForegroundStatusForMeasurementRegisterSources")
                .that(mCachedFlags.getEnforceForegroundStatusForMeasurementRegisterSources())
                .isEqualTo(booleanValue);
        expect.withMessage("getEnforceForegroundStatusForMeasurementRegisterWebSource")
                .that(mCachedFlags.getEnforceForegroundStatusForMeasurementRegisterWebSource())
                .isEqualTo(booleanValue);
        expect.withMessage("getEnforceForegroundStatusForMeasurementRegisterWebTrigger")
                .that(mCachedFlags.getEnforceForegroundStatusForMeasurementRegisterWebTrigger())
                .isEqualTo(booleanValue);
        expect.withMessage("getEnforceForegroundStatusForMeasurementDeleteRegistrations")
                .that(mCachedFlags.getEnforceForegroundStatusForMeasurementDeleteRegistrations())
                .isEqualTo(booleanValue);
        expect.withMessage("getEnforceForegroundStatusForMeasurementStatus")
                .that(mCachedFlags.getEnforceForegroundStatusForMeasurementStatus())
                .isEqualTo(booleanValue);
        expect.withMessage("getMsmtEnableApiStatusAllowListCheck")
                .that(mCachedFlags.getMsmtEnableApiStatusAllowListCheck())
                .isEqualTo(booleanValue);

        // String values
        expect.withMessage("getMsmtApiAppAllowList")
                .that(mCachedFlags.getMsmtApiAppAllowList())
                .isEqualTo(stringValue);
        expect.withMessage("getMsmtApiAppBlockList")
                .that(mCachedFlags.getMsmtApiAppBlockList())
                .isEqualTo(stringValue);
        expect.withMessage("getWebContextClientAppAllowList")
                .that(mCachedFlags.getWebContextClientAppAllowList())
                .isEqualTo(stringValue);
    }

    private void overrideFlags(boolean isOverridden) {
        boolean booleanValue =
                isOverridden ? OVERRIDDEN_BOOLEAN_FLAG_VALUE : DEFAULT_BOOLEAN_FLAG_VALUE;
        String stringValue =
                isOverridden ? OVERRIDDEN_STRING_FLAG_VALUE : DEFAULT_STRING_FLAG_VALUE;

        when(mMockFlags.getMeasurementApiRegisterSourceKillSwitch()).thenReturn(booleanValue);
        when(mMockFlags.getMeasurementApiRegisterSourcesKillSwitch()).thenReturn(booleanValue);
        when(mMockFlags.getMeasurementApiRegisterWebSourceKillSwitch()).thenReturn(booleanValue);
        when(mMockFlags.getMeasurementApiRegisterTriggerKillSwitch()).thenReturn(booleanValue);
        when(mMockFlags.getMeasurementApiRegisterWebTriggerKillSwitch()).thenReturn(booleanValue);
        when(mMockFlags.getMeasurementApiDeleteRegistrationsKillSwitch()).thenReturn(booleanValue);
        when(mMockFlags.getMeasurementApiStatusKillSwitch()).thenReturn(booleanValue);
        when(mMockFlags.getEnforceForegroundStatusForMeasurementRegisterSource())
                .thenReturn(booleanValue);
        when(mMockFlags.getEnforceForegroundStatusForMeasurementRegisterTrigger())
                .thenReturn(booleanValue);
        when(mMockFlags.getEnforceForegroundStatusForMeasurementRegisterSources())
                .thenReturn(booleanValue);
        when(mMockFlags.getEnforceForegroundStatusForMeasurementRegisterWebSource())
                .thenReturn(booleanValue);
        when(mMockFlags.getEnforceForegroundStatusForMeasurementRegisterWebTrigger())
                .thenReturn(booleanValue);
        when(mMockFlags.getEnforceForegroundStatusForMeasurementDeleteRegistrations())
                .thenReturn(booleanValue);
        when(mMockFlags.getMsmtEnableApiStatusAllowListCheck()).thenReturn(booleanValue);
        when(mMockFlags.getEnforceForegroundStatusForMeasurementStatus()).thenReturn(booleanValue);
        when(mMockFlags.getMsmtApiAppAllowList()).thenReturn(stringValue);
        when(mMockFlags.getMsmtApiAppBlockList()).thenReturn(stringValue);
        when(mMockFlags.getWebContextClientAppAllowList()).thenReturn(stringValue);
    }
}
