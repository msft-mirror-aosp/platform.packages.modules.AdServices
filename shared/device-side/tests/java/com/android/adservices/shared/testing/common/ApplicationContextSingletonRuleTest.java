/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.shared.testing.common;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.util.Log;

import com.android.adservices.shared.common.ApplicationContextSingleton;

import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public final class ApplicationContextSingletonRuleTest {

    private static final String TAG = ApplicationContextSingletonRuleTest.class.getSimpleName();

    // Not a real test (i.e., it doesn't exist on this class), but it's passed to Description
    private static final String TEST_METHOD_BEING_EXECUTED = "testAmI..OrNot";

    private Context mPreviousContext;

    public @Rule final Expect expect = Expect.create();

    @Before
    public void savePreviousContext() {
        mPreviousContext = ApplicationContextSingleton.getForTests();
        Log.d(TAG, "@Before: save context as " + mPreviousContext);
    }

    @After
    public void restorePreviousContext() {
        Log.d(TAG, "@After: restoring context as " + mPreviousContext);
        ApplicationContextSingleton.setForTests(mPreviousContext);
    }

    @Test
    public void testNullConstructor() {
        assertThrows(NullPointerException.class, () -> new ApplicationContextSingletonRule(null));
    }

    @Test
    public void testDefaultConstructorSetsNonNullContext() throws Throwable {
        ApplicationContextSingletonRule rule = new ApplicationContextSingletonRule();

        evaluate(
                rule,
                () ->
                        expect.withMessage("ApplicationContextSingleton.get() during test")
                                .that(ApplicationContextSingleton.get())
                                .isNotNull());
    }

    @Test
    public void testApplicationContextSetDuringTestAndResetAtTheEnd() throws Throwable {
        Context contextBefore = mock(Context.class, "contextBefore");
        Context ruleContext = mock(Context.class, "ruleContext");
        ApplicationContextSingleton.setForTests(contextBefore);
        ApplicationContextSingletonRule rule = new ApplicationContextSingletonRule(ruleContext);

        evaluate(
                rule,
                () -> {
                    expect.withMessage("ApplicationContextSingleton.get() during test")
                            .that(ApplicationContextSingleton.get())
                            .isSameInstanceAs(ruleContext);
                    expect.withMessage("rule.get() during test")
                            .that(rule.get())
                            .isSameInstanceAs(ruleContext);
                });

        expect.withMessage("ApplicationContextSingleton.get() after test")
                .that(ApplicationContextSingleton.get())
                .isSameInstanceAs(contextBefore);
    }

    @Test
    public void testApplicationContextSetDuringTestAndResetAtTheEndEvenWhenTestFails()
            throws Throwable {
        Context contextBefore = mock(Context.class, "contextBefore");
        Context ruleContext = mock(Context.class, "ruleContext");
        ApplicationContextSingleton.setForTests(contextBefore);
        ApplicationContextSingletonRule rule = new ApplicationContextSingletonRule(ruleContext);
        RuntimeException testFailure = new RuntimeException("D'OH!");

        RuntimeException actualFailure =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                evaluate(
                                        rule,
                                        () -> {
                                            expect.withMessage(
                                                            "ApplicationContextSingleton.get()"
                                                                    + " during test")
                                                    .that(ApplicationContextSingleton.get())
                                                    .isSameInstanceAs(ruleContext);
                                            expect.withMessage("rule.get() during test")
                                                    .that(rule.get())
                                                    .isSameInstanceAs(ruleContext);
                                            throw testFailure;
                                        }));

        expect.withMessage("test exception").that(actualFailure).isSameInstanceAs(testFailure);
        expect.withMessage("ApplicationContextSingleton.get() after test")
                .that(ApplicationContextSingleton.get())
                .isSameInstanceAs(contextBefore);
    }

    @Test
    public void testSet() throws Throwable {
        Context ruleContext = mock(Context.class, "ruleContext");
        Context setContext = mock(Context.class, "setContext");
        ApplicationContextSingletonRule rule = new ApplicationContextSingletonRule(ruleContext);

        evaluate(
                rule,
                () -> {
                    rule.set(setContext);
                    expect.withMessage("ApplicationContextSingleton.get() after set()")
                            .that(ApplicationContextSingleton.get())
                            .isSameInstanceAs(setContext);
                    expect.withMessage("rule.get() after set()")
                            .that(rule.get())
                            .isSameInstanceAs(setContext);
                });
    }

    private void evaluate(ApplicationContextSingletonRule rule, Runnable assertions)
            throws Throwable {
        RunnableStatement statement = new RunnableStatement(assertions);
        rule.apply(statement, newTestMethod()).evaluate();
        statement.assertEvaluated();
    }

    private static Description newTestMethod() {
        return Description.createTestDescription(
                ApplicationContextSingletonRuleTest.class, TEST_METHOD_BEING_EXECUTED);
    }

    // TODO(b/306522832): move to shared place (same package as SimpleStatement)
    private static final class RunnableStatement extends Statement {

        private final Runnable mRunnable;
        private boolean mEvaluated;

        RunnableStatement(Runnable runnable) {
            mRunnable = runnable;
        }

        @Override
        public void evaluate() throws Throwable {
            mEvaluated = true;
            Log.d(
                    TAG,
                    "RunnableStatement: before run(), ApplicationContextSingleton is "
                            + ApplicationContextSingleton.getForTests());
            mRunnable.run();
            Log.d(
                    TAG,
                    "RunnableStatement: after run(), ApplicationContextSingleton is "
                            + ApplicationContextSingleton.getForTests());
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + ": " + mRunnable;
        }

        public void assertEvaluated() {
            if (!mEvaluated) {
                throw new AssertionError("test statement was not evaluated");
            }
        }

        public void assertNotEvaluated() {
            if (mEvaluated) {
                throw new AssertionError("test statement was evaluated");
            }
        }
    }
}
