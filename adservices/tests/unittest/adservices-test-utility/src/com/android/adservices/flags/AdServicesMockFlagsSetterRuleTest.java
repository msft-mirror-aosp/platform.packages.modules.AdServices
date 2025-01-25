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
package com.android.adservices.flags;

import static com.android.adservices.shared.testing.flags.MissingFlagBehavior.THROWS_EXCEPTION;
import static com.android.adservices.shared.testing.flags.MissingFlagBehavior.USES_EXPLICIT_DEFAULT;
import static com.android.adservices.shared.testing.flags.MissingFlagBehavior.USES_JAVA_LANGUAGE_DEFAULT;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import com.android.adservices.service.Flags;

import org.junit.Test;

@SuppressWarnings("deprecation")
public final class AdServicesMockFlagsSetterRuleTest
        extends AdServicesFlagsSetterRuleForUnitTestsTestCase<
                AdServicesMockFlagsSetterRule, Flags> {

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

    @Test
    public void testGetFlagsSnapshot() {
        var rule = newRule();

        assertThrows(UnsupportedOperationException.class, () -> rule.getFlagsSnapshot());
    }

    @Test
    public void testOnGetFlagThrows() {
        var rule = newRule();

        assertThrows(
                UnsupportedOperationException.class, () -> rule.onGetFlagThrows("DUDE", "SWEET"));
    }

    @Test
    public void testToString() {
        var rule = newRule();
        var flags = rule.getFlags();
        // should be immutable
        var expectedString = flags.toString();

        expect.withMessage("toString() right away")
                .that(flags.toString())
                .isEqualTo(expectedString);

        rule.setFlag("dude", "sweet");
        expect.withMessage("toString() after setting 1 flag")
                .that(flags.toString())
                .isEqualTo(expectedString);
        rule.setFlag("sweet", "lord");
        expect.withMessage("toString() after setting 2 flags")
                .that(flags.toString())
                .isEqualTo(expectedString);
        // make sure they're sorted
        rule.setFlag("a flag", "has a name");
        expect.withMessage("toString() after setting 3 flags")
                .that(flags.toString())
                .isEqualTo(expectedString);

        rule.setFlag("dude", "SWEEET");
        expect.withMessage("toString() after setting 3 flags")
                .that(flags.toString())
                .isEqualTo(expectedString);
    }
}
