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

import com.android.adservices.shared.testing.Logger.RealLogger;

import org.junit.AssumptionViolatedException;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.List;

// TODO(b/328682831): add unit tests
/**
 * Base class for rule that capture test failures and re-throw them with more info (like state of
 * flags before and after the test).
 */
public abstract class AbstractRethrowerRule extends AbstractRule {

    protected AbstractRethrowerRule(RealLogger logger) {
        super(logger);
    }

    @Override
    protected final void evaluate(Statement base, Description description) throws Throwable {
        String testName = description.getDisplayName();
        List<Throwable> cleanUpErrors = new ArrayList<>();
        preTest(base, description, cleanUpErrors);
        Throwable testError = null;
        try {
            base.evaluate();
        } catch (Throwable t) {
            testError = t;
            cleanUpErrors.add(t);
            onTestFailure(base, description, cleanUpErrors, t);
        } finally {
            postTest(base, description, cleanUpErrors);
        }
        // TODO(b/328682831): ideally it should throw an exception if cleanUpErrors is not
        // empty, but it's better to wait until this class is unit tested to do so (for now,
        // it's just logging it)
        throwIfNecessary(testName, testError, cleanUpErrors);
    }

    private void throwIfNecessary(
            String testName, @Nullable Throwable testError, List<Throwable> cleanUpErrors)
            throws Throwable {
        if (testError == null) {
            mLog.v("Good News, Everyone! %s passed.", testName);
            return;
        }
        if (testError instanceof AssumptionViolatedException) {
            mLog.i("%s is being ignored: %s", testName, testError);
            throw testError;
        }
        StringBuilder dump = new StringBuilder();
        String dumpDescription = decorateTestFailureMessage(dump, cleanUpErrors);
        failTest(testName, testError, dumpDescription, dump);
    }

    /**
     * Called before the test is executed.
     *
     * <p>This method should not throw any exception, but rather store then on {@code cleanUpErrors}
     * (typically using {@link #runSafely(List, Runnable)} to execute its logic.
     */
    protected void preTest(Statement base, Description description, List<Throwable> cleanUpErrors) {
        mLog.v("preTest(%s): not overridden by subclass", TestHelper.getTestName(description));
    }

    /**
     * Called when the test failed .
     *
     * <p>This method should not throw any exception, but rather store then on {@code cleanUpErrors}
     * (typically using {@link #runSafely(List, Runnable)} to execute its logic.
     */
    protected void onTestFailure(
            Statement base,
            Description description,
            List<Throwable> cleanUpErrors,
            Throwable testFailure) {
        mLog.v(
                "onTestFailure(%s, %s): not overridden by subclass",
                TestHelper.getTestName(description), testFailure);
    }

    /**
     * Called after the test is executed (even if it threw an exception).
     *
     * <p>This method should not throw any exception, but rather store then on {@code cleanUpErrors}
     * (typically using {@link #runSafely(List, Runnable)} to execute its logic.
     */
    protected void postTest(
            Statement base, Description description, List<Throwable> cleanUpErrors) {
        mLog.v("postTest(%s): not overridden by subclass", TestHelper.getTestName(description));
    }

    /** Decorates the message that is thrown when a test fail. */
    protected abstract String decorateTestFailureMessage(
            StringBuilder dump, List<Throwable> cleanUpErrors);

    // Currently private, but could be exposed
    private void failTest(
            String testName, Throwable testError, String dumpDescription, StringBuilder dump)
            throws Exception {
        mLog.e("%s failed with %s. %s: \n%s", testName, testError, dumpDescription, dump);

        throw new TestFailure(testError, dumpDescription, dump);
    }
}
