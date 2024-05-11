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

/** Base class providing common functionatlities to all rules. */
abstract class AbstractRule implements TestRule {

    protected final Logger mLog;

    protected AbstractRule(RealLogger logger) {
        mLog = new Logger(Objects.requireNonNull(logger), getClass());
    }

    @Override
    public final Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                AbstractRule.this.evaluate(base, description);
            }
        };
    }

    /** Defines the rule lofic. */
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

    /** Gets a user-friendly name of a test method. */
    protected final String getTestName(Description test) {
        // TODO(b/315339283): copied from TestHelper.java, which is on device side, should reuse
        StringBuilder testName = new StringBuilder(test.getTestClass().getSimpleName());
        String methodName = test.getMethodName();
        if (methodName != null) {
            testName.append('#').append(methodName).append("()");
        }
        return testName.toString();
    }
}
