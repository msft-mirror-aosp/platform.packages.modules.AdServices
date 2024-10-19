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
package com.android.adservices.shared.meta_testing;

import static com.android.adservices.shared.testing.AndroidSdk.RVC;
import static com.android.adservices.shared.testing.AndroidSdk.SC;
import static com.android.adservices.shared.testing.AndroidSdk.SC_V2;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.DISABLED_SOMEHOW;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.NONE;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.PERSISTENT;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.UNSUPPORTED;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.UNTIL_REBOOT;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.RequiresSdkRange;
import com.android.adservices.shared.testing.device.DeviceConfig;
import com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest;

import org.junit.Before;
import org.junit.Test;

/**
 * Integration test for {@link DeviceConfig} implementations.
 *
 * <p>It executes the commands and checks the result; it's needed to make sure the output of {@code
 * device_config} is properly parsed.
 *
 * @param <T> implementation type
 */
public abstract class DeviceConfigIntegrationTestCase<T extends DeviceConfig>
        extends IntegrationTestCase {

    private T mDeviceConfig;

    /** Creates a new instance of the {@link DeviceConfig} implementation being tested */
    protected abstract T newFixture();

    @Before
    public final void setFixture() {
        mDeviceConfig = newFixture();
        assertWithMessage("newFixture()").that(mDeviceConfig).isNotNull();
    }

    @Test
    @RequiresSdkRange(atMost = RVC)
    public final void testGetAndSetSyncDisabledMode_R() {
        expect.withMessage("getSyncDisabledMode() initially")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(UNSUPPORTED);

        mDeviceConfig.setSyncDisabledMode(UNTIL_REBOOT);

        expect.withMessage("getSyncDisabledMode() after setting to %s", UNTIL_REBOOT)
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(UNSUPPORTED);
    }

    @Test
    @RequiresSdkRange(atLeast = SC, atMost = SC_V2)
    public final void testGetAndSetSyncDisabledMode_S_whenDisabledSomehow() {
        SyncDisabledModeForTest initialMode = mDeviceConfig.getSyncDisabledMode();
        assumeTrue("Initial mode is " + initialMode, initialMode.equals(DISABLED_SOMEHOW));

        // We don't now what the real initial mode is, so we're using UNTIL_REBOOT as it's the less
        // "invasive"
        setAssertAndReset(/* mode= */ NONE, /* initialMode= */ UNTIL_REBOOT);
    }

    @Test
    @RequiresSdkRange(atLeast = SC, atMost = SC_V2)
    public final void testGetAndSetSyncDisabledMode_S_whenNotDisabled() {
        SyncDisabledModeForTest initialMode = mDeviceConfig.getSyncDisabledMode();
        assumeTrue("Initial mode is " + initialMode, initialMode.equals(NONE));

        setAssertCheckAndReset(
                /* mode= */ UNTIL_REBOOT, /* expectedSetMode= */ DISABLED_SOMEHOW, initialMode);
    }

    @Test
    @RequiresSdkLevelAtLeastT
    public final void testGetAndSetSyncDisabledMode_TPlus_whenPersistent() {
        setAndAssert(UNTIL_REBOOT);
    }

    @Test
    @RequiresSdkLevelAtLeastT
    public final void testGetAndSetSyncDisabledMode_TPlus_whenNotPersistent() {
        setAndAssert(PERSISTENT);
    }

    private void setAndAssert(SyncDisabledModeForTest mode) {
        SyncDisabledModeForTest initialMode = mDeviceConfig.getSyncDisabledMode();
        mLog.v("setAndAssert(%s): initialMode=%s", mode, initialMode);
        assumeFalse("Initial mode is " + initialMode, initialMode.equals(mode));

        setAssertAndReset(mode, initialMode);
    }

    private void setAssertAndReset(
            SyncDisabledModeForTest mode, SyncDisabledModeForTest initialMode) {
        setAssertCheckAndReset(mode, mode, initialMode);
    }

    private void setAssertCheckAndReset(
            SyncDisabledModeForTest mode,
            SyncDisabledModeForTest expectedSetMode,
            SyncDisabledModeForTest initialMode) {
        mLog.v("setAndAssertAndReset(mode=%s, initialMode=%s)", mode, initialMode);
        try {
            mDeviceConfig.setSyncDisabledMode(mode);
            expect.withMessage(
                            "getSyncDisabledMode() after changing from %s to %s", initialMode, mode)
                    .that(mDeviceConfig.getSyncDisabledMode())
                    .isEqualTo(expectedSetMode);
        } finally {
            mDeviceConfig.setSyncDisabledMode(initialMode);
        }
    }
}
