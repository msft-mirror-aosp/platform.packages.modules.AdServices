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

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.List;
import java.util.Objects;

/** Base class providing common functionalities to all rules. */
public abstract class AbstractRule implements TestRule, TestNamer {
    protected final Logger mLog;

    @Nullable private String mTestName = DEFAULT_TEST_NAME;

    protected AbstractRule(RealLogger logger) {
        Objects.requireNonNull(logger, "logger cannot be null");
        mLog = new Logger(logger, getClass());
    }

    @Override
    public final Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                mTestName = TestHelper.getTestName(description);
                try {
                    AbstractRule.this.evaluate(base, description);
                } catch (Throwable t) {
                    mLog.w("%s failed; rethrowing %s", mTestName, t);
                    throw t;
                } finally {
                    mTestName = DEFAULT_TEST_NAME;
                }
            }
        };
    }

    /** Defines the rule logic. */
    protected abstract void evaluate(Statement base, Description description) throws Throwable;

    /**
     * Helper methods used to run something without throwing.
     *
     * @param errors where errors throwing by {@code r} would go to.
     * @param r what to run
     */
    protected final void runSafely(List<Throwable> errors, Runnable r) {
        try {
            r.run();
        } catch (Throwable e) {
            mLog.e(e, "runSafely() failed");
            errors.add(e);
        }
    }

    @Override
    public final String getTestName() {
        return mTestName;
    }
}
