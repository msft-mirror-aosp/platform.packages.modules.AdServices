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
import java.util.Objects;

// TODO(b/302757068): add unit tests

/** See documentation on {#link {@link ProcessLifeguardRule}. */
abstract class AbstractProcessLifeguardRule implements TestRule {
    protected final Logger mLog;

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

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                UncaughtExceptionHandler realHandler = Thread.getDefaultUncaughtExceptionHandler();
                DreamCatcher dreamCatcher = new DreamCatcher();
                Thread.setDefaultUncaughtExceptionHandler(dreamCatcher);
                Throwable testFailure = null;
                try {
                    base.evaluate();
                } catch (Throwable t) {
                    testFailure = t;
                } finally {
                    Thread.setDefaultUncaughtExceptionHandler(realHandler);
                }
                if (dreamCatcher.uncaughtThrowable != null) {
                    if (dreamCatcher.isMain && realHandler != null) {
                        mLog.e("passing uncaught exception to %s", realHandler);
                        realHandler.uncaughtException(
                                dreamCatcher.thread, dreamCatcher.uncaughtThrowable);
                        return;
                    }
                    if (testFailure != null) {
                        throw new TestFailure(
                                dreamCatcher.uncaughtThrowable,
                                "Failing test because an exception was caught on background");
                    }
                    mLog.e(testFailure, "Exception thrown by test (but not re-surfaced)");
                    throw new IllegalStateException(
                            "Failing test because an exception was caught on background; test also"
                                    + " threw an exception('"
                                    + testFailure
                                    + "'; see log with tag "
                                    + mLog.getTag()
                                    + " for full stack trace)",
                            dreamCatcher.uncaughtThrowable);
                }
                if (testFailure != null) {
                    throw testFailure;
                }
            }
        };
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
            mLog.e("uncaughtException(): thread=%s, exception=%s, isMain=%b", t, e, isMain);
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
    public static final class TestFailure extends Exception {

        TestFailure(Throwable cause, String message) {
            super(message, cause, /* enableSuppression= */ false, /* writableStackTrace= */ false);
            setStackTrace(cause.getStackTrace());
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + ": " + getMessage();
        }
    }
}
