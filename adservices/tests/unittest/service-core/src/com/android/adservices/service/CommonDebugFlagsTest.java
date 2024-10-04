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

import static com.android.adservices.service.CommonDebugFlags.DEFAULT_ADSERVICES_SHELL_COMMAND_ENABLED;
import static com.android.adservices.service.CommonDebugFlags.DUMP_EQUALS;
import static com.android.adservices.service.CommonDebugFlags.DUMP_PREFIX;
import static com.android.adservices.service.CommonDebugFlagsConstants.KEY_ADSERVICES_SHELL_COMMAND_ENABLED;
import static com.android.adservices.shared.meta_testing.FlagsTestLittleHelper.expectDumpHasAllFlags;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.fixture.TestableSystemProperties;

import org.junit.Test;

public final class CommonDebugFlagsTest extends AdServicesExtendedMockitoTestCase {

    private final CommonDebugFlags mCommonDebugFlags = new CommonDebugFlags() {};

    @Override
    protected AdServicesExtendedMockitoRule getAdServicesExtendedMockitoRule() {
        return newDefaultAdServicesExtendedMockitoRuleBuilder()
                .addStaticMockFixtures(TestableSystemProperties::new)
                .build();
    }

    @Test
    public void testGetAdServicesShellCommandEnabled() {
        testDebugFlag(
                KEY_ADSERVICES_SHELL_COMMAND_ENABLED,
                DEFAULT_ADSERVICES_SHELL_COMMAND_ENABLED,
                CommonDebugFlags::getAdServicesShellCommandEnabled);
    }

    @Test
    public void testDump() throws Exception {
        expectDumpHasAllFlags(
                expect,
                CommonDebugFlagsConstants.class,
                pw -> mCommonDebugFlags.dump(pw),
                flag -> (DUMP_PREFIX + flag.second + DUMP_EQUALS));
    }

    private void testDebugFlag(
            String flagName,
            Boolean defaultValue,
            Flaginator<CommonDebugFlags, Boolean> flaginator) {
        // Without any overriding, the value is the hard coded constant.
        expect.that(flaginator.getFlagValue(mCommonDebugFlags)).isEqualTo(defaultValue);

        boolean phOverridingValue = !defaultValue;
        setSystemProperty(flagName, String.valueOf(phOverridingValue));
        expect.that(flaginator.getFlagValue(mCommonDebugFlags)).isEqualTo(phOverridingValue);
    }

    private void setSystemProperty(String name, String value) {
        mLog.v("setSystemProperty(): %s=%s", name, value);
        TestableSystemProperties.set(PhFlags.getSystemPropertyName(name), "" + value);
    }
}
