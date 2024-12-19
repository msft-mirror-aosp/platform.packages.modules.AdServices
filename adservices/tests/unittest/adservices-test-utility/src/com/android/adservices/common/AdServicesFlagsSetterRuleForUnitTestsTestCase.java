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

import com.android.adservices.service.Flags;
import com.android.adservices.shared.meta_testing.CommonDescriptions.AClassHasNoNothingAtAll;
import com.android.adservices.shared.meta_testing.SimpleStatement;

import org.junit.Test;
import org.junit.runner.Description;

import java.util.function.BiConsumer;

/** Base test class for {@link AdServicesFlagsSetterRuleForUnitTests} implementations. */
abstract class AdServicesFlagsSetterRuleForUnitTestsTestCase<
                R extends AdServicesFlagsSetterRuleForUnitTests<R, F>, F extends Flags>
        extends AdServicesUnitTestCase {

    /** Creates a new instance of the flags. */
    protected abstract F newFlags();

    /** Creates a new instance of the rule. */
    protected abstract R newRule(F flags);

    @Test
    public final void testSetGlobalKillSwitch() throws Throwable {
        onTest(
                (rule, flags) -> {
                    rule.setGlobalKillSwitch(true);
                    expect.withMessage("getGlobalKillSwitch()")
                            .that(flags.getGlobalKillSwitch())
                            .isTrue();

                    rule.setGlobalKillSwitch(false);
                    expect.withMessage("getGlobalKillSwitch()")
                            .that(flags.getGlobalKillSwitch())
                            .isFalse();
                });
    }

    /**
     * Evaluates a rule inside a test.
     *
     * @param consumer consumer of flags instantiated by {@link #newFlags()} and a rule instantiated
     *     by {@link #newRule(Flags)} (passing that flags).
     */
    protected void onTest(BiConsumer<R, F> consumer) throws Throwable {
        F flags = newFlags();
        if (flags == null) {
            throw new IllegalStateException("newFlags() returned null");
        }
        R rule = newRule(flags);
        if (rule == null) {
            throw new IllegalStateException("newRule() returned null");
        }
        SimpleStatement test = new SimpleStatement();
        Description description =
                Description.createTestDescription(AClassHasNoNothingAtAll.class, "butItHasATest");

        test.onEvaluate(() -> consumer.accept(rule, flags));
        rule.apply(test, description).evaluate();
        test.assertEvaluated();
    }
}
