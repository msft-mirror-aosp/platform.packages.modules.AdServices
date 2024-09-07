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

import static com.android.adservices.shared.testing.concurrency.DeviceSideConcurrencyHelper.runAsync;

import static org.junit.Assert.assertThrows;

import android.platform.test.annotations.DisabledOnRavenwood;
import android.util.Log;

import com.android.adservices.shared.SharedUnitTestCase;
import com.android.adservices.shared.meta_testing.TestNamerRuleTester;
import com.android.adservices.shared.testing.AbstractProcessLifeguardRule.Mode;
import com.android.adservices.shared.testing.AbstractProcessLifeguardRule.UncaughtBackgroundException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;

// TODO(b/340959631): move to side-less project
// NOTE: ideally it should extend a class that don't define the rule, but it currently doesn't
// matter because:
// 1. The rule sets the default UncaughtExceptionHandler indefinitely (so it will be set after this
//    test - ideally it should leave the device in the state it was before)
// 2. Because of #1, most likely the UncaughtExceptionHandler was already by the rule (in previous
//    tests)
@DisabledOnRavenwood(reason = "TODO(b/335935200): custom UncaughtExceptionHandler not working")
public final class ProcessLifeguardRuleTest extends SharedUnitTestCase {

    private static final String TAG = ProcessLifeguardRuleTest.class.getSimpleName();

    private final Description mTestDescription =
            Description.createTestDescription(ProcessLifeguardRuleTest.class, "testAmI");

    private final IOException mTestFailure = new IOException("D'OH!)");

    // Safeguard to avoid throwing an exception in background just in case the test failed to wait
    // for it
    private static boolean sIamGroot;

    @BeforeClass
    public static void setSafeguard() {
        Log.v(TAG, "setSafeguard()");
        sIamGroot = true;
    }

    @AfterClass
    public static void resetSafeguard() {
        Log.v(TAG, "resetSafeguard()");
        sIamGroot = false;
    }

    @Test
    public void testGetName() throws Throwable {
        ProcessLifeguardRule rule = new ProcessLifeguardRule(Mode.IGNORE);
        TestNamerRuleTester<ProcessLifeguardRule> tester = new TestNamerRuleTester<>(expect, rule);

        tester.justDoIt();
    }

    @Test
    @Ignore("TODO(b/340959631): failing on pre-submit / cloud")
    public void testFailMode_bgThrows_testPass() throws Throwable {
        BgThrowingStatement statement = new BgThrowingStatement(getTestName());
        ProcessLifeguardRule rule = new ProcessLifeguardRule(Mode.FAIL);
        rule.expectBgFailure();

        UncaughtBackgroundException thrown =
                assertThrows(UncaughtBackgroundException.class, () -> runTest(rule, statement));

        expect.withMessage("thrown exception")
                .that(thrown)
                .hasCauseThat()
                .isSameInstanceAs(statement.bgException);
    }

    @Test
    @Ignore("TODO(b/340959631): failing on pre-submit / cloud")
    public void testFailMode_bgThrows_testThrows() throws Throwable {
        BgThrowingStatement statement =
                new BgThrowingStatement(getTestName()).failWith(mTestFailure);
        ProcessLifeguardRule rule = new ProcessLifeguardRule(Mode.FAIL);
        rule.expectBgFailure();

        Throwable thrown = assertThrows(Exception.class, () -> runTest(rule, statement));

        expect.withMessage("thrown exception").that(thrown).isSameInstanceAs(mTestFailure);
    }

    @Test
    @Ignore("TODO(b/340959631): failing on pre-submit / cloud")
    public void testIgnoreMode_bgThrows_testPass() throws Throwable {
        ProcessLifeguardRule rule = new ProcessLifeguardRule(Mode.IGNORE);
        rule.expectBgFailure();
        BgThrowingStatement statement = new BgThrowingStatement(getTestName());

        runTest(rule, statement);
    }

    @Test
    @Ignore("TODO(b/340959631): failing on pre-submit / cloud")
    public void testIgnorelMode_bgThrows_testThrows() throws Throwable {
        BgThrowingStatement statement =
                new BgThrowingStatement(getTestName()).failWith(mTestFailure);
        ProcessLifeguardRule rule = new ProcessLifeguardRule(Mode.IGNORE);
        rule.expectBgFailure();

        Throwable thrown = assertThrows(Exception.class, () -> runTest(rule, statement));

        expect.withMessage("thrown exception").that(thrown).isSameInstanceAs(mTestFailure);
    }

    // TODO(b/340959631): add more tests, like for Mode.FORWARD

    private void runTest(ProcessLifeguardRule rule, Statement statement) throws Throwable {
        rule.apply(statement, mTestDescription).evaluate();
    }

    private static final class BgThrowingStatement extends Statement {

        private static final long DELAY_MS = 100;

        private final String mTestName;
        private Throwable mTestFailure;

        private final Logger mLog =
                new Logger(DynamicLogger.getInstance(), BgThrowingStatement.class);

        public final BackgroundException bgException;

        BgThrowingStatement(String testName) {
            mTestName = testName;
            bgException = new BackgroundException(testName);
        }

        @Override
        public void evaluate() throws Throwable {
            mLog.d("evaluate() called (mTestName=%s, mTestFailure=%s)", mTestName, mTestFailure);
            runAsync(
                    DELAY_MS,
                    () -> {
                        if (sIamGroot) {
                            mLog.i("Throwing %s at %s", bgException, Thread.currentThread());
                            throw bgException;
                        }
                        mLog.e(
                                "NOT throwing %s because %s is finished",
                                bgException, ProcessLifeguardRuleTest.class.getName());
                    });
            if (mTestFailure != null) {
                mLog.i("Throwing %s", mTestFailure);
                throw mTestFailure;
            }
            mLog.d("Saul Goodman!");
        }

        public BgThrowingStatement failWith(Throwable t) {
            mLog.d("failWith(%s", t);
            mTestFailure = t;
            return this;
        }

        @Override
        public String toString() {
            return "[BgThrowingStatement: mTestName="
                    + mTestName
                    + ", mTestFailure="
                    + mTestFailure
                    + "]";
        }
    }

    @SuppressWarnings("serial")
    private static final class BackgroundException extends RuntimeException {
        BackgroundException(String testName) {
            super("Thrown on purpose by " + testName + " - it should have caught it somehow");
        }
    }
}
