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
package com.android.adservices.common;

import com.android.adservices.common.Logger.RealLogger;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// TODO(b/302757068): add unit tests

/** See documentation on {#link {@link ProcessLifeguardRule}. */
abstract class AbstractProcessLifeguardRule implements TestRule {

    protected final Logger mLog;

    // TODO(b/302757068): protected these static variables (either using @GuardedBy or
    // AtomitReference)
    private static @Nullable UncaughtExceptionHandler sRealHandler;
    private static @Nullable DreamCatcher sMyHandler;

    private static final List<String> sAllTestsSoFar = new ArrayList<>();
    private static final List<String> sTestsSinceLastUncaughtFailure = new ArrayList<>();

    /** Default constructor. */
    AbstractProcessLifeguardRule(RealLogger logger) {
        mLog = new Logger(Objects.requireNonNull(logger), getClass());
    }

    /**
     * Checks whether the test is being ran in the "main" thread"
     *
     * <p>If it's the main thread, we let it go so the process is crashed, as it cannot be recovered
     * (at least not on device side).
     */
    protected abstract boolean isMainThread();

    // TODO(b/303112789): add unit tests
    protected UncaughtBackgroundException newUncaughtBackgroundException(
            String testName,
            List<String> allTests,
            List<String> lastTests,
            Throwable uncaughtThrowable) {
        return new UncaughtBackgroundException(
                uncaughtThrowable,
                "Failing "
                        + testName
                        + "because an exception was caught on background (NOTE: "
                        + allTests.size()
                        + " tests executed so far, "
                        + lastTests.size()
                        + " since last uncaught failure - see log with tag "
                        + mLog.getTag()
                        + " for list)");
    }

    // TODO(b/303112789): add unit tests
    protected UncaughtBackgroundException newUncaughtBackgroundException(
            String testName,
            List<String> allTests,
            List<String> lastTests,
            Throwable testFailure,
            Throwable uncaughtThrowable) {
        mLog.e(
                testFailure,
                "Exception thrown by test %s (but not re-surfaced). %d tests executed since last"
                        + " failure: %s",
                testName,
                lastTests.size(),
                lastTests);
        mLog.e("And %d tests executed so far: %s", allTests.size(), allTests);
        return new UncaughtBackgroundException(
                uncaughtThrowable,
                "Failing test because an exception was caught on background; test also"
                        + " threw an exception('"
                        + testFailure
                        + "'), "
                        + allTests.size()
                        + " tests have been executed so far ("
                        + lastTests.size()
                        + " since last uncaught failure) - see log with tag "
                        + mLog.getTag()
                        + " for full stack trace and name of these tests");
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                if (sRealHandler == null) {
                    sRealHandler = Thread.getDefaultUncaughtExceptionHandler();
                    if (sRealHandler != null) {
                        mLog.i("Saving real handler (%s) as sRealHandler", sRealHandler);
                    } else {
                        mLog.d("No real UncaughtExceptionHandler");
                    }
                }
                if (sMyHandler == null) {
                    sMyHandler = new DreamCatcher();
                    mLog.i(
                            "Saving sMyHandler (%s) and setting it as Thread's default"
                                    + " UncaughtExceptionHandler - it will NEVER be unset",
                            sMyHandler);
                    Thread.setDefaultUncaughtExceptionHandler(sMyHandler);
                }

                Throwable testFailure = null;
                String testName =
                        description.getTestClass().getSimpleName()
                                + "#"
                                + description.getMethodName();
                sAllTestsSoFar.add(testName);
                sTestsSinceLastUncaughtFailure.add(testName);
                try {
                    base.evaluate();
                } catch (Throwable t) {
                    testFailure = t;
                }

                if (sMyHandler.uncaughtThrowable != null) {
                    // Need to clear exception once it's thrown
                    Throwable uncaughtThrowable = sMyHandler.uncaughtThrowable;
                    sMyHandler.uncaughtThrowable = null;
                    List<String> lastTests = new ArrayList<>(sTestsSinceLastUncaughtFailure);
                    sTestsSinceLastUncaughtFailure.clear();

                    if (testFailure == null) {
                        throw newUncaughtBackgroundException(
                                testName, sAllTestsSoFar, lastTests, uncaughtThrowable);
                    } else {
                        // TODO(b/303112789): add unit tests for this scenario
                        throw newUncaughtBackgroundException(
                                testName,
                                sAllTestsSoFar,
                                lastTests,
                                testFailure,
                                uncaughtThrowable);
                    }
                }
                if (testFailure != null) {
                    // TODO(b/303112789): add unit tests for this scenario
                    throw testFailure;
                }
            }
        };
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "[mLog="
                + mLog
                + ", sRealHandler="
                + sRealHandler
                + ", sMyHandler="
                + sMyHandler
                + "]";
    }

    private final class DreamCatcher implements UncaughtExceptionHandler {

        public @Nullable Throwable uncaughtThrowable;
        public @Nullable Thread thread;
        public boolean isMain;

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            thread = t;
            uncaughtThrowable = e;
            isMain = isMainThread();
            mLog.e(
                    e,
                    "uncaughtException() on thread=%s: isMain=%b, realHandler=%s",
                    t,
                    isMain,
                    sRealHandler);
            mLog.e(
                    e,
                    "%d tests executed since last failure: %s",
                    sTestsSinceLastUncaughtFailure.size(),
                    sTestsSinceLastUncaughtFailure);
            mLog.e(e, "%d tests executed so far: %s", sAllTestsSoFar.size(), sAllTestsSoFar);
            if (isMain && !(sRealHandler instanceof DreamCatcher)) {
                mLog.e("passing uncaught exception to %s", sRealHandler);
                sRealHandler.uncaughtException(t, e);
            }
        }

        @Override
        public String toString() {
            return "DreamCatcher[uncaughtThrowable="
                    + uncaughtThrowable
                    + ", thread="
                    + thread
                    + ", isMain="
                    + isMain
                    + "]";
        }
    }

    // TODO(b/302757068): copied from AbstractAdServicesFlagsSetterRule, move out / reuse (its main
    // purpose is to show the original's exception stack trace)
    @SuppressWarnings("serial")
    public static final class UncaughtBackgroundException extends Exception {

        UncaughtBackgroundException(Throwable cause, String message) {
            super(message, cause, /* enableSuppression= */ false, /* writableStackTrace= */ false);
            setStackTrace(cause.getStackTrace());
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + ": " + getMessage();
        }
    }
}
