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

import static com.android.adservices.service.FlagsTest.getConstantValue;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.service.FlagsTest.GlobalKillSwitchAwareFlags;

import org.junit.Test;

public final class CommonFlagsTest extends AdServicesUnitTestCase {

    private final CommonFlags mFlags = new CommonFlags() {};

    @Test
    public void testGetAdServicesShellCommandEnabled() {
        testFeatureFlag(
                "ADSERVICES_SHELL_COMMAND_ENABLED",
                flags -> flags.getAdServicesShellCommandEnabled());
    }

    // TODO(b/325270993): members below were copied from FlagsTest, they should be moved to a
    // FlagsTester helper

    private final Flags mGlobalKsOnFlags = new GlobalKillSwitchAwareFlags(true);
    private final Flags mGlobalKsOffFlags = new GlobalKillSwitchAwareFlags(false);

    private void testFeatureFlag(String name, Flaginator<CommonFlags, Boolean> flaginator) {
        boolean defaultValue = getConstantValue(CommonFlags.class, name);

        // Getter
        expect.withMessage("getter for %s", name)
                .that(flaginator.getFlagValue(mFlags))
                .isEqualTo(defaultValue);

        // Since the flag doesn't depend on global kill switch, it shouldn't matter if it's on or
        // off
        expect.withMessage("getter for %s when global kill_switch is on", name)
                .that(flaginator.getFlagValue(mGlobalKsOnFlags))
                .isEqualTo(defaultValue);
        expect.withMessage("getter for %s when global kill_switch is off", name)
                .that(flaginator.getFlagValue(mGlobalKsOffFlags))
                .isEqualTo(defaultValue);

        // Constant
        expect.withMessage("%s", name).that(defaultValue).isFalse();
    }
}
