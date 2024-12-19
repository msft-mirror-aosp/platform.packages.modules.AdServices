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
package com.android.adservices.common;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesFakeFlagsSetterRule.FakeFlags;

import org.junit.Test;

public final class AdServicesFakeFlagsSetterRuleTest
        extends AdServicesFlagsSetterRuleForUnitTestsTestCase<
                AdServicesFakeFlagsSetterRule, FakeFlags> {

    @Override
    protected FakeFlags newFlags() {
        return new FakeFlags();
    }

    @Override
    protected AdServicesFakeFlagsSetterRule newRule(FakeFlags flags) {
        return new AdServicesFakeFlagsSetterRule(flags);
    }

    @Test
    public void testDefaultConstructor() {
        var rule = new AdServicesFakeFlagsSetterRule();

        expect.withMessage("getFlags()").that(rule.getFlags()).isNotNull();
    }

    @Test
    public void testCustomConstructor_null() {
        assertThrows(NullPointerException.class, () -> new AdServicesFakeFlagsSetterRule(null));
    }

    @Test
    public void testCustomConstructor() {
        var flags = new AdServicesFakeFlagsSetterRule.FakeFlags();
        var rule = new AdServicesFakeFlagsSetterRule(flags);

        expect.withMessage("getFlags()").that(rule.getFlags()).isSameInstanceAs(flags);
    }
}
