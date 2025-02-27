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

import static android.provider.DeviceConfig.SYNC_DISABLED_MODE_PERSISTENT;
import static android.provider.DeviceConfig.SYNC_DISABLED_MODE_UNTIL_REBOOT;

import static com.android.adservices.shared.meta_testing.CommonDescriptions.AClassWithDefaultSetSyncDisabledModeForTest;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.app.UiAutomation;
import android.platform.test.annotations.DisabledOnRavenwood;

import com.android.adservices.shared.testing.SdkLevelSupportRule;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.flags.DeviceSideFlagsPreparerClassRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.Description;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class DeviceSideFlagsPreparerClassRuleTestCase<
                R extends DeviceSideFlagsPreparerClassRule<R>>
        extends AbstractFlagsPreparerClassRuleTestCase<R> {

    // TODO(b/342639109): move to SidelessTestCase instead (would require an abstract
    // getSdkLevelSupportRule() method())
    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAnyLevel();

    protected abstract R newRule();

    // TODO(b/335935200): Ravenwood doesn't support runWithShellPermissionIdentity(), we need to
    // figure out the alternative
    @DisabledOnRavenwood(blockedBy = UiAutomation.class)
    // NOTE: we could move this test to the superclass (so it's also tested by HostSide), but it
    // would be kind of an overkill, as it would require to abstract the getDeviceConfigSyncMode()
    // and setDeviceConfigSyncMode), not to mention that it would need to use our own APIs (like
    // DeviceGateway itself) to implement them (which here we're using DeviceConfig's)
    @Test
    @RequiresSdkLevelAtLeastT(reason = "DeviceConfig.getSyncDisabledMode() is not available on S")
    public void testDeviceConfigIntegration() throws Throwable {
        R rule = newRule();
        int modeBefore = getDeviceConfigSyncMode("before");
        AtomicInteger modeInside = new AtomicInteger();
        mTestBody.onEvaluate(() -> modeInside.set(getDeviceConfigSyncMode("inside")));

        setDeviceConfigSyncMode("before", SYNC_DISABLED_MODE_UNTIL_REBOOT);
        Description testSuite =
                Description.createSuiteDescription(
                        AClassWithDefaultSetSyncDisabledModeForTest.class);
        testSuite.addChild(mTest);
        try {
            rule.apply(mTestBody, testSuite).evaluate();
        } finally {
            // Restore it
            setDeviceConfigSyncMode("after", modeBefore);
        }

        // Default constructor sets it as SYNC_DISABLED_MODE_PERSISTENT
        expect.withMessage("DeviceConfig.getSyncDisabledMode() inside test")
                .that(modeInside.get())
                .isEqualTo(SYNC_DISABLED_MODE_PERSISTENT);
    }

    private int getDeviceConfigSyncMode(String when) {
        int mode =
                runWithShellPermissionIdentity(
                        () -> android.provider.DeviceConfig.getSyncDisabledMode());
        mLog.v("DeviceConfig.getSyncDisabledMode() %s: %d", when, mode);
        return mode;
    }

    private void setDeviceConfigSyncMode(String when, int mode) {
        mLog.v("DeviceConfig.setSyncDisabledMode(%d) %s", mode, when);
        runWithShellPermissionIdentity(
                () -> android.provider.DeviceConfig.setSyncDisabledMode(mode));
    }
}
