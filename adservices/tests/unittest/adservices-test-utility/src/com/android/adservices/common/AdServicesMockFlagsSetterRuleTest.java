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

import static com.android.adservices.common.MissingFlagBehavior.THROWS_EXCEPTION;
import static com.android.adservices.common.MissingFlagBehavior.USES_EXPLICIT_DEFAULT;
import static com.android.adservices.common.MissingFlagBehavior.USES_JAVA_LANGUAGE_DEFAULT;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import com.android.adservices.service.Flags;

import org.junit.Test;

@SuppressWarnings("deprecation")
public final class AdServicesMockFlagsSetterRuleTest
        extends AdServicesFlagsSetterRuleForUnitTestsTestCase<AdServicesMockFlagsSetterRule> {

    @Override
    protected AdServicesMockFlagsSetterRule newRule() {
        return new AdServicesMockFlagsSetterRule(mock(Flags.class));
    }

    @Test
    public void testConstructor_invalidValues() {
        assertThrows(NullPointerException.class, () -> new AdServicesMockFlagsSetterRule(null));

        // not a mock
        assertThrows(
                IllegalArgumentException.class,
                () -> new AdServicesMockFlagsSetterRule(new Flags() {}));
    }

    @Test
    public void testSetMissingFlagBehavior() {
        var rule = newRule();

        assertThrows(
                UnsupportedOperationException.class,
                () -> rule.setMissingFlagBehavior(USES_EXPLICIT_DEFAULT));
        assertThrows(
                UnsupportedOperationException.class,
                () -> rule.setMissingFlagBehavior(THROWS_EXCEPTION));

        expect.withMessage("setMockingMode(USES_JAVA_LANGUAGE_DEFAULT)")
                .that(rule.setMissingFlagBehavior(USES_JAVA_LANGUAGE_DEFAULT))
                .isSameInstanceAs(rule);
    }
}
