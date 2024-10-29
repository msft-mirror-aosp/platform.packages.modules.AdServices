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

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.CommonDescriptions.AClassHasNoNothingAtAll;
import com.android.adservices.shared.meta_testing.FakeAction;
import com.android.adservices.shared.meta_testing.SharedSidelessTestCase;
import com.android.adservices.shared.meta_testing.SimpleStatement;

import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class ActionBasedRuleTest extends SharedSidelessTestCase {

    private final Exception mException1 = new Exception("D'OH!");
    private final Exception mException2 = new IOException("Hi-Yo, Silver!");
    private final RuntimeException mTestException = new RuntimeException("TEST, Y U NO PASS?");

    // Counters used in the fake actions
    private final AtomicInteger mExecutionOrder = new AtomicInteger();
    private final AtomicInteger mReversionOrder = new AtomicInteger();

    private final FakeAction mFakeAction1 =
            new FakeAction("action1", mExecutionOrder, mReversionOrder);
    private final FakeAction mFakeAction2 =
            new FakeAction("action2", mExecutionOrder, mReversionOrder);
    private final FakeAction mFakeAction3 =
            new FakeAction("action3", mExecutionOrder, mReversionOrder);

    private final SimpleStatement mTest = new SimpleStatement();

    private final Description mTestDescription =
            Description.createTestDescription(AClassHasNoNothingAtAll.class, "butItHasATest");

    private final ConcreteActionBasedRule mRule = new ConcreteActionBasedRule();

    @Test
    public void testActionlessRule() throws Throwable {
        runRule();

        mTest.assertEvaluated();
    }

    @Test
    public void testAddAction_null() {
        assertThrows(NullPointerException.class, () -> mRule.addAction(null));
    }

    @Test
    public void testAddAction_sameAction() {
        mRule.addAction(mFakeAction1);

        assertThrows(IllegalStateException.class, () -> mRule.addAction(mFakeAction1));
    }

    @Test
    public void testAddAction_returnSelf() {
        ConcreteActionBasedRule rule = mRule.addAction(mFakeAction1);

        expect.withMessage("value returned by addAction()").that(rule).isSameInstanceAs(mRule);
    }

    @Test
    public void testAddAction_doesNotExecuteAction() {
        mRule.addAction(mFakeAction1);

        expect.withMessage("%s was executed", mFakeAction1)
                .that(mFakeAction1.isExecuted())
                .isFalse();
    }

    @Test
    public void testAddAction_duringTest_throws() {
        mTest.onEvaluate(() -> mRule.addAction(mFakeAction1));

        assertThrows(IllegalStateException.class, () -> runRule());
    }

    @Test
    public void testExecuteOrCache_null() {
        assertThrows(NullPointerException.class, () -> mRule.executeOrCache(null));
    }

    @Test
    public void testExecuteOrCache_returnSelf() throws Exception {
        ConcreteActionBasedRule rule = mRule.executeOrCache(mFakeAction1);

        expect.withMessage("value returned by addAction()").that(rule).isSameInstanceAs(mRule);
    }

    @Test
    public void testExecuteOrCache_beforeTest_sameAction() throws Exception {
        mRule.executeOrCache(mFakeAction1);

        assertThrows(IllegalStateException.class, () -> mRule.executeOrCache(mFakeAction1));
    }

    @Test
    public void testExecuteOrCache_beforeTest_caches() throws Exception {
        mRule.executeOrCache(mFakeAction1);

        expect.withMessage("%s was executed", mFakeAction1)
                .that(mFakeAction1.isExecuted())
                .isFalse();
    }

    @Test
    public void testExecuteOrCache_duringTest_executeAction() throws Throwable {
        mTest.onEvaluate(() -> mRule.executeOrCache(mFakeAction1));

        runRule();

        expect.withMessage("%s was executed", mFakeAction1)
                .that(mFakeAction1.isExecuted())
                .isTrue();
        expect.withMessage("number of times %s was executed", mFakeAction1)
                .that(mFakeAction1.getNumberTimesExecuteCalled())
                .isEqualTo(1);
        expect.withMessage("%s was reverted", mFakeAction1)
                .that(mFakeAction1.isReverted())
                .isFalse();
    }

    @Test
    public void testExecuteOrCache_duringTest_actionThrows() throws Throwable {
        mFakeAction1.onExecuteThrows(mException1);
        mTest.onEvaluate(() -> mRule.executeOrCache(mFakeAction1));

        var thrown = assertThrows(ActionExecutionException.class, () -> runRule());

        expect.withMessage("exception cause")
                .that(thrown)
                .hasCauseThat()
                .isSameInstanceAs(mException1);
    }

    @Test
    public void testExecuteOrCache_duringTest_sameAction() throws Throwable {
        mTest.onEvaluate(
                () -> {
                    mRule.executeOrCache(mFakeAction1);
                    mRule.executeOrCache(mFakeAction1);
                });

        runRule();

        expect.withMessage("%s was executed", mFakeAction1)
                .that(mFakeAction1.isExecuted())
                .isTrue();
        expect.withMessage("number of times %s was executed", mFakeAction1)
                .that(mFakeAction1.getNumberTimesExecuteCalled())
                .isEqualTo(2);
        expect.withMessage("%s was reverted", mFakeAction1)
                .that(mFakeAction1.isReverted())
                .isFalse();
    }

    @Test
    public void testCreateActionsForTest_throws() throws Throwable {
        mRule.onCreateActionsForTest = mException1;
        mRule.addAction(mFakeAction1);

        runRuleAndExpectTestFailed(mException1);

        mTest.assertNotEvaluated();
        expect.withMessage("action executed()").that(mFakeAction1.isExecuted()).isFalse();
        expect.withMessage("action reverted()").that(mFakeAction1.isReverted()).isFalse();
    }

    @Test
    public void testCreateActionsForTest_ok() throws Throwable {
        List<Action> actionsOnTest1 = new ArrayList<>();
        SimpleStatement test1 =
                new SimpleStatement()
                        .onEvaluate(() -> actionsOnTest1.addAll(mRule.getActionsToBeExecuted()));
        List<Action> actionsOnTest2 = new ArrayList<>();
        SimpleStatement test2 =
                new SimpleStatement()
                        .onEvaluate(() -> actionsOnTest2.addAll(mRule.getActionsToBeExecuted()));
        mRule.addAction(mFakeAction1);
        mRule.setActionsForTest(test1, mFakeAction2);
        mRule.setActionsForTest(test2, mFakeAction3, mFakeAction2);

        // Run against first test
        runRule(test1);

        test1.assertEvaluated();
        assertActionsExecutedAndReverted("on test1", mFakeAction1, mFakeAction2);
        expect.withMessage("actions on test1")
                .that(actionsOnTest1)
                .containsExactly(mFakeAction2, mFakeAction1)
                .inOrder();

        // Run against second test
        runRule(test2);

        test2.assertEvaluated();
        assertActionsExecutedAndReverted("on test2", mFakeAction1, mFakeAction2, mFakeAction3);
        expect.withMessage("actions on test2")
                .that(actionsOnTest2)
                .containsExactly(mFakeAction3, mFakeAction2, mFakeAction1)
                .inOrder();
    }

    @Test
    public void testWorkflow_allCommandsExecuted_testPassed() throws Throwable {
        mRule.addAction(mFakeAction1).addAction(mFakeAction2);

        runRule();

        mTest.assertEvaluated();
        expect.withMessage("execution order of action1")
                .that(mFakeAction1.getExecutionOrder())
                .isEqualTo(1);
        expect.withMessage("execution order of action2")
                .that(mFakeAction2.getExecutionOrder())
                .isEqualTo(2);
        expect.withMessage("reversion order of action1")
                .that(mFakeAction1.getReversionOrder())
                .isEqualTo(2);
        expect.withMessage("reversion order of action2")
                .that(mFakeAction2.getReversionOrder())
                .isEqualTo(1);
    }

    @Test
    public void testWorkflow_commandSkipped_testPassed() throws Throwable {
        mFakeAction1.onExecuteReturn(false);
        mRule.addAction(mFakeAction1).addAction(mFakeAction2);

        runRule();

        mTest.assertEvaluated();
        expect.withMessage("execution order of action1")
                .that(mFakeAction1.getExecutionOrder())
                .isEqualTo(1);
        expect.withMessage("execution order of action2")
                .that(mFakeAction2.getExecutionOrder())
                .isEqualTo(2);
        expect.withMessage("action 1 reverted").that(mFakeAction1.isReverted()).isFalse();
        expect.withMessage("reversion order of action2")
                .that(mFakeAction2.getReversionOrder())
                .isEqualTo(1);
    }

    @Test
    public void testWorkflow_commandThrowOnExecute() throws Throwable {
        mFakeAction2.onExecuteThrows(mException1);
        mRule.addAction(mFakeAction1).addAction(mFakeAction2).addAction(mFakeAction3);

        runRuleAndExpectTestFailed(mException1);

        mTest.assertNotEvaluated();
        expect.withMessage("execution order of action1")
                .that(mFakeAction1.getExecutionOrder())
                .isEqualTo(1);
        expect.withMessage("execution order of action2")
                .that(mFakeAction2.getExecutionOrder())
                .isEqualTo(2);
        expect.withMessage("action 3 executed").that(mFakeAction3.isExecuted()).isFalse();
        expect.withMessage("reversion order of action1")
                .that(mFakeAction1.getReversionOrder())
                .isEqualTo(1);
        expect.withMessage("action 2 reverted").that(mFakeAction2.isReverted()).isFalse();
        expect.withMessage("action 3 reverted").that(mFakeAction3.isReverted()).isFalse();
    }

    @Test
    public void testWorkflow_commandThrowOnRevert_testPassed() throws Throwable {
        mFakeAction2.onRevertThrows(mException1);
        mRule.addAction(mFakeAction1).addAction(mFakeAction2).addAction(mFakeAction3);

        runRule();

        mTest.assertEvaluated();
        expect.withMessage("execution order of action1")
                .that(mFakeAction1.getExecutionOrder())
                .isEqualTo(1);
        expect.withMessage("execution order of action2")
                .that(mFakeAction2.getExecutionOrder())
                .isEqualTo(2);
        expect.withMessage("execution order of action3")
                .that(mFakeAction3.getExecutionOrder())
                .isEqualTo(3);
        expect.withMessage("reversion order of action1")
                .that(mFakeAction1.getReversionOrder())
                .isEqualTo(3);
        expect.withMessage("reversion order of action2")
                .that(mFakeAction2.getReversionOrder())
                .isEqualTo(2);
        expect.withMessage("reversion order of action3")
                .that(mFakeAction3.getReversionOrder())
                .isEqualTo(1);
    }

    @Test
    public void testWorkflow_oneCommandThrowOnExecuteAnotherOnRevert() throws Throwable {
        mFakeAction3.onExecuteThrows(mException1);
        mFakeAction2.onRevertThrows(mException2);
        mRule.addAction(mFakeAction1).addAction(mFakeAction2).addAction(mFakeAction3);

        runRuleAndExpectTestFailed(mException1);

        mTest.assertNotEvaluated();
        expect.withMessage("execution order of action1")
                .that(mFakeAction1.getExecutionOrder())
                .isEqualTo(1);
        expect.withMessage("execution order of action2")
                .that(mFakeAction2.getExecutionOrder())
                .isEqualTo(2);
        expect.withMessage("execution order of action3")
                .that(mFakeAction3.getExecutionOrder())
                .isEqualTo(3);
        expect.withMessage("reversion order of action1")
                .that(mFakeAction1.getReversionOrder())
                .isEqualTo(2);
        expect.withMessage("reversion order of action2")
                .that(mFakeAction2.getReversionOrder())
                .isEqualTo(1);
        expect.withMessage("action 3 reverted").that(mFakeAction3.isReverted()).isFalse();
    }

    @Test
    public void testWorkflow_allCommandsExecuted_testFailed() throws Throwable {
        setTestToFail();
        mRule.addAction(mFakeAction1).addAction(mFakeAction2);

        runRuleAndExpectTestFailed(mTestException);

        mTest.assertEvaluated();
        expect.withMessage("execution order of action1")
                .that(mFakeAction1.getExecutionOrder())
                .isEqualTo(1);
        expect.withMessage("execution order of action2")
                .that(mFakeAction2.getExecutionOrder())
                .isEqualTo(2);
        expect.withMessage("reversion order of action1")
                .that(mFakeAction1.getReversionOrder())
                .isEqualTo(2);
        expect.withMessage("reversion order of action2")
                .that(mFakeAction2.getReversionOrder())
                .isEqualTo(1);
    }

    @Test
    public void testWorkflow_commandSkipped_testFailed() throws Throwable {
        setTestToFail();
        mFakeAction1.onExecuteReturn(false);
        mRule.addAction(mFakeAction1).addAction(mFakeAction2).apply(mTest, mTestDescription);

        runRuleAndExpectTestFailed(mTestException);

        mTest.assertEvaluated();
        expect.withMessage("execution order of action1")
                .that(mFakeAction1.getExecutionOrder())
                .isEqualTo(1);
        expect.withMessage("execution order of action2")
                .that(mFakeAction2.getExecutionOrder())
                .isEqualTo(2);
        expect.withMessage("action 1 reverted").that(mFakeAction1.isReverted()).isFalse();
        expect.withMessage("reversion order of action2")
                .that(mFakeAction2.getReversionOrder())
                .isEqualTo(1);
    }

    @Test
    public void testWorkflow_commandThrowOnRevert_testFailed() throws Throwable {
        setTestToFail();
        mFakeAction2.onRevertThrows(mException1);
        mRule.addAction(mFakeAction1).addAction(mFakeAction2).addAction(mFakeAction3);

        runRuleAndExpectTestFailed(mTestException);

        mTest.assertEvaluated();
        expect.withMessage("execution order of action1")
                .that(mFakeAction1.getExecutionOrder())
                .isEqualTo(1);
        expect.withMessage("execution order of action2")
                .that(mFakeAction2.getExecutionOrder())
                .isEqualTo(2);
        expect.withMessage("execution order of action3")
                .that(mFakeAction3.getExecutionOrder())
                .isEqualTo(3);
        expect.withMessage("reversion order of action1")
                .that(mFakeAction1.getReversionOrder())
                .isEqualTo(3);
        expect.withMessage("reversion order of action2")
                .that(mFakeAction2.getReversionOrder())
                .isEqualTo(2);
        expect.withMessage("reversion order of action3")
                .that(mFakeAction3.getReversionOrder())
                .isEqualTo(1);
    }

    @Test
    public void testResetActionsForReuse() throws Throwable {
        mFakeAction1.throwIfExecuteCalledMultipleTime();
        mRule.addAction(mFakeAction1);

        runRule();
        expect.withMessage("number of times %s was executed", mFakeAction1)
                .that(mFakeAction1.getNumberTimesExecuteCalled())
                .isEqualTo(1);

        runRule();
        expect.withMessage("number of times %s was executed", mFakeAction1)
                .that(mFakeAction1.getNumberTimesExecuteCalled())
                .isEqualTo(1);
    }

    @Test
    public void testDecorateToString() {
        expect.withMessage("toString()").that(mRule.toString()).contains(", concrete=I am!");
    }

    private void setTestToFail() {
        mTest.onEvaluate(
                () -> {
                    throw mTestException;
                });
    }

    private void runRule() throws Throwable {
        runRule(mTest);
    }

    private void runRule(Statement test) throws Throwable {
        mRule.apply(test, mTestDescription).evaluate();
    }

    private void runRuleAndExpectTestFailed(Throwable expected) {
        var actual = assertThrows(Throwable.class, () -> runRule());
        expect.withMessage("exception thrown by test").that(actual).isSameInstanceAs(expected);
    }

    private void assertActionsExecutedAndReverted(String when, Action... actions) {
        for (Action action : actions) {
            expect.withMessage("%s %s was executed", action, when)
                    .that(action.isExecuted())
                    .isTrue();
            expect.withMessage("%s %s was reverted", action, when)
                    .that(action.isReverted())
                    .isTrue();
        }
    }

    private final class ConcreteActionBasedRule extends ActionBasedRule<ConcreteActionBasedRule> {
        @Nullable public Throwable onCreateActionsForTest;
        private final Map<Statement, List<Action>> mActionsForTest = new HashMap<>();

        public ConcreteActionBasedRule() {
            super(mFakeRealLogger);
        }

        void setActionsForTest(Statement base, Action... actions) {
            mActionsForTest.put(base, Arrays.asList(actions));
        }

        @Override
        protected ImmutableList<Action> createActionsForTest(
                Statement base, Description description) throws Throwable {
            if (onCreateActionsForTest != null) {
                mLog.e("preExecuteActions(): trowing %s", onCreateActionsForTest);
                throw onCreateActionsForTest;
            }
            var actions = mActionsForTest.get(base);
            return actions == null ? null : ImmutableList.copyOf(actions);
        }

        @Override
        protected void decorateToString(StringBuilder string) {
            string.append(", concrete=I am!");
        }
    }
}
