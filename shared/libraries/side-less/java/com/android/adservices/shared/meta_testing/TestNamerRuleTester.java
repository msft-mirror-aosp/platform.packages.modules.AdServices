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

import static com.android.adservices.shared.testing.TestNamer.DEFAULT_TEST_NAME;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.testing.TestNamer;

import com.google.common.truth.Expect;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Helper used to test {@link TestRule JUnit rules} that implements {@link TestNamer}.
 *
 * @param <R> rule being tested
 */
public final class TestNamerRuleTester<R extends TestRule & TestNamer> {

    private final Expect mExpect;
    private final R mRule;
    private final Description mDescription =
            Description.createTestDescription(TestNamerRuleTester.class, "aTestHasNoName");

    /** Default constructor. */
    public TestNamerRuleTester(Expect expect, R rule) {
        mExpect = Objects.requireNonNull(expect);
        mRule = Objects.requireNonNull(rule);
    }

    /** Tests the expected behavior when running or not running a test. */
    public void justDoIt() throws Throwable {
        mExpect.withMessage("getTestName() BEFORE running a test")
                .that(mRule.getTestName())
                .isEqualTo(DEFAULT_TEST_NAME);

        assertInsideTest();

        mExpect.withMessage("getTestName() AFTER running a test")
                .that(mRule.getTestName())
                .isEqualTo(DEFAULT_TEST_NAME);

        assertAfterTestThatFails();
    }

    private void assertInsideTest() throws Throwable {
        // Cannot use String because it must be final
        AtomicReference<String> nameRef = new AtomicReference<>();
        Statement statement =
                new Statement() {

                    @Override
                    public void evaluate() throws Throwable {
                        nameRef.set(mRule.getTestName());
                    }
                };

        mRule.apply(statement, mDescription).evaluate();

        String testName = nameRef.get();
        mExpect.withMessage("getTestName()").that(testName).contains("TestNamerRuleTester");
        mExpect.withMessage("getTestName()").that(testName).contains("aTestHasNoName()");
    }

    private void assertAfterTestThatFails() throws Throwable {
        Throwable expected = new Throwable("D'OH!");

        Statement statement =
                new Statement() {

                    @Override
                    public void evaluate() throws Throwable {
                        throw expected;
                    }
                };

        Throwable actual =
                assertThrows(
                        Throwable.class, () -> mRule.apply(statement, mDescription).evaluate());
        mExpect.withMessage("exception thrown by test").that(actual).isSameInstanceAs(expected);

        mExpect.withMessage("getTestName() AFTER running a test that failed")
                .that(mRule.getTestName())
                .isEqualTo(DEFAULT_TEST_NAME);
    }
}
