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

import static com.android.adservices.service.CommonDebugFlags.DUMP_EQUALS;
import static com.android.adservices.service.CommonDebugFlags.DUMP_PREFIX;
import static com.android.adservices.shared.meta_testing.FlagsTestLittleHelper.expectDumpHasAllFlags;

import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.fixture.TestableSystemProperties;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule;

import org.junit.Test;

@ExtendedMockitoRule.SpyStatic(SdkLevel.class)
public final class DebugFlagsTest extends DebugFlagsTestCase<DebugFlags> {

    @Override
    protected DebugFlags newInstance() {
        return DebugFlags.getInstance();
    }

    @Override
    protected AdServicesExtendedMockitoRule getAdServicesExtendedMockitoRule() {
        return newDefaultAdServicesExtendedMockitoRuleBuilder()
                .addStaticMockFixtures(TestableSystemProperties::new)
                .build();
    }

    @Test
    public void testDump() throws Exception {
        var debugFlags = newInstance();

        expectDumpHasAllFlags(
                expect,
                DebugFlagsConstants.class,
                pw -> debugFlags.dump(pw),
                flag -> (DUMP_PREFIX + flag.second + DUMP_EQUALS));
        // Must also check the flags from its superclass
        expectDumpHasAllFlags(
                expect,
                CommonDebugFlagsConstants.class,
                pw -> debugFlags.dump(pw),
                flag -> (DUMP_PREFIX + flag.second + DUMP_EQUALS));
    }

    @Override
    protected void setDebugFlag(DebugFlags debugFlags, String name, String value) {
        String realName = PhFlags.getSystemPropertyName(name);
        mLog.v("setDebugFlag(%s, %s): setting SystemProperty %s", name, value, realName);
        TestableSystemProperties.set(realName, "" + value);
    }
}
