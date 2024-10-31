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
package com.android.adservices.shared.testing;

import static com.android.adservices.shared.testing.LogEntry.Subject.logEntry;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.adservices.shared.meta_testing.SharedSidelessTestCase;
import com.android.adservices.shared.meta_testing.SimpleStatement;
import com.android.adservices.shared.meta_testing.TestNamerRuleTester;
import com.android.adservices.shared.testing.Logger.LogLevel;
import com.android.adservices.shared.testing.Logger.RealLogger;

import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.List;

public final class AbstractRuleTest extends SharedSidelessTestCase {

    private final SimpleStatement mBaseStatement = new SimpleStatement();
    private final Description mDefaultDescription =
            Description.createTestDescription(AbstractRuleTest.class, "aTestHasNoName");

    private final ConcreteRule mRule = new ConcreteRule(mFakeRealLogger);

    @Test
    public void testApply() throws Throwable {
        mRule.apply(mBaseStatement, mDefaultDescription).evaluate();

        mBaseStatement.assertEvaluated();
        expect.withMessage("description")
                .that(mRule.description)
                .isSameInstanceAs(mDefaultDescription);
    }

    @Test
    public void testRunSafely_noErrors() {
        List<Throwable> errors = new ArrayList<>();

        mRule.runSafely(errors, () -> mLog.v("I'm fine"));

        expect.withMessage("errors").that(errors).isEmpty();
    }

    @Test
    public void testRunSafely_withError() {
        List<Throwable> errors = new ArrayList<>();
        RuntimeException exception = new RuntimeException("D'OH");

        mRule.runSafely(
                errors,
                () -> {
                    throw exception;
                });

        expect.withMessage("errors").that(errors).containsExactly(exception);

        ImmutableList<LogEntry> logEntries = mFakeRealLogger.getEntries();
        assertWithMessage("log entries").that(logEntries).hasSize(1);
        expect.withMessage("logged message")
                .about(logEntry())
                .that(logEntries.get(0))
                .hasLevel(LogLevel.ERROR)
                .hasTag("ConcreteRule")
                .hasThrowable(exception);
    }

    @Test
    public void testGetTestName() throws Throwable {
        TestNamerRuleTester<ConcreteRule> tester = new TestNamerRuleTester<>(expect, mRule);

        tester.justDoIt();
    }

    private static final class ConcreteRule extends AbstractRule {

        @Nullable public Description description;

        private ConcreteRule(RealLogger logger) {
            super(logger);
        }

        @Override
        protected void evaluate(Statement base, Description description) throws Throwable {
            this.description = description;
            base.evaluate();
        }
    }
}
