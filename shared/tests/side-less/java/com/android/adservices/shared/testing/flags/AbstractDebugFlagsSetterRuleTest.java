/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.adservices.shared.testing.flags;

import com.android.adservices.shared.meta_testing.AbstractDebugFlagsSetterRuleTestCase;
import com.android.adservices.shared.testing.DynamicLogger;
import com.android.adservices.shared.testing.NameValuePairSetter;
import com.android.adservices.shared.testing.flags.AbstractDebugFlagsSetterRuleTest.FakeDebugFlagsSetterRuleRule;

/**
 * Default test case for {@link AbstractDebugFlagsSetterRuleTestCase} implementations.
 *
 * <p>It uses a {@link FakeDebugFlagsSetterRuleRule bogus rule} so it can be run by IDEs
 *
 * <p>Notice that currently there is not Android project to run these side-less tests, so you would
 * need to use either the device-side ({@code AdServicesSharedLibrariesUnitTests}) or host-side
 * ({@code AdServicesSharedLibrariesHostTests}) project:
 *
 * <ul>
 *   <li>{@code atest AdServicesSharedLibrariesUnitTests:AbstractDebugFlagsSetterRuleTest}
 *   <li>{@code atest AdServicesSharedLibrariesHostTests:AbstractDebugFlagsSetterRuleTest}
 * </ul>
 *
 * <p>Notice that when running the host-side tests, you can use the {@code --host} option so it
 * doesn't require a connected device.
 */
public final class AbstractDebugFlagsSetterRuleTest
        extends AbstractDebugFlagsSetterRuleTestCase<FakeDebugFlagsSetterRuleRule> {

    @Override
    protected FakeDebugFlagsSetterRuleRule newRule(NameValuePairSetter setter) {
        return new FakeDebugFlagsSetterRuleRule(setter);
    }

    public static final class FakeDebugFlagsSetterRuleRule
            extends AbstractDebugFlagsSetterRule<FakeDebugFlagsSetterRuleRule> {

        protected FakeDebugFlagsSetterRuleRule(NameValuePairSetter setter) {
            super(DynamicLogger.getInstance(), setter);
        }
    }
}
